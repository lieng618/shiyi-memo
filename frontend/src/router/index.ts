import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    // 首页 = 顶部搜索 + 账号区 + 笔记列表，只有这一层套 AppShell
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: '/notes' },
      { path: 'notes', name: 'notes', component: () => import('@/views/NotesListView.vue') },
    ],
  },
  {
    path: '/notes/:id(\\d+)',
    name: 'note-detail',
    component: () => import('@/views/NoteDetailView.vue'),
  },
  {
    path: '/share/:token',
    name: 'public-share',
    component: () => import('@/views/PublicShareView.vue'),
    meta: { public: true, title: '查看分享' },
  },
  {
    // 邀请链接：/j/{邀请码}，未登录也能打开，登录后直接申请加好友
    path: '/j/:code',
    name: 'invite',
    component: () => import('@/views/InviteView.vue'),
    meta: { public: true, title: '好友邀请' },
  },
  {
    path: '/me',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
  },
  {
    path: '/messages',
    name: 'messages',
    component: () => import('@/views/MessagesView.vue'),
  },
  {
    path: '/messages/:id(\\d+)',
    name: 'message-detail',
    component: () => import('@/views/MessageDetailView.vue'),
  },
  {
    path: '/friends',
    name: 'friends',
    component: () => import('@/views/FriendsView.vue'),
  },
  {
    path: '/friends/:id(\\d+)',
    name: 'friend-chat',
    component: () => import('@/views/ChatView.vue'),
  },
  { path: '/:pathMatch(.*)*', redirect: '/notes' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized && !to.meta.public) {
    await auth.fetchMe()
  }
  if (to.meta.public) {
    return auth.isLoggedIn && to.name === 'login' ? { path: '/notes' } : true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
