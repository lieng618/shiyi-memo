<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useCreateNote } from '@/composables/useCreateNote'
import { useUnreadMessages } from '@/composables/useUnreadMessages'
import UserAvatar from '@/components/UserAvatar.vue'

const auth = useAuthStore()
const router = useRouter()
const createNote = useCreateNote()
const { unread, refresh: refreshUnread } = useUnreadMessages()
const creating = ref(false)

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

      <!-- 右上角账号区：头像 + 昵称，头像正下方是新建图标 -->
      <div class="account">
        <button type="button" class="account-btn" title="个人中心" @click="router.push('/me')">
          <UserAvatar :user="auth.user" :size="38" />
          <span class="account-name">{{ auth.user?.nickname }}</span>
        </button>
        <div class="account-actions">
          <button
            type="button"
            class="icon-round"
            title="新建笔记"
            aria-label="新建笔记"
            :disabled="creating"
            @click="onNew"
          >
            <el-icon :size="18"><Plus /></el-icon>
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
        </div>
      </div>
    </header>

    <main class="shell-content">
      <router-view />
    </main>
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
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-6);
  background: var(--panel);
  border-bottom: 1px solid var(--border);
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

.account {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.account-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: 0 var(--space-2) 0 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out);
}

.account-btn:hover {
  background: var(--panel-hover);
}

.account-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.account-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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
  }

  .brand-name {
    display: none;
  }

  .account {
    flex-direction: row;
    align-items: center;
    justify-self: end;
    gap: var(--space-3);
  }

  .account-actions {
    gap: var(--space-2);
  }

  .account-name {
    display: none;
  }
}
</style>
