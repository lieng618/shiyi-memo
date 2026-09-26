import { ref } from 'vue'

const isDark = ref(document.documentElement.classList.contains('dark'))

/**
 * 主题：跟随上次选择，首次访问跟随系统。
 * 单例 ref，侧边栏和设置页共用同一份状态。
 */
export function useTheme() {
  function setDark(value: boolean) {
    isDark.value = value
    document.documentElement.classList.toggle('dark', value)
    localStorage.setItem('theme', value ? 'dark' : 'light')
  }

  function toggle() {
    setDark(!isDark.value)
  }

  return { isDark, setDark, toggle }
}
