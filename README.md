# Chen404 博客系统 - 后端

Chen404Bac 是 Chen404 的 Spring Boot 后端服务，负责 REST API、认证鉴权、内容数据、站点配置、文件与对象存储、私人小说书架、旅行纪念地图、Sakura Radio、AI 场景编排以及后台任务。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 框架 | Spring Boot 3.1.8、Java 17 |
| Web / 校验 | spring-boot-starter-web、spring-boot-starter-validation |
| 数据访问 | MyBatis Plus 3.5.5、MySQL 8、HikariCP、Flyway |
| 缓存 | Redis |
| 安全 | Spring Security、JWT、方法级权限校验 |
| 存储与图片 | MinIO、Thumbnailator、webp-imageio、metadata-extractor |
| AI | OpenAI-compatible LLM Client、SSE、场景化 AI Application Layer |
| 其他 | Lombok、MapStruct、Hutool、FastJSON2、Spring Mail、SpringDoc、XXL-JOB |

## 环境要求

- JDK 17+
- Maven 3.6.3+
- MySQL 8+
- Redis 7+
- MinIO 或兼容对象存储

## 启动与构建

### 本地启动

```bash
mvn spring-boot:run
```

默认地址：

- API：`http://localhost:10404/api`
- Swagger UI：`http://localhost:10404/api/swagger-ui/index.html`
- OpenAPI：`http://localhost:10404/api/v3/api-docs`

### 指定本地 profile

```powershell
$env:JAVA_HOME='D:\Jdk\JDK17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"
```

### 打包

```bash
mvn clean verify
java -jar target/chen404bac-1.0.0.jar --spring.profiles.active=prod
```

## 数据库迁移

首次使用请创建数据库：

