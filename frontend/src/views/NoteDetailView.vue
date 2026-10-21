<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, EditPen, Reading } from '@element-plus/icons-vue'
import { memoApi } from '@/api'
import { ApiError } from '@/api/http'
import type { MemoDetail } from '@/types'
import { formatFull } from '@/utils/format'
import { debounce } from '@/composables/useDebounce'
import { useNotesCache } from '@/composables/useNotesCache'
import { useTagsCache } from '@/composables/useTagsCache'
import {
  forceNoteViewMode,
  useNoteViewMode,
  type NoteViewMode,
} from '@/composables/useNoteViewMode'
import { getEditSessionId } from '@/composables/useEditSession'
import { onRealtime } from '@/composables/useRealtime'
import { exportNote, type ExportFormat } from '@/utils/noteExport'
import MarkdownView from '@/components/MarkdownView.vue'
import RichEditor, { exportTriggerStore } from '@/components/RichEditor.vue'

/** 外壳自动更新重启前会调这个，把防抖里还没发出去的编辑先落库 */
const flushBridge = globalThis as unknown as { __shiyiFlushEdits?: (() => Promise<void>) | null }

interface MemoRealtimeEvent {
  event?: 'create' | 'update' | 'lease' | 'delete'
  memoId?: number
  version?: number | null
  sessionId?: string | null
}

const EDIT_LEASE_HEARTBEAT_MS = 20_000
const route = useRoute()
const router = useRouter()
const notesCache = useNotesCache()
const { tags, load: loadTagsFromCache, invalidate: invalidateTags } = useTagsCache()
const editSessionId = getEditSessionId()

const noteId = computed(() => Number(route.params.id))
const detail = ref<MemoDetail | null>(null)
const loading = ref(true)
const title = ref('')
const contentHtml = ref('')
const tagNames = ref<string[]>([])
const saveState = ref<'idle' | 'saving' | 'saved' | 'error' | 'conflict'>('idle')
const savedAt = ref<string | null>(null)
const titleRef = ref<HTMLInputElement | null>(null)
const editorRef = ref<InstanceType<typeof RichEditor> | null>(null)
const dirty = ref(false)
const exporting = ref(false)
const acquiring = ref(false)
const conflictVisible = ref(false)
const conflictLatest = ref<MemoDetail | null>(null)
const savingCopy = ref(false)
/** 阅读 / 编辑：默认沿用上次选过的那个，但最终还要由服务端租约决定。 */
const { mode, setMode } = useNoteViewMode()
/** 编辑器进过一次编辑态才创建，之后一直留着：来回切不丢撤销记录，也不用重建。 */
const editorAlive = ref(false)

let applyingRemote = false
let changeVersion = 0
let savedVersion = 0
let saveQueue: Promise<void> = Promise.resolve()
let syncedTagNames = ''
let leaseTimer: ReturnType<typeof setInterval> | undefined
let remoteRefreshTimer: ReturnType<typeof setTimeout> | undefined
let offMemo: (() => void) | undefined

const tagOptions = computed(() => tags.value.map((tag) => tag.name))
const editingActive = computed(() =>
  mode.value === 'edit' && Boolean(detail.value?.editLease.heldByMe))
const readOnlyLabel = computed(() => {
  const lease = detail.value?.editLease
  if (!lease || lease.heldByMe || !lease.holderLabel) return ''
  return `${lease.holderLabel} 正在编辑`
})
const saveLabel = computed(() => {
  if (saveState.value === 'conflict') return '版本冲突，等待处理'
  switch (saveState.value) {
    case 'saving':
      return '保存中…'
    case 'saved':
      return savedAt.value ? `已保存 · ${savedAt.value}` : '已保存'
    case 'error':
      return '保存失败，请重试'
    default:
      return ''
  }
})

function tagColorOf(name: string) {
  return tags.value.find((tag) => tag.name === name)?.color ?? 'var(--brand)'
}

function normalizeDetail(input: MemoDetail): MemoDetail {
  return {
    ...input,
    version: input.version ?? 0,
    editLease: input.editLease ?? { heldByMe: false },
  }
}

function stopLeaseHeartbeat() {
  if (leaseTimer !== undefined) {
    clearInterval(leaseTimer)
    leaseTimer = undefined
  }
}

