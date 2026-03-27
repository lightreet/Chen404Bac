---
name: article-autosave-plan
overview: 为文章编辑页设计一个仅服务端的自动保存方案：用户输入后在前端做防抖与定时同步，统一保存为草稿，避免页面异常导致大量内容丢失。方案尽量复用现有创建/更新草稿接口，不新增后端接口。
todos:
  - id: extract-draft-save-core
    content: 抽离统一草稿保存核心方法，复用现有 create/update + buildSubmitData 逻辑
    status: completed
  - id: add-autosave-scheduler
    content: 在编辑页增加脏状态、防抖保存、定时兜底和并发合并机制
    status: completed
  - id: add-save-status-ui
    content: 增加自动保存状态提示，并避免自动保存场景频繁弹出消息
    status: completed
  - id: add-leave-guard
    content: 增加路由离开与浏览器关闭前的未同步改动保护
    status: completed
  - id: verify-autosave-flow
    content: 验证新建草稿、编辑草稿、网络失败、发布流程下的自动保存行为
    status: completed
isProject: false
---

# 文章自动保存方案

## 目标
在编写文章页中加入“自动保存为草稿”的能力：用户编辑内容后，系统在合适的节奏下自动把当前整篇文章同步到后端草稿，尽量减少因刷新、崩溃、误关闭导致的内容损失。

## 现状结论
- 前端已有手动草稿保存逻辑，位于 [d:\code_repo\Chen404\Chen404Fro\src\views\Article\ArticleEdit.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Article\ArticleEdit.vue)。
- 前端可直接复用 [d:\code_repo\Chen404\Chen404Fro\src\api\article.ts](d:\code_repo\Chen404\Chen404Fro\src\api\article.ts) 里的 `createArticle()` / `updateArticle()`。
- 后端已有草稿状态支持，入口在 [d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\controller\ArticleController.java](d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\controller\ArticleController.java) 和 [d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\service\impl\ArticleServiceImpl.java](d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\service\impl\ArticleServiceImpl.java)。
- 后端当前 `PUT /articles/{id}` 更适合“整篇全量草稿保存”，不适合只传局部字段，因为更新时会同步标签并清理未使用文件。

## 核心设计
- 复用现有草稿接口，不新增后端 autosave 接口。
- 自动保存始终按“草稿”语义执行：提交前强制 `status = DRAFT`。
- 每次自动保存都发送完整草稿数据，而不是只传变更字段，避免标签丢失、封面/正文图片被误清理。
- 新文章在首次满足保存条件后先 `POST /articles` 创建草稿，成功后立即切换到 `/article/edit/{id}`；后续自动保存统一走 `PUT /articles/{id}`。
- 自动保存采用“双触发”策略：
  - 输入防抖：用户停止输入一小段时间后触发一次保存。
  - 定时兜底：当用户持续输入很久没有停顿时，按固定时间间隔再保存一次。
- 保存状态做轻提示，不使用频繁弹窗：展示“未保存 / 自动保存中 / 已自动保存 / 自动保存失败”。

## 建议交互
- 保存前置条件：至少要求标题非空，和现有“保存草稿”规则保持一致。
- 触发时机：监听标题、正文、摘要、分类、封面、标签、置顶/推荐、状态等会影响草稿内容的字段。
- 避免并发：如果上一次自动保存未完成，不重复发请求；若期间内容又变化，则在当前请求结束后补一次最新保存。
- 页面离开时机：
  - 路由离开前，如果存在未同步改动，主动触发一次立即保存。
  - 浏览器关闭/刷新前，不依赖异步请求一定成功，只给出“正在离开，最近改动可能尚未同步”的原生提示。
- 手动“保存草稿”保留，但和自动保存共用同一套保存核心逻辑，避免两套分叉行为。

## 具体改动点
- [d:\code_repo\Chen404\Chen404Fro\src\views\Article\ArticleEdit.vue](d:\code_repo\Chen404\Chen404Fro\src\views\Article\ArticleEdit.vue)
  - 抽离统一的 `saveDraftCore({ immediate, silent, source })` 方法，供手动保存和自动保存共用。
  - 增加自动保存状态：是否脏数据、是否保存中、最后保存时间、是否需要补一次保存。
  - 增加防抖调度与定时兜底逻辑。
  - 在首次自动保存成功后接入 `router.replace('/article/edit/:id')`，让后续保存都用 update。
  - 增加页面离开保护和轻量状态文案。
- [d:\code_repo\Chen404\Chen404Fro\src\api\request.ts](d:\code_repo\Chen404\Chen404Fro\src\api\request.ts)
  - 仅评估是否需要让自动保存请求支持“静默错误”标记，避免网络抖动时连续 `ElMessage.error` 打断用户；若不动请求层，则在编辑页内自行吞掉自动保存场景的重复提示。
- [d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\service\impl\ArticleServiceImpl.java](d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\service\impl\ArticleServiceImpl.java)
  - 本方案默认不改后端逻辑，只要求前端继续发送完整文章对象。

## 关键实现约束
- 自动保存必须调用和手动草稿保存一致的 `buildSubmitData()`，确保 `content`、`coverImage`、`categoryId`、`tagIds`、`tagNames` 等字段完整。
- 自动保存不能影响“发布文章”流程；发布仍走现有校验和确认逻辑。
- 自动保存成功后，只更新草稿状态提示，不弹成功 toast，避免打断写作。
- 自动保存失败时，应保留脏状态，等待下次自动重试或手动保存。

## 关键代码依据
现有手动草稿保存已经具备可复用入口，只需抽象并加调度层：

```532:553:d:\code_repo\Chen404\Chen404Fro\src\views\Article\ArticleEdit.vue
const handleSaveDraft = async () => {
  if (!form.title?.trim()) {
    ElMessage.warning('请输入文章标题');
    return;
  }

  savingDraft.value = true;
  try {
    const data = buildSubmitData();
    data.status = ArticleStatus.DRAFT;

    if (isEdit.value && articleId.value) {
      await updateArticle(articleId.value, data);
    } else {
      const created = await createArticle(data);
      // 首次保存后切到 /article/edit/:id
    }
  } finally {
    savingDraft.value = false;
  }
};
```

后端更新逻辑要求前端传完整草稿，而不是局部 patch：

```244:250:d:\code_repo\Chen404\Chen404bac\src\main\java\com\chen404\service\impl\ArticleServiceImpl.java
List<Long> resolvedTagIds = resolveTagIds(article);
articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
        .eq(ArticleTag::getArticleId, id));
if (!resolvedTagIds.isEmpty()) {
    saveArticleTags(id, resolvedTagIds);
}
```

## 验证方案
- 新建文章，只输入标题和部分正文，不手动点保存，等待自动保存后刷新页面，确认草稿仍在。
- 新建文章持续输入较长时间，确认会按定时兜底继续同步，不必等完全停下来。
- 编辑已有草稿时修改标题、正文、标签、封面，确认自动保存后重新进入数据完整。
- 网络失败时确认不会疯狂弹错，且页面状态显示“自动保存失败/未保存”。
- 发布文章后确认不会被自动保存重新覆盖成草稿。

## 风险与取舍
- 仅服务端方案仍无法覆盖“首次自动保存之前页面就崩溃”的极端场景，因为没有本地缓存兜底。
- 自动保存请求过于频繁会增加后端压力，因此需要合理防抖和并发合并。
- 由于当前后端缺少“作者校验”，自动保存本身不新增此风险，但这是现有文章写接口的独立安全问题，后续可单独修复。