# Chen404 权限设计方案汇总

> 文档生成日期：2026-03-25
>
> 本文档基于当前代码库的实际实现，完整描述权限模型、落地细节、前后端对接方式，以及已知待改进项。

---

## 一、整体架构

```
客户端请求
  │
  ▼
┌────────────────────────────────────┐
│  JwtInterceptor                    │
│  ├ 公开接口 → 尽量识别用户(可选)     │
│  ├ 受保护接口 → 强制校验 JWT + 状态  │
│  └ 禁用账号 → 401 拦截              │
├────────────────────────────────────┤
│  @RequireAdmin 切面               │
│  └ 仅管理员可执行标注的方法          │
├────────────────────────────────────┤
│  AccessService（核心权限引擎）       │
│  ├ isAdmin / isFriend              │
│  ├ canManageArticle                │
│  ├ canViewArticle                  │
│  ├ canCommentArticle               │
│  ├ canDeleteFile                   │
│  └ fillArticlePermissions          │
├────────────────────────────────────┤
│  业务 Service / Controller          │
│  └ 调用 AccessService 做业务判断    │
└────────────────────────────────────┘
```

---

## 二、角色与身份分层

| 层级 | 标识方式 | 能力 |
|------|---------|------|
| **游客 Guest** | 未携带 JWT 或 token 无效 | 浏览公开内容；是否可评论取决于文章 `comment_policy` |
| **注册用户 User** | JWT 有效 + `sys_user.status = 1` | 维护个人资料；创建和管理**自己的**文章；在允许的文章下评论 |
| **好友/受信用户 Friend** | `sys_user.trust_level = 1` | 在 User 基础上，可访问"好友可见"文章和"好友可评论"互动 |
| **管理员 Admin** | `sys_role.role_code = 'admin'` | 全站管理；编辑/删除任意文章和文件；设置置顶/推荐 |

### 判定逻辑

- **Admin**：从 `sys_user_role` → `sys_role` 查询角色列表，优先匹配 `role_code = 'admin'`，赋值到 `User.roleCode`。
- **Friend**：直接读取 `sys_user.trust_level` 字段。Admin 自动拥有 Friend 级别的所有权限。
- 判定函数集中在 `AccessServiceImpl`，前端镜像在 `src/utils/permission.ts`。

---

## 三、数据模型变更

### 3.1 `sys_user` 表

| 新增字段 | 类型 | 默认值 | 说明 |
|---------|------|-------|------|
| `trust_level` | `tinyint NOT NULL` | `0` | 0 = 普通用户，1 = 好友/受信用户 |

### 3.2 `article` 表

| 新增字段 | 类型 | 默认值 | 说明 |
|---------|------|-------|------|
| `visibility` | `tinyint NOT NULL` | `0` | 0=公开 1=登录可见 2=好友可见 3=私密 |
| `comment_policy` | `tinyint NOT NULL` | `1` | 0=关闭 1=登录可评论 2=好友可评论 3=游客可评论 |

### 3.3 非数据库字段（瞬态）

`Article` 实体上新增 `@TableField(exist = false)` 字段：

| 字段 | 类型 | 来源 |
|------|------|------|
| `canEdit` | `Boolean` | `AccessService.canManageArticle` |
| `canDelete` | `Boolean` | `AccessService.canManageArticle` |
| `canComment` | `Boolean` | `AccessService.canCommentArticle` |

`User` 实体上新增 `@TableField(exist = false)` 字段：

| 字段 | 类型 | 来源 |
|------|------|------|
| `role` | `Integer` | `applyRoleInfo()`，0=普通 1=管理 |
| `roleCode` | `String` | `applyRoleInfo()`，`"user"` / `"admin"` |

### 3.4 升级 SQL

已有数据库执行 [`permission-phase1-upgrade.sql`](../sql/3-20/permission-phase1-upgrade.sql)：

```sql
ALTER TABLE `sys_user`
  ADD COLUMN `trust_level` tinyint NOT NULL DEFAULT 0
  COMMENT '信任级别：0-普通用户 1-好友/受信用户' AFTER `status`;

ALTER TABLE `article`
  ADD COLUMN `visibility` tinyint NOT NULL DEFAULT 0
  COMMENT '可见性：0-公开 1-登录可见 2-好友可见 3-私密' AFTER `password`,
  ADD COLUMN `comment_policy` tinyint NOT NULL DEFAULT 1
  COMMENT '评论策略：0-关闭 1-登录可评论 2-好友可评论 3-游客可评论' AFTER `visibility`;
```

---

