<template>
  <view class="page-shell order-page">
    <view class="page-title">订单列表</view>
    <view class="page-desc">买到和卖出的宝贝都在这里，重点跟踪付款、发货、收货和售后。</view>

    <view class="role-switch ds-card">
      <view v-for="item in roles" :key="item.value" class="role-item tapable" :class="{ active: role === item.value }" @click="switchRole(item.value)">
        {{ item.label }}
      </view>
    </view>

    <view class="status-scroll">
      <view v-for="item in statusTabs" :key="item.value" class="status-chip tapable" :class="{ active: status === item.value }" @click="switchStatus(item.value)">
        {{ item.label }}<text v-if="countByStatus(item.value)"> · {{ countByStatus(item.value) }}</text>
      </view>
    </view>

    <view v-if="loading" class="empty-card ds-card">
      <view class="empty-icon">⌛</view>
      <view class="empty-title">正在读取订单</view>
      <view class="empty-desc">平台正在同步买卖订单和售后状态。</view>
    </view>

    <view v-else-if="errorText" class="empty-card ds-card danger">
      <view class="empty-icon">!</view>
      <view class="empty-title">订单读取失败</view>
      <view class="empty-desc">{{ errorText }}</view>
    </view>

    <view v-else-if="filteredOrders.length" class="order-list">
      <view v-for="item in filteredOrders" :key="item.orderNo" class="order-card ds-card">
        <view class="order-head">
          <view>
            <view class="order-no">{{ item.orderNo }}</view>
            <view class="order-time">{{ item.createdAt }}</view>
          </view>
          <view class="status-badge" :class="displayStatus(item)">{{ statusLabel(displayStatus(item)) }}</view>
        </view>

        <view class="goods-row tapable" @click="openOrder(item.orderNo)">
          <view class="goods-cover">{{ coverIcon(item.productTitle) }}</view>
          <view class="goods-main">
            <view class="goods-title">{{ item.productTitle }}</view>
            <view class="goods-desc">{{ item.role === 'buyer' ? '卖家' : '买家' }}：{{ item.counterpartyName }} · {{ item.tradeRuleSnapshot }}</view>
            <view class="goods-price">¥{{ item.amount }}</view>
          </view>
        </view>

        <view class="flow-row">
          <view v-for="step in flowSteps" :key="step.value" class="flow-step" :class="{ done: stepIndex(step.value) <= stepIndex(displayStatus(item)) }">
            <view class="dot"></view>
            <view>{{ step.label }}</view>
          </view>
        </view>

        <view class="actions">
          <button v-for="action in actionsFor(item)" :key="action" class="action-btn" :class="{ primary: action === '去付款' || action === '确认收货' }" @click="handleAction(item, action)">{{ action }}</button>
        </view>
      </view>
    </view>

    <view v-else class="empty-card ds-card">
      <view class="empty-icon">🧾</view>
      <view class="empty-title">暂无相关订单</view>
      <view class="empty-desc">换个状态看看，或者先去宝贝页挑一件喜欢的。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { confirmReceipt, listOrders, type OrderListItemResponse, type OrderListStatus, type OrderRole } from '../../../api/modules/order'
import { resolveOrderContactTarget, type OrderContactAction } from '../../../api/modules/order-contact'

