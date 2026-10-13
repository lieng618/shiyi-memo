import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { memoApi } from '@/api'

/**
 * 编辑页是懒加载路由，整块 wangEditor（约 790 KB）都挂在它的 chunk 里，
 * 首次打开要现下载现初始化，肉眼看是卡一两秒。
 *
 * 点开笔记和「新建笔记」用的是同一块代码，所以预取两边都受益：
 * - 列表页闲下来后自动预取（原来的策略）；
 * - 鼠标悬停 / 键盘聚焦「新建笔记」或某条笔记时立刻预取，
 *   覆盖「进列表很快就点」的情况。
 *
 * 一个会话里只会真正下载一次；省流量模式或 2G 这类弱网直接跳过——
 * 这种网络下的用户宁可点下去多等一会儿，也不希望页面在后台偷跑流量。
 */

/** Network Information API：Chrome / Edge 支持，Safari / Firefox 还没有 */
interface NetworkInformationLike {
  saveData?: boolean
  effectiveType?: string
}

let prefetched = false
let scheduled = false

function shouldSkipPrefetch(): boolean {
  const connection = (navigator as Navigator & { connection?: NetworkInformationLike }).connection
  if (!connection) {
    return false
  }
  return Boolean(connection.saveData)
    || connection.effectiveType === 'slow-2g'
    || connection.effectiveType === '2g'
}

/** 立刻预取编辑页代码；已经预取过就直接返回 */
export function prefetchNoteEditor() {
  if (prefetched || shouldSkipPrefetch()) {
    return
  }
  prefetched = true
  void import('@/views/NoteDetailView.vue').catch(() => {
    // 预取失败不影响正常流程：真要点开时还会再走一次普通的懒加载
    prefetched = false
  })
}

function scheduleIdlePrefetch() {
  if (scheduled || prefetched || shouldSkipPrefetch()) {
    return
  }
  scheduled = true
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(prefetchNoteEditor, { timeout: 3000 })
  } else {
    window.setTimeout(prefetchNoteEditor, 1200)
  }
}

/** 新建一条空笔记并直接进入编辑页 */
export function useCreateNote() {
  const router = useRouter()
  scheduleIdlePrefetch()
  return async function createNote() {
    const created = await memoApi.create({ title: '', contentHtml: '' })
    ElMessage.success('已新建笔记')
    await router.push(`/notes/${created.id}`)
    return created
  }
}
