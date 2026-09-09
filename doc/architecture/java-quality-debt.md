# Java 质量债务

本文档记录无法在当前小范围修改中安全处理、但会持续影响 Java 代码质量的遗留问题。只记录具备明确位置、风险和处理边界的事项，不收集一般性的“以后重构”愿望。

## 维护规则

- 修改相关代码时先确认债务是否仍然存在。
- 能在当前任务内安全解决时直接修复并删除对应条目。
- 需要跨服务、数据库兼容或接口迁移时，补充影响范围后单独处理。
- 不把启发式扫描命中直接视为债务，必须先应用 Reuse Test。

## 当前事项

### `SysFile.RefType` 使用字符串常量接口

- **位置**：`domain/entity/SysFile`
- **现状**：文件引用类型由实体内部的字符串常量接口表达。
- **风险**：类型和值的语义分离，调用方容易绕过约束，并且扩展时需要人工同步。
- **处理边界**：改为真实枚举时需要同步服务、Mapper 和已有持久化值，必须验证数据兼容性。
- **状态**：待专项处理。

### 核心服务体量与字段注入

- **位置**：`ArticleServiceImpl`、`ReaderLibraryServiceImpl`、`TravelMemoryServiceImpl`、`UserTrustRequestServiceImpl`、`MusicRadioServiceImpl`、`FileReferenceServiceImpl` 等。
- **现状**：文章视图组装已抽到 `ArticleViewAssembler`，通用 CRUD 不再从业务接口暴露；部分核心服务仍承担权限、文件引用和通知等多类编排职责，并沿用较多字段注入。
- **风险**：依赖边界不易辨认，单元测试构造成本较高，小改动容易触发跨职责回归。
- **处理边界**：需要按业务能力逐步抽取协作服务并同步测试，不能在安全修复中一次性机械拆类或改完所有注入方式。
- **状态**：待按模块专项处理。

### 其他模块的服务实体边界尚未完全迁移

- **位置**：`UserService`、`CategoryService`、`TagService`、`BannerService`、`SysFileService` 等。
- **现状**：公共接口已移除 `IService` 继承，文章用例已使用命令和专用视图；其他模块仍有明确读方法或业务操作返回实体，部分实体也仍含非持久化字段。
- **风险**：业务字段和存储结构继续耦合，内部调用可能依赖不应公开的字段。
- **处理边界**：按完整用例同步服务、转换器及调用方；文件存储元数据等内部用途应区分于 HTTP 响应，不为改名而机械复制同形 DTO。
- **状态**：待按模块迁移。

### 文章非分页入口仍有候选扫描

- **位置**：`ArticleServiceImpl.getHotArticles`、`getRecommendArticles`、`getNeighbors`、`listArchives`。
- **现状**：公开文章、我的文章、点赞和收藏已改为数据库权限过滤后分页；热门/推荐和相邻文章仍先读取有限候选，归档仍一次性读取所有可见条目。
- **风险**：不可见候选较多时，热门/推荐/相邻文章可能不足；归档数据持续增长时响应体仍会增大。
- **处理边界**：复用 `ArticleReadScope` 的 SQL 片段重做这些查询，并先明确归档是否需要按年或游标加载；查询性能应使用实际 MySQL 数据量和执行计划验证。
- **状态**：本轮未扩大到非分页入口，待后续优化。

### `ReaderBookParser` 多格式解析集中在单类

- **位置**：`service/support/reader/ReaderBookParser`（约 1,337 行）。
- **现状**：TXT、EPUB、HTML、Markdown、FB2 识别、章节切分、目录构造、归档资源与安全限制集中在一个解析器中。
- **风险**：新增格式或调整启发式规则容易影响其他格式；归档安全限制、字符集和章节识别之间的回归面较大。
- **处理边界**：先为每种格式建立正常、损坏、超限和恶意归档夹具，再按格式 adapter 与共享安全组件拆分；不得在缺少回归样本时直接重写解析器。
- **状态**：待专项处理。

### 音乐服务测试存在 unchecked 编译警告

- **位置**：`MusicRadioServiceImplTest`。
- **现状**：`mvn clean verify` 通过，但测试编译报告 unchecked/unsafe operations。
- **风险**：原始泛型或不安全转换可能掩盖测试桩类型错误，并持续制造构建噪声。
- **处理边界**：只调整测试构造与泛型声明，不为消除警告改变生产接口契约；修复后用 JDK 17 重新执行全量 `mvn clean verify`。
- **状态**：待小范围清理。

## 已清理

- 2026-07-23：控制器和过滤器中的裸 `Result.error(<number>, ...)` 已改用 `ApiErrorCode`。
- 2026-07-23：`UploadController` 重复上传异常处理已收敛到 `executeUpload`。
- 2026-07-23：`UserTrustRequestServiceImpl` 与 `MailTemplateSupport` 中的内联 HTML 已迁移到 `src/main/resources/mail/fragment/`。
- 2026-09-09：文章校验规则、站点资产键、JWT 会话声明名、AI 协议及默认参数、JSON 围栏解析和阅读器公共字段限制已收敛；定义归属见 [公共规则与业务边界](backend-reuse-and-boundaries.md)。
- 2026-09-09：阅读器导入增加持续补投、数据库租约、旧执行器隔离和有界重试；文章主分页路径改为数据库过滤与分页。
