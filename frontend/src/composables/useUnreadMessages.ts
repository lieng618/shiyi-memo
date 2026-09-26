import { ref } from 'vue'
import { messageApi } from '@/api'

/** 未读站内信数量：顶部铃铛角标用它，模块级单例 */
const unread = ref(0)

export function useUnreadMessages() {
  async function refresh() {
    try {
      unread.value = await messageApi.unreadCount()
    } catch {
      /* 拿不到未读数就不显示角标 */
    }
  }

  function set(count: number) {
    unread.value = Math.max(0, count)
  }

  return { unread, refresh, set }
}
