<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, DocumentCopy } from '@element-plus/icons-vue'
import { friendApi, memoApi } from '@/api'
import type { ChatMessage, Friend, MemoSummary } from '@/types'
import { useFriendSummary } from '@/composables/useFriendSummary'
import { onRealtime } from '@/composables/useRealtime'
import { useAuthStore } from '@/stores/auth'
import { loadSmoji, resolveSmoji, smojiMarker } from '@/utils/smoji'
import { memoShareMarker, resolveMemoShare } from '@/utils/memoShare'
import { formatFull } from '@/utils/format'
import EmojiPicker from '@/components/EmojiPicker.vue'
import MemoSharePicker from '@/components/MemoSharePicker.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import PageHeader from '@/components/PageHeader.vue'

/** SSE 负责实时推送，这里的慢轮询只作为连接掉线时的兜底。 */
const POLL_MS = 30_000

const route = useRoute()
const router = useRouter()
const friendId = computed(() => Number(route.params.id))
const { refresh: refreshFriendSummary } = useFriendSummary()
const auth = useAuthStore()

const friend = ref<Friend | null>(null)
const loading = ref(true)
const notFriend = ref(false)

const messages = ref<ChatMessage[]>([])
const sending = ref(false)
const draft = ref('')
const emojiOpen = ref(false)
const memoShareOpen = ref(false)
const listRef = ref<HTMLElement | null>(null)
const messageView = computed(() => messages.value.map((message) => ({
  message,
  emoji: resolveSmoji(message.content),
  memo: resolveMemoShare(message.content),
})))

let pollTimer: ReturnType<typeof setInterval> | undefined
let polling = false
let offChat: (() => void) | undefined
/** 已加载的最大消息 id，轮询只取比它新的 */
let lastId = 0

onMounted(async () => {
  void loadSmoji().catch(() => undefined)
  await loadFriend()
  if (!notFriend.value) {
    await loadInitial()
    pollTimer = setInterval(() => void poll(), POLL_MS)
    document.addEventListener('visibilitychange', onVisibilityChange)
    offChat = onRealtime('chat', (data) => {
      const message = data as ChatMessage
      if (message.senderId === friendId.value) {
        void poll()
      }
    })
  }
  loading.value = false
  await nextTick()
  if (!notFriend.value) {
    await scrollToBottom()
  }
})

onBeforeUnmount(() => {
  clearInterval(pollTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  offChat?.()
})

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
  await refreshFriendSummary()
}

async function poll() {
  if (document.hidden || polling) return
  polling = true
  try {
    const fresh = await friendApi.chat(friendId.value, { afterId: lastId })
    if (!fresh.length) return
    messages.value = [...messages.value, ...fresh]
    lastId = fresh[fresh.length - 1].id
    await scrollToBottom()
    await refreshFriendSummary()
  } catch {
    /* 掉一次轮询不打紧 */
  } finally {
    polling = false
  }
}

function onVisibilityChange() {
  if (!document.hidden) {
    void poll()
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
  await sendContent(content)
  draft.value = ''
}

async function sendEmoji(packId: string, itemId: string) {
  await sendContent(smojiMarker(packId, itemId))
}

async function shareMemo(note: MemoSummary) {
  try {
    let token = note.shareToken ?? ''
    if (!note.shareEnabled || !token) {
      await ElMessageBox.confirm(
        `「${note.title || '无标题'}」还没有开启分享，是否开启后发送给好友？`,
        '分享笔记',
        { confirmButtonText: '开启并发送', cancelButtonText: '取消', type: 'info' },
      )
      const result = await memoApi.share(note.id, true)
      token = result.token ?? ''
      note.shareEnabled = result.enabled
      note.shareToken = token
    }
    if (!token) {
      ElMessage.error('笔记分享链接生成失败')
      return
    }
    await sendContent(memoShareMarker({
      token,
      title: note.title || '无标题',
      excerpt: note.excerpt,
    }))
    memoShareOpen.value = false
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '分享失败')
  }
}

function openMemoShare(token: string) {
  void router.push(`/share/${token}`)
}

