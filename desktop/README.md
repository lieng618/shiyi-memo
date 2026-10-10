# 拾忆桌面端（Windows）

一个 Electron 外壳，打开后直接加载线上拾忆站点，本身不含任何业务逻辑。
前端和后端改完部署到服务器，所有用户的桌面端立刻就是新版本，**不需要重新发版**。

## 前置

- Node.js 20+
- 首次安装依赖时 Electron 二进制要从镜像下载（约 250MB）

## 开发运行

```powershell
cd desktop
npm install
npm start
```

如果 `npm install` 之后 `node_modules/electron/dist/` 是空的（npm 11+ 默认拦掉 postinstall 脚本），
手动补下载一次：

```powershell
$env:ELECTRON_MIRROR = 'https://npmmirror.com/mirrors/electron/'
node node_modules/electron/install.js
```

## 打包

```powershell
cd desktop
$env:ELECTRON_MIRROR = 'https://npmmirror.com/mirrors/electron/'
$env:ELECTRON_BUILDER_BINARIES_MIRROR = 'https://npmmirror.com/mirrors/electron-builder-binaries/'
npm run dist
```

产物在 `release/`：

| 文件 | 说明 |
| --- | --- |
| `shiyi-memo-setup.exe` | NSIS 安装包，可选安装目录，建桌面和开始菜单快捷方式 |
| `win-unpacked/` | 未压缩的绿色目录，方便排查问题 |

只想快速验证不生成安装包，用 `npm run dist:dir`。

只发 NSIS 安装版：免安装版没有安装过程，接不了自动更新。

## 发布新版本

安装包**不走自己的服务器**（带宽太小，100MB 的包下到一半就断），全部放 Gitee 发行版。
三步：

1. 改 `package.json` 的 `version`（比如 `1.2.0`）
2. 打包：

   ```powershell
   cd desktop
   npm run dist
   ```

3. 到 Gitee 建发行版（tag 填 `v1.2.0`），把 `release\` 里这三个文件拖上去发布：

```
shiyi-memo-setup.exe            安装包
shiyi-memo-setup.exe.blockmap   差量更新用，别漏
latest.yml                      客户端读的更新清单
```

文件名里**故意不带版本号**：这样下载地址永远不变，网页的下载按钮和客户端里写死的
更新源都不用跟着改。

### 更新源为什么写 `releases/download/latest`

客户端里写死的更新源是：

```
https://gitee.com/<owner>/<repo>/releases/download/latest/
```

`latest` 不是真实的 tag，是 Gitee 的别名，**永远指向最新那个发行版**（实测：随便填个
不存在的 tag 会 404，只有 `latest` 会跳到最新版）。地址不随版本变，客户端打包时才能
写死。

⚠️ 这个别名官方没有文档，是实测出来的。**每发一个版本顺手确认一下它还指着新版本**
——万一哪天变了，所有客户端的自动更新会静默失效（不报错，就是永远不更新）。

### 换到自己的服务器要改哪些

整套站点相关的值散在下面这些地方，自建时都要改：

| 位置 | 改什么 |
| --- | --- |
| `src/config.js` | `SERVER_URL`：外壳加载哪个站点 |
| `package.json` → `build.publish[0].url` | 更新源地址，换成你自己的发行版地址 |
| `package.json` → `appId` / `productName` | 应用标识和显示名 |
| 后端 `application-local.yml` → `app.desktop.download-url` | 网页上「下载桌面端」指向哪 |
| 后端 `application-local.yml` → `app.base-url` | 站点对外地址，邮件里的链接用它 |
| `ecosystem.config.js` → `APP_DIR` / `APP_JAR` | 服务器上的应用目录和 jar 名 |

`app.desktop.download-url` 留空时网页不显示桌面端入口，所以自建时忘了配也不会出问题。
客户端启动后自己检查 `latest.yml`，有新版就在顶部提示，点一下就下载、安装并重启。

## 改服务器地址

地址编译在 `src/config.js` 里，改完重新打包：

```js
const SERVER_URL = 'https://memo.goldminer.cloud'
```

同一个文件里的 `OPEN_DEVTOOLS` 打开后，应用启动时会自动弹出开发者工具，方便排查线上页面。

## 目录结构

```
desktop/
  src/
    main.js        主进程：窗口、托盘、菜单、加载失败处理
    config.js      打包期常量（站点地址、是否开开发者工具）
    preload.js     暴露给渲染层的少量能力（重试 / 打开外链 / 退出 / 同步主题）
    error.html     连接失败时显示的本地页面
    assets/
      icon.png     256×256，窗口图标
      tray.png     32×32，托盘图标
  build/
    icon.png       512×512，electron-builder 用它生成 exe 图标和安装包图标
