<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CopyDocument,
  Link,
  Notebook,
  Paperclip,
  Search,
  Share,
  Star,
  StarFilled,
} from '@element-plus/icons-vue'
import { memoApi, tagApi } from '@/api'
import type { MemoSummary, Tag } from '@/types'
import { formatTime } from '@/utils/format'
import { copyText } from '@/utils/clipboard'
import { useNotesFilter } from '@/composables/useNotesFilter'
import { useCreateNote } from '@/composables/useCreateNote'

const PAGE_SIZE = 20

const router = useRouter()
const createNote = useCreateNote()
const { keywordInput, keyword, activeTagIds, toggleTag, clear, hasFilter } = useNotesFilter()

const notes = ref<MemoSummary[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const tags = ref<Tag[]>([])
const shareDialog = ref(false)
const shareNote = ref<MemoSummary | null>(null)
const shareUrl = ref('')
const shareLoading = ref(false)
const shareEnabled = ref(false)
/** 一条笔记都没有时不显示搜索框（搜索无结果时仍保留，方便改关键词） */
const hasAnyNotes = ref(false)

const filtered = computed(() => hasFilter())
const hasMore = computed(() => notes.value.length < total.value)

watch([keyword, activeTagIds], () => void load(true), { deep: true })

onMounted(() => {
  void loadTags()
  void load(true)
})

async function loadTags() {
  try {
    tags.value = await tagApi.list()
  } catch {
    /* 标签只是辅助筛选 */
  }
}

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const result = await memoApi.list({
      keyword: keyword.value.trim() || undefined,
      tagIds: activeTagIds.value.length ? activeTagIds.value : undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    notes.value = reset ? result.records : [...notes.value, ...result.records]
    total.value = result.total
    if (!filtered.value) {
      hasAnyNotes.value = result.total > 0
    }
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function togglePin(note: MemoSummary) {
  const next = !note.pinned
  await memoApi.pin(note.id, next)
  ElMessage.success(next ? '已置顶' : '已取消置顶')
  await load(true)
}

async function openNote(id: number) {
  await router.push(`/notes/${id}`)
}

async function openShare(note: MemoSummary) {
  shareNote.value = note
  shareEnabled.value = note.shareEnabled
  shareUrl.value = note.shareEnabled && note.shareToken
    ? `${window.location.origin}/share/${note.shareToken}`
    : ''
  shareDialog.value = true
}

async function copyShareUrl() {
  try {
    await copyText(shareUrl.value)
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制链接')
  }
}

async function toggleShare(value: string | number | boolean) {
  const note = shareNote.value
  if (!note) return
  const enabled = Boolean(value)
  shareLoading.value = true
  try {
    const result = await memoApi.share(note.id, enabled)
    note.shareEnabled = result.enabled
    if (result.token) {
      note.shareToken = result.token
      shareUrl.value = `${window.location.origin}/share/${result.token}`
    }
    if (!result.enabled) {
      shareUrl.value = ''
    }
    shareEnabled.value = result.enabled
    ElMessage.success(result.enabled ? '分享已开启' : '分享已关闭')
  } catch {
    shareEnabled.value = !enabled
  } finally {
    shareLoading.value = false
  }
}
</script>

<template>
  <div class="notes-page">
    <div class="notes-inner">
      <div v-if="hasAnyNotes" class="search-row">
        <label class="visually-hidden" for="note-search">搜索笔记</label>
        <el-input id="note-search" v-model="keywordInput" size="large" placeholder="搜索标题或正文…" clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div v-if="tags.length || activeTagIds.length" class="filters" role="group" aria-label="按标签筛选">
        <button
          v-for="tag in tags"
          :key="tag.id"
          type="button"
          class="tag-chip"
          :class="{ active: activeTagIds.includes(tag.id) }"
          :aria-pressed="activeTagIds.includes(tag.id)"
          :style="{ '--chip-color': tag.color }"
          @click="toggleTag(tag.id)"
        >
          <span class="dot" aria-hidden="true" />
          <span>{{ tag.name }}</span>
          <span v-if="tag.memoCount" class="tnum count">{{ tag.memoCount }}</span>
        </button>
        <button v-if="filtered" type="button" class="clear-chip" @click="clear">清除筛选</button>
      </div>

      <div v-if="loading" class="skeleton-wrap">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="!notes.length" class="empty">
        <span class="empty-icon"><el-icon><Notebook /></el-icon></span>
        <template v-if="filtered">
          <p>没有匹配的笔记，换个关键词或清掉筛选试试。</p>
          <el-button @click="clear">清除筛选</el-button>
        </template>
        <template v-else>
          <p>还没有笔记，写下第一条吧。</p>
          <el-button type="primary" @click="createNote">新建笔记</el-button>
        </template>
      </div>

      <template v-else>
        <ul class="note-list">
          <li
            v-for="(note, index) in notes"
            :key="note.id"
            v-memo="[note.id, note.title, note.excerpt, note.pinned, note.shareEnabled, note.updatedAt]"
            class="stagger-item"
            :style="{ animationDelay: `${Math.min(index, 10) * 30}ms` }"
          >
            <article class="note-row" :class="{ pinned: note.pinned }">
              <button type="button" class="note-main" @click="openNote(note.id)">
                <img
                  v-if="note.coverUrl"
                  class="note-cover"
                  :src="note.coverUrl"
                  alt=""
                  loading="lazy"
                  width="104"
                  height="78"
                />
                <span class="note-text">
                  <span class="note-title">{{ note.title || '无标题' }}</span>
                  <span v-if="note.excerpt" class="note-excerpt">{{ note.excerpt }}</span>
                  <span class="note-meta meta-row">
                    <span>{{ formatTime(note.updatedAt) }}</span>
                    <span v-if="note.fileCount" class="meta-item">
                      <el-icon :size="13"><Paperclip /></el-icon>{{ note.fileCount }}
                    </span>
                    <span
                      v-for="tag in note.tags.slice(0, 4)"
                      :key="tag.id"
                      class="note-tag"
                      :style="{ '--chip-color': tag.color }"
                    >
                      <span class="dot" aria-hidden="true" />{{ tag.name }}
                    </span>
                  </span>
                </span>
              </button>

              <button
                type="button"
                class="row-action share-btn"
                :class="{ on: note.shareEnabled }"
                :aria-label="`分享 ${note.title || '无标题'}`"
                :title="note.shareEnabled ? '管理分享' : '分享'"
                :disabled="shareLoading"
                @click.stop="openShare(note)"
              >
                <el-icon><Link /></el-icon>
              </button>

              <button
                type="button"
                class="pin-btn"
                :class="{ on: note.pinned }"
                :aria-label="note.pinned ? `取消置顶 ${note.title || '无标题'}` : `置顶 ${note.title || '无标题'}`"
                :aria-pressed="note.pinned"
                @click.stop="togglePin(note)"
              >
                <el-icon><StarFilled v-if="note.pinned" /><Star v-else /></el-icon>
              </button>
            </article>
          </li>
        </ul>

        <div class="load-more">
          <el-button v-if="hasMore" :loading="loadingMore" @click="page += 1; load()">加载更多</el-button>
          <span v-else class="muted">没有更多了</span>
        </div>
      </template>
    </div>

    <el-dialog
      v-model="shareDialog"
      width="min(520px, 92vw)"
      align-center
      class="share-dialog-modal"
    >
      <template #header>
        <div class="share-dialog-heading">
          <span class="share-dialog-icon"><el-icon :size="19"><Share /></el-icon></span>
          <h2>分享</h2>
        </div>
      </template>

      <div class="share-dialog">
        <section class="share-link-panel">
          <div class="share-link-heading">
            <label for="share-link">分享链接</label>
            <el-switch
              :model-value="shareEnabled"
              :loading="shareLoading"
              active-color="var(--success)"
              inactive-color="var(--danger-fill)"
              aria-label="分享开启"
              @change="toggleShare"
            />
          </div>
          <div class="share-link-control">
            <el-input id="share-link" :model-value="shareUrl" readonly />
            <el-button type="primary" :disabled="!shareEnabled || !shareUrl" @click="copyShareUrl">
              <el-icon><CopyDocument /></el-icon>
              <span>复制链接</span>
            </el-button>
          </div>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.notes-page {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.notes-inner {
  max-width: 880px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-12);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.search-row {
  margin-bottom: var(--space-4);
}

.tag-chip,
.clear-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--panel);
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}

.tag-chip:hover {
  background: var(--panel-hover);
}

.tag-chip.active {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
  color: var(--brand-hover);
  font-weight: 600;
}

.tag-chip .dot,
.note-tag .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--chip-color, var(--brand));
}