function syncLeaseHeartbeat() {
  stopLeaseHeartbeat()
  if (mode.value !== 'edit' || !detail.value?.editLease.heldByMe || conflictVisible.value) return
  leaseTimer = setInterval(() => void renewLease(), EDIT_LEASE_HEARTBEAT_MS)
}

async function enterEditMode() {
  mode.value = 'edit'
  setMode('edit')
  editorAlive.value = true
  syncLeaseHeartbeat()
  await nextTick()
  editorRef.value?.focus()
}

async function leaveEditModeLocally() {
  stopLeaseHeartbeat()
  editorRef.value?.getEditor()?.blur()
  mode.value = 'read'
}

async function setViewMode(next: NoteViewMode) {
  if (next === mode.value || acquiring.value) return

  if (next === 'read') {
    scheduleSave.cancel()
    if (dirty.value && !conflictVisible.value) await save()
    if (conflictVisible.value || saveState.value === 'error') return
    await releaseLease()
    setMode('read')
    await leaveEditModeLocally()
    return
  }

  if (detail.value?.editLease.heldByMe) {
    await enterEditMode()
    return
  }

  acquiring.value = true
  try {
    const data = normalizeDetail(await memoApi.acquireEdit(noteId.value))
    notesCache.rememberDetail(data)
    await applyDetail(data)
    await enterEditMode()
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      ElMessage.warning(error.message)
      await refreshFromServer()
    } else {
      ElMessage.error('暂时无法取得编辑权，请稍后重试')
    }
  } finally {
    acquiring.value = false
  }
}

const scheduleSave = debounce(() => void save(), 800)

watch([title, contentHtml], () => {
  if (detail.value && !applyingRemote && mode.value === 'edit' && !conflictVisible.value) {
    changeVersion += 1
    dirty.value = true
    scheduleSave()
  }
})

onMounted(async () => {
  exportTriggerStore.__shiyiExportNote = onExport
  flushBridge.__shiyiFlushEdits = async () => {
    scheduleSave.cancel()
    if (dirty.value && !conflictVisible.value) await save()
  }
  offMemo = onRealtime('memo', onMemoEvent)

  // 刚从列表页点进来时详情还在内存里：先画出来，接口结果随后对齐。
  const cached = notesCache.detailOf(noteId.value)
  if (cached) {
    loading.value = false
    await applyDetail(normalizeDetail(cached))
  }
  await Promise.all([loadDetail(), refreshTags()])
  window.addEventListener('keydown', onKeydown)
  await nextTick()
  if (mode.value === 'edit' && detail.value?.editLease.heldByMe) {
    titleRef.value?.focus()
  }
})

onBeforeUnmount(() => {
  exportTriggerStore.__shiyiExportNote = null
  flushBridge.__shiyiFlushEdits = null
  window.removeEventListener('keydown', onKeydown)
  offMemo?.()
  stopLeaseHeartbeat()
  clearTimeout(remoteRefreshTimer)
  scheduleSave.cancel()
  void releaseLease()
})

onBeforeRouteLeave(async () => {
  scheduleSave.cancel()
  if (dirty.value && !conflictVisible.value) await save()
  if (conflictVisible.value || (dirty.value && saveState.value === 'error')) {
    ElMessage.warning(conflictVisible.value
      ? '请先加载最新版本或把当前草稿另存为新笔记'
      : '当前草稿尚未保存，请检查网络后重试')
    return false
  }
  await releaseLease()
  return true
})

