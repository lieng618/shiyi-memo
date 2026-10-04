<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../endpoints'
import type { AdminUploadFile } from '../types'
import { formatChatFileSize } from '@/utils/chatFile'
import UserPicker from './UserPicker.vue'

// wangEditor 体积大，等第一次打开发信弹窗再加载，不拖慢后台首屏
const MessageEditor = defineAsyncComponent(() => import('./MessageEditor.vue'))

const props = defineProps<{ modelValue: boolean; userId?: number | null; userLabel?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; sent: [] }>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

type RecipientMode = 'single' | 'all'
const recipientMode = ref<RecipientMode>('single')
const userId = ref<number | null>(null)
const title = ref('')
const contentHtml = ref('<p><br></p>')
const userCount = ref<number | null>(null)
const sending = ref(false)
const errorMessage = ref('')

watch(
  () => props.modelValue,
  (value) => {
    if (!value) return
    userId.value = props.userId ?? null
    recipientMode.value = 'single'
    title.value = ''
    contentHtml.value = '<p><br></p>'
    errorMessage.value = ''
    userCount.value = null
    // 全体用户模式要提示将发给多少人
    adminApi
      .stats()
      .then((stats) => (userCount.value = stats.userCount))
      .catch(() => {})
  },
)

/** 上传返回的 url 是用户侧前缀；编辑器和后台预览要走 /api/admin 前缀，发送时后端会统一改写 */
function adminFileUrl(asset: AdminUploadFile, action: 'raw' | 'download') {
  return `/api/admin/files/${asset.id}/${action}`
}

async function onUploadImage(file: File, onProgress: (percent: number) => void) {
  const asset = await adminApi.uploadAttachment(file, onProgress)
  return { url: adminFileUrl(asset, 'raw') }
}

async function onUploadAttachment(file: File, onProgress: (percent: number) => void) {
  const asset = await adminApi.uploadAttachment(file, onProgress)
  return {
    url: adminFileUrl(asset, 'download'),
    name: `${asset.originalName}（${formatChatFileSize(asset.size)}）`,
  }
}

async function send() {
  errorMessage.value = ''
  if (recipientMode.value === 'single' && !userId.value) {
    errorMessage.value = '请选择要发送的用户'
    return
  }
  if (!title.value.trim()) {
    errorMessage.value = '标题要填'
    return
  }
  if (isEmptyRichText(contentHtml.value)) {
    errorMessage.value = '正文不能为空'
    return
  }
  if (recipientMode.value === 'all') {
    const count = userCount.value ?? 0
    try {
      await ElMessageBox.confirm(`将发给全体 ${count} 位用户，每人一条站内信。确认发送？`, '发送全服信', {
        confirmButtonText: '发送',
        cancelButtonText: '再想想',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  sending.value = true
  try {
    if (recipientMode.value === 'all') {
      await adminApi.broadcast({ title: title.value.trim(), contentHtml: contentHtml.value })
      ElMessage.success('全服信已发送')
    } else {
      await adminApi.sendMessage({
        userId: userId.value!,
        title: title.value.trim(),
        contentHtml: contentHtml.value,
      })
      ElMessage.success('已发送站内信')
    }
    emit('sent')
    open.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '发送失败'
  } finally {
    sending.value = false
  }
}

// 判空逻辑与 MessageEditor 导出的是同一份；这里复制一份小函数，
// 避免为了它把整个 wangEditor 模块图拉进弹窗入口的 chunk，破坏懒加载
function isEmptyRichText(html: string): boolean {
  const text = html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
  return text.length === 0 && !/<img|<video|<audio/i.test(html)
}
</script>

<template>
  <el-dialog v-model="open" title="发送站内信" width="min(720px, 94vw)" align-center>
    <div class="msg-form">
      <div class="field">
        <label>收件方式</label>
        <el-radio-group v-model="recipientMode" :disabled="Boolean(props.userId)">
          <el-radio-button value="single">发给单个用户</el-radio-button>
          <el-radio-button value="all">全体用户（全服信）</el-radio-button>
        </el-radio-group>
        <p v-if="recipientMode === 'all'" class="hint muted tnum">
          将发送给全体 {{ userCount ?? '…' }} 位正常状态用户，每人一条。
        </p>
      </div>

      <div v-if="recipientMode === 'single'" class="field">
        <label for="msg-user">收件用户</label>
        <UserPicker id="msg-user" v-model="userId" />
        <p v-if="userLabel" class="hint muted">当前指定：{{ userLabel }}</p>
      </div>

      <div class="field">
        <label for="msg-title">标题</label>
        <el-input id="msg-title" v-model="title" maxlength="128" placeholder="例如：内容提醒" />
      </div>

      <div class="field">
        <label>正文</label>
        <MessageEditor
          v-model="contentHtml"
          :upload-image="onUploadImage"
          :upload-attachment="onUploadAttachment"
        />
        <p class="hint muted">图片将直接嵌入正文，其余文件以附件形式插入，收件人可在线预览或下载。</p>
      </div>

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    </div>

    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="sending" @click="send">
        {{ recipientMode === 'all' ? '发送全服信' : '发送' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.msg-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
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

.hint,
.error {
  margin: 0;
  font-size: 12.5px;
}

.error {
  color: var(--danger);
}
</style>
