<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Edit, Link, User } from '@element-plus/icons-vue'
import { friendApi, messageApi } from '@/api'
import type { FriendNotification, UserMessage } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { prefetchNoteEditor, useCreateNote } from '@/composables/useCreateNote'
import { useAppVersion } from '@/composables/useAppVersion'
import { useUpdater } from '@/composables/useUpdater'
import { useFriendSummary } from '@/composables/useFriendSummary'
import { useUnreadMessages } from '@/composables/useUnreadMessages'
import { onRealtime } from '@/composables/useRealtime'
import { formatFull } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import OpenShareDialog from '@/components/OpenShareDialog.vue'
import UpdaterOverlay from '@/components/UpdaterOverlay.vue'

const auth = useAuthStore()
const router = useRouter()
const createNote = useCreateNote()
// 部署即生效，发现新版就提示刷新
const { outdated: versionOutdated, reload: reloadForUpdate } = useAppVersion()
const {
  supported: updaterSupported,
  hasUpdate: clientUpdateReady,
  version: clientNewVersion,
  startUpdate: startClientUpdate,
} = useUpdater()
const { unread, refresh: refreshUnread } = useUnreadMessages()
const {
  total: friendUnread,
  refresh: refreshFriendSummary,
} = useFriendSummary()
const creating = ref(false)
const shareOpen = ref(false)
const friendPreviewOpen = ref(false)
const friendPreviewLoading = ref(false)
const friendNotifications = ref<FriendNotification[]>([])
const messagePreviewOpen = ref(false)
const messagePreviewLoading = ref(false)
const unreadMessages = ref<UserMessage[]>([])

/** 兜底轮询间隔：SSE 正常时它什么都不会变，只为兜住断线和定时站内信 */
const FALLBACK_POLL_MS = 60_000

let timer: ReturnType<typeof setInterval> | undefined
let offChat: (() => void) | undefined
let offFriends: (() => void) | undefined
let offMail: (() => void) | undefined

onMounted(() => {
  void refreshUnread()
  void refreshFriendSummary()
  void loadFriendNotifications()
  void loadUnreadMessages()
  offChat = onRealtime('chat', () => {
    void refreshFriendSummary()
    void loadFriendNotifications(true)
  })
  offFriends = onRealtime('friends', () => {
    void loadFriendNotifications(true)
  })
  // 新站内信（以及别的标签页里的已读）由服务端推过来，不再靠轮询发现
  offMail = onRealtime('mail', () => {
    void refreshUnread()
    if (messagePreviewOpen.value) {
      void loadUnreadMessages(true)
    }
  })
  // 慢轮询只兜底（SSE 掉线、定时站内信到点）：只打两个很轻的计数接口，
  // 列表接口只在对应弹层开着的时候才拉
  timer = setInterval(() => {
    void refreshUnread()
    void refreshFriendSummary()
    if (friendPreviewOpen.value) {
      void loadFriendNotifications(true)
    }
    if (messagePreviewOpen.value) {
      void loadUnreadMessages(true)
    }
  }, FALLBACK_POLL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  offChat?.()
  offFriends?.()
  offMail?.()
})

/** 切回标签页时对齐一次角标：后台期间 SSE 可能断过，漏掉的事件靠这一次补上 */
function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void refreshUnread()
    void refreshFriendSummary()
  }
}

async function loadFriendNotifications(silent = false) {
  if (!silent) {
    friendPreviewLoading.value = true
  }
  try {
    friendNotifications.value = await friendApi.notifications()
  } finally {
    if (!silent) {
      friendPreviewLoading.value = false
    }
  }
}

function refreshFriendNotifications() {
  void loadFriendNotifications(true)
}

/** 铃铛预览只看未读；已读的进「查看全部」里翻 */
async function loadUnreadMessages(silent = false) {
  if (!silent) {
    messagePreviewLoading.value = true
  }
  try {
    const result = await messageApi.list({ page: 1, size: 10 })
    unreadMessages.value = result.records.filter((m) => !m.readAt)
  } finally {
    if (!silent) {
      messagePreviewLoading.value = false
    }
  }
}

function refreshUnreadMessages() {
  void loadUnreadMessages(true)
}

async function openMessage(item: UserMessage) {
  messagePreviewOpen.value = false
  await router.push(`/messages/${item.id}`)
}

async function openFriendNotification(item: FriendNotification) {
  friendPreviewOpen.value = false
  if (item.type === 'FRIEND_REQUEST') {
    await router.push({ path: '/friends', query: { tab: 'requests' } })
    return
  }
  await router.push(`/friends/${item.userId}`)
}

