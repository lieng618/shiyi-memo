<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  DocumentCopy,
  Download,
  Headset,
  Loading,
  MoreFilled,
  Paperclip,
  Picture,
  Tickets,
  VideoCamera,
  WarningFilled,
} from '@element-plus/icons-vue'
import { fileApi, friendApi, memoApi } from '@/api'
import { ApiError } from '@/api/http'
import type { ChatMessage, FileAsset, Friend, MemoSummary } from '@/types'
import { useFriendSummary } from '@/composables/useFriendSummary'
import { onRealtime } from '@/composables/useRealtime'
import { useAuthStore } from '@/stores/auth'
import { loadSmoji, resolveSmoji, smojiMarker } from '@/utils/smoji'
import { memoShareMarker, resolveMemoShare } from '@/utils/memoShare'
import {
  chatFileDownloadUrl,
  chatFileMarker,
  chatFileRawUrl,
  chatFileThumbUrl,
  formatChatFileSize,
  resolveChatFile,
} from '@/utils/chatFile'
import type { ChatFileKind } from '@/utils/chatFile'
import { formatFull } from '@/utils/format'
import EmojiPicker from '@/components/EmojiPicker.vue'
import FilePreview from '@/components/FilePreview.vue'
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

/** 图片/文件发送：上传进度与两个隐藏的文件选择框 */
const imageInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const uploadPercent = ref(0)
/** 文件卡片点开的预览弹窗（复用个人中心的 FilePreview） */
const previewOpen = ref(false)
const previewFile = ref<FileAsset | null>(null)
/** 加载失败的图片（被删/被屏蔽），就地换成占位提示 */
const imageBroken = ref<Record<number, boolean>>({})
/** 外部文件拖进来的悬停层数，进出子元素会成对触发，归零才收起遮罩 */
const dragDepth = ref(0)

const searchOpen = ref(false)
const searchType = ref<'text' | 'memo'>('text')
const searchKeyword = ref('')
const searchResults = ref<ChatMessage[]>([])
const searching = ref(false)
const searchDone = ref(false)
const highlightId = ref<number | null>(null)

/** 连服务器都没碰到的发送失败（比如断网）：id -> 原因 */
const failed = ref<Record<number, string>>({})
const retrying = ref(false)
/** 对方不是我的好友了，给一个加好友的入口 */
const peerRemoved = ref(false)
const readding = ref(false)

const searchTitle = computed(() => (searchType.value === 'memo' ? '查找笔记分享' : '查找聊天内容'))
const searchView = computed(() => searchResults.value.map((message) => ({
  message,
  memo: resolveMemoShare(message.content),
})))

const messageView = computed(() => messages.value.map((message) => ({
  message,
  emoji: resolveSmoji(message.content),
  memo: resolveMemoShare(message.content),
  file: resolveChatFile(message.content),
})))

let pollTimer: ReturnType<typeof setInterval> | undefined
let highlightTimer: ReturnType<typeof setTimeout> | undefined
let polling = false
let offChat: (() => void) | undefined
/** 已加载的最大消息 id，轮询只取比它新的 */
let lastId = 0
/** 发送中消息的临时 id，负数就不会和真实消息撞 */
let pendingSeq = 0

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
  clearTimeout(highlightTimer)
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
  // 先把气泡放出去，草稿立刻清空；失败再给它标红叹号
  draft.value = ''
  await sendContent(content)
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

/* ---------------------------------------------------------------- 图片 / 文件 */

function fileKindIcon(kind: ChatFileKind): Component {
  if (kind === 'video') return VideoCamera
  if (kind === 'audio') return Headset
  if (kind === 'other') return Tickets
  return Picture
}

function pickImage() {
  if (uploading.value) return
  imageInput.value?.click()
}

function pickFile() {
  if (uploading.value) return
  fileInput.value?.click()
}

function onFileChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清掉 value，下次选同一个文件也能触发 change
  input.value = ''
  if (file) {
    void sendChatFile(file)
  }
}

