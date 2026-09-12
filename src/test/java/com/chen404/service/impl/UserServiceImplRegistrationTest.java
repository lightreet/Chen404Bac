package com.chen404.service.impl;

import com.chen404.domain.dto.RegisterDTO;
import com.chen404.domain.entity.Role;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserRole;
import com.chen404.exception.ConflictException;
import com.chen404.exception.BadRequestException;
import com.chen404.mapper.RoleMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.mapper.UserRoleMapper;
import com.chen404.service.support.UserAccessProfileSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证邮箱用户名、默认昵称、角色绑定以及失败时的写入边界。 */
class UserServiceImplRegistrationTest {
    private final UserMapper users = mock(UserMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final UserRoleMapper userRoles = mock(UserRoleMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final UserAccessProfileSupport profiles = mock(UserAccessProfileSupport.class);
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "roleMapper", roles);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoles);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "userAccessProfileSupport", profiles);
        when(passwords.encode("secure-password")).thenReturn("encoded-password");
        Role role = new Role();
        role.setId(2L);
        when(roles.selectByRoleCode("user")).thenReturn(role);
        when(userRoles.insert(any(UserRole.class))).thenReturn(1);
        when(users.insert(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(2033818965667954691L);
            when(profiles.loadUserProfile(saved.getId())).thenReturn(saved);
            return 1;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"reader@example.com", "reader.with.a.long.email.address+tag@a-long-mail-domain.example.com"})
    void shouldUseFullEmailAsUsernameAndDefaultNickname(String email) {
        RegisterDTO request = request();
        request.setEmail(email);
        User user = service.register(request);

        assertEquals(email, user.getUsername());
        assertEquals(email, user.getNickname());
        assertEquals("encoded-password", user.getPassword());
        assertEquals(email, user.getEmail());
        assertEquals(1, user.getEmailVerified());
        assertEquals(1, user.getStatus());
        assertEquals(0, user.getTrustLevel());
        assertEquals(2033818965667954691L, user.getId());
        verify(userRoles).insert(argThat((UserRole role) ->
                role.getUserId().equals(user.getId()) && role.getRoleId().equals(2L)));
        var order = inOrder(users, userRoles);
        order.verify(users).insert(user);
        order.verify(userRoles).insert(any(UserRole.class));
    }

    @Test
    void shouldPreserveExplicitNicknameForExistingApiClients() {
        RegisterDTO request = request();
        request.setNickname("Reader");
        assertEquals("Reader", service.register(request).getNickname());
    }

    @Test
    void existingEmailMustNotInsertUser() {
        when(users.selectByEmail("reader@example.com")).thenReturn(new User());
        assertThrows(ConflictException.class, () -> service.register(request()));
        verifyNoInteractions(userRoles, passwords);
        verify(users, never()).insert(any(User.class));
    }

    @Test
    void existingUsernameMustNotBeOverwrittenByEmailRegistration() {
        when(users.selectByUsername("reader@example.com")).thenReturn(new User());
        assertThrows(ConflictException.class, () -> service.register(request()));
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(userRoles);
    }

    private RegisterDTO request() {
        RegisterDTO request = new RegisterDTO();
        request.setEmail("reader@example.com");
        request.setPassword("secure-password");
        request.setCode("123456");
        request.setRegisterType("email");
        return request;
    }

    @Test
    void missingDefaultRoleMustNotCreateAnOrphanAccount() {
        when(roles.selectByRoleCode("user")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.register(request()));
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(passwords, userRoles);
    }

    @Test
    void concurrentUniqueConflictMustBeReportedAsBusinessConflict() {
        when(users.insert(any(User.class))).thenThrow(new DuplicateKeyException("test duplicate"));
        assertThrows(ConflictException.class, () -> service.register(request()));
        verify(userRoles, never()).insert(any(UserRole.class));
    }

    @Test
    void failedRoleBindingMustFailTheRegistrationTransaction() {
        when(userRoles.insert(any(UserRole.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.register(request()));
        verifyNoInteractions(profiles);
    }

    @Test
    void serviceBoundaryMustRejectUnsupportedOrMissingRegistrationChannels() {
        RegisterDTO request = request();
        request.setEmail(null);
        assertThrows(BadRequestException.class, () -> service.register(request));
        assertThrows(BadRequestException.class, () -> service.register(null));
        verifyNoInteractions(users, userRoles, passwords);
    }
}
