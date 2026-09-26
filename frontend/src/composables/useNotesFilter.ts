import { ref } from 'vue'
import { useDebouncedValue } from './useDebounce'

/**
 * 搜索条件在「顶部搜索框」和「笔记列表」之间共享，所以做成模块级单例。
 */
const { input: keywordInput, debounced: keyword } = useDebouncedValue('', 340)
const activeTagIds = ref<number[]>([])

export function useNotesFilter() {
  function clear() {
    keywordInput.value = ''
    keyword.value = ''
    activeTagIds.value = []
  }

  function toggleTag(id: number) {
    activeTagIds.value = activeTagIds.value.includes(id)
      ? activeTagIds.value.filter((item) => item !== id)
      : [...activeTagIds.value, id]
  }

  const hasFilter = () => Boolean(keyword.value.trim()) || activeTagIds.value.length > 0

  return { keywordInput, keyword, activeTagIds, clear, toggleTag, hasFilter }
}
