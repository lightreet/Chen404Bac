package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 运行时配置。
 * <p>
 * 统一承载聊天、文章助手、相关文章推荐等 AI 功能的开关与阈值，
 * 便于在不修改业务代码的前提下调整运行策略。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.runtime")
public class AiRuntimeProperties {

    /**
     * 女仆聊天运行参数。
     */
    private Chat chat = new Chat();

    /**
     * 文章助手运行参数。
     */
    private ArticleAssist articleAssist = new ArticleAssist();

    /**
     * 音乐曲目补全运行参数。
     */
    private MusicAssist musicAssist = new MusicAssist();

    /**
     * 相关文章推荐运行参数。
     */
    private Recommend recommend = new Recommend();

    @Data
    public static class Chat {

        /**
         * 是否开启 AI 聊天。
         */
        private boolean enabled = true;

        /**
         * 站内引用上限。
         */
        private int maxCitationCount = 3;

        /**
         * 写入 prompt 的历史消息上限。
         */
        private int maxContextMessages = 8;

        /**
         * 当前文章正文截断长度。
         */
        private int maxArticleContentChars = 3_000;

        /**
         * 当前文章摘要截断长度。
         */
        private int maxArticleSummaryChars = 300;

        /**
         * 建议问题上限。
         */
        private int maxSuggestionCount = 3;

        /**
         * 聊天附带相关文章数量上限。
         */
        private int relatedArticleLimit = 2;

        /**
         * 是否仅在用户表达推荐意图时附带相关文章。
         */
        private boolean requireRecommendIntentForRelatedArticles = true;
    }

    @Data
    public static class ArticleAssist {

        /**
         * 是否开启文章摘要/标签助手。
         */
        private boolean enabled = true;

        /**
         * 文章正文最大输入长度。
         */
        private int maxInputChars = 12_000;

        /**
         * 摘要最大长度。
         */
        private int maxSummaryLength = 180;

        /**
         * 推荐标签数量。
         */
        private int preferredTagCount = 3;

        /**
         * 标签上限。
         */
        private int maxTagCount = 5;
    }

    @Data
    public static class MusicAssist {

        /**
         * 是否开启音乐曲目信息补全能力。
         */
        private boolean enabled = true;
    }

    @Data
    public static class Recommend {

        /**
         * 是否开启相关文章推荐。
         */
        private boolean enabled = true;

        /**
         * 默认返回数量。
         */
        private int defaultLimit = 3;

        /**
         * 候选扫描数量上限。
         */
        private int scanLimit = 30;
    }
}
