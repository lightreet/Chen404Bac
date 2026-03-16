# Chen404 博客系统 - 数据库设计文档

> 基于 MySQL 8.0+ 设计，支持多角色权限系统

---

## 1. 数据库概述

| 项目 | 内容 |
|------|------|
| 数据库名 | `chen404` |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_unicode_ci` |
| 存储引擎 | `InnoDB` |
| 时区 | `Asia/Shanghai` |
| 外键约束 | **无物理外键**（应用层维护关联关系） |

### 1.1 无物理外键设计说明

本数据库设计采用**无物理外键**策略，表间关联仅通过字段（如 `user_id`, `role_id`）表示，不在数据库层面设置 FOREIGN KEY 约束。

**设计优势**：

| 优势 | 说明 |
|------|------|
| 性能优化 | 避免外键检查开销，提升写入性能 20%-40% |
| 水平扩展 | 支持分库分表，不受外键跨库限制 |
| 数据安全 | 避免级联删除导致的意外数据丢失 |
| 维护灵活 | 便于数据迁移、归档和清理 |
| 并发优化 | 减少锁竞争，提高并发写入性能 |

**数据一致性保障**（应用层实现）：

```java
// 1. 事务控制保证一致性
@Transactional
public void deleteUser(Long userId) {
    // 先删除关联数据
    userRoleMapper.deleteByUserId(userId);
    // 再删除主数据
    userMapper.deleteById(userId);
}

// 2. 关联数据有效性校验
public void createArticle(Article article) {
    // 校验作者存在
    if (userMapper.selectById(article.getAuthorId()) == null) {
        throw new BusinessException("作者不存在");
    }
}
```

---

## 2. 表结构概览

| 表名 | 说明 | 所属模块 |
|------|------|----------|
| sys_user | 用户表 | 系统管理 |
| sys_role | 角色表 | 系统管理 |
| sys_user_role | 用户角色关联表 | 系统管理 |
| sys_menu | 菜单/权限表 | 系统管理 |
| sys_role_menu | 角色菜单关联表 | 系统管理 |
| sys_operation_log | 操作日志表 | 系统管理 |
| sys_login_log | 登录日志表 | 系统管理 |
| sys_file | 文件上传记录表 | 系统管理 |
| sys_verification_code | 验证码记录表 | 系统管理 |
| article | 文章表 | 内容管理 |
| category | 分类表 | 内容管理 |
| tag | 标签表 | 内容管理 |
| article_tag | 文章标签关联表 | 内容管理 |
| comment | 评论表 | 内容管理 |
| friend_link | 友链表 | 内容管理 |
| banner | 轮播图表 | 内容管理 |
| site_config | 站点配置表 | 站点管理 |
| view_log | 文章浏览记录表 | 数据统计 |

---

## 3. 详细表结构

### 3.1 用户权限模块

#### sys_user（用户表）

存储系统所有用户信息，支持多种登录方式。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| username | VARCHAR | 50 | 否 | - | 用户名（唯一） |
| password | VARCHAR | 255 | 否 | - | 密码（BCrypt加密） |
| nickname | VARCHAR | 50 | 是 | - | 昵称 |
| email | VARCHAR | 100 | 是 | - | 邮箱（唯一） |
| phone | VARCHAR | 20 | 是 | - | 手机号（唯一） |
| avatar | VARCHAR | 500 | 是 | default-avatar.jpg | 头像URL |
| bio | VARCHAR | 500 | 是 | - | 个人简介 |
| status | TINYINT | - | 否 | 1 | 状态：0-禁用 1-启用 |
| email_verified | TINYINT | - | 否 | 0 | 邮箱是否验证：0-否 1-是 |
| phone_verified | TINYINT | - | 否 | 0 | 手机是否验证：0-否 1-是 |
| last_login_time | DATETIME | - | 是 | - | 最后登录时间 |
| last_login_ip | VARCHAR | 50 | 是 | - | 最后登录IP |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除：0-正常 1-删除 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_username` (`username`)
- UNIQUE KEY `uk_email` (`email`)
- UNIQUE KEY `uk_phone` (`phone`)
- INDEX `idx_status` (`status`)

