package com.chen404.config;

import com.chen404.domain.entity.User;
import com.chen404.domain.enums.UserCapabilityEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiUserFeaturePropertiesTest {

    @Test
    void shouldKeepAdminCapabilitiesWhenCollaborationSwitchesAreDisabled() {
        MultiUserFeatureProperties properties = new MultiUserFeatureProperties();
        User admin = buildUser("admin", 0);

        assertEquals(
                UserCapabilityEnum.resolveCodes(admin),
                properties.resolveAvailableCapabilities(admin)
        );
    }

    @Test
    void shouldApplyCollaborationSwitchesToFriendCapabilities() {
        MultiUserFeatureProperties properties = new MultiUserFeatureProperties();
        properties.setTravelCreationEnabled(true);
        User friend = buildUser("user", 1);

        assertEquals(
                List.of(
                        UserCapabilityEnum.FRIEND_CONTENT_VIEW.getCode(),
                        UserCapabilityEnum.TRAVEL_CREATE.getCode()
                ),
                properties.resolveAvailableCapabilities(friend)
        );
    }

    private User buildUser(String roleCode, int trustLevel) {
        User user = new User();
        user.setRoleCode(roleCode);
        user.setTrustLevel(trustLevel);
        user.setStatus(1);
        return user;
    }
}
