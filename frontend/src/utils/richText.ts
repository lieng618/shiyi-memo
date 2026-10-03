import DOMPurify from 'dompurify'

/**
 * 存储的富文本 HTML → 净化后的 HTML。分享页、后台预览和导出共用同一条管线，
 * 保证导出文件和页面看到的内容一致。
 */
export function sanitizeRichText(source: string): string {
  if (!source.trim()) {
    return ''
  }
  return DOMPurify.sanitize(source, {
    ADD_ATTR: ['target', 'controls', 'preload', 'style'],
  })
}
