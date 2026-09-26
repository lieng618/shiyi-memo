# 拾忆

自用的备忘录 / 文件收集站：写 Markdown 记录、打标签、搜索、置顶，随手传照片、视频、文档。
后端 Spring Boot 3.5 + MySQL 8，前端 Vue 3 + Vite + TypeScript，登录用邮箱验证码。

## 环境要求

JDK 21、Maven 3.9、Node.js 20+、MySQL 8。
本机已装好 JDK 21（`C:\Program Files\Java\jdk-21`）、Node.js 和 MySQL 8；
Maven 便携版放在项目内的 `.tools/apache-maven-3.9.11`，没有装到系统里。

## 首次运行

数据库（已建好 `shiyi_memo` 库和 `shiyi_memo` 账号，密码见 `application-local.yml`）：

```sql
CREATE DATABASE IF NOT EXISTS shiyi_memo
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

填配置：编辑 `src/main/resources/application-local.yml`（已被 git 忽略），
主要就两处——数据库密码、后台控制台账号。

构建 + 启动：

```powershell
cd frontend
npm install
npm run build      # 产物直接输出到后端 static 目录
cd ..
mvn -DskipTests package
java -jar target/shiyi-memo-1.0.0.jar
```

也可以直接跑 `.\start.ps1`（等价于上面三步）。
浏览器打开 http://localhost:8080，填邮箱 → 点「获取验证码」。
**还没配置 SMTP 时**，验证码会自动填进输入框并写进后端日志，配好邮箱后请把
`app.mail.expose-code` 关掉。

## 开发模式（前端热更新）

```powershell
.\dev.ps1
```

等价于：一个窗口 `mvn spring-boot:run`（8080），另一个窗口 `cd frontend; npm run dev`（5173，
Vite 会把 `/api` 代理到 8080，登录 Cookie 直接可用）。

## 配置说明（`application-local.yml`）

| 配置项 | 作用 |
|---|---|
| `spring.datasource.*` | 数据库连接，库名默认 `shiyi_memo` |
| `app.storage.root` | 上传文件存放目录 |
| `app.storage.thumbnail-root` | 图片缩略图缓存目录 |
| `app.security.allow-register` | 是否允许新邮箱自助注册 |
| `app.admin.username` / `password` | 后台控制台（`/admin`）的登录账号；和备忘录用户完全无关 |
| `app.jwt.secret` | 登录令牌密钥，公网部署务必换掉，至少 32 字节 |
| `app.jwt.secure-cookie` | 上了 HTTPS 之后改成 `true` |
| `app.mail.enabled` | 改成 `true` 才会真的发验证码邮件 |
| `app.mail.expose-code` | 未配置邮件时把验证码返回给前端，公网必须设为 `false` |
| `spring.mail.username` / `password` | 发信邮箱 + SMTP 授权码（QQ 邮箱：设置 → 账户 → 开启 SMTP 拿授权码） |

生产环境可以直接用环境变量覆盖，不必改文件：

```bash
SPRING_DATASOURCE_PASSWORD=xxx \
APP_JWT_SECRET=至少32字节的随机串 \
APP_MAIL_ENABLED=true \
APP_MAIL_EXPOSE_CODE=false \
SPRING_MAIL_USERNAME=you@qq.com \
SPRING_MAIL_PASSWORD=授权码 \
APP_ADMIN_USERNAME=admin \
APP_ADMIN_PASSWORD=换个强密码 \
java -jar shiyi-memo-1.0.0.jar
```

## 界面与功能

主页面只有一个：最上方是搜索框，右上角是账号（头像 + 昵称，头像正下方是「新建」按钮），
下面就是笔记列表，没有笔记时显示空状态。点某条笔记进入全屏编辑页，
写 Markdown 的同时可以直接上传照片 / 视频 / 文件 —— 附件属于笔记，没有单独的文件管理页。

- 登录 / 注册：邮箱验证码，首次登录自动建号；登录态是 HttpOnly Cookie 里的 JWT（7 天有效）
- 笔记：Markdown 编辑 + 实时预览、自动保存（Ctrl/Cmd+S 也能存）、标签、置顶、全文搜索；带图的笔记在列表里有封面缩略图
- 附件：一条笔记挂多个文件，支持拖拽和多选上传，图片自动生成缩略图，视频音频可在线播放并拖进度条
- 个人中心（点右上角头像）：换头像、改昵称、看空间用量、切换深色 / 浅色、退出登录
- 站内信：头像下方铃铛图标，有未读时带角标；管理员发的通知 / 屏蔽说明都在这里，只能查看不能删除
- 配额：按等级限制**总空间**，不限单个文件大小
- 深色模式、手机与电脑同一套响应式界面

## 后台控制台（另一个网页）

后台是**独立的站点**：浏览器打开 `http://localhost:8080/admin`，用配置文件里 `app.admin` 的账号密码登录，
和备忘录用户的邮箱登录互不影响（两套 Cookie，互不通用）。所有备忘录账号都只是普通用户，没有管理员角色。

