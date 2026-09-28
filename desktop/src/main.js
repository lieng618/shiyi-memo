'use strict'

const {
  app, BrowserWindow, Menu, Tray, shell, ipcMain, nativeImage, nativeTheme, screen,
} = require('electron')
const fs = require('node:fs')
const path = require('node:path')

// 固定 userData 为纯 ASCII 目录名。中文目录名在缓存、崩溃转储等环节偶尔出问题。
app.setPath('userData', path.join(app.getPath('appData'), 'shiyi-memo'))

const { getConfigPath, ensureConfigFile, loadConfig } = require('./config')

const ASSETS_DIR = path.join(__dirname, 'assets')
const WINDOW_STATE_FILE = path.join(app.getPath('userData'), 'window-state.json')
const DEFAULT_BOUNDS = { width: 1180, height: 800 }
const APP_TITLE = '拾忆'
/** 无边框窗口的标题栏高度，跟系统按钮一样高 */
const TITLE_BAR_HEIGHT = 34
/**
 * 标题栏配色，两边都用站点里的 --bg。
 * 这样窗口最上面和页面正文是一个色，不再有一条独立的系统栏。
 */
const TITLE_BAR_THEME = {
  dark: { color: '#171614', symbolColor: '#f1ede6' },
  light: { color: '#faf9f5', symbolColor: '#1c1917' },
}

const config = loadConfig()

let mainWindow = null
let tray = null
let isQuitting = false
let trayHintShown = false
let saveTimer = null

function titleBarOptions(dark) {
  return { ...(dark ? TITLE_BAR_THEME.dark : TITLE_BAR_THEME.light), height: TITLE_BAR_HEIGHT }
}

/**
 * 无边框窗口下，最小化 / 最大化 / 关闭由 Window Controls Overlay 提供，
 * 它的底色和符号色要我们自己跟进站点深浅色，否则会在深色页面上留一块浅色。
 */
function applyTitleBarTheme(dark) {
  nativeTheme.themeSource = dark ? 'dark' : 'light'
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.setTitleBarOverlay(titleBarOptions(dark))
  }
}

/* ---------------------------------------------------------------- 窗口位置记忆 */

function loadBounds() {
  let saved = null
  try {
    saved = JSON.parse(fs.readFileSync(WINDOW_STATE_FILE, 'utf8'))
  } catch {
    // 首次运行没有这个文件，正常
  }
  if (!saved || !Number.isFinite(saved.width) || !Number.isFinite(saved.height)) {
    return { ...DEFAULT_BOUNDS }
  }
  // 显示器拔掉/换分辨率后，旧坐标可能落在屏幕外，这时只用尺寸不用位置
  const visible = Number.isFinite(saved.x) && Number.isFinite(saved.y)
    && screen.getAllDisplays().some((display) => {
      const area = display.workArea
      return saved.x < area.x + area.width
        && saved.x + saved.width > area.x
        && saved.y < area.y + area.height
        && saved.y + saved.height > area.y
    })
  return {
    width: saved.width,
    height: saved.height,
    ...(visible ? { x: saved.x, y: saved.y } : {}),
    ...(saved.maximized ? { maximized: true } : {}),
  }
}

function saveBounds() {
  if (!mainWindow || mainWindow.isDestroyed()) return
  if (mainWindow.isMinimized()) return
  const maximized = mainWindow.isMaximized()
  // 最大化时 getBounds 返回的是最大化后的尺寸，存普通状态的大小才有意义
  const bounds = maximized ? mainWindow.getNormalBounds() : mainWindow.getBounds()
  try {
    fs.writeFileSync(
      WINDOW_STATE_FILE,
      JSON.stringify({ ...bounds, maximized }, null, 2),
      'utf8',
    )
  } catch (err) {
    console.warn('[window] 保存窗口位置失败：', err.message)
  }
}

function scheduleSaveBounds() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveBounds, 500)
}

/* ---------------------------------------------------------------------- 地址判断 */

function isInternalUrl(url) {
  try {
    return new URL(url).origin === new URL(config.serverUrl).origin
  } catch {
    return false
  }
}

function openExternal(url) {
  // 只放行 http/https，避免渲染层被注入后拿去启动别的协议
  if (/^https?:\/\//i.test(url)) {
    shell.openExternal(url)
  }
}

/* ------------------------------------------------------------------------ 窗口 */

function showMainWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    createWindow()
    return
  }
  if (mainWindow.isMinimized()) mainWindow.restore()
  mainWindow.show()
  mainWindow.focus()
}

function showConnectionError(errorCode, errorDescription) {
  if (!mainWindow || mainWindow.isDestroyed()) return
  // 错误页本身是深色的，标题栏也跟着深色，免得顶上留一条白边
  applyTitleBarTheme(true)
  const page = path.join(__dirname, 'error.html')
  mainWindow.loadFile(page, {
    query: {
      url: config.serverUrl,
      code: String(errorCode),
      desc: errorDescription || 'unknown',
    },
  })
}

function loadApp() {
  if (!mainWindow || mainWindow.isDestroyed()) return
  mainWindow.loadURL(config.serverUrl).catch((err) => {
    // did-fail-load 一般已经覆盖了，这里兜底避免出现未捕获的 rejection
    console.warn('[app] 加载失败：', err.message)
  })
}

