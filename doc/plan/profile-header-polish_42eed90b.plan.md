---
name: profile-header-polish
overview: 优化个人中心“我的文章”头部区域：移除说明提示文案，并将筛选与搜索区改为从左侧起始对齐，让这块区域更紧凑、更整洁。
todos:
  - id: remove-profile-header-desc
    content: 移除我的文章头部提示文案
    status: completed
  - id: left-align-profile-controls
    content: 调整筛选和搜索区为左对齐布局
    status: completed
  - id: verify-profile-header-layout
    content: 验证头部在桌面端和窄屏下的排列效果
    status: completed
isProject: false
---

# 个人中心头部精简优化

## 目标
让个人中心“我的文章”头部更简洁好看：去掉多余的提示文案，并让筛选按钮和搜索区从左侧开始排列，避免当前中间留白过多、视觉重心分散。

## 当前问题
- [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue) 里头部模板仍包含一段灰色说明文字：

```51:80:d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue
<div class="content-header">
  <div class="header-main">
    <div class="header-heading">
      <span class="card-title">...</span>
      <span class="article-total">共 {{ articleTotal }} 篇</span>
    </div>
    <p class="header-desc">统一管理草稿与已发布文章，支持快速筛选与搜索。</p>
  </div>
  <div class="content-actions">
    ...
  </div>
</div>
```

- 样式中 `.content-actions` 用的是 `justify-content: space-between`，`.search-row` 还设成了 `flex: 1` 且 `justify-content: flex-end`，所以搜索区被推到了右侧，导致中间空得比较难看：

```643:678:d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue
.content-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px 18px;
  flex-wrap: wrap;
}

.search-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex: 1;
  justify-content: flex-end;
  min-width: min(420px, 100%);
}
```

## 修改方案
- 在 [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue) 中移除 `header-desc` 这段提示文案。
- 保留标题和“共 N 篇”这一行，让头部信息更精简。
- 把 `.content-actions` 改成左对齐布局。
- 把 `.search-row` 从“占满剩余空间并贴右”改成“跟随筛选按钮一起从左开始排列”。
- 视需要微调搜索框宽度和间距，让头部整体更平衡。

## 影响范围
- 只改 [d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Profile\Profile.vue)。
- 不涉及接口、数据逻辑、分页或文章卡片内容。

## 验证方式
- 进入个人中心“我的文章”，确认灰色提示文字已消失。
- 筛选按钮、搜索输入框、搜索按钮从左向右连续排列，不再被推到右边。
- 窄屏下仍能自动换行，不出现明显挤压或重叠。