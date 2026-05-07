DELETE FROM `site_config`
WHERE `config_key` IN ('article.pageSize', 'upload.maxSize', 'upload.allowTypes');
