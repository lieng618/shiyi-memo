<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ content?: string | null; publicToken?: string | null }>()

const html = computed(() => {
  const rendered = renderMarkdown(props.content ?? '')
  return props.publicToken ? rewritePublicAssets(rendered, props.publicToken) : rendered
})

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
  <div class="markdown-body" v-html="html" />
</template>