async function sendContent(content: string) {
  if (!content || sending.value) return
  sending.value = true
  try {
    const message = await friendApi.sendChat(friendId.value, content)
    messages.value = [...messages.value, message]
    lastId = Math.max(lastId, message.id)
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
    <PageHeader :title="friend?.nickname ?? '聊天'" back-to="/friends" back-label="返回好友列表" />

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
              v-for="entry in messageView"
              :key="entry.message.id"
              class="chat-row"
              :class="{ mine: entry.message.mine }"
            >
              <UserAvatar
                v-if="friend && !entry.message.mine"
                :user="friend"
                :size="30"
                class="chat-avatar"
              />
              <div class="chat-bubble-wrap">
                <button
                  v-if="entry.memo"
                  type="button"
                  class="chat-memo-card"
                  @click="openMemoShare(entry.memo.token)"
                >
                  <span class="memo-card-icon"><el-icon :size="18"><DocumentCopy /></el-icon></span>
                  <span class="memo-card-copy">
                    <strong>{{ entry.memo.title || '无标题' }}</strong>
                    <small>{{ entry.memo.excerpt || '点击查看笔记' }}</small>
                  </span>
                </button>
                <div v-else-if="entry.emoji" class="chat-emoji">
                  <img :src="entry.emoji.url" :alt="entry.emoji.label" loading="lazy" />
                </div>
                <p v-else class="chat-bubble">{{ entry.message.content }}</p>
                <span class="muted chat-time tnum">{{ formatFull(entry.message.createdAt) }}</span>
              </div>
              <UserAvatar
                v-if="auth.user && entry.message.mine"
                :user="auth.user"
                :size="30"
                class="chat-avatar"
              />
            </div>
          </div>
        </div>

        <footer class="chat-input-bar">
          <el-popover
            v-model:visible="memoShareOpen"
            trigger="click"
            placement="top-start"
            width="min(440px, calc(100vw - 28px))"
            :show-arrow="false"
            :popper-style="{ padding: '0', borderRadius: '10px', overflow: 'hidden' }"
          >
            <template #reference>
              <button type="button" class="share-note-trigger" title="分享笔记" aria-label="分享我的笔记">
                <el-icon :size="19"><DocumentCopy /></el-icon>
              </button>
            </template>
            <MemoSharePicker @select="shareMemo" @close="memoShareOpen = false" />
          </el-popover>
          <el-popover
            v-model:visible="emojiOpen"
            trigger="click"
            placement="top-start"
            width="min(520px, calc(100vw - 28px))"
            :show-arrow="false"
            :popper-style="{ padding: '0', borderRadius: '10px', overflow: 'hidden' }"
          >
            <template #reference>
              <button type="button" class="emoji-trigger" title="表情" aria-label="选择聊天表情">
                <span class="emoji-glyph" aria-hidden="true">☺</span>
              </button>
            </template>
            <EmojiPicker @select="sendEmoji" @close="emojiOpen = false" />
          </el-popover>
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
  justify-content: flex-end;
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

.chat-emoji {
  padding: 2px;
}

.chat-emoji img {
  display: block;
  width: 118px;
  height: 118px;
  object-fit: contain;
}

.chat-memo-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: min(300px, 72vw);
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--panel);
  color: var(--text);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur) var(--ease-out), background var(--dur) var(--ease-out);
}

.chat-memo-card:hover,
.chat-memo-card:focus-visible {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
}

.chat-row.mine .chat-memo-card {
  background: var(--brand-soft);
  border-color: transparent;
}

.memo-card-icon {
  display: grid;
  place-items: center;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--panel-2);
  color: var(--brand);
}

.memo-card-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.memo-card-copy strong,
.memo-card-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memo-card-copy strong {
  font-size: 13.5px;
}

.memo-card-copy small {
  color: var(--text-3);
  font-size: 12px;
}

.chat-row.mine .chat-bubble {
  background: var(--brand-soft);
  border-color: transparent;
  color: var(--text);
}

.chat-time {
  font-size: 11.5px;
}

.chat-input-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 680px;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-3) var(--space-4) var(--space-4);
  border-top: 1px solid var(--border);
  background: var(--bg);
}

.emoji-trigger,
.share-note-trigger {
  display: grid;
  place-items: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--panel);
  color: var(--text-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.emoji-trigger:hover,
.emoji-trigger:focus-visible,
.share-note-trigger:hover,
.share-note-trigger:focus-visible {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
  color: var(--brand-hover);
}

.emoji-glyph {
  font-size: 21px;
  font-weight: 700;
  line-height: 1;
}

@media (max-width: 640px) {
  .chat-emoji img {
    width: 96px;
    height: 96px;
  }
}
</style>
