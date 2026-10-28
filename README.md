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
├── deploy/            # 部署目录（Docker / 1Panel）
│   ├── backend/       # 后端 jar + Dockerfile + compose
│   └── frontend/      # 前端 dist + nginx + compose
├── scripts/           # 工具脚本（含 pack-deploy）
└── README.md
```

## 打包到 deploy 目录

一键打包前后端并复制到 `deploy`（与 Dockerfile 路径一致）：

```bash
node scripts/pack-deploy.mjs
```

产物位置：

| 类型 | 路径 |
|------|------|
| 后端 JAR | `deploy/backend/recipe-backend-1.0.0-SNAPSHOT.jar` |
| 前端静态文件 | `deploy/frontend/dist/` |

随后在 1Panel 中分别使用 `deploy/backend`、`deploy/frontend` 目录进行编排部署即可。

## 环境要求

- Node.js 18+
- Java 8+
- Maven 3.6+
- MySQL 8.4

## 快速开始

### 1. 初始化数据库

在 MySQL 中依次执行 `init.sql`、`add_household.sql`、`person_owned_recipe.sql`、`add_household_invite.sql`、`add_todo_creator.sql`、`add_email_verification_code.sql`：

```bash
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/init.sql
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/add_household.sql
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/person_owned_recipe.sql
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/add_household_invite.sql
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/add_todo_creator.sql
mysql -h wgxhl.space -u recipe -p123456 < backend/sql/add_email_verification_code.sql
```

> `person_owned_recipe.sql` 会把菜谱和基础数据从“家庭所有”改为“人所有”，并删除相关表的
> `household_id`。存量库执行前必须先备份，且执行后不可逆。

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

## 账号安全

- 升级前备份 `app_user`、`email_verification_code`，然后执行 `backend/sql/add_account_security.sql`。脚本可重复执行，测试数据库初始化清单已同步。
- 注册验证码、邮箱验证/更换和密码重置邮件共用数据库额度：每账号每天 3 封、每收件邮箱每天 3 封、每 IP 每天 20 封；同一收件邮箱间隔至少 60 秒。日期按北京时间计算，重启不会清零。更换邮箱需各向旧、新邮箱发送一封，占账号/IP 两封额度。发送失败也保留额度，避免 SMTP 超时后实际已投递导致重复发送。
- 找回密码入口为 `/#/reset-password`。邮件链接有效期 30 分钟，单次使用；邮箱验证码有效期 10 分钟，最多尝试 5 次。数据库不保存明文验证码或重置令牌。
- 旧账号不会自动标记邮箱已验证，也不会猜测是否为家庭代建账号。成员须先登录，在「我的 → 邮箱安全」完成验证，之后才能通过邮件找回密码。
- 唯一家庭拥有者仅能直接重置本人在本家庭创建、且没有其他家庭关系的代建账号；独立注册账号通过成员本人已验证邮箱重置。管理员不能直接修改邮箱或密码。本人修改邮箱需当前密码及新、旧邮箱验证码；初次绑定/验证只需对应邮箱验证码。
- 密码或邮箱修改成功会使所有旧登录和未使用的重置链接失效。安全操作的原密码连续输错 5 次将暂停验证 10 分钟。
- `ACCOUNT_SECURITY_PUBLIC_URL` 是可信的 HTTPS 前端地址，默认 `https://recipe.wgxhl.space`，用于生成邮件链接，不从请求 Host 拼接。
- 生产后端端口应仅绑定 `127.0.0.1`，由反向代理覆盖 `X-Real-IP` 后转发，确保 IP 额度无法被伪造请求头绕过。仓库中的 compose 已限制为本机绑定，部署时应同步检查线上配置。
