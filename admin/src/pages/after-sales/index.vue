<template>
  <section class="page-shell after-sales-page">
    <div class="page-title">售后管理</div>
    <div class="page-desc">按后端售后编号读取真实售后详情；无有效编号或接口失败时不展示本地样例，也不执行本地处理成功态。</div>

    <form class="lookup-card" @submit.prevent="loadDetail">
      <label>
        <span>售后编号</span>
        <input v-model.trim="afterSalesNo" placeholder="例如 AS-ADMIN-20260510-0001" />
      </label>
      <button class="primary-btn" :disabled="loading || !afterSalesNo">{{ loading ? '查询中...' : '查询详情' }}</button>
    </form>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">售后记录加载中...</div>
    <div v-else-if="!detail" class="empty">请输入后端售后编号查询详情；未接通接口时不会展示预览或本地售后样例。</div>

    <article v-else class="detail-card">
      <div class="detail-head">
        <div>
          <strong>{{ detail.afterSalesNo }}</strong>
          <span>订单：{{ detail.orderNo }}</span>
        </div>
        <span class="status pending">{{ detail.status }}</span>
      </div>
      <dl class="detail-grid">
        <div><dt>申请人 ID</dt><dd>{{ detail.applicantId }}</dd></div>
        <div><dt>售后类型</dt><dd>{{ detail.afterSalesType }}</dd></div>
        <div><dt>退款金额</dt><dd>¥{{ detail.refundAmount }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
        <div><dt>原因</dt><dd>{{ detail.reason }}</dd></div>
        <div><dt>上传票据数量</dt><dd>{{ detail.evidenceUrls?.length || 0 }}</dd></div>
      </dl>
      <p class="safe-note">{{ detail.description || '暂无补充说明' }}</p>
      <p class="safe-note">售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准；本页当前仅查询详情，不做本地审核成功。</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getAdminAfterSalesDetail, isValidAdminAfterSalesNo, type AdminAfterSalesDetail } from '../../api'

const afterSalesNo = ref('')
const loading = ref(false)
const error = ref('')
const detail = ref<AdminAfterSalesDetail | null>(null)

async function loadDetail() {
  const safeNo = afterSalesNo.value.trim()
  loading.value = true
  error.value = ''
  detail.value = null
  if (!isValidAdminAfterSalesNo(safeNo)) {
    error.value = '售后编号无效：已阻止预览、占位或非后端编号查询。'
    loading.value = false
    return
  }
  try {
    detail.value = await getAdminAfterSalesDetail(safeNo)
  } catch {
    error.value = '售后详情加载失败：未展示本地样例，请确认管理员权限、后端服务与 /api/admin/after-sales/{afterSalesNo} 可用。'
  } finally {
    loading.value = false
  }
}
</script>
