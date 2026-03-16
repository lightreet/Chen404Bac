# Chen404 博客系统 - 接口文档

## 基础信息

| 项目 | 内容 |
|------|------|
| 基础URL | `http://localhost:8080/api` (开发环境) |
| 协议 | HTTP/HTTPS |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

## 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 响应状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（Token无效或过期） |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 一、认证模块

### 1.1 用户登录

**接口地址**：`POST /auth/login`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名/邮箱/手机号 |
| password | string | 是 | 密码（MD5加密后） |
| captcha | string | 否 | 图形验证码（可选） |

**请求示例**：
```json
{
  "username": "chen@example.com",
  "password": "e10adc3949ba59abbe56e057f20f883e"
}
```

**响应数据**：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expires": 7200,
    "user": {
      "id": 1,
      "username": "chen",
      "nickname": "Chen",
      "email": "chen@example.com",
      "phone": "13800138000",
      "avatar": "https://example.com/avatar.jpg",
      "role": 0,
      "status": 1
    }
  }
}
```

**错误响应**：

```json
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": null
}
```

---

### 1.2 用户注册

**接口地址**：`POST /auth/register`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名（3-20位字母数字下划线） |
| password | string | 是 | 密码（6-20位） |
| nickname | string | 否 | 昵称（默认使用用户名） |
| email | string | 条件 | 邮箱（邮箱注册时必填） |
| phone | string | 条件 | 手机号（手机注册时必填） |
| code | string | 条件 | 验证码（手机注册时必填） |
| registerType | string | 是 | 注册类型：`email` / `phone` |

**请求示例**（邮箱注册）：
```json
{
  "username": "newuser",
  "password": "password123",
  "nickname": "新用户",
  "email": "newuser@example.com",
  "registerType": "email"
}
```

**请求示例**（手机注册）：
```json
{
  "username": "newuser",
  "password": "password123",
  "nickname": "新用户",
  "phone": "13800138000",
  "code": "123456",
  "registerType": "phone"
}
```

**响应数据**：

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 2,
    "username": "newuser",
    "nickname": "新用户",
    "email": "newuser@example.com",
    "phone": null,
    "avatar": "default-avatar.jpg",
    "role": 0,
    "status": 1,
    "createTime": "2024-03-15T10:30:00"
  }
}
```

**错误响应**：

```json
{
  "code": 400,
  "message": "用户名已存在",
  "data": null
}
```

---

### 1.3 发送验证码

**接口地址**：`POST /auth/send-code`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | string | 条件 | 手机号（发送短信验证码） |
| email | string | 条件 | 邮箱（发送邮件验证码） |
| type | string | 是 | 验证码类型：`register` / `login` / `reset` |

**请求示例**：
```json
{
  "phone": "13800138000",
  "type": "register"
}
```

**响应数据**：

```json
{
  "code": 200,
  "message": "验证码发送成功",
  "data": {
    "expireSeconds": 300
  }
}
```

---

### 1.4 退出登录

**接口地址**：`POST /auth/logout`

**请求头**：`Authorization: Bearer {token}`

**响应数据**：

```json
{
  "code": 200,
  "message": "退出成功",
  "data": null
}
```

---

### 1.5 获取当前用户信息

**接口地址**：`GET /auth/info`

**请求头**：`Authorization: Bearer {token}`

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "chen",
    "nickname": "Chen",
    "email": "chen@example.com",
    "phone": "13800138000",
    "avatar": "https://example.com/avatar.jpg",
    "role": 0,
    "status": 1,
    "createTime": "2024-01-01T00:00:00"
  }
}
```

---

### 1.6 刷新 Token

**接口地址**：`POST /auth/refresh`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| refreshToken | string | 是 | 刷新令牌 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expires": 7200
  }
}
```

---

### 1.7 忘记密码 - 重置密码

**接口地址**：`POST /auth/reset-password`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| account | string | 是 | 邮箱或手机号 |
| code | string | 是 | 验证码 |
| newPassword | string | 是 | 新密码 |

