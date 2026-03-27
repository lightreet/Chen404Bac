---
name: profile-scroll-layout
overview: 修复个人中心左侧菜单滚动不美观的问题，并把“我的文章”改成卡片内部滚动区域，同时缩小管理态文章卡片高度，让页面更像稳定的后台面板。
todos:
  - id: stabilize-profile-sidebar
    content: 调整个人中心左侧菜单的 sticky 与断点策略，修复随页面滚动的问题
    status: completed
  - id: split-article-scroll-area
    content: 将我的文章卡片拆分为头部、内层滚动列表、底部分页结构
    status: completed
  - id: shrink-manage-cards
    content: 收紧个人中心管理态文章卡片的尺寸与排版
    status: completed
  - id: verify-profile-scroll-layout
    content: 验证桌面端侧栏稳定、右侧内层滚动、分页位置与响应式表现
    status: completed
isProject: false
---

# 个人中心滚动与列表布局优化

## 目标
让个人中心左侧菜单在桌面端保持稳定，不再随着页面正文一起显得“飘”；同时将“我的文章”区域改成卡片内部滚动展示，减少整页纵向拉长，并把文章卡片压缩得更紧凑。

## 问题原因
- 左侧菜单本身在 [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue) 使用了 `position: sticky`，但在当前样式里，`1024px` 以下会被明确改成 `position: static`，所以会跟着页面一起滚。
- “我的文章”当前仍然是页面级滚动，结构上是卡片头部 + 整个列表 + 分页在同一个普通流里，没有单独的内层滚动容器。
- 管理态文章卡片复用了较大的 `compact.manage-mode` 样式，标题、摘要、封面宽度和无封面最小高度都偏大，所以列表显得松散。

## 方案
- 左侧菜单：在桌面端保持真正稳定的侧边导航，不再只依赖现在这套易受断点影响的 sticky 行为。
  - 优先方案：保留两栏整体布局，但让桌面端 `.profile-nav` 始终使用稳定的 sticky 规则，并单独校正断点行为。
  - 窄屏端仍然退化为普通流式布局，避免小屏固定侧栏占空间。
- “我的文章”：把文章区域做成单张大卡片。
  - 卡片头部保留“我的文章 + 筛选 + 搜索 + 总数”。
  - 卡片 body 内新增一个专门的滚动容器，只包裹文章列表。
  - 分页保留在滚动容器外、卡片底部，避免滚动时找不到分页。
- 管理态卡片：只缩小 `Profile` 页使用到的管理态样式，不影响首页等普通文章卡片。
  - 缩小封面宽度。
  - 缩小标题字号、内边距、摘要行数。
  - 降低无封面卡片的最小高度。
  - 让操作区更贴近卡片底部，整体更像后台列表卡片。

## 关键改动点
- [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue)
  - 调整左侧 `.profile-nav` 的 sticky/断点策略。
  - 重构“我的文章”卡片 body：
    - 新增 `article-scroll-area` 之类的滚动容器，只承载文章列表。
    - 将分页移到滚动区域外。
  - 给右侧内容区设置更明确的高度约束，例如基于视口高度的 `max-height`，让内层滚动真正生效。
- [d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue](d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue)
  - 收紧 `.article-card.compact.manage-mode`：
    - 更小的 `padding`
    - 更小的 `.article-title`
    - 更短的 `.article-summary`
    - 更窄的 `.card-image`
    - 更低的 `no-cover` 最小高度
  - 保证这些调整只影响 `mode="manage" + compact` 的个人中心文章管理场景。

## 关键代码依据
当前左侧菜单之所以会随页面滚，是因为断点下直接禁用了 sticky：

```645:653:d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue
@media (max-width: 1024px) {
  .profile-main {
    grid-template-columns: 1fr;
  }

  .profile-nav {
    position: static;
    display: block;
  }
}
```

当前文章列表和分页还在同一个普通流里，不适合直接做“只滚文章、不滚分页”的内层滚动：

```81:107:d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue
<div v-else class="article-list">
  <div v-if="myArticles.length === 0" class="empty-state">
    暂无文章
  </div>

  <ArticleCard
    v-for="(a, idx) in myArticles"
    :key="String(a.id)"
    :article="a"
    :index="idx"
    mode="manage"
    compact
  />

  <div class="pager">
    <el-pagination ... />
  </div>
</div>
```

当前管理态卡片偏大的主因集中在这段样式：

```248:309:d:\code_repo\Chen404\Chen404Fro\src\components\ArticleCard\ArticleCard.vue
.article-card.compact.manage-mode {
  .card-content {
    padding: 22px 24px;
  }

  .article-title {
    font-size: 20px;
  }

  .article-summary {
    -webkit-line-clamp: 3;
  }

  .card-image {
    width: 250px;
    min-height: 210px;
  }
}
```

## 验证方式
- 桌面端进入个人中心，滚动页面时左侧菜单保持稳定，不再和正文一起显得漂移。
- “我的文章”卡片内部可以独立滚动浏览文章，分页固定在卡片底部。
- 文章较多时，页面整体高度明显收敛，不再整页过长。
- 无封面和有封面的文章卡片都比现在更紧凑，但信息仍可读。
- 窄屏下确认左侧导航和右侧列表不会因为内层滚动而出现操作困难。

## 风险与取舍
- 内层滚动会让页面变得更像后台面板，阅读型页面感会减少，但更适合“文章管理”。
- 如果滚动区域高度设得太小，会让分页和列表显得拥挤；需要选一个兼顾视口高度和可读性的阈值。
- 左侧菜单若在较小宽度仍强行 sticky，会压缩内容区，因此断点需要谨慎保留移动端退化逻辑。