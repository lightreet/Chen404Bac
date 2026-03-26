ALTER TABLE `sys_user`
ADD COLUMN `trust_level` tinyint NOT NULL DEFAULT 0 COMMENT '信任级别：0-普通用户 1-好友/受信用户' AFTER `status`;

ALTER TABLE `article`
ADD COLUMN `visibility` tinyint NOT NULL DEFAULT 0 COMMENT '可见性：0-公开 1-登录可见 2-好友可见 3-私密' AFTER `password`,
ADD COLUMN `comment_policy` tinyint NOT NULL DEFAULT 1 COMMENT '评论策略：0-关闭 1-登录可评论 2-好友可评论 3-游客可评论' AFTER `visibility`;
