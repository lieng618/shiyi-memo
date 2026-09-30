export interface UserLevel {
  id: number
  code: string
  name: string
  maxStorage: number
  description?: string | null
  sort?: number | null
  isDefault?: boolean | null
  userCount?: number | null
}

export interface User {
  id: number
  email: string
  nickname: string
  avatarUrl?: string | null
  hasPassword?: boolean | null
  status: number
  level?: UserLevel | null
  storageUsed: number
  maxStorage: number
  lastLoginAt?: string | null
  createdAt?: string | null
}

export interface Tag {
  id: number
  name: string
  color: string
  memoCount?: number | null
}

export type FileKind = 'image' | 'video' | 'audio' | 'other'

export interface FileAsset {
  id: number
  memoId?: number | null
  originalName: string
  ext: string
  mime: string
  size: number
  kind: FileKind
  width?: number | null
  height?: number | null
  url: string
  thumbUrl?: string | null
  blocked?: boolean | null
  blockedReason?: string | null
  createdAt: string
}

export interface MemoSummary {
  id: number
  title: string
  excerpt: string
  pinned: boolean
  shareEnabled: boolean
  shareToken?: string | null
  tags: Tag[]
  fileCount: number
  coverUrl?: string | null
  createdAt: string
  updatedAt: string
}

export interface MemoDetail {
  id: number
  title: string
  contentMd: string
  pinned: boolean
  shareEnabled: boolean
  shareToken?: string | null
  tags: Tag[]
  files: FileAsset[]
  createdAt: string
  updatedAt: string
}

export interface PublicMemoDetail {
  id: number
  title: string
  contentMd: string
  ownerId: number
  ownerName: string
  updatedAt: string
}

export interface ShareResponse {
  enabled: boolean
  token?: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  hasMore: boolean
}

export interface Quota {
  levelId: number
  levelName: string
  maxStorage: number
  used: number
  remainingFiles: number
  usedPercent: number
}

export interface BackupImportResult {
  version: number
  memoCount: number
  attachmentCount: number
  skippedMemos: number
  skippedAttachments: number
}

export interface SendCodeResponse {
  sent: boolean
  mailEnabled: boolean
  devCode?: string | null
  expiresInSeconds: number
  registered: boolean
}

export interface LoginResponse {
  user: User
  expiresInSeconds: number
}

export type MessageType = 'NOTICE' | 'FILE_BLOCKED' | 'MEMO_REMOVED'

export interface UserMessage {
  id: number
  title: string
  content: string
  type: MessageType
  refMemoId?: number | null
  refFileId?: number | null
  senderName: string
  readAt?: string | null
  createdAt: string
}
