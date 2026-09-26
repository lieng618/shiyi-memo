<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { EditPen, Lock, Reading } from '@element-plus/icons-vue'
import { shareApi } from '@/api'
import type { PublicMemoDetail } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { formatFull } from '@/utils/format'
import MarkdownView from '@/components/MarkdownView.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const detail = ref<PublicMemoDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const token = computed(() => String(route.params.token ?? ''))
const isOwner = computed(() => auth.user?.id === detail.value?.ownerId)

onMounted(() => {
  if (!auth.initialized) {
    void auth.fetchMe()
  }
  void load()
})
watch(token, () => void load())

async function load() {
  loading.value = true
  errorMessage.value = ''
  detail.value = null
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
          <p class="article-kicker">公开分享</p>
          <h1>{{ detail.title || '无标题' }}</h1>
          <p class="article-meta tnum">
            由 {{ detail.ownerName }} 分享 · 更新于 {{ formatFull(detail.updatedAt) }}
          </p>
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

.article-kicker {
  margin: 0 0 var(--space-3);
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.article-header h1 {
  margin: 0;
  font-size: clamp(28px, 5vw, 42px);
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.article-meta {
  margin: var(--space-4) 0 0;
  color: var(--text-3);
  font-size: 13px;
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
