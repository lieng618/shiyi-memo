<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Hide, InfoFilled, View } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

type Mode = 'code' | 'password'
const MODE_KEY = 'login-mode'

const mode = ref<Mode>(localStorage.getItem(MODE_KEY) === 'password' ? 'password' : 'code')
const email = ref('')
const code = ref('')
const password = ref('')
const showPassword = ref(false)
const emailError = ref('')
const codeError = ref('')
const passwordError = ref('')
const formError = ref('')
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
const devCode = ref('')
const mailEnabled = ref(true)
const emailRef = ref<HTMLInputElement | null>(null)
const setupOpen = ref(false)
const setupSaving = ref(false)
const setupError = ref('')
const setupPassword = ref('')
const setupConfirm = ref('')
const postLoginRedirect = ref('/notes')

let timer: ReturnType<typeof setInterval> | undefined

const canSendCode = computed(
  () => countdown.value === 0 && !sending.value && /^\S+@\S+\.\S+$/.test(email.value.trim()),
)

onMounted(() => emailRef.value?.focus())
onBeforeUnmount(() => clearInterval(timer))

watch(mode, (value) => {
  localStorage.setItem(MODE_KEY, value)
  codeError.value = ''
  passwordError.value = ''
  formError.value = ''
})

function switchMode(value: Mode) {
  mode.value = value
}

