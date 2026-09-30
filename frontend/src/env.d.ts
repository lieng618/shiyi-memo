/// <reference types="vite/client" />

/**
 * 桌面端（Electron）外壳通过 preload 注入的能力。
 * 在普通浏览器里是 undefined，调用处一律用可选链兜底。
 */
interface Window {
  shiyi?: {
    /** 把当前深浅色同步给外壳，让窗口标题栏跟着一起变 */
    setTheme?: (dark: boolean) => void
    /** 把自包含 HTML 交给主进程转 PDF，返回保存路径；用户取消时 canceled 为 true */
    exportPdf?: (html: string, filename: string) => Promise<{ canceled?: boolean; path?: string } | null>
  }
  /** html-docx-js 的 UMD 构建（public/lib/html-docx.js），导出 Word 时按需加载 */
  htmlDocx?: {
    asBlob: (
      html: string,
      options?: {
        orientation?: 'portrait' | 'landscape'
        margins?: { top?: number; right?: number; bottom?: number; left?: number; header?: number; footer?: number }
      },
    ) => Blob
  }
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
