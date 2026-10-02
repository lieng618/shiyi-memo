import { http } from './http'
import type {
  BackupImportResult,
  ChatMessage,
  FileAsset,
  FileKind,
  Friend,
  FriendNotification,
  FriendRequestList,
  FriendSummary,
  LoginResponse,
  MemoDetail,
  MemoSummary,
  PageResult,
  PublicMemoDetail,
  Quota,
  SendCodeResponse,
  ShareResponse,
  Tag,
  User,
  UserMessage,
  UserSearchResult,
} from '@/types'

export const authApi = {
  sendCode: (email: string) =>
    http.post<SendCodeResponse>('/auth/code', { email }).then((r) => r.data),
  login: (email: string, code: string) =>
    http.post<LoginResponse>('/auth/login', { email, code }).then((r) => r.data),
  loginWithPassword: (email: string, password: string) =>
    http.post<LoginResponse>('/auth/login/password', { email, password }).then((r) => r.data),
  /** 登录状态下直接设置 / 修改登录密码 */
  changePassword: (payload: { newPassword: string }) =>
    http.put<User>('/auth/password', payload).then((r) => r.data),
  logout: () => http.post('/auth/logout').then(() => undefined),
  me: () => http.get<User>('/auth/me', { skipAuthRedirect: true }).then((r) => r.data),
}

export const memoApi = {
  list: (params: { keyword?: string; tagIds?: number[]; pinned?: boolean; page?: number; size?: number }) =>
    http
      .get<PageResult<MemoSummary>>('/memos', {
        params,
        paramsSerializer: {
          indexes: null,
        },
      })
      .then((r) => r.data),
  get: (id: number) => http.get<MemoDetail>(`/memos/${id}`).then((r) => r.data),
  create: (payload: { title?: string; contentMd?: string; tagNames?: string[]; pinned?: boolean }) =>
    http.post<MemoDetail>('/memos', payload).then((r) => r.data),
  update: (id: number, payload: { title?: string; contentMd?: string; tagNames?: string[]; pinned?: boolean }) =>
    http.put<MemoDetail>(`/memos/${id}`, payload).then((r) => r.data),
  pin: (id: number, pinned: boolean) => http.post(`/memos/${id}/pin`, { pinned }).then(() => undefined),
  share: (id: number, enabled = true) =>
    http.post<ShareResponse>(`/memos/${id}/share`, { enabled }).then((r) => r.data),
  remove: (id: number) => http.delete(`/memos/${id}`).then(() => undefined),
}

export const shareApi = {
  get: (token: string) =>
    http.get<PublicMemoDetail>(`/public/memos/${encodeURIComponent(token)}`).then((r) => r.data),
}

export const tagApi = {
  list: () => http.get<Tag[]>('/tags').then((r) => r.data),
  create: (name: string, color?: string) => http.post<Tag>('/tags', { name, color }).then((r) => r.data),
  update: (id: number, payload: { name: string; color?: string }) =>
    http.put<Tag>(`/tags/${id}`, payload).then((r) => r.data),
  remove: (id: number) => http.delete(`/tags/${id}`).then(() => undefined),
}

export const fileApi = {
  list: (params: { memoId?: number; kind?: FileKind; page?: number; size?: number } = {}) =>
    http.get<PageResult<FileAsset>>('/files', { params }).then((r) => r.data),
  get: (id: number) => http.get<FileAsset>(`/files/${id}`).then((r) => r.data),
  upload: (file: File, memoId?: number, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    if (memoId != null) {
      form.append('memoId', String(memoId))
    }
    return http
      .post<FileAsset>('/files', form, {
        timeout: 0,
        onUploadProgress: (event) => {
          if (onProgress && event.total) {
            onProgress(Math.round((event.loaded / event.total) * 100))
          }
        },
      })
      .then((r) => r.data)
  },
  attach: (id: number, memoId: number | null) =>
    http.post(`/files/${id}/attach`, null, { params: { memoId } }).then(() => undefined),
  remove: (id: number) => http.delete(`/files/${id}`).then(() => undefined),
}

