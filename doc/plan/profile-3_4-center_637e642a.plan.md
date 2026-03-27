---
name: profile-3_4-center
overview: 把 Profile 参考图布局的居中面板宽度从 2/3 调整为 3/4，并移除顶部横幅，继续保持左右并排与同步滚动。
todos: []
isProject: false
---

## 目标
把个人中心 Profile 页整体调整为参考图风格：顶部横幅移除、内容居中面板占页面宽度的 3/4（桌面），并保持左右菜单+右侧内容的左右分隔布局与同步滚动表现。

## 实施步骤
### 1) 移除顶部头像横幅（必须）
- 在 [`src/views/Profile/Profile.vue`](src/views/Profile/Profile.vue) 的模板中删除 `div.profile-banner`（粉色横幅那块）。
- 对应的样式类（如 `banner-inline / banner-text / profile-avatar / circle-*`）不做强制删除；先保证布局编译与视觉正确。

### 2) 调整“居中面板”占宽为 3/4
- 在 [`src/views/Profile/Profile.vue`](src/views/Profile/Profile.vue) 中使用 `profile-center` 作为居中面板容器。
- 修改 `profile-center` 的桌面宽度为 3/4：
  - 建议：`width: clamp(920px, 75vw, 1200px);`
  - 移动端保持：`@media (max-width: 900px) { width: 100%; }`

### 3) 面板内保留左右分隔 + 同步滚动结构
- 继续使用现有 `profile-split-scroll-box` 结构：左右菜单 `profile-nav` 与右侧 `profile-content` 放在同一滚动容器内。
- 若移除横幅后出现底部缝隙/滚动阈值不匹配，则微调 `profile-split-scroll-box` 的 `max-height: calc(...)` 值，使它与新布局匹配。

### 4) 验证
- `npx vite build` 通过。
- 手动确认：
  - 桌面端面板占宽更符合参考图（更大气）
  - 左侧菜单不再出现“碎/空缺”错位观感
  - 右侧文章卡片左右留边仍然保留
  - 移动端依旧可用（不挤压、不遮挡）

## 关键文件
- [`src/views/Profile/Profile.vue`](src/views/Profile/Profile.vue)