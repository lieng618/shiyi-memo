/// <reference types="vite/client" />

/**
 * 桌面端（Electron）外壳通过 preload 注入的能力。
 * 在普通浏览器里是 undefined，调用处一律用可选链兜底。
 */
interface Window {
  shiyi?: {
    /** 把当前深浅色同步给外壳，让窗口标题栏跟着一起变 */
    setTheme?: (dark: boolean) => void
  }
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
