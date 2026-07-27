ALTER TABLE `sys_user`
  ADD COLUMN `profile_visibility` TINYINT NOT NULL DEFAULT 0 COMMENT '公开成员资料：0-隐藏 1-公开' AFTER `trust_level`,
  ADD COLUMN `email_public` TINYINT NOT NULL DEFAULT 0 COMMENT '公开邮箱：0-隐藏 1-公开' AFTER `profile_visibility`,
  ADD KEY `idx_user_public_profile` (`status`, `profile_visibility`, `trust_level`, `create_time`, `id`);

UPDATE `sys_user` `u`
SET `u`.`profile_visibility` = 1
WHERE `u`.`id` = 1
   OR `u`.`trust_level` = 1
   OR EXISTS (
      SELECT 1
      FROM `sys_user_role` `ur`
      INNER JOIN `sys_role` `r` ON `r`.`id` = `ur`.`role_id`
      WHERE `ur`.`user_id` = `u`.`id`
        AND `r`.`role_code` = 'admin'
   );

ALTER TABLE `sys_file`
  ADD COLUMN `storage_scope` VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT '存储范围：PUBLIC/PROTECTED' AFTER `object_name`,
  ADD COLUMN `bucket_name` VARCHAR(128) NULL COMMENT '对象实际所在存储桶' AFTER `storage_scope`,
  ADD KEY `idx_file_scope_reference` (`storage_scope`, `status`, `ref_type`, `ref_id`, `id`);