/** 先传文件拿到 id，再把标记当作一条消息发出去，复用整套乐观气泡/失败重试 */
async function sendChatFile(file: File) {
  if (uploading.value || sending.value) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    const asset = await fileApi.upload(file, undefined, (percent) => {
      uploadPercent.value = percent
    })
    await sendContent(chatFileMarker({
      fileId: asset.id,
      kind: asset.kind,
      size: asset.size,
      name: asset.originalName,
    }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

function markImageBroken(messageId: number) {
  imageBroken.value = { ...imageBroken.value, [messageId]: true }
}

/* ---------------------------------------------------------------- 拖拽发送 */

function draggingFiles(event: DragEvent) {
  return Array.from(event.dataTransfer?.types ?? []).includes('Files')
}

function canAcceptDrop() {
  return !notFriend.value && !uploading.value
}

function onDragEnter(event: DragEvent) {
  if (draggingFiles(event) && canAcceptDrop()) {
    dragDepth.value += 1
  }
}

function onDragOver(event: DragEvent) {
  if (draggingFiles(event) && event.dataTransfer) {
    event.dataTransfer.dropEffect = canAcceptDrop() ? 'copy' : 'none'
  }
}

function onDragLeave() {
  dragDepth.value = Math.max(0, dragDepth.value - 1)
}

function onDrop(event: DragEvent) {
  dragDepth.value = 0
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (!files.length || !canAcceptDrop()) {
    return
  }
  void confirmAndSendFiles(files)
}

/** 拖进来的文件先确认再发；一次拖多个就逐个上传、逐条发送 */
async function confirmAndSendFiles(files: File[]) {
  const label = files.length === 1
    ? `是否发送文件「${files[0].name}」？`
    : `是否发送 ${files.length} 个文件？`
  try {
    await ElMessageBox.confirm(label, '发送文件', {
      confirmButtonText: '发送',
      cancelButtonText: '取消',
      type: 'info',
    })
  } catch {
    return
  }
  for (const file of files) {
    await sendChatFile(file)
  }
}

/** 点文件卡片：取最新元数据（mime/尺寸）再进预览弹窗 */
async function openChatFile(info: { fileId: number }) {
  try {
    previewFile.value = await fileApi.get(info.fileId)
    previewOpen.value = true
  } catch {
    ElMessage.error('文件已失效或被屏蔽')
  }
}

/* ---------------------------------------------------------------- 右上角菜单 */

function onMenuCommand(command: string | number | object) {
  const action = String(command)
  if (action === 'clear') {
    void clearChat()
    return
  }
  if (action === 'remove') {
    void removeFriend()
    return
  }
  openSearch(action === 'search-memo' ? 'memo' : 'text')
}

function openSearch(type: 'text' | 'memo') {
  searchType.value = type
  searchKeyword.value = ''
  searchResults.value = []
  searchDone.value = false
  searchOpen.value = true
  if (type === 'memo') {
    // 分享的笔记本来就不多，打开直接列出来，不用再输关键词
    void runSearch()
  }
}

async function runSearch() {
  const keyword = searchKeyword.value.trim()
  if (searchType.value === 'text' && !keyword) {
    ElMessage.warning('输入关键词再搜索')
    return
  }
  searching.value = true
  try {
    searchResults.value = await friendApi.searchChat(friendId.value, {
      type: searchType.value,
      keyword: searchType.value === 'text' ? keyword : undefined,
    })
    searchDone.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索失败')
  } finally {
    searching.value = false
  }
}

function openMemoFromSearch(message: ChatMessage) {
  const info = resolveMemoShare(message.content)
  if (!info) {
    return
  }
  searchOpen.value = false
  openMemoShare(info.token)
}

/** 搜索命中时就地跳过去；没加载到的老消息只提示一下 */
async function jumpToMessage(message: ChatMessage) {
  if (!messages.value.some((item) => item.id === message.id)) {
    ElMessage.info('这条消息不在最近加载的记录里')
    return
  }
  searchOpen.value = false
  await nextTick()
  const node = document.getElementById(`chat-msg-${message.id}`)
  if (!node) {
    return
  }
  node.scrollIntoView({ block: 'center', behavior: 'smooth' })
  highlightId.value = message.id
  clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => {
    highlightId.value = null
  }, 1600)
}

async function clearChat() {
  try {
    await ElMessageBox.confirm('是否清空本地聊天记录？', '清空聊天记录', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await friendApi.clearChat(friendId.value)
    messages.value = []
    ElMessage.success('已清空聊天记录')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清空失败')
  }
}

async function removeFriend() {
  try {
    await ElMessageBox.confirm('是否删除好友？', '删除好友', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await friendApi.remove(friendId.value)
    ElMessage.success('已删除好友')
    await refreshFriendSummary()
    await router.push('/friends')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function sendContent(content: string) {
  if (!content || sending.value) return
  sending.value = true
  const pendingId = --pendingSeq
  messages.value = [...messages.value, {
    id: pendingId,
    senderId: auth.user?.id ?? 0,
    mine: true,
    content,
    createdAt: new Date().toISOString(),
  }]
  await scrollToBottom()
  try {
    const message = await friendApi.sendChat(friendId.value, content)
    messages.value = messages.value.map((item) => (item.id === pendingId ? message : item))
    lastId = Math.max(lastId, message.id)
    // 没送达的消息后端也存着，这里跟着它的状态走
    peerRemoved.value = Boolean(message.failed)
    delete failed.value[pendingId]
  } catch (error) {
    failed.value[pendingId] = error instanceof Error ? error.message : '发送失败'
  } finally {
    sending.value = false
  }
}

/** 没送出去的消息：要么是后端标了 failed，要么是这次请求根本没到服务器 */
function isFailed(message: ChatMessage) {
  return Boolean(message.failed) || message.id in failed.value
}

function failedText(message: ChatMessage) {
  return failed.value[message.id] ?? '对方不是你的好友'
}

/** 点红叹号重发 */
async function retryMessage(message: ChatMessage) {
  if (retrying.value) return
  retrying.value = true
  try {
    // 存到服务器上的失败消息走重发接口（后端会换一条已送达的回来）；没存上的就再发一次
    const saved = message.id > 0
      ? await friendApi.resendChat(friendId.value, message.id)
      : await friendApi.sendChat(friendId.value, message.content)
    messages.value = messages.value.map((item) => (item.id === message.id ? saved : item))
    lastId = Math.max(lastId, saved.id)
    delete failed.value[message.id]
    peerRemoved.value = Boolean(saved.failed)
  } catch (error) {
    failed.value[message.id] = error instanceof Error ? error.message : '发送失败'
    if (error instanceof ApiError && error.status === 403) {
      peerRemoved.value = true
    }
  } finally {
    retrying.value = false
  }
}

/** 对方单删了我，重新发一条好友申请 */
async function readd() {
  readding.value = true
  try {
    await friendApi.sendRequest(friendId.value)
    peerRemoved.value = false
    ElMessage.success('已发送好友申请')
  } catch {
    /* 拦截器已经弹过提示了 */
  } finally {
    readding.value = false
  }
}
</script>

<template>
  <div
    class="chat-page"
    @dragenter.prevent="onDragEnter"
    @dragover.prevent="onDragOver"
    @dragleave="onDragLeave"
    @drop.prevent="onDrop"
  >
    <PageHeader :title="friend?.nickname ?? '聊天'" back-to="/friends" back-label="返回好友列表">
      <template #actions>
        <el-dropdown v-if="!loading && !notFriend" trigger="click" placement="bottom-end" @command="onMenuCommand">
          <button type="button" class="chat-menu-trigger" title="更多" aria-label="聊天设置">
            <el-icon :size="18"><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="search-text">查找聊天内容</el-dropdown-item>
              <el-dropdown-item command="search-memo">查找笔记分享</el-dropdown-item>
              <el-dropdown-item command="clear" divided>清空聊天记录</el-dropdown-item>
              <el-dropdown-item command="remove" divided>删除好友</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </PageHeader>

    <div class="chat-body">
      <div v-if="loading" class="chat-state">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="notFriend" class="chat-state empty">
        <span class="empty-icon"><el-icon><ChatDotRound /></el-icon></span>
        <p>对方不是你的好友</p>
      </div>

      <template v-else>
        <div ref="listRef" class="chat-scroll">
          <div class="chat-list">
            <div v-if="!messages.length" class="muted chat-hint">打个招呼吧。</div>
            <div
              v-for="entry in messageView"
              :key="entry.message.id"
              :id="`chat-msg-${entry.message.id}`"
              class="chat-row"
              :class="{ mine: entry.message.mine, 'chat-row--hit': highlightId === entry.message.id }"
            >
              <UserAvatar
                v-if="friend && !entry.message.mine"
                :user="friend"
                :size="30"
                class="chat-avatar"
              />
              <button
                v-if="entry.message.mine && isFailed(entry.message)"
                type="button"
                class="chat-failed"
                :title="`${failedText(entry.message)}，点击重新发送`"
                aria-label="发送失败，点击重新发送"
                @click="retryMessage(entry.message)"
              >
                <el-icon :size="17"><WarningFilled /></el-icon>
              </button>
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
                <div
                  v-else-if="entry.file && entry.file.kind === 'image' && imageBroken[entry.message.id]"
                  class="chat-file-missing"
                >
                  图片已失效或被屏蔽
                </div>
                <div v-else-if="entry.file && entry.file.kind === 'image'" class="chat-image">
                  <el-image
                    :src="chatFileThumbUrl(entry.file.fileId)"
                    :preview-src-list="[chatFileRawUrl(entry.file.fileId)]"
                    preview-teleported
                    hide-on-click-modal
                    @error="markImageBroken(entry.message.id)"
                  />
                </div>
                <div
                  v-else-if="entry.file"
                  class="chat-file-card"
                  role="button"
                  tabindex="0"
                  @click="openChatFile(entry.file)"
                  @keydown.enter.prevent="openChatFile(entry.file)"
                >
                  <span class="memo-card-icon">
                    <el-icon :size="18"><component :is="fileKindIcon(entry.file.kind)" /></el-icon>
                  </span>
                  <span class="file-card-copy">
                    <strong>{{ entry.file.name }}</strong>
                    <small>{{ formatChatFileSize(entry.file.size) }} · 点击预览</small>
                  </span>
                  <a
                    class="file-card-download"
                    title="下载"
                    :href="chatFileDownloadUrl(entry.file.fileId)"
                    @click.stop
                  >
                    <el-icon :size="15"><Download /></el-icon>
                  </a>
                </div>
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

        <div v-if="peerRemoved" class="chat-peer-removed">
          <el-icon :size="15"><WarningFilled /></el-icon>
          <span>对方不是你的好友</span>
          <el-button size="small" type="primary" plain :loading="readding" @click="readd">
            添加好友
          </el-button>
        </div>

        <footer class="chat-input-bar">
          <div v-if="uploading" class="chat-upload-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>上传中 {{ uploadPercent }}%</span>
          </div>
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
          <button
            type="button"
            class="emoji-trigger"
            title="发送图片"
            aria-label="发送图片"
            :disabled="uploading"
            @click="pickImage"
          >
            <el-icon :size="19"><Picture /></el-icon>
          </button>
          <button
            type="button"
            class="emoji-trigger"
            title="发送文件"
            aria-label="发送文件"
            :disabled="uploading"
            @click="pickFile"
          >
            <el-icon :size="19"><Paperclip /></el-icon>
          </button>
          <input
            ref="imageInput"
            type="file"
            accept="image/*"
            hidden
            @change="onFileChosen"
          />
          <input
            ref="fileInput"
            type="file"
            hidden
            @change="onFileChosen"
          />
          <el-input
            v-model="draft"
            maxlength="2000"
            @keyup.enter="onEnterKey"
          />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">
            发送
          </el-button>
        </footer>
      </template>
    </div>

    <el-dialog
      v-model="searchOpen"
      :title="searchTitle"
      width="min(520px, 92vw)"
      append-to-body
    >
      <div class="chat-search">
        <el-input
          v-if="searchType === 'text'"
          v-model="searchKeyword"
          placeholder="输入关键词，回车搜索"
          maxlength="50"
          clearable
          @keyup.enter="runSearch"
        >
          <template #append>
            <el-button :loading="searching" @click="runSearch">搜索</el-button>
          </template>
        </el-input>

        <div v-if="searching" class="chat-search-state">
          <el-skeleton :rows="3" animated />
        </div>
        <p v-else-if="searchDone && !searchResults.length" class="muted chat-search-state">
          {{ searchType === 'memo' ? '还没有分享过笔记。' : '没有找到相关的聊天内容。' }}
        </p>

        <ul v-else class="chat-search-list">
          <li v-for="entry in searchView" :key="entry.message.id">
            <button
              v-if="entry.memo"
              type="button"
              class="chat-search-memo"
              @click="openMemoFromSearch(entry.message)"
            >
              <span class="memo-card-icon"><el-icon :size="16"><DocumentCopy /></el-icon></span>
              <span class="chat-search-copy">
                <strong>{{ entry.memo?.title || '无标题' }}</strong>
                <small>{{ entry.memo?.excerpt || '点击查看笔记' }}</small>
              </span>
              <span class="muted chat-search-time">{{ formatFull(entry.message.createdAt) }}</span>
            </button>
            <button
              v-else
              type="button"
              class="chat-search-text"
              @click="jumpToMessage(entry.message)"
            >
              <span class="chat-search-meta">
                <span class="chat-search-sender">{{ entry.message.mine ? '我' : friend?.nickname }}</span>
                <span class="muted chat-search-time">{{ formatFull(entry.message.createdAt) }}</span>
              </span>
              <span class="chat-search-body">{{ entry.message.content }}</span>
            </button>
          </li>
        </ul>
      </div>
    </el-dialog>

    <div v-if="dragDepth > 0" class="chat-drop-overlay" aria-hidden="true">
      <div class="chat-drop-box">
        <el-icon :size="26"><Paperclip /></el-icon>
        <span>松开发送文件</span>
      </div>
    </div>

    <FilePreview v-model="previewOpen" :file="previewFile" />
  </div>
</template>

<style scoped>
.chat-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: var(--bg);
}

/* 拖文件进来时的全页提示遮罩；pointer-events 别挡住 drop */
.chat-drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  background: var(--el-overlay-color-lighter);
  pointer-events: none;
}

.chat-drop-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 28px;
  border: 2px dashed var(--brand-fill);
  border-radius: 14px;
  background: var(--panel);
  color: var(--brand-hover);
  font-size: 14px;
  font-weight: 600;
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

/* 图片消息：缩略图气泡，点开看原图 */
.chat-image {
  padding: 2px;
}

.chat-image :deep(.el-image) {
  display: block;
  width: min(260px, 64vw);
  border-radius: 12px;
  overflow: hidden;
  cursor: zoom-in;
}

.chat-image :deep(.el-image__inner) {
  display: block;
  width: 100%;
}

/* 图片没了（被删除/封禁）时的占位 */
.chat-file-missing {
  display: inline-block;
  padding: 9px var(--space-3);
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--panel);
  color: var(--text-3);
  font-size: 13px;
}

/* 视频 / 音频 / 其他文件的卡片消息 */
.chat-file-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(300px, 72vw);
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--panel);
  color: var(--text);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease-out), background var(--dur) var(--ease-out);
}