**请求示例**：
```json
{
  "account": "13800138000",
  "code": "123456",
  "newPassword": "newpassword123"
}
```

**响应数据**：

```json
{
  "code": 200,
  "message": "密码重置成功",
  "data": null
}
```

---

### 1.8 检查用户名是否可用

**接口地址**：`GET /auth/check-username`

**查询参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "available": true,
    "message": "用户名可用"
  }
}
```

---

### 1.9 检查邮箱是否已注册

**接口地址**：`GET /auth/check-email`

**查询参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| email | string | 是 | 邮箱 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "exists": false,
    "message": "邮箱未注册"
  }
}
```

---

### 1.10 检查手机号是否已注册

**接口地址**：`GET /auth/check-phone`

**查询参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| phone | string | 是 | 手机号 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "exists": false,
    "message": "手机号未注册"
  }
}
```

---

## 二、首页模块

### 2.1 获取首页聚合数据

**接口地址**：`GET /home`

**描述**：获取首页展示所需的所有聚合数据，包括 Banner、站点统计、热门文章、最新评论等。

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "banners": [
      {
        "id": 1,
        "title": "欢迎来到 Chen404",
        "subtitle": "记录技术成长之路",
        "image": "https://example.com/banner1.jpg",
        "link": "/about",
        "sort": 1
      }
    ],
    "stats": {
      "articleCount": 128,
      "categoryCount": 8,
      "tagCount": 32,
      "commentCount": 256,
      "viewCount": 10000
    },
    "hotArticles": [
      {
        "id": 1,
        "title": "Vue 3 组合式 API 最佳实践",
        "viewCount": 22183
      }
    ],
    "recentComments": [
      {
        "id": 1,
        "content": "写得真好，受益匪浅！",
        "authorName": "访客A",
        "authorAvatar": "https://example.com/avatar.jpg",
        "articleId": 1,
        "articleTitle": "Vue 3 组合式 API 最佳实践",
        "createTime": "2024-03-15T10:30:00"
      }
    ]
  }
}
```

---

### 2.2 获取站点配置

**接口地址**：`GET /site/config`

**描述**：获取站点基本信息配置。

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "siteName": "Chen404 Blog",
    "siteDescription": "一个热爱技术分享的博客",
    "siteLogo": "https://example.com/logo.svg",
    "siteFavicon": "https://example.com/favicon.ico",
    "icp": "粤ICP备XXXXXXXX号",
    "github": "https://github.com/chen404",
    "email": "admin@chen404.com"
  }
}
```

---

### 2.3 获取站点统计

**接口地址**：`GET /site/stats`

**描述**：获取站点统计数据。

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "articleCount": 128,
    "categoryCount": 8,
    "tagCount": 32,
    "commentCount": 256,
    "viewCount": 10000
  }
}
```

---

### 2.4 获取轮播图列表

**接口地址**：`GET /site/banners`

**描述**：获取首页轮播图/Banner 列表。

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "title": "欢迎来到 Chen404",
      "subtitle": "记录技术成长之路",
      "image": "https://example.com/banner1.jpg",
      "link": "/about",
      "sort": 1
    },
    {
      "id": 2,
      "title": "Spring Boot 3.0 新特性",
      "subtitle": "全面升级的性能体验",
      "image": "https://example.com/banner2.jpg",
      "link": "/article/1",
      "sort": 2
    }
  ]
}
```

---

### 2.5 获取热门文章

**接口地址**：`GET /articles/hot`

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| limit | number | 否 | 10 | 获取数量 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "title": "基于 AIDA64 和现代 web 技术的电脑性能监控页",
      "viewCount": 22183
    },
    {
      "id": 2,
      "title": "基于 SCSS mixin 的 flex gap polyfill",
      "viewCount": 47210
    }
  ]
}
```

---

### 2.6 获取最新评论

