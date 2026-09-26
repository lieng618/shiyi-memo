import { adminHttp } from './api'
import type {
  AdminFile,
  AdminLevel,
  AdminMemo,
  AdminMemoDetail,
  AdminMessage,
  AdminStats,
  AdminUser,
  LevelPayload,
  PageResult,
} from './types'

export const adminApi = {
  me: () => adminHttp.get<{ username: string }>('/auth/me').then((r) => r.data),
  login: (username: string, password: string) =>
    adminHttp.post<{ username: string; expiresInSeconds: number }>('/auth/login', { username, password }).then((r) => r.data),
  logout: () => adminHttp.post('/auth/logout').then(() => undefined),

  stats: () => adminHttp.get<AdminStats>('/stats').then((r) => r.data),

  users: (params: { keyword?: string; status?: number; page?: number; size?: number }) =>
    adminHttp.get<PageResult<AdminUser>>('/users', { params }).then((r) => r.data),
  updateUser: (id: number, payload: { nickname?: string; levelId?: number; status?: number }) =>
    adminHttp.put<AdminUser>(`/users/${id}`, payload).then((r) => r.data),

  levels: () => adminHttp.get<AdminLevel[]>('/levels').then((r) => r.data),
  createLevel: (payload: LevelPayload) => adminHttp.post<AdminLevel>('/levels', payload).then((r) => r.data),
  updateLevel: (id: number, payload: LevelPayload) =>
    adminHttp.put<AdminLevel>(`/levels/${id}`, payload).then((r) => r.data),
  deleteLevel: (id: number) => adminHttp.delete(`/levels/${id}`).then(() => undefined),

  memos: (params: { keyword?: string; userId?: number; page?: number; size?: number }) =>
    adminHttp.get<PageResult<AdminMemo>>('/memos', { params }).then((r) => r.data),
  memoDetail: (id: number) => adminHttp.get<AdminMemoDetail>(`/memos/${id}`).then((r) => r.data),
  deleteMemo: (id: number, reason?: string) =>
    adminHttp.delete(`/memos/${id}`, { params: { reason } }).then(() => undefined),

  files: (params: { keyword?: string; userId?: number; blocked?: boolean; page?: number; size?: number }) =>
    adminHttp.get<PageResult<AdminFile>>('/files', { params }).then((r) => r.data),
  blockFile: (id: number, reason?: string) =>
    adminHttp.post<AdminFile>(`/files/${id}/block`, { reason }).then((r) => r.data),
  unblockFile: (id: number) => adminHttp.post<AdminFile>(`/files/${id}/unblock`).then((r) => r.data),

  messages: (params: { userId?: number; page?: number; size?: number }) =>
    adminHttp.get<PageResult<AdminMessage>>('/messages', { params }).then((r) => r.data),
  sendMessage: (payload: { userId: number; title: string; content: string }) =>
    adminHttp.post('/messages', payload).then(() => undefined),
}
