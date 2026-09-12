-- 新注册用户使用完整邮箱作为用户名，容量与现有 email 列保持一致。
ALTER TABLE sys_user
    MODIFY COLUMN username VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名';

-- 默认昵称同样沿用邮箱，保留完整邮箱而不依赖数据库截断。
ALTER TABLE sys_user
    MODIFY COLUMN nickname VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称';
