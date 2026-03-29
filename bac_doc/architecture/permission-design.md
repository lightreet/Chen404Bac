# Chen404 权限设计（合并版）

> 说明：将 `permission-design.md` 与 `permission-summary.md` 合并为一份“当前落地 + 最终策略（V2）”的综合文档。
> 文档生成日期：2026-03-29

---

## 1. 当前落地状态（已实现）

本项目在第一阶段已落地以下权限相关能力（后续代码如与本文冲突，以本文“目标即实现”为准）：

- JWT 公开接口：不拦截匿名访问的前提下，尽量识别当前登录用户（用于可见性/编辑按钮等“体验型权限”）
- JWT/状态校验：受保护接口强制校验 JWT + 用户状态（禁用用户拦截）
- `@RequireAdmin`：通过切面保证管理类接口（如分类管理）仅管理员可操作
- 用户新增 `trust_level`：用于区分普通用户与好友/受信用户
- 文章新增 `visibility` 与 `comment_policy`：用于文章可见性与评论策略控制
- 文章详情响应体会附带权限字段：`canEdit`、`canDelete`、`canComment`
- 评论模块落地：
  - 文章评论：`articleId != null` 按文章权限判定是否可评论
  - 留言板评论：`articleId = null` 可根据策略放行游客/登录用户
  - 游客评论自助删除：创建时一次性返回 `delete_key`，服务端仅保存 hash + 过期时间
- 文件删除权限：上传者本人或管理员可删；禁止“文件记录不存在时按 URL 直接删存储对象”

---

## 2. 目标架构（权限引擎集中化）

权限判定的核心集中在 `AccessService`（及其实现类）中，业务 Service/Controller 只负责业务流程编排，权限点调用统一判定方法。

建议的调用链：

客户端请求
  → JwtInterceptor（公开接口尽量识别 userId；受保护接口强制校验）
  → Controller / Service
  → AccessService（`canView* / canManage* / canComment* / canDelete*`）
  → 填充权限字段（如文章详情 `canEdit/canDelete/canComment`）
  → 返回 DTO/响应

---

## 3. 角色与身份分层

### 3.1 Guest（游客）

- 未携带 JWT 或 token 无效
- 可浏览公开内容
- 是否可评论由 `article.comment_policy` 决定

### 3.2 User（注册用户）

- JWT 有效 + `sys_user.status = 1`
- 默认不可创建文章（需 `trust_level=1` 允许创建）
- 不可更新/删除文章（更新/删除仅管理员）

### 3.3 Friend（好友/受信用户）

- 由 `sys_user.trust_level = 1` 标识
- 访问“好友可见”文章
- 可参与“好友可评论”文章评论
- 可创建文章（POST `/articles`）

### 3.4 Admin（管理员）

- 从数据库角色编码判定：`sys_role.role_code = 'admin'`
- 管理任意文章、文件以及后续评论审核能力
- 也等价拥有 Friend 的所有能力

---

## 4. 权限策略模型

### 4.1 文章可见性 `article.visibility`

- `0`：公开（PUBLIC）
- `1`：登录可见（LOGIN）
- `2`：好友可见（FRIEND）
- `3`：私密（PRIVATE）

### 4.2 评论策略 `article.comment_policy`

- `0`：关闭评论（CLOSED）
- `1`：登录可评论（REGISTERED）
- `2`：好友可评论（FRIEND）
- `3`：游客可评论（PUBLIC）

---

## 5. AccessService 判定矩阵（V2 最终版）

> 说明：若后续代码与本矩阵不一致，以本矩阵为准并按“迁移步骤”更新。

### 5.1 `canViewArticle(userId, article)`

- `article == null`：❌
- 用户是作者或管理员：✅（任何可见性都可看；但未发布文章仍需按业务策略拒绝）
- 文章未发布：❌（403）
- 依据 `visibility`：
  - `PUBLIC`：✅
  - `LOGIN`：登录？✅ : ❌
  - `FRIEND`：Friend/Admin？✅ : ❌
  - `PRIVATE`：❌（作者/管理员例外已覆盖）

### 5.2 `canManageArticle(userId, article)`

- 用户是文章作者：✅
- 用户是管理员：✅
- 其他：❌

### 5.3 `canCommentArticle(userId, article)`

前置条件：必须先通过 `canViewArticle`，否则直接拒绝。

- `CLOSED`：仅作者/管理员
- `REGISTERED`：任意登录用户
- `FRIEND`：Friend 级别以上
- `PUBLIC`：所有用户（含游客）

