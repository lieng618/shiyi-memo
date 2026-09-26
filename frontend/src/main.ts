import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource-variable/inter'
// 按需引入后，这两个命令式组件的样式要手动带上
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/main.css'
import App from '@/App.vue'
import router from '@/router'

// 主题：跟随上次选择，默认跟随系统
const savedTheme = localStorage.getItem('theme')
if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
  document.documentElement.classList.add('dark')
}

createApp(App).use(createPinia()).use(router).mount('#app')
