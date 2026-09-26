import type { Component } from 'vue'
import { Document, Headset, Picture, VideoCamera } from '@element-plus/icons-vue'

/**
 * 附件类型 → 图标组件。
 * 动态图标必须传组件本身（而不是名字字符串），这样打包时才能按需引入。
 */
const ICONS: Record<string, Component> = {
  image: Picture,
  video: VideoCamera,
  audio: Headset,
  other: Document,
}

export function fileIcon(kind: string): Component {
  return ICONS[kind] ?? Document
}
