import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { memoApi } from '@/api'

/**
 * 编辑页是懒加载路由，整块 wangEditor 都挂在它的 chunk 里，首次点「新建笔记」
 * 要现下载现初始化，肉眼看是卡一两秒。列表页空闲时把这块代码预取下来，
 * 点击时就只剩一次建库请求加编辑器渲染。
 */
function prefetchDetailPage() {
  const warm = () => void import('@/views/NoteDetailView.vue')
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(warm, { timeout: 3000 })
  } else {
    window.setTimeout(warm, 1200)
  }
}

/** 新建一条空笔记并直接进入编辑页 */
export function useCreateNote() {
  const router = useRouter()
  prefetchDetailPage()
  return async function createNote() {
    const created = await memoApi.create({ title: '', contentHtml: '' })
    ElMessage.success('已新建笔记')
    await router.push(`/notes/${created.id}`)
    return created
  }
}