.chat-file-card:hover,
.chat-file-card:focus-visible {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
}

.chat-row.mine .chat-file-card {
  background: var(--brand-soft);
  border-color: transparent;
}

.file-card-copy {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.file-card-copy strong,
.file-card-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-card-copy strong {
  font-size: 13.5px;
}

.file-card-copy small {
  color: var(--text-3);
  font-size: 12px;
}

.file-card-download {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--text-2);
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.file-card-download:hover,
.file-card-download:focus-visible {
  background: var(--panel-2);
  color: var(--brand);
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

/* 发送失败：气泡旁边的红色叹号，点一下重发 */
.chat-failed {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  margin-bottom: 17px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--danger-fill);
  cursor: pointer;
}

.chat-failed:hover,
.chat-failed:focus-visible {
  color: var(--danger);
}

.chat-peer-removed {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 680px;
  width: 100%;
  margin: 0 auto;
  padding: var(--space-2) var(--space-4);
  border-top: 1px solid var(--border);
  color: var(--danger);
  font-size: 12.5px;
}

.chat-peer-removed span {
  flex: 1 1 auto;
  min-width: 0;
}

.chat-input-bar {
  position: relative;
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

/* 上传时的悬浮进度条 */
.chat-upload-tip {
  position: absolute;
  right: var(--space-4);
  bottom: calc(100% + 8px);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--panel);
  color: var(--text-2);
  font-size: 12.5px;
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
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

.chat-menu-trigger {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.chat-menu-trigger:hover,
.chat-menu-trigger:focus-visible {
  background: var(--panel-hover);
  color: var(--text);
}

/* 从搜索结果跳过来时闪一下，方便定位 */
.chat-row--hit .chat-bubble,
.chat-row--hit .chat-memo-card,
.chat-row--hit .chat-file-card {
  border-color: var(--brand-fill);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.chat-search {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.chat-search-state {
  margin: 0;
  padding: var(--space-3) 0;
  text-align: center;
  font-size: 13px;
}

.chat-search-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: min(52vh, 460px);
  overflow-y: auto;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chat-search-text,
.chat-search-memo {
  display: flex;
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--panel);
  color: var(--text);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur) var(--ease-out), background var(--dur) var(--ease-out);
}

.chat-search-text {
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
}

.chat-search-memo {
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
}

.chat-search-text:hover,
.chat-search-text:focus-visible,
.chat-search-memo:hover,
.chat-search-memo:focus-visible {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
}

.chat-search-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.chat-search-sender {
  color: var(--text-2);
  font-size: 12.5px;
  font-weight: 600;
}

.chat-search-time {
  flex: 0 0 auto;
  margin-left: auto;
  font-size: 11.5px;
  white-space: nowrap;
}

.chat-search-body {
  font-size: 13.5px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.chat-search-copy {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.chat-search-copy strong,
.chat-search-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-search-copy strong {
  font-size: 13.5px;
}

.chat-search-copy small {
  color: var(--text-3);
  font-size: 12px;
}

@media (max-width: 640px) {
  .chat-emoji img {
    width: 96px;
    height: 96px;
  }

  .chat-image :deep(.el-image) {
    width: min(200px, 56vw);
  }
}
</style>
