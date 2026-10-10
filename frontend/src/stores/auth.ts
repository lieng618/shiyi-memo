import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const initialized = ref(false)
  const loading = ref(false)

  const isLoggedIn = computed(() => user.value !== null)

  async function fetchMe() {
    loading.value = true
    try {
      user.value = await authApi.me()
    } catch {
      user.value = null
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  async function login(email: string, code: string) {
    const result = await authApi.login(email, code)
    user.value = result.user
    initialized.value = true
    return result.user
  }

  /**
   * 退出登录：本地状态先清掉，界面立刻回到未登录。
   * 服务端清 Cookie 只是「顺手通知」，断网或服务端异常都不能把用户卡在已经退出的页面上。
   */
  function logout() {
    user.value = null
    void authApi.logout().catch(() => {
      ElMessage.warning('网络异常，未能通知服务器退出登录')
    })
  }

  function clear() {
    user.value = null
  }

  return { user, loading, initialized, isLoggedIn, fetchMe, login, logout, clear }
})
