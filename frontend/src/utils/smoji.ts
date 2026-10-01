import { ref } from 'vue'

export interface SmojiItem {
  id: string
  label: string
}

export interface SmojiPack {
  id: string
  label: string
  items: SmojiItem[]
}

interface SmojiData {
  version: number
  base: string
  packs: SmojiPack[]
}

interface ResolvedSmoji {
  packId: string
  itemId: string
  label: string
  url: string
}

const MARKER_PATTERN = /^\[\[smoji:([a-z0-9-]+)\/([a-z0-9]+)\]\]$/i

export const smojiData = ref<SmojiData | null>(null)

let loading: Promise<SmojiData> | null = null
let packIndex = new Map<string, SmojiPack>()
let itemIndex = new Map<string, SmojiItem>()

export function loadSmoji(): Promise<SmojiData> {
  if (smojiData.value) {
    return Promise.resolve(smojiData.value)
  }
  if (loading) {
    return loading
  }
  loading = fetch('/emoji/smoji.json', { cache: 'force-cache' })
    .then((response) => {
      if (!response.ok) {
        throw new Error('表情数据加载失败')
      }
      return response.json() as Promise<SmojiData>
    })
    .then((data) => {
      smojiData.value = data
      packIndex = new Map(data.packs.map((pack) => [pack.id, pack]))
      itemIndex = new Map(data.packs.flatMap((pack) =>
        pack.items.map((item) => [`${pack.id}/${item.id}`, item])))
      return data
    })
    .catch((error) => {
      loading = null
      throw error
    })
  return loading
}

export function smojiUrl(packId: string, itemId: string): string {
  const data = smojiData.value
  if (!data || !packIndex.has(packId) || !itemIndex.has(`${packId}/${itemId}`)) {
    return ''
  }
  return data.base
    .replace('{pack}', encodeURIComponent(packId))
    .replace('{id}', encodeURIComponent(itemId))
}

export function smojiMarker(packId: string, itemId: string): string {
  return `[[smoji:${packId}/${itemId}]]`
}

export function resolveSmoji(content: string): ResolvedSmoji | null {
  if (!smojiData.value) {
    return null
  }
  const match = MARKER_PATTERN.exec(content.trim())
  if (!match) {
    return null
  }
  const [, packId, itemId] = match
  const item = itemIndex.get(`${packId}/${itemId}`)
  if (!item) {
    return null
  }
  return {
    packId,
    itemId,
    label: item.label,
    url: smojiUrl(packId, itemId),
  }
}
