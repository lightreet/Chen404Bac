# Chen404 后端架构设计

## 1. 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                         前端层                               │
│                   (Vue3 + Vite + TS)                        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                        控制层 (Controller)                  │
│  AuthController | ArticleController | CommentController     │
│  SiteController | UploadController  | AdminController       │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                        业务层 (Service)                     │
│  业务接口 + 业务实现 (impl)                                  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                        数据层 (Mapper)                      │
│  MyBatis Plus 数据访问                                       │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                        数据存储                              │
│  MySQL (主存储) | Redis (缓存) | MinIO (文件存储)            │
└─────────────────────────────────────────────────────────────┘
```

## 2. 技术选型

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 基础框架 | Spring Boot | 3.2.x | 核心框架 |
| JDK | Java | 17+ | 运行环境 |
| ORM | MyBatis Plus | 3.5.x | 数据访问 |
| 数据库 | MySQL | 8.0+ | 关系型数据库 |
| 缓存 | Redis | 7.x | 缓存/会话 |
| 安全 | Spring Security + JWT | - | 认证授权 |
| 连接池 | Druid | 1.2.x | 数据库连接池 |
| 文件存储 | MinIO | - | 对象存储 |
| 构建工具 | Maven | 3.9+ | 依赖管理 |

## 3. 项目结构

```
src/main/java/com/chen404/blog/
├── config/                    # 配置类
│   ├── SecurityConfig.java    # 安全配置
│   ├── CorsConfig.java        # 跨域配置
│   ├── RedisConfig.java       # Redis配置
│   ├── MinIOConfig.java       # MinIO配置
│   └── MybatisPlusConfig.java # MyBatis Plus配置
├── controller/                # 控制层
│   ├── AuthController.java    # 认证接口
│   ├── ArticleController.java # 文章接口
│   ├── SiteController.java    # 站点接口
│   └── AdminController.java   # 管理接口
├── service/                   # 业务层
│   ├── interfaces/            # 业务接口
│   └── impl/                  # 业务实现
├── mapper/                    # 数据访问层
├── domain/                    # 领域模型
│   ├── entity/                # 实体类
│   ├── dto/                   # 数据传输对象
│   │   ├── request/           # 请求DTO
│   │   └── response/          # 响应DTO
│   ├── vo/                    # 视图对象
│   └── common/                # 通用结果封装
│       ├── Result.java        # 统一响应
│       └── PageResult.java    # 分页响应
├── interceptor/               # 拦截器
│   ├── JwtInterceptor.java    # JWT认证拦截
│   └── LogInterceptor.java    # 日志拦截
├── exception/                 # 异常处理
│   └── GlobalExceptionHandler.java
├── util/                      # 工具类
│   ├── JwtUtil.java           # JWT工具
│   └── RedisUtil.java         # Redis工具
└── Chen404Application.java    # 启动类
```

## 4. 核心设计

### 4.1 统一响应格式

```java
{
  "code": 200,           // 状态码
  "message": "success",  // 消息
  "data": {}             // 数据
}
```

状态码定义：
- 200: 成功
- 400: 参数错误
- 401: 未授权
- 403: 禁止访问
- 404: 资源不存在
- 500: 服务器错误

### 4.2 分层架构

| 层级 | 职责 | 命名规范 |
|------|------|----------|
| Controller | 接收请求、参数校验、调用Service | XxxController |
| Service | 业务逻辑、事务控制 | XxxService / XxxServiceImpl |
| Mapper | 数据访问 | XxxMapper |
| Entity | 数据库实体 | Xxx |
| DTO | 数据传输 | XxxDTO / XxxRequest / XxxResponse |
| VO | 视图对象 | XxxVO |

### 4.3 数据库设计原则

- **无物理外键**：通过应用层维护关联关系，提升性能
- **逻辑删除**：所有表使用 `deleted` 字段进行软删除
- **时间追踪**：所有表包含 `create_time` 和 `update_time`
- **索引优化**：常用查询字段建立索引

## 5. 安全设计

### 5.1 JWT 认证

```
1. 用户登录 → 验证用户名密码
2. 生成 JWT Token (AccessToken + RefreshToken)
3. 前端存储 Token
4. 请求时携带 Authorization: Bearer {token}
5. 后端验证 Token 有效性
```

配置：
```yaml
jwt:
  secret: your-secret-key
  expiration: 86400000  # 24小时
```

### 5.2 权限控制

基于 RBAC 模型：
- 用户 (User) - 角色 (Role) - 菜单/权限 (Menu)
- 支持多角色
- 接口权限通过注解控制

### 5.3 跨域配置

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

## 6. 缓存策略

| 缓存键 | 过期时间 | 说明 |
|--------|----------|------|
| article:hot | 1小时 | 热门文章 |
| article:{id} | 30分钟 | 文章详情 |
| category:list | 24小时 | 分类列表 |
| tag:list | 24小时 | 标签列表 |

缓存更新策略：
- 文章更新时删除对应缓存
- 定时任务刷新热门文章缓存
- 发布/删除文章时清除列表缓存

## 7. API 模块划分

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | /api/auth/** | 登录、注册、Token刷新 |
| 首页 | /api/home | 聚合数据、轮播图 |
| 文章 | /api/articles/** | 文章列表、详情、搜索 |
| 分类 | /api/categories/** | 分类列表、详情 |
| 标签 | /api/tags/** | 标签列表、详情 |
| 评论 | /api/comments/** | 评论列表、发表 |
| 归档 | /api/archives/** | 归档数据 |
| 友链 | /api/friends/** | 友链列表、申请 |
| 文件 | /api/upload/** | 图片/文件上传 |
| 管理 | /api/admin/** | 后台管理接口 |

## 8. 配置文件

### application.yml 主要配置

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/chen404?useUnicode=true&characterEncoding=utf-8
    username: root
    password: password
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

jwt:
  secret: chen404-blog-secret-key
  expiration: 86400000

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: chen404
```

## 9. 实体关系

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────┐
│  sys_user   │────<│  sys_user_role  │>────│  sys_role   │
└──────┬──────┘     └─────────────────┘     └──────┬──────┘
       │                                            │
       └───────────────┬────────────────────────────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   article   │  │   comment   │  │  sys_file   │
└──────┬──────┘  └─────────────┘  └─────────────┘
       │
       │>────┐
       │      │
       ▼      ▼
┌─────────────┐     ┌─────────────┐
│  category   │     │     tag     │
└─────────────┘     └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │ article_tag │
                    └─────────────┘
```

## 10. 开发规范

### 10.1 代码规范

- 使用 Lombok 简化 Getter/Setter
- 使用 MyBatis Plus 简化 CRUD
- 统一使用 Result 包装响应
- 业务异常使用自定义异常

### 10.2 接口规范

- RESTful 风格设计
- 请求参数校验使用 @Valid
- 分页接口统一使用 PageResult
- 管理接口统一前缀 /api/admin

### 10.3 日志规范

- Controller 层记录请求日志
- Service 层记录业务操作
- 异常统一捕获并记录