function viewAllFriends() {
  friendPreviewOpen.value = false
  void router.push('/friends')
}

async function onNew() {
  if (creating.value) return
  creating.value = true
  try {
    await createNote()
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div class="shell">
    <header class="shell-header">
      <div class="brand">
        <img class="brand-badge" src="/logo.png" alt="" />
        <span class="brand-name">拾忆</span>
      </div>

      <!-- 右侧单行：主操作 + 站内信 + 账号，不再上下堆叠 -->
      <div class="header-actions">
        <!-- 悬停 / 聚焦就先把编辑页代码拉下来：点下去时只剩建库请求和渲染 -->
        <button
          type="button"
          class="new-note-btn"
          :disabled="creating"
          @click="onNew"
          @mouseenter="prefetchNoteEditor"
          @focus="prefetchNoteEditor"
        >
          <el-icon :size="16"><Edit /></el-icon>
          <span>新建笔记</span>
        </button>
        <el-popover
          v-model:visible="friendPreviewOpen"
          trigger="hover"
          placement="bottom-end"
          width="320"
          :show-arrow="false"
          :disabled="friendUnread === 0"
          :popper-style="{ padding: '0', borderRadius: '10px', overflow: 'hidden' }"
          @show="refreshFriendNotifications"
        >
          <template #reference>
            <button
              type="button"
              class="icon-round"
              :aria-label="friendUnread > 0 ? `好友，${friendUnread} 条新动态` : '好友'"
              @click="viewAllFriends"
            >
              <el-badge :value="friendUnread" :hidden="friendUnread === 0" :max="99" class="header-badge">
                <el-icon :size="18"><User /></el-icon>
              </el-badge>
            </button>
          </template>

          <div class="header-preview">
            <header class="header-preview-head">
              <strong>新动态</strong>
              <span class="muted">{{ friendUnread }} 条</span>
            </header>

            <div v-if="friendPreviewLoading && !friendNotifications.length" class="header-preview-loading">
              <el-skeleton :rows="2" animated />
            </div>

            <div v-else-if="friendNotifications.length" class="header-preview-list">
              <button
                v-for="item in friendNotifications"
                :key="`${item.type}-${item.userId}`"
                type="button"
                class="header-preview-item"
                @click="openFriendNotification(item)"
              >
                <UserAvatar :user="item" :size="34" />
                <span class="header-preview-copy">
                  <strong>{{ item.nickname }}</strong>
                  <span>{{ item.content }}</span>
                </span>
                <span class="header-preview-meta">
                  <span v-if="item.unread" class="preview-count">{{ item.unread }}</span>
                  <time class="tnum">{{ formatFull(item.createdAt) }}</time>
                </span>
              </button>
            </div>

            <div v-else class="header-preview-empty">暂无新动态</div>
          </div>
        </el-popover>
        <el-popover
          v-model:visible="messagePreviewOpen"
          trigger="hover"
          placement="bottom-end"
          width="320"
          :show-arrow="false"
          :disabled="unread === 0"
          :popper-style="{ padding: '0', borderRadius: '10px', overflow: 'hidden' }"
          @show="refreshUnreadMessages"
        >
          <template #reference>
            <button
              type="button"
              class="icon-round"
              :aria-label="unread > 0 ? `站内信，${unread} 条未读` : '站内信'"
              @click="router.push('/messages')"
            >
              <el-badge :value="unread" :hidden="unread === 0" :max="99" class="header-badge">
                <el-icon :size="18"><Bell /></el-icon>
              </el-badge>
            </button>
          </template>

          <div class="header-preview">
            <header class="header-preview-head">
              <strong>未读站内信</strong>
              <span class="muted">{{ unread }} 条</span>
            </header>

            <div v-if="messagePreviewLoading && !unreadMessages.length" class="header-preview-loading">
              <el-skeleton :rows="2" animated />
            </div>

            <div v-else-if="unreadMessages.length" class="header-preview-list">
              <button
                v-for="item in unreadMessages"
                :key="item.id"
                type="button"
                class="header-preview-item"
                @click="openMessage(item)"
              >
                <span class="preview-dot" />
                <span class="header-preview-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.content }}</span>
                </span>
                <span class="header-preview-meta">
                  <time class="tnum">{{ formatFull(item.createdAt) }}</time>
                </span>
              </button>
            </div>

            <div v-else class="header-preview-empty">暂无未读站内信</div>
          </div>
        </el-popover>
        <button
          type="button"
          class="icon-round"
          title="打开分享链接"
          aria-label="打开分享链接"
          @click="shareOpen = true"
        >
          <el-icon :size="18"><Link /></el-icon>
        </button>
        <button type="button" class="account-btn" title="个人中心" @click="router.push('/me')">
          <UserAvatar :user="auth.user" :size="34" />
          <span class="account-name">{{ auth.user?.nickname }}</span>
        </button>
      </div>
    </header>

    <!-- 服务器发了新版本，刷一下就能拿到 -->
    <div v-if="versionOutdated" class="update-banner" role="status">
      <span>网站已更新到新版本</span>
      <el-button size="small" type="primary" @click="reloadForUpdate">立即刷新</el-button>
    </div>

    <!-- 客户端有新版本：点一下就下载、安装、重启 -->
    <div v-else-if="updaterSupported && clientUpdateReady" class="update-banner" role="status">
      <span>客户端有新版本 {{ clientNewVersion }}</span>
      <el-button size="small" type="primary" @click="startClientUpdate">更新</el-button>
    </div>

    <main class="shell-content">
      <router-view />
    </main>

    <OpenShareDialog v-model="shareOpen" />
    <UpdaterOverlay />
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

/* 新版本提示条 */
.update-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-6);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
}

