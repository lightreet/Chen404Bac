package com.chen404.domain.access;

import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.ArticleStatusEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.enums.UserStatusEnum;
import com.chen404.domain.enums.UserTrustLevelEnum;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 一次请求的文章读取范围；对象检查和数据库列表查询共用角色与可见值解析。 */
@Getter
public final class ArticleReadScope {
    private final Long requesterId;
    private final boolean administrator;
    private final List<Integer> visibleValues;

    private ArticleReadScope(Long requesterId, boolean administrator, List<Integer> visibleValues) {
        this.requesterId = requesterId;
        this.administrator = administrator;
        this.visibleValues = List.copyOf(visibleValues);
    }

    /** 不存在或被停用的账号按匿名范围处理，不能通过作者身份扩大权限。 */
    public static ArticleReadScope forUser(User user) {
        List<Integer> values = new ArrayList<>();
        values.add(ArticleVisibilityEnum.PUBLIC.getValue());
        if (user == null || !UserStatusEnum.isEnabled(user.getStatus())) {
            return new ArticleReadScope(null, false, values);
        }
        boolean administrator = UserRoleEnum.ADMIN.matchesRoleCode(user.getRoleCode());
        values.add(ArticleVisibilityEnum.LOGIN.getValue());
        if (administrator || Objects.equals(user.getTrustLevel(), UserTrustLevelEnum.FRIEND.getLevel())) {
            values.add(ArticleVisibilityEnum.FRIEND.getValue());
        }
        return new ArticleReadScope(user.getId(), administrator, values);
    }

    /** 作者和管理员可读非公开状态；其他访问者只能读已发布且明确允许的可见性。 */
    public boolean canRead(Article article) {
        if (article == null) {
            return false;
        }
        if (administrator || (requesterId != null && Objects.equals(requesterId, article.getAuthorId()))) {
            return true;
        }
        return ArticleStatusEnum.is(article.getStatus(), ArticleStatusEnum.PUBLISHED)
                && article.getVisibility() != null && visibleValues.contains(article.getVisibility());
    }
}
