<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { onUnauthorized } from '@/api/http'
import { onRealtime, startRealtime, stopRealtime } from '@/composables/useRealtime'
import { useFriendSummary } from '@/composables/useFriendSummary'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const { refresh: refreshFriendSummary } = useFriendSummary()

let offChat: (() => void) | undefined
let offFriends: (() => void) | undefined

onMounted(() => {
  offChat = onRealtime('chat', () => void refreshFriendSummary())
  offFriends = onRealtime('friends', () => void refreshFriendSummary())
  onUnauthorized.handler = () => {
    if (auth.isLoggedIn) {
      ElMessage.warning('登录已过期，请重新登录')
    }
    auth.clear()
    void router.replace({ name: 'login' })
  }
})

watch(() => auth.isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    startRealtime()
    void refreshFriendSummary()
  } else {
    stopRealtime()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  offChat?.()
  offFriends?.()
  stopRealtime()
})
</script>

<template>
  <!-- 统一 Element Plus 的中文文案（下拉空状态、弹窗按钮等） -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
