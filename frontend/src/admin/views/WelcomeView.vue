<script setup lang="ts">
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../endpoints'
import type { AdminWelcomeLetter } from '../types'
import { useAdminUploads } from '../useAdminUploads'
import { formatFull } from '@/utils/format'

// 和发信弹窗一样，等这一页真正渲染时再加载 wangEditor，不拖慢后台首屏
const MessageEditor = defineAsyncComponent(() => import('../components/MessageEditor.vue'))

const { onUploadImage, onUploadAttachment } = useAdminUploads()

const loading = ref(false)
const saving = ref(false)
const enabled = ref(false)
const title = ref('')
const contentHtml = ref('<p><br></p>')
const updatedBy = ref<string | null>(null)
const updatedAt = ref<string | null>(null)
const errorMessage = ref('')

onMounted(() => void load())

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    apply(await adminApi.welcomeLetter())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '读取欢迎信配置失败'
  } finally {
    loading.value = false
  }
}

function apply(data: AdminWelcomeLetter) {
  enabled.value = data.enabled
  title.value = data.title ?? ''
  contentHtml.value = data.contentHtml || '<p><br></p>'
  updatedBy.value = data.updatedBy ?? null
  updatedAt.value = data.updatedAt ?? null
}

async function save() {
  errorMessage.value = ''
  // 关闭状态允许留空当草稿，开启前必须填全，否则用户收到的会是一封空信
  if (enabled.value && !title.value.trim()) {
    errorMessage.value = '先填标题'
    return
  }
  if (enabled.value && isBlankRichText(contentHtml.value)) {
    errorMessage.value = '先填正文'
    return
  }
  saving.value = true
  try {
    apply(
      await adminApi.updateWelcomeLetter({
        enabled: enabled.value,
        title: title.value.trim(),
        contentHtml: contentHtml.value,
      }),
    )
    ElMessage.success(enabled.value ? '已保存，新注册用户会收到' : '已保存，未开启')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

/** 富文本判空：只有空行也算空，但插了图 / 视频 / 音频不算 */
function isBlankRichText(html: string): boolean {
  const text = html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim()
  return text.length === 0 && !/<img|<video|<audio/i.test(html)
}
</script>

<template>
  <div v-loading="loading">
    <div class="adm-toolbar">
      <el-tag :type="enabled ? 'success' : 'info'" effect="plain">
        {{ enabled ? '已开启' : '已关闭' }}
      </el-tag>
      <span class="muted">
        {{ enabled ? '新注册用户会收到' : '未开启，不会发送' }}
      </span>
      <div style="flex: 1" />
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </div>

    <div class="adm-card">
      <div class="adm-form">
        <div class="field">
          <label>欢迎信开关</label>
          <el-switch v-model="enabled" active-text="开启" inactive-text="关闭" />
        </div>

        <div class="field">
          <label for="welcome-title">标题</label>
          <el-input id="welcome-title" v-model="title" maxlength="128" placeholder="例如：欢迎使用拾忆" />
        </div>

        <div class="field">
          <label>正文</label>
          <MessageEditor
            v-model="contentHtml"
            :upload-image="onUploadImage"
            :upload-attachment="onUploadAttachment"
          />
          <p class="hint muted">
            支持 <code>{nickname}</code>、<code>{email}</code> 占位符
          </p>
        </div>

        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

        <p v-if="updatedAt" class="hint muted tnum">
          最近更新：{{ formatFull(updatedAt) }}{{ updatedBy ? ` · ${updatedBy}` : '' }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.adm-form {
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

.hint,
.error {
  margin: 0;
  font-size: 12.5px;
}

.error {
  color: var(--danger);
}

code {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--panel-2);
  font-size: 12px;
}
</style>
