<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const props = defineProps<{ content?: string | null }>()

const html = computed(() => {
  const source = props.content ?? ''
  if (!source.trim()) {
    return ''
  }
  const rendered = marked.parse(source, { gfm: true, breaks: true }) as string
  return DOMPurify.sanitize(rendered, { ADD_ATTR: ['target', 'controls', 'preload'] })
})
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>

<style>
.markdown-body {
  font-size: 15px;
  line-height: 1.75;
  color: var(--text);
  word-break: break-word;
}

.markdown-body > *:first-child {
  margin-top: 0;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 1.4em 0 0.6em;
  line-height: 1.35;
  font-weight: 700;
}

.markdown-body h1 {
  font-size: 1.7em;
}

.markdown-body h2 {
  font-size: 1.4em;
}

.markdown-body h3 {
  font-size: 1.18em;
}

.markdown-body p {
  margin: 0.7em 0;
}

.markdown-body a {
  color: var(--brand);
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 1.5em;
  margin: 0.6em 0;
}

.markdown-body li {
  margin: 0.25em 0;
}

.markdown-body blockquote {
  margin: 0.8em 0;
  padding: 2px 14px;
  border-left: 3px solid var(--brand);
  background: var(--panel-2);
  border-radius: 0 8px 8px 0;
  color: var(--text-2);
}

.markdown-body code {
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--panel-hover);
  font-family: 'JetBrains Mono', Consolas, Monaco, monospace;
  font-size: 0.88em;
}

.markdown-body pre {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--panel-hover);
  border: 1px solid var(--border);
  overflow-x: auto;
}

.markdown-body pre code {
  padding: 0;
  background: transparent;
}

.markdown-body img {
  max-width: 100%;
  border-radius: 12px;
  display: block;
  margin: 0.6em 0;
}

.markdown-body hr {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.4em 0;
}

.markdown-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 0.8em 0;
  font-size: 14px;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid var(--border);
  padding: 8px 10px;
  text-align: left;
}

.markdown-body th {
  background: var(--panel-2);
}

.markdown-body input[type='checkbox'] {
  margin-right: 6px;
}
</style>
