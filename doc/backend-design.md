# Chen404 博客系统 - 后端设计文档

## 1. 技术选型

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.x | 后端框架 |
| JDK | 17+ | Java 版本 |
| MyBatis Plus | 3.5.x | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 7.x | 缓存/会话 |
| JWT | 0.12.x | 认证授权 |
| Maven | 3.9+ | 构建工具 |
| Druid | 1.2.x | 数据库连接池 |

## 2. 项目目录结构

```
Chen404Bac/
├── 📁 src/main/java/com/chen404/blog/
│   ├── 📁 config/          # 配置类
│   │   ├── CorsConfig.java         # 跨域配置
│   │   ├── SecurityConfig.java     # 安全配置
│   │   ├── RedisConfig.java        # Redis 配置
│   │   └── MybatisPlusConfig.java  # MyBatis Plus 配置
│   ├── 📁 controller/      # 控制层
│   │   ├── ArticleController.java
│   │   ├── CategoryController.java
│   │   ├── TagController.java
│   │   ├── CommentController.java
│   │   ├── UserController.java
│   │   └── AdminController.java
│   ├── 📁 service/         # 业务层
│   │   ├── impl/           # 业务实现
│   │   └── interfaces/     # 业务接口
│   ├── 📁 mapper/          # 数据访问层
│   ├── 📁 entity/          # 实体类
│   ├── 📁 dto/             # 数据传输对象
│   │   ├── request/        # 请求 DTO
│   │   └── response/       # 响应 DTO
│   ├── 📁 vo/              # 视图对象
│   ├── 📁 enums/           # 枚举类
│   ├── 📁 utils/           # 工具类
│   │   ├── JwtUtil.java
│   │   ├── Result.java
│   │   └── RedisUtil.java
│   ├── 📁 interceptor/     # 拦截器
│   ├── 📁 aspect/          # AOP 切面
│   ├── 📁 common/          # 公共常量/异常
│   └── BlogApplication.java
├── 📁 src/main/resources/
│   ├── 📁 mapper/          # MyBatis XML
│   ├── application.yml     # 主配置
│   ├── application-dev.yml # 开发环境
│   └── application-prod.yml# 生产环境
├── 📁 src/test/            # 测试代码
└── pom.xml
```

## 3. 数据库设计

### 3.1 用户表 (sys_user)
```sql
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    avatar VARCHAR(255) COMMENT '头像URL',
    role TINYINT DEFAULT 0 COMMENT '角色：0-普通用户 1-管理员',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 3.2 文章表 (article)
```sql
CREATE TABLE article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    summary VARCHAR(500) COMMENT '摘要',
    content LONGTEXT COMMENT '内容 (Markdown)',
    cover_image VARCHAR(255) COMMENT '封面图',
    author_id BIGINT COMMENT '作者ID',
    category_id BIGINT COMMENT '分类ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0-草稿 1-已发布 2-回收站',
    view_count INT DEFAULT 0 COMMENT '浏览量',
    comment_count INT DEFAULT 0 COMMENT '评论数',
    is_top TINYINT DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
    is_recommend TINYINT DEFAULT 0 COMMENT '是否推荐：0-否 1-是',
    publish_time DATETIME COMMENT '发布时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_category (category_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';
```

### 3.3 分类表 (category)
```sql
CREATE TABLE category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    slug VARCHAR(50) NOT NULL UNIQUE COMMENT '别名',
    description VARCHAR(200) COMMENT '描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';
```

### 3.4 标签表 (tag)
```sql
CREATE TABLE tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    slug VARCHAR(50) NOT NULL UNIQUE COMMENT '别名',
    color VARCHAR(20) DEFAULT '#fb7299' COMMENT '标签颜色',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';
```

### 3.5 文章标签关联表 (article_tag)
```sql
CREATE TABLE article_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT NOT NULL COMMENT '文章ID',
    tag_id BIGINT NOT NULL COMMENT '标签ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_tag (article_id, tag_id),
    INDEX idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';
