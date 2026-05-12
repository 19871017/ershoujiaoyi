<template>
  <section class="page-shell after-sales-page">
    <div class="page-title">售后管理</div>
    <div class="page-desc">默认从后端读取待处理售后列表；详情与审核按后端售后编号执行，无有效编号或接口失败时不展示本地样例，也不执行本地处理成功态。</div>

    <div class="lookup-card">
      <label>
        <span>状态筛选</span>
        <select v-model="statusFilter" :disabled="loadingList || afterSalesReviewing" @change="loadList">
          <option value="PENDING_REVIEW">待处理</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已拒绝</option>
          <option value="ALL">全部</option>
        </select>
      </label>
      <button class="primary-btn" :disabled="loadingList || afterSalesReviewing" @click="loadList">{{ loadingList ? '刷新中...' : '刷新列表' }}</button>
    </div>

    <div v-if="listError" class="alert">{{ listError }}</div>
    <div v-if="loadingList" class="empty">售后列表加载中...</div>
    <div v-else-if="rows.length === 0" class="empty">后端未返回售后记录；本页不展示预览或本地售后样例。</div>
    <div v-else class="table-card">
      <table>
        <thead>
          <tr>
            <th>售后编号</th>
            <th>订单编号</th>
            <th>申请人</th>
            <th>金额</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.afterSalesNo">
            <td>{{ row.afterSalesNo }}</td>
            <td>{{ row.orderNo }}</td>
            <td>{{ row.applicantId }}</td>
            <td>¥{{ row.refundAmount }}</td>
            <td>{{ row.status }}</td>
            <td><button class="link-btn" :disabled="afterSalesReviewing" @click="selectDetail(row.afterSalesNo)">查看详情</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <form class="lookup-card" @submit.prevent="loadDetail">
      <label>
        <span>售后编号</span>
        <input v-model.trim="afterSalesNo" :disabled="afterSalesReviewing" placeholder="例如 AS-ADMIN-20260510-0001" />
      </label>
      <button class="primary-btn" :disabled="loadingDetail || afterSalesReviewing || !afterSalesNo">{{ loadingDetail ? '查询中...' : '查询详情' }}</button>
    </form>

    <div v-if="detailError" class="alert">{{ detailError }}</div>
    <div v-if="loadingDetail" class="empty">售后详情加载中...</div>
    <div v-else-if="!detail" class="empty">请选择列表记录或输入后端售后编号查询详情。</div>

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
      <p class="safe-note">售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准；审核成功只以后端响应为准。</p>
      <div class="review-panel">
        <label>
          <span>审核备注</span>
          <textarea v-model.trim="reviewRemark" :disabled="afterSalesReviewing || !canReviewDetail" maxlength="200" placeholder="选填，仅提交给后端售后审核接口"></textarea>
        </label>
        <label class="confirm-field">
          <span>售后审核二次确认</span>
          <input v-model.trim="afterSalesConfirmText" :disabled="afterSalesReviewing || !canReviewDetail" :placeholder="`请输入：${expectedAfterSalesConfirmText}`" />
        </label>
        <p class="safe-note">通过或拒绝售后前必须输入对应确认文本：{{ expectedAfterSalesConfirmText }}；避免误触发退款/售后流程。</p>
        <div class="review-actions">
          <button class="primary-btn" :disabled="afterSalesReviewing || !canReviewDetail" @click="submitReview('approve')">
            {{ afterSalesReviewing ? '提交中...' : '通过售后' }}
          </button>
          <button class="danger-btn" :disabled="afterSalesReviewing || !canReviewDetail" @click="submitReview('reject')">
            拒绝售后
          </button>
        </div>
        <p v-if="!canReviewAfterSales(auth.session)" class="safe-note">当前管理员仅可查看售后记录；缺少 after-sales:review 权限时不会提交通过/拒绝操作。</p>
        <p class="safe-note">审核结果只以后端 /api/admin/after-sales 审核响应为准；失败时不会本地改写售后状态。</p>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  getAdminAfterSalesDetail,
  getAdminAfterSalesList,
  isValidAdminAfterSalesNo,
  reviewAdminAfterSales,
  type AdminAfterSalesDetail,
  type AdminAfterSalesListQuery
} from '../../api'
import { canReviewAfterSales, useAuthStore } from '../../store/modules/auth'

