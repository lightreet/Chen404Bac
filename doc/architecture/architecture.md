# Chen404 后端架构设计

## 1. 架构概览

```text
Vue Frontend
  -> Controller
  -> Service / ServiceImpl
  -> Mapper (MyBatis Plus)
  -> MySQL / Redis / MinIO
```

除标准的 Controller -> Service -> Mapper 主链路外，当前项目还包含：

- `JwtAuthenticationFilter`：解析请求头中的 JWT，并把当前用户写入 `SecurityContext`
- `@RequireAdmin`：基于 Spring Security 方法级授权的管理员校验
- `TraceIdFilter`、`RequestBodyCacheFilter`：请求链路辅助
- `LoggingInterceptor`、`SqlPerformanceInterceptor`：日志与性能观测
- `AccessService`：统一文章可见性、评论权限、文件删除权限判断

## 2. 技术栈

| 模块 | 技术 |
| ---- | ---- |
| 框架 | Spring Boot 3.1.8 |
| 语言 | Java 17 |
| 数据访问 | MyBatis Plus 3.5.5 |
| 数据库 | MySQL 8 |
| 缓存 | Redis |
| 鉴权 | Spring Security + JWT |
| 对象存储 | MinIO |
| 邮件 | Spring Mail |
| 文档 | SpringDoc OpenAPI |

## 3. 当前目录结构

```text
src/main/java/com/chen404/
├─ annotation/
├─ aspect/
├─ config/
├─ controller/
├─ domain/
│  ├─ dto/
│  └─ entity/
├─ exception/
├─ filter/
├─ interceptor/
├─ job/
├─ mapper/
├─ service/
├─ service/impl/
├─ util/
└─ Chen404Application.java
```

需要特别注意：

- 当前主包路径是 `com.chen404`
- 项目以 `domain.Result` 作为统一响应包装
- 文档描述应以当前代码现状为准，而不是历史设计目标

## 4. 当前控制器与职责

| 控制器 | 路径前缀 | 说明 |
| ---- | ---- | ---- |
| `AuthController` | `/auth` | 登录、注册、登出、刷新令牌、资料修改、改密码 |
| `ArticleController` | `/articles` | 列表、详情、我的文章、邻接文章、点赞、收藏、推荐、CRUD |
| `CategoryController` | `/categories` | 分类查询与管理 |
| `TagController` | `/tags` | 标签列表、标签详情 |
| `HomeController` | `/home` | 首页聚合数据与站点统计 |
| `SiteController` | `/site` | 站点配置、Banner、公开资料 |
| `UploadController` | `/upload` | 图片、头像、封面、批量上传、文件删除 |
| `CommentController` | `/comments` | 评论列表、留言板、发布、删除、点赞、审核 |

当前仓库中没有完整独立的 `AdminController` 资源体系，后台能力仍分布在现有控制器与权限校验中。

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
  -> issue access token + refresh token
  -> frontend stores token
  -> request carries Authorization header
  -> JwtAuthenticationFilter resolves current user and authorities
  -> SecurityContext stores AuthenticatedUser
  -> controller prefers @AuthenticationPrincipal / CurrentUserUtil
```

当前还补充了两层安全约束：

- 登录失败频控
- refresh token 服务端吊销/轮换黑名单

## 6. 配置结构

当前 `application.yml` 的核心配置包括：

- `server.port = 10404`
- `server.servlet.context-path = /api`
- `spring.datasource`
- `spring.data.redis`
- `spring.mail`
- `minio`
- `mybatis-plus`
- `jwt`
- `logging`

文档不重复记录真实密钥，只保留结构层说明。

## 7. 当前 API 模块边界

| 模块 | 当前状态 |
| ---- | ---- |
| 认证 | 已实现 |
| 用户资料 / 密码 | 已实现 |
| 文章 CRUD | 已实现 |
| 我的文章 | 已实现 |
| 分类查询与管理 | 已实现 |
| 标签查询与详情 | 已实现 |
| 首页聚合与统计 | 已实现 |
| 站点配置 / Banner | 已实现 |
| 上传 | 已实现 |
| 评论 / 留言板 | 已实现 |
| 独立后台资源层 | 尚未完整拆分 |

## 8. 数据模型概览

从当前实体与 Mapper 可以看出，核心表关系围绕以下对象展开：

```text
sys_user
  ├─ sys_user_role
  ├─ article
  └─ sys_file

article
  ├─ category
  ├─ tag (through article_tag)
  ├─ comment
  ├─ user_article_like
  └─ user_article_favorite
```

其中：

- 用户与角色通过 `sys_user_role` 关联
- 文章关联分类
- 文章与标签通过 `article_tag` 关联
- 评论与留言板共用评论体系
- 上传文件通过 `SysFile` 与文章/头像/附件引用关系管理

## 9. 当前实现特征

### 首页数据

`HomeController` 当前提供：

- `GET /home`
- `GET /home/stats`

### 上传体系

`UploadController` 当前支持：

- `/upload/image`
- `/upload/images`
- `/upload/cover`
- `/upload/avatar`
- `DELETE /upload/file?url=...`

### 用户认证

`AuthController` 当前支持：

- 登录 / 注册 / 登出
- 获取当前用户
- 刷新 token
- 修改个人资料
- 修改密码
- 用户名 / 邮箱 / 手机号可用性检查

## 10. 文档维护建议

后端文档后续维护时，建议优先同步以下内容：

- 控制器增删时更新“当前控制器与职责”
- 新增或删除接口能力时更新 README 中的实现边界
- SQL 初始化脚本有变更时同步更新启动说明
- 如果未来补齐独立后台资源层，再移除“当前仍未完整拆分”的说明
