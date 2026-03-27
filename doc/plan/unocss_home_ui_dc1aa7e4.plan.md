---
name: UnoCSS home UI
overview: Integrate UnoCSS in the Vue3+Vite frontend and refactor Home (Header + Hero + article list) to a cohesive Japanese light-pink anime aesthetic using a theme-first approach (tokens + shortcuts), without changing existing functionality.
todos:
  - id: unocss-install
    content: Add UnoCSS deps and Vite plugin; import uno.css
    status: completed
  - id: unocss-theme
    content: Create uno.config.ts with tokens + shortcuts for jp-pink anime style
    status: completed
  - id: home-hero
    content: Refactor Home.vue to add Hero section and apply shortcuts without changing logic
    status: completed
  - id: card-unify
    content: Apply jp-card/jp-chip styling to ArticleCard and reduce color noise
    status: completed
  - id: header-touchup
    content: Lightly align Header styling to the same design language
    status: completed
  - id: smoke-check
    content: Build and quick visual check Home + dark mode
    status: cancelled
isProject: false
---

# UnoCSS 接入 + 首页（日系浅粉动漫风）改造计划

## 目标与约束

- **目标**：先把首页做出稳定的“日系浅粉动漫主体风”（更柔和的留白、卡片、渐变、装饰元素），并形成可复用的风格基础。
- **范围（方案 B）**：`Header` + `Home Hero` + 首页文章卡片列表（不改接口、不改业务逻辑）。
- **策略（主题化）**：先建立统一 token/shortcuts，再把首页组件逐步替换为 UnoCSS class；保留现有 SCSS 与 CSS 变量体系（`--primary` 等），避免大改。

## 当前现状（已调研）

- 首页目前在 `d:\code_repo\Chen404\Chen404Fro\src\views\Home\Home.vue`，结构简单：标题 + `ArticleCard` 列表 + Load more。
- 全局主题变量在 `d:\code_repo\Chen404\Chen404Fro\src\assets\styles\variables.scss`，主色已是樱花粉：`--primary: #fb7299`。
- 项目尚未集成 UnoCSS；`vite.config.ts` 目前只含 Vue 插件。

## 实施方案

### 1) 集成 UnoCSS（最小侵入）

- **新增依赖**（devDependencies）：
- `unocss`
- `@unocss/preset-wind`
- `@unocss/preset-icons`（可选：统一图标风格，避免多图标源混用）
- `@unocss/transformer-directives`（可选：`@apply` 风格整合）
- **新增配置文件**：`d:\code_repo\Chen404\Chen404Fro\uno.config.ts`
- `presetWind()` 提供类 Tailwind 体验
- `theme` 中用 CSS 变量桥接你的既有配色（如 `colors: { brand: 'var(--primary)' }`）
- 定义 `shortcuts`（核心）：
  - `jp-page`：浅底背景 + 柔和纹理
  - `jp-card`：白色/半透明卡片、rounded-xl、shadow-sm、hover 阴影
  - `jp-chip`：灰粉系简洁 chip（统一圆角/字号/间距）
  - `jp-hero`：顶部 Hero 容器（渐变、光斑、樱花点点装饰）
  - `jp-btn-primary` / `jp-btn-secondary`：按钮层级统一（只突出一个主色）
- **Vite 接入**：修改 `d:\code_repo\Chen404\Chen404Fro\vite.config.ts` 添加 UnoCSS 插件。
- **入口引入**：在 `d:\code_repo\Chen404\Chen404Fro\src\main.ts`（或等效入口）引入 `uno.css`。

### 2) 首页布局改造（Home.vue）

目标：不改数据加载逻辑，仅增强视觉层级。

- `Home.vue` 分为三块：
- **Hero（新增）**：
  - 标题（Discovery）+ 副标题（偏动漫语气）+ 轻量 CTA（例如“写一篇文章”按钮：已登录才显示，不改权限）
  - 背景装饰：圆形渐变 blob + 轻微噪点/网格（用 CSS/渐变实现，避免引入大图）
- **文章列表区**：
  - 外层容器用 `jp-card`/`jp-section` 做统一留白与边界
  - Load more / no more 文案与按钮统一用 `jp-btn-*`
- 保留原来的 `getArticles / loadMore / hasMore` 逻辑不变。

### 3) 文章卡片风格统一（ArticleCard.vue）

- 不改交互（整卡点击、阅读详情、作者展示等保持）。
- 用 UnoCSS `shortcuts` 给卡片加“日系浅粉卡片风”外观：
- 边框更轻、阴影更柔、hover 不抖动（仅阴影/描边变化）
- tag 渲染改为统一 `jp-chip`（减少现在 `tag.color` 导致的多色混乱；仍可保留微弱色点作为可选）

### 4) Header 轻量适配（不破坏现有结构）

- 在 `d:\code_repo\Chen404\Chen404Fro\src\components\Header\Header.vue`：
- 保留现有布局与功能
- 用 `jp-card`/`jp-glass` 风格轻量增强：滚动态背景更柔和、边框更轻
- 右侧按钮（搜索/主题）统一 hover 与圆角尺度

## 关键文件清单

- **新增**
- `d:\code_repo\Chen404\Chen404Fro\uno.config.ts`
- **修改**
- `d:\code_repo\Chen404\Chen404Fro\vite.config.ts`
- `d:\code_repo\Chen404\Chen404Fro\src\main.ts`（引入 `uno.css`）
- `d:\code_repo\Chen404\Chen404Fro\src\views\Home\Home.vue`
- `d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue`
- `d:\code_repo\Chen404\Chen404Fro\src\components\Header\Header.vue`

## 不做的事（避免影响功能）

- 不替换 Element Plus
- 不改接口、不改路由、不改鉴权
- 不大规模重写全站 SCSS；仅首页相关组件渐进迁移

## 验收标准

- 首页视觉形成统一“浅粉动漫风”：Hero + 卡片 + 标签 chip + 按钮层级一致。
- 所有原功能（加载更多、跳转详情、作者/分类/标签展示、Header 登录态等）无回归。
- UnoCSS 仅生成用到的样式；构建通过。