## 四、后端权限执行细节

### 4.1 JWT 拦截器 (`JwtInterceptor`)

| 场景 | 行为 |
|------|------|
| 公开接口 + 有 token | 尝试解析 userId，成功则写入 `request.attribute("userId")`；失败不阻断 |
| 公开接口 + 无 token | 直接放行，userId 为 null |
| 受保护接口 + 无/失效 token | 返回 401 |
| 受保护接口 + 用户已禁用 | 查 `sys_user.status`，非 ENABLED 则 401 |

公开接口清单（`isPublicUri`）：

| 路径 | 公开条件 |
|------|---------|
| `/auth/login`, `/auth/register`, `/auth/send-code`, `/auth/refresh`, `/auth/check-*` | 无条件 |
| `/articles` (GET), `/articles/{id}` (GET), `/articles/hot`, `/articles/recommend` | 仅 GET |
| `/articles/{id}/neighbors` (GET) | 仅 GET |
| `/articles/{id}/like` (POST) | POST |
| `/categories`, `/categories/{id}` | 仅 GET |
| `/home`, `/site`, `/tags`, `/archives`, `/comments`, `/friends` | 全方法 |
| Swagger 相关路径 | 无条件 |

### 4.2 `@RequireAdmin` 切面 (`RequireAdminAspect`)

- 从 `request.attribute("userId")` 取用户 → 查库 → 校验 `roleCode = "admin"`
- 当前用于：分类管理（POST/PUT/DELETE `/categories`）

### 4.3 `AccessService` 判定矩阵

#### `canViewArticle(userId, article)`

| 条件 | 结果 |
|------|------|
| article 为 null | ❌ |
| 用户是作者或管理员 | ✅（任何状态/可见性都可看） |
| 文章未发布 | ❌ |
| `visibility = PUBLIC` | ✅ |
| `visibility = LOGIN` + 已登录 | ✅ |
| `visibility = FRIEND` + 已登录且为 Friend/Admin | ✅ |
| `visibility = PRIVATE` | ❌（仅作者/管理员可看） |

#### `canManageArticle(userId, article)`

| 条件 | 结果 |
|------|------|
| 用户是文章作者 | ✅ |
| 用户是管理员 | ✅ |
| 其他 | ❌ |

#### `canCommentArticle(userId, article)`

前置条件：必须先通过 `canViewArticle`，否则直接拒绝。

| 评论策略 | 谁能评论 |
|---------|---------|
| `CLOSED` | 仅作者/管理员 |
| `REGISTERED` | 任何登录用户 |
| `FRIEND` | Friend 级别以上 |
| `PUBLIC` | 所有人（含游客） |

#### `canDeleteFile(userId, file)`

| 条件 | 结果 |
|------|------|
| 用户是文件上传者 | ✅ |
| 用户是管理员 | ✅ |
| 其他 | ❌ |
| 文件记录不存在 | ❌（不允许按 URL 盲删存储对象） |

### 4.4 业务层权限执行

| 操作 | 权限检查 | 额外保护 |
|------|---------|---------|
| 创建文章 | 需登录；`isTop`/`isRecommend` 仅管理员可设 | `viewCount`/`likeCount`/`commentCount` 强制归零 |
| 更新文章 | `canManageArticle`；保留原 `authorId` 和统计字段 | 非管理员不可改 `isTop`/`isRecommend` |
| 删除文章 | `canManageArticle` | 逻辑删除 |
| 查看文章详情 | `canViewArticle`；通过则填充 `canEdit`/`canDelete`/`canComment` | 不通过抛 403 |
| 点赞 | `canViewArticle`（不可见文章不可点赞） | — |
| 公开文章列表 | 强制 `status=PUBLISHED AND visibility=PUBLIC` | 热门/推荐/站点统计同理 |
| 删除文件 | `canDeleteFile` | — |

### 4.5 异常处理 (`GlobalExceptionHandler`)

| 异常 | HTTP 状态码 | 业务码 |
|------|------------|-------|
| `UnauthorizedException` | 401 | 401 |
| `ForbiddenException` | 403 | 403 |
| `BindException` | 400 | 400 |
| `RuntimeException` | 500 | 500 |

---

## 五、前端权限配合

### 5.1 类型定义 (`src/types/index.ts`)

```typescript
enum UserRoleCode  { USER = 'user', ADMIN = 'admin' }
enum UserTrustLevel { NORMAL = 0, FRIEND = 1 }
enum ArticleVisibility { PUBLIC = 0, LOGIN = 1, FRIEND = 2, PRIVATE = 3 }
enum ArticleCommentPolicy { CLOSED = 0, REGISTERED = 1, FRIEND = 2, PUBLIC = 3 }
```