const auth = useAuthStore()
const route = useRoute()
const afterSalesNo = ref('')
const statusFilter = ref<NonNullable<AdminAfterSalesListQuery['status']>>('PENDING_REVIEW')
const loadingList = ref(false)
const loadingDetail = ref(false)
const listError = ref('')
const detailError = ref('')
const reviewRemark = ref('')
const afterSalesConfirmText = ref('')
const afterSalesReviewing = ref(false)
const rows = ref<AdminAfterSalesDetail[]>([])
const detail = ref<AdminAfterSalesDetail | null>(null)
const canReviewDetail = computed(() => canReviewAfterSales(auth.session) && detail.value?.status === 'PENDING_REVIEW')
const expectedAfterSalesConfirmText = computed(() => `售后审核${detail.value?.afterSalesNo || ''}`)

async function loadList() {
  if (afterSalesReviewing.value) return
  loadingList.value = true
  listError.value = ''
  rows.value = []
  try {
    rows.value = await getAdminAfterSalesList({ status: statusFilter.value, limit: 20 })
  } catch {
    listError.value = '售后列表加载失败：未展示本地样例，请确认管理员权限、后端服务与 /api/admin/after-sales 可用。'
  } finally {
    loadingList.value = false
  }
}

async function selectDetail(no: string) {
  if (afterSalesReviewing.value) return
  afterSalesNo.value = no
  afterSalesConfirmText.value = ''
  reviewRemark.value = ''
  await loadDetail()
}

async function loadDetail() {
  if (afterSalesReviewing.value) return
  const safeNo = afterSalesNo.value.trim()
  loadingDetail.value = true
  detailError.value = ''
  detail.value = null
  afterSalesConfirmText.value = ''
  reviewRemark.value = ''
  if (!isValidAdminAfterSalesNo(safeNo)) {
    detailError.value = '售后编号无效：已阻止预览、占位或非后端编号查询。'
    loadingDetail.value = false
    return
  }
  try {
    detail.value = await getAdminAfterSalesDetail(safeNo)
  } catch {
    detailError.value = '售后详情加载失败：未展示本地样例，请确认管理员权限、后端服务与 /api/admin/after-sales/{afterSalesNo} 可用。'
  } finally {
    loadingDetail.value = false
  }
}

async function submitReview(action: 'approve' | 'reject') {
  const safeNo = detail.value?.afterSalesNo || afterSalesNo.value.trim()
  detailError.value = ''
  if (afterSalesReviewing.value) return
  if (!isValidAdminAfterSalesNo(safeNo) || !detail.value) {
    detailError.value = '售后编号无效：已阻止本地审核提交。'
    return
  }
  if (!canReviewAfterSales(auth.session)) {
    detailError.value = '当前管理员缺少售后审核权限：未执行任何本地变更。'
    return
  }
  if (!canReviewDetail.value) {
    detailError.value = '当前售后状态不可审核：未执行任何本地变更。'
    return
  }
  if (afterSalesConfirmText.value !== expectedAfterSalesConfirmText.value) {
    detailError.value = `售后审核二次确认失败：请输入“${expectedAfterSalesConfirmText.value}”。`
    return
  }
  afterSalesReviewing.value = true
  try {
    const reviewed = await reviewAdminAfterSales(safeNo, action, reviewRemark.value)
    detail.value = reviewed
    rows.value = rows.value.map((row) => row.afterSalesNo === safeNo ? reviewed : row)
    reviewRemark.value = ''
    afterSalesConfirmText.value = ''
  } catch {
    detailError.value = '售后审核提交失败：未执行任何本地状态变更，请确认权限、编号与后端接口。'
  } finally {
    afterSalesReviewing.value = false
  }
}

onMounted(() => {
  const routeAfterSalesNo = String(route.params.afterSalesNo || '').trim()
  loadList()
  if (routeAfterSalesNo) {
    afterSalesNo.value = routeAfterSalesNo
    loadDetail()
  }
})
</script>
