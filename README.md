# 拾忆

拾忆是一个 Markdown 备忘录和文件收集站，支持笔记编辑、标签、搜索、置顶、附件上传和公开分享。

## 功能

- 邮箱验证码登录，首次登录自动注册，可设置登录密码
- Markdown 编辑、预览、撤销、字号、颜色和段落格式化
- 标签、全文搜索、置顶和自动保存
- 图片、视频、音频和文件上传，图片自动生成缩略图
- 笔记公开分享，可随时开启或关闭
- 个人中心、空间配额、深浅主题和站内信
- 独立后台控制台，可管理用户、笔记、文件、消息和等级

## 技术栈

- 后端：Java 21、Spring Boot 3.5、MyBatis-Plus、Flyway
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia
- 数据库：MySQL 8

## 本地运行

需要 JDK 21、Maven 3.9、Node.js 20+ 和 MySQL 8。

1. 创建数据库：

```sql
CREATE DATABASE shiyi_memo
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

2. 参考 `src/main/resources/application-local.yml.example` 创建本地配置：

```text
src/main/resources/application-local.yml
```

至少填写数据库连接。需要邮件登录时，再配置 SMTP。

3. 构建并启动：

```powershell
cd frontend
npm install
npm run build
cd ..
mvn -DskipTests package
java -jar start/shiyi-memo-1.0.0.jar
```

也可以在项目根目录直接执行：

```powershell
.\start-windows.ps1
```

访问地址：

- 用户端：`http://localhost:8080`
- 后台：`http://localhost:8080/admin`

## 开发模式

```powershell
.\dev.ps1
```

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`

## 部署

`mvn package` 已经把可执行 jar 输出到 `start/`，`ecosystem.config.js` 的 pm2 应用目录也指向这里：

```bash
bash start-linux.sh       # Linux 服务器：前端构建 -> 后端打包 -> pm2 启动 / 重启
.\start-windows.ps1       # Windows 本地：同样的三步，装了 pm2 就走 pm2，否则直接 java -jar
```

线上配置 `start/application-local.yml` 由 pm2 通过 `--spring.config.additional-location` 加载，和 jar 放在一起；日志在 `start/logs/`。

## 主要配置

配置写在 `src/main/resources/application-local.yml` 中：

| 配置项 | 说明 |
| --- | --- |
| `spring.datasource.*` | MySQL 连接 |
| `spring.mail.*` | 验证码邮件 |
| `app.jwt.secret` | 登录令牌密钥 |
| `app.jwt.secure-cookie` | HTTPS 环境设为 `true` |
| `app.mail.enabled` | 是否真实发送邮件 |
| `app.mail.expose-code` | 本地联调时返回验证码，线上必须为 `false` |
| `app.admin.username/password` | 后台登录账号 |
| `app.storage.root` | 上传文件目录 |
| `app.storage.thumbnail-root` | 缩略图目录 |

## 数据目录

```text
data/
  uploads/
  thumbnails/
```

数据库保存元数据，`data/` 保存文件，两者需要一起备份。
