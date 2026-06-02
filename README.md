# wgxhl-recipe

菜谱管理项目，前后端分离架构。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Vant |
| 后端 | Spring Boot 2.7 + MyBatis Plus |
| 数据库 | MySQL 8.4 |
| 图片存储 | 腾讯云 COS |

## 目录结构

```
wgxhl-recipe/
├── frontend/          # 前端项目
├── backend/           # 后端项目
└── README.md
```

## 环境要求

- Node.js 18+
- Java 8+
- Maven 3.6+
- MySQL 8.4

## 快速开始

### 1. 初始化数据库

在 MySQL 中执行 `backend/sql/init.sql`：

```bash
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/init.sql
```

### 2. 后端配置

复制开发配置模板并填写密钥：

```bash
cp backend/src/main/resources/application-dev.yml.example backend/src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，配置数据库密码和腾讯云 COS 密钥。

启动后端：

```bash
cd backend
mvn spring-boot:run
```

后端默认端口：`9001`，上下文路径 `/recipe`，健康检查：`http://localhost:9001/recipe/health`

主要接口前缀示例：

```text
POST /recipe/category/page
POST /recipe/recipe/page
POST /recipe/ingredient/page
POST /recipe/user/login
```

### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端默认端口：`5173`，已配置 `/api` 代理到后端。

## MySQL MCP（Cursor）

项目已配置 [MySQL MCP Server](https://github.com/benborla/mcp-server-mysql)，可在 Cursor 中直接查询和操作数据库。

### 首次使用

1. 复制配置模板（如尚未创建）：

```bash
cp .cursor/mcp.json.example .cursor/mcp.json
```

2. 编辑 `.cursor/mcp.json`，填写 `MYSQL_PASS` 等连接信息。
3. 重启 Cursor，或在 **Settings → MCP** 中刷新 `mysql` 服务器。
4. 连接成功后，Agent 可使用 `mysql_query` 等工具操作 `recipe` 数据库。

> `.cursor/mcp.json` 含数据库密码，已加入 `.gitignore`，请勿提交到仓库。

## COS 配置说明

在 `application-dev.yml` 或环境变量中配置：

| 配置项 | 环境变量 | 说明 |
|--------|----------|------|
| secret-id | COS_SECRET_ID | 腾讯云 SecretId |
| secret-key | COS_SECRET_KEY | 腾讯云 SecretKey |
| region | COS_REGION | 地域，如 ap-guangzhou |
| bucket | COS_BUCKET | 存储桶名称 |
| base-url | COS_BASE_URL | 访问域名 |
