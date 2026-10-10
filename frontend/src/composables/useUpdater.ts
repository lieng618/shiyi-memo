import { computed, ref, watch } from 'vue'

/** 只有装了新版外壳才有这几个接口，老外壳和浏览器里都没有 */
const supported = Boolean(window.shiyi?.startUpdate && window.shiyi?.onUpdateState)

/** 下完到重启之间的缓冲，让「正在安装更新」有时间画出来 */
const RESTART_DELAY = 1200
/** 落盘最多等这么久，保存卡住也不能把更新堵死 */
const FLUSH_TIMEOUT = 3000

/** 笔记页挂上来的桥接：重启前把没落库的编辑写回去 */
const flushBridge = globalThis as unknown as { __shiyiFlushEdits?: (() => Promise<void>) | null }

// 单例：状态由主进程推送，多个页面共用一份
const state = ref<ShiyiUpdateState>({ state: 'idle' })
let started = false

function apply(next: ShiyiUpdateState | null | undefined) {
  if (next) state.value = next
}

function install() {
  window.shiyi?.installUpdate?.()
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

/** 先落盘再重启；没有待保存的编辑就立刻返回 */
async function flushPendingEdits() {
  const flush = flushBridge.__shiyiFlushEdits
  if (!flush) return
  try {
    await Promise.race([flush(), sleep(FLUSH_TIMEOUT)])
  } catch {
    // 保存失败也照样更新，总比卡在旧版本强
  }
}

/**
 * 触发一次检查，并等到有结论再返回。
 * electron-updater 是事件式的，checkForUpdates() 不会同步给出结果，只能等状态推回来。
 */
async function checkAndWait(timeout = 15000): Promise<'available' | 'latest' | 'error'> {
  const before = state.value.state
  const result = await (window.shiyi?.checkUpdate?.() ?? Promise.resolve({ ok: false }))
  if (!result?.ok) return 'error'

  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const phase = state.value.state
    if (phase === 'available') return 'available'
    if (phase === 'error') return 'error'
    // 状态没动过就说明还是上一次的结论，继续等这一次的
    if (phase === 'up-to-date' && phase !== before) return 'latest'
    await sleep(150)
  }
  return 'error'
}

/** 桌面端自动更新：检查 → 下载 → 重启安装 */
export function useUpdater() {
  // 只启动一次，单页应用里不需要随组件卸载
  if (!started) {
    started = true
    const shell = window.shiyi
    shell?.onUpdateState?.(apply)
    if (shell?.getUpdateState) {
      void shell.getUpdateState().then(apply).catch(() => {})
    }
    watch(state, (next, prev) => {
      if (next.state === 'downloaded' && prev?.state !== 'downloaded') {
        window.setTimeout(() => void restartToInstall(), RESTART_DELAY)
      }
    })
  }

  const downloading = computed(() => state.value.state === 'downloading')
  const downloaded = computed(() => state.value.state === 'downloaded')
  /** 有可下载的新版本 */
  const hasUpdate = computed(() => state.value.state === 'available')

  return {
    state,
    supported,
    downloading,
    downloaded,
    hasUpdate,
    /** 服务器上报的新版本号 */
    version: computed(() => state.value.version ?? ''),
    percent: computed(() => state.value.percent ?? 0),
    checkAndWait,
    startUpdate: () => window.shiyi?.startUpdate?.() ?? Promise.resolve({ ok: false, unsupported: true }),
    install: restartToInstall,
  }
}

/** 退出并安装新版本；先等编辑落库，免得把刚打的字丢掉 */
async function restartToInstall() {
  await flushPendingEdits()
  install()
}
