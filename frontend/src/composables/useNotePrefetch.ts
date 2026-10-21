import { memoApi } from '@/api'
import { ApiError } from '@/api/http'
import { useNotesCache } from '@/composables/useNotesCache'
import type { MemoDetail } from '@/types'

const SUMMARY_PAGE_SIZE = 100
const DETAIL_CONCURRENCY = 2
const FULL_PREFETCH_FRESH_MS = 60_000

let running: Promise<void> | null = null
let paused = false
let visibilityListening = false
let fullPrefetchAt = 0
const detailRequests = new Map<number, Promise<MemoDetail>>()

interface NetworkInformationLike {
  saveData?: boolean
  effectiveType?: string
}

function shouldSkipForNetwork() {
  const connection = (navigator as Navigator & { connection?: NetworkInformationLike }).connection
  return Boolean(connection?.saveData)
    || connection?.effectiveType === 'slow-2g'
    || connection?.effectiveType === '2g'
}

function waitForIdle() {
  return new Promise<void>((resolve) => {
    if (typeof window.requestIdleCallback === 'function') {
      window.requestIdleCallback(() => resolve(), { timeout: 500 })
    } else {
      window.setTimeout(resolve, 16)
    }
  })
}

function rememberPrefetchedDetail(detail: MemoDetail) {
  const cache = useNotesCache()
  const existing = cache.detailOf(detail.id)
  // 正在查看的详情可能有更新的编辑租约，旧请求不能把租约状态盖回去。
  if (existing && (existing.version ?? 0) > (detail.version ?? 0)) return
  cache.rememberDetail(existing?.editLease.heldByMe
    ? { ...detail, editLease: existing.editLease }
    : detail)
}

/**
 * 共用正在进行的详情请求：悬停预取和真正打开页面不会为同一篇笔记重复请求。
 */
export function requestNoteDetail(id: number, silent = true) {
  const existing = detailRequests.get(id)
  if (existing) return existing
  const request = memoApi.get(id, { silent })
    .then((detail) => {
      rememberPrefetchedDetail(detail)
      return detail
    })
    .finally(() => {
      detailRequests.delete(id)
    })
  detailRequests.set(id, request)
  return request
}

export function prefetchNoteDetail(id: number) {
  if (useNotesCache().detailOf(id)) return Promise.resolve()
  return requestNoteDetail(id, true).then(() => undefined).catch(() => {
    // 悬停预取失败不打扰用户，点击时还会重新请求。
  })
}

async function prefetchDetails(ids: number[]) {
  if (!ids.length) return
  const cache = useNotesCache()
  let nextIndex = 0

  async function worker() {
    while (!paused && !document.hidden) {
      const index = nextIndex++
      if (index >= ids.length) return
      const id = ids[index]
      if (cache.detailOf(id)) continue
      await waitForIdle()
      if (paused || document.hidden) return
      try {
        await requestNoteDetail(id, true)
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          paused = true
          return
        }
        // 单篇失败不影响继续预取其他笔记。
      }
    }
  }

  await Promise.all(Array.from(
    { length: Math.min(DETAIL_CONCURRENCY, ids.length) },
    () => worker(),
  ))
}

async function runPrefetch(): Promise<boolean> {
  if (shouldSkipForNetwork()) return false
  let page = 1
  while (!paused && !document.hidden) {
    let result
    try {
      result = await memoApi.list({ page, size: SUMMARY_PAGE_SIZE }, { silent: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) paused = true
      return false
    }
    if (paused || document.hidden) return false
    await prefetchDetails(result.records.map((memo) => memo.id))
    if (paused || document.hidden) return false
    if (!result.records.length || page * SUMMARY_PAGE_SIZE >= result.total) return true
    page += 1
  }
  return false
}

/**
 * 在列表页空闲时按“最新笔记优先”静默预取所有详情。
 * 每页最多 100 条，正文请求只开两个并发，避免把 CPU 和网络瞬间打满。
 */
export function startNotePrefetch() {
  paused = false
  if (!visibilityListening) {
    visibilityListening = true
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && !paused) {
        void startNotePrefetch()
      }
    })
  }
  if (Date.now() - fullPrefetchAt < FULL_PREFETCH_FRESH_MS) {
    return Promise.resolve()
  }
  if (running) return running
  running = runPrefetch()
    .then((complete) => {
      if (complete) fullPrefetchAt = Date.now()
    })
    .catch(() => {
      // 后台预取失败不打扰用户，真正打开时还会正常请求。
    })
    .finally(() => {
      running = null
    })
  return running
}

export function pauseNotePrefetch() {
  paused = true
}
