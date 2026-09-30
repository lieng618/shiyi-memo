<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, SwitchButton, Upload } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { authApi, backupApi, fileApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { formatFull, formatSize } from '@/utils/format'
import { downloadBlob } from '@/utils/download'
import type { Quota } from '@/types'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const auth = useAuthStore()
const router = useRouter()
const { isDark, setDark } = useTheme()

const nickname = ref('')
const nicknameError = ref('')
const saving = ref(false)
const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const quota = ref<Quota | null>(null)

// ---- 登录密码 ----
const pwdOpen = ref(false)
const pwdSaving = ref(false)
const pwdError = ref('')
const pwdForm = reactive({ newPassword: '', confirm: '' })

onMounted(async () => {
  nickname.value = auth.user?.nickname ?? ''
  try {
    quota.value = await userApi.quota()
  } catch {
    /* 配额只是展示信息 */
  }
})

async function refreshQuota() {
  try {
    quota.value = await userApi.quota()
  } catch {
    /* 忽略 */
  }
}

function pickAvatar() {
  avatarInput.value?.click()
}

async function onAvatarPicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('头像需要是图片文件')
    return
  }
  uploadingAvatar.value = true
  try {
    const asset = await fileApi.upload(file)
    auth.user = await userApi.updateAvatar(asset.id)
    ElMessage.success('头像已更新')
    await refreshQuota()
  } finally {
    uploadingAvatar.value = false
  }
}

async function resetAvatar() {
  uploadingAvatar.value = true
  try {
    auth.user = await userApi.updateAvatar(null)
    ElMessage.success('已恢复默认头像')
  } finally {
    uploadingAvatar.value = false
  }
}