---

#### sys_role（角色表）

定义系统角色，支持多角色权限管理。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| role_name | VARCHAR | 50 | 否 | - | 角色名称 |
| role_code | VARCHAR | 50 | 否 | - | 角色编码（唯一） |
| description | VARCHAR | 200 | 是 | - | 角色描述 |
| sort_order | INT | - | 否 | 0 | 排序号 |
| status | TINYINT | - | 否 | 1 | 状态：0-禁用 1-启用 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |

**预设角色**:
| role_code | role_name | 说明 |
|-----------|-----------|------|
| super_admin | 超级管理员 | 拥有所有权限 |
| admin | 管理员 | 内容管理权限 |
| editor | 编辑 | 文章管理权限 |
| developer | 开发者 | 技术相关权限 |
| user | 普通用户 | 基础权限 |
| guest | 访客 | 只读权限 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_role_code` (`role_code`)

---

#### sys_user_role（用户角色关联表）

多对多关系，一个用户可拥有多个角色。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| user_id | BIGINT | - | 否 | - | 用户ID |
| role_id | BIGINT | - | 否 | - | 角色ID |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
- INDEX `idx_role_id` (`role_id`)

---

#### sys_menu（菜单/权限表）

存储菜单和按钮权限，支持多级菜单。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| parent_id | BIGINT | - | 否 | 0 | 父菜单ID（0为顶级） |
| menu_name | VARCHAR | 50 | 否 | - | 菜单名称 |
| menu_type | TINYINT | - | 否 | 1 | 类型：1-目录 2-菜单 3-按钮 |
| icon | VARCHAR | 100 | 是 | - | 菜单图标 |
| path | VARCHAR | 200 | 是 | - | 路由路径 |
| component | VARCHAR | 200 | 是 | - | 组件路径 |
| permission | VARCHAR | 100 | 是 | - | 权限标识（如：article:list） |
| sort_order | INT | - | 否 | 0 | 排序号 |
| status | TINYINT | - | 否 | 1 | 状态：0-禁用 1-启用 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_parent_id` (`parent_id`)
- INDEX `idx_type` (`menu_type`)

---

#### sys_role_menu（角色菜单关联表）

定义角色拥有的菜单权限。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| role_id | BIGINT | - | 否 | - | 角色ID |
| menu_id | BIGINT | - | 否 | - | 菜单ID |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
- INDEX `idx_menu_id` (`menu_id`)

---

### 3.2 日志模块

#### sys_operation_log（操作日志表）

记录用户重要操作，用于审计。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| user_id | BIGINT | - | 是 | - | 操作用户ID |
| username | VARCHAR | 50 | 是 | - | 操作用户名 |
| operation | VARCHAR | 100 | 否 | - | 操作描述 |
| method | VARCHAR | 200 | 是 | - | 请求方法 |
| request_url | VARCHAR | 500 | 是 | - | 请求URL |
| request_params | TEXT | - | 是 | - | 请求参数 |
| response_data | TEXT | - | 是 | - | 响应数据（可选） |
| ip | VARCHAR | 50 | 是 | - | IP地址 |
| user_agent | VARCHAR | 500 | 是 | - | 浏览器UA |
| execute_time | INT | - | 是 | - | 执行时长（毫秒） |
| status | TINYINT | - | 否 | 1 | 状态：0-失败 1-成功 |
| error_msg | TEXT | - | 是 | - | 错误信息 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_user_id` (`user_id`)
- INDEX `idx_create_time` (`create_time`)

---

#### sys_login_log（登录日志表）

记录用户登录行为，用于安全审计。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| user_id | BIGINT | - | 是 | - | 用户ID |
| username | VARCHAR | 50 | 否 | - | 登录账号 |
| login_type | TINYINT | - | 否 | 1 | 登录类型：1-账号密码 2-邮箱 3-手机 |
| ip | VARCHAR | 50 | 是 | - | IP地址 |
| location | VARCHAR | 100 | 是 | - | 登录地点 |
| user_agent | VARCHAR | 500 | 是 | - | 浏览器UA |
| browser | VARCHAR | 50 | 是 | - | 浏览器类型 |
| os | VARCHAR | 50 | 是 | - | 操作系统 |
| status | TINYINT | - | 否 | 1 | 状态：0-失败 1-成功 |
| error_msg | VARCHAR | 200 | 是 | - | 失败原因 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_user_id` (`user_id`)
- INDEX `idx_username` (`username`)
- INDEX `idx_create_time` (`create_time`)

