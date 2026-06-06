<template>
  <section class="page-shell">
    <div class="page-title">审核工作台</div>
    <div class="page-desc">统一处理举报、提现、视频认证、商品审核等后台记录。</div>
    <form class="toolbar audit-filter-bar" @submit.prevent="load">
      <select v-model="auditTypeFilter">
        <option value="ALL">全部类型</option>
        <option value="REPORT">举报处理</option>
        <option value="WITHDRAWAL">提现审核</option>
        <option value="VIDEO_IDENTITY">视频认证</option>
        <option value="PRODUCT">商品审核</option>
      </select>
      <select v-model="statusFilter">
        <option value="PENDING">待处理</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已拒绝</option>
        <option value="ALL">全部状态</option>
      </select>
      <input v-model.trim="keyword" maxlength="64" placeholder="审核号/目标编号/用户ID/原因" @keyup.enter="load" />
      <input v-model.number="limit" type="number" min="1" max="100" step="1" @keyup.enter="load" />
      <button class="primary-btn" :disabled="loading">{{ loading ? '加载中...' : '刷新队列' }}</button>
      <button class="secondary-btn" type="button" :disabled="loading" @click="showReportsOnly">只看举报</button>
      <span>当前待审 {{ pendingCount }} 条</span>
    </form>
    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">审核记录加载中...</div>
    <div v-else-if="audits.length === 0" class="empty">暂无审核记录。</div>
    <article v-for="item in audits" :key="item.auditNo" class="audit-card">
      <div class="audit-main">
        <strong>{{ item.auditNo }}</strong>
        <span>{{ item.auditType }} / {{ item.targetType || '未标注目标' }}</span>
        <p>{{ item.reason || item.description || '无补充说明' }}</p>
        <div class="audit-trace-actions">
          <RouterLink class="detail-link" :to="`/audit/${encodeURIComponent(item.auditNo)}`">查看详情</RouterLink>
          <button v-if="chatTraceFor(item)" class="link-btn" @click="openChatTrace(item)">私聊追溯</button>
          <button v-if="orderTraceFor(item)" class="link-btn" @click="openOrderTrace(item)">订单追溯</button>
          <button v-if="afterSalesTraceFor(item)" class="link-btn" @click="openAfterSalesTrace(item)">售后追溯</button>
        </div>
      </div>
      <div class="audit-side">
        <b :class="['status', item.status.toLowerCase()]">{{ item.status }}</b>
        <div class="actions" v-if="item.status === 'PENDING' && canReviewAuditRecord(auth.session, item.auditType)">
          <button :disabled="reviewingAuditNo === item.auditNo" @click="review(item, 'approve')">{{ reviewingAuditNo === item.auditNo ? '提交中...' : '通过' }}</button>
          <button class="danger" :disabled="reviewingAuditNo === item.auditNo" @click="review(item, 'reject')">拒绝</button>
        </div>
        <div class="permission-note" v-else-if="item.status === 'PENDING'">{{ item.auditType === 'WITHDRAWAL' ? '提现审核需 finance:review 权限，audit:review 不会触发资金审核。' : '仅拥有 audit:review 权限的管理员可提交审核动作。' }}</div>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { approveAdminAudit, approveAdminProduct, getAdminAuditList, rejectAdminAudit, type AdminAuditListQuery, type AuditRecordResponse } from '../../api'
import { canReviewAuditRecord, useAuthStore } from '../../store/modules/auth'
import { afterSalesAuditTraceLocation } from '../after-sales/after-sales-trace-links'
import { chatAuditTraceLocation } from '../chat-trace/chat-trace-links'
import { orderAuditTraceLocation } from '../orders/order-trace-links'

const audits = ref<AuditRecordResponse[]>([])
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const reviewingAuditNo = ref('')
const auditTypeFilter = ref<NonNullable<AdminAuditListQuery['auditType']>>('ALL')
const statusFilter = ref<NonNullable<AdminAuditListQuery['status']>>('PENDING')
const keyword = ref('')
const limit = ref(50)
const pendingCount = computed(() => audits.value.filter((item) => item.status === 'PENDING').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const safeKeyword = keyword.value.trim()
    audits.value = await getAdminAuditList({
      auditType: auditTypeFilter.value,
      status: statusFilter.value,
      keyword: safeKeyword || undefined,
      limit: Number(limit.value)
    })
    syncQuery()
  } catch (err) {
    audits.value = []
    error.value = err instanceof Error && err.message ? err.message : '审核列表加载失败，请确认管理员权限与服务状态。'
  } finally {
    loading.value = false
  }
}

