# Chen404 后端架构设计

## 1. 架构概览

```text
Vue Frontend
  ↓
Controller
  ↓
Service / ServiceImpl
  ↓
Mapper (MyBatis Plus)
  ↓
MySQL / Redis / MinIO
```

除标准的 Controller → Service → Mapper 链路外，当前项目还包含：

- `JwtInterceptor`：解析请求中的 JWT
- `@RequireAdmin` + `RequireAdminAspect`：管理员权限校验
- `TraceIdFilter`、`RequestBodyCacheFilter`：请求链路辅助
- `LoggingInterceptor`、`SqlPerformanceInterceptor`：日志与性能观测

## 2. 技术栈

| 模块 | 技术 |
| ------ | ------ |
| 框架 | Spring Boot 3.1.8 |
| 语言 | Java 17 |
| 数据访问 | MyBatis Plus 3.5.5 |
| 数据库 | MySQL 8 |
| 连接池 | HikariCP |
| 缓存 / 验证码 | Redis |
| 鉴权 | Spring Security + JWT |
| 对象存储 | MinIO |
| 邮件 | Spring Mail |
| 文档 | SpringDoc OpenAPI |

## 3. 当前目录结构

```text
src/main/java/com/chen404/
├── annotation/
├── aspect/
├── config/
├── controller/
├── domain/
│   ├── dto/
│   └── entity/
├── exception/
├── filter/
├── interceptor/
├── job/
├── mapper/
├── service/
├── service/impl/
├── util/
└── Chen404Application.java
```

需要特别注意：

- 当前主包路径是 `com.chen404`，不是旧文档中的 `com.chen404.blog`
- 项目以 `domain.Result` 作为统一响应包装，而不是单独的 common/vo 目录体系

## 4. 当前控制器与职责

| 控制器 | 路径前缀 | 说明 |
| ------ | ------ | ------ |
| `AuthController` | `/auth` | 登录、注册、验证码、刷新令牌、资料修改、改密码 |
| `ArticleController` | `/articles` | 列表、详情、我的文章、邻接文章、点赞、推荐、CRUD |
| `CategoryController` | `/categories` | 分类查询与管理 |
| `TagController` | `/tags` | 标签列表 |
| `HomeController` | `/home` | 首页聚合数据与站点统计 |
| `SiteController` | `/site` | Banner、站点配置 |
| `UploadController` | `/upload` | 图片、头像、封面、批量上传、文件删除 |

当前仓库中未见完整的：

- `CommentController`
- `FriendController`
- 独立 `AdminController`

因此架构文档只记录目前代码里真实存在的模块。

## 5. 统一响应与鉴权

### 统一响应

后端统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 鉴权流程

```text
Login
  → issue access token + refresh token
  → frontend stores token
  → request carries Authorization header
  → JwtInterceptor resolves userId
  → controller/service uses RequestAttrUtil.requireUserId()
```

管理员接口当前主要通过：

- 前端路由角色守卫
- 后端 `@RequireAdmin` 切面

共同约束。

## 6. 配置结构

当前 [`application.yml`](../../src/main/resources/application.yml) 的核心配置包括：

- `server.port = 10404`
- `server.servlet.context-path = /api`
- `spring.datasource`（MySQL）
- `spring.data.redis`（Redis）
- `spring.mail`（邮件）
- `minio`（对象存储）
- `mybatis-plus`
- `jwt`
- `logging`

文档中不再重复真实凭据，只保留结构层说明。

## 7. 当前 API 模块边界

| 模块 | 当前状态 |
| ------ | ------ |
| 认证 | 已实现 |
| 用户资料 / 密码 | 已实现 |
| 文章 CRUD | 已实现 |
| 我的文章 | 已实现 |
| 分类查询与管理 | 已实现 |
| 标签查询 | 已实现 |
| 首页聚合与统计 | 已实现 |
| 站点配置 / Banner | 已实现 |
| 上传 | 已实现 |
| 评论 | 当前仓库未见完整控制器 |
| 友链 | 当前仓库未见完整控制器 |
| 后台全量管理 | 当前仓库未见独立管理控制器 |

## 8. 数据模型概览

从当前实体与 Mapper 可以看出，核心表关系仍围绕以下对象展开：

```text
sys_user
  ├─ sys_user_role
  ├─ article
  └─ sys_file

article
  ├─ category
  └─ tag (through article_tag)
```

其中：

- 用户与角色通过 `sys_user_role` 关联
- 文章关联分类
- 文章与标签通过中间表关联
- 上传文件通过 `SysFile` 管理

## 9. 当前实现特点

### 首页数据

`HomeController` 当前提供：

- `GET /home`
- `GET /home/stats`

首页聚合数据包含：

- Banner
- 站点统计
- 热门文章

### 上传体系

`UploadController` 当前支持：

- `/upload/image`
- `/upload/images`
- `/upload/cover`
- `/upload/avatar`
- `DELETE /upload/file?url=...`

并基于 `SysFileService` 和 `FileStorageService` 管理临时文件与引用关系。

### 用户认证

`AuthController` 当前支持：

- 登录 / 注册 / 登出
- 发送验证码
- 获取当前用户
- 刷新 token
- 修改个人资料
- 修改密码
- 用户名 / 邮箱 / 手机号可用性检查

## 10. 文档维护建议

后端文档后续维护时，建议优先同步以下内容：

- 控制器增删时更新“当前控制器与职责”
- 新增接口时更新 README 中的实现边界
- 如果评论/友链/后台管理模块补齐，需把“当前仓库未见完整控制器”的说明移除
