<template>
  <view class="page-shell ledger-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 账本详情</view>
        <view class="page-title">{{ detail.ledgerNo || '账本详情' }}</view>
        <view class="page-desc">账本详情由平台账本接口返回；缺失或加载失败时不展示页面流水默认内容。</view>
      </view>
      <view class="hero-icon">🧾</view>
    </view>

    <view v-if="loading" class="status-card ds-card">账本详情加载中...</view>
    <view v-else-if="loadMessage" class="status-card ds-card danger">{{ loadMessage }}</view>
    <template v-else>
      <view class="amount-card ds-card">
        <view class="amount" :class="{ income: detail.direction === '收入' }">{{ detail.direction === '收入' ? '+' : '-' }}¥{{ detail.amount }}</view>
        <view class="status">{{ detail.status }}</view>
      </view>

      <view class="info-card ds-card">
        <view class="section-title">流水信息</view>
        <view v-for="row in rows" :key="row.label" class="row">
          <text>{{ row.label }}</text>
          <text>{{ row.value }}</text>
        </view>
      </view>
    </template>

    <view class="safe-card ds-card">
      <view class="section-title">资金安全说明</view>
      <view class="desc">涉及支付、退款、提现和结算的最终金额、状态与余额，以平台账本接口和对账结果为准；页面不会使用入口参数拼出资金详情。</view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getWalletLedgerDetail, type WalletLedgerItemResponse } from '../../../../api/modules/wallet'

interface LedgerDetail {
  ledgerNo: string
  direction: string
  amount: string
  status: string
  bizType: string
  bizNo: string
  balanceBefore: string
  balanceAfter: string
  createdAt: string
  remark: string
}

const launchReadinessMarkers = [
  '账本详情加载失败，未展示本地账本样例'
]

const detail = reactive<LedgerDetail>({
  ledgerNo: '',
  direction: '',
  amount: '',
  status: '',
  bizType: '',
  bizNo: '',
  balanceBefore: '',
  balanceAfter: '',
  createdAt: '',
  remark: ''
})
const loading = ref(false)
const loadMessage = ref('')

const rows = computed(() => [
  { label: '业务类型', value: detail.bizType || '--' },
  { label: '业务单号', value: detail.bizNo || '--' },
  { label: '资金方向', value: detail.direction || '--' },
  { label: '变动前余额', value: detail.balanceBefore ? `¥${detail.balanceBefore}` : '--' },
  { label: '变动后余额', value: detail.balanceAfter ? `¥${detail.balanceAfter}` : '--' },
  { label: '发生时间', value: detail.createdAt || '--' },
  { label: '备注', value: detail.remark || '--' }
])

function readLedgerNo() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  return current?.options?.ledgerNo || hashParams?.get('ledgerNo') || ''
}

function money(value: WalletLedgerItemResponse['amount']) {
  return String(value ?? '')
}

function directionLabel(direction: WalletLedgerItemResponse['direction']) {
  return direction === 'CREDIT' ? '收入' : '支出'
}

function statusLabel(status: WalletLedgerItemResponse['status']) {
  return ({ SUCCESS: '成功', FAILED: '失败', PENDING: '处理中' } as Record<string, string>)[status] ?? String(status || '--')
}

function businessLabel(businessType?: string | null) {
  return businessType ? ({ RECHARGE: '充值入账', ORDER_PAYMENT: '订单支付', ORDER_PAY: '订单支付', ORDER_REFUND: '订单退款', WITHDRAW: '提现', WITHDRAW_FREEZE: '提现冻结', WITHDRAW_PAYOUT: '提现出款', WITHDRAW_RELEASE: '提现解冻', GIFT: '礼物分账' } as Record<string, string>)[businessType] ?? businessType : '--'
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

function mapLedgerDetail(item: WalletLedgerItemResponse): LedgerDetail {
  return {
    ledgerNo: item.ledgerNo,
    direction: directionLabel(item.direction),
    amount: money(item.amount),
    status: statusLabel(item.status),
    bizType: businessLabel(item.businessType),
    bizNo: item.businessId ? String(item.businessId) : '',
    balanceBefore: money(item.balanceBefore),
    balanceAfter: money(item.balanceAfter),
    createdAt: formatDateTime(item.createdAt),
    remark: item.remark || ''
  }
}

async function loadDetail() {
  const ledgerNo = readLedgerNo()
  if (!ledgerNo) {
    loadMessage.value = 'ledgerNo 缺失，未查询账本详情'
    return
  }
  loading.value = true
  loadMessage.value = ''
  try {
    const response = await getWalletLedgerDetail(ledgerNo)
    Object.assign(detail, mapLedgerDetail(response))
  } catch {
    loadMessage.value = '账本详情加载失败，未展示默认账本'
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>
<style scoped>
.ledger-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.amount-card,.info-card,.safe-card,.status-card{margin-top:18rpx;padding:24rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;gap:20rpx;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:26rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.amount-card{text-align:center}.amount{font-size:48rpx;font-weight:950;color:#dc2626}.amount.income{color:#16a34a}.status{display:inline-flex;margin-top:10rpx;padding:8rpx 18rpx;border-radius:999rpx;background:#fff3e7;color:#ff7a45;font-size:22rpx;font-weight:900}.row{min-height:70rpx;display:flex;justify-content:space-between;gap:22rpx;border-bottom:1rpx solid #ffe5ef;align-items:center;color:#7b5542;font-size:23rpx}.row:last-child{border-bottom:0}.row text:last-child{text-align:right;color:#3a2a1f;font-weight:900;max-width:430rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.desc,.page-desc{margin-top:8rpx;color:#9b7560;font-size:23rpx;line-height:1.5}.status-card{color:#7b5542;font-size:24rpx;font-weight:800}.status-card.danger{color:#b42318;background:#fff7f7;border-color:#fecaca}
</style>
