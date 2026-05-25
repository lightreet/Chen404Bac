# Chen404 博客系统 - 后端

基于 Spring Boot 3.1.8 + Java 17 + MyBatis Plus 的博客后端服务。当前代码已经覆盖认证、文章、分类、标签、评论/留言板、站点配置、上传与文件引用、表情包、受信申请、旅行纪念地图、AI 文章辅助、Lyra 女仆聊天、AI 后台配置等模块。

## 技术栈

| 类别 | 技术 |
| ---- | ---- |
| 框架 | Spring Boot 3.1.8、Java 17 |
| Web / 校验 | spring-boot-starter-web、spring-boot-starter-validation |
| 数据访问 | MyBatis Plus 3.5.5、MySQL 8、HikariCP、Flyway |
| 缓存 | Redis |
| 安全 | Spring Security、JWT、方法级权限校验 |
| 存储与图片 | MinIO、Thumbnailator、webp-imageio、metadata-extractor |
| AI | OpenAI-compatible LLM Client、SSE、场景化 AI Application Layer |
| 其他 | Lombok、MapStruct、Hutool、FastJSON2、Spring Mail、SpringDoc |

## 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8+
- Redis 7+
- MinIO 或兼容对象存储

## 快速开始

### 1. 初始化数据库

先创建空数据库：

