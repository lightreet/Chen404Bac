-- 评论头像关联 sys_file：展示 URL 以 sys_file.file_url 为准，避免历史 author_avatar 域名过期
-- 执行：在目标库运行本脚本

ALTER TABLE `comment`
  ADD COLUMN `author_avatar_file_id` bigint NULL DEFAULT NULL COMMENT '评论头像对应 sys_file.id（注册用户快照）' AFTER `author_avatar`,
  ADD KEY `idx_author_avatar_file_id` (`author_avatar_file_id`);

-- 可选：根据已有 author_avatar URL 回填 file_id（需 sys_file 中仍有对应 file_url）
UPDATE `comment` c
INNER JOIN `sys_file` f
  ON f.file_url = c.author_avatar
  AND f.user_id = c.author_id
  AND f.ref_type = 'AVATAR'
  AND f.deleted = 0
SET c.author_avatar_file_id = f.id
WHERE c.author_id IS NOT NULL
  AND c.author_avatar IS NOT NULL
  AND c.author_avatar_file_id IS NULL;
