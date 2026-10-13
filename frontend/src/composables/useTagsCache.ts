import { ref } from 'vue'
import { tagApi } from '@/api'
import type { Tag } from '@/types'

/**
 * 标签缓存（模块级单例，和 useNotesFilter 一个套路）。
 *
 * 标签是「读得很多、变得很少」的数据：列表页要拿它画筛选项，详情页要拿它做
 * 自动补全，而自动保存每次都会顺手读一遍。以前每处挂载都直接打接口，
 * 现在并发调用共用同一次请求，短时间内复用同一份结果。
 */
const tags = ref<Tag[]>([])
let fetchedAt = 0
let inflight: Promise<Tag[]> | null = null

/** 缓存的新鲜期：超过这个时间再挂载会重新拉一次 */
const FRESH_MS = 60_000

export function useTagsCache() {
  /** 强制拉一次；并发调用共用同一个请求 */
  function refresh(): Promise<Tag[]> {
    if (inflight) {
      return inflight
    }
    inflight = tagApi
      .list()
      .then((list) => {
        tags.value = list
        fetchedAt = Date.now()
        return list
      })
      .finally(() => {
        inflight = null
      })
    return inflight
  }

  /** 有新鲜缓存就直接给，否则拉一次 */
  function load(): Promise<Tag[]> {
    if (fetchedAt > 0 && Date.now() - fetchedAt < FRESH_MS) {
      return Promise.resolve(tags.value)
    }
    return refresh()
  }

  /** 标签被增删改之后调用（比如保存笔记时新建了标签），下次读取会重新拉 */
  function invalidate() {
    fetchedAt = 0
  }

  /** 换账号 / 退出登录：连内容一起丢掉，别让别人的标签闪一下 */
  function clear() {
    tags.value = []
    fetchedAt = 0
  }

  return { tags, load, refresh, invalidate, clear }
}
