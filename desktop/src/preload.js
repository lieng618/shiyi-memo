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
})
