# Chen404 权限设计

## 当前落地状态

本次第一阶段权限改造已经把权限模型从“仅登录 / 仅管理员”推进到“资源所有权 + 受信用户 + 内容可见性”。

已落地的核心点：

- JWT 公开接口现在会在不拦截匿名访问的前提下，尽量识别当前登录用户
- 文章更新 / 删除已收口为“仅管理员”（用于兼容历史普通用户文章，仅管理员可管）
- 文件删除必须满足“上传者本人或管理员”
- 用户新增 `trust_level`
- 文章新增 `visibility` 与 `comment_policy`
- 文章详情接口会按当前用户身份判定是否可查看
- 文章返回体会附带 `canEdit`、`canDelete`、`canComment`
- 评论模块已落地：支持文章评论与留言板（`articleId=null`），并支持游客评论 delete_key 自助删除

## 角色与身份分层

### 1. 游客 Guest

- 可浏览公开内容
- 不可进入个人中心
- 不可创建、编辑、删除文章
- 是否可评论由文章评论策略决定（`comment_policy=PUBLIC` 允许游客评论）
- 游客评论可通过 **delete_key** 自助删除（明文仅返回一次，服务端仅保存 hash）

### 2. 注册用户 User

- 拥有游客的全部公开访问能力
- 可维护个人资料
- 默认**不可创建文章**（需 `trust_level=1` 才允许创建）
- 不可更新/删除文章（文章更新/删除仅管理员）
- 默认可对允许登录评论的文章发表评论

### 3. 好友 / 受信用户 Friend

- 本质上仍是普通用户
- 通过 `sys_user.trust_level = 1` 标识
- 可访问“好友可见”文章
- 可参与“好友可评论”文章的评论互动
- 可创建文章（POST `/articles`）

### 4. 管理员 Admin

- 拥有全站管理权限
- 通过角色编码 `admin` 判定，不再依赖数据库角色 ID 的 magic number
- 可管理任意文章、文件与后续的评论审核等后台能力
- 可创建/更新/删除任意文章

## 文章权限模型

### 可见性 `article.visibility`

- `0`：公开
- `1`：登录可见
- `2`：好友可见
- `3`：私密，仅作者本人和管理员

### 评论策略 `article.comment_policy`

- `0`：关闭评论
- `1`：登录可评论
- `2`：好友可评论
- `3`：游客可评论

说明：

- 当前后端评论模块尚未完整实现，因此这部分字段先作为权限基础设施落地
- 后续评论接口接入时，应直接复用现有访问判断逻辑

## 所有权规则

### 文章

- **仅管理员**可编辑 / 删除文章
- 说明：该策略用于满足“历史普通用户创建的文章仅管理员可管”的收口目标；如未来需要放宽，可调整为“管理员或（作者且 `trust_level=1`）”

### 文件

- 上传者本人可删除自己上传的文件
- 管理员可删除任意文件
- 不再允许“文件记录不存在时按 URL 直接删存储对象”的宽松行为

## 评论权限模型（已落地）

### 发表评论（`POST /comments`）

- 若评论关联文章（`articleId != null`）：必须通过 `AccessService.canCommentArticle(userId, article)` 校验
- 若为留言板（`articleId = null`）：按“游客/登录用户均可发表评论”的策略放行
- 游客身份不落 `sys_user`：`comment.author_id = null`，使用 `author_name/author_email/author_website` 存储署名

### 游客自助删除（`DELETE /comments/{id}?guestDeleteKey=...`）

- 创建游客评论时生成 `guestDeleteKey`（明文仅在创建响应返回一次）
- 服务端保存 `SHA-256` hash 与过期时间（默认 30 天）
- 删除时校验 key 通过才允许删除（并级联删除子评论）

对应增量 SQL：新增表 `comment_guest_token`。

## 前端配合策略

前端只负责体验层权限展示，不承担最终安全校验。

当前前端已配合新增：

- 用户 `trustLevel` / `roleCode` 类型
- 文章 `visibility` / `commentPolicy` 类型
- 文章详情页根据后端返回的 `canEdit` 显示编辑入口
- 编辑页可设置文章可见性和评论策略
- 路由与头部管理员判断改为优先基于稳定的权限辅助函数

## 数据库升级

新部署可直接使用更新后的 [`chen404.sql`](../sql/3-20/chen404.sql)。

已有数据库请执行：

- [`permission-phase1-upgrade.sql`](../sql/3-20/permission-phase1-upgrade.sql)
- `../sql/3-27/comment-guest-token.sql`（新增游客删除 token 表）
- `../sql/3-27/seed-permission-min.sql`（可选：最小权限数据对齐）

## 推荐的下一阶段

### 评论模块

- 已落地 `CommentController` / `CommentService` 并接入 `comment_policy`
- 可选增强：评论审核、隐藏、置顶、编辑、敏感词/限流等

### 好友能力

- 增加后台对 `trust_level` 的管理入口
- 支持“好友可见文章”筛选与管理

### 后续可选升级

如果未来有多人协作后台，再考虑升级为更完整的 RBAC：

- `USER`
- `FRIEND`
- `EDITOR`
- `ADMIN`

并进一步细化为权限点，例如：

- `article:update:own`
- `article:update:any`
- `comment:review`
