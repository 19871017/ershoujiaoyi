<template>
  <section class="page-shell finance-page">
    <div class="page-title">提现审核</div>
    <div class="page-desc">按提现编号读取真实详情，仅展示脱敏收款账号；通过或拒绝后会重新读取详情。</div>

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
    <div v-else-if="withdrawals.length === 0" class="empty">暂无提现记录。</div>
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
    <div v-else-if="!detail" class="empty">请输入提现编号查询详情；服务不可用时不会展示提现详情。</div>

    <article v-else-if="currentWithdrawal" class="detail-card">
      <div class="detail-head">
        <div>
          <strong>{{ currentWithdrawal.withdrawalNo }}</strong>
          <span>关联审核：{{ currentWithdrawal.auditNo || '暂无' }}</span>
        </div>
        <b :class="['status', currentWithdrawal.status.toLowerCase()]">{{ currentWithdrawal.status }}</b>
      </div>
      <dl class="detail-grid">
        <div><dt>用户 ID</dt><dd>{{ currentWithdrawal.userId }}</dd></div>
        <div><dt>提现金额</dt><dd>¥{{ currentWithdrawal.amount }}</dd></div>
        <div><dt>收款方式</dt><dd>{{ currentWithdrawal.paymentMethod }}</dd></div>
        <div><dt>户名</dt><dd>{{ currentWithdrawal.accountName }}</dd></div>
        <div><dt>脱敏账号</dt><dd>{{ currentWithdrawal.maskedAccountNo }}</dd></div>
        <div><dt>实名一致性</dt><dd>{{ currentWithdrawal.accountVerifyStatus || '以平台记录为准' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ currentWithdrawal.createdAt || '暂无' }}</dd></div>
        <div><dt>复核时间</dt><dd>{{ currentWithdrawal.reviewedAt || '未复核' }}</dd></div>
      </dl>
      <section class="review-context">
        <div class="context-card">
          <h3>用户与实名</h3>
          <p>{{ detail.user.nickname }} / {{ detail.user.userNo || '无用户号' }} / {{ detail.user.status }}</p>
          <p>实名：{{ detail.user.identityStatus }}；角色：{{ detail.user.mainRole }}；城市：{{ detail.user.city || '未填写' }}</p>
          <p>视频认证：{{ detail.user.videoIdentityStatus }} / {{ detail.user.videoVerified ? '已通过' : '未通过' }}</p>
        </div>
        <div class="context-card">
          <h3>钱包余额</h3>
          <p>可提现 ¥{{ detail.balance.withdrawableBalance }} / 冻结 ¥{{ detail.balance.frozenBalance }}</p>
          <p>充值 ¥{{ detail.balance.rechargeBalance }} / 收入 ¥{{ detail.balance.incomeBalance }}</p>
        </div>
      </section>
      <section class="ledger-context">
        <div class="section-title">最近钱包流水</div>
        <div v-if="detail.recentLedgers.length === 0" class="empty small">暂无钱包流水。</div>
        <div v-for="ledger in detail.recentLedgers" :key="ledger.ledgerNo" class="ledger-row">
          <div>
            <strong>{{ ledger.businessType }}</strong>
            <span>{{ ledger.ledgerNo }}</span>
          </div>
          <div class="ledger-side">
            <b>{{ ledger.direction === 'CREDIT' ? '+' : '-' }}{{ ledger.amount }}</b>
            <span>{{ ledger.balanceType }} / {{ ledger.status }}</span>
          </div>
        </div>
      </section>
      <p class="safe-note">本页不接收或展示完整收款账号；审核提交必须依赖关联审核编号。</p>
      <form v-if="currentWithdrawal.auditNo && currentWithdrawal.status === 'PENDING' && canReviewFinance(auth.session)" class="review-card" @submit.prevent>
        <label>
          <span>审核备注</span>
          <input v-model.trim="reviewRemark" placeholder="请填写本次提现复核备注" />
        </label>
        <label class="confirm-field">
          <span>提现审核二次确认</span>
          <input v-model.trim="reviewConfirmText" :placeholder="`请输入：${expectedReviewConfirmText}`" />
        </label>
        <p class="safe-note">通过或拒绝提现前必须输入对应确认文本：{{ expectedReviewConfirmText }}；避免误触发资金复核。</p>
        <div class="actions">
          <button class="primary-btn" :disabled="reviewing" @click="submitReview('approve')">{{ reviewing ? '提交中...' : '通过提现审核' }}</button>
          <button class="danger" :disabled="reviewing" @click="submitReview('reject')">拒绝提现审核</button>
        </div>
        <p class="safe-note">审核动作提交后会重新读取提现详情；状态以平台返回为准。</p>
      </form>
      <div v-else-if="currentWithdrawal.status === 'PENDING' && !canReviewFinance(auth.session)" class="alert">当前管理员缺少 finance:review 权限，已阻止提现审核操作。</div>
      <div v-else-if="currentWithdrawal.status === 'PENDING'" class="alert">该提现记录缺少审核编号，已阻止本页审核操作。</div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getAdminWithdrawalDetail, getAdminWithdrawalList, isValidAdminWithdrawalNo, reviewAdminWithdrawal, type AdminWithdrawalDetail, type AdminWithdrawalReviewDetail, type WithdrawalStatus } from '../../../api'
