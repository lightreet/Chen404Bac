# API 接口设计补充文档

> 本文档记录首页和登录注册模块接口设计中的缺陷、遗漏和补充建议，供后端开发参考。

---

## 一、发现的问题

### 1. 类型定义错误

**问题位置**: `src/api/home.ts` 第 67-74 行

**问题描述**: `getRecommendArticles` 返回类型定义错误，返回的是单个对象，但实际应该是数组。

```typescript
// 错误代码
export function getRecommendArticles(limit: number = 6): Promise<{
  id: number;
  title: string;
  coverImage?: string;
  summary?: string;
}> {
  return get('/articles/recommend', { limit });
}
```

**修正后**:
```typescript
export interface RecommendArticle {
  id: number;
  title: string;
  coverImage?: string;
  summary?: string;
}

export function getRecommendArticles(limit: number = 6): Promise<RecommendArticle[]> {
  return get('/articles/recommend', { limit });
}
```

---

### 2. 接口缺失 - 图形验证码

**问题描述**: 登录接口有一个可选的 `captcha` 参数，但缺少获取图形验证码的接口。

**建议补充**:

```typescript
/**
 * 获取图形验证码
 * @returns 验证码图片 URL 和验证标识
 */
export function getCaptcha(): Promise<{
  captchaKey: string;      // 验证码标识，用于验证时回传
  captchaImage: string;    // Base64 编码的图片，如：data:image/png;base64,xxx
}> {
  return get('/auth/captcha');
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "captchaKey": "abc123-def456",
    "captchaImage": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
  }
}
```

**登录接口调整**:
登录时需要将 `captchaKey` 和 `captcha` 一起提交：
```typescript
export interface LoginParams {
  username: string;
  password: string;
  captcha?: string;
  captchaKey?: string;  // 新增：验证码标识
}
```

---

### 3. 接口缺失 - 分类列表

**问题描述**: 首页侧边栏通常需要展示分类列表，但缺少获取分类列表的接口。

**建议补充**:

```typescript
// src/api/home.ts

/**
 * 获取文章分类列表
 * @param withCount 是否包含文章数量，默认 true
 */
export function getCategories(withCount: boolean = true): Promise<Category[]> {
  return get('/categories', { withCount });
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `GET /categories` |
| 查询参数 | `withCount` - 是否包含文章数量，默认 true |

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "前端开发",
      "slug": "frontend",
      "description": "前端技术相关文章",
      "icon": "icon-code",
      "articleCount": 25,
      "sortOrder": 1
    }
  ]
}
```

---

### 4. 接口缺失 - 标签列表

**问题描述**: 首页侧边栏通常需要展示标签云，但缺少获取标签列表的接口。

**建议补充**:

```typescript
// src/api/home.ts

/**
 * 获取标签列表
 * @param withCount 是否包含文章数量，默认 true
 */
export function getTags(withCount: boolean = true): Promise<Tag[]> {
  return get('/tags', { withCount });
}

/**
 * 获取热门标签（按文章数量排序）
 * @param limit 数量限制，默认 20
 */
export function getHotTags(limit: number = 20): Promise<Tag[]> {
  return get('/tags/hot', { limit });
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `GET /tags` |
| 查询参数 | `withCount` - 是否包含文章数量，默认 true |

| 项目 | 内容 |
|------|------|
| 接口地址 | `GET /tags/hot` |
| 查询参数 | `limit` - 数量限制，默认 20 |

---

### 5. 接口缺失 - 归档数据

**问题描述**: 类型定义中已有 `ArchiveYear` 和 `ArchiveMonth`，但缺少获取归档数据的接口。

**建议补充**:

```typescript
// src/api/home.ts

/**
 * 获取文章归档数据（按年月分组）
 */
export function getArchives(): Promise<ArchiveYear[]> {
  return get('/archives');
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `GET /archives` |
| 描述 | 获取按年月分组的文章归档数据 |

**响应数据**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "year": 2024,
      "count": 25,
      "months": [
        {
          "month": 3,
          "count": 8,
          "articles": [
            {
              "id": 1,
              "title": "文章标题",
              "publishTime": "2024-03-15T10:30:00"
            }
          ]
        }
      ]
    }
  ]
}
```

---

### 6. 接口缺失 - 友链列表

**问题描述**: 类型定义中已有 `FriendLink`，但缺少获取友链列表的接口。

**建议补充**:

```typescript
// src/api/friend.ts

