import { ElMessage } from 'element-plus'
import markdownCss from '@/styles/markdown.css?raw'
import { sanitizeRichText } from '@/utils/richText'
import { enhanceAttachmentLinks } from '@/utils/attachments'
import { htmlToMarkdown } from '@/utils/htmlToMarkdown'
import { downloadBlob } from '@/utils/download'

export type ExportFormat = 'md' | 'html' | 'doc' | 'pdf' | 'png'

export interface ExportableNote {
  title: string
  contentHtml: string
}

/** 站内图片内联进导出文件的上限，超过的退回绝对链接（需要服务器可达才能看到） */
const EMBED_LIMIT = 8 * 1024 * 1024
/** 长图 / PDF 截取用的文档宽度 */
const SHEET_WIDTH = 760
const ASSET_PATTERN = /^\/api\/files\/(\d+)\/(raw|thumb|download)(\?.*)?$/

/**
 * 单条笔记导出入口。md / html 直接落文件；png / pdf 先把渲染好的笔记
 * 挂到一个屏幕外的文档节点上再截图或转印。
 */
export async function exportNote(format: ExportFormat, note: ExportableNote) {
  const base = exportBaseName(note.title)
  try {
    switch (format) {
      case 'md':
        exportMarkdown(note, base)
        break
      case 'html':
        downloadBlob(
          `${base}.html`,
          new Blob([await buildStandaloneHtml(note)], { type: 'text/html;charset=utf-8' }),
        )
        ElMessage.success('已导出 HTML')
        break
      case 'doc':
        downloadBlob(`${base}.doc`, await exportWord(note))
        ElMessage.success('已导出 Word 文档')
        break
      case 'pdf':
        await exportPdf(note, base)
        break
      case 'png':
        await exportPng(note, base)
        break
    }
  } catch (error) {
    console.error('[export] 导出失败', error)
    ElMessage.error('导出失败，请重试')
  }
}

/* ---------------------------------------------------------------------- Markdown */

function exportMarkdown(note: ExportableNote, base: string) {
  let md = htmlToMarkdown(sanitizeRichText(note.contentHtml ?? ''))
  // 标题不补进正文的话，单独打开导出的文件就没有名字了
  if (note.title.trim() && !/^#\s/.test(md)) {
    md = `# ${note.title.trim()}\n\n${md}`
  }
  downloadBlob(`${base}.md`, new Blob([md], { type: 'text/markdown;charset=utf-8' }))
  ElMessage.success('已导出 Markdown')
}

/* ---------------------------------------------------------------------- HTML */

/**
 * 自包含的单文件 HTML：内联主题变量和 markdown 样式，站内图片转成 data URI，
 * 断网也能打开。视频等大附件保留指向服务器的绝对链接。
 */