**接口地址**：`GET /comments/recent`

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| limit | number | 否 | 5 | 获取数量 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "content": "写得真好，受益匪浅！",
      "authorName": "访客A",
      "authorAvatar": "https://example.com/avatar1.jpg",
      "articleId": 1,
      "articleTitle": "Vue 3 组合式 API 最佳实践",
      "createTime": "2024-03-15T10:30:00"
    },
    {
      "id": 2,
      "content": "学习了，感谢分享！",
      "authorName": "开发者B",
      "authorAvatar": "https://example.com/avatar2.jpg",
      "articleId": 2,
      "articleTitle": "Spring Boot 3.0 新特性详解",
      "createTime": "2024-03-14T15:20:00"
    }
  ]
}
```

---

### 2.7 获取推荐文章

**接口地址**：`GET /articles/recommend`

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| limit | number | 否 | 6 | 获取数量 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "title": "Vue 3 组合式 API 最佳实践",
      "coverImage": "https://example.com/cover1.jpg",
      "summary": "本文将分享在实际项目中总结的最佳实践..."
    }
  ]
}
```

---

## 三、文章管理模块

### 3.1 获取文章列表

**接口地址**：`GET /articles`

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| page | number | 否 | 1 | 页码 |
| size | number | 否 | 10 | 每页数量 |
| categoryId | number | 否 | - | 分类ID筛选 |
| tagId | number | 否 | - | 标签ID筛选 |
| keyword | string | 否 | - | 关键词搜索 |
| orderBy | string | 否 | newest | 排序方式：newest-最新，hottest-最热，recommend-推荐 |
| status | number | 否 | 1 | 文章状态：0-草稿，1-已发布 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "Vue 3 组合式 API 最佳实践",
        "summary": "本文将分享在实际项目中总结的最佳实践...",
        "coverImage": "https://example.com/cover1.jpg",
        "categoryId": 1,
        "status": 1,
        "viewCount": 1000,
        "commentCount": 20,
        "likeCount": 50,
        "isTop": false,
        "isRecommend": true,
        "publishTime": "2024-03-15T10:30:00",
        "createTime": "2024-03-15T10:00:00",
        "updateTime": "2024-03-15T10:30:00",
        "category": {
          "id": 1,
          "name": "前端开发",
          "slug": "frontend"
        },
        "tags": [
          {
            "id": 1,
            "name": "Vue",
            "slug": "vue"
          }
        ],
        "author": {
          "id": 1,
          "username": "chen",
          "nickname": "Chen",
          "avatar": "https://example.com/avatar.jpg"
        }
      }
    ],
    "total": 100,
    "page": 1,
    "size": 10
  }
}
```

---

### 3.2 获取文章详情

**接口地址**：`GET /articles/{id}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 文章ID |

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| incrementView | boolean | 否 | true | 是否增加阅读量 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "Vue 3 组合式 API 最佳实践",
    "summary": "本文将分享在实际项目中总结的最佳实践...",
    "content": "# 文章正文内容（Markdown格式）...",
    "coverImage": "https://example.com/cover1.jpg",
    "authorId": 1,
    "categoryId": 1,
    "status": 1,
    "viewCount": 1001,
    "commentCount": 20,
    "likeCount": 50,
    "isTop": false,
    "isRecommend": true,
    "publishTime": "2024-03-15T10:30:00",
    "createTime": "2024-03-15T10:00:00",
    "updateTime": "2024-03-15T10:30:00",
    "category": {
      "id": 1,
      "name": "前端开发",
      "slug": "frontend"
    },
    "tags": [
      {
        "id": 1,
        "name": "Vue",
        "slug": "vue"
      }
    ],
    "author": {
      "id": 1,
      "username": "chen",
      "nickname": "Chen",
      "avatar": "https://example.com/avatar.jpg",
      "bio": "热爱技术的开发者"
    }
  }
}
```

---

### 3.3 创建文章

**接口地址**：`POST /admin/articles`

**请求头**：`Authorization: Bearer {token}`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| title | string | 是 | 文章标题（1-100字符） |
| content | string | 是 | 文章内容（Markdown格式） |
| summary | string | 否 | 文章摘要（不填自动提取） |
| coverImage | string | 否 | 封面图片URL |
| categoryId | number | 是 | 分类ID |
| tagIds | number[] | 否 | 标签ID数组 |
| status | number | 否 | 文章状态：0-草稿，1-已发布，默认0 |
| isTop | boolean | 否 | 是否置顶，默认false |
| isRecommend | boolean | 否 | 是否推荐，默认false |

**请求示例**：

```json
{
  "title": "Vue 3 组合式 API 最佳实践",
  "content": "# 引言\n\nVue 3 的组合式 API 带来了全新的开发体验...",
  "summary": "本文将分享在实际项目中总结的最佳实践",
  "coverImage": "https://example.com/cover1.jpg",
  "categoryId": 1,
  "tagIds": [1, 2, 3],
  "status": 1,
  "isTop": false,
  "isRecommend": true
}
```

**响应数据**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 2,
    "title": "Vue 3 组合式 API 最佳实践",
    "summary": "本文将分享在实际项目中总结的最佳实践",
    "content": "# 引言...",
    "coverImage": "https://example.com/cover1.jpg",
    "categoryId": 1,
    "status": 1,
    "viewCount": 0,
    "commentCount": 0,
    "likeCount": 0,
    "isTop": false,
    "isRecommend": true,
    "publishTime": "2024-03-15T14:30:00",
    "createTime": "2024-03-15T14:30:00",
    "updateTime": "2024-03-15T14:30:00"
  }
}
```

