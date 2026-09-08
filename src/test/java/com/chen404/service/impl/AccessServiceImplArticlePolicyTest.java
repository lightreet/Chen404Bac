package com.chen404.service.impl;

import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.ArticleCommentPolicyEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.service.support.UserAccessProfileSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessServiceImplArticlePolicyTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 99})
    void unknownPoliciesMustFailClosedForAnonymousAndRegisteredReaders(Integer unknown) {
        AccessServiceImpl service = spy(new AccessServiceImpl());
        UserAccessProfileSupport profiles = mock(UserAccessProfileSupport.class);
        ReflectionTestUtils.setField(service, "userAccessProfileSupport", profiles);
        User friend = new User();
        friend.setId(8L);
        friend.setStatus(1);
        friend.setTrustLevel(1);
        when(profiles.loadUserProfile(8L)).thenReturn(friend);
        Article article = new Article();
        article.setId(1L);
        article.setAuthorId(7L);
        article.setStatus(1);
        article.setVisibility(unknown);
        doReturn(false).when(service).canManageArticle(8L, article);

        assertEquals(ArticleVisibilityEnum.PRIVATE, ArticleVisibilityEnum.fromValue(unknown));
        assertFalse(service.canViewArticle(null, article));
        assertFalse(service.canViewArticle(8L, article));

        article.setVisibility(ArticleVisibilityEnum.PUBLIC.getValue());
        article.setCommentPolicy(unknown);
        assertEquals(ArticleCommentPolicyEnum.CLOSED, ArticleCommentPolicyEnum.fromValue(unknown));
        assertFalse(service.canCommentArticle(null, article));
        assertFalse(service.canCommentArticle(8L, article));
    }
}
