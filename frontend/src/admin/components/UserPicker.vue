<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '../endpoints'

defineProps<{ modelValue: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [number | null] }>()

interface Option {
  id: number
  email: string
  nickname: string
}

const options = ref<Option[]>([])
const loading = ref(false)

async function search(keyword: string) {
  loading.value = true
  try {
    const result = await adminApi.users({ keyword: keyword.trim() || undefined, page: 1, size: 20 })
    options.value = result.records.map((user) => ({ id: user.id, email: user.email, nickname: user.nickname }))
  } finally {
    loading.value = false
  }
}

onMounted(() => void search(''))
</script>

<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    clearable
    reserve-keyword
    :remote-method="search"
    :loading="loading"
    placeholder="搜索邮箱或昵称"
    aria-label="选择用户"
    @change="(value: number | null) => emit('update:modelValue', value)"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="`${item.nickname}（${item.email}）`"
      :value="item.id"
    />
  </el-select>
</template>
