INSERT INTO `site_config`
    (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
VALUES
    ('feature.collaboration.article_creation_enabled', 'false', 'false', '知友文章创作开关', 3, 1, 0),
    ('feature.collaboration.travel_creation_enabled', 'false', 'false', '知友旅行创作开关', 3, 1, 0),
    ('feature.collaboration.music_creation_enabled', 'false', 'false', '知友音乐创作开关', 3, 1, 0),
    ('feature.admin_notification.enabled', 'false', 'false', '管理员消息中心开关', 3, 1, 0),
    ('feature.ai.article_assist_enabled', 'true', 'true', 'AI 文章助手开关', 3, 1, 0),
    ('feature.ai.music_assist_enabled', 'true', 'true', 'AI 音乐信息补全开关', 3, 1, 0),
    ('feature.ai.article_recommend_enabled', 'true', 'true', '相关文章推荐开关', 3, 1, 0)
ON DUPLICATE KEY UPDATE
    `is_system` = VALUES(`is_system`),
    `is_public` = VALUES(`is_public`),
    `description` = VALUES(`description`),
    `config_type` = VALUES(`config_type`);
