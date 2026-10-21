const SESSION_KEY = 'memo-edit-session'

let fallbackSessionId: string | null = null

function randomSessionId() {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
}

/** 每个标签页一个编辑会话；刷新页面沿用同一个会话，其他标签页则各有自己的会话。 */
export function getEditSessionId() {
  try {
    const existing = sessionStorage.getItem(SESSION_KEY)
    if (existing) return existing
    const generated = randomSessionId()
    sessionStorage.setItem(SESSION_KEY, generated)
    return generated
  } catch {
    fallbackSessionId ??= randomSessionId()
    return fallbackSessionId
  }
}

export function getEditClientLabel() {
  const ua = navigator.userAgent
  const os = /Windows/i.test(ua)
    ? 'Windows'
    : /iPhone|iPad|iPod/i.test(ua)
      ? 'iOS'
      : /Android/i.test(ua)
        ? 'Android'
        : /Mac OS X|Macintosh/i.test(ua)
          ? 'macOS'
          : /Linux/i.test(ua)
            ? 'Linux'
            : '网页'
  const browser = /Edg\//i.test(ua)
    ? 'Edge'
    : /Firefox\//i.test(ua)
      ? 'Firefox'
      : /Chrome\//i.test(ua)
        ? 'Chrome'
        : /Safari\//i.test(ua)
          ? 'Safari'
          : '浏览器'
  return `${window.shiyi ? '桌面版' : browser} · ${os}`
}
