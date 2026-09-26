<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataLine,
  Document,
  FolderOpened,
  Histogram,
  Message,
  SwitchButton,
  User as UserIcon,
} from '@element-plus/icons-vue'
import { adminEvents } from './api'
import { adminApi } from './endpoints'
import DashboardView from './views/DashboardView.vue'
import UsersView from './views/UsersView.vue'
import NotesView from './views/NotesView.vue'
import FilesView from './views/FilesView.vue'
import MessagesView from './views/MessagesView.vue'
import LevelsView from './views/LevelsView.vue'

type ViewKey = 'dashboard' | 'users' | 'notes' | 'files' | 'messages' | 'levels'

const navItems: { key: ViewKey; label: string; icon: unknown }[] = [
  { key: 'dashboard', label: '概览', icon: DataLine },
  { key: 'users', label: '用户', icon: UserIcon },
  { key: 'notes', label: '笔记', icon: Document },
  { key: 'files', label: '文件', icon: FolderOpened },
  { key: 'messages', label: '站内信', icon: Message },
  { key: 'levels', label: '等级与配额', icon: Histogram },
]

const current = ref<ViewKey>('dashboard')
const authed = ref(false)
const checking = ref(true)
const adminName = ref('')
const loggingIn = ref(false)
const form = reactive({ username: '', password: '' })
const errorMessage = ref('')

onMounted(async () => {
  const hash = location.hash.replace('#', '')
  if (navItems.some((item) => item.key === hash)) {
    current.value = hash as ViewKey
  }
  adminEvents.onUnauthorized = () => {
    authed.value = false
  }
  try {
    const me = await adminApi.me()
    adminName.value = me.username
    authed.value = true
  } catch {
    authed.value = false
  } finally {
    checking.value = false
  }
})

function switchView(key: ViewKey) {
  current.value = key
  location.hash = key
}

async function login() {
  errorMessage.value = ''
  if (!form.username.trim() || !form.password) {
    errorMessage.value = '请填写账号和密码'
    return
  }
  loggingIn.value = true
  try {
    const result = await adminApi.login(form.username.trim(), form.password)
    adminName.value = result.username
    authed.value = true
    form.password = ''
    ElMessage.success('登录成功')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loggingIn.value = false
  }
}

async function logout() {
  await adminApi.logout()
  authed.value = false
  adminName.value = ''
}
</script>

<template>
  <div v-if="checking" class="adm-loading">
    <el-skeleton :rows="4" animated style="max-width: 420px; width: 100%" />
  </div>

  <!-- 登录：后台自己的账号，和备忘录用户无关 -->
  <div v-else-if="!authed" class="adm-login">
    <form class="adm-login-card" @submit.prevent="login">
      <img class="adm-badge" src="/logo.png" alt="拾忆" />
      <h1>后台控制台</h1>

      <div class="field">
        <label for="admin-username">账号</label>
        <el-input id="admin-username" v-model="form.username" placeholder="admin" autocomplete="username" />
      </div>
      <div class="field">
        <label for="admin-password">密码</label>
        <el-input
          id="admin-password"
          v-model="form.password"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="密码"
          @keyup.enter="login"
        />
      </div>

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

      <el-button type="primary" class="login-btn" :loading="loggingIn" native-type="submit">登录</el-button>
    </form>
  </div>

  <div v-else class="adm-shell">
    <nav class="adm-nav" aria-label="后台导航">
      <div class="adm-brand">
        <img class="adm-badge" src="/logo.png" alt="" />
        <span>后台控制台</span>
      </div>
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        class="adm-nav-item"
        :class="{ active: current === item.key }"
        @click="switchView(item.key)"
      >
        <el-icon :size="16"><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
      <div class="adm-nav-footer">
        <button type="button" class="adm-nav-item" @click="logout">
          <el-icon :size="16"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </button>
      </div>
    </nav>

    <main class="adm-main">
      <header class="adm-header">
        <h1>{{ navItems.find((item) => item.key === current)?.label }}</h1>
        <div class="spacer" />
        <el-tag size="small" effect="plain">{{ adminName }}</el-tag>
      </header>

      <div class="adm-body">
        <DashboardView v-if="current === 'dashboard'" @open="switchView" />
        <UsersView v-else-if="current === 'users'" />
        <NotesView v-else-if="current === 'notes'" />
        <FilesView v-else-if="current === 'files'" />
        <MessagesView v-else-if="current === 'messages'" />
        <LevelsView v-else-if="current === 'levels'" />
      </div>
    </main>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.field label {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-2);
}

.login-btn {
  width: 100%;
  min-height: 44px;
  margin-top: var(--space-2);
}

.error {
  margin: 0 0 var(--space-3);
  color: var(--danger);
  font-size: 13px;
}

.adm-login-card code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--panel-hover);
  font-size: 12px;
}
</style>
