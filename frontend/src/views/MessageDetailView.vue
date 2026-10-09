<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { fileApi, messageApi } from '@/api'
import type { FileAsset, UserMessage } from '@/types'
import { formatFull } from '@/utils/format'
import { sanitizeRichText } from '@/utils/richText'
import { hydrateMessageAttachments } from '@/utils/messageAttachments'
import { useImageLightbox } from '@/composables/useImageLightbox'
import { useUnreadMessages } from '@/composables/useUnreadMessages'
import FilePreview from '@/components/FilePreview.vue'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const messageId = computed(() => Number(route.params.id))

const message = ref<UserMessage | null>(null)
const loading = ref(true)
const notFound = ref(false)
const { set: setUnread } = useUnreadMessages()

const typeLabels: Record<string, string> = {
  NOTICE: '通知',
  FILE_BLOCKED: '文件屏蔽',
  MEMO_REMOVED: '笔记删除',
  WELCOME: '欢迎',
}

const previewOpen = ref(false)
const previewFile = ref<FileAsset | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const { viewerVisible, viewerUrls, viewerIndex, attach: attachLightbox } = useImageLightbox()

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await messageApi.detail(messageId.value)
    message.value = data
    // 进详情页才算已读；角标随之刷新
    if (!data.readAt) {
      const remaining = await messageApi.markRead([data.id])
      setUnread(remaining)
      message.value = { ...data, readAt: new Date().toISOString() }
    }
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

// 富文本正文是 v-html 插入的，Vue 不接管里面的 DOM，渲染后把附件链接换成卡片、图片挂上点开看大图
watch(message, () => nextTick(() => {
  hydrateMessageAttachments(bodyRef.value, openPreview)
  attachLightbox(bodyRef.value)
}), {
  deep: true,
  immediate: true,
})

function openPreview(file: FileAsset) {
  previewFile.value = file
  previewOpen.value = true
}
</script>

<template>
  <div class="message-detail-page">
    <PageHeader :title="message?.title || '站内信'" back-to="/messages" back-label="返回站内信" />

    <div class="detail-body">
      <div class="detail-inner">
        <div v-if="loading" class="skeleton-wrap">
          <el-skeleton :rows="5" animated />
        </div>

        <div v-else-if="notFound || !message" class="empty">
          <span class="empty-icon"><el-icon><Bell /></el-icon></span>
          <p>消息不存在或已删除。</p>
        </div>

        <template v-else>
          <header class="detail-head">
            <el-tag size="small" type="info" effect="plain">
              {{ typeLabels[message.type] ?? '通知' }}
            </el-tag>
            <span class="read-state" :class="{ unread: !message.readAt }">
              {{ message.readAt ? '已读' : '未读' }}
            </span>
            <span class="muted tnum">{{ formatFull(message.createdAt) }}</span>
          </header>

          <!-- 后台发的富文本通知（可含内嵌图片/附件卡片）；系统消息回落到纯文本 -->
          <div
            v-if="message.contentHtml"
            ref="bodyRef"
            class="detail-content markdown-body"
            v-html="sanitizeRichText(message.contentHtml)"
          />
          <p v-else class="detail-plain">{{ message.content }}</p>

          <footer class="detail-foot muted">来自 {{ message.senderName }}</footer>
        </template>
      </div>
    </div>

    <FilePreview v-model="previewOpen" :file="previewFile" />
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIndex"
      @close="viewerVisible = false"
    />
  </div>
</template>

<style scoped>
.message-detail-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.detail-body {
  flex: 1;
  overflow-y: auto;
}

.detail-inner {
  max-width: 760px;
  margin: 0 auto;
  padding: var(--space-2) var(--space-4) var(--space-12);
}

.skeleton-wrap {
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.empty {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-10) 0;
  color: var(--text-3);
}

.detail-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) 0;
}

.read-state {
  font-size: 12px;
  color: var(--text-3);
}

.read-state.unread {
  color: var(--brand-hover);
  font-weight: 600;
}

.detail-content {
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.detail-content :deep(img) {
  border-radius: var(--radius-sm);
  cursor: zoom-in;
}

/* 正文里的附件链接（后台插入的下载链接）渲染成附件卡片，样式同列表页 */
.detail-content :deep(.msg-attachment) {
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

.detail-content :deep(.msg-attachment:hover) {
  background: var(--panel-hover);
  color: var(--text);
}

.detail-content :deep(.msg-attachment .icon svg),
.detail-content :deep(.msg-attachment .action svg) {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}

.detail-content :deep(.msg-attachment .name) {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}

.detail-content :deep(.msg-attachment .size) {
  flex: 0 0 auto;
  color: var(--text-3);
  font-size: 12px;
}

.detail-content :deep(.msg-attachment.dead) {
  color: var(--text-3);
  cursor: not-allowed;
}

.detail-plain {
  margin: 0;
  padding: var(--space-4);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  color: var(--text);
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.detail-foot {
  padding: var(--space-3) var(--space-1);
  font-size: 12.5px;
}
</style>
