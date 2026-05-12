package com.chen404.service.support.scenario.article;

import java.util.List;

/**
 * 文章辅助场景请求。
 *
 * @param title          文章标题
 * @param content        文章正文
 * @param regenerate     是否重新生成
 * @param currentSummary 当前摘要
 * @param currentTags    当前标签
 */
public record ArticleAssistScenarioRequest(
        String title,
        String content,
        boolean regenerate,
        String currentSummary,
        List<String> currentTags
) {
}
