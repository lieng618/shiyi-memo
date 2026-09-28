<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatLineSquare,
  Delete as ClearIcon,
  Finished,
  Grid,
  Link as LinkIcon,
  List as ListIcon,
  Minus,
  Paperclip,
  Picture,
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import TurndownService from 'turndown'
import { gfm } from 'turndown-plugin-gfm'
import { fileApi } from '@/api'
import type { FileAsset } from '@/types'

/**
 * 所见即所得编辑器：编辑区本身就是渲染后的样子（单页，不分编辑 / 预览）。
 * 存储仍然是 Markdown：进入时 marked 渲染成 HTML，编辑后 turndown 再写回 Markdown。
 */
const props = defineProps<{ modelValue: string; memoId?: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [string]; uploaded: [FileAsset] }>()

const surface = ref<HTMLDivElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const empty = ref(!props.modelValue.trim())
const colorPopover = ref(false)
const customColor = ref('#d97706')

const fontSizes = [12, 14, 16, 18, 20, 24, 32]
const colors = ['#1c1917', '#57534e', '#b91c1c', '#d97706', '#15803d', '#2563eb', '#7c3aed', '#db2777']

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
// 字号 / 颜色是存在行内 style 上的 HTML，写回 Markdown 时要原样保留。
// 注意：keep 的优先级低于内建规则（<b>、<em> 会先被当成加粗 / 斜体），所以要显式注册一条规则。
turndown.addRule('styledInline', {
  filter: (node) => /(?:font-size|color)\s*:/.test((node as HTMLElement).getAttribute?.('style') ?? ''),
  replacement: (_content, node) => {
    const html = (node as HTMLElement).outerHTML
    return (node as unknown as { isBlock?: boolean }).isBlock ? `\n\n${html}\n\n` : html
  },
})
// 视频 / 音频 / 内嵌内容同样原样保留
turndown.keep(['video', 'audio', 'source', 'iframe'])
// 表格单元格里的 <br>（没内容时浏览器会塞一个）会把 GFM 表格行拆断，统一压成空格
turndown.addRule('tableCell', {
  filter: ['th', 'td'],
  replacement: (content, node) => {
    const index = [...(node.parentNode?.childNodes ?? [])].indexOf(node as ChildNode)
    const prefix = index === 0 ? '| ' : ' '
    return `${prefix}${content.replace(/\s*\n\s*/g, ' ').trim()} |`
  },
})
// 代码块：编辑时浏览器用 <br> 表示换行，直接取 textContent 会把换行吞掉；
// 另外用工具栏插入时文字有可能落在 <code> 外面，所以整块 <pre> 都当作代码内容。
turndown.addRule('fencedCodeBlock', {
  filter: (node, options) => options.codeBlockStyle === 'fenced' && node.nodeName === 'PRE',
  replacement: (_content, node, options) => {
    const el = node as HTMLElement
    const language = (el.querySelector('code')?.getAttribute('class') ?? '').match(/language-([\w-]+)/)?.[1] ?? ''
    const text = preformattedText(el).replace(/\n+$/, '')
    return `\n\n${options.fence}${language}\n${text}\n${options.fence}\n\n`
  },
})

function preformattedText(el: HTMLElement) {
  const clone = el.cloneNode(true) as HTMLElement
  clone.querySelectorAll('br').forEach((br) => br.replaceWith('\n'))
  return clone.textContent ?? ''
}

let lastEmitted = props.modelValue
/** 工具栏点击会让编辑区失焦，所以随时记住最后一次落在正文里的选区 */
let savedRange: Range | null = null
let composing = false

function renderMarkdown(source: string): string {
  if (!source.trim()) return ''
  const rendered = marked.parse(source, { gfm: true, breaks: true }) as string
  return DOMPurify.sanitize(rendered, {
    ADD_ATTR: ['target', 'controls', 'preload', 'style'],
  })
}

function syncFromProps(markdown: string) {
  const el = surface.value
  if (!el) return
  el.innerHTML = renderMarkdown(markdown)
  // marked 给任务列表的复选框加了 disabled，编辑时要能勾选
  el.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((box) => {
    box.removeAttribute('disabled')
  })
  ensureTrailingParagraph()
  empty.value = isBlank(el)
}

