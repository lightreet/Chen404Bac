package com.chen404.service.support.scenario.recommend;

/**
 * 文章推荐场景请求。
 *
 * @param currentArticleId 当前文章 ID
 * @param pageContext      页面上下文
 * @param requesterId      请求用户 ID
 * @param seedText         推荐种子文本
 * @param limit            推荐数量
 */
public record ArticleRecommendScenarioRequest(
        Long currentArticleId,
        String pageContext,
        Long requesterId,
        String seedText,
        int limit
) {
}
