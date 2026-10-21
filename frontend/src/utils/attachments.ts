/** 站内普通附件下载地址 */
export function fileDownloadUrl(fileId: number) {
  return `/api/files/${fileId}/download`
}

export const ATTACHMENT_ELEMENT_TYPE = 'shiyiAttachment'

export const ATTACHMENT_FILE_ICON =
  '<svg viewBox="0 0 1024 1024" aria-hidden="true"><path d="M832 384H576V128H192v768h640zm-26.496-64L640 154.496V320zM160 64h480l256 256v608a32 32 0 0 1-32 32H160a32 32 0 0 1-32-32V96a32 32 0 0 1 32-32m160 448h384v64H320zm0-192h160v64H320zm0 384h384v64H320z"/></svg>'

/**
 * 正文里的附件链接可能来自历史数据（/raw）或新数据（/download），
 * 公开分享页还会带 /api/public/memos/{token} 前缀；这些链接都应直接下载。
 */
export function isSiteAttachmentUrl(value: string) {
  let pathname = ''
  try {
    pathname = new URL(value, window.location.origin).pathname
  } catch {
    return false
  }
  return /^\/api\/(?:files\/\d+|public\/memos\/[^/]+\/files\/\d+)\/(?:raw|download)\/?$/.test(pathname)
}

export function attachmentName(anchor: HTMLAnchorElement) {
  return anchor.dataset.attachmentName
    || anchor.dataset.downloadName
    || anchor.closest<HTMLElement>('.shiyi-attachment-card')?.getAttribute('data-name')
    || anchor.getAttribute('data-name')
    || anchor.textContent?.trim()
    || '附件'
}

function createAttachmentCard(href: string, name: string) {
  const card = document.createElement('span')
  card.className = 'shiyi-attachment-card'
  card.dataset.attachmentName = name
  card.dataset.attachmentReady = '1'
  card.setAttribute('role', 'group')
  card.setAttribute('aria-label', `${name}，附件`)
  card.setAttribute('title', name)

  const icon = document.createElement('span')
  icon.className = 'shiyi-attachment-icon'
  icon.innerHTML = ATTACHMENT_FILE_ICON

  const label = document.createElement('span')
  label.className = 'shiyi-attachment-name'
  label.textContent = name

  const action = document.createElement('a')
  action.className = 'shiyi-attachment-action'
  action.href = href
  action.download = name
  action.dataset.downloadName = name
  action.title = `下载 ${name}`
  action.setAttribute('aria-label', `下载 ${name}`)
  action.textContent = '下载'

  card.append(icon, label, action)
  return card
}

/**
 * 历史数据只有普通 <a>。读取时把外层链接换成不可点击的卡片容器，
 * 只保留右侧下载按钮的链接行为；编辑态由自定义附件节点渲染同样的结构。
 */
export function enhanceAttachmentLinks(root: HTMLElement | null) {
  if (!root) return
  for (const anchor of Array.from(root.querySelectorAll<HTMLAnchorElement>('a[href]'))) {
    if (anchor.classList.contains('shiyi-attachment-action')) continue
    const href = anchor.getAttribute('href') ?? ''
    if (!isSiteAttachmentUrl(href) || anchor.dataset.attachmentReady) continue
    // 图片外链可能包在 <a> 里，不要把它误改成文件卡片。
    if (anchor.querySelector('img, video, audio')) continue

    const name = anchor.getAttribute('data-name')
      || anchor.dataset.attachmentName
      || anchor.textContent?.trim()
      || '附件'
    anchor.replaceWith(createAttachmentCard(href, name))
  }
}