---

### 3.3 文件与验证码模块

#### sys_file（文件上传记录表）

管理用户上传的文件。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| file_name | VARCHAR | 255 | 否 | - | 原始文件名 |
| file_original_name | VARCHAR | 255 | 否 | - | 原文件名 |
| file_suffix | VARCHAR | 20 | 是 | - | 文件后缀 |
| file_url | VARCHAR | 500 | 否 | - | 文件访问URL |
| file_path | VARCHAR | 500 | 否 | - | 文件存储路径 |
| file_size | BIGINT | - | 否 | 0 | 文件大小（字节） |
| file_type | VARCHAR | 50 | 是 | - | 文件MIME类型 |
| module | VARCHAR | 50 | 是 | - | 所属模块：avatar/article/comment等 |
| user_id | BIGINT | - | 是 | - | 上传用户ID |
| download_count | INT | - | 否 | 0 | 下载次数 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_user_id` (`user_id`)
- INDEX `idx_module` (`module`)

---

#### sys_verification_code（验证码记录表）

存储短信/邮箱验证码，用于注册、登录、重置密码。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| target | VARCHAR | 100 | 否 | - | 目标（邮箱或手机号） |
| code | VARCHAR | 10 | 否 | - | 验证码 |
| type | TINYINT | - | 否 | - | 类型：1-注册 2-登录 3-重置密码 |
| scene | TINYINT | - | 否 | 1 | 场景：1-邮箱 2-短信 |
| expire_time | DATETIME | - | 否 | - | 过期时间 |
| used | TINYINT | - | 否 | 0 | 是否已使用：0-否 1-是 |
| use_time | DATETIME | - | 是 | - | 使用时间 |
| ip | VARCHAR | 50 | 是 | - | 请求IP |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_target_type` (`target`, `type`)
- INDEX `idx_create_time` (`create_time`)

---

### 3.4 内容管理模块

#### article（文章表）

博客核心表，存储文章内容。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| title | VARCHAR | 200 | 否 | - | 文章标题 |
| summary | VARCHAR | 500 | 是 | - | 文章摘要 |
| content | LONGTEXT | - | 是 | - | 文章内容（Markdown） |
| content_html | LONGTEXT | - | 是 | - | HTML内容（渲染后缓存） |
| cover_image | VARCHAR | 500 | 是 | - | 封面图URL |
| author_id | BIGINT | - | 否 | - | 作者ID |
| category_id | BIGINT | - | 是 | - | 分类ID |
| status | TINYINT | - | 否 | 0 | 状态：0-草稿 1-已发布 2-回收站 |
| view_count | INT | - | 否 | 0 | 浏览量 |
| like_count | INT | - | 否 | 0 | 点赞数 |
| comment_count | INT | - | 否 | 0 | 评论数 |
| is_top | TINYINT | - | 否 | 0 | 是否置顶：0-否 1-是 |
| is_recommend | TINYINT | - | 否 | 0 | 是否推荐：0-否 1-是 |
| is_original | TINYINT | - | 否 | 1 | 是否原创：0-转载 1-原创 |
| original_url | VARCHAR | 500 | 是 | - | 原文链接（转载时） |
| password | VARCHAR | 100 | 是 | - | 访问密码（私密文章） |
| publish_time | DATETIME | - | 是 | - | 发布时间 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_author_id` (`author_id`)
- INDEX `idx_category_id` (`category_id`)
- INDEX `idx_status` (`status`)
- INDEX `idx_is_top` (`is_top`)
- INDEX `idx_is_recommend` (`is_recommend`)
- INDEX `idx_publish_time` (`publish_time`)
- INDEX `idx_create_time` (`create_time`)
- FULLTEXT INDEX `ft_title_content` (`title`, `content`) WITH PARSER ngram

---

#### category（分类表）

文章分类管理。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| name | VARCHAR | 50 | 否 | - | 分类名称 |
| slug | VARCHAR | 50 | 否 | - | 别名（URL用） |
| description | VARCHAR | 200 | 是 | - | 分类描述 |
| icon | VARCHAR | 100 | 是 | - | 图标 |
| sort_order | INT | - | 否 | 0 | 排序号 |
| article_count | INT | - | 否 | 0 | 文章数量（冗余） |
| status | TINYINT | - | 否 | 1 | 状态：0-禁用 1-启用 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_slug` (`slug`)

