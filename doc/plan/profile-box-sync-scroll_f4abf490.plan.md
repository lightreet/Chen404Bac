---
name: profile-box-sync-scroll
overview: 把个人中心的左侧菜单与右侧文章卡片放入同一个可滚动box内，让左右内容同步滚动避免空缺；同时给文章卡片左右加内边距并进一步缩小管理态卡片。
todos:
  - id: todo-sync-scroll-box
    content: 在 `src/views/Profile/Profile.vue` 中把左侧菜单 + 右侧文章区放入同一个可滚动 box，设置 `overflow-y:auto`，并让二者同步滚动。
    status: completed
  - id: todo-disable-inner-scroll
    content: 在 `Profile.vue` 中禁用 `.article-scroll-area` 的内层滚动（移除 `max-height/overflow`），让文章列表随外层 box 同步滚动。
    status: completed
  - id: todo-add-card-gutters
    content: 在 `Profile.vue` 中为文章卡片区域增加左右留边（对 `.article-scroll-area` 或 `.article-list` 增加 padding）。
    status: completed
  - id: todo-shrink-manage-cards
    content: 在 `src/components/ArticleCard/ArticleCard.vue` 中进一步缩小 `.article-card.compact.manage-mode`（padding、标题、摘要、封面宽高与无封面 min-height）。
    status: completed
  - id: todo-verify-layout
    content: 验证桌面端/窄屏下：左侧不再出现空缺错位；右侧卡片留边到位且密度更紧凑；首页/阅读模式不受影响。
    status: completed
isProject: false
---

# 个人中心同步滚动与卡片美化

## 目标

1. 修复左侧菜单在滚动下滑时产生“空缺/错位”的问题。
2. 让左侧菜单与右侧文章卡片在同一个布局容器里同步滚动（避免右侧内层滚动导致高度不同步）。
3. 给文章卡片左右增加留边，让卡片观感更紧凑，并适当缩小管理态卡片。

## 当前问题定位（基于现有实现）

- `Profile.vue` 当前“我的文章”用了右侧的内层滚动容器：
- `.article-scroll-area` 设定了 `max-height` + `overflow-y:auto`（见 [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue) 中的 `.article-scroll-area`）。
- 左侧菜单使用 `position: sticky`（桌面端）或在断点处退化（见同文件 `.profile-nav` 与媒体查询）。
- 当右侧内层滚动与外层滚动不同步时，容易出现你截图里那种“左侧看起来像错位/空缺”的观感。

## 实施方案（选择 A：左右同步滚动）

- 把左侧菜单 + 右侧“我的文章”区域放进同一个滚动box：
- 在 [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue) 中，把 `.profile-main`（或新增一个包裹 div，例如 `.profile-split-scroll-box`）改成单一滚动容器：`overflow-y:auto`。
- 左侧菜单不再依赖 sticky 作为主要机制（桌面端与移动端都用同一套稳定布局），避免 sticky 边界造成“突然空缺”。
- 禁用右侧“我的文章”的内层滚动：
- 在 [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue) 中，把 `.article-scroll-area` 改为不限制高度、`overflow: visible`，让文章列表随外层 box 同步滚动。

## 卡片留边与缩小

- 在右侧列表区增加左右留边（推荐做法）：
- 给 `.article-list` 或 `.article-scroll-area` 加左右 `padding`（例如 16px），让卡片不贴边。
- 管理态卡片进一步缩小（不影响首页/阅读模式）：
- 在 [d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue](d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue) 里，只调整 `.article-card.compact.manage-mode`（以及必要的 `.no-cover` min-height）：
- 降低 `.card-content` padding
- 缩小 `.card-image` 宽度与 `min-height`
- 将 `.article-title` 与 `.article-summary` 再压缩一档（例如 title 16–17px，summary 行数 2）

## 关键改动点（文件级）

1. `Profile` 布局/滚动容器

- `d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue`
- 改 `.profile-main`（或新增 `.profile-split-scroll-box`）为单一滚动box。
- 移除/减弱 `.profile-nav` 的 sticky（桌面端同一机制）。
- 禁用 `.article-scroll-area` 的内层滚动。
- 增加文章列表左右留边。

2. `ArticleCard` 管理态密度

- `d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue`
- 只收紧 `.article-card.compact.manage-mode` 的间距与尺寸。

## 验证方式

- 桌面端滚动：左侧菜单与右侧文章卡片同一滚动容器内同步滚动，无空缺错位。
- 文章数量较多时：页面高度显著变长，但左右布局始终对齐。
- 文章卡片左右留边：卡片不会贴着内容区边缘。
- 不回归：首页文章列表（mode/home）与阅读卡片不被影响。
- 移动端：在窄屏下仍能正常显示，不会因为取消 sticky 导致可用性下降。

## 风险与取舍

- 将右侧内层滚动改为外层同步滚动后，右侧“header 固定在卡片顶部”的体验会弱一些（如需要可再用 `position: sticky` 只固定卡片 header，但不做内层滚动）。
- 单一滚动box的高度需要合理设置（通常用 `flex` + `min-height:0` 来避免溢出）。