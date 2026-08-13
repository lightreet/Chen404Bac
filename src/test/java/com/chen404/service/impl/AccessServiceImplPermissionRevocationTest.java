package com.chen404.service.impl;

import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.MusicTrack;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.service.FeatureToggleService;
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
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.resolveAvailableCapabilities(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> UserCapabilityEnum.resolveCodes(invocation.getArgument(0)));
        ReflectionTestUtils.setField(accessService, "featureToggleService", featureToggleService);
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
        ReaderBook book = new ReaderBook();
        book.setOwnerUserId(USER_ID);

        assertFalse(accessService.canManageArticle(USER_ID, article));
        assertFalse(accessService.canManageMusicTrack(USER_ID, track));
        assertFalse(accessService.canManageTravelMemory(USER_ID, location));
        assertFalse(accessService.canManageReaderBook(USER_ID, book));
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
        ReaderBook book = new ReaderBook();
        book.setOwnerUserId(99L);

        assertFalse(accessService.canCurateArticle(USER_ID));
        assertFalse(accessService.canManageArticle(USER_ID, article));
        assertFalse(accessService.canManageMusicTrack(USER_ID, track));
        assertFalse(accessService.canManageTravelMemory(USER_ID, location));
        assertFalse(accessService.canManageReaderBook(USER_ID, book));
    }

    @Test
    void shouldAllowOnlyEnabledAdminOrCurrentOwnerToManageContent() {
        Article ownArticle = new Article();
        ownArticle.setAuthorId(USER_ID);
        Article otherArticle = new Article();
        otherArticle.setAuthorId(99L);
        MusicTrack ownTrack = new MusicTrack();
        ownTrack.setContributorId(USER_ID);
        MusicTrack otherTrack = new MusicTrack();
        otherTrack.setContributorId(99L);
        TravelMemoryLocation ownLocation = new TravelMemoryLocation();
        ownLocation.setCreatedBy(USER_ID);
        TravelMemoryLocation otherLocation = new TravelMemoryLocation();
        otherLocation.setCreatedBy(99L);
        ReaderBook ownBook = new ReaderBook();
        ownBook.setOwnerUserId(USER_ID);
        ReaderBook otherBook = new ReaderBook();
        otherBook.setOwnerUserId(99L);

        when(support.loadUserProfile(USER_ID)).thenReturn(buildUser("user", 1, 1));
        assertTrue(accessService.canManageArticle(USER_ID, ownArticle));
        assertTrue(accessService.canManageMusicTrack(USER_ID, ownTrack));
        assertTrue(accessService.canManageTravelMemory(USER_ID, ownLocation));
        assertTrue(accessService.canManageReaderBook(USER_ID, ownBook));
        assertFalse(accessService.canManageArticle(USER_ID, otherArticle));
        assertFalse(accessService.canManageMusicTrack(USER_ID, otherTrack));
        assertFalse(accessService.canManageTravelMemory(USER_ID, otherLocation));
        assertFalse(accessService.canManageReaderBook(USER_ID, otherBook));

        when(support.loadUserProfile(USER_ID)).thenReturn(buildUser("admin", 0, 1));
        assertTrue(accessService.canManageArticle(USER_ID, otherArticle));
        assertTrue(accessService.canManageMusicTrack(USER_ID, otherTrack));
        assertTrue(accessService.canManageTravelMemory(USER_ID, otherLocation));
        assertTrue(accessService.canManageReaderBook(USER_ID, otherBook));
    }

    @Test
    void shouldAllowReaderBookImportOnlyForEnabledFriendsAndAdmins() {
        when(support.loadUserProfile(USER_ID)).thenReturn(buildUser("user", 0, 1));
        assertFalse(accessService.canImportReaderBook(USER_ID));

        when(support.loadUserProfile(USER_ID)).thenReturn(buildUser("user", 1, 1));
        assertTrue(accessService.canImportReaderBook(USER_ID));

        when(support.loadUserProfile(USER_ID)).thenReturn(buildUser("admin", 0, 1));
        assertTrue(accessService.canImportReaderBook(USER_ID));

        when(support.loadUserProfile(USER_ID)).thenReturn(buildUser("user", 1, 0));
        assertFalse(accessService.canImportReaderBook(USER_ID));
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
