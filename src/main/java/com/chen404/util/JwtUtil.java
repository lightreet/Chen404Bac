package com.chen404.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * JWT工具类
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /**
     * 生成访问Token
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
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
                .withClaim("type", "refresh")
                .withIssuedAt(now)
                .withExpiresAt(expireDate)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 验证Token
     */
    public DecodedJWT verifyToken(String token) throws JWTVerificationException {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
        return verifier.verify(token);
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getClaim("username").asString();
    }

    /**
     * 检查Token是否过期
     */
    public boolean isTokenExpired(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getExpiresAt().before(new Date());
    }

    public long getRemainingMillis(DecodedJWT decodedJWT) {
        if (decodedJWT == null || decodedJWT.getExpiresAt() == null) {
            return 0L;
        }
        return Math.max(decodedJWT.getExpiresAt().getTime() - System.currentTimeMillis(), 0L);
    }
}
