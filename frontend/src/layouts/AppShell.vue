<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Edit, Link, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useCreateNote } from '@/composables/useCreateNote'
import { useUnreadMessages } from '@/composables/useUnreadMessages'
import UserAvatar from '@/components/UserAvatar.vue'
import OpenShareDialog from '@/components/OpenShareDialog.vue'

const auth = useAuthStore()
const router = useRouter()
const createNote = useCreateNote()
const { unread, refresh: refreshUnread } = useUnreadMessages()
const creating = ref(false)
const shareOpen = ref(false)

let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  void refreshUnread()
  // 站内信可能随时被后台发出，这里定时拉一下未读数
  timer = setInterval(() => void refreshUnread(), 60_000)
})

onBeforeUnmount(() => clearInterval(timer))

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
        <button type="button" class="new-note-btn" :disabled="creating" @click="onNew">
          <el-icon :size="16"><Edit /></el-icon>
          <span>新建笔记</span>
        </button>
        <button
          type="button"
          class="icon-round"
          title="好友"
          aria-label="好友"
          @click="router.push('/friends')"
        >
          <el-icon :size="18"><User /></el-icon>
        </button>
        <button
          type="button"
          class="icon-round"
          title="站内信"
          :aria-label="unread > 0 ? `站内信，${unread} 条未读` : '站内信'"
          @click="router.push('/messages')"
        >
          <el-badge :value="unread" :hidden="unread === 0" :max="99" class="msg-badge">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
        </button>
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

    <main class="shell-content">
      <router-view />
    </main>

    <OpenShareDialog v-model="shareOpen" />
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
.msg-badge :deep(.el-badge__content) {
  background: var(--danger-fill);
  color: #fff;
  border: none;
  font-variant-numeric: tabular-nums;
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