function startCountdown(seconds = 60) {
  countdown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

async function sendCode() {
  emailError.value = ''
  formError.value = ''
  if (!/^\S+@\S+\.\S+$/.test(email.value.trim())) {
    emailError.value = '请填写正确的邮箱地址'
    emailRef.value?.focus()
    return
  }
  sending.value = true
  try {
    const result = await authApi.sendCode(email.value.trim())
    mailEnabled.value = result.mailEnabled
    devCode.value = result.devCode ?? ''
    if (devCode.value) {
      code.value = devCode.value
    }
    startCountdown(60)
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '验证码发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function submit() {
  emailError.value = ''
  codeError.value = ''
  passwordError.value = ''
  formError.value = ''

  if (!/^\S+@\S+\.\S+$/.test(email.value.trim())) {
    emailError.value = '请填写正确的邮箱地址'
    emailRef.value?.focus()
    return
  }
  if (mode.value === 'code') {
    if (!/^\d{4,8}$/.test(code.value.trim())) {
      codeError.value = '请输入邮件里的数字验证码'
      return
    }
  } else if (!password.value) {
    passwordError.value = '请输入密码'
    return
  }

  submitting.value = true
  try {
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/notes'
    if (mode.value === 'code') {
      const user = await auth.login(email.value.trim(), code.value.trim())
      if (!user.hasPassword) {
        postLoginRedirect.value = redirect
        setupPassword.value = ''
        setupConfirm.value = ''
        setupError.value = ''
        setupOpen.value = true
        return
      }
    } else {
      const result = await authApi.loginWithPassword(email.value.trim(), password.value)
      auth.user = result.user
      auth.initialized = true
    }
    await finishLogin()
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '登录失败，请重试'
    if (mode.value === 'code') {
      codeError.value = '验证码可能已过期，可以重新获取'
    }
  } finally {
    submitting.value = false
  }
}

async function finishLogin() {
  await router.replace(postLoginRedirect.value)
}

async function saveInitialPassword() {
  setupError.value = ''
  if (setupPassword.value.length < 8) {
    setupError.value = '密码至少 8 位'
    return
  }
  if (setupPassword.value !== setupConfirm.value) {
    setupError.value = '两次输入的密码不一致'
    return
  }
  setupSaving.value = true
  try {
    auth.user = await authApi.changePassword({ newPassword: setupPassword.value })
    setupOpen.value = false
    await finishLogin()
  } catch (error) {
    setupError.value = error instanceof Error ? error.message : '密码设置失败'
  } finally {
    setupSaving.value = false
  }
}

function skipPasswordSetup() {
  setupOpen.value = false
  void finishLogin()
}
</script>

<template>
  <div class="login-page">
    <section class="login-shell">
      <!-- 品牌区：纯几何图形，不放文案 -->
      <aside class="brand-panel">
        <div class="brand">
          <img class="brand-mark" src="/logo.png" alt="" />
          <span class="brand-name">拾忆</span>
        </div>

        <div class="sketch" aria-hidden="true">
          <div class="sketch-card">
            <span class="sketch-line w45" />
            <span class="sketch-line w90" />
            <span class="sketch-line w70" />
            <div class="sketch-row">
              <span class="sketch-thumb" />
              <span class="sketch-pill" />
            </div>
          </div>
          <div class="sketch-card sketch-card--soft">
            <span class="sketch-line w60" />
            <span class="sketch-line w35" />
          </div>
        </div>
      </aside>

      <main class="form-panel">
        <div class="brand brand--mobile">
          <img class="brand-mark" src="/logo.png" alt="" />
          <span class="brand-name">拾忆</span>
        </div>

        <!-- 视觉上不显示，只留给读屏和文档结构用 -->
        <h1 class="visually-hidden">登录 / 注册</h1>

        <div class="mode-switch" role="tablist" aria-label="登录方式">
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'code'"
            :class="{ active: mode === 'code' }"
            @click="switchMode('code')"
          >
            验证码登录
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="mode === 'password'"
            :class="{ active: mode === 'password' }"
            @click="switchMode('password')"
          >
            密码登录
          </button>
        </div>

        <form class="login-form" novalidate @submit.prevent="submit">
          <div class="field">
            <label for="login-email">邮箱</label>
            <input
              id="login-email"
              ref="emailRef"
              v-model="email"
              type="email"
              name="email"
              autocomplete="email"
              inputmode="email"
              :aria-invalid="Boolean(emailError)"
              :aria-describedby="emailError ? 'login-email-error' : undefined"
              :disabled="submitting"
            />
            <p v-if="emailError" id="login-email-error" class="error" role="alert">{{ emailError }}</p>
          </div>

          <div v-if="mode === 'code'" class="field">
            <label for="login-code">验证码</label>
            <div class="code-row">
              <input
                id="login-code"
                v-model="code"
                type="text"
                name="code"
                autocomplete="one-time-code"
                inputmode="numeric"
                maxlength="8"
                :aria-invalid="Boolean(codeError)"
                :aria-describedby="codeError ? 'login-code-error' : undefined"
                :disabled="submitting"
              />
              <button type="button" class="ghost-btn" :disabled="!canSendCode" @click="sendCode">
                {{ sending ? '发送中' : countdown > 0 ? `${countdown}s` : '获取验证码' }}
              </button>
            </div>
            <p v-if="codeError" id="login-code-error" class="error" role="alert">{{ codeError }}</p>
          </div>

          <div v-else class="field">
            <label for="login-password">密码</label>
            <div class="password-wrap">
              <input
                id="login-password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                name="password"
                autocomplete="current-password"
                :aria-invalid="Boolean(passwordError)"
                :aria-describedby="passwordError ? 'login-password-error' : undefined"
                :disabled="submitting"
              />
              <button
                type="button"
                class="eye-btn"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                :aria-pressed="showPassword"
                @click="showPassword = !showPassword"
              >
                <el-icon :size="16"><Hide v-if="showPassword" /><View v-else /></el-icon>
              </button>
            </div>
            <p v-if="passwordError" id="login-password-error" class="error" role="alert">{{ passwordError }}</p>
          </div>

          <p v-if="formError" class="error form-error" role="alert">
            <span>{{ formError }}</span>
            <button v-if="mode === 'code'" type="button" class="link-btn" @click="sendCode">重新获取验证码</button>
            <button v-else type="button" class="link-btn" @click="switchMode('code')">用验证码登录</button>
          </p>

          <button type="submit" class="primary-btn" :disabled="submitting">
            {{ submitting ? '登录中…' : '登录' }}
          </button>
        </form>

        <div v-if="devCode" class="dev-hint" role="status">
          <el-icon><InfoFilled /></el-icon>
          <span>
            邮件服务未配置，验证码已自动填入：<strong>{{ devCode }}</strong>
          </span>
        </div>
      </main>
    </section>

    <el-dialog
      v-model="setupOpen"
      title="设置登录密码"
      width="min(440px, 92vw)"
      align-center
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="setup-form">
        <p class="setup-copy">设置后可直接用邮箱和密码登录，也可以现在跳过。</p>
        <div class="field">
          <label for="setup-password">新密码</label>
          <el-input
            id="setup-password"
            v-model="setupPassword"
            type="password"
            show-password
            maxlength="72"
            autocomplete="new-password"
            @keyup.enter="saveInitialPassword"
          />
        </div>
        <div class="field">
          <label for="setup-confirm">确认密码</label>
          <el-input
            id="setup-confirm"
            v-model="setupConfirm"
            type="password"
            show-password
            maxlength="72"
            autocomplete="new-password"
            @keyup.enter="saveInitialPassword"
          />
        </div>
        <p v-if="setupError" class="error" role="alert">{{ setupError }}</p>
      </div>

      <template #footer>
        <el-button :disabled="setupSaving" @click="skipPasswordSetup">跳过</el-button>
        <el-button type="primary" :loading="setupSaving" @click="saveInitialPassword">设置并进入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: var(--space-6) var(--space-4);
  background: var(--bg);
  background-image: radial-gradient(900px 520px at 12% -10%, rgba(217, 119, 6, 0.09), transparent 62%);
  /* 桌面端无边框窗口：这一页没有顶栏，整块背景兼作窗口拖动区 */
  -webkit-app-region: drag;
}

.login-shell {
  display: grid;
  -webkit-app-region: no-drag;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1fr);
  width: min(920px, 100%);
  /* 高度交给内容决定，标题去掉后卡片保持紧凑 */
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  animation: shell-in var(--dur-slow) var(--ease-out) both;
}

