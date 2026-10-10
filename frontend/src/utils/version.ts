/** 版本号比较：按数字段比大小，忽略 v 前缀和 -beta 后缀，非数字段当 0 */
function parse(version: string): number[] {
  return version
    .trim()
    .replace(/^v/i, '')
    .split('-')[0]
    .split('.')
    .map((part) => {
      const value = Number.parseInt(part, 10)
      return Number.isFinite(value) ? value : 0
    })
}

/** a 比 b 新返回正数，旧返回负数，相同返回 0 */
export function compareVersions(a: string, b: string): number {
  const left = parse(a)
  const right = parse(b)
  const length = Math.max(left.length, right.length)
  for (let i = 0; i < length; i += 1) {
    const diff = (left[i] ?? 0) - (right[i] ?? 0)
    if (diff !== 0) return diff
  }
  return 0
}

/** latest 比 current 新才算有更新；任一为空返回 false */
export function hasNewerVersion(current: string, latest?: string | null): boolean {
  if (!current || !latest) return false
  return compareVersions(latest, current) > 0
}
