import { createRouter, createMemoryHistory, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { shouldRedirectToLogin } from '../store/modules/auth'
import { useAuthStore } from '../store/modules/auth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('../pages/login/index.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    children: [
      { path: 'dashboard', component: () => import('../pages/dashboard/index.vue') },
      { path: 'audit', component: () => import('../pages/audit/index.vue') },
      { path: 'audit/:auditNo', component: () => import('../pages/audit/detail.vue') },
      { path: 'finance/withdrawals', component: () => import('../pages/finance/withdrawals/index.vue') },
      { path: 'finance/withdrawals/:withdrawalNo', component: () => import('../pages/finance/withdrawals/index.vue') },
      { path: 'orders', component: () => import('../pages/orders/index.vue') },
      { path: 'orders/:orderNo', component: () => import('../pages/orders/index.vue') },
      { path: 'users', component: () => import('../pages/users/index.vue') },
      { path: 'users/:userId', component: () => import('../pages/users/index.vue') },
      { path: 'after-sales', component: () => import('../pages/after-sales/index.vue') },
      { path: 'after-sales/:afterSalesNo', component: () => import('../pages/after-sales/index.vue') },
      { path: 'audit-logs', component: () => import('../pages/audit-logs/index.vue') },
      { path: 'operators', component: () => import('../pages/operators/index.vue') },
      { path: 'operators/:userId', component: () => import('../pages/operators/index.vue') },
      { path: 'system/location', component: () => import('../pages/system/location/index.vue') }
    ]
  }
]

export const adminRoutes = routes

const router = createRouter({
  history: typeof window === 'undefined' ? createMemoryHistory() : createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const auth = useAuthStore()
  if (auth.session) {
    try {
      await auth.refresh()
    } catch {
      return '/login'
    }
  }
  if (shouldRedirectToLogin(to.path, auth.session)) return '/login'
  return true
})

export default router
