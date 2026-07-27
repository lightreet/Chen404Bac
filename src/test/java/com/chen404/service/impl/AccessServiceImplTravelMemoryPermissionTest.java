package com.chen404.service.impl;

import com.chen404.config.MultiUserFeatureProperties;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.service.support.UserAccessProfileSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessServiceImplTravelMemoryPermissionTest {

    @Test
    void shouldAllowEveryVisitorToOpenTravelMemoryMap() {
        UserAccessProfileSupport support = mock(UserAccessProfileSupport.class);
        AccessServiceImpl service = new AccessServiceImpl();
        ReflectionTestUtils.setField(service, "userAccessProfileSupport", support);
        ReflectionTestUtils.setField(service, "multiUserFeatureProperties", enabledFeatures());

        when(support.loadUserProfile(1L)).thenReturn(buildUser("admin", 0));
        when(support.loadUserProfile(2L)).thenReturn(buildUser("user", 1));
        when(support.loadUserProfile(3L)).thenReturn(buildUser("user", 0));

        assertTrue(service.canViewTravelMemory(1L));
        assertTrue(service.canViewTravelMemory(2L));
        assertTrue(service.canViewTravelMemory(3L));
        assertTrue(service.canViewTravelMemory(null));
    }

    @Test
    void shouldAllowAdminOrOwnerToManageTravelMemory() {
        UserAccessProfileSupport support = mock(UserAccessProfileSupport.class);
        AccessServiceImpl service = new AccessServiceImpl();
        ReflectionTestUtils.setField(service, "userAccessProfileSupport", support);
        ReflectionTestUtils.setField(service, "multiUserFeatureProperties", enabledFeatures());

        when(support.loadUserProfile(1L)).thenReturn(buildUser("admin", 0));
        when(support.loadUserProfile(2L)).thenReturn(buildUser("user", 1));

        TravelMemoryLocation adminContent = new TravelMemoryLocation();
        adminContent.setCreatedBy(99L);
        TravelMemoryLocation friendContent = new TravelMemoryLocation();
        friendContent.setCreatedBy(2L);
        TravelMemoryLocation otherContent = new TravelMemoryLocation();
        otherContent.setCreatedBy(3L);

        assertTrue(service.canManageTravelMemory(1L, adminContent));
        assertTrue(service.canManageTravelMemory(2L, friendContent));
        assertFalse(service.canManageTravelMemory(2L, otherContent));
        assertFalse(service.canManageTravelMemory(null, friendContent));
    }

    private User buildUser(String roleCode, Integer trustLevel) {
        User user = new User();
        user.setRoleCode(roleCode);
        user.setTrustLevel(trustLevel);
        user.setStatus(1);
        return user;
    }

    private MultiUserFeatureProperties enabledFeatures() {
        MultiUserFeatureProperties properties = new MultiUserFeatureProperties();
        properties.setArticleCreationEnabled(true);
        properties.setTravelCreationEnabled(true);
        properties.setMusicCreationEnabled(true);
        return properties;
    }
}
