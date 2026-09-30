import DOMPurify from 'dompurify'
import { marked } from 'marked'

/**
 * Markdown → 净化后的 HTML。分享页、详情页和导出共用同一条渲染管线，
 * 保证导出文件和页面看到的内容一致。
 */
export function renderMarkdown(source: string): string {
  if (!source.trim()) {
    return ''
  }
  const rendered = marked.parse(source, { gfm: true, breaks: true }) as string
  return DOMPurify.sanitize(rendered, {
    ADD_ATTR: ['target', 'controls', 'preload', 'style'],
  })
}
