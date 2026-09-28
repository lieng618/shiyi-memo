import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource-variable/inter'
// 按需引入后，这两个命令式组件的样式要手动带上
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/main.css'
import '@/styles/markdown.css'
import App from '@/App.vue'
import router from '@/router'

// 主题：跟随上次选择，默认跟随系统
const savedTheme = localStorage.getItem('theme')
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
if (savedTheme === 'dark' || (!savedTheme && prefersDark)) {
  document.documentElement.classList.add('dark')
}
// 桌面端外壳：Windows 原生标题栏默认只跟系统主题走，这里把站点的选择同步过去。
// 放在挂载之前调用，窗口显示出来时标题栏已经是正确颜色了。
window.shiyi?.setTheme?.(document.documentElement.classList.contains('dark'))

createApp(App).use(createPinia()).use(router).mount('#app')
