<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../endpoints'
import type { AdminMemo, AdminStats } from '../types'
import { formatSize, formatTime } from '@/utils/format'

const emit = defineEmits<{ open: [key: 'users' | 'notes' | 'files' | 'messages' | 'levels'] }>()

const stats = ref<AdminStats | null>(null)
const loading = ref(true)
const recent = ref<AdminMemo[]>([])
const recentLoading = ref(true)

onMounted(async () => {
  try {
    stats.value = await adminApi.stats()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '统计数据加载失败')
  } finally {
    loading.value = false
  }
  try {
    const result = await adminApi.memos({ page: 1, size: 5 })
    recent.value = result.records
  } catch {
    /* 最近笔记拿不到不影响概览 */
  } finally {
    recentLoading.value = false
  }
})
</script>

<template>
  <div>
    <div v-if="loading" class="adm-loading"><el-skeleton :rows="2" animated style="max-width: 520px; width: 100%" /></div>

    <template v-else-if="stats">
      <div class="adm-stats">
        <button type="button" class="adm-stat" @click="emit('open', 'users')">
          <div class="label">用户总数</div>
          <div class="value">{{ stats.userCount }}</div>
        </button>
        <button type="button" class="adm-stat" @click="emit('open', 'notes')">
          <div class="label">笔记总数</div>
          <div class="value">{{ stats.memoCount }}</div>
        </button>
        <button type="button" class="adm-stat" @click="emit('open', 'files')">
          <div class="label">文件总数</div>
          <div class="value">{{ stats.fileCount }}</div>
        </button>
        <button type="button" class="adm-stat warn" @click="emit('open', 'files')">
          <div class="label">已屏蔽文件</div>
          <div class="value">{{ stats.blockedFileCount }}</div>
        </button>
        <div class="adm-stat">
          <div class="label">总占用空间</div>
          <div class="value">{{ formatSize(stats.storageUsed) }}</div>
        </div>
        <button type="button" class="adm-stat" @click="emit('open', 'messages')">
          <div class="label">站内信 / 未读</div>
          <div class="value">{{ stats.messageCount }} / {{ stats.unreadMessageCount }}</div>
        </button>
      </div>

      <section class="adm-card">
        <h2>最近笔记</h2>
        <div v-if="recentLoading" class="adm-loading"><el-skeleton :rows="2" animated /></div>
        <div v-else-if="!recent.length" class="muted">还没有任何笔记。</div>
        <div v-else class="table-wrap">
          <el-table :data="recent" stripe>
            <el-table-column prop="userEmail" label="作者" min-width="180" />
            <el-table-column prop="title" label="标题" min-width="200">
              <template #default="{ row }">{{ row.title || '无标题' }}</template>
            </el-table-column>
            <el-table-column prop="fileCount" label="附件" width="80" />
            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.adm-stat {
  text-align: left;
  font-family: inherit;
  cursor: pointer;
}

.adm-stat:hover {
  border-color: var(--border-strong);
}
</style>