function onKeydown(event: KeyboardEvent) {
  if (mode.value !== 'edit' || conflictVisible.value) return
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

/** 把一份服务端详情灌进编辑器；期间不触发自动保存。 */
async function applyDetail(data: MemoDetail) {
  applyingRemote = true
  detail.value = normalizeDetail(data)
  title.value = data.title
  contentHtml.value = data.contentHtml ?? ''
  tagNames.value = data.tags.map((tag) => tag.name)
  syncedTagNames = tagSignature(tagNames.value)
  changeVersion = 0
  savedVersion = 0
  dirty.value = false
  conflictVisible.value = false
  conflictLatest.value = null
  saveState.value = 'idle'
  if (editingActive.value) {
    editorAlive.value = true
  }
  await nextTick()
  applyingRemote = false
  syncLeaseHeartbeat()
}

function tagSignature(names: string[]) {
  return [...names].sort().join('\u0000')
}

async function loadDetail() {
  try {
    let data = normalizeDetail(await memoApi.get(noteId.value))
    let blockedByOther = false

    // 上一次选择编辑，只是表达“打开后想编辑”；真正能不能编辑由服务端租约决定。
    if (mode.value === 'edit' && !data.editLease.heldByMe) {
      try {
        data = normalizeDetail(await memoApi.acquireEdit(noteId.value))
      } catch (error) {
        blockedByOther = error instanceof ApiError && error.status === 409
        await leaveEditModeLocally()
        data = normalizeDetail(await memoApi.get(noteId.value))
      }
    }

    notesCache.rememberDetail(data)
    if (changeVersion === 0 && !dirty.value) {
      await applyDetail(data)
    } else {
      detail.value = data
    }
    if (mode.value !== 'edit' && data.editLease.heldByMe) {
      await releaseLease()
    }
    if (blockedByOther) {
      ElMessage.info(data.editLease.holderLabel
        ? `${data.editLease.holderLabel} 正在编辑，当前以只读方式打开`
        : '当前笔记正在其他设备编辑，已切换为只读')
    }
  } catch {
    await router.replace('/notes')
  } finally {
    loading.value = false
  }
}

function save() {
  saveQueue = saveQueue.then(() => persist())
  return saveQueue
}

async function persist() {
  const target = detail.value
  if (!target || conflictVisible.value) return
  if (!target.editLease.heldByMe) {
    await openConflict('编辑权已失效，请重新取得编辑权')
    return
  }

  const localVersion = changeVersion
  saveState.value = 'saving'
  try {
    const updated = normalizeDetail(await memoApi.update(target.id, {
      title: title.value,
      contentHtml: contentHtml.value,
      tagNames: tagNames.value,
      expectedVersion: target.version,
    }))
    detail.value = updated
    notesCache.rememberDetail(updated)
    savedAt.value = formatFull(updated.updatedAt)
    saveState.value = 'saved'
    savedVersion = Math.max(savedVersion, localVersion)
    dirty.value = savedVersion < changeVersion
    const savedTags = tagSignature(updated.tags.map((tag) => tag.name))
    if (savedTags !== syncedTagNames) {
      syncedTagNames = savedTags
      invalidateTags()
      void refreshTags()
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      await openConflict(error.message)
    } else {
      saveState.value = 'error'
      ElMessage.error('保存失败，请检查网络后重试')
    }
  }
}

async function renewLease() {
  const target = detail.value
  if (!target || mode.value !== 'edit' || !target.editLease.heldByMe || conflictVisible.value) return
  try {
    const renewed = normalizeDetail(await memoApi.acquireEdit(target.id))
    if (detail.value?.id === renewed.id) {
      detail.value = {
        ...detail.value,
        version: renewed.version,
        editLease: renewed.editLease,
        files: renewed.files,
        updatedAt: renewed.updatedAt,
      }
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      await openConflict('编辑权已被其他设备接管')
    }
    // 网络抖动时保留当前租约，下一次心跳继续尝试；服务端到期前仍可保存。
  }
}

async function releaseLease() {
  const target = detail.value
  if (!target?.editLease.heldByMe) return
  stopLeaseHeartbeat()
  try {
    await memoApi.releaseEdit(target.id)
  } catch {
    // 页面已经离开时释放失败也无妨，服务端租约会自动过期。
  } finally {
    if (detail.value?.id === target.id) {
      detail.value = {
        ...detail.value,
        editLease: { heldByMe: false },
      }
    }
  }
}

async function refreshFromServer() {
  const data = normalizeDetail(await memoApi.get(noteId.value))
  notesCache.rememberDetail(data)
  if (changeVersion === 0 && !dirty.value) {
    await applyDetail(data)
  } else {
    detail.value = data
  }
  return data
}

async function openConflict(message: string) {
  scheduleSave.cancel()
  stopLeaseHeartbeat()
  saveState.value = 'conflict'
  conflictVisible.value = true
  await releaseLease()
  try {
    conflictLatest.value = normalizeDetail(await memoApi.get(noteId.value))
    notesCache.rememberDetail(conflictLatest.value)
  } catch {
    conflictLatest.value = null
  }
  ElMessage.warning(message)
}

async function loadLatestVersion() {
  try {
    const latest = conflictLatest.value ?? await refreshFromServer()
    await releaseLease()
    await applyDetail(latest)
    setMode('read')
    await leaveEditModeLocally()
  } catch {
    ElMessage.error('加载最新版本失败，请检查网络后重试')
  }
}

async function saveConflictCopy() {
  if (!detail.value || savingCopy.value) return
  savingCopy.value = true
  try {
    const copy = normalizeDetail(await memoApi.copyConflict(detail.value.id, {
      title: title.value,
      contentHtml: contentHtml.value,
      tagNames: tagNames.value,
      expectedVersion: detail.value.version,
    }))
    notesCache.rememberDetail(copy)
    conflictVisible.value = false
    conflictLatest.value = null
    dirty.value = false
    saveState.value = 'idle'
    await releaseLease()
    forceNoteViewMode('edit')
    await router.push(`/notes/${copy.id}`)
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '另存副本失败，请稍后重试')
  } finally {
    savingCopy.value = false
  }
}

