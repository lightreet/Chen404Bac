# Chen404 博客系统 - 后端

基于 Spring Boot 3.1 + MyBatis Plus 的博客后端服务。当前仓库的核心实现已经覆盖认证、文章、分类、标签、评论/留言板、首页聚合、站点配置、文件上传和基础权限控制等模块。

## 技术栈

| 类别 | 技术 |
| ---- | ---- |
| 框架 | Spring Boot 3.1.8、Java 17 |
| Web / 校验 | spring-boot-starter-web、spring-boot-starter-validation |
| 数据访问 | MyBatis Plus 3.5.5、MySQL 8、HikariCP |
| 缓存 | Redis |
| 安全 | Spring Security、JWT、自定义拦截与管理员切面 |
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

```bash
mysql -u root -p < bac_doc/sql/3-26/chen404.sql
```

当前主初始化脚本已经包含 `article_file_ref` 等主链路所需表结构，不需要再额外执行独立补丁脚本才能完成首次启动。

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
├─ bac_doc/
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
- 登录后接口通过 JWT 与请求属性读取 `userId`
- 管理能力主要依赖 `@RequireAdmin`、访问控制服务与前端角色约束共同完成

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

- [架构设计](bac_doc/architecture/architecture.md)
- [数据库脚本](bac_doc/sql/3-26/chen404.sql)
- [权限设计](bac_doc/architecture/permission-design.md)
- [权限升级脚本](bac_doc/sql/3-20/permission-phase1-upgrade.sql)
