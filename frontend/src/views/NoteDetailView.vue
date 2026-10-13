<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, EditPen, Reading } from '@element-plus/icons-vue'
import { memoApi } from '@/api'
import type { MemoDetail } from '@/types'
import { formatFull } from '@/utils/format'
import { debounce } from '@/composables/useDebounce'
import { useNotesCache } from '@/composables/useNotesCache'
import { useTagsCache } from '@/composables/useTagsCache'
import { useNoteViewMode, type NoteViewMode } from '@/composables/useNoteViewMode'
import { exportNote, type ExportFormat } from '@/utils/noteExport'
import MarkdownView from '@/components/MarkdownView.vue'
import RichEditor, { exportTriggerStore } from '@/components/RichEditor.vue'

/** 外壳自动更新重启前会调这个，把防抖里还没发出去的编辑先落库 */
const flushBridge = globalThis as unknown as { __shiyiFlushEdits?: (() => Promise<void>) | null }

const route = useRoute()
const router = useRouter()
const notesCache = useNotesCache()
const { tags, load: loadTagsFromCache, invalidate: invalidateTags } = useTagsCache()

const noteId = computed(() => Number(route.params.id))
const detail = ref<MemoDetail | null>(null)
const loading = ref(true)
const title = ref('')
const contentHtml = ref('')
const tagNames = ref<string[]>([])
const saveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
const savedAt = ref<string | null>(null)
const titleRef = ref<HTMLInputElement | null>(null)
const editorRef = ref<InstanceType<typeof RichEditor> | null>(null)
const dirty = ref(false)
const exporting = ref(false)
/** 阅读 / 编辑：默认沿用上次选过的那个，习惯只看的人不用每篇都切一遍 */
const { mode, setMode } = useNoteViewMode()
/** 编辑器进过一次编辑态才创建，之后一直留着：来回切不丢撤销记录，也不用重建 */
const editorAlive = ref(mode.value === 'edit')

let applyingRemote = false
let changeVersion = 0
let savedVersion = 0
let saveQueue: Promise<void> = Promise.resolve()
/** 上一次和服务端一致的标签集合，用来判断保存后要不要重新拉标签 */
let syncedTagNames = ''

const tagOptions = computed(() => tags.value.map((tag) => tag.name))
/**
 * 正文为空时阅读态就是一片空白（新建的笔记正好是这种），给一句提示。
 * 判空不能只看 contentHtml 的长度：没写字的正文其实是 <p><br></p>；
 * 反过来只有图片 / 视频 / 表格的笔记不算空，所以先认这些标签。
 */
const showEmptyHint = computed(() => !hasBodyContent(contentHtml.value))
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

function tagColorOf(name: string) {
  return tags.value.find((tag) => tag.name === name)?.color ?? 'var(--brand)'
}

function hasBodyContent(html: string) {
  if (/<(img|video|audio|table|hr|iframe)\b/i.test(html)) {
    return true
  }
  return html.replace(/<[^>]*>/g, '').replace(/&nbsp;/gi, ' ').trim().length > 0
}

/** 切到阅读态先把光标从编辑器里挪走，切回编辑态再把它交还给编辑器 */
async function setViewMode(next: NoteViewMode) {
  if (next === mode.value) return
  if (next === 'read') {
    editorRef.value?.getEditor()?.blur()
  }
  setMode(next)
  if (next === 'edit') {
    editorAlive.value = true
    await nextTick()
    editorRef.value?.focus()
  }
}

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
  flushBridge.__shiyiFlushEdits = async () => {
    scheduleSave.cancel()
    if (dirty.value) await save()
  }
  // 刚从列表页点进来时详情还在内存里：先画出来，接口结果随后对齐
  const cached = notesCache.detailOf(noteId.value)
  if (cached) {
    loading.value = false
    await applyDetail(cached)
  }
  await Promise.all([loadDetail(), refreshTags()])
  window.addEventListener('keydown', onKeydown)
  await nextTick()
  if (mode.value === 'edit') {
    // 阅读态没有标题输入框；编辑态沿用原来的落点，进页面就能直接打字
    titleRef.value?.focus()
  }
})

onBeforeUnmount(() => {
  exportTriggerStore.__shiyiExportNote = null
  flushBridge.__shiyiFlushEdits = null
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
  if (mode.value !== 'edit') return
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    scheduleSave.cancel()
    void save()
  }
}

function refreshTags() {
  return loadTagsFromCache().catch(() => {
    /* 标签加载失败不影响正文编辑 */
  })
}

/** 把一份详情灌进编辑器；期间不触发自动保存 */
async function applyDetail(data: MemoDetail) {
  applyingRemote = true
  detail.value = data
  title.value = data.title
  contentHtml.value = data.contentHtml ?? ''
  tagNames.value = data.tags.map((tag) => tag.name)
  syncedTagNames = tagSignature(tagNames.value)
  changeVersion = 0
  savedVersion = 0
  dirty.value = false
  saveState.value = 'idle'
  await nextTick()
  applyingRemote = false
}