type StatusTab = 'ALL' | OrderListStatus
const roles: Array<{ label: string; value: OrderRole }> = [
  { label: '我买到的', value: 'buyer' },
  { label: '我卖出的', value: 'seller' }
]
const statusTabs: Array<{ label: string; value: StatusTab }> = [
  { label: '全部', value: 'ALL' },
  { label: '待付款', value: 'PENDING_PAY' },
  { label: '待发货', value: 'PAID' },
  { label: '待收货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '售后', value: 'REFUNDING' }
]
const flowSteps: Array<{ label: string; value: OrderListStatus }> = [
  { label: '付款', value: 'PAID' },
  { label: '发货', value: 'SHIPPED' },
  { label: '收货', value: 'COMPLETED' }
]
const orders = ref<OrderListItemResponse[]>([])
const role = ref<OrderRole>('buyer')
const status = ref<StatusTab>('ALL')
const loading = ref(false)
const confirmingOrderNo = ref('')
const errorText = ref('')
const filteredOrders = computed(() => orders.value.filter((item) => status.value === 'ALL' || displayStatus(item) === status.value))
function isValidBackendOrderNo(value: string) {
  return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}
function countByStatus(value: StatusTab) {
  return orders.value.filter((item) => value === 'ALL' || displayStatus(item) === value).length
}
function displayStatus(item: OrderListItemResponse): OrderListStatus {
  return item.afterSalesNo ? 'REFUNDING' : item.status
}
function statusLabel(value: OrderListStatus) {
  const labels: Record<OrderListStatus, string> = { PENDING_PAY: '待付款', PAID: '待发货', SHIPPED: '待收货', COMPLETED: '已完成', REFUNDING: '售后中' }
  return labels[value]
}
function stepIndex(value: OrderListStatus) {
  const map: Record<OrderListStatus, number> = { PENDING_PAY: 0, PAID: 1, SHIPPED: 2, COMPLETED: 3, REFUNDING: 1 }
  return map[value]
}
function actionsFor(item: OrderListItemResponse) {
  const current = displayStatus(item)
  if (current === 'PENDING_PAY') return ['去付款', '取消订单']
  if (current === 'PAID' && item.role === 'seller') return ['去发货', '联系买家']
  if (current === 'PAID') return ['提醒发货', '联系卖家', '申请售后']
  if (current === 'SHIPPED') return ['确认收货', '查看物流', '申请售后']
  if (current === 'COMPLETED') return ['评价', '申请售后']
  return ['查看售后', '联系客服']
}
async function loadOrders() {
  loading.value = true; errorText.value = ''
  try { orders.value = await listOrders(role.value, 'ALL') }
  catch (error) { orders.value = []; errorText.value = error instanceof Error ? error.message : '订单列表读取失败' }
  finally { loading.value = false }
}
function switchRole(value: OrderRole) { role.value = value; status.value = 'ALL'; void loadOrders() }
function switchStatus(value: StatusTab) { status.value = value }
function handleAction(item: OrderListItemResponse, action: string) {
  if (!isValidBackendOrderNo(item.orderNo)) {
    uni.showToast({ title: '订单编号无效，已阻止敏感订单操作', icon: 'none' })
    return
  }
  if (action === '去付款') uni.navigateTo({ url: `/pages/payment/checkout/index?orderNo=${encodeURIComponent(item.orderNo)}&amount=${item.amount}&productId=${item.productId}` })
  else if (action === '去发货') uni.navigateTo({ url: `/pages/order/ship/index?orderNo=${encodeURIComponent(item.orderNo)}` })
  else if (action === '查看物流') uni.navigateTo({ url: `/pages/order/logistics/index?orderNo=${encodeURIComponent(item.orderNo)}` })
  else if (action === '申请售后') uni.navigateTo({ url: `/pages/after-sales/apply/index?orderNo=${encodeURIComponent(item.orderNo)}&amount=${item.amount}` })
  else if (action === '确认收货') void confirmFromList(item)
  else if (action === '查看售后') {
    if (!item.afterSalesNo) return uni.showToast({ title: '暂无售后单号', icon: 'none' })
    uni.navigateTo({ url: `/pages/after-sales/detail/index?afterSalesNo=${encodeURIComponent(item.afterSalesNo)}&orderNo=${encodeURIComponent(item.orderNo)}` })
  }
  else if (action === '评价') uni.navigateTo({ url: `/pages/review/submit/index?orderNo=${encodeURIComponent(item.orderNo)}` })
  else if (action === '联系客服' || action.includes('联系')) openOrderContact(item, action as OrderContactAction)
  else showUnavailableAction(action)
}
function showUnavailableAction(action: string) {
  uni.showToast({ title: `${action}暂不可用，请稍后重试`, icon: 'none' })
}
function openOrderContact(item: OrderListItemResponse, action: OrderContactAction) {
  const target = resolveOrderContactTarget(item, action)
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${target.receiverId}` })
}
async function confirmFromList(item: OrderListItemResponse) {
  if (confirmingOrderNo.value) return
  uni.showModal({
    title: '确认收货',
    content: '确认收到宝贝且无争议后，结算与售后状态以平台订单、支付和物流记录为准。',
    success: async (res) => {
      if (!res.confirm) return
      confirmingOrderNo.value = item.orderNo
      try {
        await confirmReceipt(item.orderNo)
        uni.showToast({ title: '已确认收货', icon: 'success' })
        await loadOrders()
      } catch (error) {
        uni.showModal({ title: '确认失败', content: error instanceof Error ? error.message : '确认收货失败，请稍后重试', showCancel: false })
      } finally { confirmingOrderNo.value = '' }
    }
  })
}
function coverIcon(title: string) { if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🎀'; if (title.includes('包')) return '👜'; if (title.includes('衣') || title.includes('裙')) return '👗'; return '🛍️' }
function openOrder(orderNo: string) { uni.navigateTo({ url: `/pages/order/detail/index?orderNo=${encodeURIComponent(orderNo)}` }) }
onMounted(() => { void loadOrders() })
</script>

<style scoped>
.order-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.role-switch { margin-top:14rpx; padding:6rpx; display:grid; grid-template-columns:repeat(2, 1fr); gap:8rpx; border-color:#ffd9bd; }
.role-item { min-height:52rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; color:#9b7560; font-size:21rpx; font-weight:950; }
.role-item.active { color:#fff; background:#ff7a45; box-shadow:0 8rpx 18rpx rgba(255,122,69,.18); }
.status-scroll { margin-top:12rpx; display:flex; gap:8rpx; overflow-x:auto; padding-bottom:4rpx; }
.status-chip { flex:none; padding:10rpx 16rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:22rpx; font-weight:900; }
.status-chip.active { background:#3a2a1f; color:#fff; border-color:#3a2a1f; }
.order-list { margin-top:12rpx; display:flex; flex-direction:column; gap:8rpx; }
.order-card { padding:16rpx; border-color:#ffd9bd; }
.order-head { display:flex; align-items:flex-start; justify-content:space-between; gap:10rpx; }
.order-no { color:#3a2a1f; font-size:22rpx; font-weight:950; }
.order-time { margin-top:4rpx; color:#b9856a; font-size:21rpx; }
.status-badge { padding:7rpx 12rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:21rpx; font-weight:950; }
.status-badge.PAID,.status-badge.SHIPPED { background:#fff8e8; color:#b45309; }
.status-badge.COMPLETED { background:#f0fdf4; color:#15803d; }
.status-badge.REFUNDING { background:#fff1f2; color:#be123c; }
.goods-row { margin-top:12rpx; display:flex; gap:10rpx; }
.goods-cover { width:104rpx; height:104rpx; border-radius:20rpx; background:linear-gradient(135deg,#fff3e7,#ffe5ef); display:flex; align-items:center; justify-content:center; font-size:38rpx; }
.goods-main { flex:1; min-width:0; }
.goods-title { color:#3a2a1f; font-size:24rpx; font-weight:950; }
.goods-desc { margin-top:5rpx; color:#9b7560; font-size:18rpx; line-height:1.32; }
.goods-price { margin-top:6rpx; color:#ff3f8d; font-size:26rpx; font-weight:950; }
.flow-row { margin-top:12rpx; padding:10rpx; border-radius:18rpx; background:#fffaf6; display:grid; grid-template-columns:repeat(3, 1fr); gap:8rpx; }
.flow-step { display:flex; flex-direction:column; align-items:center; gap:4rpx; color:#c49aac; font-size:18rpx; font-weight:850; }
.dot { width:14rpx; height:14rpx; border-radius:50%; background:#ffd9bd; }
.flow-step.done { color:#ff7a45; }
.flow-step.done .dot { background:#ff7a45; }
.actions { margin-top:12rpx; display:flex; justify-content:flex-end; flex-wrap:wrap; gap:8rpx; }
.action-btn { margin:0; padding:0 16rpx; min-width:112rpx; height:48rpx; line-height:48rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#7b5542; font-size:22rpx; font-weight:900; }
.action-btn.primary { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.empty-card { margin-top:14rpx; padding:28rpx 20rpx; text-align:center; border-color:#ffd9bd; }
.empty-card.danger{border-color:#fecaca;background:#fff7f7}.empty-icon { font-size:44rpx; }.empty-title { margin-top:12rpx; color:#3a2a1f; font-size:26rpx; font-weight:950; }.empty-desc { margin-top:8rpx; color:#9b7560; font-size:21rpx; }
</style>
