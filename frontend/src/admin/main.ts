import { createApp } from 'vue'
import '@fontsource-variable/inter'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/main.css'
import './admin.css'
import AdminApp from './AdminApp.vue'

createApp(AdminApp).mount('#admin-app')
