<template>
  <view class="page-shell ledger-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 钱包账本</view>
        <view class="page-title">全部流水</view>
        <view class="page-desc">收入、支出、冻结、退款和礼物分账统一记录，流水数据以服务端账本为准。</view>
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
const filters = [{ label: '全部', value: 'ALL' }, { label: '收入', value: 'CREDIT' }, { label: '支出', value: 'DEBIT' }, { label: '冻结/提现', value: 'FREEZE' }]
const active = ref('ALL')
const ledgerList = ref<WalletLedgerItemResponse[]>([])
const loading = ref(false)
const loadMessage = ref('')
const filtered = computed(() => {
  if (active.value === 'ALL') return ledgerList.value
  if (active.value === 'FREEZE') return ledgerList.value.filter((item) => ['FROZEN', 'WITHDRAWABLE'].includes(item.balanceType) || String(item.businessType || '').startsWith('WITHDRAW'))
  return ledgerList.value.filter((item) => item.direction === active.value)
})
async function loadLedger() {
  loading.value = true
  loadMessage.value = ''
  try {
    ledgerList.value = await getWalletLedger()
  } catch {
    ledgerList.value = []
    loadMessage.value = '流水加载失败，未展示本地账本样例'
  } finally {
    loading.value = false
  }
}
function businessLabel(businessType?: string | null) { return businessType ? ({ RECHARGE: '充值入账', ORDER_PAYMENT: '订单支付', ORDER_PAY: '订单支付', ORDER_REFUND: '订单退款', WITHDRAW: '提现', WITHDRAW_FREEZE: '提现冻结', WITHDRAW_PAYOUT: '提现出款', WITHDRAW_RELEASE: '提现解冻', GIFT: '礼物分账' } as Record<string, string>)[businessType] ?? businessType : '钱包流水' }
function formatDateTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '--' }
function openDetail(item: WalletLedgerItemResponse) { uni.navigateTo({ url: `/pages/wallet/ledger/detail/index?ledgerNo=${encodeURIComponent(item.ledgerNo)}` }) }
onMounted(() => { void loadLedger() })
</script>
<style scoped>
.ledger-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.ledger-card,.status-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.filter-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.filter-chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.filter-chip.active{background:#3a2a1f;color:#fff}.status-card{color:#9b7560;font-size:24rpx;text-align:center}.status-card.danger{color:#be123c;background:#fff7f7;border-color:#fecaca}.ledger-card{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.title{color:#3a2a1f;font-size:26rpx;font-weight:950}.meta{margin-top:8rpx;color:#9b7560;font-size:21rpx}.right{font-size:30rpx;font-weight:950}.right.CREDIT{color:#16a34a}.right.DEBIT{color:#ff3f8d}
</style>
