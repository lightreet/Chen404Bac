package com.chen404.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.chen404.converter.UserConverter;
import com.chen404.domain.dto.RefreshTokenDTO;
import com.chen404.domain.entity.User;
import com.chen404.exception.UnauthorizedException;
import com.chen404.service.AuthSessionService;
import com.chen404.service.UserService;
import com.chen404.service.VerificationCodeService;
import com.chen404.util.JwtUtil;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthControllerTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 99})
    void refreshShouldRejectDisabledOrUnknownAccountStatusBeforeIssuingTokens(Integer status) {
        UserService users = mock(UserService.class);
        JwtUtil jwt = mock(JwtUtil.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        RedisUtil redis = mock(RedisUtil.class);
        AuthController controller = new AuthController(users, mock(VerificationCodeService.class),
                jwt, redis, mock(UserConverter.class), sessions);
        RefreshTokenDTO request = new RefreshTokenDTO();
        request.setRefreshToken("refresh");
        DecodedJWT decoded = mock(DecodedJWT.class, RETURNS_DEEP_STUBS);
        when(jwt.verifyRefreshToken("refresh")).thenReturn(decoded);
        when(decoded.getId()).thenReturn("token-id");
        when(jwt.getUserId(decoded)).thenReturn(42L);
        User account = new User();
        account.setStatus(status);
        when(users.findAccount(42L)).thenReturn(account);

        assertThrows(UnauthorizedException.class, () -> controller.refresh(request));
        verifyNoInteractions(sessions, redis);
    }

    @Test
    void refreshTokenShouldOnlyBeConsumedOnce() {
        UserService userService = mock(UserService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AuthSessionService authSessionService = mock(AuthSessionService.class);
        AuthController controller = new AuthController(
                userService,
                mock(VerificationCodeService.class),
                jwtUtil,
                redisUtil,
                mock(UserConverter.class),
                authSessionService
        );
        ReflectionTestUtils.setField(controller, "expiration", 60_000L);

        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("refresh-token");
        DecodedJWT decodedJWT = mock(DecodedJWT.class, RETURNS_DEEP_STUBS);
        User user = new User();
        user.setId(42L);
        user.setUsername("chen404");
        user.setStatus(1);

        when(jwtUtil.verifyRefreshToken("refresh-token")).thenReturn(decodedJWT);
        when(decodedJWT.getId()).thenReturn("token-id");
        when(decodedJWT.getClaim("username").asString()).thenReturn("chen404");
        when(jwtUtil.getUserId(decodedJWT)).thenReturn(42L);
        when(userService.findAccount(42L)).thenReturn(user);
        when(authSessionService.isCurrent(42L, decodedJWT)).thenReturn(true);
        when(jwtUtil.getRemainingMillis(decodedJWT)).thenReturn(60_000L);
        when(redisUtil.setIfAbsent(
                RedisKeys.refreshTokenBlacklist("token-id"),
                "1",
                Duration.ofMillis(60_000L)
        )).thenReturn(true, false);
        when(jwtUtil.generateToken(42L, "chen404")).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(42L, "chen404")).thenReturn("new-refresh-token");

        assertEquals(200, controller.refresh(dto).getCode());
        assertThrows(UnauthorizedException.class, () -> controller.refresh(dto));
    }
}
