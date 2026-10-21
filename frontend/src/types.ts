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
  /** 我的邀请码：拼成 /j/{code} 发给朋友 */
  inviteCode?: string | null
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
  contentHtml: string
  pinned: boolean
  shareEnabled: boolean
  shareToken?: string | null
  tags: Tag[]
  files: FileAsset[]
  version: number
  editLease: EditLeaseInfo
  createdAt: string
  updatedAt: string
}

export interface EditLeaseInfo {
  heldByMe: boolean
  holderLabel?: string | null
  expiresAt?: string | null
}

export interface PublicMemoDetail {
  id: number
  title: string
  contentHtml: string
  ownerId: number
  ownerName: string
  ownerAvatarUrl?: string | null
  friend: boolean
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

export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELED'

export interface UserMessage {
  id: number
  title: string
  content: string
  /** 后台富文本通知的正文（wangEditor HTML），系统消息为空 */
  contentHtml?: string | null
  type: MessageType
  refMemoId?: number | null
  refFileId?: number | null
  senderName: string
  readAt?: string | null
  createdAt: string
}

export interface Friend {
  userId: number
  nickname: string
  avatarUrl?: string | null
  unread: number
  friendedAt: string
}

export interface UserSearchResult {
  userId: number
  nickname: string
  avatarUrl?: string | null
}

/** 邀请链接落地页拿到的信息 */
export interface InviteCard {
  userId: number
  nickname: string
}

export interface FriendRequest {
  id: number
  userId: number
  nickname: string
  avatarUrl?: string | null
  status: FriendRequestStatus
  createdAt: string
}

export interface FriendRequestList {
  incoming: FriendRequest[]
  outgoing: FriendRequest[]
}

export interface FriendSummary {
  pendingRequests: number
  unreadChats: number
}

export interface FriendNotification {
  type: 'CHAT' | 'FRIEND_REQUEST'
  userId: number
  nickname: string
  avatarUrl?: string | null
  content: string
  unread: number
  createdAt: string
}

export interface ChatMessage {
  id: number
  senderId: number
  mine: boolean
  content: string
  /** 没送出去（对方不是好友了），只有自己看得见 */
  failed?: boolean
  createdAt: string
}

/** /api/version 返回的版本信息 */
export interface AppVersionInfo {
  /** 站点版本 */
  version: string
  buildTime?: string | null
  desktop?: {
    /** 安装包下载地址，没配就是空 */
    downloadUrl?: string | null
  } | null
}

