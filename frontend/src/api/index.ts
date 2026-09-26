import { http } from './http'
import type {
  FileAsset,
  FileKind,
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

/** 站内信：用户只能看，不能删 */
export const messageApi = {
  list: (params: { page?: number; size?: number } = {}) =>
    http.get<PageResult<UserMessage>>('/messages', { params }).then((r) => r.data),
  unreadCount: () => http.get<{ count: number }>('/messages/unread-count').then((r) => r.data.count),
  markRead: (ids?: number[]) =>
    http.post<{ count: number }>('/messages/read', { ids: ids ?? null }).then((r) => r.data.count),
}
