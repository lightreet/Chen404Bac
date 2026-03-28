SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 下线友人帐/友链功能的增量脚本（已有库升级）
-- 说明：
-- 1) 本脚本会直接删除 friend_link 表及其数据
-- 2) 当前项目无 friend_link 外键依赖，可直接执行

DROP TABLE IF EXISTS `friend_link`;

SET FOREIGN_KEY_CHECKS = 1;

