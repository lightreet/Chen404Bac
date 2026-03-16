-- Chen404 博客系统数据库脚本
-- MySQL 8.0+
-- 创建日期: 2026-03-16
-- 字符集: utf8mb4

-- ============================================
-- 1. 创建数据库
-- ============================================

CREATE DATABASE IF NOT EXISTS chen404
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chen404;

-- 设置时区
SET time_zone = '+08:00';

-- ============================================
-- 2. 用户权限模块
-- ============================================

-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    avatar VARCHAR(500) DEFAULT 'default-avatar.jpg' COMMENT '头像URL',
    bio VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱是否验证：0-否 1-是',
    phone_verified TINYINT NOT NULL DEFAULT 0 COMMENT '手机是否验证：0-否 1-是',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_code (role_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_role_id (role_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- 菜单/权限表
CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID（0为顶级）',
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    menu_type TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1-目录 2-菜单 3-按钮',
    icon VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
    path VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
    component VARCHAR(200) DEFAULT NULL COMMENT '组件路径',
    permission VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent_id (parent_id),
    INDEX idx_menu_type (menu_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单/权限表';

-- 角色菜单关联表
CREATE TABLE sys_role_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- ============================================
-- 3. 日志模块
-- ============================================

-- 操作日志表
CREATE TABLE sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '操作用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '操作用户名',
    operation VARCHAR(100) NOT NULL COMMENT '操作描述',
    method VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
    request_url VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    request_params TEXT DEFAULT NULL COMMENT '请求参数',
    response_data TEXT DEFAULT NULL COMMENT '响应数据',
    ip VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT '浏览器UA',
    execute_time INT DEFAULT NULL COMMENT '执行时长（毫秒）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败 1-成功',
    error_msg TEXT DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 登录日志表
CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    login_type TINYINT NOT NULL DEFAULT 1 COMMENT '登录类型：1-账号密码 2-邮箱 3-手机',
    ip VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    location VARCHAR(100) DEFAULT NULL COMMENT '登录地点',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT '浏览器UA',
    browser VARCHAR(50) DEFAULT NULL COMMENT '浏览器类型',
    os VARCHAR(50) DEFAULT NULL COMMENT '操作系统',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败 1-成功',
    error_msg VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- ============================================
-- 4. 文件与验证码模块
-- ============================================

-- 文件上传记录表
CREATE TABLE sys_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    file_name VARCHAR(255) NOT NULL COMMENT '存储文件名',
    file_original_name VARCHAR(255) NOT NULL COMMENT '原文件名',
    file_suffix VARCHAR(20) DEFAULT NULL COMMENT '文件后缀',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问URL',
    file_path VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_type VARCHAR(50) DEFAULT NULL COMMENT '文件MIME类型',
    module VARCHAR(50) DEFAULT NULL COMMENT '所属模块',
    user_id BIGINT DEFAULT NULL COMMENT '上传用户ID',
    download_count INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user_id (user_id),
    INDEX idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传记录表';

-- 验证码记录表
CREATE TABLE sys_verification_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    target VARCHAR(100) NOT NULL COMMENT '目标（邮箱或手机号）',
    code VARCHAR(10) NOT NULL COMMENT '验证码',
    type TINYINT NOT NULL COMMENT '类型：1-注册 2-登录 3-重置密码',
    scene TINYINT NOT NULL DEFAULT 1 COMMENT '场景：1-邮箱 2-短信',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：0-否 1-是',
    use_time DATETIME DEFAULT NULL COMMENT '使用时间',
    ip VARCHAR(50) DEFAULT NULL COMMENT '请求IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_target_type (target, type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码记录表';

-- ============================================
-- 5. 内容管理模块
-- ============================================

-- 文章表
CREATE TABLE article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID',
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    summary VARCHAR(500) DEFAULT NULL COMMENT '文章摘要',
    content LONGTEXT DEFAULT NULL COMMENT '文章内容（Markdown）',
    content_html LONGTEXT DEFAULT NULL COMMENT 'HTML内容（缓存）',
    cover_image VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    author_id BIGINT NOT NULL COMMENT '作者ID',
    category_id BIGINT DEFAULT NULL COMMENT '分类ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布 2-回收站',
    view_count INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数',
    is_top TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
    is_recommend TINYINT NOT NULL DEFAULT 0 COMMENT '是否推荐：0-否 1-是',
    is_original TINYINT NOT NULL DEFAULT 1 COMMENT '是否原创：0-转载 1-原创',
    original_url VARCHAR(500) DEFAULT NULL COMMENT '原文链接（转载时）',
    password VARCHAR(100) DEFAULT NULL COMMENT '访问密码（私密文章）',
    publish_time DATETIME DEFAULT NULL COMMENT '发布时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_author_id (author_id),
    INDEX idx_category_id (category_id),
    INDEX idx_status (status),
    INDEX idx_is_top (is_top),
    INDEX idx_is_recommend (is_recommend),
    INDEX idx_publish_time (publish_time),
    INDEX idx_create_time (create_time),
    FULLTEXT INDEX ft_title_summary (title, summary) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- 分类表
CREATE TABLE category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    slug VARCHAR(50) NOT NULL COMMENT '别名（URL用）',
    description VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    icon VARCHAR(100) DEFAULT NULL COMMENT '图标',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    article_count INT NOT NULL DEFAULT 0 COMMENT '文章数量',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_slug (slug),
    INDEX idx_status (status),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- 标签表
CREATE TABLE tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    slug VARCHAR(50) NOT NULL COMMENT '别名（URL用）',
    color VARCHAR(20) NOT NULL DEFAULT '#fb7299' COMMENT '标签颜色',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    article_count INT NOT NULL DEFAULT 0 COMMENT '文章数量',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_slug (slug),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- 文章标签关联表
CREATE TABLE article_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    tag_id BIGINT NOT NULL COMMENT '标签ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_article_tag (article_id, tag_id),
    INDEX idx_tag_id (tag_id),
    INDEX idx_article_id (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签关联表';

-- 评论表
CREATE TABLE comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    article_id BIGINT DEFAULT NULL COMMENT '文章ID（null为留言板）',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父评论ID（0为顶级）',
    root_id BIGINT NOT NULL DEFAULT 0 COMMENT '根评论ID',
    content TEXT NOT NULL COMMENT '评论内容',
    author_name VARCHAR(50) NOT NULL COMMENT '评论者名称',
    author_email VARCHAR(100) DEFAULT NULL COMMENT '评论者邮箱',
    author_website VARCHAR(200) DEFAULT NULL COMMENT '评论者网站',
    author_avatar VARCHAR(500) DEFAULT NULL COMMENT '评论者头像',
    author_id BIGINT DEFAULT NULL COMMENT '注册用户ID（游客为null）',
    ip VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    location VARCHAR(100) DEFAULT NULL COMMENT '归属地',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'UA信息',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-已通过 2-已拒绝',
    is_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否管理员回复：0-否 1-是',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_article_id (article_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_root_id (root_id),
    INDEX idx_author_id (author_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- 友链表
CREATE TABLE friend_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '友链ID',
    site_name VARCHAR(100) NOT NULL COMMENT '站点名称',
    site_url VARCHAR(500) NOT NULL COMMENT '站点链接',
    site_logo VARCHAR(500) DEFAULT NULL COMMENT '站点Logo',
    description VARCHAR(500) DEFAULT NULL COMMENT '站点描述',
    email VARCHAR(100) DEFAULT NULL COMMENT '联系邮箱',
    owner_name VARCHAR(50) DEFAULT NULL COMMENT '站长名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-已通过 2-已拒绝',
    reject_reason VARCHAR(200) DEFAULT NULL COMMENT '拒绝原因',
    view_count INT NOT NULL DEFAULT 0 COMMENT '点击次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_status (status),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='友链表';

-- 轮播图表
CREATE TABLE banner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '轮播图ID',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    subtitle VARCHAR(200) DEFAULT NULL COMMENT '副标题',
    image VARCHAR(500) NOT NULL COMMENT '图片URL',
    link VARCHAR(500) DEFAULT NULL COMMENT '链接地址',
    target TINYINT NOT NULL DEFAULT 1 COMMENT '打开方式：1-当前页 2-新窗口',
    position TINYINT NOT NULL DEFAULT 1 COMMENT '位置：1-首页 2-文章页',
    background_color VARCHAR(20) DEFAULT NULL COMMENT '背景色',
    text_color VARCHAR(20) DEFAULT NULL COMMENT '文字颜色',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    view_count INT NOT NULL DEFAULT 0 COMMENT '点击次数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_position (position),
    INDEX idx_status (status),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮播图表';

-- ============================================
-- 6. 站点管理模块
-- ============================================

-- 站点配置表
CREATE TABLE site_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT DEFAULT NULL COMMENT '配置值',
    default_value TEXT DEFAULT NULL COMMENT '默认值',
    description VARCHAR(200) DEFAULT NULL COMMENT '配置描述',
    config_type TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1-文本 2-数字 3-布尔 4-JSON',
    is_system TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统配置：0-否 1-是',
    is_public TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开：0-私密 1-公开',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点配置表';

-- ============================================
-- 7. 数据统计模块
-- ============================================

-- 文章浏览记录表
CREATE TABLE view_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    article_id BIGINT NOT NULL COMMENT '文章ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID（游客为null）',
    ip VARCHAR(50) NOT NULL COMMENT 'IP地址',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'UA信息',
    referer VARCHAR(500) DEFAULT NULL COMMENT '来源页面',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_date DATE NOT NULL DEFAULT (CURDATE()) COMMENT '创建日期',
    INDEX idx_article_id (article_id),
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_create_date (create_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章浏览记录表';

-- ============================================
-- 8. 数据库设计说明
-- ============================================
-- 本设计不使用数据库外键约束，关联关系通过应用层维护
--
-- 无物理外键设计优势：
-- 1. 性能优化：避免外键检查开销，提高写入性能
-- 2. 水平扩展：支持分库分表，不受外键跨库限制
-- 3. 数据安全：避免级联删除导致的意外数据丢失
-- 4. 维护灵活：便于数据迁移、归档和清理
-- 5. 并发优化：减少锁竞争，提高并发性能
--
-- 数据一致性保障：
-- 1. 应用层通过事务保证数据一致性
-- 2. 关联字段保留索引（idx_user_id, idx_article_id等）
-- 3. 软删除机制（deleted字段）替代级联删除
-- 4. 业务代码中校验关联数据有效性
-- ============================================

-- ============================================
-- 9. 初始化数据
-- ============================================

-- 初始化角色
INSERT INTO sys_role (id, role_name, role_code, description, sort_order, status) VALUES
(1, '超级管理员', 'super_admin', '拥有所有权限', 1, 1),
(2, '管理员', 'admin', '内容管理权限', 2, 1),
(3, '编辑', 'editor', '文章管理权限', 3, 1),
(4, '开发者', 'developer', '技术相关权限', 4, 1),
(5, '普通用户', 'user', '基础权限', 5, 1),
(6, '访客', 'guest', '只读权限', 6, 1);

-- 初始化管理员账号（密码：admin123，BCrypt加密）
INSERT INTO sys_user (id, username, password, nickname, email, avatar, status, email_verified, create_time) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '管理员', 'admin@chen404.com', '/default-avatar.jpg', 1, 1, NOW());

-- 管理员赋予超级管理员角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 初始化菜单/权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort_order, status) VALUES
-- 系统管理
(1, 0, '系统管理', 1, 'Setting', '/system', NULL, NULL, 1, 1),
(2, 1, '用户管理', 2, 'User', 'user', 'system/user/index', 'user:list', 1, 1),
(3, 2, '用户新增', 3, NULL, NULL, NULL, 'user:create', 1, 1),
(4, 2, '用户编辑', 3, NULL, NULL, NULL, 'user:update', 2, 1),
(5, 2, '用户删除', 3, NULL, NULL, NULL, 'user:delete', 3, 1),
(6, 1, '角色管理', 2, 'Role', 'role', 'system/role/index', 'role:list', 2, 1),
(7, 6, '角色新增', 3, NULL, NULL, NULL, 'role:create', 1, 1),
(8, 6, '角色编辑', 3, NULL, NULL, NULL, 'role:update', 2, 1),
(9, 6, '角色删除', 3, NULL, NULL, NULL, 'role:delete', 3, 1),
(10, 1, '菜单管理', 2, 'Menu', 'menu', 'system/menu/index', 'menu:list', 3, 1),
(11, 1, '操作日志', 2, 'Log', 'log', 'system/log/index', 'log:list', 4, 1),
-- 内容管理
(20, 0, '内容管理', 1, 'Document', '/content', NULL, NULL, 2, 1),
(21, 20, '文章管理', 2, 'Article', 'article', 'content/article/index', 'article:list', 1, 1),
(22, 21, '文章新增', 3, NULL, NULL, NULL, 'article:create', 1, 1),
(23, 21, '文章编辑', 3, NULL, NULL, NULL, 'article:update', 2, 1),
(24, 21, '文章删除', 3, NULL, NULL, NULL, 'article:delete', 3, 1),
(25, 20, '分类管理', 2, 'Category', 'category', 'content/category/index', 'category:list', 2, 1),
(26, 25, '分类新增', 3, NULL, NULL, NULL, 'category:create', 1, 1),
(27, 25, '分类编辑', 3, NULL, NULL, NULL, 'category:update', 2, 1),
(28, 25, '分类删除', 3, NULL, NULL, NULL, 'category:delete', 3, 1),
(29, 20, '标签管理', 2, 'Tag', 'tag', 'content/tag/index', 'tag:list', 3, 1),
(30, 20, '评论管理', 2, 'Comment', 'comment', 'content/comment/index', 'comment:list', 4, 1),
(31, 30, '评论审核', 3, NULL, NULL, NULL, 'comment:audit', 1, 1),
(32, 20, '轮播图管理', 2, 'Banner', 'banner', 'content/banner/index', 'banner:list', 5, 1),
(33, 20, '友链管理', 2, 'Link', 'friend', 'content/friend/index', 'friend:list', 6, 1),
-- 站点管理
(40, 0, '站点管理', 1, 'Site', '/site', NULL, NULL, 3, 1),
(41, 40, '站点配置', 2, 'Setting', 'config', 'site/config/index', 'site:config', 1, 1),
(42, 40, '文件管理', 2, 'File', 'file', 'site/file/index', 'file:list', 2, 1);

-- 为超级管理员分配所有菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 为管理员分配权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
(2, 11), (2, 20), (2, 21), (2, 22), (2, 23), (2, 24),
(2, 25), (2, 26), (2, 27), (2, 28), (2, 29), (2, 30), (2, 31),
(2, 32), (2, 33), (2, 40), (2, 41), (2, 42);

-- 为编辑分配权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 20), (3, 21), (3, 22), (3, 23), (3, 24),
(3, 25), (3, 26), (3, 27), (3, 28), (3, 29), (3, 30), (3, 31);

-- 为开发者分配权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 11), (4, 40), (4, 41), (4, 42);

-- 初始化站点配置
INSERT INTO site_config (config_key, config_value, default_value, description, config_type, is_system, is_public) VALUES
('site.name', 'Chen404 Blog', 'Chen404 Blog', '站点名称', 1, 1, 1),
('site.description', '一个热爱技术分享的博客', '一个热爱技术分享的博客', '站点描述', 1, 1, 1),
('site.logo', '/logo.svg', '/logo.svg', '站点Logo', 1, 1, 1),
('site.favicon', '/favicon.ico', '/favicon.ico', '站点图标', 1, 1, 1),
('site.icp', '', '', 'ICP备案号', 1, 0, 1),
('site.beian', '', '', '公安备案号', 1, 0, 1),
('site.github', 'https://github.com/chen404', '', 'GitHub链接', 1, 0, 1),
('site.email', 'admin@chen404.com', '', '联系邮箱', 1, 0, 1),
('site.copyright', 'Copyright 2024 Chen404', 'Copyright 2024 Chen404', '版权信息', 1, 1, 1),
('seo.keywords', '博客,技术,前端,后端,Java,Vue', '', 'SEO关键词', 1, 0, 1),
('seo.description', 'Chen404的个人技术博客，分享前后端开发经验', '', 'SEO描述', 1, 0, 1),
('comment.audit', 'true', 'true', '评论是否需要审核', 3, 1, 1),
('comment.guest', 'true', 'true', '是否允许游客评论', 3, 1, 1),
('article.pageSize', '10', '10', '文章每页数量', 2, 1, 1),
('upload.maxSize', '10485760', '10485760', '最大上传大小（字节）', 2, 1, 0),
('upload.allowTypes', '["jpg","jpeg","png","gif","webp","mp4"]', '["jpg","jpeg","png","gif","webp"]', '允许上传的文件类型', 4, 1, 0);

-- 初始化示例分类
INSERT INTO category (id, name, slug, description, icon, sort_order, status) VALUES
(1, '前端开发', 'frontend', '前端技术相关文章', 'icon-code', 1, 1),
(2, '后端开发', 'backend', '后端技术相关文章', 'icon-server', 2, 1),
(3, '数据库', 'database', '数据库技术分享', 'icon-database', 3, 1),
(4, '运维部署', 'devops', '服务器运维和部署', 'icon-cloud', 4, 1),
(5, '生活随笔', 'life', '生活感悟和随笔', 'icon-coffee', 5, 1);

-- 初始化示例标签
INSERT INTO tag (id, name, slug, color, sort_order, status) VALUES
(1, 'Vue', 'vue', '#42b883', 1, 1),
(2, 'React', 'react', '#61dafb', 2, 1),
(3, 'JavaScript', 'javascript', '#f7df1e', 3, 1),
(4, 'TypeScript', 'typescript', '#3178c6', 4, 1),
(5, 'Java', 'java', '#007396', 5, 1),
(6, 'Spring Boot', 'spring-boot', '#6db33f', 6, 1),
(7, 'MySQL', 'mysql', '#4479a1', 7, 1),
(8, 'Redis', 'redis', '#dc382d', 8, 1),
(9, 'Docker', 'docker', '#2496ed', 9, 1),
(10, 'Git', 'git', '#f05032', 10, 1);

-- 初始化示例轮播图
INSERT INTO banner (id, title, subtitle, image, link, position, sort_order, status) VALUES
(1, '欢迎来到 Chen404', '记录技术成长之路', '/banner/banner1.jpg', '/about', 1, 1, 1),
(2, '探索技术的无限可能', '分享前后端开发经验', '/banner/banner2.jpg', '/articles', 1, 2, 1),
(3, 'Spring Boot 3.0 新特性', '全面升级的性能体验', '/banner/banner3.jpg', '/article/1', 1, 3, 1);

-- ============================================
-- 10. 创建定时清理事件（可选）
-- ============================================

-- 启用事件调度器
SET GLOBAL event_scheduler = ON;

-- 创建定时清理验证码事件（每天凌晨执行）
DELIMITER //
CREATE EVENT IF NOT EXISTS cleanup_verification_code
ON SCHEDULE EVERY 1 DAY
STARTS TIMESTAMP(CURRENT_DATE, '03:00:00')
DO
BEGIN
    DELETE FROM sys_verification_code
    WHERE used = 1 OR expire_time < DATE_SUB(NOW(), INTERVAL 7 DAY);
END //
DELIMITER ;

-- ============================================
-- 11. 数据库说明
-- ============================================

-- 默认管理员账号：
-- 用户名: admin
-- 密码: admin123
-- 邮箱: admin@chen404.com

-- 注意事项：
-- 1. 所有表都包含 create_time 和 update_time 字段用于追踪
-- 2. 逻辑删除使用 deleted 字段（0=正常，1=删除）
-- 3. 不使用外键约束，通过应用层维护数据关联关系
-- 4. 全文搜索使用 ngram 解析器支持中文
-- 5. 索引设计考虑了常见查询场景
