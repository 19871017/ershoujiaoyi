<template>
  <section class="page-shell dashboard-page">
    <div class="page-title">后台仪表盘</div>
    <div class="page-desc">聚合真实审核、提现、售后、用户和订单状态；加载失败时不展示统计。</div>
    <div v-if="error" class="alert">{{ error }}</div>
    <div class="stat-grid dashboard-grid">
      <div class="stat-card"><strong>{{ display(summary?.pendingAudits) }}</strong><span>待审核</span></div>
      <div class="stat-card"><strong>{{ display(summary?.approvedAudits) }}</strong><span>已通过审核</span></div>
      <div class="stat-card"><strong>{{ display(summary?.rejectedAudits) }}</strong><span>已拒绝审核</span></div>
      <div class="stat-card"><strong>{{ display(summary?.pendingWithdrawals) }}</strong><span>待处理提现</span></div>
      <div class="stat-card"><strong>{{ display(summary?.pendingAfterSales) }}</strong><span>待处理售后</span></div>
      <div class="stat-card"><strong>{{ display(summary?.activeUsers) }}</strong><span>活跃用户</span></div>
      <div class="stat-card"><strong>{{ display(summary?.todayOrders) }}</strong><span>今日订单</span></div>
      <div class="stat-card"><strong>{{ money(summary?.grossMerchandiseValue) }}</strong><span>今日成交额</span></div>
    </div>
    <p class="safe-note">仪表盘展示平台聚合的审核、财务、售后、订单与用户指标。</p>
    <div class="actions dashboard-actions">
      <RouterLink v-for="item in dashboardActions" :key="item.path" class="primary-link" :to="item.path">{{ item.label }}</RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getAdminDashboard, type AdminDashboardSummary } from '../../api'
import { dashboardActionsForSession, useAuthStore } from '../../store/modules/auth'

const auth = useAuthStore()
const summary = ref<AdminDashboardSummary | null>(null)
const error = ref('')
const dashboardActions = dashboardActionsForSession(auth.session)

function display(value: number | undefined) {
  return value === undefined ? '-' : String(value)
}

function money(value: number | undefined) {
  return value === undefined ? '-' : `¥${value.toFixed(2)}`
}

onMounted(async () => {
  try {
    summary.value = await getAdminDashboard()
  } catch {
    error.value = '后台数据加载失败，请确认服务状态和管理员访问权限。'
  }
})
</script>