async function saveNickname() {
  nicknameError.value = ''
  const value = nickname.value.trim()
  if (!value) {
    nicknameError.value = '昵称不能为空'
    return
  }
  if (value.length > 64) {
    nicknameError.value = '昵称最多 64 个字'
    return
  }
  saving.value = true
  try {
    auth.user = await userApi.updateProfile(value)
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

// ---------------- 登录密码 ----------------

function openPasswordDialog() {
  pwdForm.newPassword = ''
  pwdForm.confirm = ''
  pwdError.value = ''
  pwdOpen.value = true
}

async function savePassword() {
  pwdError.value = ''
  const hasPassword = Boolean(auth.user?.hasPassword)

  if (pwdForm.newPassword.length < 8) {
    pwdError.value = '新密码至少 8 位'
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    pwdError.value = '两次输入的新密码不一致'
    return
  }

  pwdSaving.value = true
  try {
    auth.user = await authApi.changePassword({
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success(hasPassword ? '密码已修改' : '密码已设置')
    pwdOpen.value = false
  } catch (error) {
    pwdError.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    pwdSaving.value = false
  }
}

// ---------------- 备份与恢复 ----------------

const backingUp = ref(false)
const restoring = ref(false)
const restoreInput = ref<HTMLInputElement | null>(null)

function pickRestore() {
  restoreInput.value?.click()
}

async function exportBackup() {
  if (backingUp.value) return
  try {
    await ElMessageBox.confirm(
      '将把全部笔记和附件打包成一个 zip 备份文件，是否继续？',
      '导出备份',
      { confirmButtonText: '导出', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  backingUp.value = true
  try {
    const blob = await backupApi.export()
    downloadBlob(`shiyi-backup-${dayjs().format('YYYYMMDD-HHmmss')}.zip`, blob)
    ElMessage.success('备份已导出')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '备份导出失败')
  } finally {
    backingUp.value = false
  }
}

async function onRestorePicked(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.warning('备份文件需要是 .zip')
    return
  }
  try {
    await ElMessageBox.confirm(
      '导入会把备份里的笔记新增到当前账号，不会覆盖或删除现有内容；与现有内容相同的笔记不会导入。是否继续？',
      '导入备份',
      { confirmButtonText: '开始导入', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  restoring.value = true
  try {
    const result = await backupApi.importBackup(file)
    const parts = [`已导入 ${result.memoCount} 条笔记、${result.attachmentCount} 个附件`]
    if (result.skippedMemos > 0) {
      parts.push(`跳过 ${result.skippedMemos} 条相同笔记`)
    }
    if (result.skippedAttachments > 0) {
      parts.push(`${result.skippedAttachments} 个附件已跳过`)
    }
    ElMessage.success(parts.join('，'))
    await refreshQuota()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败，请检查备份文件')
  } finally {
    restoring.value = false
  }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="profile-page">
    <PageHeader title="个人中心" back-to="/notes" back-label="返回笔记列表" />

    <div class="profile-body">
      <div class="profile-inner">
        <section class="card avatar-card">
          <button
            type="button"
            class="avatar-button"
            :aria-label="auth.user?.avatarUrl ? '更换头像' : '上传头像'"
            :disabled="uploadingAvatar"
            @click="pickAvatar"
          >
            <UserAvatar :user="auth.user" :size="96" />
            <span class="avatar-overlay">{{ uploadingAvatar ? '上传中…' : '更换' }}</span>
          </button>
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPicked" />

          <div class="avatar-info">
            <p class="nickname">{{ auth.user?.nickname }}</p>
            <p class="muted email">{{ auth.user?.email }}</p>
            <!-- 上传按钮是键盘/触屏都能用的替代方案，不依赖拖拽 -->
            <div class="avatar-actions">
              <el-button size="small" :loading="uploadingAvatar" @click="pickAvatar">更换头像</el-button>
              <el-button v-if="auth.user?.avatarUrl" size="small" text @click="resetAvatar">恢复默认</el-button>
            </div>
          </div>
        </section>

        <section class="card">
          <h2>昵称</h2>
          <div class="field">
            <div class="row">
              <input
                id="profile-nickname"
                v-model="nickname"
                type="text"
                maxlength="64"
                aria-label="昵称"
                :aria-invalid="Boolean(nicknameError)"
                :aria-describedby="nicknameError ? 'profile-nickname-error' : undefined"
              />
              <el-button type="primary" :loading="saving" @click="saveNickname">保存</el-button>
            </div>
            <p v-if="nicknameError" id="profile-nickname-error" class="error" role="alert">{{ nicknameError }}</p>
          </div>
        </section>

        <section class="card">
          <h2>登录密码</h2>
          <div class="row row--split">
            <span class="password-state">
              <el-tag v-if="auth.user?.hasPassword" type="success" size="small" effect="plain">已设置</el-tag>
              <span v-else class="muted">还没有设置密码</span>
            </span>
            <el-button :type="auth.user?.hasPassword ? 'default' : 'primary'" @click="openPasswordDialog">
              {{ auth.user?.hasPassword ? '修改密码' : '设置密码' }}
            </el-button>
          </div>
        </section>

        <section class="card">
          <h2>空间用量</h2>
          <template v-if="quota">
            <p class="quota-line tnum">
              当前等级 <strong>{{ quota.levelName }}</strong> · 已用 {{ formatSize(quota.used) }} /
              {{ formatSize(quota.maxStorage) }}
            </p>
            <el-progress
              :percentage="quota.usedPercent"
              :stroke-width="8"
              :color="quota.usedPercent > 90 ? 'var(--danger-fill)' : 'var(--brand-fill)'"
            />
          </template>
          <p v-else class="muted">用量加载失败。</p>
        </section>

        <section class="card">
          <h2>备份与恢复</h2>
          <div class="backup-actions">
            <el-button :loading="backingUp" @click="exportBackup">
              <el-icon><Download /></el-icon><span>导出备份</span>
            </el-button>
            <el-button :loading="restoring" @click="pickRestore">
              <el-icon><Upload /></el-icon><span>导入备份</span>
            </el-button>
            <input ref="restoreInput" type="file" accept=".zip,application/zip" hidden @change="onRestorePicked" />
          </div>
        </section>

        <section class="card">
          <h2 id="appearance-title">外观</h2>
          <div class="field">
            <el-radio-group
              :model-value="isDark"
              aria-labelledby="appearance-title"
              @change="(value: string | number | boolean | undefined) => setDark(Boolean(value))"
            >
              <el-radio-button :value="false">浅色</el-radio-button>
              <el-radio-button :value="true">深色</el-radio-button>
            </el-radio-group>
          </div>
        </section>

        <section class="card">
          <h2>账号</h2>
          <p class="hint muted tnum">
            注册于 {{ formatFull(auth.user?.createdAt) }} · 上次登录 {{ formatFull(auth.user?.lastLoginAt) }}
          </p>
          <el-button type="danger" plain @click="logout">
            <el-icon><SwitchButton /></el-icon><span>退出登录</span>
          </el-button>
        </section>
      </div>
    </div>

    <el-dialog
      v-model="pwdOpen"
      :title="auth.user?.hasPassword ? '修改密码' : '设置密码'"
      width="min(460px, 92vw)"
      align-center
    >
      <div class="pwd-form">
        <div class="field">
          <label for="pwd-new">新密码</label>
          <el-input
            id="pwd-new"
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            maxlength="72"
            placeholder="至少 8 位"
            autocomplete="new-password"
          />
        </div>

        <div class="field">
          <label for="pwd-confirm">确认新密码</label>
          <el-input
            id="pwd-confirm"
            v-model="pwdForm.confirm"
            type="password"
            show-password
            maxlength="72"
            autocomplete="new-password"
          />
        </div>

        <p v-if="pwdError" class="error" role="alert">{{ pwdError }}</p>
      </div>

      <template #footer>
        <el-button @click="pwdOpen = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="savePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.profile-body {
  flex: 1;
  overflow-y: auto;
}

.profile-inner {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 640px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-12);
}

.card {
  padding: var(--space-5);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.card h2 {
  margin: 0 0 var(--space-4);
  font-size: 15px;
  font-weight: 650;
}

.avatar-card {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.avatar-button {
  position: relative;
  display: grid;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.avatar-overlay {
  position: absolute;
  bottom: -6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--text);
  color: var(--panel);
  font-size: 11.5px;
  opacity: 0;
  transition: opacity var(--dur) var(--ease-out);
}

.avatar-button:hover .avatar-overlay,
.avatar-button:focus-visible .avatar-overlay {
  opacity: 1;
}

.avatar-info {
  min-width: 0;
}

.nickname {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.email {
  margin: 2px 0 var(--space-3);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.avatar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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
  min-height: 40px;
  padding: 0 var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--text);
  font-family: inherit;
  font-size: 15px;
}

.field input:focus {
  border-color: var(--brand-fill);
  box-shadow: 0 0 0 3px var(--brand-soft);
  outline: none;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.row--split {
  justify-content: space-between;
}

.password-state {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 13.5px;
}

.pwd-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.row input {
  flex: 1;
  min-width: 180px;
}

.hint,
.error {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.error {
  color: var(--danger);
  margin-top: var(--space-2);
}

.quota-line {
  margin: 0 0 var(--space-3);
  font-size: 14px;
}

.backup-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
