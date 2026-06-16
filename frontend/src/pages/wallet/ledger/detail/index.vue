<template>
  <view class="page-shell ledger-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 账本详情</view>
        <view class="page-title">{{ detail.ledgerNo || '账本详情' }}</view>
        <view class="page-desc">账本详情由平台账本返回；缺失或加载失败时请稍后重试。</view>
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
      <view class="desc">涉及支付、退款、提现和结算的最终金额、状态与余额，以平台账本和对账结果为准。</view>
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
  '账本详情暂时加载失败，请稍后重试'
]

const businessLabels: Record<string, string> = {
  RECHARGE: '充值入账',
  ORDER_PAYMENT: '订单支付',
  ORDER_PAY: '订单支付',
  ORDER_REFUND: '订单退款',
  WITHDRAW: '提现',
  WITHDRAW_FREEZE: '提现冻结',
  WITHDRAW_PAYOUT: '提现出款',
  WITHDRAW_RELEASE: '提现解冻',
  GIFT: '礼物分账'
}

const statusLabels: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  PENDING: '处理中'
}

const ledgerNoPattern = /^[A-Za-z0-9_-]{6,80}$/

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

const rows = computed(function () {
  return [
    { label: '业务类型', value: detail.bizType || '--' },
    { label: '业务单号', value: detail.bizNo || '--' },
    { label: '资金方向', value: detail.direction || '--' },
    { label: '变动前余额', value: detail.balanceBefore ? `¥${detail.balanceBefore}` : '--' },
    { label: '变动后余额', value: detail.balanceAfter ? `¥${detail.balanceAfter}` : '--' },
    { label: '发生时间', value: detail.createdAt || '--' },
    { label: '备注', value: detail.remark || '--' }
  ]
})

function readLedgerNo(): string {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  return current?.options?.ledgerNo || hashParams?.get('ledgerNo') || ''
}

function isValidLedgerNo(value: string): boolean {
  return ledgerNoPattern.test(value)
}

function money(value: WalletLedgerItemResponse['amount']): string {
  return String(value ?? '')
}

function directionLabel(direction: WalletLedgerItemResponse['direction']): string {
  return direction === 'CREDIT' ? '收入' : '支出'
}

function statusLabel(status: WalletLedgerItemResponse['status']): string {
  return statusLabels[status] ?? String(status || '--')
}

function businessLabel(businessType?: string | null): string {
  if (!businessType) return '--'
  return businessLabels[businessType] ?? businessType
}

function formatDateTime(value: string): string {
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

async function loadDetail(): Promise<void> {
  const ledgerNo = readLedgerNo()
  if (!ledgerNo) {
    loadMessage.value = 'ledgerNo 缺失，未查询账本详情'
    return
  }
  if (!isValidLedgerNo(ledgerNo)) {
    loadMessage.value = 'ledgerNo 无效，未查询账本详情'
    return
  }
  loading.value = true
  loadMessage.value = ''
  try {
    const response = await getWalletLedgerDetail(ledgerNo)
    Object.assign(detail, mapLedgerDetail(response))
  } catch (error) {
    console.warn('wallet ledger detail unavailable', { ledgerNo, error })
    loadMessage.value = '账本详情暂时加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>
<style scoped>
.ledger-page {
  min-height: 100vh;
  padding-top: 18rpx;
  padding-bottom: 44rpx;
  background:
    radial-gradient(circle at 12% 0%, rgba(255, 202, 150, .26), transparent 28%),
    radial-gradient(circle at 88% 16%, rgba(255, 226, 214, .42), transparent 24%),
    linear-gradient(180deg, #fff8f0 0%, #fffdfa 55%, #fff5ee 100%);
}

.hero,
.amount-card,
.info-card,
.safe-card,
.status-card {
  padding: 24rpx;
  border-color: rgba(255, 217, 189, .78);
  box-shadow: 0 15rpx 30rpx rgba(132, 70, 36, .08);
}

.amount-card,
.info-card,
.safe-card,
.status-card {
  margin-top: 16rpx;
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 244, 234, .96));
}

.amount-card,
.info-card,
.safe-card,
.status-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(255, 248, 242, .97));
}

.kicker {
  color: #df6735;
  font-size: 22rpx;
  font-weight: 950;
  letter-spacing: .18rpx;
}

.hero-icon {
  width: 82rpx;
  height: 82rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 38rpx;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .17);
  flex: 0 0 auto;
}

.amount-card {
  text-align: center;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 243, 231, .98));
}

.amount {
  color: #dc2626;
  font-size: 50rpx;
  font-weight: 950;
  letter-spacing: .3rpx;
}

.amount.income {
  color: #16a34a;
}

.status {
  display: inline-flex;
  margin-top: 10rpx;
  padding: 8rpx 18rpx;
  border: 1rpx solid rgba(239, 111, 63, .18);
  border-radius: 999rpx;
  background: #fff3e7;
  color: #df6735;
  font-size: 22rpx;
  font-weight: 900;
}

.row {
  min-height: 70rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 22rpx;
  border-bottom: 1rpx solid rgba(255, 217, 189, .52);
  color: #7b5542;
  font-size: 23rpx;
  font-weight: 760;
}

.row:last-child {
  border-bottom: 0;
}

.row text:first-child {
  flex: 0 0 auto;
  color: #8f6b57;
  font-weight: 850;
}

.row text:last-child {
  max-width: 430rpx;
  color: #342116;
  text-align: right;
  word-break: break-all;
  font-weight: 930;
}

.section-title {
  color: #342116;
  font-size: 29rpx;
  font-weight: 950;
  letter-spacing: .16rpx;
}

.desc,
.page-desc {
  margin-top: 8rpx;
  color: #8f6b57;
  font-size: 23rpx;
  line-height: 1.5;
  font-weight: 650;
}

.status-card {
  color: #8f6b57;
  font-size: 24rpx;
  line-height: 1.5;
  font-weight: 800;
  text-align: center;
}

.status-card.danger {
  color: #be123c;
  background: #fff7f7;
  border-color: #fecaca;
}
</style>
