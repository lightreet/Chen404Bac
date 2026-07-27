package com.chen404.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 受管文件稳定 URL 与短时访问票据编解码器。
 */
@Component
public class ManagedFileUrlCodec {

    private static final String TICKET_TYPE = "protected-file";
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(?:^|/api)/files/(\\d+)$");

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration ticketTtl;

    public ManagedFileUrlCodec(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${app.protected-file.ticket-expire-minutes:5}") long ticketExpireMinutes) {
        this.algorithm = Algorithm.HMAC256(jwtSecret);
        this.verifier = JWT.require(algorithm)
                .withClaim("type", TICKET_TYPE)
                .build();
        this.ticketTtl = Duration.ofMinutes(Math.max(1, ticketExpireMinutes));
    }

    public String stableUrl(Long fileId) {
        return "/api/files/" + fileId;
    }

    public String ticketedUrl(Long fileId) {
        Date now = new Date();
        String ticket = JWT.create()
                .withSubject(String.valueOf(fileId))
                .withClaim("type", TICKET_TYPE)
                .withIssuedAt(now)
                .withExpiresAt(new Date(now.getTime() + ticketTtl.toMillis()))
                .sign(algorithm);
        return stableUrl(fileId) + "?ticket=" + ticket;
    }

    public Long resolveFileId(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        try {
            String path = URI.create(fileUrl.trim()).getPath();
            Matcher matcher = FILE_PATH_PATTERN.matcher(path);
            return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String normalize(String fileUrl) {
        Long fileId = resolveFileId(fileUrl);
        return fileId == null ? fileUrl : stableUrl(fileId);
    }

    public boolean isValidTicket(Long fileId, String ticket) {
        if (fileId == null || !StringUtils.hasText(ticket)) {
            return false;
        }
        try {
            DecodedJWT decoded = verifier.verify(ticket);
            return Objects.equals(String.valueOf(fileId), decoded.getSubject());
        } catch (JWTVerificationException exception) {
            return false;
        }
    }
}
