<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, EditPen, Lock, Reading } from '@element-plus/icons-vue'
import { friendApi, shareApi } from '@/api'
import type { PublicMemoDetail } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { formatFull } from '@/utils/format'
import MarkdownView from '@/components/MarkdownView.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const detail = ref<PublicMemoDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const ownerActionsOpen = ref(false)
const ownerAvatarFailed = ref(false)
const ownerMenuRef = ref<HTMLElement | null>(null)

const token = computed(() => String(route.params.token ?? ''))
const isOwner = computed(() => auth.user?.id === detail.value?.ownerId)
const owner = computed(() => ({
  nickname: detail.value?.ownerName,
  avatarUrl: ownerAvatarFailed.value ? null : detail.value?.ownerAvatarUrl,
}))

onMounted(() => {
  if (!auth.initialized) {
    void auth.fetchMe()
  }
  void load()
  document.addEventListener('pointerdown', onDocumentPointerDown)
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
watch(token, () => void load())

async function load() {
  loading.value = true
  errorMessage.value = ''
  detail.value = null
  ownerActionsOpen.value = false
  ownerAvatarFailed.value = false
  try {
    detail.value = await shareApi.get(token.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法访问这篇分享'
  } finally {
    loading.value = false
  }
}

function goLogin() {
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

function goApp() {
  if (isOwner.value && detail.value) {
    void router.push(`/notes/${detail.value.id}`)
    return
  }
  void router.push('/notes')
}

function toggleOwnerActions() {
  if (isOwner.value) return
  if (!auth.isLoggedIn) {
    goLogin()
    return
  }
  ownerActionsOpen.value = !ownerActionsOpen.value
}

function onDocumentPointerDown(event: PointerEvent) {
  if (!ownerActionsOpen.value || !ownerMenuRef.value) return
  if (!ownerMenuRef.value.contains(event.target as Node)) {
    ownerActionsOpen.value = false
  }
}

async function addFriend() {
  if (!detail.value?.ownerId) return
  try {
    await friendApi.sendRequest(detail.value.ownerId)
    ownerActionsOpen.value = false
    ElMessage.success(`已向 ${detail.value.ownerName} 发送好友申请`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  }
}

function openChat() {
  if (!detail.value?.ownerId) return
  ownerActionsOpen.value = false
  void router.push(`/friends/${detail.value.ownerId}`)
}
</script>

<template>
  <div class="share-page">
    <header class="share-header">
      <button type="button" class="brand" aria-label="返回首页" @click="router.push(auth.isLoggedIn ? '/notes' : '/login')">
        <img src="/logo.png" alt="" />
        <span>拾忆</span>
      </button>

      <button v-if="!auth.isLoggedIn" type="button" class="header-action" @click="goLogin">登录</button>
      <button v-else type="button" class="header-action" @click="goApp">
        <el-icon><EditPen v-if="isOwner" /><Reading v-else /></el-icon>
        <span>{{ isOwner ? '编辑笔记' : '进入我的拾忆' }}</span>
      </button>
    </header>

    <main class="share-main">
      <div v-if="loading" class="share-content">
        <el-skeleton :rows="8" animated />
      </div>

      <section v-else-if="errorMessage" class="share-state" role="alert">
        <span class="state-icon"><el-icon :size="28"><Lock /></el-icon></span>
        <h1>没有访问权限</h1>
        <p>{{ errorMessage }}</p>
        <el-button type="primary" @click="auth.isLoggedIn ? goApp() : goLogin()">
          {{ auth.isLoggedIn ? '进入拾忆' : '登录' }}
        </el-button>
      </section>

      <article v-else-if="detail" class="share-content">
        <header class="article-header">
          <h1>{{ detail.title || '无标题' }}</h1>
          <div class="article-meta tnum">
            <div
              ref="ownerMenuRef"
              class="owner-menu-wrap"
              @keydown.esc="ownerActionsOpen = false"
            >
              <button
                type="button"
                class="owner-trigger"
                aria-haspopup="menu"
                :aria-expanded="ownerActionsOpen"
                :aria-label="`查看 ${detail.ownerName} 的操作`"
                @click="toggleOwnerActions"
              >
                <UserAvatar :user="owner" :size="30" @error="ownerAvatarFailed = true" />
                <span class="owner-name">{{ detail.ownerName }}</span>
              </button>

              <Transition name="owner-menu">
                <div
                  v-if="ownerActionsOpen && auth.isLoggedIn && !isOwner"
                  class="owner-menu"
                  role="menu"
                >
                  <button
                    type="button"
                    class="owner-menu-item"
                    role="menuitem"
                    @click="detail.friend ? openChat() : addFriend()"
                  >
                    <el-icon v-if="detail.friend" :size="15">
                      <ChatDotRound />
                    </el-icon>
                    <span>{{ detail.friend ? '私信' : '加好友' }}</span>
                  </button>
                </div>
              </Transition>
            </div>
            <span class="meta-copy">分享 · 更新于 {{ formatFull(detail.updatedAt) }}</span>
          </div>
        </header>
        <MarkdownView :content="detail.contentMd" :public-token="token" />
      </article>
    </main>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.share-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: var(--space-3) clamp(16px, 4vw, 40px);
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--panel) 94%, transparent);
  backdrop-filter: blur(12px);
  /* 桌面端无边框窗口：右侧给系统按钮让位，顶栏兼作拖动区 */
  padding-right: calc(clamp(16px, 4vw, 40px) + var(--titlebar-inset));
  -webkit-app-region: drag;
}

.share-header :is(button, a) {
  -webkit-app-region: no-drag;
}

.brand,
.header-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 38px;
  border: none;
  background: transparent;
  color: var(--text);
  font-family: inherit;
  cursor: pointer;
}

.brand {
  padding: 0;
  font-size: 16px;
  font-weight: 700;
}

.brand img {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  object-fit: cover;
}

.header-action {
  padding: 0 var(--space-4);
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: var(--panel);
  font-size: 13.5px;
  font-weight: 600;
  transition: background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}

.header-action:hover {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
  color: var(--brand-hover);
}

.share-main {
  width: min(820px, 100%);
  margin: 0 auto;
  padding: clamp(32px, 6vw, 72px) var(--space-5) var(--space-12);
}

.share-content {
  min-width: 0;
}

.article-header {
  margin-bottom: var(--space-8);
  padding-bottom: var(--space-6);
  border-bottom: 1px solid var(--border);
}

.article-header h1 {
  margin: 0;
  font-size: clamp(28px, 5vw, 42px);
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.article-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: var(--space-4) 0 0;
  color: var(--text-3);
  font-size: 13px;
}

.owner-menu-wrap {
  position: relative;
  display: inline-flex;
}

.owner-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 1px 7px 1px 1px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--text);
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), border-color var(--dur) var(--ease-out);
}

