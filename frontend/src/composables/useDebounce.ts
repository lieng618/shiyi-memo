import { ref, watch, type Ref } from 'vue'

/**
 * 输入防抖：搜索框边打字边请求容易打爆后端，这里统一处理。
 * 返回 input（绑定输入框）和 debounced（防抖后的值，用来发请求）。
 */
export function useDebouncedValue<T>(initial: T, delay = 320): { input: Ref<T>; debounced: Ref<T> } {
  const input = ref(initial) as Ref<T>
  const debounced = ref(initial) as Ref<T>
  let timer: ReturnType<typeof setTimeout> | undefined
  watch(input, (value) => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      debounced.value = value
    }, delay)
  })
  return { input, debounced }
}

/** 延迟执行，常用于自动保存；cancel() 用来在离开页面时取消 */
export function debounce<T extends (...args: never[]) => void>(fn: T, delay = 600) {
  let timer: ReturnType<typeof setTimeout> | undefined
  const wrapped = (...args: Parameters<T>) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
  wrapped.cancel = () => clearTimeout(timer)
  wrapped.flush = (...args: Parameters<T>) => {
    clearTimeout(timer)
    fn(...args)
  }
  return wrapped
}
