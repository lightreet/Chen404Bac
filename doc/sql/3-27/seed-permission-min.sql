SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 最小权限数据对齐（admin/user + trust_level）
-- 使用方式：
-- 1) 确保 sys_role 存在 admin/user
-- 2) 将你的管理员账号绑定到 admin 角色
-- 3) 将需要发文的账号 trust_level 设为 1

-- 1) 确保角色存在
INSERT IGNORE INTO `sys_role` (`role_name`, `role_code`, `description`, `sort_order`, `status`)
VALUES
('管理员', 'admin', '全站管理权限', 1, 1),
('普通用户', 'user', '基础权限', 5, 1);

-- 2) 绑定管理员账号（把下面的 username 改成你的管理员用户名）
SET @admin_user_id := (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1);
SET @admin_role_id := (SELECT id FROM sys_role WHERE role_code = 'admin' LIMIT 1);
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT @admin_user_id, @admin_role_id
WHERE @admin_user_id IS NOT NULL AND @admin_role_id IS NOT NULL;

-- 3) 设置受信任用户（把下面的 username 改成需要发文的账号）
UPDATE `sys_user` SET trust_level = 1 WHERE username IN ('admin');

SET FOREIGN_KEY_CHECKS = 1;

