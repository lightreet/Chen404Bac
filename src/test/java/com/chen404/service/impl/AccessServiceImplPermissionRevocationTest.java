package com.chen404.service.impl;

import com.chen404.config.MultiUserFeatureProperties;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.MusicTrack;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.User;
import com.chen404.service.support.UserAccessProfileSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessServiceImplPermissionRevocationTest {

    private static final long USER_ID = 7L;

    private UserAccessProfileSupport support;
    private AccessServiceImpl accessService;

    @BeforeEach
    void setUp() {
        support = mock(UserAccessProfileSupport.class);
        accessService = new AccessServiceImpl();
        ReflectionTestUtils.setField(accessService, "userAccessProfileSupport", support);
        MultiUserFeatureProperties properties = new MultiUserFeatureProperties();
        properties.setArticleCreationEnabled(true);
        properties.setTravelCreationEnabled(true);
        properties.setMusicCreationEnabled(true);
        ReflectionTestUtils.setField(accessService, "multiUserFeatureProperties", properties);
    }

    @Test
    void shouldRevokeEveryEditPermissionImmediatelyAfterFriendDowngrade() {
        User downgraded = buildUser("user", 0, 1);
        when(support.loadUserProfile(USER_ID)).thenReturn(downgraded);

        Article article = new Article();
        article.setAuthorId(USER_ID);
        MusicTrack track = new MusicTrack();
        track.setContributorId(USER_ID);
        TravelMemoryLocation location = new TravelMemoryLocation();
        location.setCreatedBy(USER_ID);

        assertFalse(accessService.canManageArticle(USER_ID, article));
        assertFalse(accessService.canManageMusicTrack(USER_ID, track));
        assertFalse(accessService.canManageTravelMemory(USER_ID, location));
        assertTrue(accessService.canViewTravelMemory(USER_ID, location), "降级后仍可查看自己的旅行记录");
    }

    @Test
    void shouldRejectDisabledAdminAtServiceBoundary() {
        User disabledAdmin = buildUser("admin", 1, 0);
        when(support.loadUserProfile(USER_ID)).thenReturn(disabledAdmin);

        Article article = new Article();
        article.setAuthorId(99L);
        MusicTrack track = new MusicTrack();
        track.setContributorId(99L);
        TravelMemoryLocation location = new TravelMemoryLocation();
        location.setCreatedBy(99L);

        assertFalse(accessService.canCurateArticle(USER_ID));
        assertFalse(accessService.canManageArticle(USER_ID, article));
        assertFalse(accessService.canManageMusicTrack(USER_ID, track));
        assertFalse(accessService.canManageTravelMemory(USER_ID, location));
    }

    private User buildUser(String roleCode, int trustLevel, int status) {
        User user = new User();
        user.setId(USER_ID);
        user.setRoleCode(roleCode);
        user.setTrustLevel(trustLevel);
        user.setStatus(status);
        return user;
    }
}
