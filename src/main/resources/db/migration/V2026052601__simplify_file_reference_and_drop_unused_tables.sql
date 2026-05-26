INSERT IGNORE INTO `file_reference`
    (`file_id`, `module_code`, `biz_type`, `biz_id`, `field_key`, `source_type`)
SELECT
    c.`author_avatar_file_id`,
    'COMMENT',
    'COMMENT_AUTHOR_AVATAR',
    c.`id`,
    'authorAvatar',
    'DIRECT'
FROM `comment` c
INNER JOIN `sys_file` f
    ON f.`id` = c.`author_avatar_file_id`
    AND f.`deleted` = 0
    AND f.`status` <> 'DELETED'
WHERE c.`deleted` = 0
  AND c.`author_avatar_file_id` IS NOT NULL;

DROP TABLE IF EXISTS `article_file_ref`;
DROP EVENT IF EXISTS `cleanup_verification_code`;
DROP EVENT IF EXISTS `clean_expired_verification_codes`;
DROP TABLE IF EXISTS `sys_verification_code`;
DROP TABLE IF EXISTS `view_log`;
DROP TABLE IF EXISTS `sys_login_log`;
DROP TABLE IF EXISTS `sys_operation_log`;
