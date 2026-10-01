import { computed, ref } from 'vue'
import { friendApi } from '@/api'

/** 好友角标：待处理申请 + 未读聊天，模块级单例 */
const pendingRequests = ref(0)
const unreadChats = ref(0)
const total = computed(() => pendingRequests.value + unreadChats.value)

export function useFriendSummary() {
  async function refresh() {
    try {
      const summary = await friendApi.summary()
      pendingRequests.value = Math.max(0, summary.pendingRequests)
      unreadChats.value = Math.max(0, summary.unreadChats)
    } catch {
      /* 拿不到数量就不显示角标 */
    }
  }

  function setPending(value: number) {
    pendingRequests.value = Math.max(0, value)
  }

  function setUnreadChats(value: number) {
    unreadChats.value = Math.max(0, value)
  }

  return { pendingRequests, unreadChats, total, refresh, setPending, setUnreadChats }
}
