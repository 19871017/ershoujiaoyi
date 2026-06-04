<template>
  <section class="page-shell">
    <div class="page-title">审核详情</div>
    <div class="page-desc">读取持久化审核记录，展示目标、状态与备注。</div>

    <div class="toolbar">
      <RouterLink class="secondary-btn" to="/audit">返回审核工作台</RouterLink>
      <button class="primary-btn" :disabled="loading || !safeAuditNo" @click="load">刷新详情</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">审核详情加载中...</div>
    <div v-else-if="!detail" class="empty">请输入有效审核编号。</div>

    <article v-else class="detail-card">
      <header class="detail-head">
        <div>
          <strong>{{ detail.auditNo }}</strong>
          <span>{{ detail.auditType }} / {{ detail.targetType || '未标注目标' }}</span>
        </div>
        <b :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</b>
      </header>
      <dl class="detail-grid">
        <div><dt>目标编号</dt><dd>{{ detail.targetId || '暂无' }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
        <div><dt>复核时间</dt><dd>{{ detail.reviewedAt || '未复核' }}</dd></div>
        <div><dt>审核备注</dt><dd>{{ detail.reviewRemark || '暂无' }}</dd></div>
      </dl>
      <div v-if="orderTraceLocation || afterSalesTraceLocation" class="toolbar detail-trace-actions">
        <button v-if="orderTraceLocation" class="secondary-btn" @click="openOrderTrace">订单追溯</button>
        <button v-if="afterSalesTraceLocation" class="secondary-btn" @click="openAfterSalesTrace">售后追溯</button>
      </div>
      <div v-if="videoEvidenceUrl" class="media-panel">
        <strong>视频认证资料</strong>
        <video class="audit-video" :src="videoEvidenceUrl" controls playsinline></video>
        <a class="detail-link" :href="videoEvidenceUrl" target="_blank" rel="noopener noreferrer">新窗口打开视频</a>
      </div>
      <p class="detail-desc">{{ detailSummary }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink, useRouter } from 'vue-router'
import { getAdminAuditDetail, isValidAdminAuditNo, type AuditRecordResponse } from '../../api'
import { afterSalesAuditTraceLocation } from '../after-sales/after-sales-trace-links'
import { orderAuditTraceLocation } from '../orders/order-trace-links'

const route = useRoute()
const router = useRouter()
const detail = ref<AuditRecordResponse | null>(null)
const loading = ref(false)
const error = ref('')

const safeAuditNo = computed(() => {
  const value = String(route.params.auditNo || '')
  return isValidAdminAuditNo(value) ? value : ''
})
const videoEvidenceUrl = computed(() => {
  const current = detail.value
  const reason = current?.reason || ''
  return current?.auditType === 'VIDEO_IDENTITY' && reason.startsWith('/uploads/') ? reason : ''
})
const detailSummary = computed(() => {
  const current = detail.value
  if (!current) return ''
  if (current.auditType === 'VIDEO_IDENTITY') return current.description || '视频认证资料以平台上传票据为准。'
  return current.reason || current.description || '无补充说明'
})
const afterSalesTraceLocation = computed(() => detail.value ? afterSalesAuditTraceLocation(detail.value) : null)
const orderTraceLocation = computed(() => detail.value ? orderAuditTraceLocation(detail.value) : null)

async function load() {
  detail.value = null
  error.value = ''
  if (!safeAuditNo.value) {
    error.value = '审核编号无效，请输入正确的审核编号。'
    return
  }
  loading.value = true
  try {
    detail.value = await getAdminAuditDetail(safeAuditNo.value)
  } catch {
    detail.value = null
    error.value = '审核详情加载失败，请确认管理员权限与审核编号。'
  } finally {
    loading.value = false
  }
}

function openAfterSalesTrace() {
  if (!afterSalesTraceLocation.value) {
    error.value = '审核目标无法追溯到售后记录。'
    return
  }
  router.push(afterSalesTraceLocation.value).catch(() => {
    error.value = '售后追溯页面打开失败，请稍后重试。'
  })
}

function openOrderTrace() {
  if (!orderTraceLocation.value) {
    error.value = '审核目标无法追溯到订单记录。'
    return
  }
  router.push(orderTraceLocation.value).catch(() => {
    error.value = '订单追溯页面打开失败，请稍后重试。'
  })
}

onMounted(load)
</script>
