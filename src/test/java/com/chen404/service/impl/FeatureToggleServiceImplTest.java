package com.chen404.service.impl;

import com.chen404.domain.dto.FeatureToggleConfigDTO;
import com.chen404.domain.entity.SiteConfig;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.mapper.SiteConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureToggleServiceImplTest {

    @Test
    void shouldLoadPersistedValuesAndCompleteMissingDefaults() {
        SiteConfigMapper mapper = mock(SiteConfigMapper.class);
        when(mapper.selectAllConfigs()).thenReturn(List.of(
                row("feature.collaboration.travel_creation_enabled", "false"),
                row("feature.admin_notification.enabled", "true")
        ));
        FeatureToggleServiceImpl service = new FeatureToggleServiceImpl(mapper);

        FeatureToggleConfigDTO config = service.getAdminConfig();

        assertFalse(config.getArticleCreationEnabled());
        assertFalse(config.getTravelCreationEnabled());
        assertFalse(config.getMusicCreationEnabled());
        assertTrue(config.getAdminNotificationEnabled());
        assertTrue(config.getAiArticleAssistEnabled());
        assertEquals(
                List.of(UserCapabilityEnum.FRIEND_CONTENT_VIEW.getCode()),
                service.resolveAvailableCapabilities(user("user", 1))
        );
    }

    @Test
    void shouldFilterFriendCreationCapabilitiesButKeepAdminCapabilities() {
        SiteConfigMapper mapper = mock(SiteConfigMapper.class);
        when(mapper.selectAllConfigs()).thenReturn(List.of(
                row("feature.collaboration.article_creation_enabled", "false"),
                row("feature.collaboration.travel_creation_enabled", "true"),
                row("feature.collaboration.music_creation_enabled", "false")
        ));
        FeatureToggleServiceImpl service = new FeatureToggleServiceImpl(mapper);

        assertEquals(
                List.of(
                        UserCapabilityEnum.FRIEND_CONTENT_VIEW.getCode(),
                        UserCapabilityEnum.TRAVEL_CREATE.getCode()
                ),
                service.resolveAvailableCapabilities(user("user", 1))
        );
        assertEquals(
                UserCapabilityEnum.resolveCodes(user("admin", 0)),
                service.resolveAvailableCapabilities(user("admin", 0))
        );
    }

    @Test
    void shouldPersistEveryPrivateFeatureWhenApplyingPartialUpdate() {
        SiteConfigMapper mapper = mock(SiteConfigMapper.class);
        when(mapper.selectAllConfigs()).thenReturn(List.of());
        FeatureToggleServiceImpl service = new FeatureToggleServiceImpl(mapper);
        FeatureToggleConfigDTO patch = new FeatureToggleConfigDTO();
        patch.setMusicCreationEnabled(false);
        patch.setAdminNotificationEnabled(true);

        FeatureToggleConfigDTO updated = service.updateAdminConfig(patch, 1L);

        assertFalse(updated.getArticleCreationEnabled());
        assertFalse(updated.getMusicCreationEnabled());
        assertTrue(updated.getAdminNotificationEnabled());
        ArgumentCaptor<SiteConfig> captor = ArgumentCaptor.forClass(SiteConfig.class);
        verify(mapper, times(7)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(row -> Integer.valueOf(0).equals(row.getIsPublic())));
    }

    private SiteConfig row(String key, String value) {
        SiteConfig row = new SiteConfig();
        row.setId(1L);
        row.setConfigKey(key);
        row.setConfigValue(value);
        return row;
    }

    private User user(String roleCode, int trustLevel) {
        User user = new User();
        user.setStatus(1);
        user.setRoleCode(roleCode);
        user.setTrustLevel(trustLevel);
        return user;
    }
}
