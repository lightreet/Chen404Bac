# 后端公共规则与业务边界

本轮围绕文章、阅读器导入和实际重复规则进行整理。优先合并会同步变化的规则；相同数字、同名字段或相似实现并不自动代表同一个业务概念。

## 公共定义的归属

| 规则 | 唯一定义位置 | 使用范围 |
| --- | --- | --- |
| 文章状态、可见性、评论策略的合法值 | 对应枚举、`IntegerValueEnum` | `@EnumValue` 请求校验与 `ArticlePolicyValidator` 内部写入校验 |
| 文章写入字段及长度约束 | `ArticleWriteCommand`、`ArticleConstraints` | 创建、编辑命令共同继承；版本仅属于编辑命令 |
| 文章读取范围 | `ArticleReadScope` | 对象权限检查、列表 SQL；角色与可见值只解析一次，矩阵测试校验两种执行形式 |
| 页码、页大小及上限 | `PageBounds` | 文章、管理员评论/消息/文件、知友申请列表；站点文章默认页大小引用同一默认值 |
| 站点资产配置键与引用 ID | `SiteAssetConfig` | 配置保存和文件引用重建 |
| JWT 会话版本声明名 | `AuthConstants.SESSION_VERSION_CLAIM` | JWT 签发与会话校验 |
| 用户启停状态 | `UserStatusEnum` | 读取权限、登录、刷新令牌、找回密码、启用用户查询；未知值按不可用处理 |
| 账号字段长度与密码约束 | `UserConstraints` | 注册、资料更新、验证码申请和密码重置；邮箱及默认昵称容量保持一致 |
| LLM 协议风格 | `LlmApiStyle` | 配置归一化、请求工厂与客户端分派 |
| AI 默认参数 | `LlmProperties`、`AiMaidProperties`、`AiRuntimeProperties.Chat` | 启动值、数据库配置回退及聊天场景；已有常量直接复用 |
| AI 输出 JSON 围栏处理 | `AiJsonOutput` | 聊天、文章助手、音乐建议 |
| 阅读器书籍字段长度 | `ReaderBookConstraints` | 预览、任务创建、后台持久化与解析中的相同字段 |
| 文件删除受理语义 | `SysFileService.requestDeletionByUrl` | 业务提交删除任务；`FILE_DELETE_ACCEPTED` 表示受理，消费者 `FILE_DELETE_OK` 才表示物理清理完成 |
| 保留原字符的截断 | `TextUtil.truncate` | 阅读器内原本相同的截断逻辑；需要去空白或加省略号的操作保留各自语义 |
| 文章关联和权限视图组装 | `ArticleViewAssembler` | 列表、详情、热门、推荐、归档标签 |
| 最新评论的公开文章标题组装 | `RecentCommentViewAssembler` | 首页与最新评论接口，统一按匿名范围获取标题 |

没有合并文件名长度与书籍作者长度、缓冲区大小与业务限制、HTTP 状态区间与重试时间等独立概念。HTML 检测日志中的 `style=` 也不属于页面模板。

## 文章读写边界

`ArticleService` 接收 `CreateArticleCommand` / `UpdateArticleCommand`，返回 `ArticleDetailVO`、`ArticleListItemVO`、`ArticleNeighborsVO` 和 `PageResult`。作者 ID 来自已认证的操作者，标签输入来自命令。`Article` 只保留持久化字段，不再承载标签输入、作者对象、权限标记、点赞收藏状态等临时数据。

`ArticleCommandConverter` 使用字段白名单，并继承公共映射补充编辑版本。`ArticleViewConverter` 只做结构转换；关联查询、文件访问票据和用户权限由 `ArticleViewAssembler` 组装到 VO，不再修改文章实体。归档标签也返回 `ArticleTagVO`，不暴露标签数据库内部字段。

8 个原先继承 `IService` 的业务接口已移除通用 CRUD 继承。业务调用使用明确的方法；服务内部的管理查询直接依赖 Mapper。其余模块部分方法仍返回内部实体，后续按完整用例逐步迁移，不能把“去掉继承”视为所有实体边界都已完成。

## 验证与后续边界

- `ServiceBoundaryTest` 防止业务接口重新继承通用 CRUD，以及文章服务重新暴露实体或文章实体增加临时视图字段。
- `ArticleServiceCommandTest` 验证创建和编辑的标签保存、版本传递、文件及知识切片同步。
- `ArticleViewAssemblerTest` 验证关联/权限响应、敏感字段排除、实体不被修改和空页无查询。
- `RecentCommentViewAssemblerTest` 验证公共标题读取与隐藏文章标题回退。
- `EnumValueValidatorTest` 验证有间隔的枚举合法值，避免用最小/最大值近似枚举。
- 分页和导入恢复的 SQL、并发验证分别见 [文章查询边界](article-query-boundaries.md) 与 [阅读器导入恢复](reader-import-recovery.md)。

尚未迁移的实体边界、核心服务职责、热门/相邻文章候选扫描等保留在 [Java 质量债务](java-quality-debt.md)，后续修改对应模块时继续消除。

## 邮箱注册写入边界

新账号以已核验邮箱作为用户名，旧客户端的 `username` 字段会被忽略。注册前检查邮箱与用户名冲突，并在写入前确认默认角色存在；数据库唯一键冲突转换为业务冲突，角色绑定失败抛出异常并由注册事务回滚。测试使用 Mock 验证写入顺序和失败边界，迁移由正式 Flyway 文件交付。
