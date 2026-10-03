<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adminApi } from '../endpoints'
import type { AdminMemo, AdminMemoDetail } from '../types'
import { formatFull, formatSize, formatTime } from '@/utils/format'
import { useDebouncedValue } from '@/composables/useDebounce'
import MarkdownView from '@/components/MarkdownView.vue'

const PAGE_SIZE = 20

const memos = ref<AdminMemo[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const { input: keywordInput, debounced: keyword } = useDebouncedValue('', 340)

const detailOpen = ref(false)
const detail = ref<AdminMemoDetail | null>(null)
const detailLoading = ref(false)

onMounted(() => void load(true))
watch(keyword, () => void load(true))

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  }
  try {
    const result = await adminApi.memos({
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    memos.value = reset ? result.records : [...memos.value, ...result.records]
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function asMemo(row: unknown): AdminMemo {
  return row as AdminMemo
}

async function openDetail(memo: AdminMemo) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await adminApi.memoDetail(memo.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function removeMemo(memo: AdminMemo | AdminMemoDetail) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `删除「${memo.title || '无标题'}」？附件一并删除，并通知作者。`,
      '删除违规笔记',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        inputPlaceholder: '删除原因（会写进站内信，可留空）',
        inputValue: '',
        type: 'warning',
      },
    )
    reason = result.value ?? ''
  } catch {
    return
  }
  await adminApi.deleteMemo(memo.id, reason || undefined)
  ElMessage.success('已删除，并已通知作者')
  detailOpen.value = false
  await load(true)
}
</script>

<template>
  <div>
    <div class="adm-toolbar">
      <el-input v-model="keywordInput" placeholder="搜索标题或正文" clearable aria-label="搜索笔记" style="width: 260px">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <span class="muted tnum">共 {{ total }} 条笔记</span>
      <el-button @click="load(true)">刷新</el-button>
    </div>

    <div class="adm-card">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="memos" stripe>
          <el-table-column label="作者" min-width="180">
            <template #default="{ row }">
              <div>{{ row.userNickname }}</div>
              <div class="muted" style="font-size: 12px">{{ row.userEmail }}</div>
            </template>
          </el-table-column>
          <el-table-column label="标题 / 摘要" min-width="280">
            <template #default="{ row }">
              <div style="font-weight: 600">
                {{ row.title || '无标题' }}
                <el-tag v-if="row.pinned" size="small" effect="plain">置顶</el-tag>
              </div>
              <div class="muted" style="font-size: 12.5px">{{ row.excerpt }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="fileCount" label="附件" width="80" />
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              <span class="tnum">{{ formatTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openDetail(asMemo(row))">查看</el-button>
              <el-button size="small" type="danger" plain @click="removeMemo(asMemo(row))">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="adm-toolbar" style="justify-content: center; margin: var(--space-4) 0 0">
        <el-button v-if="memos.length < total" @click="page += 1; load()">加载更多</el-button>
        <span v-else class="muted">没有更多了</span>
      </div>
    </div>

    <el-dialog v-model="detailOpen" title="笔记内容" width="min(760px, 94vw)" align-center top="6vh">
      <div v-if="detailLoading" class="adm-loading"><el-skeleton :rows="5" animated /></div>
      <div v-else-if="detail">
        <div class="detail-meta muted tnum">
          {{ detail.userNickname }}（{{ detail.userEmail }}） · 创建于 {{ formatFull(detail.createdAt) }} ·
          更新于 {{ formatFull(detail.updatedAt) }}
        </div>
        <h3 class="detail-title">{{ detail.title || '无标题' }}</h3>
        <MarkdownView v-if="detail.contentHtml" :content="detail.contentHtml" />
        <p v-else class="muted">（没有正文）</p>

        <div v-if="detail.files.length" class="detail-files">
          <h4>附件（{{ detail.files.length }}）</h4>
          <ul>
            <li v-for="file in detail.files" :key="file.id">
              <span>{{ file.originalName }}</span>
              <span class="muted tnum">{{ formatSize(file.size) }}</span>
              <el-tag v-if="file.blocked" type="danger" size="small" effect="plain">已屏蔽</el-tag>
            </li>
          </ul>
        </div>

        <div style="margin-top: var(--space-5); display: flex; justify-content: flex-end">
          <el-button type="danger" plain @click="removeMemo(detail)">删除这条笔记</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-meta {
  font-size: 12.5px;
}

.detail-title {
  margin: var(--space-2) 0 var(--space-4);
  font-size: 18px;
  font-weight: 700;
}

.detail-files {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
}

.detail-files h4 {
  margin: 0 0 var(--space-3);
  font-size: 14px;
}

.detail-files ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail-files li {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 13.5px;
}
</style>
