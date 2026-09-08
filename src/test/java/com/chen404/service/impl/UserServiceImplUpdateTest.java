package com.chen404.service.impl;

import com.chen404.domain.dto.ChangePasswordDTO;
import com.chen404.domain.dto.LoginDTO;
import com.chen404.domain.entity.User;
import com.chen404.exception.ConflictException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.mapper.UserMapper;
import com.chen404.service.AuthSessionService;
import com.chen404.service.EmailService;
import com.chen404.util.JwtUtil;
import com.chen404.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceImplUpdateTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final EmailService email = mock(EmailService.class);
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "jwtUtil", jwt);
        ReflectionTestUtils.setField(service, "redisUtil", mock(RedisUtil.class));
        ReflectionTestUtils.setField(service, "authSessionService", sessions);
        ReflectionTestUtils.setField(service, "emailService", email);
        User user = new User();
        user.setId(7L);
        user.setUsername("reader");
        user.setPassword("old-hash");
        user.setStatus(1);
        when(mapper.selectByUsername("reader")).thenReturn(user);
        when(mapper.selectById(7L)).thenReturn(user);
        when(passwords.matches("old", "old-hash")).thenReturn(true);
    }

    @Test
    void loginMustNotIssueTokensWhenPasswordOrStatusChangedAfterValidation() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("reader");
        dto.setPassword("old");
        assertThrows(UnauthorizedException.class, () -> service.login(dto, "127.0.0.1"));
        verify(mapper).updateLoginAudit(eq(7L), eq("old-hash"), any(), eq("127.0.0.1"));
        verify(mapper, never()).updateById(any(User.class));
        verifyNoInteractions(jwt);
    }

    @Test
    void conflictingPasswordChangeMustNotSendSuccessNotificationOrRevokeSessions() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("old");
        dto.setNewPassword("new");
        when(passwords.encode("new")).thenReturn("new-hash");
        assertThrows(ConflictException.class, () -> service.changePassword(7L, dto, null, null));
        verify(mapper).updatePasswordIfUnchanged(7L, "old-hash", "new-hash");
        verify(mapper, never()).updateById(any(User.class));
        verifyNoInteractions(sessions, email);
    }
}
