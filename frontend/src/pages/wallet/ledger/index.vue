<template>
  <view class="page-shell ledger-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 钱包账本</view>
        <view class="page-title">全部流水</view>
        <view class="page-desc">收入、支出、冻结、退款和礼物分账统一记录，流水数据以平台账本为准。</view>
      </view>
      <view class="hero-icon">📒</view>
    </view>
    <view class="filter-row"><view v-for="item in filters" :key="item.value" class="filter-chip tapable" :class="{ active: active === item.value }" @click="active = item.value">{{ item.label }}</view></view>
    <view v-if="loading" class="status-card ds-card">流水加载中...</view>
    <view v-else-if="loadMessage" class="status-card ds-card danger">{{ loadMessage }}</view>
    <view v-else-if="filtered.length === 0" class="status-card ds-card">暂无钱包流水</view>
    <view v-else class="ledger-list">
      <view v-for="item in filtered" :key="item.ledgerNo" class="ledger-card ds-card tapable" @click="openDetail(item)">
        <view class="left"><view class="title">{{ businessLabel(item.businessType) }}</view><view class="meta">{{ item.ledgerNo }} · {{ formatDateTime(item.createdAt) }}</view></view>
        <view class="right" :class="item.direction">{{ item.direction === 'CREDIT' ? '+' : '-' }}{{ item.amount }}</view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getWalletLedger, type WalletLedgerItemResponse } from '../../../api/modules/wallet'

const filters = [
  { label: '全部', value: 'ALL' },
  { label: '收入', value: 'CREDIT' },
  { label: '支出', value: 'DEBIT' },
  { label: '冻结/提现', value: 'FREEZE' },
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
  GIFT: '礼物分账',
}

const freezeBalanceTypes = ['FROZEN', 'WITHDRAWABLE']
const ledgerNoPattern = /^[A-Za-z0-9_-]{6,80}$/
const active = ref('ALL')
const ledgerList = ref<WalletLedgerItemResponse[]>([])
const loading = ref(false)
const loadMessage = ref('')
const filtered = computed(function () {
  if (active.value === 'ALL') return ledgerList.value
  if (active.value === 'FREEZE') return ledgerList.value.filter(isFreezeLedger)
  return ledgerList.value.filter(function (item) { return item.direction === active.value })
})

async function loadLedger(): Promise<void> {
  loading.value = true
  loadMessage.value = ''
  try {
    ledgerList.value = await getWalletLedger()
  } catch {
    ledgerList.value = []
    loadMessage.value = '流水加载失败，未展示默认账本'
  } finally {
    loading.value = false
  }
}

function isFreezeLedger(item: WalletLedgerItemResponse): boolean {
  return freezeBalanceTypes.includes(item.balanceType) || String(item.businessType || '').startsWith('WITHDRAW')
}

function businessLabel(businessType?: string | null): string {
  if (!businessType) return '钱包流水'
  return businessLabels[businessType] ?? businessType
}

function formatDateTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '--'
}

function isValidLedgerNo(value?: string | null): boolean {
  return ledgerNoPattern.test(String(value || ''))
}

function openDetail(item: WalletLedgerItemResponse): void {
  if (!isValidLedgerNo(item.ledgerNo)) {
    uni.showToast({ title: '流水编号无效，未打开账本详情', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/wallet/ledger/detail/index?ledgerNo=${encodeURIComponent(item.ledgerNo)}` })
}

onMounted(function () { void loadLedger() })
</script>
<style scoped>
.ledger-page {
  min-height: 100vh;
  padding-top: 18rpx;
  padding-bottom: 44rpx;
  background:
    radial-gradient(circle at 12% 0%, rgba(255, 202, 150, .28), transparent 28%),
    radial-gradient(circle at 88% 16%, rgba(255, 226, 214, .44), transparent 24%),
    linear-gradient(180deg, #fff8f0 0%, #fffdfa 55%, #fff5ee 100%);
}

.hero,
.ledger-card,
.status-card {
  padding: 22rpx;
  border-color: rgba(255, 217, 189, .78);
  box-shadow: 0 16rpx 32rpx rgba(132, 70, 36, .085);
}

.ledger-card,
.status-card {
  margin-top: 16rpx;
}

.hero,
.hero-icon,
.filter-row,
.ledger-list,
.ledger-card {
  display: flex;
}

.hero {
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 244, 234, .96));
}

.ledger-card,
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
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  font-size: 38rpx;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .17);
}

.filter-row {
  margin-top: 18rpx;
  gap: 12rpx;
  overflow-x: auto;
  padding-bottom: 4rpx;
}

.filter-chip {
  flex: none;
  padding: 13rpx 20rpx;
  border: 1rpx solid rgba(255, 217, 189, .78);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .92);
  color: #8f6b57;
  font-size: 22rpx;
  font-weight: 900;
  box-shadow: 0 8rpx 16rpx rgba(132, 70, 36, .05);
}

.filter-chip.active {
  border-color: rgba(58, 38, 26, .82);
  background: linear-gradient(135deg, #3a261a, #6f432b);
  color: #fffaf4;
  box-shadow: 0 10rpx 20rpx rgba(58, 38, 26, .13);
}

.status-card {
  color: #8f6b57;
  font-size: 24rpx;
  font-weight: 760;
  line-height: 1.5;
  text-align: center;
}

.status-card.danger {
  border-color: #fecaca;
  background: #fff7f7;
  color: #be123c;
}

.ledger-list {
  margin-top: 12rpx;
  flex-direction: column;
  gap: 10rpx;
}

.ledger-card {
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.left {
  flex: 1;
  min-width: 0;
}

.title {
  color: #342116;
  font-size: 26rpx;
  font-weight: 950;
  letter-spacing: .12rpx;
}

.meta {
  margin-top: 8rpx;
  color: #8f6b57;
  font-size: 21rpx;
  font-weight: 650;
  line-height: 1.35;
  word-break: break-all;
}

.right {
  flex: 0 0 auto;
  font-size: 30rpx;
  font-weight: 950;
}

.right.CREDIT {
  color: #16a34a;
}

.right.DEBIT {
  color: #dc2626;
}
</style>
