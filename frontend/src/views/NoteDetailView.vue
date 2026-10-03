<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { memoApi, tagApi } from '@/api'
import type { MemoDetail, Tag } from '@/types'
import { formatFull } from '@/utils/format'
import { debounce } from '@/composables/useDebounce'
import { exportNote, type ExportFormat } from '@/utils/noteExport'
import RichEditor, { exportTriggerStore } from '@/components/RichEditor.vue'

const route = useRoute()
const router = useRouter()

const noteId = computed(() => Number(route.params.id))
const detail = ref<MemoDetail | null>(null)
const loading = ref(true)
const title = ref('')
const contentHtml = ref('')
const tagNames = ref<string[]>([])
const tags = ref<Tag[]>([])
const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const savedAt = ref<string | null>(null)
const titleRef = ref<HTMLInputElement | null>(null)
const dirty = ref(false)
const exporting = ref(false)

let applyingRemote = false
let changeVersion = 0
let savedVersion = 0
let saveQueue: Promise<void> = Promise.resolve()

const tagOptions = computed(() => tags.value.map((tag) => tag.name))
const saveLabel = computed(() => {
  switch (saveState.value) {
    case 'saving':
      return '保存中…'
    case 'saved':
      return savedAt.value ? `已保存 · ${savedAt.value}` : '已保存'
    case 'error':
      return '保存失败，稍后会重试'
    default:
      return ''
  }
})

const scheduleSave = debounce(() => void save(), 800)

watch([title, contentHtml], () => {
  if (detail.value && !applyingRemote) {
    changeVersion += 1
    dirty.value = true
    scheduleSave()
  }
})

onMounted(async () => {
  // 编辑器工具栏的「导出」分组菜单通过这个桥接调到本页的导出逻辑。
  // 必须在编辑器挂载（loading 结束后）之前就绪，否则工具栏会判定导出不可用。
  exportTriggerStore.__shiyiExportNote = onExport
  await Promise.all([loadDetail(), loadTags()])
  window.addEventListener('keydown', onKeydown)
  await nextTick()
  titleRef.value?.focus()
})

onBeforeUnmount(() => {
  exportTriggerStore.__shiyiExportNote = null
  window.removeEventListener('keydown', onKeydown)
  scheduleSave.cancel()
})

onBeforeRouteLeave(async () => {
  scheduleSave.cancel()
  if (dirty.value) {
    await save()
  }
})

function onKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    scheduleSave.cancel()
    void save()
  }
}

async function loadTags() {
  try {
    tags.value = await tagApi.list()
  } catch {
    /* 标签加载失败不影响正文编辑 */
  }
}

async function loadDetail() {
  loading.value = true
  try {
    const data = await memoApi.get(noteId.value)
    applyingRemote = true
    detail.value = data
    title.value = data.title
    contentHtml.value = data.contentHtml ?? ''
    tagNames.value = data.tags.map((tag) => tag.name)
    changeVersion = 0
    savedVersion = 0
    dirty.value = false
    saveState.value = 'idle'
    await nextTick()
    applyingRemote = false
  } catch {
    await router.replace('/notes')
  } finally {
    loading.value = false
  }
}

async function refreshDetail() {
  detail.value = await memoApi.get(noteId.value)
}

function save() {
  saveQueue = saveQueue.then(() => persist())
  return saveQueue
}

async function persist() {
  const target = detail.value
  if (!target) return
  const version = changeVersion
  saveState.value = 'saving'
  try {
    const updated = await memoApi.update(target.id, {
      title: title.value,
      contentHtml: contentHtml.value,
      tagNames: tagNames.value,
    })
    detail.value = updated
    savedAt.value = formatFull(updated.updatedAt)
    saveState.value = 'saved'
    savedVersion = Math.max(savedVersion, version)
    dirty.value = savedVersion < changeVersion
    void loadTags()
  } catch {
    saveState.value = 'error'
  }
}

function onTagsChanged() {
  changeVersion += 1
  dirty.value = true
  scheduleSave.cancel()
  void save()
}

/** 导出用的是编辑器里的最新内容，没保存的部分也在内 */
async function onExport(format: ExportFormat) {
  if (!detail.value || exporting.value) return
  exporting.value = true
  try {
    await exportNote(format, {
      title: title.value.trim() || detail.value.title,
      contentHtml: contentHtml.value,
    })
  } finally {
    exporting.value = false
  }
}

</script>

<template>
  <div class="note-page">
    <header class="note-header">
      <button type="button" class="icon-btn" aria-label="返回笔记列表" title="返回" @click="router.push('/notes')">
        <el-icon :size="18"><ArrowLeft /></el-icon>
      </button>

      <input
        ref="titleRef"
        v-model="title"
        class="title-input"
        type="text"
        maxlength="255"
        aria-label="笔记标题"
        placeholder="无标题"
        :disabled="loading"
      />

      <span class="save-state muted" role="status" aria-live="polite">{{ saveLabel }}</span>
    </header>

    <div class="tag-row">
      <el-select
        v-model="tagNames"
        multiple
        filterable
        allow-create
        default-first-option
        :reserve-keyword="false"
        placeholder="添加标签（输入新标签后回车）"
        aria-label="标签"
        class="tag-select"
        @change="onTagsChanged"
      >
        <el-option v-for="name in tagOptions" :key="name" :label="name" :value="name" />
      </el-select>
      <span v-if="detail" class="muted detail-time tnum">创建于 {{ formatFull(detail.createdAt) }}</span>
    </div>

    <div class="editor-wrap">
      <el-skeleton v-if="loading" :rows="6" animated class="editor-skeleton" />
      <RichEditor v-else v-model="contentHtml" :memo-id="noteId" />
    </div>
  </div>
</template>

<style scoped>
.note-page {
  display: flex;
  flex-direction: column;
  /* 固定整页高度：标题 / 标签 / 工具栏不动，只有正文区域内部滚动 */
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  background: var(--panel);
}

.note-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border);
  /* 桌面端无边框窗口：右侧给系统按钮让位，顶栏兼作拖动区 */
  padding-right: calc(var(--space-4) + var(--titlebar-inset));
  -webkit-app-region: drag;
}

.note-header :is(button, input) {
  -webkit-app-region: no-drag;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.icon-btn:hover {
  background: var(--panel-hover);
  color: var(--text);
}

.title-input {
  flex: 1;
  min-width: 0;
  max-width: 720px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  font-family: inherit;
  font-size: 18px;
  font-weight: 700;
}

.title-input:hover {
  background: var(--panel-2);
}

.title-input:focus {
  border-color: var(--border-strong);
  background: var(--panel);
  outline: none;
}

.save-state {
  flex: 0 0 auto;
  font-size: 12.5px;
}

.tag-row {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border);
}

.tag-select {
  flex: 1;
  max-width: 560px;
}

.detail-time {
  margin-left: auto;
  font-size: 12.5px;
}

.editor-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-skeleton {
  padding: var(--space-5);
}

@media (max-width: 768px) {
  .save-state {
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .detail-time {
    display: none;
  }
}
</style>
