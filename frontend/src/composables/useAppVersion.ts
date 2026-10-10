import { computed, ref } from 'vue'
import { appApi } from '@/api'
import type { AppVersionInfo } from '@/types'

/** 桌面端外壳注入的，浏览器里没有 */
const shell = window.shiyi
export const isDesktop = Boolean(shell)
export const shellVersion = shell?.version ?? ''

/** 重新检查的间隔（切回标签页时也会查一次） */
const RECHECK_INTERVAL = 5 * 60 * 1000

// 单例：多个页面共用一份，避免各自轮询
const versionInfo = ref<AppVersionInfo | null>(null)
const outdated = ref(false)
let started = false

async function loadVersion(): Promise<void> {
  try {
    const next = await appApi.version()
    // 和加载时的版本不一致，说明服务器发了新版
    if (versionInfo.value && versionInfo.value.version !== next.version) {
      outdated.value = true
    }
    versionInfo.value = next
  } catch {
    // 版本检查失败不影响功能
  }
}

/** 站点版本 + 桌面端下载信息 */
export function useAppVersion() {
  // 只启动一次，单页应用里不需要随组件卸载
  if (!started) {
    started = true
    void loadVersion()
    window.addEventListener('visibilitychange', onVisibilityChange)
    window.setInterval(loadVersion, RECHECK_INTERVAL)
  }

  return {
    versionInfo,
    /** 服务器已发新版，建议刷新 */
    outdated,
    isDesktop,
    shellVersion,
    refresh: loadVersion,
    reload: () => window.location.reload(),
    /** 安装包下载地址 */
    downloadUrl: computed(() => versionInfo.value?.desktop?.downloadUrl ?? ''),
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void loadVersion()
  }
}
