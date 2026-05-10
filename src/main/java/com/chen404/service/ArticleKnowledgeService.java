package com.chen404.service;

import com.chen404.service.support.chat.ArticleKnowledgeHit;

import java.util.List;

/**
 * 文章知识切片服务。
 * <p>
 * 负责文章内容切片、索引同步与站内知识检索，
 * 为 AI 女仆提供可引用、可核对的上下文片段。
 */
public interface ArticleKnowledgeService {

    /**
     * 同步单篇文章的知识切片。
     *
     * @param articleId 文章 ID
     */
    void syncArticleChunks(Long articleId);

    /**
     * 删除单篇文章的所有知识切片。
     *
     * @param articleId 文章 ID
     */
    void removeArticleChunks(Long articleId);

    /**
     * 检索当前用户可见的知识切片。
     *
     * @param query            用户问题
     * @param requesterId      当前用户 ID，游客为空
     * @param currentArticleId 当前文章 ID，可为空
     * @param limit            返回上限
     * @return 匹配到的知识片段
     */
    List<ArticleKnowledgeHit> searchVisibleChunks(String query, Long requesterId, Long currentArticleId, int limit);
}
