<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Close, Document, Share } from '@element-plus/icons-vue'
import { memoApi } from '@/api'
import type { MemoSummary } from '@/types'

const emit = defineEmits<{
  select: [memo: MemoSummary]
  close: []
}>()

const PAGE_SIZE = 30
const notes = ref<MemoSummary[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const loadingMore = ref(false)

onMounted(() => void load(true))

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const result = await memoApi.list({ page: page.value, size: PAGE_SIZE })
    notes.value = reset ? result.records : [...notes.value, ...result.records]
    total.value = result.total
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  page.value += 1
  void load(false)
}
</script>

<template>
  <div class="memo-picker" role="dialog" aria-label="分享笔记">
    <header class="memo-picker-head">
      <div>
        <el-icon :size="16"><Document /></el-icon>
        <strong>分享笔记</strong>
      </div>
      <button type="button" class="close-btn" aria-label="关闭笔记列表" @click="emit('close')">
        <el-icon :size="16"><Close /></el-icon>
      </button>
    </header>

    <div v-if="loading" class="memo-state">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="!notes.length" class="memo-state">还没有可分享的笔记。</div>

    <div v-else class="memo-list">
      <button
        v-for="note in notes"
        :key="note.id"
        type="button"
        class="memo-item"
        @click="emit('select', note)"
      >
        <span class="memo-icon"><el-icon :size="17"><Document /></el-icon></span>
        <span class="memo-copy">
          <span class="memo-title">
            {{ note.title || '无标题' }}
            <el-icon v-if="note.shareEnabled" :size="12" class="shared-icon"><Share /></el-icon>
          </span>
          <span class="memo-excerpt">{{ note.excerpt || '暂无正文摘要' }}</span>
        </span>
      </button>

      <div v-if="notes.length < total" class="memo-more">
        <el-button size="small" :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.memo-picker {
  display: flex;
  flex-direction: column;
  width: min(440px, calc(100vw - 28px));
  height: min(430px, 68vh);
  min-height: 300px;
  color: var(--text);
}

.memo-picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 var(--space-2) 0 var(--space-4);
  border-bottom: 1px solid var(--border);
}

.memo-picker-head > div {
  display: flex;
  align-items: center;
  gap: 7px;
}

.memo-picker-head strong {
  font-size: 14px;
}

.close-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
}

.close-btn:hover {
  background: var(--panel-2);
  color: var(--text);
}

.memo-state {
  flex: 1;
  padding: var(--space-4);
  color: var(--text-3);
  font-size: 13px;
}

.memo-list {
  flex: 1;
  min-height: 0;
  padding: 6px;
  overflow-y: auto;
}

.memo-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  width: 100%;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.memo-item:hover,
.memo-item:focus-visible {
  border-color: var(--border);
  background: var(--panel-2);
}

.memo-icon {
  display: grid;
  place-items: center;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--brand);
}

.memo-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.memo-title {
  display: flex;
  align-items: center;
  gap: 5px;
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shared-icon {
  flex: 0 0 auto;
  color: var(--brand);
}

.memo-excerpt {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-3);
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.memo-more {
  display: grid;
  place-items: center;
  padding: var(--space-3);
}

@media (max-width: 640px) {
  .memo-picker {
    height: min(390px, 60vh);
  }
}
</style>