.shell-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-6);
  /* 跟页面同色，顶栏和正文连成一片，不再多出一条浅一档的带；
     正文在自己的滚动容器里，不会滑到顶栏下面，所以不需要分隔线 */
  background: var(--bg);
  /* 桌面端无边框窗口：右侧给系统按钮让位，整条顶栏兼作窗口拖动区 */
  padding-right: calc(var(--space-6) + var(--titlebar-inset));
  -webkit-app-region: drag;
}

/* 拖动区里的控件要单独标回来，否则点不动 */
.shell-header button {
  -webkit-app-region: no-drag;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 40px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.brand-badge {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  object-fit: cover;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

/* 主操作给文字标签，比一个圆形图标更容易被发现 */
.new-note-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 var(--space-4);
  border: none;
  border-radius: 999px;
  background: var(--brand-fill);
  color: var(--on-brand-fill);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.new-note-btn:hover:not(:disabled) {
  background: var(--brand-fill-hover);
}

.new-note-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.new-note-btn:disabled {
  opacity: 0.55;
  cursor: progress;
}

.account-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: 0 var(--space-3) 0 var(--space-1);
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}

.account-btn:hover {
  border-color: var(--border);
  background: var(--panel-2);
}

.account-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.icon-round {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: var(--brand-fill);
  color: var(--on-brand-fill);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.icon-round:hover:not(:disabled) {
  background: var(--brand-fill-hover);
}

.icon-round:active:not(:disabled) {
  transform: scale(0.94);
}

.icon-round:disabled {
  opacity: 0.55;
  cursor: progress;
}

/* 未读角标：琥珀底 + 深色字，和主色一致 */
.header-badge :deep(.el-badge__content) {
  background: var(--danger-fill);
  color: #fff;
  border: none;
  font-variant-numeric: tabular-nums;
}

/* 顶栏两个悬停预览（好友新动态 / 未读站内信）共用的样式 */
.header-preview {
  color: var(--text);
}

.header-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 var(--space-3);
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}

.header-preview-loading {
  padding: var(--space-3);
}

.header-preview-list {
  max-height: 320px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.header-preview-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding: 10px var(--space-3);
  border: none;
  border-bottom: 1px solid var(--border);
  background: transparent;
  color: var(--text);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.header-preview-item:hover,
.header-preview-item:focus-visible {
  background: var(--panel-2);
}

.header-preview-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.header-preview-copy strong,
.header-preview-copy > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-preview-copy strong {
  font-size: 13px;
}

.header-preview-copy > span {
  color: var(--text-3);
  font-size: 12px;
}

.header-preview-meta {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 4px;
  flex: 0 0 auto;
}

.header-preview-meta time {
  color: var(--text-3);
  font-size: 10.5px;
  white-space: nowrap;
}

.preview-count {
  min-width: 18px;
  padding: 1px 5px;
  border-radius: 999px;
  background: var(--danger-fill);
  color: #fff;
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

/* 站内信一行一条，未读用小圆点标出 */
.preview-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--danger-fill);
}

.header-preview-empty {
  padding: var(--space-5) var(--space-3);
  color: var(--text-3);
  font-size: 13px;
  text-align: center;
}

.shell-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

@media (max-width: 768px) {
  .shell-header {
    padding: var(--space-3) var(--space-4);
    padding-right: calc(var(--space-4) + var(--titlebar-inset));
  }

  /* 窄屏保留「新建笔记」的文字，只收起 logo 字和昵称 */
  .brand-name,
  .account-name {
    display: none;
  }
}
</style>
