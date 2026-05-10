package com.chen404.service.support.chat;

/**
 * 文章知识命中结果。
 *
 * @param articleId     命中文章 ID
 * @param articleTitle  文章标题
 * @param chunkSnippet  命中片段
 * @param url           跳转链接
 * @param score         命中分数
 * @param currentArticle 是否来自当前文章
 */
public record ArticleKnowledgeHit(
        Long articleId,
        String articleTitle,
        String chunkSnippet,
        String url,
        int score,
        boolean currentArticle
) {
}
