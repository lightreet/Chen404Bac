package com.chen404.domain.enums;

/**
 * 可由管理后台即时调整的运行时业务功能。
 *
 * <p>数据库、存储、鉴权密钥和任务执行器等启动期配置不属于此枚举。</p>
 */
public enum RuntimeFeatureEnum {

    ARTICLE_CREATION(
            "feature.collaboration.article_creation_enabled",
            false,
            "知友文章创作开关"),
    TRAVEL_CREATION(
            "feature.collaboration.travel_creation_enabled",
            false,
            "知友旅行创作开关"),
    MUSIC_CREATION(
            "feature.collaboration.music_creation_enabled",
            false,
            "知友音乐创作开关"),
    ADMIN_NOTIFICATION(
            "feature.admin_notification.enabled",
            true,
            "管理员消息中心开关"),
    AI_ARTICLE_ASSIST(
            "feature.ai.article_assist_enabled",
            true,
            "AI 文章助手开关"),
    AI_MUSIC_ASSIST(
            "feature.ai.music_assist_enabled",
            true,
            "AI 音乐信息补全开关"),
    AI_ARTICLE_RECOMMEND(
            "feature.ai.article_recommend_enabled",
            true,
            "相关文章推荐开关");

    private final String configKey;
    private final boolean defaultEnabled;
    private final String description;

    RuntimeFeatureEnum(String configKey, boolean defaultEnabled, String description) {
        this.configKey = configKey;
        this.defaultEnabled = defaultEnabled;
        this.description = description;
    }

    public String getConfigKey() {
        return configKey;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public String getDescription() {
        return description;
    }
}