**错误响应**：

```json
{
  "code": 400,
  "message": "分类不存在",
  "data": null
}
```

---

### 3.4 更新文章

**接口地址**：`PUT /admin/articles/{id}`

**请求头**：`Authorization: Bearer {token}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 文章ID |

**请求参数**：同创建文章，所有字段可选

**响应数据**：

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "id": 1,
    "title": "Vue 3 组合式 API 最佳实践（已更新）",
    "updateTime": "2024-03-15T15:00:00"
  }
}
```

---

### 3.5 删除文章

**接口地址**：`DELETE /admin/articles/{id}`

**请求头**：`Authorization: Bearer {token}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 文章ID |

**响应数据**：

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

### 3.6 点赞文章

**接口地址**：`POST /articles/{id}/like`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 文章ID |

**响应数据**：

```json
{
  "code": 200,
  "message": "点赞成功",
  "data": {
    "likes": 51
  }
}
```

---

### 3.7 获取上一篇/下一篇文章

**接口地址**：`GET /articles/{id}/neighbors`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 当前文章ID |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "prev": {
      "id": 2,
      "title": "上一篇文章标题"
    },
    "next": {
      "id": 4,
      "title": "下一篇文章标题"
    }
  }
}
```

---

## 四、分类管理模块

### 4.1 获取分类列表

**接口地址**：`GET /categories`

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| withArticleCount | boolean | 否 | true | 是否包含文章数量 |

**响应数据**：

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
    },
    {
      "id": 2,
      "name": "后端开发",
      "slug": "backend",
      "description": "后端技术相关文章",
      "icon": "icon-server",
      "articleCount": 18,
      "sortOrder": 2
    }
  ]
}
```

---

### 4.2 获取分类详情

**接口地址**：`GET /categories/{id}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number/string | 是 | 分类ID或slug |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "前端开发",
    "slug": "frontend",
    "description": "前端技术相关文章",
    "icon": "icon-code",
    "articleCount": 25,
    "sortOrder": 1
  }
}
```

---

### 4.3 获取分类下的文章

**接口地址**：`GET /categories/{id}/articles`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number/string | 是 | 分类ID或slug |

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| page | number | 否 | 1 | 页码 |
| size | number | 否 | 10 | 每页数量 |

**响应数据**：同 3.1 获取文章列表

---

### 4.4 创建分类（管理员）

**接口地址**：`POST /admin/categories`

**请求头**：`Authorization: Bearer {token}`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 是 | 分类名称（1-50字符） |
| slug | string | 是 | 分类标识（唯一，1-50字符，支持字母数字下划线连字符） |
| description | string | 否 | 分类描述（最大200字符） |
| icon | string | 否 | 图标类名 |
| sortOrder | number | 否 | 排序顺序，默认0 |

**响应数据**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 3,
    "name": "数据库",
    "slug": "database",
    "description": "数据库相关技术",
    "icon": null,
    "articleCount": 0,
    "sortOrder": 3
  }
}
```

