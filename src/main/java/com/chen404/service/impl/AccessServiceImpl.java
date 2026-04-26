package com.chen404.service.impl;

import com.chen404.domain.enums.ArticleCommentPolicyEnum;
import com.chen404.domain.enums.ArticleStatusEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.enums.UserTrustLevelEnum;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.service.AccessService;
import com.chen404.service.support.UserAccessProfileSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AccessServiceImpl implements AccessService {

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Override
    public User getUserOrNull(Long userId) {
        return userAccessProfileSupport.loadUserProfile(userId);
    }

    @Override
    public boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        return UserRoleEnum.ADMIN.matchesRoleCode(user.getRoleCode());
    }

    @Override
    public boolean isFriend(User user) {
        if (user == null) {
            return false;
        }
        return Objects.equals(user.getTrustLevel(), UserTrustLevelEnum.FRIEND.getLevel());
    }

    private boolean isArticleOwner(Long userId, Article article) {
        if (userId == null || article == null) {
            return false;
        }
        return Objects.equals(article.getAuthorId(), userId);
    }

    @Override
    public boolean canManageArticle(Long userId, Article article) {
        if (article == null) {
            return false;
        }
        User user = getUserOrNull(userId);
        if (user == null) {
            return false;
        }
        return isAdmin(user);
    }

    @Override
    public boolean canViewArticle(Long userId, Article article) {
        if (article == null) {
            return false;
        }

        if (isArticleOwner(userId, article) || canManageArticle(userId, article)) {
            return true;
        }

        if (!ArticleStatusEnum.is(article.getStatus(), ArticleStatusEnum.PUBLISHED)) {
            return false;
        }

        User user = getUserOrNull(userId);
        ArticleVisibilityEnum visibility = ArticleVisibilityEnum.fromValue(article.getVisibility());

        return switch (visibility) {
            case PUBLIC -> true;
            case LOGIN -> user != null;
            case FRIEND -> user != null && (isAdmin(user) || isFriend(user));
            case PRIVATE -> false;
            default -> false;
        };
    }

    @Override
    public boolean canCommentArticle(Long userId, Article article) {
        if (article == null || !canViewArticle(userId, article)) {
            return false;
        }

        if (isArticleOwner(userId, article) || canManageArticle(userId, article)) {
            return true;
        }

        User user = getUserOrNull(userId);
        ArticleCommentPolicyEnum commentPolicy = ArticleCommentPolicyEnum.fromValue(article.getCommentPolicy());

        return switch (commentPolicy) {
            case CLOSED -> false;
            case REGISTERED -> user != null;
            case FRIEND -> user != null && (isAdmin(user) || isFriend(user));
            case PUBLIC -> true;
            default -> false;
        };
    }

    @Override
    public boolean canDeleteFile(Long userId, SysFile file) {
        if (file == null) {
            return false;
        }
        User user = getUserOrNull(userId);
        if (user == null) {
            return false;
        }
        return isAdmin(user) || Objects.equals(file.getUserId(), user.getId());
    }

    @Override
    public void fillArticlePermissions(Article article, Long userId) {
        if (article == null) {
            return;
        }
        article.setCanEdit(canManageArticle(userId, article));
        article.setCanDelete(canManageArticle(userId, article));
        article.setCanComment(canCommentArticle(userId, article));
    }
}
