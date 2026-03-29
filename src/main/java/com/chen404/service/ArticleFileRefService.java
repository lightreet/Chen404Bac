package com.chen404.service;

/**
 * 维护 article 与 sys_file 的关联表（在文件已写入 sys_file 且转为永久后调用）
 */
public interface ArticleFileRefService {

    /**
     * 按当前正文与封面 URL 重建关联：先删该文章旧行，再插入能在 sys_file 中解析且归属本文章的记录
     */
    void syncForArticle(Long articleId, String content, String coverImage);

    /** 文章删除时清理关联（逻辑删文章前调用） */
    void removeByArticleId(Long articleId);
}
