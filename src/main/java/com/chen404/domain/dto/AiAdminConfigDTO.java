package com.chen404.domain.dto;

import lombok.Data;

/**
 * 后台 AI 配置 DTO。
 * <p>
 * 仅用于管理员接口。API Key 允许写入，但读取时必须由服务层脱敏并清空明文字段。
 */
@Data
public class AiAdminConfigDTO {

    private LlmConfig llm = new LlmConfig();
    private MaidConfig maid = new MaidConfig();
    private ChatConfig chat = new ChatConfig();
    private ToolConfig tools = new ToolConfig();

    @Data
    public static class LlmConfig {
        private Boolean enabled;
        private String baseUrl;
        private String model;
        private String apiStyle;
        private Boolean apiKeyConfigured;
        private String apiKeyPreview;
        private String apiKey;
        private Double temperature;
        private Integer maxTokens;
        private Integer timeoutSeconds;
    }

    @Data
    public static class MaidConfig {
        private String name;
        private String personaVersion;
        private String systemPrompt;
        private String helperPrompt;
        private String companionPrompt;
    }

    @Data
    public static class ChatConfig {
        private Boolean enabled;
        private Boolean retrievalEnabled;
        private Integer maxCitationCount;
        private Integer maxContextMessages;
        private Integer maxArticleContentChars;
        private Integer maxArticleSummaryChars;
        private Integer maxSuggestionCount;
        private Integer relatedArticleLimit;
        private Boolean requireRecommendIntentForRelatedArticles;
        private Integer bubbleMaxChars;
        private String bubbleLongReplyText;
    }

    @Data
    public static class ToolConfig {
        private Boolean webSearchEnabled;
    }
}
