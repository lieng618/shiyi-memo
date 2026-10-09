import { adminApi } from './endpoints'
import type { AdminUploadFile } from './types'
import { formatChatFileSize } from '@/utils/chatFile'

/**
 * 上传返回的 url 是用户侧前缀；编辑器和后台预览要走 /api/admin 前缀，
 * 发送时后端会把 /api/admin/files/ 统一改写成 /api/files/。
 */
function adminFileUrl(asset: AdminUploadFile, action: 'raw' | 'download') {
  return `/api/admin/files/${asset.id}/${action}`
}

/** 后台富文本编辑器的上传回调：图片原位插入正文，其他文件插成附件下载链接 */
export function useAdminUploads() {
  async function onUploadImage(file: File, onProgress: (percent: number) => void) {
    const asset = await adminApi.uploadAttachment(file, onProgress)
    return { url: adminFileUrl(asset, 'raw') }
  }

  async function onUploadAttachment(file: File, onProgress: (percent: number) => void) {
    const asset = await adminApi.uploadAttachment(file, onProgress)
    return {
      url: adminFileUrl(asset, 'download'),
      name: `${asset.originalName}（${formatChatFileSize(asset.size)}）`,
    }
  }

  return { onUploadImage, onUploadAttachment }
}
