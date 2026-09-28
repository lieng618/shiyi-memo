<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const props = defineProps<{ content?: string | null; publicToken?: string | null }>()

const html = computed(() => {
  const source = props.content ?? ''
  if (!source.trim()) {
    return ''
  }
  const rendered = marked.parse(source, { gfm: true, breaks: true }) as string
  const publicHtml = props.publicToken ? rewritePublicAssets(rendered, props.publicToken) : rendered
  return DOMPurify.sanitize(publicHtml, {
    ADD_ATTR: ['target', 'controls', 'preload', 'style'],
  })
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
