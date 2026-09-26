<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { onUnauthorized } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(() => {
  onUnauthorized.handler = () => {
    if (auth.isLoggedIn) {
      ElMessage.warning('登录已过期，请重新登录')
    }
    auth.clear()
    void router.replace({ name: 'login' })
  }
})
</script>

<template>
  <!-- 统一 Element Plus 的中文文案（下拉空状态、弹窗按钮等） -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
