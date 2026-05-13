# Chen404 博客系统 - 后端

基于 Spring Boot 3.1 + MyBatis Plus 的博客后端服务。当前仓库的核心实现已经覆盖认证、文章、分类、标签、评论/留言板、首页聚合、站点配置、文件上传和基础权限控制等模块。

## 技术栈

| 类别 | 技术 |
| ---- | ---- |
| 框架 | Spring Boot 3.1.8、Java 17 |
| Web / 校验 | spring-boot-starter-web、spring-boot-starter-validation |
| 数据访问 | MyBatis Plus 3.5.5、MySQL 8、HikariCP |
| 缓存 | Redis |
| 安全 | Spring Security、JWT、方法级权限校验 |
| 存储 | MinIO |
| 其他 | Lombok、Hutool、FastJSON2、Spring Mail、SpringDoc |

## 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+
- Redis 7+
- MinIO 或兼容对象存储

## 快速开始

### 1. 初始化数据库

先创建一个空的 `chen404` 数据库：

```sql
CREATE DATABASE chen404 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

当前仓库已经通过 Flyway 接管数据库基线与后续核心 schema 迁移。应用启动时会自动执行 `src/main/resources/db/migration/` 下的迁移脚本：

- `V2026032601__baseline_schema.sql`：当前主基线
- `V2026032801__interaction_like_tables.sql`：点赞 / 收藏 / 评论点赞关系表
- `V2026040201__article_cover_file_id.sql`：文章封面文件引用
- `V2026042501__create_user_trust_request.sql`：受信申请主表
- `V2026042502__drop_trust_request_attachment_urls.sql`：受信申请附件字段收敛

兼容策略：

- 新环境：空库启动时自动执行全部 Flyway 迁移
- 已手工初始化的旧环境：通过 `baseline-on-migrate` 自动写入当前基线版本，避免重复执行历史迁移

说明：`doc/sql/` 下的历史 SQL 仍保留为归档参考。其中 `seed-permission-min.sql` 这类环境相关数据脚本仍按需手工执行，不纳入自动 schema 迁移。

### 2. 配置说明

| 文件 | 用途 |
| ---- | ---- |
| `application.yml` | 公共配置，默认 profile 为 `dev` |
| `application-dev.yml` | 本地开发配置，可通过环境变量覆盖敏感值 |
| `application-prod.yml` | 生产配置，建议完全通过环境变量或部署系统注入 |

不要在仓库中提交真实生产凭据。部署流程与生产环境变量样例请参考 `deploy/` 目录中的说明文件。

### 3. 启动项目

本地默认 `dev`：

```bash
mvn spring-boot:run
```

生产打包：

```bash
mvn clean package -DskipTests
java -jar target/chen404bac-1.0.0.jar --spring.profiles.active=prod
```

## 当前运行配置

- 端口：`10404`
- 上下文前缀：`/api`
- Swagger UI：`/api/swagger-ui/index.html`

本地接口基地址通常为：

```text
http://localhost:10404/api
```

## 当前项目结构

```text
Chen404Bac/
├─ src/main/java/com/chen404/
│  ├─ annotation/
│  ├─ aspect/
│  ├─ config/
│  ├─ controller/
│  ├─ domain/
│  ├─ exception/
│  ├─ filter/
│  ├─ interceptor/
│  ├─ job/
│  ├─ mapper/
│  ├─ service/
│  ├─ service/impl/
│  ├─ util/
│  └─ Chen404Application.java
├─ src/main/resources/
│  └─ db/migration/
├─ doc/
│  ├─ architecture/
│  └─ sql/
└─ pom.xml
```

## 当前控制器与职责

| 控制器 | 路径前缀 | 说明 |
| ---- | ---- | ---- |
| `AuthController` | `/auth` | 登录、注册、登出、刷新令牌、用户资料与密码 |
| `ArticleController` | `/articles` | 文章列表、详情、我的文章、CRUD、点赞、收藏、推荐、邻接文章 |
| `CategoryController` | `/categories` | 分类查询与管理 |
| `TagController` | `/tags` | 标签列表、标签详情 |
| `HomeController` | `/home` | 首页聚合数据、站点统计 |
| `SiteController` | `/site` | 站点配置、Banner、公开资料 |
| `UploadController` | `/upload` | 图片、封面、头像、批量上传、文件删除 |
| `CommentController` | `/comments` | 评论列表、留言板、发布、删除、点赞、审核 |

## 当前接口约定

- 统一响应：`{ code, message, data }`
- 鉴权方式：`Authorization: Bearer <token>`
- 登录后接口统一通过 `JwtAuthenticationFilter` 写入 `SecurityContext`，控制器优先使用 `@AuthenticationPrincipal` / `CurrentUserUtil` 获取当前用户
- 管理能力主要依赖 `@RequireAdmin`（基于 Spring Security 方法级授权）与访问控制服务共同完成

## 当前实现边界

| 模块 | 当前状态 |
| ---- | ---- |
| 认证 | 已实现 |
| 用户资料 / 修改密码 | 已实现 |
| 文章 CRUD / 我的文章 | 已实现 |
| 点赞 / 收藏 / 热门 / 推荐 / 邻接文章 | 已实现 |
| 分类查询与管理 | 已实现 |
| 标签查询与详情 | 已实现 |
| 评论 / 留言板 | 已实现基础主链路 |
| 首页聚合 / 站点统计 / Banner / 站点配置 | 已实现 |
| 上传 | 已实现 |
| 完整独立后台资源体系 | 当前仓库仍未完全拆分落地 |

## 文档

- [架构设计](doc/architecture/项目架构设计)
- [数据库脚本](doc/sql/3-26/chen404.sql)
- [权限设计](doc/architecture/permission-design.md)
- [最小权限种子脚本](doc/sql/3-27/seed-permission-min.sql)
