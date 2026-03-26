package com.chen404.service.impl;

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
        return User.RoleCode.ADMIN.equals(user.getRoleCode());
    }

    @Override
    public boolean isFriend(User user) {
        if (user == null) {
            return false;
        }
        return Objects.equals(user.getTrustLevel(), User.TrustLevel.FRIEND);
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
        return isAdmin(user) || Objects.equals(article.getAuthorId(), user.getId());
    }

    @Override
    public boolean canViewArticle(Long userId, Article article) {
        if (article == null) {
            return false;
        }

        if (canManageArticle(userId, article)) {
            return true;
        }

        if (!Objects.equals(article.getStatus(), Article.Status.PUBLISHED)) {
            return false;
        }

        User user = getUserOrNull(userId);
        int visibility = article.getVisibility() == null ? Article.Visibility.PUBLIC : article.getVisibility();

        return switch (visibility) {
            case Article.Visibility.PUBLIC -> true;
            case Article.Visibility.LOGIN -> user != null;
            case Article.Visibility.FRIEND -> user != null && (isAdmin(user) || isFriend(user));
            case Article.Visibility.PRIVATE -> false;
            default -> false;
        };
    }

    @Override
    public boolean canCommentArticle(Long userId, Article article) {
        if (article == null || !canViewArticle(userId, article)) {
            return false;
        }

        if (canManageArticle(userId, article)) {
            return true;
        }

        User user = getUserOrNull(userId);
        int commentPolicy = article.getCommentPolicy() == null
                ? Article.CommentPolicy.REGISTERED
                : article.getCommentPolicy();

        return switch (commentPolicy) {
            case Article.CommentPolicy.CLOSED -> false;
            case Article.CommentPolicy.REGISTERED -> user != null;
            case Article.CommentPolicy.FRIEND -> user != null && (isAdmin(user) || isFriend(user));
            case Article.CommentPolicy.PUBLIC -> true;
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
