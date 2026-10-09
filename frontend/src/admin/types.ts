export interface AdminUser {
  id: number
  email: string
  nickname: string
  avatarUrl?: string | null
  status: number
  levelId?: number | null
  levelName?: string | null
  maxStorage?: number | null
  storageUsed?: number | null
  memoCount?: number | null
  fileCount?: number | null
  blockedFileCount?: number | null
  messageCount?: number | null
  lastLoginAt?: string | null
  createdAt?: string | null
}

export interface AdminLevel {
  id: number
  code: string
  name: string
  maxStorage: number
  description?: string | null
  sort?: number | null
  isDefault?: boolean | null
  userCount?: number | null
}

export interface AdminMemo {
  id: number
  userId: number
  userEmail?: string | null
  userNickname?: string | null
  title: string
  excerpt?: string | null
  pinned?: boolean | null
  fileCount?: number | null
  createdAt: string
  updatedAt: string
}

export interface AdminMemoDetail {
  id: number
  userId: number
  userEmail?: string | null
  userNickname?: string | null
  title: string
  contentHtml: string
  files: AdminFileItem[]
  createdAt: string
  updatedAt: string
}

export interface AdminFileItem {
  id: number
  memoId?: number | null
  originalName: string
  ext: string
  mime: string
  size: number
  kind: 'image' | 'video' | 'audio' | 'other'
  width?: number | null
  height?: number | null
  url?: string | null
  thumbUrl?: string | null
  blocked?: boolean | null
  blockedReason?: string | null
  createdAt: string
}

export interface AdminFile extends AdminFileItem {
  userId: number
  userEmail?: string | null
  blockedAt?: string | null
}

export interface AdminMessage {
  id: number
  userId: number
  userEmail?: string | null
  userNickname?: string | null
  title: string
  content: string
  type: string
  senderName: string
  /** 定时送达时间；为空表示发出即送达 */
  sendAt?: string | null
  readAt?: string | null
  createdAt: string
}

/** 新用户欢迎信配置（单行） */
export interface AdminWelcomeLetter {
  enabled: boolean
  title: string
  contentHtml: string
  updatedBy?: string | null
  updatedAt?: string | null
}

/** 后台上传信件附件的返回（FileDto 的子集，够编辑器插图/插附件用） */
export interface AdminUploadFile {
  id: number
  originalName: string
  size: number
  kind: 'image' | 'video' | 'audio' | 'other'
  url: string
  thumbUrl?: string | null
}

export interface AdminStats {
  userCount: number
  memoCount: number
  fileCount: number
  blockedFileCount: number
  storageUsed: number
  messageCount: number
  unreadMessageCount: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  hasMore: boolean
}

export interface LevelPayload {
  code?: string
  name: string
  maxStorage: number
  description?: string | null
  sort?: number | null
  isDefault?: boolean
}
