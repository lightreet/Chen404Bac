package com.chen404.service.impl;

import com.chen404.domain.entity.User;
import com.chen404.service.support.UserAccessProfileSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessServiceImplTravelMemoryPermissionTest {

    @Test
    void shouldAllowAdminAndFriendToViewTravelMemory() {
        UserAccessProfileSupport support = mock(UserAccessProfileSupport.class);
        AccessServiceImpl service = new AccessServiceImpl();
        ReflectionTestUtils.setField(service, "userAccessProfileSupport", support);

        when(support.loadUserProfile(1L)).thenReturn(buildUser("admin", 0));
        when(support.loadUserProfile(2L)).thenReturn(buildUser("user", 1));
        when(support.loadUserProfile(3L)).thenReturn(buildUser("user", 0));

        assertTrue(service.canViewTravelMemory(1L));
        assertTrue(service.canViewTravelMemory(2L));
        assertFalse(service.canViewTravelMemory(3L));
        assertFalse(service.canViewTravelMemory(null));
    }

    @Test
    void shouldOnlyAllowAdminToManageTravelMemory() {
        UserAccessProfileSupport support = mock(UserAccessProfileSupport.class);
        AccessServiceImpl service = new AccessServiceImpl();
        ReflectionTestUtils.setField(service, "userAccessProfileSupport", support);

        when(support.loadUserProfile(1L)).thenReturn(buildUser("admin", 0));
        when(support.loadUserProfile(2L)).thenReturn(buildUser("user", 1));

        assertTrue(service.canManageTravelMemory(1L));
        assertFalse(service.canManageTravelMemory(2L));
        assertFalse(service.canManageTravelMemory(null));
    }

    private User buildUser(String roleCode, Integer trustLevel) {
        User user = new User();
        user.setRoleCode(roleCode);
        user.setTrustLevel(trustLevel);
        return user;
    }
}
