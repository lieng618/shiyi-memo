import { fileApi } from '@/api'
import type { FileAsset } from '@/types'
import { formatChatFileSize } from '@/utils/chatFile'

/** 正文里指向附件下载的链接：/api/files/{id}/download */
const ATTACH_HREF = /^\/api\/files\/(\d+)\/download\/?$/

// SVG 图标取自 @element-plus/icons-vue 的官方路径数据
const ICON_DOCUMENT =
  'M832 384H576V128H192v768h640zm-26.496-64L640 154.496V320zM160 64h480l256 256v608a32 32 0 0 1-32 32H160a32 32 0 0 1-32-32V96a32 32 0 0 1 32-32m160 448h384v64H320zm0-192h160v64H320zm0 384h384v64H320z'
const ICON_DOWNLOAD =
  'M160 832h704a32 32 0 1 1 0 64H160a32 32 0 1 1 0-64m384-253.696 236.288-236.352 45.248 45.248L508.8 704 192 387.2l45.248-45.248L480 584.704V128h64z'

/** 同一附件在列表和详情里都只拉一次元数据 */
const cache = new Map<number, FileAsset | null>()

/**
 * 站内信富文本正文是 v-html 插入的，Vue 不接管里面的 DOM。
 * 这里把容器内指向附件下载的链接替换成带元数据的附件卡片。
 * interactive=true（详情页）：点卡片回调 onPreview 打开预览弹窗；
 * interactive=false（列表页）：卡片仅展示，点击只拦住链接跳转、事件继续冒泡给外层卡片进详情。
 */
export async function hydrateMessageAttachments(
  root: HTMLElement | null,
  onPreview: ((file: FileAsset) => void) | null,
  options: { interactive?: boolean } = {},
) {
  const { interactive = true } = options
  if (!root) return
  for (const anchor of Array.from(root.querySelectorAll<HTMLAnchorElement>('a[href]'))) {
    const match = ATTACH_HREF.exec(anchor.getAttribute('href') ?? '')
    if (!match || anchor.dataset.hydrated) continue
    anchor.dataset.hydrated = '1'
    const id = Number(match[1])
    let asset = cache.get(id)
    if (asset === undefined) {
      try {
        asset = await fileApi.get(id)
      } catch {
        asset = null
      }
      cache.set(id, asset)
    }
    renderAttachmentCard(anchor, asset, interactive ? onPreview : null)
  }
}

function renderAttachmentCard(anchor: HTMLAnchorElement, asset: FileAsset | null, onPreview: ((file: FileAsset) => void) | null) {
  anchor.classList.add('msg-attachment')
  if (!asset) {
    anchor.classList.add('dead')
    anchor.textContent = '附件已失效或被屏蔽'
  } else {
    // 文件名用 textContent 写入，防止富文本注入
    anchor.innerHTML =
      `<span class="icon"><svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="${ICON_DOCUMENT}"></path></svg></span>` +
      `<span class="name"></span>` +
      `<span class="size">${formatChatFileSize(asset.size)}</span>` +
      `<span class="action"><svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="${ICON_DOWNLOAD}"></path></svg></span>`
    anchor.querySelector<HTMLElement>('.name')!.textContent = asset.originalName
  }
  // 链接的默认行为（跳下载）始终拦掉；详情页里再打开预览弹窗，
  // 列表页里不拦冒泡，让点击落在外层卡片上进入详情
  anchor.addEventListener('click', (event) => {
    event.preventDefault()
    if (asset && onPreview) {
      event.stopPropagation()
      onPreview(asset)
    }
  })
}
