'use strict'

const { contextBridge, ipcRenderer } = require('electron')

// 渲染层只拿到这几个明确的能力，除此之外碰不到 Node
contextBridge.exposeInMainWorld('shiyi', {
  /** 外壳版本号，网页用它判断客户端要不要更新 */
  version: ipcRenderer.sendSync('app:get-version'),
  /** 检查有没有新版本（只查不下载） */
  checkUpdate: () => ipcRenderer.invoke('app:check-update'),
  /** 开始下载新版本；下完由界面调 installUpdate 重启安装 */
  startUpdate: () => ipcRenderer.invoke('app:start-update'),
  /** 取一次当前更新状态（界面加载晚于检查时补上） */
  getUpdateState: () => ipcRenderer.invoke('app:get-update-state'),
  /** 订阅更新状态变化，返回取消订阅的函数 */
  onUpdateState: (callback) => {
    const listener = (_event, state) => callback(state)
    ipcRenderer.on('app:update-state', listener)
    return () => ipcRenderer.removeListener('app:update-state', listener)
  },
  /** 退出并安装已经下好的新版本 */
  installUpdate: () => ipcRenderer.send('app:install-update'),
  /** 连接失败页上的「重试」 */
  retry: () => ipcRenderer.send('app:retry'),
  /** 用系统浏览器打开地址 */
  openExternal: (url) => ipcRenderer.send('app:open-external', url),
  /** 退出应用 */
  quit: () => ipcRenderer.send('app:quit'),
  /** 站点切换深浅色时同步过来，让窗口标题栏跟着一起变 */
  setTheme: (dark) => ipcRenderer.send('app:set-theme', Boolean(dark)),
  /** 把自包含 HTML 交给主进程用 Chromium 打印引擎转成 PDF，弹系统「另存为」 */
  exportPdf: (html, filename) =>
    ipcRenderer.invoke('app:export-pdf', String(html ?? ''), String(filename ?? '')),
})
