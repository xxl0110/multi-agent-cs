import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/chat/index.vue'),
    meta: { title: '智能客服' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
