<template>
  <view class="page-shell admin-home">
    <view class="dashboard-hero ds-card">
      <view>
        <view class="kicker">Admin Console</view>
        <view class="page-title">后台首页</view>
        <view class="page-desc">审核、提现、风控和定位配置集中处理。</view>
      </view>
      <button class="refresh-btn" :disabled="loading" @click="loadAdminData">{{ loading ? '刷新中' : '刷新' }}</button>
    </view>

    <view class="metric-grid">
      <view v-for="item in metrics" :key="item.label" class="metric-card ds-card">
        <view class="metric-num">{{ item.num }}</view>
        <view class="metric-label">{{ item.label }}</view>
      </view>
    </view>

    <view class="tab-row">
      <view v-for="item in tabs" :key="item.value" class="tab-chip tapable" :class="{ active: tab === item.value }" @click="tab = item.value">{{ item.label }}</view>
    </view>

    <view v-if="errorMessage" class="error-card ds-card">{{ errorMessage }}</view>

    <view v-if="tab === 'audit'" class="panel ds-card">
      <view class="section-title">审核中心</view>
      <view v-if="audits.length === 0" class="empty">暂无审核单</view>
      <view v-for="item in audits" :key="item.auditNo" class="audit-item">
        <view class="audit-head tapable" @click="openAuditDetail(item.auditNo)">
          <view>
            <view class="audit-title">{{ item.auditNo }}</view>
            <view class="audit-meta">{{ item.auditType }} · {{ item.targetType }} #{{ item.targetId }}</view>
          </view>
          <view class="status" :class="item.status">{{ statusLabel(item.status) }}</view>
        </view>
        <view class="reason">{{ item.reason }} {{ item.description || '' }}</view>
        <view class="actions">
          <button class="mini-btn primary" @click="approve(item.auditNo)">通过</button>
          <button class="mini-btn" @click="reject(item.auditNo)">拒绝</button>
        </view>
      </view>
    </view>

    <view v-if="tab === 'withdraw'" class="panel ds-card">
      <view class="section-title">提现审核</view>
      <view class="section-desc">核对实名、收款账户、冻结金额和账本影响后再处理。</view>
      <view v-for="item in withdrawItems" :key="item.no" class="simple-row tapable" @click="openWithdrawDetail(item)">
        <view>
          <view class="row-title">{{ item.no }} · ¥{{ item.amount }}</view>
          <view class="row-desc">{{ item.user }} · {{ item.method }} · {{ item.status }}</view>
        </view>
        <view class="row-action">详情</view>
      </view>
    </view>

    <view v-if="tab === 'risk'" class="panel ds-card">
      <view class="section-title">风控看板</view>
      <view class="risk-grid">
        <view v-for="item in riskItems" :key="item.label" class="risk-card">
          <view class="risk-num">{{ item.num }}</view>
          <view class="risk-label">{{ item.label }}</view>
        </view>
      </view>
    </view>

    <view v-if="tab === 'config'" class="panel ds-card">
      <view class="section-title">定位配置</view>
      <view class="section-desc">AK 不在前端展示；如需设置，请通过后台接口安全写入。</view>
      <input v-model.trim="locationForm.defaultProvince" class="field" placeholder="默认省份" />
      <input v-model.trim="locationForm.defaultCity" class="field" placeholder="默认城市" />
      <view class="method-row">
        <view class="method-chip tapable" :class="{ active: locationForm.provider === 'BAIDU' }" @click="locationForm.provider = 'BAIDU'">百度</view>
        <view class="method-chip tapable" :class="{ active: locationForm.provider === 'MANUAL' }" @click="locationForm.provider = 'MANUAL'">手动兜底</view>
      </view>
      <button class="primary-btn" :disabled="savingConfig" @click="saveLocationConfig">{{ savingConfig ? '保存中' : '保存定位配置' }}</button>
      <view v-if="configMessage" class="section-desc">{{ configMessage }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { adminDashboard, approveAudit, getAdminAuditList, rejectAudit } from '../../../api/modules/admin'
import type { AuditRecordResponse } from '../../../api/modules/audit'
import { getAdminLocationConfig, updateAdminLocationConfig } from '../../../api/modules/location'
import { isDevRuntimeEnabled } from '../../../api/http'

type Tab = 'audit' | 'withdraw' | 'risk' | 'config'

function adminDevHeader() {
  if (!isDevRuntimeEnabled()) {
    throw new Error('后台开发模式未启用，请接入正式管理员登录后再访问')
  }
  return { 'X-Admin-Mode': 'enabled', 'X-User-Id': '1', 'X-Dev-Mode': 'enabled' }
}

const tabs: Array<{ label: string; value: Tab }> = [
  { label: '审核', value: 'audit' }, { label: '提现', value: 'withdraw' }, { label: '风控', value: 'risk' }, { label: '配置', value: 'config' }
]
const tab = ref<Tab>('audit')
const loading = ref(false)
const dashboard = ref('未加载')
const audits = ref<AuditRecordResponse[]>([])
const errorMessage = ref('')
const savingConfig = ref(false)
const configMessage = ref('')
const locationForm = reactive({ provider: 'BAIDU' as 'BAIDU' | 'MANUAL', defaultProvince: '', defaultCity: '' })
const withdrawItems = computed(() => audits.value
  .filter((item) => item.auditType === 'WITHDRAWAL')
  .map((item) => ({
    no: item.targetId,
    auditNo: item.auditNo,
    user: `User ${item.userId}`,
    amount: extractAmount(item.description),
    method: extractPaymentMethod(item.description),
    status: statusLabel(item.status),
    rawStatus: item.status
  })))
const riskItems = computed(() => {
  const pending = audits.value.filter((item) => item.status === 'PENDING')
  return [
    { label: '举报待处理', num: pending.filter((item) => item.auditType === 'REPORT').length },
    { label: '提现复核', num: pending.filter((item) => item.auditType === 'WITHDRAWAL').length },
    { label: '视频认证', num: pending.filter((item) => item.auditType === 'VIDEO_IDENTITY').length },
    { label: '商品审核', num: pending.filter((item) => item.auditType === 'GOODS').length }
  ]
})
const metrics = computed(() => [
  { label: '仪表盘', num: dashboard.value === '未加载' ? '--' : 'OK' },
  { label: '待审核', num: audits.value.filter((item) => item.status === 'PENDING').length },
  { label: '提现待审', num: withdrawItems.value.filter((item) => item.rawStatus === 'PENDING').length },
  { label: '风控预警', num: riskItems.value.reduce((sum, item) => sum + item.num, 0) }
])
async function loadAdminData() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [dashboardText, list] = await Promise.all([adminDashboard(), getAdminAuditList()])
    dashboard.value = dashboardText
    audits.value = list
  } catch {
    dashboard.value = '未连接'
    audits.value = []
    errorMessage.value = '后台数据加载失败：已阻断本地审核样例与伪审核单'
  } finally { loading.value = false }
  await loadLocationConfig()
}
async function loadLocationConfig() {
  try {
    const config = await getAdminLocationConfig(adminDevHeader())
    locationForm.provider = config.provider === 'MANUAL' ? 'MANUAL' : 'BAIDU'
    locationForm.defaultProvince = config.defaultProvince || '广东省'
    locationForm.defaultCity = config.defaultCity || '深圳市'
  } catch {
    locationForm.defaultProvince = '广东省'
    locationForm.defaultCity = '深圳市'
  }
}
function openAuditDetail(auditNo: string) { uni.navigateTo({ url: `/pages/admin/audit/detail/index?auditNo=${encodeURIComponent(auditNo)}` }) }
async function approve(auditNo: string) { await review(auditNo, true) }
async function reject(auditNo: string) { await review(auditNo, false) }
async function review(auditNo: string, ok: boolean) {
  try {
    ok ? await approveAudit(auditNo, { remark: '前端审核通过' }) : await rejectAudit(auditNo, { remark: '前端审核拒绝' })
    const target = audits.value.find((item) => item.auditNo === auditNo)
    if (target) target.status = ok ? 'APPROVED' : 'REJECTED'
  } catch {
    uni.showToast({ title: '审核状态未同步', icon: 'none' })
  }
}
async function saveLocationConfig() {
  savingConfig.value = true
  configMessage.value = ''
  try {
    const result = await updateAdminLocationConfig({ provider: locationForm.provider, defaultProvince: locationForm.defaultProvince, defaultCity: locationForm.defaultCity }, adminDevHeader())
    configMessage.value = `定位配置已同步：${result.defaultProvince}${result.defaultCity}`
  } catch { configMessage.value = '定位配置保存失败，请检查管理员权限或稍后重试' }
  finally { savingConfig.value = false }
}
function statusLabel(status: string) { return ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' } as Record<string, string>)[status] ?? status }
function extractAmount(description?: string | null) {
  const match = (description || '').match(/(?:金额|amount)[:：]?\s*([0-9]+(?:\.[0-9]{1,2})?)/i)
  return match?.[1] || '--'
}
function extractPaymentMethod(description?: string | null) {
  if (!description) return '服务端记录'
  if (description.includes('支付宝')) return '支付宝'
  if (description.includes('银行卡')) return '银行卡'
  if (description.includes('微信')) return '微信'
  return '服务端记录'
}
function openWithdrawDetail(item: { no: string; auditNo?: string }) {
  const query = [`withdrawNo=${encodeURIComponent(item.no)}`]
  if (item.auditNo) query.push(`auditNo=${encodeURIComponent(item.auditNo)}`)
  uni.navigateTo({ url: `/pages/admin/withdraw/detail/index?${query.join('&')}` })
}
onMounted(loadAdminData)
</script>

<style scoped>
.admin-home { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.dashboard-hero { padding:24rpx; display:flex; justify-content:space-between; gap:18rpx; border-color:#ffd9bd; }
.kicker { color:#ff7a45; font-size:21rpx; font-weight:950; }
.refresh-btn { margin:0; width:130rpx; height:64rpx; line-height:64rpx; border-radius:999rpx; background:#3a2a1f; color:#fff; font-size:22rpx; font-weight:950; }
.metric-grid { margin-top:18rpx; display:grid; grid-template-columns:repeat(4,1fr); gap:10rpx; }
.metric-card { padding:16rpx 8rpx; text-align:center; border-color:#ffd9bd; }
.metric-num { color:#ff7a45; font-size:30rpx; font-weight:950; }
.metric-label { margin-top:5rpx; color:#9b7560; font-size:18rpx; font-weight:850; }
.tab-row { margin-top:18rpx; display:flex; gap:12rpx; overflow-x:auto; }
.tab-chip,.method-chip { flex:none; padding:13rpx 20rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:22rpx; font-weight:900; }
.tab-chip.active,.method-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.error-card,.panel { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.error-card { color:#dc2626; }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.section-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.45; }
.empty { margin-top:18rpx; color:#b9856a; font-size:24rpx; }
.audit-item,.simple-row { margin-top:16rpx; padding:18rpx; border-radius:22rpx; background:#fffaf6; }
.audit-head,.simple-row { display:flex; justify-content:space-between; gap:18rpx; align-items:flex-start; }
.audit-title,.row-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.audit-meta,.row-desc,.reason { margin-top:7rpx; color:#9b7560; font-size:21rpx; }
.status { padding:8rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:950; }
.status.APPROVED { background:#f0fdf4; color:#15803d; }
.status.REJECTED { background:#fff1f2; color:#be123c; }
.actions { margin-top:16rpx; display:flex; justify-content:flex-end; gap:12rpx; }
.mini-btn { margin:0; width:120rpx; height:54rpx; line-height:54rpx; border-radius:999rpx; color:#7b5542; background:#fff; font-size:21rpx; font-weight:900; }
.mini-btn.primary { color:#fff; background:#ff7a45; }
.row-action { padding:9rpx 16rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:21rpx; font-weight:950; }
.risk-grid { margin-top:18rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:12rpx; }
.risk-card { padding:20rpx; border-radius:22rpx; background:#fffaf6; }
.risk-num { color:#ff3f8d; font-size:36rpx; font-weight:950; }
.risk-label { margin-top:6rpx; color:#9b7560; font-size:22rpx; }
.field { box-sizing:border-box; width:100%; margin-top:16rpx; padding:19rpx; border-radius:20rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:26rpx; }
.method-row { margin-top:16rpx; display:flex; gap:12rpx; }
.primary-btn { margin-top:20rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:26rpx; font-weight:950; }
</style>