.tag-chip .count {
  color: var(--text-3);
}

.clear-chip {
  border-style: dashed;
  background: transparent;
}

.skeleton-wrap {
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.list-count {
  margin: 0 0 var(--space-3);
  font-size: 12.5px;
}

.note-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.note-row {
  display: flex;
  align-items: stretch;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--panel);
  overflow: hidden;
  transition: border-color var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}

.note-row:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.note-row.pinned {
  border-left: 3px solid var(--brand-fill);
}

.note-main {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-3) var(--space-2) var(--space-3) var(--space-4);
  border: none;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.note-cover {
  flex: 0 0 auto;
  width: 104px;
  height: 78px;
  border-radius: var(--radius-sm);
  object-fit: cover;
  background: var(--panel-2);
}

.note-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.note-title {
  font-size: 15.5px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-excerpt {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--text-2);
  font-size: 13.5px;
  line-height: 1.55;
}

.note-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: 2px;
  color: var(--text-3);
  font-size: 12px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.note-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pin-btn,
.row-action {
  display: grid;
  place-items: center;
  width: 48px;
  border: none;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.pin-btn:hover,
.row-action:hover {
  background: var(--panel-hover);
  color: var(--text);
}

.pin-btn.on {
  color: var(--brand-hover);
}

.share-btn.on {
  color: var(--brand-hover);
}

.share-btn:disabled {
  opacity: 0.5;
  cursor: wait;
}

.share-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.share-dialog-heading {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.share-dialog-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 12px;
  background: var(--brand-soft);
  color: var(--brand);
}

.share-dialog-heading h2 {
  margin: 0;
}

.share-dialog-heading h2 {
  color: var(--text);
  font-size: 17px;
  font-weight: 700;
}

.share-link-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.share-link-heading {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
}

.share-link-heading label {
  color: var(--text-2);
  font-size: 13.5px;
  font-weight: 600;
}

.share-link-heading :deep(.el-switch__core) {
  border-color: var(--danger-fill);
  background: var(--danger-fill);
}

.share-link-heading :deep(.el-switch.is-checked .el-switch__core) {
  border-color: var(--success);
  background: var(--success);
}

.share-link-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
}

.share-link-control :deep(.el-input__wrapper) {
  min-height: 40px;
  border-radius: 9px;
  background: var(--panel-2);
  box-shadow: 0 0 0 1px var(--border) inset;
}

.share-link-control .el-button {
  min-height: 40px;
  border-radius: 9px;
}

.share-link-control .el-button span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}


.load-more {
  display: grid;
  place-items: center;
  padding: var(--space-6) 0;
  font-size: 13px;
}

@media (max-width: 560px) {
  .notes-inner {
    padding: var(--space-4) var(--space-3) var(--space-10);
  }

  .note-main {
    gap: var(--space-3);
    padding: var(--space-3);
  }

  .note-cover {
    width: 72px;
    height: 72px;
  }
}
</style>