`User` 接口新增：`roleCode?`, `trustLevel?`
`Article` 接口新增：`visibility?`, `commentPolicy?`, `canEdit?`, `canDelete?`, `canComment?`

### 5.2 权限工具 (`src/utils/permission.ts`)

| 函数 | 用途 |
|------|------|
| `isAdminUser(user)` | 判断是否管理员（兼容 `roleCode` 和 `role` 两个字段） |
| `isFriendUser(user)` | 判断是否好友级别（Admin 自动包含） |
| `getTrustLevelLabel(user)` | 返回中文标签：游客/普通用户/好友/管理员 |

### 5.3 路由守卫 (`src/router/index.ts`)

| meta 字段 | 行为 |
|-----------|------|
| `requiresAuth` | 无 token → 跳转 `/login` 并记录 redirect |
| `requiresAdmin` | 从 `localStorage.user` 解析角色，非 admin → 跳回首页 |
| `guest` | 已登录用户访问 `/login`、`/register` → 跳回首页 |

### 5.4 UI 层配合

| 位置 | 实现 |
|------|------|
| Header 组件 | 显示角色标签（`getTrustLevelLabel`） |
| Profile 页 | 显示角色 + 信任级别 |
| 文章详情页 | 根据 `canEdit` 显示/隐藏编辑按钮；403 显示"仅特定用户可见"提示 |
| 文章编辑页 | 可设置 `visibility` 和 `commentPolicy` 下拉选项 |

---

## 六、安全加固点（已落地）

| 问题 | 修复 |
|------|------|
| 非管理员可设 `isTop`/`isRecommend` | 创建/更新时强制为 0 或保留原值 |
| 客户端提交 `viewCount`/`likeCount`/`commentCount` | 创建时强制归零，更新时保留服务端值 |
| `likeCount` 可能为 null 导致 NPE | 加 null 检查 |
| 批量上传文件不创建 SysFile 记录 | 改为调用 `sysFileService.uploadTempFile` |
| 无记录文件可按 URL 盲删 | 记录不存在则返回 false，不触及存储 |
| 验证码返回在 API 响应中 | 移除 `result.put("code", code)` |
| `@RequireAdmin` 用硬编码 role ID | 改为 `User.RoleCode.ADMIN` 字符串比较 |
| 禁用用户持有效 JWT 可继续使用 | 拦截器查库校验 `status = ENABLED` |
| 异常返回 HTTP 200 + 业务错误码 | 改为返回真实 HTTP 状态码（401/403/400/500） |
| `Article.password` 序列化到前端 | 加 `@JsonIgnore` |
| 公开接口点赞绕过可见性 | `likeArticle` 先校验 `canViewArticle` |

---

## 七、已知待改进项

### 高优先级

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| 1 | **头像上传不走 SysFile** | 头像文件无所有权记录，旧头像永远不清理 | `uploadAvatar` 改用 `sysFileService.uploadTempFile`，`updateProfile` 时清理旧头像 |
| 2 | **点赞无频率限制** | 可被脚本无限刷赞 | 加用户维度或 IP 维度限流（Redis SETNX 或 RateLimiter） |
| 3 | **`/comments` 全路径公开** | 评论模块接入后 `POST /comments` 会绕过 JWT | `isPublicUri` 中 `/comments` 仅放行 GET |
| 4 | **`trust_level` 无管理入口** | "好友"层只能直接改数据库 | 新增 admin 端点 `PUT /admin/users/{id}/trust-level` |

### 中优先级

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| 5 | **`fillArticlePermissions` 重复查库** | 一次文章详情请求产生多次冗余 DB 调用 | 先获取 user 对象，传入各判定方法，避免重复 `getUserOrNull` |
| 6 | **`getArticlePage` 的 `tagId` 参数无效** | 前端标签筛选功能无法工作 | 实现 tag 过滤（关联查询 `article_tag` 表） |
| 7 | **`getArticlePage` 的 `status` 参数被忽略** | 参数签名有误导性 | 公开列表方法移除该参数；后台管理列表另建方法 |
| 8 | **`getMyArticlePage` 不填充权限字段** | 个人文章列表无法显示操作按钮 | 遍历时调用 `fillArticlePermissions` |
| 9 | **Logout 无服务端失效** | 被盗 token 在过期前无法撤销 | 可选：Redis token 黑名单 |

