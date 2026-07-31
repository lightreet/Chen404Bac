package com.chen404.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.chen404.service.AuthSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * JWT工具类
 */
@Component
public class JwtUtil {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String SESSION_VERSION_CLAIM = "sessionVersion";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final AuthSessionService authSessionService;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    public JwtUtil(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    /**
     * 生成访问Token
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withClaim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .withClaim(SESSION_VERSION_CLAIM, authSessionService.getCurrentVersion(userId))
                .withIssuedAt(now)
                .withExpiresAt(expireDate)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 生成刷新Token
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + refreshExpiration);

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withJWTId(UUID.randomUUID().toString())
                .withClaim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .withClaim(SESSION_VERSION_CLAIM, authSessionService.getCurrentVersion(userId))
                .withIssuedAt(now)
                .withExpiresAt(expireDate)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 验证访问令牌；刷新令牌和文件票据不能通过此验证器。
     */
    public DecodedJWT verifyAccessToken(String token) throws JWTVerificationException {
        return buildVerifier(ACCESS_TOKEN_TYPE).verify(token);
    }

    /**
     * 验证刷新令牌；访问令牌和其他业务票据不能通过此验证器。
     */
    public DecodedJWT verifyRefreshToken(String token) throws JWTVerificationException {
        return buildVerifier(REFRESH_TOKEN_TYPE).verify(token);
    }

    /**
     * 从已经完成签名和类型校验的令牌中读取用户 ID。
     */
    public Long getUserId(DecodedJWT decodedJWT) {
        if (decodedJWT == null || decodedJWT.getSubject() == null) {
            throw new JWTVerificationException("Token subject 缺失");
        }
        try {
            return Long.valueOf(decodedJWT.getSubject());
        } catch (NumberFormatException ex) {
            throw new JWTVerificationException("Token subject 非法", ex);
        }
    }

    public long getRemainingMillis(DecodedJWT decodedJWT) {
        if (decodedJWT == null || decodedJWT.getExpiresAt() == null) {
            return 0L;
        }
        return Math.max(decodedJWT.getExpiresAt().getTime() - System.currentTimeMillis(), 0L);
    }

    private JWTVerifier buildVerifier(String tokenType) {
        return JWT.require(Algorithm.HMAC256(secret))
                .withClaim(TOKEN_TYPE_CLAIM, tokenType)
                .build();
    }
}
