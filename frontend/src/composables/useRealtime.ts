type RealtimeEventName = 'chat' | 'friends'
type RealtimeHandler = (data: unknown) => void

const listeners: Record<RealtimeEventName, Set<RealtimeHandler>> = {
  chat: new Set(),
  friends: new Set(),
}

let source: EventSource | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | undefined

function dispatch(name: RealtimeEventName, event: MessageEvent<string>) {
  let payload: unknown = event.data
  try {
    payload = JSON.parse(event.data)
  } catch {
    /* 普通字符串事件直接按原值传给监听者 */
  }
  listeners[name].forEach((handler) => handler(payload))
}

export function startRealtime() {
  if (source) return
  source = new EventSource('/api/events/stream')
  source.addEventListener('chat', (event) => dispatch('chat', event as MessageEvent<string>))
  source.addEventListener('friends', (event) => dispatch('friends', event as MessageEvent<string>))
  source.onerror = () => {
    source?.close()
    source = null
    clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(startRealtime, 3000)
  }
}

export function stopRealtime() {
  clearTimeout(reconnectTimer)
  reconnectTimer = undefined
  source?.close()
  source = null
}

export function onRealtime(name: RealtimeEventName, handler: RealtimeHandler) {
  listeners[name].add(handler)
  return () => listeners[name].delete(handler)
}
