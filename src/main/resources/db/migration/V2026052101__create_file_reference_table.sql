CREATE TABLE `file_reference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` bigint NOT NULL COMMENT '关联 sys_file.id',
  `module_code` varchar(64) NOT NULL COMMENT '模块编码',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` bigint NOT NULL COMMENT '业务主键',
  `field_key` varchar(128) NOT NULL COMMENT '引用字段标识',
  `source_type` varchar(32) NOT NULL DEFAULT 'DIRECT' COMMENT '引用来源类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_owner` (`file_id`, `module_code`, `biz_type`, `biz_id`, `field_key`),
  KEY `idx_file_reference_file_id` (`file_id`),
  KEY `idx_file_reference_owner` (`module_code`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一文件引用关系表';