/**
 * 正文以引用块 / 列表 / 表格这类块收尾时，下面没有可落光标的空行，
 * 光标就点不出去。补一个空段落（空段落写回 Markdown 是空字符串，不影响存储）。
 */
function ensureTrailingParagraph() {
  const el = surface.value
  if (!el) return
  const last = el.lastElementChild
  if (!last || last.tagName === 'P' || last.tagName === 'DIV') return
  const tail = document.createElement('p')
  tail.appendChild(document.createElement('br'))
  el.appendChild(tail)
}

/** 只有图片 / 表格之类的内容也算「有内容」，不要盖住占位提示 */
function isBlank(el: HTMLElement) {
  return !el.textContent?.trim() && !el.querySelector('img, video, audio, table, hr, input')
}

function serialize(): string {
  const el = surface.value
  if (!el) return ''
  // 保证块级内容后面总有落脚的空行（写回 Markdown 是空字符串，不污染内容）
  ensureTrailingParagraph()
  // 待办的复选框必须直接挂在 <li> 下：被加粗 / 上色包进去的话，
  // turndown 就认不出它是待办项，`- [ ]` 会退化成普通列表甚至消失。
  el.querySelectorAll<HTMLInputElement>('input[type="checkbox"]').forEach((box) => {
    const item = box.closest('li')
    if (item && box.parentElement !== item) {
      item.insertBefore(box, item.firstChild)
      if (box.nextSibling?.nodeType === Node.TEXT_NODE && !/^\s/.test(box.nextSibling.textContent ?? '')) {
        box.after(document.createTextNode(' '))
      }
    }
    // turndown 解析的是 HTML 字符串，勾选状态得先落到属性上
    box.toggleAttribute('checked', box.checked)
  })
  return turndown.turndown(el.innerHTML).replace(/\n{3,}/g, '\n\n').trim()
}

/** 把编辑区内容写回 Markdown，内容没变就不打扰父组件（避免来回覆盖） */
function commit() {
  const markdown = serialize()
  if (markdown === lastEmitted) return
  lastEmitted = markdown
  emit('update:modelValue', markdown)
}

function rememberSelection() {
  const el = surface.value
  const selection = window.getSelection()
  if (!el || !selection || selection.rangeCount === 0) return
  const range = selection.getRangeAt(0)
  if (el.contains(range.commonAncestorContainer)) savedRange = range.cloneRange()
}

/** 待办项的复选框不能被行内格式命令包进去 */
function avoidTaskCheckbox(range: Range): Range {
  const el = surface.value
  if (!el) return range
  const start = range.startContainer
  const element = start.nodeType === Node.ELEMENT_NODE ? (start as Element) : start.parentElement
  const item = element?.closest('li')
  if (!item || !el.contains(item)) return range
  const box = item.querySelector('input[type="checkbox"]')
  if (!box || !range.intersectsNode(box)) return range
  const next = document.createRange()
  next.setStartAfter(box)
  next.setEnd(range.endContainer, range.endOffset)
  return next
}

/**
 * 恢复正文选区：工具栏控件点下去时焦点已经跑到控件上，这里把选区还回去。
 * 没有可用选区（比如空笔记里直接点工具栏）就把光标落到文末，命令才有作用对象。
 */
function ensureCaret(needBlock = true) {
  const el = surface.value
  if (!el) return
  el.focus()
  const selection = window.getSelection()
  if (!selection) return
  const range = savedRange
  if (range && el.contains(range.commonAncestorContainer)) {
    const safe = avoidTaskCheckbox(range)
    selection.removeAllRanges()
    selection.addRange(safe)
    savedRange = safe.cloneRange()
    // 选区本来就落在某个块里，直接用
    if (!needBlock || currentBlock()) return
  }
  // 行内命令（加粗、字号…）没有块也能执行，不用硬塞一个空段落
  if (!needBlock) return

  // 空文档、或者光标停在正文容器本身（块命令没有作用对象）时，补一个空段落
  let target: Node | null = null
  if (range?.startContainer === el && el.childNodes.length) {
    target = el.childNodes[Math.min(range.startOffset, el.childNodes.length - 1)]
  }
  if (!target) {
    const paragraph = document.createElement('p')
    el.appendChild(paragraph)
    target = paragraph
  }
  const next = document.createRange()
  next.selectNodeContents(target)
  next.collapse(false)
  selection.removeAllRanges()
  selection.addRange(next)
  savedRange = next.cloneRange()
}