async function review(item: AuditRecordResponse, action: 'approve' | 'reject') {
  if (reviewingAuditNo.value) return
  if (!canReviewAuditRecord(auth.session, item.auditType)) {
    error.value = item.auditType === 'WITHDRAWAL' ? '提现审核已阻止：当前管理员缺少 finance:review 权限。' : '审核操作已阻止：当前管理员缺少 audit:review 权限。'
    return
  }
  error.value = ''
  reviewingAuditNo.value = item.auditNo
  try {
    const remark = action === 'approve' ? '后台审核通过' : '后台审核拒绝'
    const isProductAudit = action === 'approve' && item.targetType === 'PRODUCT'
    if (isProductAudit) {
      await approveAdminProduct(item.targetId || '')
    }
    const updated = action === 'approve' ? await approveAdminAudit(item.auditNo, remark) : await rejectAdminAudit(item.auditNo, remark)
    audits.value = audits.value.map((row) => row.auditNo === item.auditNo ? updated : row)
  } catch {
    error.value = '审核操作失败，请确认审核服务状态。'
  } finally {
    reviewingAuditNo.value = ''
  }
}

function afterSalesTraceFor(item: AuditRecordResponse) {
  return afterSalesAuditTraceLocation(item)
}

function orderTraceFor(item: AuditRecordResponse) {
  return orderAuditTraceLocation(item)
}

function chatTraceFor(item: AuditRecordResponse) {
  return chatAuditTraceLocation(item)
}

function openChatTrace(item: AuditRecordResponse) {
  const location = chatTraceFor(item)
  if (!location) {
    error.value = '审核目标无法追溯到私聊记录。'
    return
  }
  router.push(location).catch(() => {
    error.value = '私聊追溯页面打开失败，请稍后重试。'
  })
}

function openAfterSalesTrace(item: AuditRecordResponse) {
  const location = afterSalesTraceFor(item)
  if (!location) {
    error.value = '审核目标无法追溯到售后记录。'
    return
  }
  router.push(location).catch(() => {
    error.value = '售后追溯页面打开失败，请稍后重试。'
  })
}

function openOrderTrace(item: AuditRecordResponse) {
  const location = orderTraceFor(item)
  if (!location) {
    error.value = '审核目标无法追溯到订单记录。'
    return
  }
  router.push(location).catch(() => {
    error.value = '订单追溯页面打开失败，请稍后重试。'
  })
}

function showReportsOnly() {
  auditTypeFilter.value = 'REPORT'
  statusFilter.value = 'PENDING'
  load()
}

function syncQuery() {
  router.replace({
    path: '/audit',
    query: {
      auditType: auditTypeFilter.value,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined,
      limit: String(limit.value)
    }
  }).catch(() => {})
}

function initFiltersFromRoute() {
  const routeAuditType = String(route.query.auditType || '').toUpperCase()
  if (['ALL', 'REPORT', 'WITHDRAWAL', 'VIDEO_IDENTITY', 'PRODUCT'].includes(routeAuditType)) {
    auditTypeFilter.value = routeAuditType as NonNullable<AdminAuditListQuery['auditType']>
  }
  const routeStatus = String(route.query.status || '').toUpperCase()
  if (['ALL', 'PENDING', 'APPROVED', 'REJECTED'].includes(routeStatus)) {
    statusFilter.value = routeStatus as NonNullable<AdminAuditListQuery['status']>
  }
  const routeKeyword = String(route.query.keyword || '').trim()
  if (routeKeyword) keyword.value = routeKeyword
  const routeLimit = Number(route.query.limit || '')
  if (Number.isInteger(routeLimit) && routeLimit >= 1 && routeLimit <= 100) {
    limit.value = routeLimit
  }
}

onMounted(() => {
  initFiltersFromRoute()
  load()
})
</script>