@keyframes shell-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------- 左侧品牌区 ---------- */

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-8);
  padding: clamp(24px, 4vw, 40px);
  background: #1b1917;
  color: #f2efe9;
  overflow: hidden;
}

/* 深色模式下卡片本身也是深的，把品牌区再压深一档并加一条分隔，避免两栏糊在一起 */
html.dark .brand-panel {
  background: #121110;
}

.brand-panel::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1px;
  background: rgba(255, 255, 255, 0.07);
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -90px;
  bottom: -120px;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(217, 119, 6, 0.55), transparent 66%);
  pointer-events: none;
}

.brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  object-fit: cover;
}

/* 深色品牌区里给图标加一圈微弱描边，避免和背景糊在一起 */
.brand-panel .brand-mark {
  width: 34px;
  height: 34px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16), 0 6px 16px rgba(0, 0, 0, 0.35);
}

.brand--mobile {
  display: none;
  margin-bottom: var(--space-6);
  color: var(--text);
}

.sketch {
  position: relative;
  z-index: 1;
  display: grid;
  gap: var(--space-4);
}

.sketch-card {
  padding: var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.055);
}

.sketch-card--soft {
  opacity: 0.65;
}

.sketch-line {
  display: block;
  height: 8px;
  margin-bottom: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
}

.sketch-line:last-child {
  margin-bottom: 0;
}

.w35 {
  width: 35%;
}

.w45 {
  width: 45%;
  background: rgba(255, 255, 255, 0.42);
}

.w60 {
  width: 60%;
}

.w70 {
  width: 70%;
}

.w90 {
  width: 90%;
}

.sketch-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.sketch-thumb {
  width: 62px;
  height: 46px;
  border-radius: 10px;
  background: linear-gradient(135deg, #d97706, #f0c07a);
}

.sketch-pill {
  flex: 1;
  height: 26px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
}

/* ---------- 右侧表单 ---------- */

.form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(28px, 5vw, 56px);
}

.mode-switch {
  display: inline-flex;
  align-self: flex-start;
  gap: 2px;
  margin-bottom: var(--space-6);
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--panel-2);
}

.mode-switch button {
  min-height: 34px;
  padding: 0 var(--space-4);
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-3);
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.mode-switch button:hover {
  color: var(--text-2);
}

.mode-switch button.active {
  background: var(--panel);
  color: var(--brand-hover);
  box-shadow: var(--shadow-sm);
}

.password-wrap {
  position: relative;
}

.password-wrap input {
  width: 100%;
  padding-right: 48px;
}

.eye-btn {
  position: absolute;
  top: 50%;
  right: 4px;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  transform: translateY(-50%);
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out);
}

.eye-btn:hover {
  background: var(--panel-hover);
  color: var(--text);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.field label {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-2);
}

.field input {
  min-height: 48px;
  padding: 0 var(--space-4);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--text);
  font-family: inherit;
  font-size: 16px;
  transition: border-color var(--dur) var(--ease-out), box-shadow var(--dur) var(--ease-out);
}

.field input::placeholder {
  color: var(--text-3);
}

.field input:hover:not(:disabled) {
  border-color: var(--text-3);
}

.field input:focus {
  border-color: var(--brand-fill);
  box-shadow: 0 0 0 4px var(--brand-soft);
  outline: none;
}

.field input[aria-invalid='true'] {
  border-color: var(--danger);
}

.field input:disabled {
  opacity: 0.6;
}

.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-3);
}

.ghost-btn {
  min-width: 118px;
  min-height: 48px;
  padding: 0 var(--space-4);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--panel-2);
  color: var(--text-2);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), color var(--dur) var(--ease-out),
    border-color var(--dur) var(--ease-out);
}

.ghost-btn:hover:not(:disabled) {
  border-color: var(--brand-fill);
  background: var(--brand-soft);
  color: var(--brand-hover);
}

.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.primary-btn {
  min-height: 50px;
  margin-top: var(--space-1);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--brand-fill);
  color: var(--on-brand-fill);
  font-family: inherit;
  font-size: 16px;
  font-weight: 650;
  cursor: pointer;
  transition: background var(--dur) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.primary-btn:hover:not(:disabled) {
  background: var(--brand-fill-hover);
}

.primary-btn:active:not(:disabled) {
  transform: scale(0.99);
}

.primary-btn:disabled {
  opacity: 0.65;
  cursor: progress;
}

.error {
  margin: 0;
  color: var(--danger);
  font-size: 13px;
  line-height: 1.5;
}

.form-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--danger);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--danger) 8%, transparent);
}

.link-btn {
  padding: 0;
  border: none;
  background: none;
  color: var(--brand);
  font-family: inherit;
  font-size: 13px;
  text-decoration: underline;
  cursor: pointer;
}

.dev-hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-5);
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel-2);
  color: var(--text-2);
  font-size: 13px;
}

.setup-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.setup-copy {
  margin: 0;
  color: var(--text-2);
  font-size: 13.5px;
}

@media (max-width: 860px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .brand--mobile {
    display: flex;
  }
}
</style>
