-- 文章与 sys_file 显式关联（不改 article 表结构）
-- 执行后：新建/更新文章会在 convert 永久文件后同步此表；删除文章会清理关联行

CREATE TABLE IF NOT EXISTS `article_file_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `file_id` bigint NOT NULL COMMENT 'sys_file.id',
  `ref_kind` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CONTENT-正文图片 COVER-封面',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_article_file_kind` (`article_id`, `file_id`, `ref_kind`) USING BTREE,
  KEY `idx_article_id` (`article_id` ASC) USING BTREE,
  KEY `idx_file_id` (`file_id` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章资源与 sys_file 关联表';
