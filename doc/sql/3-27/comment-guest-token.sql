SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 游客评论自助删除：delete_key 仅返回一次，服务端仅保存 hash
-- 说明：
-- - token 明文不入库
-- - expire_at 可为空（不过期），推荐由业务设置默认过期（如 30 天）
-- - comment_id 与 comment 表为一对一关系（同一评论仅一个 token）

CREATE TABLE IF NOT EXISTS `comment_guest_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `comment_id` bigint NOT NULL COMMENT '评论ID（comment.id）',
  `token_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA-256 hex',
  `expire_at` datetime NULL DEFAULT NULL COMMENT '过期时间（可为空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_comment_id` (`comment_id`) USING BTREE,
  KEY `idx_expire_at` (`expire_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游客评论删除token表';

SET FOREIGN_KEY_CHECKS = 1;

