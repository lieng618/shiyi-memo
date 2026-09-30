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
