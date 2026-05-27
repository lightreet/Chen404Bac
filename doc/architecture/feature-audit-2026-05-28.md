# Chen404 后端功能审查与优化清单

更新时间：2026-05-28

## 审查范围

本次审查覆盖当前后端已有功能、最近新增能力、接口边界、数据迁移、AI 场景、文件引用、测试覆盖和现有架构文档。重点对照了 Controller、Service、Flyway migration、测试用例和前端调用面。

## 已落地能力

- 认证与用户：登录、注册、验证码、JWT、refresh token、登出吊销、资料与密码修改。
- 内容系统：文章 CRUD、草稿/发布、可见性、分类、标签、归档、点赞、收藏、热门、推荐、上下篇。
- 评论与留言板：评论列表、留言板、最新评论、评论点赞、管理员审核。
- 站点配置：公开站点配置、Banner、站点统计、AI 私有配置隔离。
- 文件与上传：文章图片、封面、站点资源、头像、受信附件、旅行图片、音乐音频、音乐封面、MinIO、图片压缩、后台文件列表和引用状态。
- 表情包：公开下发、后台维护、ZIP 导入。
- 受信申请：提交、附件、审批、邮件通知、知友访问控制。
- 旅行纪念地图：地点、游记条目、图片、EXIF 坐标、访问时间范围、管理员维护和知友访问。
- AI：文章摘要/标签、Lyra 聊天、SSE、会话恢复、站内轻量检索、规则版相关推荐、后台 AI 配置、连接测试。
- Sakura Radio：公开歌曲、公开歌单、默认电台、管理员歌曲/歌单维护、LRC 校验、音乐文件转永久、AI 曲目信息补全。
- 运维辅助：Flyway migration、TraceId、请求/SQL 日志、XXL-JOB 文件引用重建与清理任务。

## 最近新增或调整

- `V2026052701__create_music_radio_tables.sql` 新增音乐歌曲、歌单和歌单歌曲关系表。
- `MusicRadioController` 提供 `/music/**` 公开接口和 `/admin/music/**` 管理接口。
- `LlmMusicTrackAiSuggestServiceImpl` 和 `MusicTrackSuggestScenarioDefinition` 接入 `MUSIC_TRACK_SUGGEST` AI 场景。
- `SysFile.RefType` 新增 `MUSIC_AUDIO` 和 `MUSIC_COVER`。
- 文件引用结构通过 `V2026052601__simplify_file_reference_and_drop_unused_tables.sql` 收敛，后台文件管理按引用状态统计。

## 当前不足或未实现

- `webSearchEnabled` 仍只是 AI 后台配置中的预留开关，没有真实联网搜索工具调用。
- AI 检索仍是 MySQL 轻量切片和关键词召回，尚无 embedding、向量库、混合召回或 rerank。
- AI API Key 当前是私有配置脱敏展示，但还没有服务端加密存储、版本回滚、调用成本统计和审计日志。
- Sakura Radio 没有歌单删除接口，没有播放统计、收藏/点赞、评论、播放历史和第三方平台同步。
- 音乐文件可通过 `sys_file.refType/refId` 标记业务归属，但 `file_reference` 统一重建尚未扫描 `music_track`，后台文件详情不能像文章/旅行/受信那样展示音乐引用记录。
- 后台文件引用状态筛选需要加载匹配文件后按引用状态过滤再分页，大文件量下需要改为 SQL 级筛选或建立聚合视图。
- 文件清理策略和永久删除流程仍需要更清晰的产品闭环，避免误删仍被业务引用的资源。
- 旅行地图后端仍以手动坐标和 EXIF 为主，没有城市行政编码、坐标纠偏批处理或地图数据版本审计。
- OpenAPI 已暴露，但前端仍主要使用手写 API，接口变更需要额外人工同步。

## 优化建议

- 优先把音乐文件纳入 `FileReferenceService.rebuildAllReferences()`，增加 `MUSIC_TRACK` 模块和 `MUSIC_AUDIO` / `MUSIC_COVER` 业务类型，并补后台文件测试。
- 为音乐歌单补删除接口和默认歌单保护逻辑。
- 将后台文件列表的引用状态过滤前移到数据库层，减少全量文件加载和内存分页。
- 为 AI 配置增加 API Key 加密存储、变更审计、连接测试历史和调用成本统计。
- 将 `MUSIC_TRACK_SUGGEST` 的结果增加“需要人工确认”的提示字段，避免事实字段被误当作确定数据。
- 为 Web Search 设计真实工具调用边界：超时、引用来源、白名单、费用控制、隐私过滤。
- 增加端到端契约测试或 OpenAPI 生成校验，让前端 `src/api/*.ts` 和后端 DTO 更容易保持一致。
- 为文件清理任务增加 dry-run、清理报告和后台可见状态。

