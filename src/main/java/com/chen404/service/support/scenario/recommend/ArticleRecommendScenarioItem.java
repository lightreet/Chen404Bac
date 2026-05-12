package com.chen404.service.support.scenario.recommend;

/**
 * 推荐文章条目。
 *
 * @param articleId 文章 ID
 * @param title     标题
 * @param reason    推荐理由
 * @param url       跳转地址
 */
public record ArticleRecommendScenarioItem(
        Long articleId,
        String title,
        String reason,
        String url
) {
}