export const userApi = {
  quota: () => http.get<Quota>('/user/quota').then((r) => r.data),
  updateProfile: (nickname: string) => http.put<User>('/user/profile', { nickname }).then((r) => r.data),
  updateAvatar: (fileId: number | null) => http.put<User>('/user/avatar', { fileId }).then((r) => r.data),
}

/**
 * 备份走 fetch 而不是 axios：整包下载上传不受全局 30s 超时限制，
 * 鉴权同样是 cookie，错误信息也在这里自己解析，提示更准确。
 */
export const backupApi = {
  async export(): Promise<Blob> {
    const res = await fetch('/api/backup', { credentials: 'include' })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res))
    }
    return res.blob()
  },
  async importBackup(file: File): Promise<BackupImportResult> {
    const form = new FormData()
    form.append('file', file)
    const res = await fetch('/api/backup', { method: 'POST', credentials: 'include', body: form })
    if (!res.ok) {
      throw new Error(await readErrorMessage(res))
    }
    return (await res.json()) as BackupImportResult
  },
}

async function readErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { message?: string }
    return body?.message || `请求失败（${res.status}）`
  } catch {
    return `请求失败（${res.status}）`
  }
}

/** 站内信：用户只能看，不能删 */
export const messageApi = {
  list: (params: { page?: number; size?: number } = {}) =>
    http.get<PageResult<UserMessage>>('/messages', { params }).then((r) => r.data),
  unreadCount: () => http.get<{ count: number }>('/messages/unread-count').then((r) => r.data.count),
  markRead: (ids?: number[]) =>
    http.post<{ count: number }>('/messages/read', { ids: ids ?? null }).then((r) => r.data.count),
}

/** 好友：申请和处理都在好友页完成，同意后才能聊天 */
export const friendApi = {
  list: () => http.get<Friend[]>('/friends').then((r) => r.data),
  remove: (userId: number) => http.delete(`/friends/${userId}`).then(() => undefined),
  search: (keyword: string) =>
    http.get<UserSearchResult[]>('/friends/search', { params: { keyword } }).then((r) => r.data),
  sendRequest: (toUserId: number) =>
    http.post('/friends/requests', { toUserId }).then(() => undefined),
  requests: () => http.get<FriendRequestList>('/friends/requests').then((r) => r.data),
  requestCount: () =>
    http.get<{ count: number }>('/friends/requests/count').then((r) => r.data.count),
  summary: () => http.get<FriendSummary>('/friends/summary').then((r) => r.data),
  notifications: () => http.get<FriendNotification[]>('/friends/notifications').then((r) => r.data),
  accept: (requestId: number) =>
    http.post(`/friends/requests/${requestId}/accept`).then(() => undefined),
  reject: (requestId: number) =>
    http.post(`/friends/requests/${requestId}/reject`).then(() => undefined),
  cancelRequest: (requestId: number) =>
    http.delete(`/friends/requests/${requestId}`).then(() => undefined),
  chat: (userId: number, params: { afterId?: number; limit?: number } = {}) =>
    http.get<ChatMessage[]>(`/friends/${userId}/chat`, { params }).then((r) => r.data),
  /** 查聊天内容：type = text 按关键词搜文字，type = memo 只看分享的笔记 */
  searchChat: (userId: number, params: { keyword?: string; type?: 'text' | 'memo' }) =>
    http.get<ChatMessage[]>(`/friends/${userId}/chat/search`, { params }).then((r) => r.data),
  /** 清空聊天记录：只清自己这一侧 */
  clearChat: (userId: number) => http.delete(`/friends/${userId}/chat`).then(() => undefined),
  /** 重发没送出去的消息 */
  resendChat: (userId: number, messageId: number) =>
    http.post<ChatMessage>(`/friends/${userId}/chat/${messageId}/resend`).then((r) => r.data),
  sendChat: (userId: number, content: string) =>
    http.post<ChatMessage>(`/friends/${userId}/chat`, { content }).then((r) => r.data),
}
