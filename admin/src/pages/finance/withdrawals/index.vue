<template>
  <section class="page-shell finance-page">
    <div class="page-title">提现审核</div>
    <div class="page-desc">按后端提现编号读取真实详情，仅展示脱敏收款账号；通过/拒绝会调用后端审核接口并重新读取详情。</div>

    <form class="lookup-card" @submit.prevent="loadDetail">
      <label>
        <span>提现编号</span>
        <input v-model.trim="withdrawalNo" placeholder="例如 WD-20260510-0001" />
      </label>
      <button class="primary-btn" :disabled="loading || !withdrawalNo">{{ loading ? '查询中...' : '查询详情' }}</button>
    </form>

    <form class="toolbar" @submit.prevent="loadList">
      <label>
        <span>列表状态</span>
        <select v-model="statusFilter">
          <option value="PENDING">待复核</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已拒绝</option>
          <option value="ALL">全部</option>
        </select>
      </label>
      <label>
        <span>条数</span>
        <input v-model.number="listLimit" type="number" min="1" max="100" />
      </label>
      <button class="primary-btn" :disabled="listLoading">{{ listLoading ? '加载中...' : '加载提现列表' }}</button>
    </form>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="listLoading" class="empty">提现列表加载中...</div>
    <div v-else-if="withdrawals.length === 0" class="empty">暂无后端提现列表记录；不会展示本地提现样例。</div>
    <article v-for="item in withdrawals" :key="item.withdrawalNo" class="audit-card">
      <div class="audit-main">
        <strong>{{ item.withdrawalNo }}</strong>
        <span>用户 {{ item.userId }} / ¥{{ item.amount }} / {{ item.paymentMethod }}</span>
        <p>脱敏账号：{{ item.maskedAccountNo }}；关联审核：{{ item.auditNo || '暂无' }}</p>
      </div>
      <div class="audit-side">
        <span class="status-pill">{{ item.status }}</span>
        <button @click="selectWithdrawal(item)">查看/复核</button>
      </div>
    </article>

    <div v-if="loading" class="empty">提现记录加载中...</div>
    <div v-else-if="!detail" class="empty">请输入后端提现编号查询详情；未接通接口时不会展示本地样例。</div>

    <article v-else class="detail-card">
      <div class="detail-head">
        <div>
          <strong>{{ detail.withdrawalNo }}</strong>
          <span>关联审核：{{ detail.auditNo || '暂无' }}</span>
        </div>
        <b :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</b>
      </div>
      <dl class="detail-grid">
        <div><dt>用户 ID</dt><dd>{{ detail.userId }}</dd></div>
        <div><dt>提现金额</dt><dd>¥{{ detail.amount }}</dd></div>
        <div><dt>收款方式</dt><dd>{{ detail.paymentMethod }}</dd></div>
        <div><dt>户名</dt><dd>{{ detail.accountName }}</dd></div>
        <div><dt>脱敏账号</dt><dd>{{ detail.maskedAccountNo }}</dd></div>
        <div><dt>实名一致性</dt><dd>{{ detail.accountVerifyStatus || '以后端记录为准' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
        <div><dt>复核时间</dt><dd>{{ detail.reviewedAt || '未复核' }}</dd></div>
      </dl>
      <p class="safe-note">本页不接收或展示完整收款账号；审核提交必须依赖关联后端审核编号，接口失败时不做本地成功态。</p>
      <form v-if="detail.auditNo && detail.status === 'PENDING' && canReviewFinance(auth.session)" class="review-card" @submit.prevent>
        <label>
          <span>审核备注</span>
          <input v-model.trim="reviewRemark" placeholder="请填写本次提现复核备注" />
        </label>
        <div class="actions">
          <button class="primary-btn" :disabled="reviewing" @click="submitReview('approve')">{{ reviewing ? '提交中...' : '通过提现审核' }}</button>
          <button class="danger" :disabled="reviewing" @click="submitReview('reject')">拒绝提现审核</button>
        </div>
        <p class="safe-note">审核动作调用 /api/admin/audit/{auditNo}/approve|reject 后重新读取提现详情；不会在本地提前改状态。</p>
      </form>
      <div v-else-if="detail.status === 'PENDING' && !canReviewFinance(auth.session)" class="alert">当前管理员缺少 finance:review 权限，已阻止提现审核操作。</div>
      <div v-else-if="detail.status === 'PENDING'" class="alert">该提现记录缺少后端审核编号，已阻止本页审核操作。</div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getAdminWithdrawalDetail, getAdminWithdrawalList, isValidAdminWithdrawalNo, reviewAdminWithdrawal, type AdminWithdrawalDetail, type WithdrawalStatus } from '../../../api'
import { canReviewFinance, useAuthStore } from '../../../store/modules/auth'

const auth = useAuthStore()
const route = useRoute()
const withdrawalNo = ref('')
const loading = ref(false)
const listLoading = ref(false)
const reviewing = ref(false)
const error = ref('')
const detail = ref<AdminWithdrawalDetail | null>(null)
const withdrawals = ref<AdminWithdrawalDetail[]>([])
const statusFilter = ref<WithdrawalStatus | 'ALL'>('PENDING')
const listLimit = ref(20)
const reviewRemark = ref('')

async function loadDetail() {
  const safeNo = withdrawalNo.value.trim()
  loading.value = true
  error.value = ''
  detail.value = null
  if (!isValidAdminWithdrawalNo(safeNo)) {
    error.value = '提现编号无效：已阻止预览、占位或非后端编号查询。'
    loading.value = false
    return
  }
  try {
    detail.value = await getAdminWithdrawalDetail(safeNo)
    reviewRemark.value = detail.value.remark || ''
  } catch {
    error.value = '提现详情加载失败：未展示本地样例，请确认管理员权限、后端服务与 /api/admin/withdrawals/{withdrawalNo} 可用。'
  } finally {
    loading.value = false
  }
}

async function loadList() {
  listLoading.value = true
  error.value = ''
  withdrawals.value = []
  try {
    withdrawals.value = await getAdminWithdrawalList({ status: statusFilter.value, limit: listLimit.value })
  } catch {
    error.value = '提现列表加载失败：未展示本地样例，请确认管理员权限、后端服务与 /api/admin/withdrawals 可用。'
  } finally {
    listLoading.value = false
  }
}

function selectWithdrawal(item: AdminWithdrawalDetail) {
  withdrawalNo.value = item.withdrawalNo
  detail.value = item
  reviewRemark.value = item.remark || ''
}

async function submitReview(action: 'approve' | 'reject') {
  const current = detail.value
  if (!current) return
  error.value = ''
  if (!canReviewFinance(auth.session)) {
    error.value = '提现审核已阻止：当前管理员缺少 finance:review 权限。'
    return
  }
  if (!current.auditNo) {
    error.value = '提现记录缺少后端审核编号，已阻止审核提交。'
    return
  }
  reviewing.value = true
  try {
    detail.value = await reviewAdminWithdrawal(current.withdrawalNo, current.auditNo, action, reviewRemark.value || (action === 'approve' ? '提现审核通过' : '提现审核拒绝'))
    await loadList()
  } catch {
    error.value = '提现审核提交失败：未执行本地状态变更，请确认后端审核接口、权限与记录状态。'
  } finally {
    reviewing.value = false
  }
}

onMounted(() => {
  const routeWithdrawalNo = String(route.params.withdrawalNo || '').trim()
  if (routeWithdrawalNo) {
    withdrawalNo.value = routeWithdrawalNo
    loadDetail()
  }
  loadList()
})
</script>
