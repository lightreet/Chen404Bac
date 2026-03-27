---
name: "Admin migration: categories"
overview: Move admin-only category management out of Personal Center into /admin, hide it from normal users, and wire up admin routing + navigation while keeping existing permission checks (JWT + @RequireAdmin).
todos:
  - id: add-admin-route
    content: Add /admin route + requiresAdmin guard in router
    status: completed
  - id: admin-layout
    content: Create Admin layout page with navigation
    status: completed
  - id: migrate-categories-ui
    content: Move category management UI/logic from Profile.vue into AdminCategories page
    status: completed
  - id: remove-profile-categories
    content: Remove categories menu/section from Profile.vue and cleanup code
    status: completed
  - id: verify-permissions
    content: Verify admin-only visibility and endpoint access for categories CRUD
    status: completed
isProject: false
---

# Admin-only 分类管理迁移计划

## 目标
- 将“分类管理”从个人中心迁移到后台管理（`/admin`）。
- **普通用户完全不展示**分类管理入口与页面。
- 管理员从头像下拉的“后台管理”进入后可管理分类。

## 默认决策（基于你刚才的确认）
- 先迁移 **分类管理** 这条功能线到 `/admin`。
- 个人中心左侧菜单 **移除**“分类管理”。
- 给管理员在个人中心保留一个轻量入口：在“个人信息/设置”区块显示“前往后台管理”的按钮（可选）或仅通过 Header 下拉进入。

## 代码改动范围
- 前端：
  - 路由新增/完善 `'/admin'`（管理员专属，`meta.requiresAuth + meta.requiresAdmin`）。
  - 新建后台页面：`src/views/Admin/AdminLayout.vue` + `src/views/Admin/AdminCategories.vue`（或合并为一个 `Admin.vue` 页面）。
  - 将现有分类管理 UI/逻辑从 `src/views/Profile/Profile.vue` 迁移到 `AdminCategories`。
  - `Profile.vue`：移除 `el-menu-item index="categories"`、对应内容区渲染、以及相关加载/弹窗逻辑引用（避免无用代码）。
  - `Header.vue`：保持“后台管理”仅管理员可见（现有 `isAdmin` 已做），确保跳转到 `/admin`。
  - `router/index.ts`：新增管理员路由守卫（无管理员权限则 403 提示并重定向）。

- 后端：
  - 不需要新增 Controller：已存在 `CategoryController` + `@RequireAdmin`。
  - 确认 `JwtInterceptor` 对 `/categories` 的 `POST/PUT/DELETE` 仍要求 JWT（已是 method-aware 逻辑）。

## 路由与权限策略（前端）
- `requiresAuth`: 无 token → 跳转 `/login?redirect=...`
- `requiresAdmin`: 已登录但非管理员 → 提示“仅管理员可访问”，跳 `/profile` 或 `/`。
- 管理员角色判断复用：`userStore.user?.role === 1`（与你现有 `Header.vue` / `Profile.vue` 一致）。

## 迁移步骤（实现顺序）
1. 新增后台路由与基础页面骨架（布局 + 左侧后台菜单）。
2. 从 `Profile.vue` 复制/迁移分类管理的 table + dialog 表单 + CRUD 方法到 `AdminCategories.vue`。
3. 删除/移除 `Profile.vue` 中“分类管理”入口与页面内容，并清理对应脚本逻辑。
4. 路由守卫加 `requiresAdmin` 校验；Header 下拉“后台管理”链接保持管理员才显示。
5. 手工验证：
   - 普通用户：个人中心不见分类管理；访问 `/admin` 会被拦截。
   - 管理员：`/admin` 能进入并完成分类增删改。

## 主要涉及文件（计划修改/新增）
- 新增：
  - `d:\code_repo\Chen404\Chen404Fro\src\views\Admin\AdminLayout.vue`
  - `d:\code_repo\Chen404\Chen404Fro\src\views\Admin\AdminCategories.vue`
- 修改：
  - `d:\code_repo\Chen404\Chen404Fro\src\router\index.ts`
  - `d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue`
  - `d:\code_repo\Chen404\Chen404Fro\src\components\Header\Header.vue`

## 验收标准
- 普通用户看不到分类管理入口；访问 `/admin` 被拒绝。
- 管理员在 `/admin` 可进行分类增删改删（调用 `/categories` 的 POST/PUT/DELETE，后端 `@RequireAdmin` 生效）。
- 个人中心页面结构不受影响，代码无多余死逻辑。