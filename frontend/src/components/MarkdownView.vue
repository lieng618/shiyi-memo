<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { sanitizeRichText } from '@/utils/richText'
import { useImageLightbox } from '@/composables/useImageLightbox'
import LinkActionBar from '@/components/LinkActionBar.vue'
import { attachmentName, enhanceAttachmentLinks, isSiteAttachmentUrl } from '@/utils/attachments'
import { downloadUrl } from '@/utils/download'

const props = defineProps<{
  content?: string | null
  publicToken?: string | null
  /** 点链接时弹「复制链接 / 打开链接」快捷条（笔记阅读态用；分享页保持原样，点了直接跳） */
  linkActions?: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)
const { viewerVisible, viewerUrls, viewerIndex, attach } = useImageLightbox()

/** 快捷条的尺寸写死在 LinkActionBar 的样式里（88 × 42），这里按同一组值预留 */
const BAR_WIDTH = 88
const BAR_HEIGHT = 42
const BAR_GAP = 8

const bar = ref<{ url: string; top: number; left: number } | null>(null)
let barAnchor: HTMLAnchorElement | null = null

const html = computed(() => {
  const rendered = sanitizeRichText(props.content ?? '')
  return props.publicToken ? rewritePublicAssets(rendered, props.publicToken) : rendered
})

// v-html 里的 DOM Vue 不接管，渲染后给图片挂上点开看大图，并把文件链接整形成附件卡片
watch(html, () => nextTick(() => {
  attach(containerRef.value)
  enhanceAttachmentLinks(containerRef.value)
  containerRef.value?.querySelectorAll<'img'>('img').forEach((image) => {
    if (!image.hasAttribute('loading')) image.setAttribute('loading', 'lazy')
    if (!image.hasAttribute('decoding')) image.setAttribute('decoding', 'async')
  })
  containerRef.value?.querySelectorAll<HTMLMediaElement>('video, audio').forEach((media) => {
    if (!media.hasAttribute('preload')) media.setAttribute('preload', 'metadata')
  })
}), { immediate: true })
// 换了内容（比如切到另一篇笔记）就把旧条子收掉，别留在原来的位置
watch(() => props.content, () => closeBar())

/** 正文里的链接也是 v-html 插进来的，用事件委托找点中的那一个 */
function onContentClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement | null)?.closest?.('a[href]') as HTMLAnchorElement | null
  if (!anchor) {
    if (props.linkActions) closeBar()
    return
  }
  if (isSiteAttachmentUrl(anchor.href)) {
    // 附件直接下载。阅读态的通用链接快捷条不再插一层，避免“点了没反应”：
    // 旧入口用 window.open 打开 attachment 响应，容易被新窗口或弹窗限制吞掉。
    event.preventDefault()
    event.stopPropagation()
    closeBar()
    downloadUrl(anchor.href, attachmentName(anchor))
    ElMessage.success('已开始下载附件')
    return
  }
  if (!props.linkActions) return
  // 阅读时先别直接跳走：把编辑模式那两条操作摆出来，由用户自己选
  event.preventDefault()
  barAnchor = anchor
  showBar(anchor)
}

function showBar(anchor: HTMLAnchorElement) {
  const rect = anchor.getBoundingClientRect()
  const left = Math.min(
    Math.max(BAR_GAP, rect.left),
    Math.max(BAR_GAP, window.innerWidth - BAR_WIDTH - BAR_GAP),
  )
  // 默认贴在链接上方；顶上放不下就翻到下面
  const above = rect.top - BAR_HEIGHT - BAR_GAP
  const top = above >= BAR_GAP
    ? above
    : Math.min(rect.bottom + BAR_GAP, Math.max(BAR_GAP, window.innerHeight - BAR_HEIGHT - BAR_GAP))
  bar.value = { url: anchor.href, top, left }
}

function closeBar() {
  if (!bar.value) return
  bar.value = null
  // 收起来时把焦点还给刚才那个链接，键盘操作不会掉到 body 上
  barAnchor?.focus({ preventScroll: true })
  barAnchor = null
}

function rewritePublicAssets(html: string, token: string) {
  const documentFragment = new DOMParser().parseFromString(html, 'text/html')
  const selector = 'img[src], a[href], video[src], audio[src], source[src]'
  for (const element of documentFragment.querySelectorAll(selector)) {
    for (const attribute of ['src', 'href']) {
      const current = element.getAttribute(attribute)
      if (!current) continue
      const match = current.match(/^\/api\/files\/(\d+)\/(raw|thumb|download)(\?.*)?$/)
      if (match) {
        element.setAttribute(
          attribute,
          `/api/public/memos/${encodeURIComponent(token)}/files/${match[1]}/${match[2]}${match[3] ?? ''}`,
        )
      }
    }
  }
  return documentFragment.body.innerHTML
}
</script>

<template>
  <div ref="containerRef" class="markdown-body rich-view" v-html="html" @click="onContentClick" />
  <LinkActionBar v-if="bar" :url="bar.url" :top="bar.top" :left="bar.left" @close="closeBar" />
  <el-image-viewer
    v-if="viewerVisible"
    :url-list="viewerUrls"
    :initial-index="viewerIndex"
    @close="viewerVisible = false"
  />
</template>

<style scoped>
.rich-view :deep(img) {
  cursor: zoom-in;
}
</style>
