import { ref } from 'vue'
import type { MemoDetail, MemoSummary } from '@/types'

/**
 * 笔记数据的本地缓存（模块级单例）。
 *
 * 列表页原来每次挂载都重新请求：从详情页返回会丢掉已加载的分页，滚动位置也归零。
 * 这里把「上次画出来的那一屏」留在内存里，回到列表就先原样画出来，
 * 再后台静默刷新（refreshQuiet）。
 */

/** 快照对应的筛选条件（关键词 + 标签），条件变了就不能复用 */
const snapshotKey = ref('')
let hasSnapshot = false

const records = ref<MemoSummary[]>([])
const total = ref(0)
const hasAnyNotes = ref(false)

/**
 * 列表页离开时的滚动位置。列表页整体跟着窗口滚动，
 * 所以这里记的是 window.scrollY，由路由的 scrollBehavior 恢复。
 */
let scrollTop = 0

/** 详情缓存：进详情页先用它渲染，接口结果回来再覆盖 */
const details = new Map<number, MemoDetail>()

export function useNotesCache() {
  /** 筛选条件指纹：关键词 + 排序后的标签 id */
  function keyOf(keyword: string, tagIds: number[]): string {
    return `${keyword.trim()}|${[...tagIds].sort((a, b) => a - b).join(',')}`
  }

  /** 手上这份缓存是不是当前筛选条件对应的 */
  function matches(key: string): boolean {
    return hasSnapshot && snapshotKey.value === key
  }

  function remember(key: string, list: MemoSummary[], count: number, anyNotes: boolean) {
    if (snapshotKey.value !== key) {
      // 换了筛选条件，滚动位置就不该沿用了
      scrollTop = 0
    }
    snapshotKey.value = key
    hasSnapshot = true
    records.value = list
    total.value = count
    hasAnyNotes.value = anyNotes
  }

  /** 整体作废：换账号、退出登录时用，避免把上一个人的内容留在内存里 */
  function clear() {
    hasSnapshot = false
    snapshotKey.value = ''
    records.value = []
    total.value = 0
    hasAnyNotes.value = false
    scrollTop = 0
    details.clear()
  }

  function detailOf(id: number): MemoDetail | undefined {
    return details.get(id)
  }

  function rememberDetail(detail: MemoDetail) {
    details.set(detail.id, detail)
  }

  function forgetDetail(id: number) {
    details.delete(id)
  }

  return {
    records,
    total,
    hasAnyNotes,
    keyOf,
    matches,
    remember,
    clear,
    getScrollTop: () => scrollTop,
    setScrollTop: (value: number) => {
      scrollTop = value
    },
    detailOf,
    rememberDetail,
    forgetDetail,
  }
}