/**
 * 获取友链列表（只返回已批准的）
 */
export function getFriendLinks(): Promise<FriendLink[]> {
  return get('/friends');
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `GET /friends` |
| 描述 | 获取已批准的友链列表 |

---

### 7. 接口缺失 - 用户资料更新

**问题描述**: 有获取用户信息的接口，但缺少更新用户资料的接口。

**建议补充**:

```typescript
// src/api/auth.ts

export interface UpdateProfileParams {
  nickname?: string;
  avatar?: string;
  email?: string;
  phone?: string;
  bio?: string;  // 个人简介
}

/**
 * 更新当前用户信息
 */
export function updateProfile(params: UpdateProfileParams): Promise<User> {
  return post('/auth/profile', params);
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `POST /auth/profile` |
| 请求头 | `Authorization: Bearer {token}` |
| 描述 | 更新当前登录用户的资料 |

---

### 8. 接口缺失 - 头像上传

**问题描述**: 用户更新头像需要上传文件接口。

**建议补充**:

```typescript
// src/api/upload.ts

/**
 * 上传用户头像
 * @param file 图片文件
 */
export function uploadAvatar(file: File): Promise<{
  url: string;  // 上传后的头像URL
}> {
  const formData = new FormData();
  formData.append('file', file);
  return post('/upload/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `POST /upload/avatar` |
| 请求头 | `Authorization: Bearer {token}`, `Content-Type: multipart/form-data` |
| 请求体 | `file` - 图片文件（限制大小如 2MB，格式 jpg/png） |

---

### 9. 接口缺失 - 文章搜索

**问题描述**: 虽然 `ArticleQueryParams` 定义了 `keyword` 参数，但缺少专门的搜索接口。

**建议补充**:

```typescript
// src/api/article.ts

export interface SearchParams extends PageParams {
  keyword: string;
  categoryId?: number;
  tagId?: number;
}

/**
 * 搜索文章
 */
export function searchArticles(params: SearchParams): Promise<PageResult<Article>> {
  return get('/articles/search', params);
}
```

**接口文档**:

| 项目 | 内容 |
|------|------|
| 接口地址 | `GET /articles/search` |
| 查询参数 | `keyword` - 搜索关键词（必填）<br>`categoryId` - 分类ID筛选（可选）<br>`tagId` - 标签ID筛选（可选）<br>`page` - 页码，默认1<br>`size` - 每页数量，默认10 |

---

### 10. 接口设计优化 - 文章列表

**问题描述**: 原设计中文章列表在首页模块，建议统一到文章模块。

**建议调整**:

```typescript
// src/api/article.ts - 新增

/**
 * 获取文章列表
 * @param params 查询参数
 */
export function getArticles(params: ArticleQueryParams): Promise<PageResult<Article>> {
  return get('/articles', params);
}

/**
 * 获取文章详情
 * @param id 文章ID
 */
export function getArticleById(id: number): Promise<Article> {
  return get(`/articles/${id}`);
}
```

---

## 二、接口完善建议

### 2.1 热门文章排序策略

**建议**: 明确热门文章的排序算法

```typescript
// 建议后端支持多种排序方式
export interface HotArticleQuery {
  limit?: number;
  period?: 'day' | 'week' | 'month' | 'all';  // 时间范围
  sortBy?: 'views' | 'comments' | 'likes';     // 排序依据
}
```

**接口调整**:
```typescript
export function getHotArticles(
  limit: number = 10,
  period: 'day' | 'week' | 'month' | 'all' = 'week'
): Promise<HotArticle[]> {
  return get('/articles/hot', { limit, period });
}
```

### 2.2 轮播图筛选

**建议**: 轮播图接口增加状态筛选

```typescript
export function getBanners(position?: 'home' | 'article'): Promise<Banner[]> {
  return get('/site/banners', { position });
}
```

---

## 三、后端开发建议

### 3.1 统一响应格式

所有接口必须统一返回格式：
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 3.2 错误码规范

建议的错误码定义：

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（Token无效或过期） |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如用户名已存在） |
| 422 | 业务逻辑错误（如验证码错误） |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

### 3.3 Token 刷新策略

建议实现：
1. Access Token 有效期：2小时
2. Refresh Token 有效期：7天
3. Token 刷新窗口：Refresh Token 过期前可续期
4. 单点登录：新设备登录时，旧设备 Token 失效（可选）

### 3.4 验证码安全

1. 图形验证码：
   - 有效期：5分钟
   - 验证失败后立即失效
   - 错误3次后强制要求验证码

2. 短信/邮箱验证码：
   - 有效期：5分钟
   - 同一手机号/邮箱每天最多发送5条
   - 发送间隔：60秒

### 3.5 接口限流建议

| 接口 | 限流策略 |
|------|----------|
| 登录 | 同一IP每分钟5次 |
| 发送验证码 | 同一手机号/邮箱每60秒1次，每天5次 |
| 注册 | 同一IP每小时10次 |
| 搜索 | 同一IP每分钟30次 |
| 发表评论 | 同一IP每分钟10次 |

---

## 四、补充接口清单

以下是建议后端优先实现的接口（按优先级排序）：

### P0 - 核心必需
1. `GET /auth/captcha` - 图形验证码
2. `GET /categories` - 分类列表
3. `GET /tags` - 标签列表
4. `GET /archives` - 归档数据
5. `GET /friends` - 友链列表

### P1 - 重要功能
6. `GET /articles` - 文章列表
7. `GET /articles/:id` - 文章详情
8. `GET /articles/search` - 文章搜索

### P2 - 用户体验
9. `POST /auth/profile` - 更新用户资料
10. `POST /upload/avatar` - 头像上传

---

## 五、前端同步更新

后端开发完成后，前端需要同步更新以下文件：

1. `src/api/auth.ts` - 添加 `getCaptcha` 和 `updateProfile`
2. `src/api/home.ts` - 添加 `getCategories`, `getTags`, `getArchives`
3. `src/api/article.ts` - 添加 `getArticles`, `getArticleById`, `searchArticles`
4. `src/api/friend.ts` - 添加 `getFriendLinks`
5. `src/api/upload.ts` - 添加 `uploadAvatar`
6. `src/types/index.ts` - 完善类型定义

---

## 六、文章编写功能补充

### 6.1 新增页面和组件

| 文件 | 说明 |
|------|------|
| `src/views/Article/ArticleEdit.vue` | 文章编辑页面（新增/编辑） |
| `src/stores/user.ts` | 用户状态管理（新增） |

### 6.2 路由更新

新增路由：

```typescript
{
  path: '/article/edit/:id?',
  name: 'ArticleEdit',
  component: () => import('@/views/Article/ArticleEdit.vue'),
  meta: {
    title: '编写文章',
    requiresAuth: true, // 需要登录
  },
}
```

### 6.3 新增依赖

文章编辑使用 `md-editor-v3` 作为 Markdown 编辑器：

```bash
npm install md-editor-v3
```

### 6.4 前端API更新

文章编辑页面使用以下API：

```typescript
// 获取分类列表
const categories = await getCategories();

// 获取标签列表
const tags = await getTags();

// 创建文章
const article = await createArticle({
  title: '文章标题',
  content: 'Markdown内容',
  categoryId: 1,
  tagIds: [1, 2, 3],
  status: ArticleStatus.PUBLISHED,
});

// 更新文章
await updateArticle(articleId, { ... });

// 上传图片
const { url } = await uploadImage(file);
```

### 6.5 后端需实现接口

| 优先级 | 接口 | 说明 |
|--------|------|------|
| P0 | POST /admin/articles | 创建文章 |
| P0 | PUT /admin/articles/{id} | 更新文章 |
| P0 | DELETE /admin/articles/{id} | 删除文章 |
| P0 | POST /upload/image | 上传图片 |
| P1 | POST /upload/images | 批量上传图片 |
| P1 | POST /admin/categories | 创建分类 |
| P1 | POST /admin/tags | 创建标签 |

### 6.6 注意事项

1. **图片上传**：
   - 支持拖拽上传
   - 支持粘贴上传
   - 自动压缩图片

2. **自动保存**：
   - 建议实现自动保存草稿功能（每30秒）
   - 使用 localStorage 作为本地备份

3. **文章状态**：
   - DRAFT(0): 草稿，仅作者可见
   - PUBLISHED(1): 已发布，所有人可见
   - 切换状态时会更新 publishTime

---

*文档版本: 1.1*
*最后更新: 2026-03-16*
