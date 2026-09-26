import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { memoApi } from '@/api'

/** 新建一条空笔记并直接进入编辑页 */
export function useCreateNote() {
  const router = useRouter()
  return async function createNote() {
    const created = await memoApi.create({ title: '', contentMd: '' })
    ElMessage.success('已新建笔记')
    await router.push(`/notes/${created.id}`)
    return created
  }
}
