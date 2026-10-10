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
| `shiyi-memo-setup-<版本>.exe` | NSIS 安装包，可选安装目录，建桌面和开始菜单快捷方式 |
| `win-unpacked/` | 未压缩的绿色目录，方便排查问题 |

只想快速验证不生成安装包，用 `npm run dist:dir`。

只发 NSIS 安装版：免安装版没有安装过程，接不了自动更新。

## 发布新版本

一条命令搞定：

```powershell
cd desktop
.\publish.ps1 -Version 1.1.0
```

它做的事：把版本号写进 `package.json` → 打包 → 把下面三个文件传到服务器的下载目录
→ 在服务器上拷一份固定文件名的副本给网页的「下载桌面端」用。

```
shiyi-memo-setup-<版本>.exe            安装包
shiyi-memo-setup-<版本>.exe.blockmap   差量更新用，别漏
latest.yml                             客户端读的更新清单
```

| 参数 | 说明 |
| --- | --- |
| 不带参数 | 用当前版本号重新打包并上传 |
| `-Version 1.1.0` | 先把版本号改成 1.1.0 |
| `-SkipUpload` | 只打包，不传服务器 |
| `-SkipBuild` | 用 `release\` 里已有的产物，只上传 |
| `-Server` / `-RemoteDir` | 覆盖默认的服务器地址和下载目录 |

### 服务器上的前提

脚本默认走 `ubuntu` 账号——腾讯云 Ubuntu 镜像的登录账号，`root` 的 SSH 登录默认是关的
（所以能 `su` 进 root，但 `ssh root@` 连不上）。别改回 `root@`。

下载目录如果是 root 建的（用 root 部署过就会这样），脚本会自动退回「先传到临时目录、
再用 `sudo` 搬进去」，中间可能让你输一次 ubuntu 的密码。想彻底免掉，在服务器上跑一次：

```bash
sudo mkdir -p /home/shiyi-memo/start/downloads
sudo chown ubuntu:ubuntu /home/shiyi-memo/start/downloads
```

再把本机的 SSH 公钥加到服务器的 `~/.ssh/authorized_keys`，以后就是真正的一键，
密码和 sudo 都不用输。

版本号只有 `package.json` 这一处——后端会去下载目录读 `latest.yml` 拿最新版本
（想手动指定才用 `application.yml` 的 `app.desktop.latest-version`）。
客户端启动后自己检查，有新版就在顶部提示，点一下就下载、安装并重启。

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
  「当前版本」并和服务器上的最新版比对
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