import { canReviewFinance, useAuthStore } from '../../../store/modules/auth'

const auth = useAuthStore()
const route = useRoute()
const withdrawalNo = ref('')
const loading = ref(false)
const listLoading = ref(false)
const reviewing = ref(false)
const error = ref('')
const detail = ref<AdminWithdrawalReviewDetail | null>(null)
const withdrawals = ref<AdminWithdrawalDetail[]>([])
const statusFilter = ref<WithdrawalStatus | 'ALL'>('PENDING')
const listLimit = ref(20)
const reviewRemark = ref('')
const reviewConfirmText = ref('')
const currentWithdrawal = computed(() => detail.value?.withdrawal)
const expectedReviewConfirmText = computed(() => `提现审核${currentWithdrawal.value?.withdrawalNo || ''}`)

async function loadDetail() {
  const safeNo = withdrawalNo.value.trim()
  loading.value = true
  error.value = ''
  detail.value = null
  if (!isValidAdminWithdrawalNo(safeNo)) {
    error.value = '提现编号无效，请输入正确的提现编号。'
    loading.value = false
    return
  }
  try {
    detail.value = await getAdminWithdrawalDetail(safeNo)
    reviewRemark.value = detail.value.withdrawal.remark || ''
    reviewConfirmText.value = ''
  } catch {
    error.value = '提现详情加载失败，请确认管理员权限与提现编号。'
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
    error.value = '提现列表加载失败，请确认管理员权限与服务状态。'
  } finally {
    listLoading.value = false
  }
}

function selectWithdrawal(item: AdminWithdrawalDetail) {
  withdrawalNo.value = item.withdrawalNo
  void loadDetail()
  reviewRemark.value = item.remark || ''
  reviewConfirmText.value = ''
}

async function submitReview(action: 'approve' | 'reject') {
  const current = detail.value?.withdrawal
  if (!current) return
  error.value = ''
  if (!canReviewFinance(auth.session)) {
    error.value = '提现审核已阻止：当前管理员缺少 finance:review 权限。'
    return
  }
  if (!current.auditNo) {
    error.value = '提现记录缺少审核编号，已阻止审核提交。'
    return
  }
  if (reviewConfirmText.value !== expectedReviewConfirmText.value) {
    error.value = `提现审核二次确认失败：请输入“${expectedReviewConfirmText.value}”。`
    return
  }
  reviewing.value = true
  try {
    detail.value = await reviewAdminWithdrawal(current.withdrawalNo, current.auditNo, action, reviewRemark.value || (action === 'approve' ? '提现审核通过' : '提现审核拒绝'))
    reviewConfirmText.value = ''
    await loadList()
  } catch {
    error.value = '提现审核提交失败，请确认审核服务、权限与记录状态。'
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
