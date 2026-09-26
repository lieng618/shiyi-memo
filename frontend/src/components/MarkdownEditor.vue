<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Brush,
  ChatLineSquare,
  Finished,
  Link,
  List,
  MagicStick,
  Paperclip,
  Picture,
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'
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
const undoStack = ref<string[]>([])
const redoStack = ref<string[]>([])
const selectedFontSize = ref<number | ''>('')
const colorPopover = ref(false)
const customColor = ref('#d97706')
let lastValue = props.modelValue
let applyingHistory = false

const value = computed({
  get: () => props.modelValue,
  set: (next: string) => emit('update:modelValue', next),
})

const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)

watch(
  () => props.modelValue,
  (next) => {
    if (applyingHistory) {
      applyingHistory = false
      lastValue = next
      return
    }
    if (next === lastValue) return
    undoStack.value.push(lastValue)
    if (undoStack.value.length > 100) {
      undoStack.value.shift()
    }
    redoStack.value = []
    lastValue = next
  },
)

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

function restoreValue(next: string) {
  applyingHistory = true
  value.value = next
  lastValue = next
  void nextTick(() => {
    const el = textarea.value
    el?.focus()
    el?.setSelectionRange(next.length, next.length)
  })
}

function undo() {
  const next = undoStack.value.pop()
  if (next === undefined) return
  redoStack.value.push(value.value)
  restoreValue(next)
}

function redo() {
  const next = redoStack.value.pop()
  if (next === undefined) return
  undoStack.value.push(value.value)
  restoreValue(next)
}

function onEditorKeydown(event: KeyboardEvent) {
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return
  const key = event.key.toLowerCase()
  if (key === 'z') {
    event.preventDefault()
    if (event.shiftKey) {
      redo()
    } else {
      undo()
    }
  } else if (key === 'y') {
    event.preventDefault()
    redo()
  }
}

function applySelectedFontSize(size: number | string) {
  if (size === '') return
  surround(`<span style="font-size: ${Number(size)}px;">`, '</span>', '文字')
  selectedFontSize.value = ''
}

function applyColor(color: string) {
  surround(`<span style="color: ${color};">`, '</span>', '彩色文字')
  colorPopover.value = false
}

function formatDocument() {
  const formatted = formatMarkdown(value.value)
  if (formatted === value.value) {
    ElMessage.info('当前内容已经很整齐了')
    return
  }
  value.value = formatted
  void nextTick(() => textarea.value?.focus())
}

function formatMarkdown(source: string) {
  if (!source.trim()) return source
  const lines = source
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map((line) => line.replace(/[ \t]+$/, ''))
  const output: string[] = []
  let inFence = false

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    const trimmed = line.trim()
    if (/^\s*(```|~~~)/.test(line)) {
      if (!inFence && output.at(-1) && output.at(-1) !== '') output.push('')
      output.push(line)
      inFence = !inFence
      continue
    }
    if (inFence) {
      output.push(line)
      continue
    }
    if (!trimmed) {
      if (output.at(-1) !== undefined && output.at(-1) !== '') output.push('')
      continue
    }

    const currentList = /^(?:[-*+]\s|\d+[.)]\s)/.test(trimmed)
    const previous = output.at(-1) ?? ''
    const previousList = /^(?:[-*+]\s|\d+[.)]\s)/.test(previous)
    const startsBlock = /^(?:#{1,6}\s|>\s?|[-*+]\s|\d+[.)]\s)/.test(trimmed)
    if (startsBlock && previous && previous !== '' && !(currentList && previousList)) {
      output.push('')
    } else if (previousList && !currentList && previous !== '') {
      output.push('')
    }
    output.push(line)

    const next = lines[index + 1]?.trim()
    if (/^#{1,6}\s/.test(trimmed) && next) {
      output.push('')
    }
  }

  return output.join('\n').replace(/^\n+|\n+$/g, '') + '\n'
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

const fontSizes = [12, 14, 16, 18, 20, 24, 32]
const colors = ['#1c1917', '#57534e', '#b91c1c', '#d97706', '#15803d', '#2563eb', '#7c3aed', '#db2777']
</script>

<template>
  <div class="editor">
    <div class="editor-toolbar" role="toolbar" aria-label="Markdown 格式">
      <button type="button" title="撤销（Ctrl+Z）" aria-label="撤销" :disabled="!canUndo" @click="undo">
        <el-icon :size="15"><RefreshLeft /></el-icon>
      </button>
      <button type="button" title="重做（Ctrl+Shift+Z）" aria-label="重做" :disabled="!canRedo" @click="redo">
        <el-icon :size="15"><RefreshRight /></el-icon>
      </button>
      <span class="toolbar-divider" aria-hidden="true" />

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
      <span class="toolbar-divider" aria-hidden="true" />

      <el-select
        v-model="selectedFontSize"
        class="font-size-select"
        size="small"
        placeholder="字号"
        aria-label="字体大小"
        @change="applySelectedFontSize"
      >
        <el-option v-for="size in fontSizes" :key="size" :label="`${size}px`" :value="size" />
      </el-select>

      <el-popover v-model:visible="colorPopover" placement="bottom-start" :width="190" trigger="click">
        <template #reference>
          <button type="button" title="字体颜色" aria-label="字体颜色">
            <el-icon :size="15"><Brush /></el-icon>
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

      <button type="button" title="整理 Markdown 段落" aria-label="整理 Markdown 段落" @click="formatDocument">
        <el-icon :size="15"><MagicStick /></el-icon>
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
        @keydown="onEditorKeydown"
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