.owner-trigger:hover,
.owner-trigger:focus-visible {
  border-color: var(--border);
  background: var(--panel-2);
}

.owner-name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 650;
}

.meta-copy {
  white-space: nowrap;
}

/* 点头像后浮出的操作菜单 */
.owner-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  width: 148px;
  padding: 5px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--panel);
  box-shadow: 0 10px 28px rgb(0 0 0 / 14%);
}

.owner-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 0 9px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-2);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.owner-menu-item:hover,
.owner-menu-item:focus-visible {
  background: var(--brand-soft);
  color: var(--brand-hover);
}

.owner-menu-enter-active,
.owner-menu-leave-active {
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.owner-menu-enter-from,
.owner-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

.share-state {
  display: grid;
  justify-items: center;
  max-width: 440px;
  margin: 10vh auto 0;
  text-align: center;
}

.state-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: var(--space-5);
  border-radius: 50%;
  background: var(--panel-2);
  color: var(--text-3);
}

.share-state h1 {
  margin: 0;
  font-size: 22px;
}

.share-state p {
  margin: var(--space-3) 0 var(--space-5);
  color: var(--text-2);
}

@media (max-width: 640px) {
  .share-main {
    padding-top: var(--space-8);
  }

  .article-header {
    margin-bottom: var(--space-6);
  }
}
</style>