function exec(command: string, value?: string) {
  ensureCaret(false)
  document.execCommand(command, false, value)
  commit()
}

/** 围栏代码在 Markdown 里是纯文本，行内样式存不下来，直接拦掉免得白做 */
function rejectInsideCodeBlock(): boolean {
  if (!codeBlockAtCaret()) return false
  ElMessage.info('代码块里不能设置文字样式')
  return true
}

function applyInline(command: string) {
  if (rejectInsideCodeBlock()) return
  exec(command)
}

function escapeHtml(value: string) {
  const map: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
  return value.replace(/[&<>"']/g, (char) => map[char])
}

function selectedText(): string {
  return savedRange?.toString() ?? ''
}

/**
 * 字号 / 颜色走浏览器的原生命令，而不是自己拼 HTML 覆盖选区，
 * 这样套在加粗、斜体上的文字不会把外层样式冲掉。
 */
function applyFontSize(size: number | string) {
  if (size === '') return
  if (rejectInsideCodeBlock()) return
  if (!selectedText().trim()) {
    ElMessage.info('先选中要调整大小的文字')
    return
  }
  ensureCaret(false)
  document.execCommand('styleWithCSS', false, 'true')
  document.execCommand('fontSize', false, '7')
  document.execCommand('styleWithCSS', false, 'false')
  // 浏览器只会写成 xxx-large 这类关键字，替换成选定的像素值
  surface.value?.querySelectorAll<HTMLElement>('[style*="font-size"]').forEach((node) => {
    if (!node.style.fontSize.endsWith('px')) node.style.fontSize = `${Number(size)}px`
  })
  commit()
}

function applyColor(color: string) {
  colorPopover.value = false
  if (rejectInsideCodeBlock()) return
  if (!selectedText().trim()) {
    ElMessage.info('先选中要改颜色的文字')
    return
  }
  ensureCaret(false)
  document.execCommand('styleWithCSS', false, 'true')
  document.execCommand('foreColor', false, color)
  document.execCommand('styleWithCSS', false, 'false')
  commit()
}

function applyInlineCode() {
  if (rejectInsideCodeBlock()) return
  const text = selectedText()
  if (!text.trim()) {
    ElMessage.info('先选中要转成代码的文字')
    return
  }
  exec('insertHTML', `<code>${escapeHtml(text)}</code>`)
}

async function applyLink() {
  const text = selectedText()
  let url: string | null = null
  try {
    const result = await ElMessageBox.prompt('链接地址', '插入链接', {
      inputValue: 'https://',
      inputPlaceholder: 'https://',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    url = result.value?.trim() || null
  } catch {
    return
  }
  if (!url) return
  if (text.trim()) {
    exec('createLink', url)
  } else {
    exec('insertHTML', `<a href="${escapeHtml(url)}">${escapeHtml(url)}</a>`)
  }
}

/** 光标所在的块级元素，用来做「待办」这类整块转换 */
function currentBlock(): HTMLElement | null {
  const el = surface.value
  if (!el) return null
  // 优先用实时选区：连续输入时 selectionchange 会晚一拍，缓存的选区可能还停在上一块
  const live = window.getSelection()
  const range =
    live?.rangeCount && el.contains(live.getRangeAt(0).commonAncestorContainer)
      ? live.getRangeAt(0)
      : savedRange
  if (!range) return null
  const blockTags = ['p', 'li', 'h1', 'h2', 'h3', 'h4', 'blockquote', 'pre', 'div']
  let node: Node | null = range.startContainer
  // 空段落时光标常被浏览器归到正文容器上，取光标所在位置的那个子节点
  if (node === el) {
    node = el.childNodes[Math.min(range.startOffset, Math.max(el.childNodes.length - 1, 0))] ?? null
  }
  while (node && node !== el) {
    if (node.nodeType === Node.ELEMENT_NODE) {
      const element = node as HTMLElement
      const tag = element.tagName.toLowerCase()
      if (blockTags.includes(tag)) return element
      if (element.parentNode === el) return element
    }
    // 新输入的内容有时是直接挂在正文下的文本节点，先补一个段落包起来
    if (node.parentNode === el) {
      const paragraph = document.createElement('p')
      el.insertBefore(paragraph, node)
      paragraph.appendChild(node)
      return paragraph
    }
    node = node.parentNode
  }
  return null
}

function makeCheckbox(checked: boolean) {
  const input = document.createElement('input')
  input.type = 'checkbox'
  input.checked = checked
  return input
}

/** 把块内容搬进列表项：顺手去掉占位的 <br>，否则写回 Markdown 会多出空行 */
function moveBlockContent(from: HTMLElement, to: HTMLElement) {
  while (from.firstChild) to.appendChild(from.firstChild)
  to.querySelectorAll('br').forEach((br) => br.remove())
}

/** 插入块级元素：当前块有内容（或是表格 / 代码块这类容器）就插到它后面，避免把内容冲掉 */
function placeBlock(node: HTMLElement): HTMLElement | null {
  ensureCaret()
  const el = surface.value
  const block = currentBlock()
  if (!el || !block) return null
  const container = ['TABLE', 'PRE', 'BLOCKQUOTE', 'UL', 'OL'].includes(block.tagName)
  if (container || (block.textContent ?? '').trim()) {
    block.after(node)
  } else {
    block.replaceWith(node)
  }
  ensureTailParagraph(node)
  return node
}

function caretAtEnd(node: Node) {
  const range = document.createRange()
  range.selectNodeContents(node)
  range.collapse(false)
  const selection = window.getSelection()
  if (!selection) return
  selection.removeAllRanges()
  selection.addRange(range)
  savedRange = range.cloneRange()
}

function toggleTodo() {
  ensureCaret()
  const el = surface.value
  const block = currentBlock()
  if (!el || !block) return
  const item = block.closest('li')
  if (item && el.contains(item)) {
    const existing = item.querySelector('input[type="checkbox"]')
    if (existing) {
      existing.remove()
    } else {
      item.insertBefore(makeCheckbox(false), item.firstChild)
    }
    caretAtEnd(item)
    commit()
    return
  }
  const list = document.createElement('ul')
  const listItem = document.createElement('li')
  listItem.appendChild(makeCheckbox(false))
  listItem.appendChild(document.createTextNode(' '))
  const wrapper = document.createElement('span')
  moveBlockContent(block, wrapper)
  listItem.appendChild(wrapper)
  list.appendChild(listItem)
  block.replaceWith(list)
  caretAtEnd(wrapper)
  commit()
}

/**
 * 列表自己改 DOM：Chrome 的 insertUnorderedList 在段落里会生成 <p><ul> 这种非法嵌套，
 * 把紧随其后的段落一起吞进列表项，写回 Markdown 时结构就变了。
 */
function toggleList(kind: 'ul' | 'ol') {
  ensureCaret()
  const el = surface.value
  const block = currentBlock()
  if (!el || !block) return
  const list = block.closest('ul, ol') as HTMLElement | null
  const item = block.closest('li') as HTMLElement | null

  if (list && item && el.contains(list)) {
    const sameKind = list.tagName.toLowerCase() === kind
    if (sameKind) {
      // 再点一次 = 取消列表，变回普通段落
      const paragraph = document.createElement('p')
      while (item.firstChild) paragraph.appendChild(item.firstChild)
      item.replaceWith(paragraph)
      if (!list.querySelector('li')) list.replaceWith(paragraph)
      caretAtEnd(paragraph)
    } else {
      const swapped = document.createElement(kind)
      while (list.firstChild) swapped.appendChild(list.firstChild)
      list.replaceWith(swapped)
      caretAtEnd(item)
    }
    commit()
    return
  }

  const wrapper = document.createElement(kind)
  const newItem = document.createElement('li')
  moveBlockContent(block, newItem)
  wrapper.appendChild(newItem)
  block.replaceWith(wrapper)
  caretAtEnd(newItem)
  commit()
}

/** 代码块后面的空段落：不然光标没地方落，也就没法在代码块下面接着写 */
function ensureTailParagraph(pre: HTMLElement) {
  if (pre.nextElementSibling) return
  const tail = document.createElement('p')
  tail.appendChild(document.createElement('br'))
  pre.after(tail)
}

/** 把光标所在的块整块换成 <pre><code>，写回 Markdown 就是 ``` 围栏 */
function insertCodeBlock() {
  ensureCaret()
  const el = surface.value
  const block = currentBlock()
  if (!el || !block) return
  const container = ['TABLE', 'PRE', 'BLOCKQUOTE', 'UL', 'OL'].includes(block.tagName)
  const text = container ? '' : (block.textContent ?? '')
  const pre = document.createElement('pre')
  const code = document.createElement('code')
  code.textContent = text
  pre.appendChild(code)
  if (container) block.after(pre)
  else block.replaceWith(pre)
  ensureTailParagraph(pre)
  caretAtEnd(code)
  commit()
}

/** 插入 3 列表格：写回 Markdown 就是 GFM 表格 */
function insertTable() {
  const columns = 3
  const rows = 2
  const table = document.createElement('table')
  const head = document.createElement('thead')
  const headRow = document.createElement('tr')
  for (let index = 1; index <= columns; index += 1) {
    const cell = document.createElement('th')
    cell.textContent = `列 ${index}`
    headRow.appendChild(cell)
  }
  head.appendChild(headRow)
  table.appendChild(head)
  const body = document.createElement('tbody')
  for (let row = 0; row < rows; row += 1) {
    const tr = document.createElement('tr')
    for (let column = 0; column < columns; column += 1) {
      const cell = document.createElement('td')
      cell.appendChild(document.createElement('br'))
      tr.appendChild(cell)
    }
    body.appendChild(tr)
  }
  table.appendChild(body)
  if (!placeBlock(table)) return
  const firstCell = table.querySelector('th')
  if (firstCell) caretAtEnd(firstCell)
  commit()
}

function insertDivider() {
  const divider = document.createElement('hr')
  if (!placeBlock(divider)) return
  caretAtEnd(divider.nextElementSibling as Node)
  commit()
}

/** 光标所在的某一类块（pre / blockquote / table…） */
function blockAtCaret(selector: string): HTMLElement | null {
  const el = surface.value
  const range = savedRange
  if (!el || !range) return null
  const node = range.startContainer
  const element = node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement
  const found = element?.closest(selector)
  return found && el.contains(found) ? (found as HTMLElement) : null
}

function codeBlockAtCaret(): HTMLElement | null {
  return blockAtCaret('pre')
}

/** 引用块和代码块一样：后面补一个空段落，光标才走得出去 */
function insertQuote() {
  if (rejectInsideCodeBlock()) return
  exec('formatBlock', 'BLOCKQUOTE')
  const quote = blockAtCaret('blockquote')
  if (quote) ensureTailParagraph(quote)
}

/** 代码块里 Ctrl+Enter / Esc 跳到代码块外面继续写 */
function leaveCodeBlock() {
  const pre = codeBlockAtCaret()
  if (!pre) return
  ensureTailParagraph(pre)
  caretAtEnd(pre.nextElementSibling as Node)
  commit()
}

/** 待办项里回车：接着生成下一条待办；已经在空项上回车就跳出列表 */
function taskEnter(event: KeyboardEvent) {
  const block = currentBlock()
  const item = block?.closest('li') as HTMLElement | null
  if (!item?.querySelector('input[type="checkbox"]')) return false
  event.preventDefault()
  if (!item.textContent?.trim()) {
    const paragraph = document.createElement('p')
    paragraph.appendChild(document.createElement('br'))
    const list = item.closest('ul, ol') as HTMLElement | null
    if (list && list.querySelectorAll('li').length > 1) {
      item.remove()
      list.after(paragraph)
    } else if (list) {
      list.replaceWith(paragraph)
    } else {
      item.replaceWith(paragraph)
    }
    caretAtEnd(paragraph)
  } else {
    const next = document.createElement('li')
    next.appendChild(makeCheckbox(false))
    next.appendChild(document.createTextNode(' '))
    const content = document.createElement('span')
    next.appendChild(content)
    item.after(next)
    caretAtEnd(content)
  }
  commit()
  return true
}

function stripLeadingMarker(block: HTMLElement, length: number) {
  const walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT)
  const first = walker.nextNode() as Text | null
  if (first?.nodeValue) first.nodeValue = first.nodeValue.slice(length)
  caretAtEnd(block)
}

/**
 * Markdown 输入习惯：行首敲 `# `、`- `、`1. `、`> `、``` ` 直接变成对应格式。
 * 返回 true 表示这次按键已经被接管。
 */
function applyMarkdownShortcut(): boolean {
  const block = currentBlock()
  if (!block || block.closest('pre')) return false
  const text = (block.textContent ?? '').trim()
  if (!text) return false

  if (text === '```') {
    stripLeadingMarker(block, text.length)
    insertCodeBlock()
    return true
  }
  if (/^#{1,3}$/.test(text)) {
    stripLeadingMarker(block, text.length)
    exec('formatBlock', `H${text.length}`)
    return true
  }
  if (/^[-*+]$/.test(text)) {
    stripLeadingMarker(block, text.length)
    toggleList('ul')
    return true
  }
  if (/^\d+\.$/.test(text)) {
    stripLeadingMarker(block, text.length)
    toggleList('ol')
    return true
  }
  if (text === '>') {
    stripLeadingMarker(block, text.length)
    insertQuote()
    return true
  }
  if (text === '[]') {
    stripLeadingMarker(block, text.length)
    toggleTodo()
    return true
  }
  return false
}

async function uploadFiles(files: File[], mode: 'image' | 'file' | 'auto') {
  const el = surface.value
  if (!el || !files.length) return
  uploading.value = true
  try {
    for (const file of files) {
      const asset = await fileApi.upload(file, props.memoId ?? undefined)
      const isImage = mode === 'image' || (mode === 'auto' && file.type.startsWith('image/'))
      const snippet = isImage
        ? `<img src="${escapeHtml(asset.url)}" alt="${escapeHtml(asset.originalName)}">`
        : `<a href="${escapeHtml(asset.url)}">${escapeHtml(asset.originalName)}</a>`
      exec('insertHTML', snippet)
      emit('uploaded', asset)
    }
    ElMessage.success('已插入')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function onFilePicked(event: Event, mode: 'image' | 'file') {
  const input = event.target as HTMLInputElement
  const files = [...(input.files ?? [])]
  input.value = ''
  void uploadFiles(files, mode)
}

function onPaste(event: ClipboardEvent) {
  const files = [...(event.clipboardData?.files ?? [])]
  if (!files.length) return
  event.preventDefault()
  void uploadFiles(files, 'auto')
}

function onDrop(event: DragEvent) {
  const files = [...(event.dataTransfer?.files ?? [])]
  if (!files.length) return
  event.preventDefault()
  void uploadFiles(files, 'auto')
}

function onInput() {
  const el = surface.value
  if (el) empty.value = isBlank(el)
  // 中文输入法拼字过程中不要打断，等 compositionend 再写回
  if (composing) return
  commit()
}

function onCompositionEnd() {
  composing = false
  commit()
}

function onCompositionStart() {
  composing = true
}

function onSurfaceChange() {
  // 勾选待办里的复选框不会触发 input
  commit()
}

function onKeydown(event: KeyboardEvent) {
  if (composing || event.isComposing) return
  // 代码块里 Ctrl+Enter / Esc：跳出代码块，接着写正文
  if (codeBlockAtCaret() && (event.key === 'Escape' || ((event.ctrlKey || event.metaKey) && event.key === 'Enter'))) {
    event.preventDefault()
    leaveCodeBlock()
    return
  }
  // 待办项里回车：继续下一条待办
  if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.metaKey && taskEnter(event)) return
  // 行首的 Markdown 简写（# 、- 、1. 、> 、```）自动转格式
  if (event.key === ' ' && !event.ctrlKey && !event.metaKey && !event.altKey && applyMarkdownShortcut()) {
    event.preventDefault()
    return
  }
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return
  const key = event.key.toLowerCase()
  if (key === 'b') {
    event.preventDefault()
    applyInline('bold')
  } else if (key === 'i') {
    event.preventDefault()
    applyInline('italic')
  } else if (key === 'e') {
    event.preventDefault()
    applyInlineCode()
  }
}

onMounted(() => {
  syncFromProps(props.modelValue)
  document.addEventListener('selectionchange', rememberSelection)
})

onBeforeUnmount(() => {
  document.removeEventListener('selectionchange', rememberSelection)
})

watch(
  () => props.modelValue,
  (next) => {
    if (next === lastEmitted) return
    syncFromProps(next)
  },
)
</script>

<template>
  <div class="editor">
    <div class="editor-toolbar" role="toolbar" aria-label="格式">
      <button type="button" title="撤销（Ctrl+Z）" aria-label="撤销" @mousedown.prevent @click="exec('undo')">
        <el-icon :size="15"><RefreshLeft /></el-icon>
      </button>
      <button type="button" title="重做（Ctrl+Shift+Z）" aria-label="重做" @mousedown.prevent @click="exec('redo')">
        <el-icon :size="15"><RefreshRight /></el-icon>
      </button>
      <span class="toolbar-divider" aria-hidden="true" />

      <button type="button" class="tb-bold" title="加粗（Ctrl+B）" aria-label="加粗" @mousedown.prevent @click="applyInline('bold')">B</button>
      <button type="button" class="tb-italic" title="斜体（Ctrl+I）" aria-label="斜体" @mousedown.prevent @click="applyInline('italic')">I</button>
      <button type="button" class="tb-strike" title="删除线" aria-label="删除线" @mousedown.prevent @click="applyInline('strikeThrough')">S</button>
      <span class="toolbar-divider" aria-hidden="true" />

      <button type="button" title="一级标题" aria-label="一级标题" @mousedown.prevent @click="exec('formatBlock', 'H1')">H1</button>
      <button type="button" title="二级标题" aria-label="二级标题" @mousedown.prevent @click="exec('formatBlock', 'H2')">H2</button>
      <button type="button" title="三级标题" aria-label="三级标题" @mousedown.prevent @click="exec('formatBlock', 'H3')">H3</button>
      <button type="button" title="正文" aria-label="正文" @mousedown.prevent @click="exec('formatBlock', 'P')">正文</button>
      <span class="toolbar-divider" aria-hidden="true" />

      <button type="button" title="无序列表" aria-label="无序列表" @mousedown.prevent @click="toggleList('ul')">
        <el-icon :size="15"><ListIcon /></el-icon>
      </button>
      <button type="button" title="有序列表" aria-label="有序列表" @mousedown.prevent @click="toggleList('ol')">
        <span class="tb-text">1.</span>
      </button>
      <button type="button" title="待办事项" aria-label="待办事项" @mousedown.prevent @click="toggleTodo">
        <el-icon :size="15"><Finished /></el-icon>
      </button>
      <button type="button" title="引用" aria-label="引用" @mousedown.prevent @click="insertQuote">
        <el-icon :size="15"><ChatLineSquare /></el-icon>
      </button>
      <span class="toolbar-divider" aria-hidden="true" />

      <button type="button" class="tb-code" title="行内代码（Ctrl+E）" aria-label="行内代码" @mousedown.prevent @click="applyInlineCode">{}</button>
      <button type="button" class="tb-code" title="代码块" aria-label="代码块" @mousedown.prevent @click="insertCodeBlock">```</button>
      <button type="button" title="链接" aria-label="链接" @mousedown.prevent @click="applyLink">
        <el-icon :size="15"><LinkIcon /></el-icon>
      </button>
      <button type="button" title="插入表格" aria-label="插入表格" @mousedown.prevent @click="insertTable">
        <el-icon :size="15"><Grid /></el-icon>
      </button>
      <button type="button" title="分割线" aria-label="分割线" @mousedown.prevent @click="insertDivider">
        <el-icon :size="15"><Minus /></el-icon>
      </button>
      <span class="toolbar-divider" aria-hidden="true" />

      <button type="button" title="插入图片" aria-label="插入图片" :disabled="uploading" @mousedown.prevent @click="imageInput?.click()">
        <el-icon :size="15"><Picture /></el-icon>
      </button>
      <button type="button" title="上传文件" aria-label="上传文件" :disabled="uploading" @mousedown.prevent @click="fileInput?.click()">
        <el-icon :size="15"><Paperclip /></el-icon>
      </button>
      <span class="toolbar-divider" aria-hidden="true" />

      <el-select
        :model-value="''"
        class="font-size-select"
        size="small"
        placeholder="字号"
        aria-label="字体大小"
        @change="applyFontSize"
      >
        <el-option v-for="size in fontSizes" :key="size" :label="`${size}px`" :value="size" />
      </el-select>

      <el-popover v-model:visible="colorPopover" placement="bottom-start" :width="190" trigger="click">
        <template #reference>
          <button type="button" title="字体颜色" aria-label="字体颜色" @mousedown.prevent>
            <span class="tb-color" :style="{ borderBottomColor: customColor }">A</span>
          </button>
        </template>
        <div class="color-palette">
          <button
            v-for="color in colors"
            :key="color"
            type="button"
            class="color-swatch"
            :style="{ backgroundColor: color }"
            :title="color"
            :aria-label="`使用颜色 ${color}`"
            @click="applyColor(color)"
          />
          <label class="custom-color" title="自定义颜色">
            <input v-model="customColor" type="color" aria-label="自定义颜色" @change="applyColor(customColor)" />
            <span>自定义</span>
          </label>
        </div>
      </el-popover>

      <button type="button" title="清除选中文字的格式" aria-label="清除格式" @mousedown.prevent @click="exec('removeFormat')">
        <el-icon :size="15"><ClearIcon /></el-icon>
      </button>

      <input ref="imageInput" type="file" accept="image/*" hidden @change="onFilePicked($event, 'image')" />
      <input ref="fileInput" type="file" hidden @change="onFilePicked($event, 'file')" />
    </div>

    <div class="editor-body">
      <div
        ref="surface"
        class="markdown-body editor-surface"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        aria-label="正文"
        spellcheck="false"
        @input="onInput"
        @change="onSurfaceChange"
        @keydown="onKeydown"
        @paste="onPaste"
        @drop="onDrop"
        @dragover.prevent
        @compositionstart="onCompositionStart"
        @compositionend="onCompositionEnd"
      />
      <div v-if="empty" class="editor-placeholder" aria-hidden="true">随手写点什么，支持加粗、标题、列表、图片…</div>
    </div>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  min-height: 0;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}

.editor-toolbar button {
  min-width: 30px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.editor-toolbar button:hover {
  background: var(--panel-hover);
  color: var(--text);
}

.editor-toolbar button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.editor-toolbar .tb-bold {
  font-weight: 800;
}

.editor-toolbar .tb-italic {
  font-style: italic;
}

.editor-toolbar .tb-strike {
  text-decoration: line-through;
}

.editor-toolbar .tb-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.editor-toolbar .tb-text {
  font-size: 12px;
  font-weight: 600;
}

.tb-color {
  border-bottom: 3px solid var(--brand-fill);
  font-weight: 700;
  line-height: 1;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  margin: 0 3px;
  background: var(--border);
}

.font-size-select {
  width: 84px;
}

.font-size-select :deep(.el-select__wrapper) {
  min-height: 30px;
  border-radius: 8px;
}

.color-palette {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.color-swatch {
  width: 30px;
  height: 30px;
  padding: 0;
  border: 2px solid var(--panel);
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--border-strong);
  cursor: pointer;
}

.color-swatch:hover {
  transform: scale(1.08);
}

.custom-color {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-2);
  font-size: 12.5px;
  cursor: pointer;
}

.custom-color input {
  width: 34px;
  height: 30px;
  padding: 2px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--panel);
  cursor: pointer;
}

.editor-body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-surface {
  flex: 1;
  min-height: 240px;
  padding: 18px 22px 40vh;
  outline: none;
  overflow-y: auto;
  overflow-wrap: break-word;
  caret-color: var(--brand);
}

.editor-surface:focus-visible {
  outline: none;
}

/* 待办事项里的复选框：跟着正文行高对齐 */
.editor-surface :deep(input[type='checkbox']) {
  width: 15px;
  height: 15px;
  margin: 0 6px 0 0;
  vertical-align: -2px;
  accent-color: var(--brand-fill);
}

/* 表格单元格：点击落点更稳一点 */
.editor-surface :deep(th),
.editor-surface :deep(td) {
  min-width: 72px;
  min-height: 30px;
}

.editor-placeholder {
  position: absolute;
  top: 18px;
  left: 22px;
  color: var(--text-3);
  pointer-events: none;
}
</style>
