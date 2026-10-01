<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { messageApi } from '@/api'
import type { UserMessage } from '@/types'
import { formatFull } from '@/utils/format'
import { useUnreadMessages } from '@/composables/useUnreadMessages'
import PageHeader from '@/components/PageHeader.vue'

const PAGE_SIZE = 20

const messages = ref<UserMessage[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const { set: setUnread } = useUnreadMessages()

const typeLabels: Record<string, string> = {
  NOTICE: '通知',
  FILE_BLOCKED: '文件屏蔽',
  MEMO_REMOVED: '笔记删除',
}

onMounted(async () => {
  await load(true)
  // 打开就算已读，角标随之消失
  const remaining = await messageApi.markRead()
  setUnread(remaining)
  messages.value = messages.value.map((message) => ({
    ...message,
    readAt: message.readAt ?? new Date().toISOString(),
  }))
})

async function load(reset = false) {
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const result = await messageApi.list({ page: page.value, size: PAGE_SIZE })
    messages.value = reset ? result.records : [...messages.value, ...result.records]
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
  <div class="messages-page">
    <PageHeader title="站内信" back-to="/notes" back-label="返回笔记列表">
      <template #actions>
        <span class="muted tnum">共 {{ total }} 条</span>
      </template>
    </PageHeader>

    <div class="messages-body">
      <div class="messages-inner">
        <div v-if="loading" class="skeleton-wrap">
          <el-skeleton :rows="4" animated />
        </div>

        <div v-else-if="!messages.length" class="empty">
          <span class="empty-icon"><el-icon><Bell /></el-icon></span>
          <p>还没有收到站内信。</p>
        </div>

        <template v-else>
          <ul class="message-list">
            <li
              v-for="(message, index) in messages"
              :key="message.id"
              class="message-card stagger-item"
              :class="{ unread: !message.readAt }"
              :style="{ animationDelay: `${Math.min(index, 10) * 30}ms` }"
            >
              <header class="message-head">
                <h2>{{ message.title }}</h2>
                <el-tag size="small" type="info" effect="plain">
                  {{ typeLabels[message.type] ?? '通知' }}
                </el-tag>
                <span class="muted message-time tnum">{{ formatFull(message.createdAt) }}</span>
              </header>
              <p class="message-content">{{ message.content }}</p>
              <footer class="message-foot muted">来自 {{ message.senderName }}</footer>
            </li>
          </ul>

          <div class="load-more">
            <el-button v-if="messages.length < total" :loading="loadingMore" @click="loadMore">加载更多</el-button>
            <span v-else class="muted">没有更多了</span>
          </div>
        </template>

      </div>
    </div>
  </div>
</template>

<style scoped>
.messages-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.messages-body {
  flex: 1;
  overflow-y: auto;
}

.messages-inner {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-12);
}

.skeleton-wrap {
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.message-card {
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.message-card.unread {
  border-left: 3px solid var(--brand-fill);
}

.message-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.message-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}

.message-time {
  margin-left: auto;
  font-size: 12.5px;
}

.message-content {
  margin: var(--space-3) 0 0;
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.message-foot {
  margin-top: var(--space-3);
  font-size: 12.5px;
}

.load-more {
  display: grid;
  place-items: center;
  padding: var(--space-5) 0 0;
  font-size: 13px;
}

</style>