```

### 3.6 评论表 (comment)
```sql
CREATE TABLE comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评论ID',
    article_id BIGINT COMMENT '文章ID (null表示留言板)',
    parent_id BIGINT DEFAULT 0 COMMENT '父评论ID (0表示顶级)',
    content TEXT NOT NULL COMMENT '评论内容',
    author_name VARCHAR(50) COMMENT '评论者名称',
    author_email VARCHAR(100) COMMENT '评论者邮箱',
    author_website VARCHAR(200) COMMENT '评论者网站',
    author_ip VARCHAR(50) COMMENT 'IP地址',
    status TINYINT DEFAULT 1 COMMENT '状态：0-待审核 1-已通过 2-已拒绝',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_article (article_id),
    INDEX idx_parent (parent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';
```

### 3.7 友链表 (friend_link)
```sql
CREATE TABLE friend_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '友链ID',
    site_name VARCHAR(100) NOT NULL COMMENT '站点名称',
    site_url VARCHAR(255) NOT NULL COMMENT '站点链接',
    site_logo VARCHAR(255) COMMENT '站点Logo',
    description VARCHAR(200) COMMENT '站点描述',
    email VARCHAR(100) COMMENT '联系邮箱',
    status TINYINT DEFAULT 0 COMMENT '状态：0-待审核 1-已通过',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='友链表';
```

### 3.8 站点配置表 (site_config)
```sql
CREATE TABLE site_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(200) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点配置表';
```

## 4. API 接口设计

### 4.1 通用响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 4.2 文章接口

#### 获取文章列表
```
GET /api/articles
Query: page=1&size=10&category=&tag=&keyword=
Response:
{
  "code": 200,
  "data": {
    "list": [...],
    "total": 100,
    "page": 1,
    "size": 10
  }
}
```

#### 获取文章详情
```
GET /api/articles/{id}
Response:
{
  "code": 200,
  "data": {
    "id": 1,
    "title": "...",
    "content": "...",
    "category": {...},
    "tags": [...],
    "prev": {...},
    "next": {...}
  }
}
```

#### 创建文章 (Admin)
```
POST /api/admin/articles
Body:
{
  "title": "...",
  "content": "...",
  "categoryId": 1,
  "tagIds": [1, 2, 3],
  "status": 1
}
```

### 4.3 分类接口
```
GET    /api/categories           # 获取分类列表
GET    /api/categories/{id}      # 获取分类详情
POST   /api/admin/categories     # 创建分类 (Admin)
PUT    /api/admin/categories/{id}# 更新分类 (Admin)
DELETE /api/admin/categories/{id}# 删除分类 (Admin)
```

### 4.4 标签接口
```
GET    /api/tags                  # 获取标签列表
GET    /api/tags/{id}             # 获取标签详情
POST   /api/admin/tags            # 创建标签 (Admin)
PUT    /api/admin/tags/{id}       # 更新标签 (Admin)
DELETE /api/admin/tags/{id}       # 删除标签 (Admin)
```

### 4.5 评论接口
```
GET    /api/comments?articleId=1  # 获取评论列表
POST   /api/comments              # 发表评论
GET    /api/admin/comments        # 管理评论列表 (Admin)
PUT    /api/admin/comments/{id}   # 审核评论 (Admin)
DELETE /api/admin/comments/{id}   # 删除评论 (Admin)
```

### 4.6 用户认证接口
```
POST /api/auth/login              # 登录
POST /api/auth/register           # 注册
POST /api/auth/logout             # 登出
GET  /api/auth/info               # 获取当前用户信息
POST /api/auth/refresh            # 刷新 Token
```

## 5. 安全配置

### 5.1 JWT 配置
```yaml
jwt:
  secret: your-secret-key
  expiration: 86400000  # 24小时
  header: Authorization
  prefix: Bearer
```

### 5.2 跨域配置
```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        // ...
    }
}
```

### 5.3 接口权限
| 接口路径 | 权限要求 |
|----------|----------|
| /api/** | 公开访问 |
| /api/admin/** | 需管理员权限 |
| /api/auth/** | 公开访问 |

## 6. 缓存策略

### 6.1 Redis 缓存
| 缓存键 | 过期时间 | 说明 |
|--------|----------|------|
| article:hot | 1小时 | 热门文章 |
| article:{id} | 30分钟 | 文章详情 |
| category:list | 24小时 | 分类列表 |
| tag:list | 24小时 | 标签列表 |
| view:{articleId} | - | 文章浏览量计数 |

### 6.2 缓存更新策略
- 文章更新时删除对应缓存
- 定时任务刷新热门文章缓存
- 发布/删除文章时清除列表缓存

## 7. 配置文件

### 7.1 application.yml
```yaml
server:
  port: 8080

spring:
  application:
    name: Chen404Bac

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/chen404?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your-password
    type: com.alibaba.druid.pool.DruidDataSource

  redis:
    host: localhost
    port: 6379
    password:
    database: 0

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  mapper-locations: classpath:/mapper/**/*.xml

jwt:
  secret: chen404-blog-secret-key
  expiration: 86400000

upload:
  path: /uploads/
  max-size: 10485760  # 10MB
```

## 8. 实体类设计

### 8.1 基础实体 (BaseEntity)
```java
@Data
public class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
```

### 8.2 文章实体 (Article)
```java
@Data
@TableName("article")
public class Article extends BaseEntity {
    private String title;
    private String summary;
    private String content;
    private String coverImage;
    private Long authorId;
    private Long categoryId;
    private Integer status;
    private Integer viewCount;
    private Integer commentCount;
    private Integer isTop;
    private Integer isRecommend;
    private LocalDateTime publishTime;

    @TableField(exist = false)
    private List<Tag> tags;

    @TableField(exist = false)
    private Category category;
}
```

## 9. Maven 依赖 (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.chen404</groupId>
    <artifactId>chen404-blog</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Druid -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>1.2.20</version>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.3</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

## 10. 接口安全设计

### 10.1 JWT 认证流程
```
1. 用户登录 -> 验证用户名密码
2. 生成 JWT Token (AccessToken + RefreshToken)
3. 前端存储 Token (localStorage)
4. 请求时携带 Authorization: Bearer {token}
5. 后端验证 Token 有效性
6. Token 过期 -> 使用 RefreshToken 换取新 Token
```

### 10.2 接口限流
```java
// 基于 Redis 的限流
@RateLimiter(key = "api:article:list", limit = 100, period = 60) // 60秒内100次
@GetMapping("/api/articles")
public Result list(...) { }
```

### 10.3 SQL 注入防护
- 使用 MyBatis Plus 参数绑定
- 禁止直接拼接 SQL

---
*文档版本: 1.0*
*创建日期: 2026-03-15*
