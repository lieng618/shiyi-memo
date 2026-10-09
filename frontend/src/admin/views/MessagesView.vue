<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { adminApi } from '../endpoints'
import type { AdminMessage } from '../types'
import { formatFull } from '@/utils/format'
import SendMessageDialog from '../components/SendMessageDialog.vue'

const PAGE_SIZE = 20

const messages = ref<AdminMessage[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const composeOpen = ref(false)

const typeLabels: Record<string, string> = {
  NOTICE: '通知',
  FILE_BLOCKED: '文件屏蔽',
  MEMO_REMOVED: '笔记删除',
  WELCOME: '欢迎',
}

/** 定时信还没到点：标一下，免得看着像已经发出去了（表格插槽传的是行数据，这里只要时间字段） */
function isPending(row: Pick<AdminMessage, 'sendAt'>): boolean {
  return Boolean(row.sendAt) && dayjs(row.sendAt).isAfter(dayjs())
}

onMounted(() => void load(true))

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  }
  try {
    const result = await adminApi.messages({ page: page.value, size: PAGE_SIZE })
    messages.value = reset ? result.records : [...messages.value, ...result.records]
    total.value = result.total
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <div class="adm-toolbar">
      <span class="muted tnum">共发出 {{ total }} 条站内信</span>
      <div style="flex: 1" />
      <el-button type="primary" @click="composeOpen = true">
        <el-icon><Plus /></el-icon><span>发送站内信</span>
      </el-button>
    </div>

    <div class="adm-card">
      <div class="table-wrap">
        <el-table v-loading="loading" :data="messages" stripe>
          <el-table-column label="收件人" min-width="200">
            <template #default="{ row }">
              <div>{{ row.userNickname }}</div>
              <div class="muted" style="font-size: 12px">{{ row.userEmail }}</div>
            </template>
          </el-table-column>
          <el-table-column label="标题 / 内容" min-width="320">
            <template #default="{ row }">
              <div style="font-weight: 600">
                {{ row.title }}
                <el-tag size="small" effect="plain" type="info">{{ typeLabels[row.type] ?? row.type }}</el-tag>
              </div>
              <div class="muted" style="font-size: 12.5px; white-space: pre-wrap">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="是否已读" width="110">
            <template #default="{ row }">
              <el-tag :type="row.readAt ? 'success' : 'warning'" size="small" effect="plain">
                {{ row.readAt ? '已读' : '未读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发送时间" width="170">
            <template #default="{ row }">
              <div class="tnum">{{ formatFull(row.sendAt ?? row.createdAt) }}</div>
              <el-tag v-if="isPending(row)" type="warning" size="small" effect="plain">待送达</el-tag>
              <div v-else-if="row.sendAt" class="muted" style="font-size: 12px">定时信</div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="adm-toolbar" style="justify-content: center; margin: var(--space-4) 0 0">
        <el-button v-if="messages.length < total" @click="page += 1; load()">加载更多</el-button>
        <span v-else class="muted">没有更多了</span>
      </div>
    </div>

    <SendMessageDialog v-model="composeOpen" @sent="load(true)" />
  </div>
</template>
