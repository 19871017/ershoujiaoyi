<template>
  <section class="page-shell dashboard-page">
    <div class="page-title">后台仪表盘</div>
    <div class="page-desc">从后端 /api/admin/dashboard 聚合真实审核、提现、售后、用户和订单状态；失败时不展示假数据。</div>
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
    <p class="safe-note">仪表盘只展示服务端聚合记录；不会从本地审核列表推导财务、售后、订单或用户指标。</p>
    <div class="actions dashboard-actions">
      <RouterLink class="primary-link" to="/audit">审核工作台</RouterLink>
      <RouterLink class="primary-link" to="/finance/withdrawals">提现审核</RouterLink>
      <RouterLink class="primary-link" to="/after-sales">售后管理</RouterLink>
      <RouterLink class="primary-link" to="/orders">订单管理</RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getAdminDashboard, type AdminDashboardSummary } from '../../api'

const summary = ref<AdminDashboardSummary | null>(null)
const error = ref('')

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
    error.value = '后台数据加载失败：未展示本地聚合假数据，请确认后端服务和管理员访问权限。'
  }
})
</script>
