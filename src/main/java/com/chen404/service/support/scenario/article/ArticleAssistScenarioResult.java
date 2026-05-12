package com.chen404.service.support.scenario.article;

import java.util.List;

/**
 * 文章辅助场景结果。
 *
 * @param summary 摘要
 * @param tags    标签建议
 */
public record ArticleAssistScenarioResult(
        String summary,
        List<String> tags
) {
}
