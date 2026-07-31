package com.chen404.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.chen404.service.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    private static final String SECRET = "test-secret-that-is-long-enough";
    private static final Long USER_ID = 42L;

    private AuthSessionService authSessionService;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        authSessionService = mock(AuthSessionService.class);
        when(authSessionService.getCurrentVersion(USER_ID)).thenReturn(3L);
        jwtUtil = new JwtUtil(authSessionService);
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 120_000L);
    }

    @Test
    void shouldKeepAccessAndRefreshTokenTypesIsolated() {
        String accessToken = jwtUtil.generateToken(USER_ID, "chen404");
        String refreshToken = jwtUtil.generateRefreshToken(USER_ID, "chen404");

        assertEquals("access", jwtUtil.verifyAccessToken(accessToken).getClaim("type").asString());
        assertEquals(3L, jwtUtil.verifyAccessToken(accessToken).getClaim("sessionVersion").asLong());
        assertEquals("refresh", jwtUtil.verifyRefreshToken(refreshToken).getClaim("type").asString());
        assertThrows(JWTVerificationException.class, () -> jwtUtil.verifyAccessToken(refreshToken));
        assertThrows(JWTVerificationException.class, () -> jwtUtil.verifyRefreshToken(accessToken));
    }

    @Test
    void shouldRejectFileTicketAsAccessTokenEvenWhenSecretIsShared() {
        String fileTicket = JWT.create()
                .withSubject(String.valueOf(USER_ID))
                .withClaim("type", "protected-file")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000L))
                .sign(Algorithm.HMAC256(SECRET));

        assertThrows(JWTVerificationException.class, () -> jwtUtil.verifyAccessToken(fileTicket));
    }
}
