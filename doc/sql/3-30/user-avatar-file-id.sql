-- 用户头像绑定 sys_file：冗余 avatar_file_id，展示时优先用 sys_file.file_url
ALTER TABLE `sys_user`
  ADD COLUMN `avatar_file_id` bigint NULL DEFAULT NULL COMMENT '头像对应 sys_file.id' AFTER `avatar`,
  ADD KEY `idx_avatar_file_id` (`avatar_file_id`);

-- 可选：按当前 avatar URL 回填（需与 sys_file.file_url 一致）
UPDATE `sys_user` u
INNER JOIN `sys_file` f
  ON f.file_url = u.avatar
  AND f.user_id = u.id
  AND f.ref_type = 'AVATAR'
  AND f.deleted = 0
SET u.avatar_file_id = f.id
WHERE u.avatar IS NOT NULL
  AND u.avatar NOT LIKE '/%'
  AND (u.avatar_file_id IS NULL);
