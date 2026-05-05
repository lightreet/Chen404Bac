-- 文章封面绑定 sys_file：冗余 cover_file_id，展示时优先用 sys_file.file_url（与 user.avatar_file_id 一致）

ALTER TABLE `article`
  ADD COLUMN `cover_file_id` bigint NULL DEFAULT NULL COMMENT '封面对应 sys_file.id（正文仍存 cover_image URL）' AFTER `cover_image`;

-- 可选：按已有封面 URL 与 sys_file 回填（须 ref_type=ARTICLE_COVER 且 ref_id=文章 id）
-- UPDATE article a
-- INNER JOIN sys_file f ON f.deleted = 0 AND f.file_url = a.cover_image AND f.ref_id = a.id AND f.ref_type = 'ARTICLE_COVER'
-- SET a.cover_file_id = f.id
-- WHERE a.deleted = 0 AND a.cover_image IS NOT NULL AND a.cover_image != '';
