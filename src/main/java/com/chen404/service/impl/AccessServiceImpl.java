package com.chen404.service.impl;

import com.chen404.config.MultiUserFeatureProperties;
import com.chen404.domain.enums.ArticleCommentPolicyEnum;
import com.chen404.domain.enums.ArticleStatusEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.domain.enums.TravelMemoryStatusEnum;
import com.chen404.domain.enums.TravelMemoryVisibilityEnum;
import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.enums.UserTrustLevelEnum;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.MusicTrack;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.User;
import com.chen404.service.AccessService;
import com.chen404.service.support.UserAccessProfileSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.List;

@Service
public class AccessServiceImpl implements AccessService {

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Autowired
    private MultiUserFeatureProperties multiUserFeatureProperties;

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

    @Override
    public List<String> listCapabilities(Long userId) {
        return multiUserFeatureProperties.resolveAvailableCapabilities(getUserOrNull(userId));
    }

    @Override
    public boolean hasCapability(Long userId, String capabilityCode) {
        return UserCapabilityEnum.containsCode(listCapabilities(userId), capabilityCode);
    }

    @Override
    public boolean canCreateArticle(Long userId) {
        return hasCapability(userId, UserCapabilityEnum.ARTICLE_CREATE.getCode());
    }

    @Override
    public boolean canCurateArticle(Long userId) {
        User user = getUserOrNull(userId);
        return isEnabled(user) && isAdmin(user);
    }

    @Override
    public boolean canViewTravelMemory(Long userId) {
        return true;
    }

    @Override
    public boolean canCreateTravelMemory(Long userId) {
        return hasCapability(userId, UserCapabilityEnum.TRAVEL_CREATE.getCode());
    }

    @Override
    public boolean canManageTravelMemory(Long userId, TravelMemoryLocation location) {
        if (location == null || userId == null) {
            return false;
        }
        User user = getUserOrNull(userId);
        if (user == null) {
            return false;
        }
        if (!isEnabled(user)) {
            return false;
        }
        return isAdmin(user)
                || (Objects.equals(location.getCreatedBy(), userId)
                && canCreateTravelMemory(userId));
    }

    @Override
    public boolean canViewTravelMemory(Long userId, TravelMemoryLocation location) {
        if (location == null) {
            return false;
        }
        if (isActiveTravelMemoryOwner(userId, location) || canManageTravelMemory(userId, location)) {
            return true;
        }
        if (!Objects.equals(location.getStatus(), TravelMemoryStatusEnum.VISIBLE.getValue())) {
            return false;
        }
        TravelMemoryVisibilityEnum visibility = TravelMemoryVisibilityEnum.fromValue(location.getVisibility());
        if (visibility == TravelMemoryVisibilityEnum.PUBLIC) {
            return true;
        }
        User user = getUserOrNull(userId);
        return visibility == TravelMemoryVisibilityEnum.FRIEND
                && user != null
                && (isAdmin(user) || isFriend(user));
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
        if (!isEnabled(user)) {
            return false;
        }
        return isAdmin(user)
                || (Objects.equals(article.getAuthorId(), userId)
                && canCreateArticle(userId));
    }

    @Override
    public boolean canCreateMusicTrack(Long userId) {
        return hasCapability(userId, UserCapabilityEnum.MUSIC_CREATE.getCode());
    }

    @Override
    public boolean canManageMusicTrack(Long userId, MusicTrack track) {
        if (track == null || userId == null) {
            return false;
        }
        User user = getUserOrNull(userId);
        if (!isEnabled(user)) {
            return false;
        }
        return isAdmin(user)
                || (Objects.equals(track.getContributorId(), userId)
                && canCreateMusicTrack(userId));
    }

    @Override
    public boolean canViewArticle(Long userId, Article article) {
        if (article == null) {
            return false;
        }

        User user = getUserOrNull(userId);
        if (isEnabled(user)
                && (isArticleOwner(userId, article) || canManageArticle(userId, article))) {
            return true;
        }

        if (!ArticleStatusEnum.is(article.getStatus(), ArticleStatusEnum.PUBLISHED)) {
            return false;
        }

        ArticleVisibilityEnum visibility = ArticleVisibilityEnum.fromValue(article.getVisibility());

        return switch (visibility) {
            case PUBLIC -> true;
            case LOGIN -> isEnabled(user);
            case FRIEND -> isEnabled(user) && (isAdmin(user) || isFriend(user));
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
        return isEnabled(user)
                && (isAdmin(user) || Objects.equals(file.getUserId(), user.getId()));
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

    private boolean isEnabled(User user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus());
    }

    private boolean isActiveTravelMemoryOwner(Long userId, TravelMemoryLocation location) {
        return userId != null
                && location != null
                && Objects.equals(location.getCreatedBy(), userId)
                && isEnabled(getUserOrNull(userId));
    }
}
