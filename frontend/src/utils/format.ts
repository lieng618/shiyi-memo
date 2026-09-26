import dayjs from 'dayjs'

export function formatSize(bytes?: number | null): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value >= 100 ? value.toFixed(0) : value.toFixed(1)} ${units[unit]}`
}

export function formatTime(value?: string | null): string {
  if (!value) return '-'
  const time = dayjs(value)
  const now = dayjs()
  if (time.isSame(now, 'day')) return `今天 ${time.format('HH:mm')}`
  if (time.isSame(now.subtract(1, 'day'), 'day')) return `昨天 ${time.format('HH:mm')}`
  if (time.isSame(now, 'year')) return time.format('MM-DD HH:mm')
  return time.format('YYYY-MM-DD HH:mm')
}

/** 详情页里显示更完整的时间 */
export function formatFull(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

export function fileKindLabel(kind: string): string {
  switch (kind) {
    case 'image':
      return '图片'
    case 'video':
      return '视频'
    case 'audio':
      return '音频'
    default:
      return '文件'
  }
}

const MB = 1024 * 1024
const GB = 1024 * MB

export function bytesToMb(bytes: number): number {
  return Math.max(1, Math.round(bytes / MB))
}

export function mbToBytes(mb: number): number {
  return Math.round(mb * MB)
}

export function bytesToGb(bytes: number): number {
  const gb = bytes / GB
  return gb >= 10 ? Math.round(gb) : Math.round(gb * 10) / 10
}

export function gbToBytes(gb: number): number {
  return Math.round(gb * GB)
}
