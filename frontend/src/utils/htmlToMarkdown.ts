import TurndownService from 'turndown'
import { gfm } from 'turndown-plugin-gfm'

/**
 * 富文本 HTML → Markdown，导出 .md 时用。
 * 规则继承自旧版 MarkdownEditor 的存储往返转换，并补充了 wangEditor 的
 * 待办块（div[data-w-e-type="todo"]）写回 GFM 任务列表的规则。
 */
const turndown = new TurndownService({
  headingStyle: 'atx',
  hr: '* * *',
  bulletListMarker: '-',
  codeBlockStyle: 'fenced',
  emDelimiter: '*',
  strongDelimiter: '**',
  linkStyle: 'inlined',
})
turndown.use(gfm)
// gfm 插件把删除线写成单个 ~，重新注册成 GFM 要求的 ~~
turndown.addRule('strikethrough', {
  filter: (node) => ['DEL', 'S', 'STRIKE'].includes(node.nodeName),
  replacement: (content) => `~~${content}~~`,
})
// 字号 / 颜色 / 行高是存在行内 style 上的 HTML，Markdown 表达不了，原样保留。
// 注意：keep 的优先级低于内建规则（<b>、<em> 会先被当成加粗 / 斜体），所以要显式注册一条规则。
turndown.addRule('styledInline', {
  filter: (node) =>
    /(?:font-size|font-family|line-height|color|background-color)\s*:/.test(
      (node as HTMLElement).getAttribute?.('style') ?? '',
    ),
  replacement: (_content, node) => {
    const html = (node as HTMLElement).outerHTML
    return (node as unknown as { isBlock?: boolean }).isBlock ? `\n\n${html}\n\n` : html
  },
})
// Markdown 没有的语义（下标 / 上标 / 下划线）同样原样保留
turndown.keep(['u', 'sub', 'sup'])
// 视频 / 音频 / 内嵌内容同样原样保留
turndown.keep(['video', 'audio', 'source', 'iframe'])
// wangEditor 的待办块写回 GFM 任务列表
turndown.addRule('wangEditorTodo', {
  filter: (node) =>
    node.nodeName === 'DIV' && (node as HTMLElement).getAttribute?.('data-w-e-type') === 'todo',
  replacement: (_content, node) => {
    const el = node as HTMLElement
    const checked = !!el.querySelector('input[type="checkbox"]')?.hasAttribute('checked')
    const text = el.textContent?.trim() ?? ''
    return `\n\n- [${checked ? 'x' : ' '}] ${text}\n`
  },
})
// 表格单元格里的 <br>（没内容时浏览器会塞一个）会把 GFM 表格行拆断，统一压成空格
turndown.addRule('tableCell', {
  filter: ['th', 'td'],
  replacement: (content, node) => {
    const index = [...(node.parentNode?.childNodes ?? [])].indexOf(node as ChildNode)
    const prefix = index === 0 ? '| ' : ' '
    return `${prefix}${content.replace(/\s*\n\s*/g, ' ').trim()} |`
  },
})
// 代码块：整块 <pre> 当作代码内容，语言从 <code> 的 language-xxx 类名读回
turndown.addRule('fencedCodeBlock', {
  filter: (node, options) => options.codeBlockStyle === 'fenced' && node.nodeName === 'PRE',
  replacement: (_content, node, options) => {
    const el = node as HTMLElement
    const language = (el.querySelector('code')?.getAttribute('class') ?? '').match(
      /language-([\w-]+)/,
    )?.[1] ?? ''
    const text = el.textContent ?? ''
    return `\n\n${options.fence}${language}\n${text.replace(/\n+$/, '')}\n${options.fence}\n\n`
  },
})

/** wangEditor 的 HTML 转 Markdown；空白压平成常规段落间距 */
export function htmlToMarkdown(html: string): string {
  if (!html.trim()) return ''
  return turndown
    .turndown(html)
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
