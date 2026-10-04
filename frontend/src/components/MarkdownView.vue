<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { sanitizeRichText } from '@/utils/richText'
import { useImageLightbox } from '@/composables/useImageLightbox'

const props = defineProps<{ content?: string | null; publicToken?: string | null }>()

const containerRef = ref<HTMLElement | null>(null)
const { viewerVisible, viewerUrls, viewerIndex, attach } = useImageLightbox()

const html = computed(() => {
  const rendered = sanitizeRichText(props.content ?? '')
  return props.publicToken ? rewritePublicAssets(rendered, props.publicToken) : rendered
})

// v-html 里的 DOM Vue 不接管，渲染后给图片挂上点开看大图
watch(html, () => nextTick(() => attach(containerRef.value)), { immediate: true })

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
  <div ref="containerRef" class="markdown-body rich-view" v-html="html" />
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
