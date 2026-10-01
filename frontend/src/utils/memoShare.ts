export interface MemoShareInfo {
  token: string
  title: string
  excerpt: string
}

export function memoShareMarker(info: MemoShareInfo): string {
  return `[[memo-share:${info.token}:${encodeURIComponent(info.title)}:${encodeURIComponent(info.excerpt)}]]`
}

export function resolveMemoShare(content: string): MemoShareInfo | null {
  const match = /^\[\[memo-share:([a-z0-9]+):([^:]*):([^\]]*)\]\]$/i.exec(content.trim())
  if (!match) {
    return null
  }
  try {
    return {
      token: match[1],
      title: decodeURIComponent(match[2]),
      excerpt: decodeURIComponent(match[3]),
    }
  } catch {
    return null
  }
}