function onMemoEvent(raw: unknown) {
  const event = raw as MemoRealtimeEvent
  if (!event || event.memoId !== noteId.value) return
  if (event.event === 'delete') {
    ElMessage.warning('这篇笔记已在其他设备删除')
    void router.replace('/notes')
    return
  }
  if (event.sessionId === editSessionId) return
  clearTimeout(remoteRefreshTimer)
  remoteRefreshTimer = setTimeout(() => void handleRemoteChange(), 120)
}

async function handleRemoteChange() {
  try {
    const data = normalizeDetail(await memoApi.get(noteId.value))
    notesCache.rememberDetail(data)

    if (mode.value !== 'edit') {
      if (dirty.value) {
        await openConflict('笔记已在其他设备更新')
      } else {
        await applyDetail(data)
      }
      return
    }

    if (!data.editLease.heldByMe) {
      if (dirty.value) {
        await openConflict('编辑权已被其他设备接管')
      } else {
        await applyDetail(data)
        setMode('read')
        await leaveEditModeLocally()
      }
      return
    }

    if (data.version !== detail.value?.version && dirty.value) {
      await openConflict('笔记已在其他设备更新')
      return
    }
    if (detail.value) {
      detail.value = {
        ...detail.value,
        version: data.version,
        editLease: data.editLease,
        files: data.files,
        updatedAt: data.updatedAt,
      }
    }
  } catch {
    // SSE 只负责提示；拉取失败时保留当前页面，下次事件或手动操作再同步。
  }
}

function onTagsChanged() {
  if (conflictVisible.value) return
  changeVersion += 1
  dirty.value = true
  scheduleSave.cancel()
  void save()
}

/** 导出用的是编辑器里的最新内容，没保存的部分也在内。 */
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
        v-if="editingActive"
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
          :disabled="acquiring"
          @click="setViewMode('edit')"
        >
          <el-icon :size="15"><EditPen /></el-icon>
          <span class="mode-label">编辑</span>
        </button>
      </div>

      <span class="save-state muted" role="status" aria-live="polite">
        {{ readOnlyLabel || saveLabel }}
      </span>
    </header>

    <div class="tag-row">
      <el-select
        v-if="editingActive"
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
        {{ editingActive ? '创建于' : '更新于' }}
        {{ formatFull(editingActive ? detail.createdAt : detail.updatedAt) }}
      </span>
    </div>

    <div class="editor-wrap">
      <el-skeleton v-if="loading" :rows="6" animated class="editor-skeleton" />
      <template v-else>
        <div v-show="!editingActive" class="read-view">
          <MarkdownView :content="contentHtml" link-actions />
        </div>
        <RichEditor
          v-if="editorAlive"
          v-show="editingActive"
          ref="editorRef"
          v-model="contentHtml"
          :memo-id="noteId"
        />
      </template>
    </div>

    <el-dialog
      v-model="conflictVisible"
      title="这篇笔记已在其他设备更新"
      width="min(520px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      append-to-body
    >
      <p class="conflict-copy">
        当前页面里的草稿仍在，尚未覆盖任何内容。请选择加载服务端的最新版本，或者把当前草稿另存为一条新笔记。
      </p>
      <template #footer>
        <el-button @click="loadLatestVersion">加载最新版本</el-button>
        <el-button type="primary" :loading="savingCopy" @click="saveConflictCopy">
          保存为新笔记
        </el-button>
      </template>
    </el-dialog>
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

.conflict-copy {
  margin: 0;
  color: var(--text-2);
  line-height: 1.7;
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
