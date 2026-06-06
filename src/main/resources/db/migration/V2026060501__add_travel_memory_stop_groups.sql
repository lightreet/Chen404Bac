CREATE TABLE IF NOT EXISTS `travel_memory_stop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `location_id` bigint NOT NULL COMMENT '地点 ID',
  `title` varchar(120) NOT NULL COMMENT '片段标题',
  `story_note` varchar(1000) DEFAULT NULL COMMENT '片段文字',
  `cover_image` varchar(500) DEFAULT NULL COMMENT '片段封面图',
  `visited_at` datetime DEFAULT NULL COMMENT '片段日期',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '片段纬度',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '片段经度',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '片段排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_travel_memory_stop_location` (`location_id`, `sort_order`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='旅行记忆片段表';

ALTER TABLE `travel_memory_entry`
  ADD COLUMN `stop_id` bigint DEFAULT NULL COMMENT '片段 ID' AFTER `location_id`,
  ADD COLUMN `is_stop_cover` tinyint NOT NULL DEFAULT 0 COMMENT '是否片段封面：0-否 1-是' AFTER `is_cover`,
  ADD KEY `idx_travel_memory_entry_stop` (`stop_id`, `display_order`, `id`),
  ADD KEY `idx_travel_memory_entry_stop_cover` (`stop_id`, `is_stop_cover`);

INSERT INTO `travel_memory_stop` (
  `location_id`,
  `title`,
  `story_note`,
  `cover_image`,
  `visited_at`,
  `latitude`,
  `longitude`,
  `sort_order`,
  `create_time`,
  `update_time`,
  `deleted`
)
SELECT
  `id`,
  CASE
    WHEN TRIM(COALESCE(`title`, '')) = '' THEN CONCAT('旅行片段 ', `id`)
    ELSE `title`
  END,
  NULLIF(TRIM(COALESCE(`summary_note`, '')), ''),
  `cover_image`,
  `visited_at`,
  `latitude`,
  `longitude`,
  0,
  `create_time`,
  `update_time`,
  `deleted`
FROM `travel_memory_location`;

UPDATE `travel_memory_entry` `entry_row`
JOIN `travel_memory_stop` `stop_row`
  ON `stop_row`.`location_id` = `entry_row`.`location_id`
 AND `stop_row`.`sort_order` = 0
 AND `stop_row`.`deleted` = 0
SET
  `entry_row`.`stop_id` = `stop_row`.`id`,
  `entry_row`.`is_stop_cover` = `entry_row`.`is_cover`
WHERE `entry_row`.`deleted` = 0;
