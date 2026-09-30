'use strict'

const { contextBridge, ipcRenderer } = require('electron')

// 渲染层只拿到这几个明确的能力，除此之外碰不到 Node
contextBridge.exposeInMainWorld('shiyi', {
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