async function buildStandaloneHtml(note: ExportableNote): Promise<string> {
  const rendered = await inlineAssets(sanitizeRichText(note.contentHtml))
  const title = note.title.trim() || '无标题'
  const today = new Date().toLocaleDateString('zh-CN')
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<style>
:root { ${themeTokenCss()} }
html, body { margin: 0; background: var(--bg, #faf9f5); }
body { font-family: ${fontStackCss()}; color: var(--text, #1c1917); }
${markdownCss}
.export-doc { max-width: ${SHEET_WIDTH}px; margin: 0 auto; padding: 48px 24px 72px; }
.export-doc-title { margin: 0 0 6px; font-size: 26px; font-weight: 700; color: var(--text); overflow-wrap: anywhere; }
.export-doc-meta { margin: 0 0 32px; color: var(--text-3, #a8a29e); font-size: 12.5px; }
.export-doc-footer { margin-top: 48px; color: var(--text-3, #a8a29e); font-size: 12px; }
</style>
</head>
<body>
<main class="export-doc">
<h1 class="export-doc-title">${escapeHtml(title)}</h1>
<p class="export-doc-meta">导出自「拾忆」 · ${today}</p>
<div class="markdown-body">${rendered}</div>
<p class="export-doc-footer">由「拾忆」导出</p>
</main>
</body>
</html>`
}

/** 把指向站内附件的链接改写成 data URI 或绝对链接 */
async function inlineAssets(html: string): Promise<string> {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  enhanceAttachmentLinks(doc.body)
  const jobs: Promise<void>[] = []
  for (const element of doc.querySelectorAll('img[src], a[href], video[src], audio[src], source[src]')) {
    for (const attribute of ['src', 'href'] as const) {
      const value = element.getAttribute(attribute)
      if (!value) continue
      if (!ASSET_PATTERN.test(value)) continue
      jobs.push(inlineOne(element, attribute, value))
    }
  }
  await Promise.all(jobs)
  return doc.body.innerHTML
}

async function inlineOne(element: Element, attribute: 'src' | 'href', path: string) {
  const absolute = new URL(path, location.origin).href
  try {
    const res = await fetch(path, { credentials: 'include' })
    if (!res.ok) throw new Error(String(res.status))
    const blob = await res.blob()
    if (blob.type.startsWith('image/') && blob.size <= EMBED_LIMIT) {
      element.setAttribute(attribute, await blobToDataUrl(blob))
      return
    }
  } catch {
    /* 拉不到就留绝对链接，别让导出卡住 */
  }
  element.setAttribute(attribute, absolute)
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error ?? new Error('读取附件失败'))
    reader.readAsDataURL(blob)
  })
}

/* ---------------------------------------------------------------------- Word */

/**
 * .doc 本质是 Word 能直接打开的 HTML（MHT 包一层）。不复用 buildStandaloneHtml
 * 是因为 Word 的 HTML 引擎不认 CSS 变量，这里用一套写死浅色值的简洁样式，
 * 渲染和图片内联管线照旧。
 */
async function exportWord(note: ExportableNote): Promise<Blob> {
  const rendered = await inlineAssets(sanitizeRichText(note.contentHtml))
  const title = note.title.trim() || '无标题'
  const today = new Date().toLocaleDateString('zh-CN')
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
<style>
body { font-family: "Microsoft YaHei", "PingFang SC", sans-serif; font-size: 12pt; color: #1c1917; line-height: 1.6; }
h1, h2, h3, h4 { line-height: 1.35; }
h1 { font-size: 20pt; } h2 { font-size: 16pt; } h3 { font-size: 14pt; }
img { max-width: 100%; }
table { border-collapse: collapse; width: 100%; }
th, td { border: 1px solid #d6d3d1; padding: 6px 10px; text-align: left; }
th { background: #f5f5f4; }
pre { background: #f5f5f4; border: 1px solid #d6d3d1; padding: 10px; font-family: Consolas, monospace; font-size: 10.5pt; white-space: pre-wrap; word-break: break-word; }
code { font-family: Consolas, monospace; background: #f5f5f4; }
blockquote { border-left: 3px solid #d6d3d1; margin-left: 0; padding-left: 12px; color: #57534e; }
a { color: #b45309; }
a.shiyi-attachment-card { display: inline-block; border: 1px solid #d6d3d1; border-radius: 8px; padding: 5px 8px; color: #1c1917; text-decoration: none; }
.shiyi-attachment-icon svg { width: 16px; height: 16px; fill: #b45309; vertical-align: middle; margin-right: 5px; }
.shiyi-attachment-action { color: #b45309; margin-left: 8px; }
.word-title { font-size: 20pt; font-weight: 700; margin: 0 0 4pt; }
.word-meta { color: #78716c; font-size: 9pt; margin: 0 0 18pt; }
.word-footer { color: #a8a29e; font-size: 9pt; margin-top: 24pt; }
</style>
</head>
<body>
<p class="word-title">${escapeHtml(title)}</p>
<p class="word-meta">导出自「拾忆」 · ${today}</p>
${rendered}
<p class="word-footer">由「拾忆」导出</p>
</body>
</html>`
  // 主入口 build/api.js 里有 Rollup 解析不了的 with 语句，用浏览器版 dist
  const htmlDocx = await loadHtmlDocx()
  return htmlDocx.asBlob(html)
}

/* html-docx-js 以 UMD 形态供应商化在 public/lib（npm 主入口的 with 语句
   无法被 Rollup 打包），这里按需注入 script，只拖慢第一次导出 Word */
let htmlDocxLoader: Promise<NonNullable<Window['htmlDocx']>> | null = null

function loadHtmlDocx() {
  htmlDocxLoader ??= new Promise<NonNullable<Window['htmlDocx']>>((resolve, reject) => {
    if (window.htmlDocx) {
      resolve(window.htmlDocx)
      return
    }
    const script = document.createElement('script')
    script.src = '/lib/html-docx.js'
    script.onload = () => {
      if (window.htmlDocx) {
        resolve(window.htmlDocx)
      } else {
        htmlDocxLoader = null
        reject(new Error('Word 组件加载异常'))
      }
    }
    script.onerror = () => {
      htmlDocxLoader = null
      reject(new Error('Word 组件加载失败'))
    }
    document.head.appendChild(script)
  })
  return htmlDocxLoader
}

/* ---------------------------------------------------------------------- PDF */

async function exportPdf(note: ExportableNote, base: string) {
  // 桌面壳在：把自包含 HTML 交给主进程，用 Chromium 打印引擎出矢量 PDF
  if (window.shiyi?.exportPdf) {
    const html = await buildStandaloneHtml(note)
    const result = await window.shiyi.exportPdf(html, `${base}.pdf`)
    if (!result?.canceled) {
      ElMessage.success(result?.path ? `已保存到 ${result.path}` : '已导出 PDF')
    }
    return
  }

  const { node, cleanup } = mountSheet(note)
  try {
    await waitForImages(node)
    const html2pdf = (await import('html2pdf.js')).default
    const background = getComputedStyle(node).backgroundColor || '#ffffff'
    await html2pdf()
      .set({
        margin: [14, 12, 16, 12],
        filename: `${base}.pdf`,
        image: { type: 'jpeg', quality: 0.96 },
        html2canvas: { scale: 2, backgroundColor: background },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      })
      .from(node)
      .save()
    ElMessage.success('已导出 PDF')
  } finally {
    cleanup()
  }
}

/* ---------------------------------------------------------------------- PNG */

async function exportPng(note: ExportableNote, base: string) {
  const { node, cleanup } = mountSheet(note)
  try {
    await waitForImages(node)
    const { snapdom } = await import('@zumer/snapdom')
    const blob = await snapdom.toBlob(node, { scale: 2 })
    downloadBlob(`${base}.png`, blob)
    ElMessage.success('已导出长图')
  } finally {
    cleanup()
  }
}

/* ---------------------------------------------------------------------- 文档节点 */

/**
 * 屏幕外挂一个「纸张」节点：当前主题的配色 + markdown 样式，
 * png / pdf-web 都从这里截取。返回的 cleanup 用完必须调用。
 */
function mountSheet(note: ExportableNote): { node: HTMLElement; cleanup: () => void } {
  const host = document.createElement('div')
  host.setAttribute('aria-hidden', 'true')
  host.style.cssText = `position:fixed;top:0;left:-99999px;width:${SHEET_WIDTH}px;pointer-events:none;`
  const title = escapeHtml(note.title.trim() || '无标题')
  const today = new Date().toLocaleDateString('zh-CN')
  host.innerHTML = `
    <style>
      .export-sheet { padding: 40px 44px 48px; background: var(--panel, #ffffff); color: var(--text); }
      .export-sheet-title { margin: 0 0 6px; font-size: 24px; font-weight: 700; color: var(--text); overflow-wrap: anywhere; }
      .export-sheet-meta { margin: 0 0 28px; color: var(--text-3); font-size: 12.5px; }
      .export-sheet h1, .export-sheet pre, .export-sheet blockquote,
      .export-sheet table, .export-sheet img { break-inside: avoid; page-break-inside: avoid; }
    </style>
    <div class="export-sheet">
      <h1 class="export-sheet-title">${title}</h1>
      <p class="export-sheet-meta">导出自「拾忆」 · ${today}</p>
      <div class="markdown-body">${sanitizeRichText(note.contentHtml)}</div>
    </div>`
  document.body.appendChild(host)
  const node = host.querySelector<HTMLElement>('.export-sheet')
  if (!node) {
    host.remove()
    throw new Error('导出节点挂载失败')
  }
  enhanceAttachmentLinks(node)
  return { node, cleanup: () => host.remove() }
}

async function waitForImages(node: HTMLElement) {
  const images = Array.from(node.querySelectorAll('img'))
  await Promise.all(
    images.map((img) => {
      if (img.complete) return Promise.resolve()
      return new Promise<void>((resolve) => {
        img.addEventListener('load', () => resolve(), { once: true })
        img.addEventListener('error', () => resolve(), { once: true })
      })
    }),
  )
  // 再等一帧，让布局稳定下来再截
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
}

/* ---------------------------------------------------------------------- 杂项 */

/** 主题变量原样搬进导出文件，导出时的深浅色就是文件里的配色 */
function themeTokenCss(): string {
  const computed = getComputedStyle(document.documentElement)
  const parts: string[] = []
  for (let i = 0; i < computed.length; i++) {
    const prop = computed.item(i)
    if (!prop.startsWith('--')) continue
    const value = computed.getPropertyValue(prop).trim()
    if (value) parts.push(`${prop}:${value}`)
  }
  return parts.join(';')
}

function fontStackCss(): string {
  return getComputedStyle(document.body).fontFamily || 'system-ui, sans-serif'
}

function exportBaseName(title: string): string {
  return `${sanitizeFilename(title)}-${stamp()}`
}

function sanitizeFilename(name: string): string {
  const clean = (name || '').replace(/[\\/:*?"<>|\x00-\x1f\x7f]/g, '').trim()
  return (clean || '无标题').slice(0, 60)
}

function stamp(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
}

function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }
  return text.replace(/[&<>"']/g, (ch) => map[ch] ?? ch)
}