function createWindow() {
  const bounds = loadBounds()

  mainWindow = new BrowserWindow({
    width: bounds.width,
    height: bounds.height,
    ...(Number.isFinite(bounds.x) ? { x: bounds.x } : {}),
    ...(Number.isFinite(bounds.y) ? { y: bounds.y } : {}),
    minWidth: 900,
    minHeight: 600,
    title: APP_TITLE,
    icon: path.join(ASSETS_DIR, 'icon.png'),
    backgroundColor: '#171614',
    autoHideMenuBar: true,
    // 不要系统那条标题栏：颜色由站点决定，系统只保留右上角那三个按钮
    titleBarStyle: 'hidden',
    titleBarOverlay: titleBarOptions(nativeTheme.shouldUseDarkColors),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  })

  if (bounds.maximized) mainWindow.maximize()

  // 先隐藏再显示，避免远程页面加载期间出现一大片白屏
  mainWindow.once('ready-to-show', () => {
    mainWindow.show()
    if (config.openDevTools) mainWindow.webContents.openDevTools({ mode: 'detach' })
  })
  // 远程站点偶尔不触发 ready-to-show，兜一个超时，别让窗口一直不出现
  const showFallback = setTimeout(() => {
    if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) mainWindow.show()
  }, 5000)
  mainWindow.on('closed', () => clearTimeout(showFallback))

  mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription, _url, isMainFrame) => {
    if (!isMainFrame) return
    if (errorCode === -3) return // ERR_ABORTED：用户主动取消或页面自身跳转，不算故障
    console.warn(`[app] 加载失败 ${errorCode} ${errorDescription}`)
    showConnectionError(errorCode, errorDescription)
  })

  mainWindow.webContents.on('did-finish-load', () => {
    console.log('[app] 页面加载完成：', mainWindow.webContents.getURL())
  })

  // 渲染进程崩了就重载一次，比留一个白窗口强
  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    console.warn('[app] 渲染进程结束：', details.reason)
    if (!isQuitting) loadApp()
  })

  // 站外链接交给系统浏览器，别把壳导航跑偏
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (isInternalUrl(url)) return
    event.preventDefault()
    openExternal(url)
  })

  mainWindow.on('resize', scheduleSaveBounds)
  mainWindow.on('move', scheduleSaveBounds)
  mainWindow.on('maximize', scheduleSaveBounds)
  mainWindow.on('unmaximize', scheduleSaveBounds)

  // 点 X 收进托盘，真正退出走托盘菜单或菜单栏
  mainWindow.on('close', (event) => {
    if (isQuitting) {
      saveBounds()
      return
    }
    // 托盘没建起来就不能拦关闭，否则窗口藏起来后再也调不回来
    if (!tray) {
      isQuitting = true
      saveBounds()
      return
    }
    event.preventDefault()
    mainWindow.hide()
    if (!trayHintShown) {
      trayHintShown = true
      try {
        tray.displayBalloon({
          title: APP_TITLE,
          content: '已最小化到托盘，右键托盘图标可以退出。',
          icon: nativeImage.createFromPath(path.join(ASSETS_DIR, 'tray.png')),
        })
      } catch {
        // 部分系统不支持气泡提示，忽略即可
      }
    }
  })

  loadApp()
}

/* ------------------------------------------------------------------------ 托盘 */

function createTray() {
  const icon = nativeImage.createFromPath(path.join(ASSETS_DIR, 'tray.png'))
  if (icon.isEmpty()) {
    console.warn('[tray] 托盘图标加载失败，跳过托盘')
    return
  }
  tray = new Tray(icon)
  tray.setToolTip(APP_TITLE)
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '打开拾忆', click: showMainWindow },
    { label: '在浏览器中打开', click: () => openExternal(config.serverUrl) },
    { type: 'separator' },
    { label: '重新加载', click: () => { showMainWindow(); mainWindow.webContents.reload() } },
    { label: '打开配置文件', click: () => shell.openPath(getConfigPath()) },
    { type: 'separator' },
    { label: '退出', click: () => { isQuitting = true; app.quit() } },
  ]))
  tray.on('click', showMainWindow)
  tray.on('double-click', showMainWindow)
}

/* ------------------------------------------------------------------------ 菜单 */

function buildMenu() {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: APP_TITLE,
      submenu: [
        { label: '在浏览器中打开', click: () => openExternal(config.serverUrl) },
        { label: '打开配置文件', click: () => shell.openPath(getConfigPath()) },
        { type: 'separator' },
        { label: '退出', click: () => { isQuitting = true; app.quit() } },
      ],
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo', label: '撤销' },
        { role: 'redo', label: '重做' },
        { type: 'separator' },
        { role: 'cut', label: '剪切' },
        { role: 'copy', label: '复制' },
        { role: 'paste', label: '粘贴' },
        { role: 'selectAll', label: '全选' },
      ],
    },
    {
      label: '视图',
      submenu: [
        { role: 'reload', label: '重新加载' },
        { role: 'forceReload', label: '强制重新加载' },
        { type: 'separator' },
        { role: 'resetZoom', label: '实际大小' },
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' },
        { role: 'toggleDevTools', label: '开发者工具' },
      ],
    },
  ]))
}

/* ------------------------------------------------------------------------- 启动 */

function bootstrap() {
  ensureConfigFile()
  buildMenu()
  createWindow()
  createTray()

  app.on('second-instance', showMainWindow)

  app.on('before-quit', () => {
    isQuitting = true
  })

  app.on('window-all-closed', () => {
    // Windows 上正常情况走不到这里（关窗被拦成隐藏），退出流程兜底
    if (isQuitting) app.quit()
  })

  app.on('activate', showMainWindow)
}

ipcMain.on('app:retry', () => {
  loadApp()
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.show()
})

ipcMain.on('app:quit', () => {
  isQuitting = true
  app.quit()
})

ipcMain.on('app:open-external', (_event, url) => {
  openExternal(String(url || ''))
})

// 站点切换深浅色时跟着换标题栏。
ipcMain.on('app:set-theme', (_event, dark) => {
  applyTitleBarTheme(Boolean(dark))
})

// 单实例：重复点图标就把已有窗口唤到前面，而不是开出第二个
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.whenReady().then(bootstrap)
}