---

#### tag（标签表）

文章标签管理。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| name | VARCHAR | 50 | 否 | - | 标签名称 |
| slug | VARCHAR | 50 | 否 | - | 别名（URL用） |
| color | VARCHAR | 20 | 否 | #fb7299 | 标签颜色 |
| sort_order | INT | - | 否 | 0 | 排序号 |
| article_count | INT | - | 否 | 0 | 文章数量（冗余） |
| status | TINYINT | - | 否 | 1 | 状态：0-禁用 1-启用 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_slug` (`slug`)

---

#### article_tag（文章标签关联表）

文章与标签的多对多关系。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| article_id | BIGINT | - | 否 | - | 文章ID |
| tag_id | BIGINT | - | 否 | - | 标签ID |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`)
- INDEX `idx_tag_id` (`tag_id`)

---

#### comment（评论表）

支持文章评论和留言板（article_id为null）。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| article_id | BIGINT | - | 是 | - | 文章ID（null为留言板） |
| parent_id | BIGINT | - | 否 | 0 | 父评论ID（0为顶级） |
| root_id | BIGINT | - | 否 | 0 | 根评论ID（用于嵌套展示） |
| content | TEXT | - | 否 | - | 评论内容 |
| author_name | VARCHAR | 50 | 否 | - | 评论者名称 |
| author_email | VARCHAR | 100 | 是 | - | 评论者邮箱 |
| author_website | VARCHAR | 200 | 是 | - | 评论者网站 |
| author_avatar | VARCHAR | 500 | 是 | - | 评论者头像 |
| author_id | BIGINT | - | 是 | - | 注册用户ID（游客为null） |
| ip | VARCHAR | 50 | 是 | - | IP地址 |
| location | VARCHAR | 100 | 是 | - | 归属地 |
| user_agent | VARCHAR | 500 | 是 | - | UA信息 |
| status | TINYINT | - | 否 | 0 | 状态：0-待审核 1-已通过 2-已拒绝 |
| is_admin | TINYINT | - | 否 | 0 | 是否管理员回复：0-否 1-是 |
| like_count | INT | - | 否 | 0 | 点赞数 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_article_id` (`article_id`)
- INDEX `idx_parent_id` (`parent_id`)
- INDEX `idx_root_id` (`root_id`)
- INDEX `idx_author_id` (`author_id`)
- INDEX `idx_status` (`status`)
- INDEX `idx_create_time` (`create_time`)

---

#### friend_link（友链表）

管理友链申请和展示。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| site_name | VARCHAR | 100 | 否 | - | 站点名称 |
| site_url | VARCHAR | 500 | 否 | - | 站点链接 |
| site_logo | VARCHAR | 500 | 是 | - | 站点Logo |
| description | VARCHAR | 500 | 是 | - | 站点描述 |
| email | VARCHAR | 100 | 是 | - | 联系邮箱 |
| owner_name | VARCHAR | 50 | 是 | - | 站长名称 |
| sort_order | INT | - | 否 | 0 | 排序号 |
| status | TINYINT | - | 否 | 0 | 状态：0-待审核 1-已通过 2-已拒绝 |
| reject_reason | VARCHAR | 200 | 是 | - | 拒绝原因 |
| view_count | INT | - | 否 | 0 | 点击次数 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_status` (`status`)
- INDEX `idx_sort_order` (`sort_order`)

---

#### banner（轮播图表）