后台能做的事：

- **概览**：用户数、笔记数、文件数、已屏蔽文件数、总占用空间、站内信统计
- **用户**：看头像、昵称、邮箱、状态、等级、已用空间、笔记数、文件数（含被屏蔽数量）、注册与最后登录时间；
  可以改昵称、改等级、启用 / 禁用账号，也可以直接给他发站内信
- **笔记**：搜索、查看完整内容（Markdown 渲染）和附件清单；删除违规笔记时会弹出原因输入框，
  删除后自动给作者发一封说明的站内信
- **文件**：按文件名 / 屏蔽状态筛选；可以预览（图片、视频、音频，被屏蔽的也能看），
  **屏蔽**时会要求填写原因，屏蔽后该文件对用户立即不可访问（预览 / 下载都返回 403），并自动给文件所有者发站内信
- **站内信**：查看所有已发出的站内信和已读状态，也可以主动给任意用户发通知
- **等级与配额**：增删改等级的总空间上限，设置新用户默认等级

删除是**真删**：笔记、附件、磁盘文件、缩略图缓存一起清掉，没有回收站。头像文件也算在你的空间用量里。

## 数据存在哪

```
data/
  uploads/{用户ID}/{年}/{月}/{随机名}.{后缀}   原始文件
  thumbnails/{文件ID}/{宽度}.jpg              缩略图缓存，删掉会自动重建
```

数据库只存元数据（文件名、大小、类型、相对路径、配额用量），备份时两者要一起备：

```powershell
mysqldump -u shiyi_memo -p shiyi_memo > backup.sql
Compress-Archive -Path data\uploads -DestinationPath uploads.zip
```

## 公网部署清单

1. 改掉 `app.jwt.secret`，设 `app.mail.expose-code: false`，配好 `spring.mail.*`
2. Nginx 反代 + 证书，然后打开 `app.jwt.secure-cookie: true`
3. 改掉 `app.admin.password`，后台把不需要的账号禁用
4. 程序不限制单个文件大小，只受总空间约束；但服务器 multipart 有 2GB 硬上限
   （`spring.servlet.multipart.max-file-size`，需要更大就改这里），Nginx 也要配 `client_max_body_size`
5. 定期备份 MySQL 和 `data/uploads`

Nginx 参考片段：

```nginx
server {
    listen 443 ssl;
    server_name memo.example.com;
    client_max_body_size 2048m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;
        proxy_request_buffering off;   # 大文件不经 Nginx 临时盘
    }
}
```

## 目录结构

```
src/main/java/org/shiyi/memo/
  auth/       邮箱验证码、登录、发信
  security/   JWT、用户/后台两套拦截器、@PublicApi
  user/       用户、等级、配额
  memo/       记录（增删改查、搜索、置顶）
  tag/        标签
  file/       附件上传 / 下载 / 缩略图 / 存储抽象
  message/    站内信
  admin/      后台控制台接口（独立登录态）
  config/     业务配置、MyBatis-Plus、SPA 回退
src/main/resources/
  db/migration/V1__init.sql    建表脚本，Flyway 启动时自动执行
  application.yml              通用配置
  application-local.yml        本地私有配置（git 忽略）
frontend/src/
  views/NotesListView.vue      首页：搜索 + 笔记列表
  views/NoteDetailView.vue     笔记页：Markdown 编辑 + 附件上传
  views/ProfileView.vue        个人中心：头像 / 昵称 / 空间 / 主题
  views/MessagesView.vue       站内信
  admin/                       后台控制台（独立入口 admin.html）
    AdminApp.vue               后台外壳 + 登录
    views/                     概览 / 用户 / 笔记 / 文件 / 站内信 / 等级
  components/                  编辑器、附件条、头像、文件预览等
design-system/                 设计规范（配色、排版、组件约定）
```

## 已知限制

- iPhone 的 HEIC 照片能上传下载，但浏览器大多不认这种格式；缩略图生成失败会退回原图
- 搜索是 `LIKE %关键词%`，几万条以内够快；量大了可以换 MySQL 的 ngram 全文索引
- 限流（验证码 60 秒重发、每日上限）是单机内存实现，将来多实例部署要换成 Redis
- 笔记没有版本历史，自动保存会覆盖旧内容
- 头像只支持浏览器能解码的位图（jpg / png / webp / gif），HEIC 会生成失败
- 后台只有一个账号（`app.admin` 里配置），没有多管理员和操作日志
- 被屏蔽的文件仍占用用户的空间配额（文件还在服务器上，只是用户访问不到）
