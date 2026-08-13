INSERT INTO `site_config`
    (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
VALUES
    ('feature.admin_notification.enabled', 'true', 'true', '管理员消息中心开关', 3, 1, 0)
ON DUPLICATE KEY UPDATE
    `config_value` = VALUES(`config_value`),
    `default_value` = VALUES(`default_value`),
    `is_system` = VALUES(`is_system`),
    `is_public` = VALUES(`is_public`),
    `description` = VALUES(`description`),
    `config_type` = VALUES(`config_type`);
