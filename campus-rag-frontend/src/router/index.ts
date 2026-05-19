import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/ChatView.vue'),
    },
    {
      path: '/manage',
      name: 'Manage',
      component: () => import('@/views/ManageView.vue'),
    },
  ],
})

export default router
