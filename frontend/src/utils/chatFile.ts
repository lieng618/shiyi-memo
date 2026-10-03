export type ChatFileKind = 'image' | 'video' | 'audio' | 'other'

export interface ChatFileInfo {
  fileId: number
  kind: ChatFileKind
  size: number
  name: string
}

/** 和后端 FriendService.FILE_MARKER 约定一致：[[file:{id}:{kind}:{size}:{encodeURIComponent(文件名)}]] */
export function chatFileMarker(info: ChatFileInfo): string {
  // 名字只用来展示，截断防止长中文文件名百分号编码后超过 content 的 2000 字上限
  const name = info.name.length > 100 ? `${info.name.slice(0, 100)}…` : info.name
  return `[[file:${info.fileId}:${info.kind}:${info.size}:${encodeURIComponent(name)}]]`
}

export function resolveChatFile(content: string): ChatFileInfo | null {
  const match = /^\[\[file:(\d+):(image|video|audio|other):(\d+):([^\]]+)\]\]$/.exec(content.trim())
  if (!match) {
    return null
  }
  try {
    return {
      fileId: Number(match[1]),
      kind: match[2] as ChatFileKind,
      size: Number(match[3]),
      name: decodeURIComponent(match[4]),
    }
  } catch {
    return null
  }
}

export function chatFileRawUrl(fileId: number): string {
  return `/api/files/${fileId}/raw`
}

export function chatFileThumbUrl(fileId: number, width = 480): string {
  return `/api/files/${fileId}/thumb?w=${width}`
}

export function chatFileDownloadUrl(fileId: number): string {
  return `/api/files/${fileId}/download`
}

export function formatChatFileSize(size: number): string {
  if (!Number.isFinite(size) || size < 0) {
    return ''
  }
  if (size < 1024) {
    return `${size} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = size
  let unit = 'B'
  for (const next of units) {
    if (value < 1024) break
    value /= 1024
    unit = next
  }
  return `${value >= 100 ? Math.round(value) : value.toFixed(1)} ${unit}`
}
