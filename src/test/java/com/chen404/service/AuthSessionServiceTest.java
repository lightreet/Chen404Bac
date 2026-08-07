package com.chen404.service;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    @Test
    void shouldRejectTokenFromPreviousSessionVersion() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        AuthSessionService service = new AuthSessionService(redisUtil);
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim claim = mock(Claim.class);

        when(redisUtil.getString(RedisKeys.authSessionVersion(42L))).thenReturn("2");
        when(decodedJWT.getClaim("sessionVersion")).thenReturn(claim);
        when(claim.asLong()).thenReturn(1L);

        assertFalse(service.isCurrent(42L, decodedJWT));
    }

    @Test
    void shouldAcceptCurrentVersionAndIncrementOnRevoke() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        AuthSessionService service = new AuthSessionService(redisUtil);
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim claim = mock(Claim.class);

        when(redisUtil.getString(RedisKeys.authSessionVersion(42L))).thenReturn("2");
        when(redisUtil.increment(RedisKeys.authSessionVersion(42L))).thenReturn(3L);
        when(decodedJWT.getClaim("sessionVersion")).thenReturn(claim);
        when(claim.asLong()).thenReturn(2L);

        assertTrue(service.isCurrent(42L, decodedJWT));
        service.revokeAll(42L);
        verify(redisUtil).increment(RedisKeys.authSessionVersion(42L));
    }

    @Test
    void shouldRejectLegacyTokenWithoutSessionVersion() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        AuthSessionService service = new AuthSessionService(redisUtil);
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim claim = mock(Claim.class);

        when(decodedJWT.getClaim("sessionVersion")).thenReturn(claim);
        when(claim.asLong()).thenReturn(null);

        assertFalse(service.isCurrent(42L, decodedJWT));
    }

    @Test
    void shouldFailClosedWhenStoredVersionIsCorrupted() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        AuthSessionService service = new AuthSessionService(redisUtil);

        when(redisUtil.getString(RedisKeys.authSessionVersion(42L))).thenReturn("invalid");

        assertThrows(IllegalStateException.class, () -> service.getCurrentVersion(42L));
    }
}
