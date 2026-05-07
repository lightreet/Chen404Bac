INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'comment.audit', 'true', 'true', 'Comment audit enabled', 3, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM `site_config` WHERE `config_key` = 'comment.audit'
);

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'comment.guest', 'true', 'true', 'Guest comment enabled', 3, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM `site_config` WHERE `config_key` = 'comment.guest'
);

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'article.pageSize', '10', '10', 'Article list page size', 2, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM `site_config` WHERE `config_key` = 'article.pageSize'
);

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'upload.maxSize', '12582912', '12582912', 'Image upload max size in bytes', 2, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM `site_config` WHERE `config_key` = 'upload.maxSize'
);

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'upload.allowTypes', '["jpg","jpeg","png","gif","webp","bmp"]', '["jpg","jpeg","png","gif","webp","bmp"]', 'Allowed upload extensions', 4, 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM `site_config` WHERE `config_key` = 'upload.allowTypes'
);