首页轮播图管理。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| title | VARCHAR | 100 | 否 | - | 标题 |
| subtitle | VARCHAR | 200 | 是 | - | 副标题 |
| image | VARCHAR | 500 | 否 | - | 图片URL |
| link | VARCHAR | 500 | 是 | - | 链接地址 |
| target | TINYINT | - | 否 | 1 | 打开方式：1-当前页 2-新窗口 |
| position | TINYINT | - | 否 | 1 | 位置：1-首页 2-文章页 |
| background_color | VARCHAR | 20 | 是 | - | 背景色 |
| text_color | VARCHAR | 20 | 是 | - | 文字颜色 |
| sort_order | INT | - | 否 | 0 | 排序号 |
| status | TINYINT | - | 否 | 1 | 状态：0-禁用 1-启用 |
| start_time | DATETIME | - | 是 | - | 开始时间 |
| end_time | DATETIME | - | 是 | - | 结束时间 |
| view_count | INT | - | 否 | 0 | 点击次数 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | - | 否 | 0 | 逻辑删除 |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_position` (`position`)
- INDEX `idx_status` (`status`)
- INDEX `idx_sort_order` (`sort_order`)

---

### 3.5 站点管理模块

#### site_config（站点配置表）

存储站点基础配置信息。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| config_key | VARCHAR | 100 | 否 | - | 配置键（唯一） |
| config_value | TEXT | - | 是 | - | 配置值 |
| default_value | TEXT | - | 是 | - | 默认值 |
| description | VARCHAR | 200 | 是 | - | 配置描述 |
| config_type | TINYINT | - | 否 | 1 | 类型：1-文本 2-数字 3-布尔 4-JSON |
| is_system | TINYINT | - | 否 | 0 | 是否系统配置：0-否 1-是 |
| is_public | TINYINT | - | 否 | 1 | 是否公开：0-私密 1-公开 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 更新时间 |

**预设配置项**:
| config_key | 说明 |
|------------|------|
| site.name | 站点名称 |
| site.description | 站点描述 |
| site.logo | 站点Logo |
| site.favicon | 站点图标 |
| site.icp | ICP备案号 |
| site.github | GitHub链接 |
| site.email | 联系邮箱 |
| site.beian | 公安备案号 |
| seo.keywords | SEO关键词 |
| seo.description | SEO描述 |
| comment.audit | 评论是否需要审核 |
| comment.guest | 是否允许游客评论 |

**索引**:
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_config_key` (`config_key`)

---

### 3.6 数据统计模块

#### view_log（文章浏览记录表）

记录文章浏览详情，用于统计和防刷。

| 字段名 | 类型 | 长度 | 是否为空 | 默认值 | 说明 |
|--------|------|------|----------|--------|------|
| id | BIGINT | - | 否 | AUTO_INCREMENT | 主键 |
| article_id | BIGINT | - | 否 | - | 文章ID |
| user_id | BIGINT | - | 是 | - | 用户ID（游客为null） |
| ip | VARCHAR | 50 | 否 | - | IP地址 |
| user_agent | VARCHAR | 500 | 是 | - | UA信息 |
| referer | VARCHAR | 500 | 是 | - | 来源页面 |
| create_time | DATETIME | - | 否 | CURRENT_TIMESTAMP | 创建时间 |
| create_date | DATE | - | 否 | CURRENT_DATE | 创建日期（冗余，便于统计） |

**索引**:
- PRIMARY KEY (`id`)
- INDEX `idx_article_id` (`article_id`)
- INDEX `idx_user_id` (`user_id`)
- INDEX `idx_create_time` (`create_time`)
- INDEX `idx_create_date` (`create_date`)

---

## 4. 角色权限设计

### 4.1 角色权限矩阵

| 功能 | super_admin | admin | editor | developer | user | guest |
|------|:-----------:|:-----:|:------:|:---------:|:----:|:-----:|
| 文章管理 | ✓ | ✓ | ✓ | - | - | - |
| 分类管理 | ✓ | ✓ | ✓ | - | - | - |
| 标签管理 | ✓ | ✓ | ✓ | - | - | - |
| 评论审核 | ✓ | ✓ | ✓ | - | - | - |
| 友链管理 | ✓ | ✓ | - | - | - | - |
| 用户管理 | ✓ | ✓ | - | - | - | - |
| 角色管理 | ✓ | - | - | - | - | - |
| 菜单管理 | ✓ | - | - | - | - | - |
| 系统配置 | ✓ | ✓ | - | ✓ | - | - |
| 日志查看 | ✓ | ✓ | - | ✓ | - | - |
| 文件管理 | ✓ | ✓ | ✓ | - | - | - |
| 文章查看 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 发表评论 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 个人中心 | ✓ | ✓ | ✓ | ✓ | ✓ | - |