```sql
CREATE DATABASE chen404 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Flyway 迁移目录：

```text
src/main/resources/db/migration/
```

当前核心迁移主题包括：

- 基线 schema
- 文章点赞/收藏/评论点赞
- 文章封面文件引用
- 好友申请
- 站点运行时配置
- 管理后台运行时功能开关
- AI 会话与文章知识切片
- 旅行纪念地图（含停留片段 `stop` 分组与结束时间）
- 统一文件引用
- AI 后台配置
- Sakura Radio 歌曲与歌单
- 私人小说书架、章节目录与阅读进度

当前 `application.yml` 已启用：

- `baseline-on-migrate = true`
- `validate-on-migrate = true`
- `clean-disabled = true`
- 默认 profile 为 `dev`

## 配置结构

### 配置文件

| 文件 | 说明 |
| --- | --- |
| `application.yml` | 公共配置，默认 `dev` profile |
| `application-dev.yml` | 开发环境默认配置 |
| `application-prod.yml` | 生产配置骨架，建议全部走环境变量 |
| `application-local.yml` | 本机私有配置（如果存在） |

### 主要配置域

- `spring.datasource`：MySQL
- `spring.data.redis`：Redis
- `minio.*`：对象存储
- `jwt.*`：认证密钥与过期时间
- `app.cors.*`：跨域
- `app.frontend-base-url` / `app.backend-base-url`：前后端对外基础地址
- `app.site-runtime.*`：文章分页、上传限制等运行时默认值
- `app.music.player-state-ttl`：登录用户播放现场在 Redis 中的滑动过期时间，默认 7 天
- `app.image-processing.*`：上传图片压缩、WebP 转换与尺寸限制
- `app.ai.runtime.*`：聊天、文章辅助、推荐默认策略
- `app.ai.maid.*`：Lyra 默认 prompt 与人设资源
- `llm.*`：OpenAI-compatible 模型调用配置
- `xxl.job.*`：后台任务执行器配置

### 管理后台运行时功能

需要管理员在日常运营中即时调整的业务开关存放在 `site_config` 私有配置项中，通过后台“站点配置 → 功能开关”维护，无需修改服务器环境变量或重启服务。当前包括：

- 知友文章、旅行、音乐创作
- 管理员消息中心
- AI 文章摘要与标签、音乐信息补全、相关文章推荐

数据库、Redis、MinIO、JWT、CORS、上传硬限制、图片编码参数、XXL-JOB 和 Swagger 仍属于部署或启动期配置，不允许从业务后台修改。

## 当前控制器边界

| 控制器 | 路径 | 说明 |
| --- | --- | --- |
| `AuthController` | `/auth/**` | 登录、注册、验证码、用户信息、刷新 token、资料与密码修改、登出 |
| `ArticleController` | `/articles/**`, `/archives` | 文章列表、详情、我的文章、点赞、收藏、热门、推荐、归档、CRUD |
| `AiArticleController` | `/articles/ai/assist` | 管理员文章摘要与标签生成 |
| `AiChatController` | `/ai/chat/**` | Lyra 同步聊天、SSE 流式聊天、会话恢复 |
| `CategoryController` | `/categories/**` | 分类公开查询与管理员 CRUD |
| `AdminCategoryController` | `/admin/categories` | 后台分类分页列表 |
| `TagController` | `/tags/**` | 标签列表、标签详情 |
| `CommentController` | `/comments/**`, `/admin/comments/**` | 评论、留言板、最新评论、点赞、审核 |
| `SiteController` | `/site/**` | 站点配置、Banner、站点成员与用户资料 |
| `HomeController` | `/home/**` | 首页聚合数据、站点统计 |
| `UploadController` | `/upload/**` | 图片、封面、头像、站点资源、旅行图片、音乐音频/封面、附件上传与文件删除 |
| `AdminFileController` | `/admin/files/**` | 文件列表、详情、统计 |
| `EmojiController` | `/emoji/**`, `/admin/emoji/**` | 表情包公开下发、后台维护、ZIP 导入 |
| `TrustRequestController` | `/trust-requests/**`, `/admin/trust-requests/**` | 好友申请提交、查询、审批、邮件审批入口 |
| `AdminUserController` | `/admin/users/**` | 用户信任等级维护 |
| `TravelMemoryController` | `/travel-memories/**`, `/admin/travel-memories/**` | 旅行纪念地图查询与管理 |
| `MusicRadioController` | `/music/**`, `/admin/music/**` | Sakura Radio 公开播放、歌曲/歌单维护、AI 曲目信息补全 |
| `ReaderLibraryController` | `/reader/**` | 私人小说导入、书架、目录、章节、全文搜索、资源、进度与阅读偏好 |
| `AdminAiConfigController` | `/admin/ai/config/**` | AI 后台配置读取、保存、连接测试 |
| `AdminFeatureToggleController` | `/admin/feature-toggles` | 运行时业务功能开关读取与保存 |

## 当前能力

### 已落地

- 认证、JWT 刷新、登出
- 文章 CRUD、我的文章、点赞、收藏、归档、推荐、上下篇
- 分类、标签、首页聚合、站点配置、站点统计
- 评论/留言板、评论审核、评论点赞
- 文件上传、图片处理、统一文件引用、后台文件管理
- 表情包公开下发、后台维护、ZIP 导入
- 好友申请、后台审批、邮件审批入口
- 旅行纪念地图、地点/片段/照片结构、图片 EXIF 解析、权限校验、文件转永久与引用同步
- Sakura Radio 公开播放、歌曲与歌单维护、默认播放集、登录用户 Redis 临时播放现场恢复
- 私人小说书架，支持 TXT、EPUB、HTML、Markdown、FB2 导入、多级目录和书内插图
- 阅读进度按章节、段落与字符偏移持久化，阅读偏好支持跨设备恢复
- AI 文章辅助、AI 音乐曲目信息补全
- Lyra 同步/流式聊天、会话恢复、站内知识检索、相关推荐
- AI 后台配置、API Key 脱敏、连接测试

### 当前边界

- `webSearchEnabled` 仅是配置开关，尚未接入真实联网搜索
- 站内知识检索当前基于 MySQL 轻量检索，不是向量数据库/embedding 方案
- 音乐播放统计、用户互动能力尚未实现
- 文件治理闭环主要依赖后端任务，前台没有完整批量治理入口

## 文件与上传模型

上传入口统一在 `UploadController`，当前覆盖：

- 文章图片 / 批量图片
- 文章封面
- 站点资源
- 用户头像
- 好友申请附件
- 音乐音频 / 音乐封面
- 旅行纪念图片
- 文件删除

当前文件生命周期要点：

- 上传阶段先进入 `sys_file`
- 业务保存时转换为永久文件
- `file_reference` 维护统一引用关系
- 后台文件页按引用状态和归属类型展示
- 小说原始文件以 `NOVEL_SOURCE` 归属保护；书内资源存入小说资源表，仅允许所属用户鉴权读取

## 后台任务

当 `XXL_JOB_ENABLED=true` 时，可启用以下任务：

- `fileReferenceRebuildJobHandler`：重建统一文件引用
- `fileCleanupJobHandler`：清理无用文件
- `musicTrackFileReferenceSyncJobHandler`：重建音乐曲目文件引用

## 测试

全量测试：

```bash
mvn clean verify
```

`verify` 会执行 Maven/JDK 版本校验、全量测试，并在 `target/site/jacoco/` 生成 JaCoCo 报告；当前行覆盖率门禁为 40%。

AI 相关改动建议至少覆盖：

```bash
mvn "-Dtest=AiConfigServiceImplTest,AdminAiConfigControllerTest,MaidChatScenarioDefinitionTest,AiChatServiceImplTest,OpenAiCompatibleLlmClientTest" test
```

音乐相关改动建议至少覆盖：

```bash
mvn "-Dtest=MusicRadioControllerTest,MusicRadioServiceImplTest,LlmMusicTrackAiSuggestServiceImplTest,MusicTrackSuggestScenarioDefinitionTest" test
```

## 部署说明

生产部署与私有运维说明在 `Chen404Pra` 维护：

- [`../Chen404Pra/README.md`](../Chen404Pra/README.md)
- [`../Chen404Pra/deployment/deploy-chen404-prod.md`](../Chen404Pra/deployment/deploy-chen404-prod.md)

公开仓库只描述配置边界，不记录敏感值。

## 文档

- 文档索引：[`doc/architecture/架构文档索引.md`](./doc/architecture/架构文档索引.md)
- 后端架构：[`doc/architecture/项目架构设计.md`](./doc/architecture/项目架构设计.md)
- AI 设计：[`doc/architecture/项目AI接入设计.md`](./doc/architecture/项目AI接入设计.md)
- AI 配置专题：[`doc/architecture/AI女仆后台配置设计.md`](./doc/architecture/AI女仆后台配置设计.md)
- 音乐馆专题：[`doc/architecture/音乐电台功能需求与界面设计.md`](./doc/architecture/音乐电台功能需求与界面设计.md)
- 旅行纪念地图专题：[`doc/architecture/旅行纪念地图设计与改造方案.md`](./doc/architecture/旅行纪念地图设计与改造方案.md)
- Java 质量债务：[`doc/architecture/java-quality-debt.md`](./doc/architecture/java-quality-debt.md)
- 阶段快照：[`doc/architecture/后端功能审查与优化清单.md`](./doc/architecture/后端功能审查与优化清单.md)、[`功能审查总结_2026-03-27.md`](./功能审查总结_2026-03-27.md)
