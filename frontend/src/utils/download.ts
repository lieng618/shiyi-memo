/**
 * 触发浏览器下载。桌面壳里由 will-download 接管，会弹出系统「另存为」对话框。
 */
export function downloadBlob(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // 大文件瞬间点多次下载时，旧链接要等浏览器真正开始读再回收
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

/**
 * 下载同源地址。不要用 window.open：附件响应带 attachment 时，
 * 新窗口容易只开出一个空页或被弹窗拦截，用户看起来就是“点了没反应”。
 */
export function downloadUrl(url: string, filename?: string) {
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename?.trim() || ''
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}
