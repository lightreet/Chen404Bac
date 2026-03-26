# Chen404 博客系统 - 后端

基于 Spring Boot 3.1 + MyBatis Plus 的博客后端服务。当前仓库实现重点在认证、文章、分类/标签、首页聚合、站点配置和文件上传等核心模块。

## 技术栈

| 类别 | 技术 |
| ------ | ------ |
| 框架 | Spring Boot 3.1.8、Java 17 |
| Web / 校验 | spring-boot-starter-web、spring-boot-starter-validation |
| 数据访问 | MyBatis Plus 3.5.5、MySQL 8、HikariCP |
| 缓存 / 验证码 | Redis |
| 安全 | Spring Security、JWT、自定义拦截与管理员切面 |
| 存储 | MinIO |
| 其他 | Lombok、Hutool、FastJSON2、Spring Mail、SpringDoc |

## 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+
- Redis 7+
- MinIO（或兼容对象存储）

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < doc/chen404.sql
```

### 2. 配置 `application.yml`

请根据本地环境修改：

- `spring.datasource.*`
- `spring.data.redis.*`
- `spring.mail.*`
- `minio.*`
- `jwt.*`

建议不要在文档或仓库中保留真实生产凭据。

### 3. 启动项目

```bash
mvn spring-boot:run
```

或打包运行：

```bash
mvn clean package -DskipTests
java -jar target/chen404bac-1.0.0.jar
```

## 当前运行配置

- 端口：`10404`
- 上下文前缀：`/api`
- Swagger UI：`/api/swagger-ui/index.html`

因此本地接口基地址通常为：

```text
http://localhost:10404/api
```

## 当前项目结构

```text
Chen404Bac/
├── src/main/java/com/chen404/
│   ├── annotation/             # 自定义注解
│   ├── aspect/                 # AOP（如管理员校验）
│   ├── config/                 # Spring / MyBatis / MinIO / Swagger 配置
│   ├── controller/             # 控制器
│   ├── domain/                 # Result、DTO、Entity 等
│   ├── exception/              # 异常定义与全局处理
│   ├── filter/                 # 请求过滤器
│   ├── interceptor/            # JWT / 日志 / SQL 性能拦截
│   ├── job/                    # 定时任务
│   ├── mapper/                 # MyBatis Plus Mapper
│   ├── service/                # 业务接口
│   ├── service/impl/           # 业务实现
│   ├── util/                   # 工具类
│   └── Chen404Application.java
├── src/main/resources/
│   ├── application.yml
│   ├── logback-spring.xml
│   └── mapper/
├── doc/
│   ├── architecture.md
│   └── chen404.sql
└── pom.xml
```

## 当前已实现控制器

| 控制器 | 路径前缀 | 说明 |
| ------ | ------ | ------ |
| `AuthController` | `/auth` | 登录、注册、验证码、刷新令牌、个人资料 |
| `ArticleController` | `/articles` | 文章列表、详情、我的文章、CRUD、点赞、推荐 |
| `CategoryController` | `/categories` | 分类查询与管理 |
| `TagController` | `/tags` | 标签查询 |
| `HomeController` | `/home` | 首页聚合数据、站点统计 |
| `SiteController` | `/site` | 站点配置、Banner |
| `UploadController` | `/upload` | 图片、封面、头像、批量上传、文件删除 |

## 当前接口说明

- 统一响应：`{ code, message, data }`
- 鉴权方式：`Authorization: Bearer <token>`
- 登录后接口通过 JWT 与请求属性读取 `userId`
- 管理能力当前主要依赖 `@RequireAdmin` 切面和前端角色判断

## 与设计目标的差异

当前后端仓库中，以下模块在 README 层面不再按“已完整实现”描述：

- 评论模块
- 友链模块
- 独立后台管理控制器

前端虽然存在部分对应 API 封装，但本仓库当前控制器层尚未完整覆盖，文档以下均按“当前代码实际存在的实现”编写。

## 常用模块

| 模块 | 当前状态 |
| ------ | ------ |
| 认证 | 已实现 |
| 用户资料 / 改密码 | 已实现 |
| 文章 CRUD / 我的文章 | 已实现 |
| 文章热门 / 推荐 / 邻接文章 | 已实现 |
| 分类管理 | 已实现 |
| 标签查询 | 已实现 |
| 首页聚合 / 站点配置 / Banner | 已实现 |
| 上传（图片/封面/头像） | 已实现 |
| 评论 / 友链 / 后台全量管理 | 当前仓库未完整落地 |

## 文档

- [架构设计](doc/architecture/architecture.md)
- [数据库脚本](doc/sql/3-20/chen404.sql)
- [权限设计](doc/architecture/permission-design.md)
- [权限升级脚本](doc/sql/3-20/permission-phase1-upgrade.sql)