### 低优先级

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| 10 | **`Article.password` 与 `visibility` 并存** | 两套保护机制关系未定义 | 明确 `password` 是 `visibility=PUBLIC` 下的额外保护，或废弃其中一个 |
| 11 | **返回实体未经 DTO 裁剪** | 公开接口可能暴露内部字段 | 引入 ArticleVO/UserVO 做响应裁剪 |
| 12 | **前端路由守卫基于 localStorage** | 可伪造角色进入 admin 页面（API 仍安全） | 可选：route guard 发一次 `/auth/info` 校验 |

---

## 八、权限流程图

### 文章访问

```
用户请求 GET /articles/{id}
        │
        ▼
  JwtInterceptor (公开接口)
  └→ 尝试解析 userId（可选）
        │
        ▼
  ArticleController.getArticleById
        │
        ▼
  ArticleServiceImpl.getArticleById
        │
        ├── article == null ? → 返回 404
        │
        ├── accessService.canViewArticle(userId, article)
        │     ├── 是作者或管理员 → ✅
        │     ├── 文章未发布 → ❌ 403
        │     └── 按 visibility 判定
        │           ├ PUBLIC  → ✅
        │           ├ LOGIN   → 登录? ✅ : ❌ 403
        │           ├ FRIEND  → Friend? ✅ : ❌ 403
        │           └ PRIVATE → ❌ 403
        │
        ▼
  通过 → 填充关联数据 + fillArticlePermissions
       → 返回文章 (含 canEdit/canDelete/canComment)
```

### 文章管理

```
用户请求 PUT /articles/{id}
        │
        ▼
  JwtInterceptor (受保护接口)
  └→ 必须有有效 JWT + 用户未禁用
        │
        ▼
  ArticleServiceImpl.updateArticle
        │
        ├── article 不存在 → RuntimeException
        │
        ├── accessService.canManageArticle(userId, article)
        │     ├── 是作者 → ✅
        │     ├── 是管理员 → ✅
        │     └── 其他 → ❌ ForbiddenException 403
        │
        ▼
  通过 → 保护 authorId、统计字段
       → 非管理员保护 isTop/isRecommend
       → 更新并返回
```

---

## 九、相关文件索引

### 后端

| 文件 | 作用 |
|------|------|
| `interceptor/JwtInterceptor.java` | JWT 校验 + 公开接口识别 + 禁用用户拦截 |
| `aspect/RequireAdminAspect.java` | `@RequireAdmin` 切面 |
| `service/AccessService.java` | 权限判定接口定义 |
| `service/impl/AccessServiceImpl.java` | 权限判定核心实现 |
| `service/impl/ArticleServiceImpl.java` | 文章业务层权限执行 |
| `service/impl/SysFileServiceImpl.java` | 文件业务层权限执行 |
| `service/impl/UserServiceImpl.java` | 角色解析 (`applyRoleInfo`) |
| `exception/GlobalExceptionHandler.java` | 统一异常 → HTTP 状态码映射 |
| `domain/entity/User.java` | `TrustLevel`, `RoleCode`, `RoleValue` 常量 |
| `domain/entity/Article.java` | `Visibility`, `CommentPolicy`, `Status` 常量 + 权限瞬态字段 |
| `doc/permission-phase1-upgrade.sql` | 增量 DDL |

### 前端

| 文件 | 作用 |
|------|------|
| `src/types/index.ts` | 枚举和接口定义 |
| `src/utils/permission.ts` | `isAdminUser`/`isFriendUser`/`getTrustLevelLabel` |
| `src/router/index.ts` | 路由守卫 (`requiresAuth`/`requiresAdmin`/`guest`) |
| `src/components/Header/Header.vue` | 顶部角色展示 |
| `src/views/Profile/Profile.vue` | 个人中心角色 + 信任级别展示 |
| `src/views/Article/ArticleDetail.vue` | 文章详情权限按钮 + 403 提示 |
| `src/views/Article/ArticleEdit.vue` | 可见性 / 评论策略设置 |

---

## 十、未来演进方向

1. **评论模块落地** — 接入 `comment_policy` 判定 + 评论审核 / 隐藏 / 删除 / 置顶
2. **好友管理入口** — 后台管理 `trust_level`，支持好友白名单和友链联动
3. **RBAC 升级**（可选，多人协作时考虑）— 引入 `EDITOR` 角色 + 细粒度权限点（`article:update:own` 等）
4. **Token 黑名单** — Redis 存储已注销 token，实现真正的服务端登出
5. **响应 DTO 裁剪** — 引入 VO 层，避免实体字段直接暴露
