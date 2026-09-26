<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../endpoints'
import UserPicker from './UserPicker.vue'

const props = defineProps<{ modelValue: boolean; userId?: number | null; userLabel?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; sent: [] }>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const userId = ref<number | null>(null)
const title = ref('')
const content = ref('')
const sending = ref(false)
const errorMessage = ref('')

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      userId.value = props.userId ?? null
      title.value = ''
      content.value = ''
      errorMessage.value = ''
    }
  },
)

async function send() {
  errorMessage.value = ''
  if (!userId.value) {
    errorMessage.value = '请选择要发送的用户'
    return
  }
  if (!title.value.trim() || !content.value.trim()) {
    errorMessage.value = '标题和内容都要填'
    return
  }
  sending.value = true
  try {
    await adminApi.sendMessage({ userId: userId.value, title: title.value.trim(), content: content.value.trim() })
    ElMessage.success('已发送站内信')
    emit('sent')
    open.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '发送失败'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="发送站内信" width="min(520px, 92vw)" align-center>
    <div class="msg-form">
      <div class="field">
        <label for="msg-user">收件用户</label>
        <UserPicker id="msg-user" v-model="userId" />
        <p v-if="userLabel" class="hint muted">当前指定：{{ userLabel }}</p>
      </div>
      <div class="field">
        <label for="msg-title">标题</label>
        <el-input id="msg-title" v-model="title" maxlength="128" placeholder="例如：内容提醒" />
      </div>
      <div class="field">
        <label for="msg-content">内容</label>
        <el-input
          id="msg-content"
          v-model="content"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="站内信内容"
        />
      </div>
      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    </div>

    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
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
