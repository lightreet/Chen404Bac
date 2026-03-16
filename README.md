# Chen404 博客系统 - 后端

基于 Spring Boot 3 + MyBatis Plus + MySQL 的个人博客后端服务，提供 RESTful API 支持。

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2.x、Java 17 |
| 数据访问 | MyBatis Plus、MySQL 8、Redis |
| 安全 | Spring Security、JWT |
| 存储 | MinIO 对象存储 |
| 工具 | Lombok、Hutool、FastJSON2 |
| 文档 | SpringDoc OpenAPI |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0+
- Redis 7.x

### 运行步骤

1. 创建数据库并导入 SQL
```bash
mysql -u root -p < doc/database.sql
```

2. 修改配置文件 `src/main/resources/application.yml`
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chen404?useUnicode=true&characterEncoding=utf-8
    username: your_username
    password: your_password
  redis:
    host: localhost
    port: 6379
```

3. 运行项目
```bash
mvn spring-boot:run
```

或打包后运行：
```bash
mvn clean package -DskipTests
java -jar target/chen404bac-1.0.0.jar
```

### 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 项目结构

```
Chen404Bac/
├── src/main/java/com/chen404/blog/
│   ├── config/          # 配置类
│   ├── controller/      # 控制层
│   ├── service/         # 业务层
│   ├── mapper/          # 数据访问层
│   ├── domain/          # 实体、DTO、VO
│   ├── interceptor/     # 拦截器
│   ├── exception/       # 异常处理
│   └── util/            # 工具类
├── src/main/resources/
│   ├── mapper/          # MyBatis XML
│   └── application.yml  # 配置文件
└── doc/
    ├── architecture.md  # 架构设计文档
    └── database.sql     # 数据库脚本
```

## 接口约定

- **Base URL**: `http://localhost:8080/api`
- **响应格式**: `{ "code": 200, "message": "success", "data": {} }`
- **认证方式**: JWT Token，请求头 `Authorization: Bearer <token>`
- **管理接口**: `/api/admin/**` 需管理员权限
- **API 文档**: 启动后访问 `/swagger-ui.html`

## 模块说明

| 模块 | 说明 |
|------|------|
| 认证模块 | 登录、注册、Token刷新、验证码 |
| 文章模块 | 文章的CRUD、发布、搜索 |
| 分类标签 | 分类和标签的管理 |
| 评论模块 | 文章评论和留言板 |
| 友链模块 | 友情链接申请和管理 |
| 文件模块 | 图片/文件上传（MinIO） |
| 系统模块 | 用户、角色、菜单、日志 |

## 文档

- [架构设计](doc/architecture.md) - 详细架构设计说明
- [数据库设计](doc/database.sql) - 数据库表结构和初始化数据