---

### 4.5 更新分类（管理员）

**接口地址**：`PUT /admin/categories/{id}`

**请求头**：`Authorization: Bearer {token}`

**请求参数**：同创建分类，所有字段可选

**响应数据**：

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "id": 1,
    "name": "前端开发（已更新）"
  }
}
```

---

### 4.6 删除分类（管理员）

**接口地址**：`DELETE /admin/categories/{id}`

**请求头**：`Authorization: Bearer {token}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 分类ID |

**响应数据**：

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

**错误响应**：

```json
{
  "code": 400,
  "message": "该分类下存在文章，无法删除",
  "data": null
}
```

---

## 五、标签管理模块

### 5.1 获取标签列表

**接口地址**：`GET /tags`

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| withArticleCount | boolean | 否 | true | 是否包含文章数量 |

**响应数据**：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "Vue",
      "slug": "vue",
      "color": "#42b883",
      "articleCount": 10
    },
    {
      "id": 2,
      "name": "React",
      "slug": "react",
      "color": "#61dafb",
      "articleCount": 8
    }
  ]
}
```

---

### 5.2 获取标签详情

**接口地址**：`GET /tags/{id}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number/string | 是 | 标签ID或slug |

**响应数据**：同 5.1 单条数据

---

### 5.3 获取标签下的文章

**接口地址**：`GET /tags/{id}/articles`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number/string | 是 | 标签ID或slug |

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| page | number | 否 | 1 | 页码 |
| size | number | 否 | 10 | 每页数量 |

**响应数据**：同 3.1 获取文章列表

---

### 5.4 创建标签（管理员）

**接口地址**：`POST /admin/tags`

**请求头**：`Authorization: Bearer {token}`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 是 | 标签名称（1-30字符） |
| slug | string | 是 | 标签标识（唯一，1-30字符） |
| color | string | 否 | 标签颜色（十六进制，如 #42b883） |

**响应数据**：

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 3,
    "name": "TypeScript",
    "slug": "typescript",
    "color": "#3178c6",
    "articleCount": 0
  }
}
```

---

### 5.5 更新标签（管理员）

**接口地址**：`PUT /admin/tags/{id}`

**请求头**：`Authorization: Bearer {token}`

**请求参数**：同创建标签，所有字段可选

**响应数据**：

```json
{
  "code": 200,
  "message": "更新成功",
  "data": {
    "id": 1,
    "name": "Vue.js",
    "color": "#35495e"
  }
}
```

---

### 5.6 删除标签（管理员）

**接口地址**：`DELETE /admin/tags/{id}`

**请求头**：`Authorization: Bearer {token}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | number | 是 | 标签ID |

**响应数据**：

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

## 六、归档模块

### 6.1 获取归档数据

**接口地址**：`GET /archives`

**描述**：按年月分组的文章归档数据

**响应数据**：

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
        },
        {
          "month": 2,
          "count": 10,
          "articles": []
        }
      ]
    }
  ]
}
```

---

### 6.2 根据日期获取文章

**接口地址**：`GET /archives/{year}` 或 `GET /archives/{year}/{month}`

**路径参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| year | number | 是 | 年份 |
| month | number | 否 | 月份（1-12） |

**查询参数**：

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| page | number | 否 | 1 | 页码 |
| size | number | 否 | 10 | 每页数量 |

**响应数据**：同 3.1 获取文章列表

---

## 七、文件上传模块

### 7.1 上传单张图片

**接口地址**：`POST /upload/image`

**请求头**：`Authorization: Bearer {token}`, `Content-Type: multipart/form-data`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 图片文件（限制：jpg/png/gif/webp，最大5MB） |
| folder | string | 否 | 上传文件夹，默认'images' |

**响应数据**：

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "url": "https://example.com/uploads/images/2024/03/15/abc123.jpg",
    "name": "abc123.jpg",
    "size": 102456
  }
}
```