```sql
CREATE DATABASE chen404 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

应用启动时会自动执行 `src/main/resources/db/migration/` 下的 Flyway 脚本。当前核心迁移包括：

| 迁移 | 说明 |
| ---- | ---- |
| `V2026032601__baseline_schema.sql` | 主基线 schema |
| `V2026032801__interaction_like_tables.sql` | 文章点赞、收藏、评论点赞 |
| `V2026040201__article_cover_file_id.sql` | 文章封面文件引用 |
| `V2026042501__create_user_trust_request.sql` | 受信申请 |
| `V2026042502__drop_trust_request_attachment_urls.sql` | 受信申请附件结构收敛 |
| `V2026050602__site_config_runtime_defaults.sql` | 站点运行时配置默认值 |
| `V2026050603__remove_runtime_site_config_keys.sql` | 站点公开配置清理 |
| `V2026051001__ai_chat_and_article_chunk.sql` | AI 会话与文章知识切片 |
| `V2026051301__create_travel_memory_tables.sql` | 旅行纪念地图 |
| `V2026052101__create_file_reference_table.sql` | 统一文件引用 |
| `V2026052401__add_travel_memory_visited_end_at.sql` | 旅行时间范围结束日期 |
| `V2026052501__ai_admin_config.sql` | AI 后台配置默认项 |

兼容策略：

- 新环境：空库启动时自动执行全部迁移。
- 已手工初始化的旧环境：通过 `baseline-on-migrate` 写入基线版本，避免重复执行历史迁移。
- 真实凭据、生产数据与临时修复 SQL 不应提交到仓库。

### 2. 配置说明

| 文件 | 用途 |
| ---- | ---- |
| `application.yml` | 公共配置，默认 profile 为 `dev` |
| `application-dev.yml` | 本地开发配置，可通过环境变量覆盖敏感值 |
| `application-prod.yml` | 生产配置，建议完全通过环境变量或部署系统注入 |
| `application-local.yml` | 本地个人配置，如果存在应只在本机使用 |

核心运行配置：

- 服务端口：`10404`
- 上下文前缀：`/api`
- Swagger UI：`/api/swagger-ui/index.html`
- OpenAPI JSON：`/api/v3/api-docs`

本地接口基地址通常为：

```text
http://localhost:10404/api
```

### 3. 启动项目

默认 `dev` profile：

```bash
mvn spring-boot:run
```

指定本地 profile 与 JDK 17：

```powershell
$env:JAVA_HOME='D:\Jdk\JDK17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"
```

生产打包：

```bash
mvn clean package -DskipTests
java -jar target/chen404bac-1.0.0.jar --spring.profiles.active=prod
```

## 当前目录结构

```text
Chen404Bac/
├─ src/main/java/com/chen404/
│  ├─ annotation/       # 权限注解
│  ├─ config/           # 安全、MyBatis、MinIO、AI、上传等配置
│  ├─ controller/       # HTTP API
│  ├─ converter/        # DTO / Entity 转换
│  ├─ domain/           # DTO、Entity、Enum、Result
│  ├─ exception/        # 全局异常体系
│  ├─ filter/           # JWT、TraceId、请求体缓存
│  ├─ interceptor/      # 请求日志、SQL 性能
│  ├─ job/              # 定时清理任务
│  ├─ mapper/           # MyBatis Plus Mapper
│  ├─ security/         # 认证用户模型
│  ├─ service/          # 业务接口与支持层
│  ├─ service/impl/     # 业务实现
│  ├─ util/             # JWT、Redis、请求工具
│  └─ Chen404Application.java
├─ src/main/resources/
│  ├─ db/migration/     # Flyway migration
│  ├─ mapper/           # XML Mapper
│  ├─ mail/             # 邮件模板
│  └─ prompts/ai/       # Lyra 默认 prompt 模板
├─ doc/architecture/
└─ pom.xml
```

## 当前控制器与职责

| 控制器 | 路径 | 说明 |
| ---- | ---- | ---- |
| `AuthController` | `/auth/**` | 登录、注册、验证码、用户信息、刷新 token、资料修改、密码修改、登出 |
| `ArticleController` | `/articles/**`, `/archives` | 文章列表、详情、我的文章、点赞、收藏、热门、推荐、归档、CRUD |
| `AiArticleController` | `/articles/ai/assist` | 管理员文章摘要与标签生成 |
| `AiChatController` | `/ai/chat/**` | Lyra 同步聊天、SSE 流式聊天、会话恢复 |
| `AdminAiConfigController` | `/admin/ai/config/**` | AI 模型、Lyra 人设、聊天策略后台配置与连接测试 |
| `CategoryController` | `/categories/**` | 分类公开查询与管理员 CRUD |
| `AdminCategoryController` | `/admin/categories` | 后台分类分页列表 |
| `TagController` | `/tags/**` | 标签列表、标签详情 |
| `HomeController` | `/home/**` | 首页聚合数据、站点统计 |
| `SiteController` | `/site/**` | 站点配置、Banner、公开成员资料 |
| `UploadController` | `/upload/**` | 文章图片、封面、站点资源、头像、受信附件、旅行图片、文件删除 |
| `AdminFileController` | `/admin/files/**` | 后台文件列表、详情、引用关系重建 |
| `EmojiController` | `/emoji/**`, `/admin/emoji/**` | 表情包公开下发与后台维护、ZIP 导入 |
| `CommentController` | `/comments/**`, `/admin/comments/**` | 评论、留言板、最新评论、点赞、审核 |
| `TrustRequestController` | `/trust-requests/**`, `/admin/trust-requests/**` | 受信申请提交、查询、审批、邮件审批入口 |
| `AdminUserController` | `/admin/users/**` | 用户信任级别维护 |
| `TravelMemoryController` | `/travel-memories/**`, `/admin/travel-memories/**` | 旅行纪念地图公开查询与后台管理 |

## 当前接口约定

- 统一响应：`{ code, message, data }`
- 鉴权方式：`Authorization: Bearer <token>`
- 登录后接口通过 `JwtAuthenticationFilter` 写入 `SecurityContext`
- 控制器优先使用 `@AuthenticationPrincipal` 或 `CurrentUserUtil` 获取当前用户
- 管理员能力主要通过 `@RequireAdmin` 和 `AccessService` 校验
- 知友能力通过 `UserTrustLevelEnum.FRIEND` 和访问控制服务校验

## 当前实现边界

| 模块 | 当前状态 |
| ---- | ---- |
| 认证 / refresh token / 登出吊销 | 已实现 |
| 用户资料 / 修改密码 | 已实现 |
| 文章 CRUD / 我的文章 / 权限可见性 | 已实现 |
| 点赞 / 收藏 / 热门 / 推荐 / 邻接文章 | 已实现 |
| 分类 / 标签 / 归档 | 已实现 |
| 评论 / 留言板 / 审核 / 评论点赞 | 已实现 |
| 首页聚合 / 站点统计 / Banner / 站点配置 | 已实现 |
| 上传 / 图片压缩 / MinIO / 文件删除 | 已实现 |
| 统一文件引用与后台文件管理 | 已实现 |
| 表情包公开下发与后台导入 | 已实现 |
| 受信申请与知友访问控制 | 已实现 |
| 旅行纪念地图 | 已实现 |
| AI 文章辅助 | 已实现 |
| Lyra 聊天、SSE、会话恢复、站内检索、相关推荐 | 已实现 |
| AI 后台配置、API Key 脱敏、连接测试 | 已实现 |
| Web Search 工具调用 | 仅预留开关，尚未接入真实联网搜索 |
| 向量数据库 / embedding 检索 | 尚未实现，当前为 MySQL 轻量检索 |

## 常用验证

```bash
mvn test
```

AI 或配置相关变更建议至少覆盖：

```bash
mvn "-Dtest=AiConfigServiceImplTest,AdminAiConfigControllerTest,MaidChatScenarioDefinitionTest,AiChatServiceImplTest,OpenAiCompatibleLlmClientTest" test
```

## 文档

- [后端架构设计](doc/architecture/项目架构设计.md)
- [项目 AI 接入设计](doc/architecture/项目AI接入设计.md)
- [AI 女仆后台配置设计](doc/architecture/AI女仆后台配置设计.md)
- [旅行纪念地图功能方案](doc/architecture/旅行纪念地图功能方案.md)
- [旅行纪念地图改造推进计划](doc/architecture/旅行纪念地图改造推进计划.md)