### 5.4 `canDeleteFile(userId, file)`

- 文件必须存在（记录不存在：❌）
- 上传者本人：✅
- 管理员：✅
- 其他：❌

---

## 6. 业务层保护与字段收敛

- 文章创建/更新/删除：
  - 创建：管理员或受信用户可用（`trust_level=1`）
  - 更新/删除：仅管理员（用于兼容“历史普通用户文章仅管理员可管”的收口目标）
- 统计字段保护：
  - 创建时强制归零
  - 更新时保留服务端值
- 越权字段保护：
  - 非管理员不得设置 `isTop / isRecommend`

---

## 7. 评论落地细节（已实现）

### 7.1 `POST /comments`

- 关联文章：`articleId != null` 时，必须通过 `AccessService.canCommentArticle(userId, article)`
- 留言板：`articleId = null` 时，按“游客/登录用户均可发表评论”的策略放行（仍受 `comment_policy` 的基础逻辑约束）
- 游客身份不落库 `sys_user`：
  - `comment.author_id = null`
  - 使用 `author_name/author_email/author_website` 存储署名

### 7.2 游客自助删除（`DELETE /comments/{id}?guestDeleteKey=...`）

- 创建游客评论时生成 `guestDeleteKey`，明文仅在创建响应返回一次
- 服务端仅保存 hash 与过期时间（默认 30 天）

---

## 8. 前端配合策略（安全不依赖前端）

前端主要承担体验层逻辑（按钮展示/隐藏、路由体验），最终安全仍由后端 `AccessService` 与拦截器/切面保证。

已配合能力：

- 前端用户类型：`trustLevel` / `roleCode`
- 前端文章类型：`visibility` / `commentPolicy`
- 文章详情页：根据后端返回的 `canEdit/canDelete/canComment` 控制编辑入口与操作按钮
- 服务器拒绝（403）时：前端显示更准确的“无权限”提示

---

## 9. 数据库升级（推荐与最小化脚本）

新部署可直接使用更新后的 `chen404.sql`（或按你当前版本执行增量脚本）。

已有数据库建议执行：

- `bac_doc/sql/3-20/permission-phase1-upgrade.sql`
  - 新增 `sys_user.trust_level`
  - 新增 `article.visibility`、`article.comment_policy`
- `bac_doc/sql/3-27/comment-guest-token.sql`
  - 新增 `comment_guest_token`（游客自助删除 token hash/过期时间）
- `bac_doc/sql/3-27/seed-permission-min.sql`（可选）
  - 最小权限数据对齐

---

## 10. 异常处理与 HTTP 状态码策略

- 统一异常处理：`GlobalExceptionHandler`
- 关键码映射：
  - 未认证/无效 token：401
  - 越权/无权限：403
  - 参数错误：400
  - 运行时异常：500

---

## 11. 已知待优化点（后续可做）

- `fillArticlePermissions` 重复查库：一次请求先加载 user，再传入权限判定方法以减少冗余查询
- 公开列表接口参数签名/行为一致性：例如公开列表 `status` 参数应移除或拆分
- Token 登出无状态失效：如后续引入 Redis，可做 token 黑名单或版本号机制
- 响应 DTO 裁剪：避免实体暴露，建议引入 VO 层（`ArticleVO/UserVO`）

---

## 12. 相关文件索引（快速定位）

后端核心：

- `interceptor/JwtInterceptor.java`
- `aspect/RequireAdminAspect.java`
- `service/AccessService.java`
- `service/impl/AccessServiceImpl.java`
- `service/impl/ArticleServiceImpl.java`
- `service/impl/UserServiceImpl.java`（`applyRoleInfo`）
- `exception/GlobalExceptionHandler.java`
- `domain/entity/User.java`（trust_level / roleCode 常量）
- `domain/entity/Article.java`（visibility/comment_policy 常量 + 权限瞬态字段）

SQL：

- `bac_doc/sql/3-20/permission-phase1-upgrade.sql`
- `bac_doc/sql/3-27/comment-guest-token.sql`
- `bac_doc/sql/3-27/seed-permission-min.sql`

前端配合（若你需要快速对齐页面）：

- `src/utils/permission.ts`
- `src/router/index.ts`
- `src/components/Header/Header.vue`
- `src/views/Profile/Profile.vue`
- `src/views/Article/ArticleDetail.vue`
- `src/views/Article/ArticleEdit.vue`

