---
name: profile-2_3-center
overview: 把个人中心 Profile 页改成“内容占全屏 2/3 居中”的大气布局，并把顶部大横幅头像卡片缩小，放到头像名称附近（保留当前粉色渐变风格）。
todos:
  - id: center-container
    content: 在 `src/views/Profile/Profile.vue` 的模板中新增 `profile-center` 外层容器，把 `.profile-banner` 和 `.profile-main` 包起来，并在 CSS 中实现 `66vw` 居中宽度（含桌面/移动端响应）。
    status: completed
  - id: compact-banner
    content: 在 `src/views/Profile/Profile.vue` 中缩小 `profile-banner` 与头像区域：减小 `el-avatar :size`，收紧 `.profile-avatar / .profile-nickname / .profile-banner padding/margin`，并缩小装饰圆 `.circle-*` 以降低横幅视觉重量。
    status: completed
  - id: align-split-box
    content: 在 `src/views/Profile/Profile.vue` 微调 `.profile-split-scroll-box` 的左右留白（如 `padding-right`），并根据观感轻调 `.article-list` 左右 padding，保证卡片左右留边更美观。
    status: completed
  - id: verify-build
    content: 执行一次前端构建验证（`npx vite build`），并在本地手动检查 Profile 页面：桌面居中宽度、顶部紧凑横幅、左右滚动不再出现错位空缺。
    status: completed
isProject: false
---

## 目标

让个人中心（`Profile.vue`）整体更像参考图：中间内容更宽敞但不铺满全屏，左右功能区左右分隔清晰；顶部头像区域改成更紧凑的“头像+昵称/用户名”小卡片/小条，避免现在的大横幅占用过多视觉重心。

## 实施步骤

### 1. 给 Profile 内容加“居中 2/3 宽度”外层容器

- 修改 [`src/views/Profile/Profile.vue`](src/views/Profile/Profile.vue) ：在现有 `.profile-page` 内，把 `.profile-banner` 和 `.profile-main` 包一层新增容器：`<div class="profile-center">...</div>`。
- 新增/调整 CSS：
- `.profile-page`：使用 `display:flex; flex-direction:column; align-items:center;`，确保内部容器居中。
- `.profile-center`：设置宽度为 `width: 66vw` 并提供 `max-width`（例如 1100px）和 `min-width`（例如 860px，可按你视觉再微调）。
- 在 `@media (max-width: 900px)` 下：把 `.profile-center` 改为 `width: 100%`，保证移动端不挤压。

### 2. 缩小顶部头像卡片（保留渐变风格）

- 修改 [`src/views/Profile/Profile.vue`](src/views/Profile/Profile.vue) 模板：把
- `el-avatar :size="96"` 调小（例如 72 或 64）。
- 同时减少 `.profile-avatar` 的 `margin-bottom` 和字体相关样式。
- 修改 CSS：
- `.profile-banner`：减小 `padding`、可能减小 `border-radius`，让横幅高度更薄。
- `.banner-content`：保持 `text-align:center` 或改为与参考图一致的更紧凑排布（优先维持当前实现的居中，但减少间距）。
- `.circle-*` 装饰圆：缩小尺寸或降低透明度，减少“横幅占视觉过强”的问题。

### 3. 微调左右功能区与滚动 box 的视觉对齐

- 由于我们已经采用“左右在同一滚动结构内”的 `profile-split-scroll-box`，需要避免左右留白过度：
- 检查并调整 [`src/views/Profile/Profile.vue`](src/views/Profile/Profile.vue) 中 `.profile-split-scroll-box` 的 `padding-right: 10px`（必要时改为 `0` 或更小值），保证在 2/3 居中容器内左右对齐更干净。
- 保持 `.article-list` 的左右留边策略（目前在 `.article-list` 上已有 padding），根据观感微调到更舒服的密度。

## 验证方式

- 桌面端：Profile 页面宽度稳定为约 2/3 居中，顶部头像区域不再“横幅过大”，滚动时左侧菜单与右侧内容不回到错位状态。
- 窄屏：`@media (max-width: 900px)` 下容器退化为宽度 100%，布局可读且不遮挡。
- 不影响：首页/阅读模式不会因为 Profile 的 CSS 层级改变（Scoped 样式保证）。