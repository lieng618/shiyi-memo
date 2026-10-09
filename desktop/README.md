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
| `shiyi-memo-<版本>-portable.exe` | 免安装单文件版，双击即用 |
| `win-unpacked/` | 未压缩的绿色目录，方便排查问题 |

只想快速验证不生成安装包，用 `npm run dist:dir`。

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
- **自动更新**：外壳本身更新需要用户手动装新版本。
  前端不在此列——前端改完部署到服务器即可，用户无感。
- **系统通知 / 屏幕截图**：站点走 HTTPS 时属于 secure context，可以直接用。
  如果哪天部署回 `http://`，这类能力会被浏览器限制，需要加
  `app.commandLine.appendSwitch('unsafely-treat-insecure-origin-as-secure', '<站点地址>')`
  才能启用。

## 日志排查

主进程日志写在标准输出。开发时直接在终端看；打包版可以这样抓：

```powershell
Start-Process "release\win-unpacked\拾忆.exe" -RedirectStandardOutput out.log -RedirectStandardError err.log
```
