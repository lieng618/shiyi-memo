<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineSquare, Finished, Link, List, Paperclip, Picture } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { fileApi } from '@/api'
import type { FileAsset } from '@/types'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{ modelValue: string; memoId?: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [string]; uploaded: [FileAsset] }>()

const mode = ref<'edit' | 'preview' | 'split'>('split')
const media = window.matchMedia('(max-width: 900px)')
const narrow = ref(media.matches)
media.addEventListener('change', (event) => {
  narrow.value = event.matches
})
/** 手机上不做分栏：窄屏分栏等于编辑区 + 预览区上下堆叠，白占地方 */
const effectiveMode = computed(() => (narrow.value && mode.value === 'split' ? 'edit' : mode.value))
const textarea = ref<HTMLTextAreaElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const value = computed({
  get: () => props.modelValue,
  set: (next: string) => emit('update:modelValue', next),
})

function surround(before: string, after = before, placeholder = '') {
  const el = textarea.value
  if (!el) return
  const start = el.selectionStart
  const end = el.selectionEnd
  const selected = value.value.slice(start, end) || placeholder
  const next = value.value.slice(0, start) + before + selected + after + value.value.slice(end)
  value.value = next
  void nextTick(() => {
    el.focus()
    el.setSelectionRange(start + before.length, start + before.length + selected.length)
  })
}

function prefixLine(prefix: string) {
  const el = textarea.value
  if (!el) return
  const start = el.selectionStart
  const lineStart = value.value.lastIndexOf('\n', start - 1) + 1
  const next = value.value.slice(0, lineStart) + prefix + value.value.slice(lineStart)
  value.value = next
  void nextTick(() => {
    el.focus()
    el.setSelectionRange(start + prefix.length, start + prefix.length)
  })
}

function insert(text: string) {
  const el = textarea.value
  const position = el ? el.selectionEnd : value.value.length
  value.value = value.value.slice(0, position) + text + value.value.slice(position)
  void nextTick(() => el?.focus())
}

async function pickImage() {
  imageInput.value?.click()
}

async function pickFile() {
  fileInput.value?.click()
}

/**
 * 图片：插入图片语法直接显示；其它文件：插入链接，点了就能看 / 下载。
 */
async function onFilePicked(event: Event, mode: 'image' | 'file') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const asset = await fileApi.upload(file, props.memoId ?? undefined)
    insert(mode === 'image'
      ? `\n![${asset.originalName}](${asset.url})\n`
      : `\n[${asset.originalName}](${asset.url})\n`)
    emit('uploaded', asset)
    ElMessage.success(mode === 'image' ? '图片已插入' : '文件已插入')
  } finally {
    uploading.value = false
  }
}

interface ToolbarItem {
  key: string
  /** 纯文字标记（B / I / H / {}）；其余用 SVG 图标 */
  text?: string
  icon?: Component
  title: string
  run: () => void
}

const toolbar: ToolbarItem[] = [
  { key: 'bold', text: 'B', title: '加粗', run: () => surround('**', '**', '加粗文字') },
  { key: 'italic', text: 'I', title: '斜体', run: () => surround('*', '*', '斜体文字') },
  { key: 'heading', text: 'H', title: '二级标题', run: () => prefixLine('## ') },
  { key: 'list', icon: List, title: '无序列表', run: () => prefixLine('- ') },
  { key: 'todo', icon: Finished, title: '待办事项', run: () => prefixLine('- [ ] ') },
  { key: 'quote', icon: ChatLineSquare, title: '引用', run: () => prefixLine('> ') },
  { key: 'code', text: '{}', title: '行内代码', run: () => surround('`', '`', 'code') },
  { key: 'link', icon: Link, title: '链接', run: () => surround('[', '](https://)', '链接文字') },
]
</script>

<template>
  <div class="editor">
    <div class="editor-toolbar" role="toolbar" aria-label="Markdown 格式">
      <button
        v-for="item in toolbar"
        :key="item.key"
        type="button"
        :class="`tb-${item.key}`"
        :title="item.title"
        :aria-label="item.title"
        @click="item.run()"
      >
        <span v-if="item.text">{{ item.text }}</span>
        <el-icon v-else :size="15"><component :is="item.icon" /></el-icon>
      </button>
      <button type="button" title="插入图片" aria-label="插入图片" :disabled="uploading" @click="pickImage">
        <el-icon :size="15"><Picture /></el-icon>
      </button>
      <button type="button" title="上传文件" aria-label="上传文件" :disabled="uploading" @click="pickFile">
        <el-icon :size="15"><Paperclip /></el-icon>
      </button>
      <input ref="imageInput" type="file" accept="image/*" hidden @change="onFilePicked($event, 'image')" />
      <input ref="fileInput" type="file" hidden @change="onFilePicked($event, 'file')" />

      <div class="spacer" />

      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="edit">编辑</el-radio-button>
        <el-radio-button value="preview">预览</el-radio-button>
        <el-radio-button value="split" class="split-only">分栏</el-radio-button>
      </el-radio-group>
    </div>

    <div class="editor-body" :class="`mode-${effectiveMode}`">
      <textarea
        v-if="effectiveMode !== 'preview'"
        ref="textarea"
        v-model="value"
        class="editor-input"
        placeholder="随手写点什么，支持 Markdown：# 标题、- 列表、**加粗**、贴图直接上传…"
        spellcheck="false"
      />
      <div v-if="effectiveMode !== 'edit'" class="editor-preview">
        <MarkdownView v-if="value.trim()" :content="value" />
        <div v-else class="empty">还没有内容</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  /* 铺满父容器：父级是 flex 列布局时靠 flex:1，普通块级容器时靠 height:100% */
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

.editor-toolbar .tb-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.editor-toolbar .spacer {
  flex: 1;
}

.editor-body {
  flex: 1;
  min-height: 0;
  display: grid;
}

.editor-body.mode-edit {
  grid-template-columns: 1fr;
}

.editor-body.mode-preview {
  grid-template-columns: 1fr;
}

.editor-body.mode-split {
  grid-template-columns: 1fr 1fr;
}

.editor-input {
  width: 100%;
  height: 100%;
  min-height: 240px;
  padding: 16px 18px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text);
  font-family: 'JetBrains Mono', Consolas, Monaco, 'Microsoft YaHei', monospace;
  font-size: 14.5px;
  line-height: 1.75;
}

.editor-preview {
  padding: 16px 18px;
  overflow-y: auto;
  background: var(--panel-2);
}

.mode-split .editor-preview {
  border-left: 1px solid var(--border);
}

@media (max-width: 900px) {
  .editor-toolbar :deep(.split-only) {
    display: none;
  }

  .editor-body.mode-split {
    grid-template-columns: 1fr;
  }
}
</style>
