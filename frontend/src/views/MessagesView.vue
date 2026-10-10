<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { messageApi } from '@/api'
import type { UserMessage } from '@/types'
import { formatFull } from '@/utils/format'
import { sanitizeRichText } from '@/utils/richText'
import { hydrateMessageAttachments } from '@/utils/messageAttachments'
import PageHeader from '@/components/PageHeader.vue'

const PAGE_SIZE = 20

const router = useRouter()
const messages = ref<UserMessage[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadingMore = ref(false)

const typeLabels: Record<string, string> = {
  NOTICE: '通知',
  FILE_BLOCKED: '文件屏蔽',
  MEMO_REMOVED: '笔记删除',
  WELCOME: '欢迎',
}

const listRef = ref<HTMLElement | null>(null)

onMounted(() => {
  void load()
  // 已读改为「点进详情才算」，列表页不再批量标记
})

// 列表里的富文本是 v-html 预览：附件链接换成展示用的卡片、不挂灯箱，
// 点图片/附件和点卡片一样都是进详情（水合工具只拦住链接默认跳转，放行冒泡）
watch(messages, () => nextTick(() => hydrateMessageAttachments(listRef.value, null, { interactive: false })), {
  deep: true,
  immediate: true,
})

function openMessage(id: number) {
  void router.push(`/messages/${id}`)
}

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
          <ul ref="listRef" class="message-list">
            <li
              v-for="(message, index) in messages"
              :key="message.id"
              class="message-card stagger-item"
              :style="{ animationDelay: `${Math.min(index, 10) * 30}ms` }"
              role="button"
              tabindex="0"
              @click="openMessage(message.id)"
              @keydown.enter="openMessage(message.id)"
            >
              <header class="message-head">
                <h2>{{ message.title }}</h2>
                <el-tag size="small" type="info" effect="plain">
                  {{ typeLabels[message.type] ?? '通知' }}
                </el-tag>
                <span class="read-state" :class="{ unread: !message.readAt }">
                  {{ message.readAt ? '已读' : '未读' }}
                </span>
                <span class="muted message-time tnum">{{ formatFull(message.createdAt) }}</span>
              </header>
              <!-- 列表里只做裁剪预览，点卡片进详情看全文大图 -->
              <div
                v-if="message.contentHtml"
                class="message-rich markdown-body"
                v-html="sanitizeRichText(message.contentHtml)"
              />
              <p v-else class="message-content">{{ message.content }}</p>
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
  /* 固定整页高度：顶栏不动，只有列表区自己滚 */
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  background: var(--bg);
}

.messages-body {
  flex: 1;
  min-height: 0;
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
  cursor: pointer;
  transition: border-color var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}

.message-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.message-card:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
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

.read-state {
  font-size: 12px;
  color: var(--text-3);
}

.read-state.unread {
  color: var(--brand-hover);
  font-weight: 600;
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
  overflow-wrap: anywhere;
  /* 纯文本预览最多三行 */
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 列表里的富文本预览：限高裁剪，全文和清晰大图在详情页 */
.message-rich {
  margin: var(--space-3) 0 0;
  max-height: 120px;
  overflow: hidden;
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.message-rich :deep(img) {
  border-radius: var(--radius-sm);
  /* 不设 cursor：跟随整张卡片的手型，点击进详情 */
}

/* 正文里的附件链接（后台插入的下载链接）渲染成附件卡片 */
.message-rich :deep(.msg-attachment) {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-2) 0;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel-2);
  color: var(--text-2);
  font-size: 13px;
  text-decoration: none;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.message-rich :deep(.msg-attachment:hover) {
  background: var(--panel-hover);
  color: var(--text);
}

.message-rich :deep(.msg-attachment .icon svg),
.message-rich :deep(.msg-attachment .action svg) {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}

.message-rich :deep(.msg-attachment .name) {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}

.message-rich :deep(.msg-attachment .size) {
  flex: 0 0 auto;
  color: var(--text-3);
  font-size: 12px;
}

.message-rich :deep(.msg-attachment.dead) {
  color: var(--text-3);
  cursor: not-allowed;
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
