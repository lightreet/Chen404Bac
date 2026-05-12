package com.chen404.service.support.scenario.recommend;

import java.util.List;

/**
 * 文章推荐场景结果。
 *
 * @param items      推荐条目
 * @param reason     结果说明
 * @param traceId    追踪 ID
 * @param sourceType 来源类型
 */
public record ArticleRecommendScenarioResult(
        List<ArticleRecommendScenarioItem> items,
        String reason,
        String traceId,
        String sourceType
) {
}