---

### 7.2 上传多张图片

**接口地址**：`POST /upload/images`

**请求头**：`Authorization: Bearer {token}`, `Content-Type: multipart/form-data`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| files | File[] | 是 | 图片文件数组（最多10张） |
| folder | string | 否 | 上传文件夹，默认'images' |

**响应数据**：

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "urls": [
      "https://example.com/uploads/images/2024/03/15/abc123.jpg",
      "https://example.com/uploads/images/2024/03/15/def456.jpg"
    ],
    "files": [
      { "name": "abc123.jpg", "size": 102456 },
      { "name": "def456.jpg", "size": 204800 }
    ]
  }
}
```

---

### 7.3 上传文件

**接口地址**：`POST /upload/file`

**请求头**：`Authorization: Bearer {token}`, `Content-Type: multipart/form-data`

**请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | File | 是 | 文件（限制：最大50MB） |
| folder | string | 否 | 上传文件夹，默认'files' |

**响应数据**：

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "url": "https://example.com/uploads/files/2024/03/15/document.pdf",
    "name": "document.pdf",
    "size": 1024567,
    "type": "application/pdf"
  }
}
```

---

## 附录

### 数据类型说明

#### UserRole 用户角色
| 值 | 说明 |
|----|------|
| 0 | 普通用户 |
| 1 | 管理员 |

#### UserStatus 用户状态
| 值 | 说明 |
|----|------|
| 0 | 禁用 |
| 1 | 启用 |

#### ArticleStatus 文章状态
| 值 | 说明 |
|----|------|
| 0 | 草稿 |
| 1 | 已发布 |
| 2 | 回收站 |

#### CommentStatus 评论状态
| 值 | 说明 |
|----|------|
| 0 | 待审核 |
| 1 | 已通过 |
| 2 | 已拒绝 |

#### FriendLinkStatus 友链状态
| 值 | 说明 |
|----|------|
| 0 | 待审核 |
| 1 | 已通过 |
| 2 | 已拒绝 |

### 前端调用示例

```typescript
import { login, getHomeData, sendVerifyCode } from '@/api';

// 登录
const handleLogin = async () => {
  try {
    const res = await login({
      username: form.email,
      password: form.password
    });
    localStorage.setItem('token', res.token);
    console.log('登录成功', res.user);
  } catch (error) {
    console.error('登录失败', error);
  }
};

// 获取首页数据
const loadHomeData = async () => {
  const data = await getHomeData();
  console.log('站点统计', data.stats);
  console.log('轮播图', data.banners);
};

// 发送验证码
const handleSendCode = async () => {
  await sendVerifyCode({
    phone: form.phone,
    type: 'register'
  });
  console.log('验证码已发送');
};
```

---

## 文章编写接口概述

### 核心接口

| 接口 | 地址 | 说明 |
|------|------|------|
| 创建文章 | POST /admin/articles | 创建新文章或保存草稿 |
| 更新文章 | PUT /admin/articles/{id} | 更新已有文章 |
| 获取文章 | GET /articles/{id} | 获取文章详情用于编辑 |
| 上传图片 | POST /upload/image | 上传封面图或正文图片 |
| 获取分类 | GET /categories | 获取所有分类供选择 |
| 获取标签 | GET /tags | 获取所有标签供选择 |

### 文章编写流程

```
1. 用户登录 → 点击"编写"按钮
2. 进入编辑页面 → 选择分类、标签
3. 编写内容（Markdown）
4. 上传封面图片
5. 保存草稿 / 发布文章
```

### 权限说明

- 所有 `/admin/**` 接口需要管理员权限（JWT Token）
- 普通用户只能编辑自己的文章
- 管理员可以编辑所有文章

---

*文档版本: 1.1*
*最后更新: 2026-03-16*
