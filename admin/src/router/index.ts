import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: () => import('../pages/login/index.vue') },
  { path: '/dashboard', component: () => import('../pages/dashboard/index.vue') },
  { path: '/system/empty', component: () => import('../pages/system/empty/index.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router