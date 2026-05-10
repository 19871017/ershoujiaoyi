<template>
  <section class="page-shell">
    <div class="page-title">审核工作台</div>
    <div class="page-desc">统一处理举报、提现、视频认证、商品审核等后台记录。</div>
    <div class="toolbar">
      <button class="primary-btn" @click="load">刷新</button>
      <span>待审 {{ pendingCount }} 条</span>
    </div>
    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">审核记录加载中...</div>
    <div v-else-if="audits.length === 0" class="empty">暂无后端审核记录。</div>
    <article v-for="item in audits" :key="item.auditNo" class="audit-card">
      <div class="audit-main">
        <strong>{{ item.auditNo }}</strong>
        <span>{{ item.auditType }} / {{ item.targetType || '未标注目标' }}</span>
        <p>{{ item.reason || item.description || '无补充说明' }}</p>
        <RouterLink class="detail-link" :to="`/audit/${encodeURIComponent(item.auditNo)}`">查看详情</RouterLink>
      </div>
      <div class="audit-side">
        <b :class="['status', item.status.toLowerCase()]">{{ item.status }}</b>
        <div class="actions" v-if="item.status === 'PENDING'">
          <button :disabled="reviewingAuditNo === item.auditNo" @click="review(item, 'approve')">{{ reviewingAuditNo === item.auditNo ? '提交中...' : '通过' }}</button>
          <button class="danger" :disabled="reviewingAuditNo === item.auditNo" @click="review(item, 'reject')">拒绝</button>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { approveAdminAudit, approveAdminProduct, getAdminAuditList, rejectAdminAudit, type AuditRecordResponse } from '../../api'

const audits = ref<AuditRecordResponse[]>([])
const loading = ref(false)
const error = ref('')
const reviewingAuditNo = ref('')
const pendingCount = computed(() => audits.value.filter((item) => item.status === 'PENDING').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    audits.value = await getAdminAuditList()
  } catch {
    audits.value = []
    error.value = '审核列表加载失败：请确认后端服务、管理员权限与 /api/admin/audit 可用。'
  } finally {
    loading.value = false
  }
}

async function review(item: AuditRecordResponse, action: 'approve' | 'reject') {
  if (reviewingAuditNo.value) return
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
    error.value = '审核操作失败：未做本地假成功，请检查后端审核接口。'
  } finally {
    reviewingAuditNo.value = ''
  }
}

onMounted(load)
</script>
