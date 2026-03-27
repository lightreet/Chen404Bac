# Chen404 历史计划去重总表

> 依据 `C:\Users\Chen\.cursor\plans` 扫描结果整理  
> 基准时间：2026-03-26

## 1) 主题去重结果

| 主题 | 历史份数 | 代表计划（最新） | 当前状态 | 整改结论 |
|---|---:|---|---|---|
| 评论功能实现 | 1 | `评论功能实现_f6a2311f.plan.md` | Done | 已落地；并按新要求改为“默认直接发布 + 回复一致性校验 + 级联删除” |
| 博客权限设计 | 2 | `博客权限设计_6bcbe4f5.plan.md` | Partial | 核心模型已落地（`trust_level`/`comment_policy`/`visibility`）；后续完善见遗留 |
| 首页海浪波纹 | 1 | `首页海浪波纹_c4531059.plan.md` | Obsolete | 该方向已在后续需求中回退，不再作为当前基线 |
| 优化首页波线 | 1 | `优化首页波线_b00e6446.plan.md` | Obsolete | 属于海浪二次迭代，因回退策略不再执行 |
| 动漫风樱花优化 | 3 | `动漫风樱花优化_0dd812d5.plan.md` | Done | 相关视觉迭代已完成，最终保留版本以当前代码为准 |
| 动漫樱花轮廓与配色增强 | 2 | `动漫樱花轮廓与配色增强_fd512067.plan.md` | Done | 已被后续迭代吸收 |
| 调整个人中心布局 | 3 | `调整个人中心布局_a3b2209a.plan.md` | Partial | 已有改动但历史分支多，建议以页面现状做一次收敛验收 |
| profile-*（header/scroll/center/sync） | 7 | `profile-3_4-center_637e642a.plan.md`（同组最新） | Partial | 与“个人中心”主题重复，合并为一个主题管理 |
| article-autosave-plan | 2 | `article-autosave-plan_a7602e71.plan.md` | Done | `ArticleEdit.vue` 已存在自动保存状态与流程 |
| 博客分类添加与权限设计 | 6 | `博客分类添加与权限设计_69078ada.plan.md` | Partial | 权限框架在后端已具备；分类端到端能力建议补一次联调验收 |
| admin_migration_categories | 3 | `admin_migration_categories_37ae7f86.plan.md` | Partial | 与“分类权限”主题重叠，合并追踪 |
| 清单页分类动态与加载更多 | 3 | `清单页分类动态与加载更多_22c5d0e2.plan.md` | Partial | 需复核前端清单页交互是否完全符合计划 |
| unocss_home_ui | 3 | `unocss_home_ui_4e0b4e21.plan.md` | Partial | 首页已多次重构，建议按当前 UI 基线重写轻量计划 |
| 个人中心页面搭建 | 6 | `个人中心页面搭建_7aa6b1ab.plan.md` | Partial | 与上方 profile/布局计划重复，合并归一 |

## 2) 与当前项目代码对齐后的整改

### 已完成整改（本轮已确认）

- 评论链路以当前代码为准，覆盖：
  - 评论树查询、留言板评论、最新评论
  - 评论创建/删除/点赞
  - 回复创建上下文一致性（父评论与 `articleId` 必须一致）
  - 删除父评论时级联删除子孙评论
  - 评论默认直接发布（不再走待审核）

### 已过期计划处理策略

- “首页海浪波纹 / 优化首页波线”归档为 **Obsolete**，不再继续执行。
- 若后续恢复该方向，建议新建单一计划文件，避免继续在旧计划上叠加。

### 重复主题合并策略

- “个人中心页面搭建 + 调整个人中心布局 + profile-*”合并为一个主题。
- “博客分类添加与权限设计 + admin_migration_categories”合并为一个主题。
- 后续统一只保留一个活跃计划，历史版本仅作参考。

## 3) 计划文件“现在在哪”

- 历史原件：`C:\Users\Chen\.cursor\plans`
- 去重归档：`Chen404Bac/doc/plan/`