function tagSignature(names: string[]) {
  return [...names].sort().join('\u0000')
}

async function loadDetail() {
  try {
    const data = await memoApi.get(noteId.value)
    notesCache.rememberDetail(data)
    if (changeVersion === 0 && !dirty.value) {
      // 用户还没动过编辑器，才用服务端的版本覆盖（否则会把刚打的字冲掉）
      await applyDetail(data)
    } else {
      detail.value = data
    }
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
    notesCache.rememberDetail(updated)
    savedAt.value = formatFull(updated.updatedAt)
    saveState.value = 'saved'
    savedVersion = Math.max(savedVersion, version)
    dirty.value = savedVersion < changeVersion
    // 标签只有在真的增删过时才需要重新拉：否则每次自动保存都会多打一个聚合查询
    const savedTags = tagSignature(updated.tags.map((tag) => tag.name))
    if (savedTags !== syncedTagNames) {
      syncedTagNames = savedTags
      invalidateTags()
      void refreshTags()
    }
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
        v-if="mode === 'edit'"
        ref="titleRef"
        v-model="title"
        class="title-input"
        type="text"
        maxlength="255"
        aria-label="笔记标题"
        placeholder="无标题"
        :disabled="loading"
      />
      <h1 v-else class="title-text">{{ title || '无标题' }}</h1>

      <!-- 阅读 / 编辑切换：预览不挂编辑器，想改的时候再点回来 -->
      <div class="mode-switch" role="group" aria-label="查看方式">
        <button
          type="button"
          class="mode-btn"
          :class="{ on: mode === 'read' }"
          :aria-pressed="mode === 'read'"
          title="预览"
          @click="setViewMode('read')"
        >
          <el-icon :size="15"><Reading /></el-icon>
          <span class="mode-label">阅读</span>
        </button>
        <button
          type="button"
          class="mode-btn"
          :class="{ on: mode === 'edit' }"
          :aria-pressed="mode === 'edit'"
          title="编辑"
          @click="setViewMode('edit')"
        >
          <el-icon :size="15"><EditPen /></el-icon>
          <span class="mode-label">编辑</span>
        </button>
      </div>

      <span class="save-state muted" role="status" aria-live="polite">{{ saveLabel }}</span>
    </header>

    <div class="tag-row">
      <el-select
        v-if="mode === 'edit'"
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
      <div v-else class="tag-chips">
        <span v-for="name in tagNames" :key="name" class="tag-chip">
          <span class="tag-dot" :style="{ background: tagColorOf(name) }" aria-hidden="true" />{{ name }}
        </span>
        <span v-if="!tagNames.length" class="muted">没有标签</span>
      </div>

      <span v-if="detail" class="muted detail-time tnum">
        {{ mode === 'read' ? '更新于' : '创建于' }}
        {{ formatFull(mode === 'read' ? detail.updatedAt : detail.createdAt) }}
      </span>
    </div>

    <div class="editor-wrap">
      <el-skeleton v-if="loading" :rows="6" animated class="editor-skeleton" />
      <template v-else>
        <div v-show="mode === 'read'" class="read-view">
          <MarkdownView v-if="!showEmptyHint" :content="contentHtml" link-actions />
          <p v-else class="read-empty muted">还没有正文，点「编辑」写点什么。</p>
        </div>
        <RichEditor
          v-if="editorAlive"
          v-show="mode === 'edit'"
          ref="editorRef"
          v-model="contentHtml"
          :memo-id="noteId"
        />
      </template>
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

/* 阅读态的标题：和输入框占同样的位置、同样的行高，切换时头部不会跳一下 */
.title-text {
  flex: 1;
  min-width: 0;
  max-width: 720px;
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border: 1px solid transparent;
  overflow: hidden;
  color: var(--text);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode-switch {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--panel-2);
}

.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 11px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-3);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.mode-btn:hover {
  color: var(--text);
}

.mode-btn.on {
  background: var(--panel);
  color: var(--brand-hover);
  box-shadow: var(--shadow-sm);
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

.tag-chips {
  display: flex;
  flex: 1;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  min-width: 0;
  max-width: 560px;
  font-size: 13px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--panel-2);
  color: var(--text-2);
}

.tag-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
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

/* 阅读态正文：宽度对齐其它内容页（列表 880 / 站内信 760），行宽不至于拉满整屏 */
.read-view {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.read-view :deep(.markdown-body) {
  max-width: 820px;
  margin: 0 auto;
  padding: 18px 22px var(--space-12);
}

.read-empty {
  max-width: 820px;
  margin: 0 auto;
  padding: 18px 22px;
  font-size: 13.5px;
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

@media (max-width: 560px) {
  /* 窄屏只留图标，把宽度让给标题。这里不能用 display:none：
     文字一旦移出无障碍树，按钮就没有读屏名字了 */
  .mode-label {
    position: absolute;
    width: 1px;
    height: 1px;
    margin: -1px;
    padding: 0;
    overflow: hidden;
    clip-path: inset(50%);
    white-space: nowrap;
  }

  .mode-btn {
    padding: 0 9px;
  }
}
</style>
