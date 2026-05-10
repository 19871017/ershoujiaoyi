<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="brand">
        <strong>小原圈</strong>
        <span>独立管理后台</span>
      </div>
      <nav class="menu">
        <RouterLink v-for="item in menus" :key="item.path" :to="item.path">{{ item.label }}</RouterLink>
      </nav>
    </aside>
    <main class="admin-main">
      <header class="admin-header">
        <div>
          <div class="header-title">运营控制台</div>
          <div class="header-desc">审核、提现和系统配置仅在独立后台处理</div>
        </div>
        <button class="ghost-btn" @click="logout">退出</button>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { menuAllowsSession, useAuthStore, type AdminMenuItem } from '../store/modules/auth'

const router = useRouter()
const auth = useAuthStore()
const allMenus: AdminMenuItem[] = [
  { path: '/dashboard', label: '仪表盘', permission: null },
  { path: '/audit', label: '审核工作台', permission: 'audit:read' },
  { path: '/finance/withdrawals', label: '提现审核', permission: 'finance:read' },
  { path: '/orders', label: '订单管理', permission: 'order:read' },
  { path: '/users', label: '用户管理', permission: 'user:read' },
  { path: '/after-sales', label: '售后管理', permission: 'after-sales:read' },
  { path: '/audit-logs', label: '审计日志', permission: 'audit:log' },
  { path: '/system/location', label: '位置配置', permission: 'system:config' }
]
const menus = computed(() => allMenus.filter((item) => menuAllowsSession(item, auth.session)))

async function logout() {
  await auth.clear()
  router.replace('/login')
}
</script>