### 4.2 权限标识设计

```
article:list      - 文章列表
article:detail    - 文章详情
article:create    - 创建文章
article:update    - 更新文章
article:delete    - 删除文章
category:list     - 分类列表
category:manage   - 分类管理
tag:list          - 标签列表
tag:manage        - 标签管理
comment:list      - 评论列表
comment:audit     - 评论审核
user:list         - 用户列表
user:manage       - 用户管理
role:list         - 角色列表
role:manage       - 角色管理
system:config     - 系统配置
system:log        - 日志查看
```

---

## 5. 数据库优化建议

### 5.1 索引优化

1. **全文搜索**: 文章表标题和内容使用 `FULLTEXT` 索引配合 `ngram` 解析器支持中文搜索
2. **时间范围查询**: 频繁按时间查询的表建立时间索引
3. **组合索引**: 多条件查询使用组合索引（如评论表的 article_id + status）

### 5.2 分区建议

对于以下大表建议按时间分区:
- `sys_operation_log` - 按月分区
- `sys_login_log` - 按月分区
- `view_log` - 按日分区

### 5.3 归档策略

1. **日志表**: 保留最近6个月数据，历史数据归档到备份表
2. **浏览记录**: 保留最近3个月数据，用于统计的数据提前汇总
3. **验证码**: 已使用和过期数据定期清理（保留7天）

---

## 6. 实体关系图

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────┐
│  sys_user   │────<│  sys_user_role  │>────│  sys_role   │
└──────┬──────┘     └─────────────────┘     └──────┬──────┘
       │                                            │
       │       ┌─────────────────┐                  │
       └──────>│ sys_login_log   │                  │
               └─────────────────┘                  │
                                                    │
                              ┌─────────────────┐   │
                              │ sys_role_menu   │<──┘
                              └─────────────────┘
                                     │
                                     ▼
                              ┌─────────────┐
                              │  sys_menu   │
                              └─────────────┘

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   article   │────<│ article_tag │>────│     tag     │
└──────┬──────┘     └─────────────┘     └─────────────┘
       │
       │>────┐
       │      │
       ▼      ▼
┌─────────────┐     ┌─────────────┐
│  category   │     │   comment   │
└─────────────┘     └─────────────┘

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   banner    │     │ friend_link │     │ site_config │
└─────────────┘     └─────────────┘     └─────────────┘
```

> 注：以上为逻辑关联关系图，数据库层面无物理外键约束，关联关系通过应用层维护。

---

## 7. 表关联关系汇总（逻辑关联）

| 主表 | 从表 | 关联字段 | 关联类型 | 处理策略 |
|------|------|----------|----------|----------|
| sys_user | sys_user_role | user_id | 一对多 | 应用层先删从表 |
| sys_role | sys_user_role | role_id | 一对多 | 应用层先删从表 |
| sys_role | sys_role_menu | role_id | 一对多 | 应用层先删从表 |
| sys_menu | sys_role_menu | menu_id | 一对多 | 应用层先删从表 |
| sys_user | sys_file | user_id | 一对多 | 逻辑删除保留 |
| sys_user | article | author_id | 一对多 | 限制删除（有文章不能删） |
| category | article | category_id | 一对多 | 置空允许 |
| article | article_tag | article_id | 一对多 | 级联删除 |
| tag | article_tag | tag_id | 一对多 | 级联删除 |
| article | comment | article_id | 一对多 | 级联删除 |
| sys_user | comment | author_id | 一对多 | 置空保留 |
| article | view_log | article_id | 一对多 | 级联删除 |
| sys_user | view_log | user_id | 一对多 | 置空保留 |

---

*文档版本: 1.0*
*最后更新: 2026-03-16*