```

## 已实现的行为

- 单实例：重复点图标会把已有窗口唤到前面
- 版本号可见：preload 把外壳版本交给网页（`window.shiyi.version`），个人中心据此显示
  「当前版本」
- 自动更新：启动后静默检查 `latest.yml`，有新版就在顶部提示；用户点「更新」后
  在应用内下载并显示进度，装完自动重启（重启前会等笔记的编辑先落库）
- 无边框窗口：不要系统那条标题栏，颜色跟站点走，系统只保留右上角的最小化 /
  最大化 / 关闭。站点切换深浅色时，那三个按钮的底色也跟着换
  （站点调用 `window.shiyi.setTheme` 通知外壳）
- 关窗收进托盘，托盘菜单里才有「退出」；首次收托盘会弹一次气泡提示
- 记忆窗口大小和位置；显示器变更导致旧坐标跑出屏幕时会自动忽略位置
- 站外链接交给系统浏览器打开，不会把外壳导航跑偏
- 连不上服务器时显示本地错误页，带「重试 / 用浏览器打开 / 退出」
- 渲染进程崩溃后自动重载一次
- 右键菜单缺失的问题：菜单栏（按 Alt 唤出）里带撤销/复制/粘贴/全选和缩放

## 换图标

- 应用图标：替换 `build/icon.png`（建议 ≥256×256 的方形 PNG），重新打包
- 托盘图标：替换 `src/assets/tray.png`。日志图标细节较多，缩到 16×16 会有点糊，
  建议单独做一版只有琥珀色图形的透明底图标

## 加新页面时注意

窗口是无边框的（`titleBarStyle: 'hidden'` + `titleBarOverlay`），系统只在右上角
叠最小化 / 最大化 / 关闭，标题栏那一块让给页面自己画。所以页面顶栏要按这两条来：

- 右边留出按钮的位置：`padding-right: calc(<原本的右边距> + var(--titlebar-inset))`。
  `--titlebar-inset` 是 `main.css` 里的全局变量，浏览器取不到那组 env()，
  自动算成 0，网页版布局不受影响；
- 顶栏加 `-webkit-app-region: drag` 才能按住它拖动窗口；顶栏里的按钮 / 输入框
  要逐个标 `-webkit-app-region: no-drag`，否则点不动。

## 尚未实现

- **全局快捷键随手记**（需求方明确要求先不做）
- **代码签名**：当前 exe 未签名，用户首次运行会被 Windows SmartScreen 拦一次，
  需要「更多信息 → 仍要运行」。要消除需要购买 OV/EV 代码签名证书。
- **自动更新的签名**：自动更新本身不需要签名，但用户首次从浏览器下载安装包时
  仍会吃一次 SmartScreen。
- **系统通知 / 屏幕截图**：站点走 HTTPS 时属于 secure context，可以直接用。
  如果哪天部署回 `http://`，这类能力会被浏览器限制，需要加
  `app.commandLine.appendSwitch('unsafely-treat-insecure-origin-as-secure', '<站点地址>')`
  才能启用。

## 日志排查

主进程日志写在标准输出。开发时直接在终端看；打包版可以这样抓：

```powershell
Start-Process "release\win-unpacked\拾忆.exe" -RedirectStandardOutput out.log -RedirectStandardError err.log
```
