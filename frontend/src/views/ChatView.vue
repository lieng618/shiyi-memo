<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { friendApi } from '@/api'
import type { ChatMessage, Friend } from '@/types'
import { formatFull } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import PageHeader from '@/components/PageHeader.vue'

/** 没有推送通道，跟站内信未读数一样靠轮询 */
const POLL_MS = 4000

const route = useRoute()
const friendId = computed(() => Number(route.params.id))

const friend = ref<Friend | null>(null)
const loading = ref(true)
const notFriend = ref(false)

const messages = ref<ChatMessage[]>([])
const sending = ref(false)
const draft = ref('')
const listRef = ref<HTMLElement | null>(null)

let pollTimer: ReturnType<typeof setInterval> | undefined
/** 已加载的最大消息 id，轮询只取比它新的 */
let lastId = 0

onMounted(async () => {
  await loadFriend()
  if (!notFriend.value) {
    await loadInitial()
    pollTimer = setInterval(() => void poll(), POLL_MS)
  }
  loading.value = false
})

onBeforeUnmount(() => clearInterval(pollTimer))

async function loadFriend() {
  try {
    const friends = await friendApi.list()
    friend.value = friends.find((item) => item.userId === friendId.value) ?? null
    notFriend.value = !friend.value
  } catch {
    notFriend.value = true
  }
}

async function loadInitial() {
  // 服务端按升序返回最近 50 条，一条没聊过就是空列表
  messages.value = await friendApi.chat(friendId.value, { limit: 50 })
  lastId = messages.value.length ? messages.value[messages.value.length - 1].id : 0
  await scrollToBottom()
}

async function poll() {
  try {
    const fresh = await friendApi.chat(friendId.value, { afterId: lastId })
    if (!fresh.length) return
    messages.value = [...messages.value, ...fresh]
    lastId = fresh[fresh.length - 1].id
    await scrollToBottom()
  } catch {
    /* 掉一次轮询不打紧 */
  }
}

async function scrollToBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

function onEnterKey(event: KeyboardEvent) {
  // 中文输入法选字回车不发送
  if (event.isComposing) return
  void send()
}

async function send() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    const message = await friendApi.sendChat(friendId.value, content)
    messages.value = [...messages.value, message]
    lastId = Math.max(lastId, message.id)
    draft.value = ''
    await scrollToBottom()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="chat-page">
    <PageHeader :title="friend?.nickname ?? '聊天'" back-to="/friends" back-label="返回好友列表">
      <template #actions>
        <UserAvatar v-if="friend" :user="friend" :size="30" />
      </template>
    </PageHeader>

    <div class="chat-body">
      <div v-if="loading" class="chat-state">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="notFriend" class="chat-state empty">
        <span class="empty-icon"><el-icon><ChatDotRound /></el-icon></span>
        <p>对方不是你的好友，去好友页重新加一下。</p>
      </div>

      <template v-else>
        <div ref="listRef" class="chat-scroll">
          <div class="chat-list">
            <div v-if="!messages.length" class="muted chat-hint">打个招呼吧。</div>
            <div
              v-for="message in messages"
              :key="message.id"
              class="chat-row"
              :class="{ mine: message.mine }"
            >
              <!-- 自己的消息用右侧占位对齐，不显示头像 -->
              <UserAvatar v-if="friend && !message.mine" :user="friend" :size="30" class="chat-avatar" />
              <div class="chat-bubble-wrap">
                <p class="chat-bubble">{{ message.content }}</p>
                <span class="muted chat-time tnum">{{ formatFull(message.createdAt) }}</span>
              </div>
              <span v-if="message.mine" class="chat-spacer" />
            </div>
          </div>
        </div>

        <footer class="chat-input-bar">
          <el-input
            v-model="draft"
            placeholder="输入消息，回车发送"
            maxlength="2000"
            @keyup.enter="onEnterKey"
          />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">
            发送
          </el-button>
        </footer>
      </template>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: var(--bg);
}

.chat-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-state {
  flex: 1;
  max-width: 680px;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4);
}

.chat-state.empty {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: var(--space-3);
  color: var(--text-2);
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--panel-2);
  color: var(--text-3);
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.chat-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 680px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4);
}

.chat-hint {
  text-align: center;
  font-size: 13px;
}

.chat-row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
}

.chat-row.mine {
  flex-direction: row-reverse;
}

.chat-avatar {
  flex: 0 0 auto;
  margin-bottom: 18px;
}

.chat-bubble-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  max-width: min(72%, 480px);
}

.chat-row.mine .chat-bubble-wrap {
  align-items: flex-end;
}

.chat-bubble {
  margin: 0;
  padding: 9px var(--space-3);
  border-radius: 14px;
  background: var(--panel);
  border: 1px solid var(--border);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.chat-row.mine .chat-bubble {
  background: var(--brand-soft);
  border-color: transparent;
  color: var(--text);
}

.chat-time {
  font-size: 11.5px;
}

/* 自己的消息右侧撑一格，保证 row-reverse 时气泡贴着右边 */
.chat-spacer {
  width: 30px;
  flex: 0 0 auto;
}

.chat-input-bar {
  display: flex;
  gap: var(--space-2);
  max-width: 680px;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-3) var(--space-4) var(--space-4);
  border-top: 1px solid var(--border);
  background: var(--bg);
}
</style>
