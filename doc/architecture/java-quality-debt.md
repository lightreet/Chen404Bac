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

- **位置**：`ArticleServiceImpl`（约 1,130 行）、`ReaderLibraryServiceImpl`（约 917 行）、`TravelMemoryServiceImpl`（约 871 行）、`UserTrustRequestServiceImpl`（约 749 行）、`MusicRadioServiceImpl`（约 666 行）、`FileReferenceServiceImpl`（约 638 行）等。
- **现状**：部分核心服务同时承担查询组装、权限、文件引用和通知等多类编排职责，并沿用较多字段注入。
- **风险**：依赖边界不易辨认，单元测试构造成本较高，小改动容易触发跨职责回归。
- **处理边界**：需要按业务能力逐步抽取协作服务并同步测试，不能在安全修复中一次性机械拆类或改完所有注入方式。
- **状态**：待按模块专项处理。

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
