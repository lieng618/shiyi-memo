<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, CircleCheck, Lock, Reading } from '@element-plus/icons-vue'
import { friendApi, inviteApi } from '@/api'
import type { InviteCard } from '@/types'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const code = computed(() => String(route.params.code ?? ''))
const card = ref<InviteCard | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const sending = ref(false)
const sent = ref(false)
const alreadyFriend = ref(false)
const isSelf = ref(false)

/** 邀请人的头像走免登录接口会额外暴露一个口子，这里就用昵称首字兜底 */
const inviter = computed(() => ({ nickname: card.value?.nickname ?? '', avatarUrl: null }))

onMounted(async () => {
  if (!auth.initialized) {
    await auth.fetchMe()
  }
  await load()
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    card.value = await inviteApi.get(code.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '邀请链接无效'
    loading.value = false
    return
  }

  if (auth.isLoggedIn && auth.user && card.value) {
    isSelf.value = auth.user.id === card.value.userId
    if (!isSelf.value) {
      // 已经是好友就别再让他发申请了，直接给「去聊天」
      try {
        const friends = await friendApi.list()
        alreadyFriend.value = friends.some((item) => item.userId === card.value?.userId)
      } catch {
        /* 拿不到好友列表不影响加好友 */
      }
    }
  }
  loading.value = false
}

async function addFriend() {
  if (!card.value) return
  sending.value = true
  try {
    await friendApi.sendRequest(card.value.userId)
    sent.value = true
    ElMessage.success('好友申请已发送')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

function goLogin() {
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

function goApp() {
  void router.push(auth.isLoggedIn ? '/notes' : { name: 'login' })
}

function goChat() {
  if (card.value) {
    void router.push(`/friends/${card.value.userId}`)
  }
}
</script>

<template>
  <div class="invite-page">
    <header class="invite-header">
      <div class="invite-brand">
        <img class="brand-mark" src="/logo.png" alt="" />
        <span>拾忆</span>
      </div>
      <button type="button" class="header-action" @click="goApp">
        <el-icon><Reading /></el-icon>
        <span>{{ auth.isLoggedIn ? '进入我的拾忆' : '登录' }}</span>
      </button>
    </header>

    <main class="invite-main">
      <div v-if="loading" class="invite-card">
        <el-skeleton :rows="4" animated />
      </div>

      <section v-else-if="errorMessage" class="invite-state" role="alert">
        <span class="state-icon"><el-icon :size="28"><Lock /></el-icon></span>
        <h1>邀请链接不可用</h1>
        <p>{{ errorMessage }}</p>
        <el-button type="primary" @click="goApp">
          {{ auth.isLoggedIn ? '进入拾忆' : '去登录' }}
        </el-button>
      </section>

      <section v-else-if="card" class="invite-card">
        <UserAvatar :user="inviter" :size="72" />
        <h1>{{ card.nickname }} 邀请你成为好友</h1>

        <template v-if="!auth.isLoggedIn">
          <el-button type="primary" size="large" @click="goLogin">登录后添加</el-button>
          <p class="invite-hint">没有账号？验证码登录会自动注册。</p>
        </template>

        <template v-else-if="isSelf">
          <p class="invite-hint">这是你自己的邀请链接。</p>
          <el-button @click="router.push('/me')">去个人中心</el-button>
        </template>

        <template v-else-if="alreadyFriend">
          <el-button type="primary" size="large" @click="goChat">
            <el-icon><ChatDotRound /></el-icon><span>已经是好友，去聊天</span>
          </el-button>
        </template>

        <template v-else-if="sent">
          <p class="invite-done">
            <el-icon><CircleCheck /></el-icon>
            <span>申请已发送，等对方同意</span>
          </p>
          <el-button @click="router.push('/friends?tab=requests')">查看我的申请</el-button>
        </template>

        <template v-else>
          <el-button type="primary" size="large" :loading="sending" @click="addFriend">
            加为好友
          </el-button>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.invite-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.invite-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: var(--space-3) clamp(16px, 4vw, 40px);
  border-bottom: 1px solid var(--border);
  background: var(--panel);
}

.invite-brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 18px;
  font-weight: 700;
}

.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
}

.header-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--panel);
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
}

.header-action:hover {
  border-color: var(--border-strong);
  color: var(--text);
}

.invite-main {
  padding: clamp(32px, 8vh, 96px) clamp(16px, 4vw, 40px);
}

.invite-card,
.invite-state {
  display: grid;
  justify-items: center;
  gap: var(--space-4);
  max-width: 460px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--panel);
  text-align: center;
}

.invite-card h1 {
  margin: 0;
  font-size: 21px;
  line-height: 1.4;
}

.invite-hint {
  margin: 0;
  color: var(--text-3);
  font-size: 12.5px;
}

.invite-done {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--success, var(--brand));
  font-size: 14px;
}

.invite-state {
  border: none;
  background: none;
}

.invite-state h1 {
  margin: 0;
  font-size: 20px;
}

.invite-state p {
  margin: 0;
  color: var(--text-2);
}

.state-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--panel-2);
  color: var(--text-3);
}
</style>
