package com.chen404.service;

import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;

/**
 * 统一封装当前阶段的访问控制判断，避免权限逻辑散落在 Controller / Service 中。
 */
public interface AccessService {

    /**
     * 根据用户 ID 获取当前用户；未登录或用户不存在时返回 null。
     */
    User getUserOrNull(Long userId);

    /**
     * 是否为管理员。
     */
    boolean isAdmin(User user);

    /**
     * 是否为好友 / 受信用户。
     */
    boolean isFriend(User user);

    /**
     * 是否可管理文章（作者本人或管理员）。
     */
    boolean canManageArticle(Long userId, Article article);

    /**
     * 是否可查看文章。
     */
    boolean canViewArticle(Long userId, Article article);

    /**
     * 是否可评论文章。
     */
    boolean canCommentArticle(Long userId, Article article);

    /**
     * 是否可删除文件。
     */
    boolean canDeleteFile(Long userId, SysFile file);

    /**
     * 将当前用户对文章的操作权限填充到文章返回对象上。
     */
    void fillArticlePermissions(Article article, Long userId);
}
