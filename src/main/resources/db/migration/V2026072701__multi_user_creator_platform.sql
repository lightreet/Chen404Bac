ALTER TABLE `travel_memory_location`
  MODIFY COLUMN `created_by` BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN `updated_by` BIGINT NULL COMMENT '最后更新人';

UPDATE `travel_memory_location`
SET `created_by` = 1
WHERE `created_by` IS NULL;

UPDATE `travel_memory_location`
SET `updated_by` = `created_by`
WHERE `updated_by` IS NULL;

ALTER TABLE `travel_memory_location`
  MODIFY COLUMN `created_by` BIGINT NOT NULL COMMENT '创建人',
  MODIFY COLUMN `updated_by` BIGINT NOT NULL COMMENT '最后更新人';

SET @creator_status_index_ddl = IF(
  EXISTS (
    SELECT 1
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'travel_memory_location'
      AND `index_name` = 'idx_travel_memory_creator_status'
  ),
  'SELECT 1',
  'ALTER TABLE `travel_memory_location` ADD KEY `idx_travel_memory_creator_status` (`created_by`, `status`, `update_time`, `id`)'
);
PREPARE creator_status_index_statement FROM @creator_status_index_ddl;
EXECUTE creator_status_index_statement;
DEALLOCATE PREPARE creator_status_index_statement;

SET @contributor_column_ddl = IF(
  EXISTS (
    SELECT 1
    FROM `information_schema`.`columns`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'music_track'
      AND `column_name` = 'contributor_id'
  ),
  'SELECT 1',
  'ALTER TABLE `music_track` ADD COLUMN `contributor_id` BIGINT NULL COMMENT ''曲目贡献者'' AFTER `id`'
);
PREPARE contributor_column_statement FROM @contributor_column_ddl;
EXECUTE contributor_column_statement;
DEALLOCATE PREPARE contributor_column_statement;

UPDATE `music_track` `track`
LEFT JOIN `sys_file` `audio_file`
  ON `audio_file`.`id` = `track`.`audio_file_id`
 AND `audio_file`.`deleted` = 0
LEFT JOIN `sys_file` `cover_file`
  ON `cover_file`.`id` = `track`.`cover_file_id`
 AND `cover_file`.`deleted` = 0
LEFT JOIN `sys_file` `audio_url_file`
  ON `track`.`audio_file_id` IS NULL
 AND BINARY `audio_url_file`.`file_url` = BINARY `track`.`audio_url`
 AND `audio_url_file`.`deleted` = 0
LEFT JOIN `sys_file` `cover_url_file`
  ON `track`.`cover_file_id` IS NULL
 AND BINARY `cover_url_file`.`file_url` = BINARY `track`.`cover_url`
 AND `cover_url_file`.`deleted` = 0
SET `track`.`contributor_id` = COALESCE(
  `audio_file`.`user_id`,
  `cover_file`.`user_id`,
  `audio_url_file`.`user_id`,
  `cover_url_file`.`user_id`,
  1
)
WHERE `track`.`contributor_id` IS NULL;

ALTER TABLE `music_track`
  MODIFY COLUMN `contributor_id` BIGINT NOT NULL COMMENT '曲目贡献者';

SET @contributor_status_index_ddl = IF(
  EXISTS (
    SELECT 1
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = 'music_track'
      AND `index_name` = 'idx_music_track_contributor_status'
  ),
  'SELECT 1',
  'ALTER TABLE `music_track` ADD KEY `idx_music_track_contributor_status` (`contributor_id`, `status`, `update_time`, `id`)'
);
PREPARE contributor_status_index_statement FROM @contributor_status_index_ddl;
EXECUTE contributor_status_index_statement;
DEALLOCATE PREPARE contributor_status_index_statement;

CREATE TABLE IF NOT EXISTS `admin_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
  `recipient_user_id` BIGINT NOT NULL COMMENT '接收管理员用户 ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '业务事件类型',
  `actor_user_id` BIGINT NULL COMMENT '触发事件用户 ID',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '业务资源类型',
  `resource_id` BIGINT NOT NULL COMMENT '业务资源 ID',
  `title` VARCHAR(160) NOT NULL COMMENT '消息标题',
  `summary` VARCHAR(500) NOT NULL COMMENT '安全摘要',
  `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
  `read_time` DATETIME NULL COMMENT '阅读时间',
  `dedupe_key` VARCHAR(160) NOT NULL COMMENT '业务幂等键',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_notification_dedupe` (`dedupe_key`),
  KEY `idx_admin_notification_unread` (`recipient_user_id`, `read_status`, `create_time`, `id`),
  KEY `idx_admin_notification_recent` (`recipient_user_id`, `create_time`, `id`),
  KEY `idx_admin_notification_resource` (`resource_type`, `resource_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员业务消息';
