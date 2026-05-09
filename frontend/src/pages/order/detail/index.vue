<template>
  <view class="page-shell order-detail-page">
    <view v-if="loading" class="status-card ds-card"><view class="status-icon">⌛</view><view class="status-main"><view class="status-title">正在读取订单</view><view class="status-desc">平台正在同步订单和售后状态。</view></view></view>
    <view v-else-if="errorText" class="status-card ds-card danger"><view class="status-icon">!</view><view class="status-main"><view class="status-title">订单读取失败</view><view class="status-desc">{{ errorText }}</view></view></view>

    <template v-else-if="order">
      <view class="status-card ds-card">
        <view class="status-icon">{{ current.icon }}</view>
        <view class="status-main">
          <view class="status-title">{{ current.label }}</view>
          <view class="status-desc">{{ current.desc }}</view>
        </view>
      </view>

      <view class="goods-card ds-card tapable" @click="openProduct">
        <view class="goods-cover">{{ coverIcon(order.productTitle) }}</view>
        <view class="goods-main">
          <view class="goods-title">{{ order.productTitle }}</view>
          <view class="goods-desc">{{ order.tradeRuleSnapshot }}</view>
          <view class="goods-price">¥{{ order.amount }}</view>
        </view>
        <view class="arrow">›</view>
      </view>

      <view class="flow-card ds-card">
        <view class="section-title">订单进度</view>
        <view class="flow-row">
          <view v-for="step in flow" :key="step.value" class="flow-step" :class="{ done: step.index <= current.index }">
            <view class="dot"></view><view class="flow-label">{{ step.label }}</view><view class="flow-time">{{ step.time || '待完成' }}</view>
          </view>
        </view>
      </view>

      <view class="info-card ds-card">
        <view class="section-title">交易信息</view>
        <view v-for="item in infoRows" :key="item.label" class="info-row"><text>{{ item.label }}</text><text>{{ item.value }}</text></view>
      </view>

      <view class="safe-card ds-card">
        <view class="section-title">订单安全提示</view>
        <view class="safe-line">订单、支付、售后和聊天记录以服务端状态为准。</view>
        <view class="safe-line">如遇私下转账、绕平台交易、诱导外部联系，请立即举报。</view>
      </view>

      <view class="bottom-actions">
        <button v-for="action in actions" :key="action" class="action-btn" :class="{ primary: action === '去付款' || action === '确认收货' }" :disabled="action === '确认收货' && confirming" @click="handleAction(action)">{{ action === '确认收货' && confirming ? '确认中...' : action }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { confirmReceipt, getOrderDetail, type OrderDetailResponse, type OrderListStatus } from '../../../api/modules/order'
import { resolveOrderContactTarget, type OrderContactAction } from '../../../api/modules/order-contact'

const orderNo = ref('')
const order = ref<OrderDetailResponse | null>(null)
const loading = ref(false)
const confirming = ref(false)
const errorText = ref('')
const states: Record<OrderListStatus, { icon: string; label: string; desc: string; index: number }> = {
  PENDING_PAY: { icon: '💳', label: '等待付款', desc: '请确认宝贝信息后完成支付。', index: 0 },
  PAID: { icon: '📦', label: '等待卖家发货', desc: '订单已付款，卖家需要尽快发货；支付状态以服务端记录为准。', index: 1 },
  SHIPPED: { icon: '🚚', label: '宝贝运输中', desc: '收到宝贝并确认无误后再确认收货。', index: 2 },
  COMPLETED: { icon: '🌸', label: '交易完成', desc: '订单完成状态以服务端订单、支付和售后记录为准，可以评价这次交易。', index: 3 },
  REFUNDING: { icon: '🛟', label: '售后处理中', desc: '售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准。', index: 1 }
}
const displayStatus = computed<OrderListStatus>(() => order.value?.afterSalesNo ? 'REFUNDING' : (order.value?.status || 'PENDING_PAY'))
const current = computed(() => states[displayStatus.value])
const flow = computed(() => {
  const item = order.value
  return [
    { label: '创建订单', value: 'created', index: 0, time: item?.createdAt || '' },
    { label: '付款', value: 'paid', index: 1, time: item?.paidAt || '' },
    { label: '发货', value: 'shipped', index: 2, time: item?.shippedAt || '' },
    { label: '完成', value: 'completed', index: 3, time: item?.completedAt || '' }
  ]
})
const infoRows = computed(() => order.value ? [
  { label: '订单号', value: order.value.orderNo },
  { label: '对方', value: order.value.counterpartyName },
  { label: '商品金额', value: `¥${order.value.amount}` },
  { label: '交易方式', value: order.value.tradeRuleSnapshot },
  { label: '售后单', value: order.value.afterSalesNo || '暂无' }
] : [])
const actions = computed(() => {
  if (!order.value) return []
  if (displayStatus.value === 'PENDING_PAY') return ['去付款', '取消订单', '联系卖家']
  if (displayStatus.value === 'PAID') return ['提醒发货', '联系卖家', '申请退款']
  if (displayStatus.value === 'SHIPPED') return ['确认收货', '查看物流', '申请售后']
  if (displayStatus.value === 'COMPLETED') return ['评价', '再次购买', '联系卖家']
  return ['查看售后', '联系客服']
})
function readQuery() { const pages = getCurrentPages(); const currentPage = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined; const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined; orderNo.value = currentPage?.options?.orderNo || hashParams?.get('orderNo') || '' }
async function loadDetail() {
  if (!orderNo.value) { errorText.value = '缺少订单号，请从订单列表进入'; return }
  loading.value = true; errorText.value = ''
  try { order.value = await getOrderDetail(orderNo.value) }
  catch (error) { errorText.value = error instanceof Error ? error.message : '订单详情读取失败' }
  finally { loading.value = false }
}
function handleAction(action: string) {
  if (!order.value) return
  if (action === '去付款') uni.navigateTo({ url: `/pages/payment/checkout/index?orderNo=${encodeURIComponent(order.value.orderNo)}&amount=${order.value.amount}&productId=${order.value.productId}` })
  else if (action === '查看物流') uni.navigateTo({ url: `/pages/order/logistics/index?orderNo=${encodeURIComponent(order.value.orderNo)}` })
  else if (action === '申请退款' || action === '申请售后') uni.navigateTo({ url: `/pages/after-sales/apply/index?orderNo=${encodeURIComponent(order.value.orderNo)}&amount=${order.value.amount}` })
  else if (action === '查看售后') {
    if (!order.value.afterSalesNo) return uni.showToast({ title: '暂无售后单号', icon: 'none' })
    uni.navigateTo({ url: `/pages/after-sales/detail/index?afterSalesNo=${encodeURIComponent(order.value.afterSalesNo)}&orderNo=${encodeURIComponent(order.value.orderNo)}` })
  }
  else if (action === '确认收货') void confirmOrderReceipt()
  else if (action === '提醒发货') showUnavailableAction(action)
  else if (action === '评价') uni.navigateTo({ url: `/pages/review/submit/index?orderNo=${encodeURIComponent(order.value.orderNo)}` })
  else if (action === '联系卖家' || action === '联系买家' || action === '联系客服') openOrderContact(action)
  else showUnavailableAction(action)
}
function showUnavailableAction(action: string) {
  uni.showToast({ title: `${action}暂未接通后端，未执行任何订单变更`, icon: 'none' })
}
function openOrderContact(action: OrderContactAction) {
  if (!order.value) return
  const target = resolveOrderContactTarget(order.value, action)
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${target.receiverId}` })
}
async function confirmOrderReceipt() {
  if (!order.value || confirming.value) return
  uni.showModal({
    title: '确认收货',
    content: '确认收到宝贝且无争议后，确认收货将调用后端接口完成状态变更。确认后不可直接撤回。',
    success: async (res) => {
      if (!res.confirm || !order.value) return
      confirming.value = true
      try {
        order.value = await confirmReceipt(order.value.orderNo)
        uni.showToast({ title: '已确认收货', icon: 'success' })
      } catch (error) {
        uni.showModal({ title: '确认失败', content: error instanceof Error ? error.message : '确认收货失败，请稍后重试', showCancel: false })
      } finally { confirming.value = false }
    }
  })
}
function coverIcon(title: string) { if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🎀'; if (title.includes('包')) return '👜'; if (title.includes('衣') || title.includes('裙')) return '👗'; return '🛍️' }
function openProduct() { if (order.value) uni.navigateTo({ url: `/pages/product/detail/index?productId=${order.value.productId}` }) }
onMounted(() => { readQuery(); void loadDetail() })
</script>

<style scoped>
.order-detail-page { padding-bottom:132rpx; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.status-card { padding:26rpx; display:flex; gap:18rpx; align-items:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }.status-card.danger{border-color:#fecaca;background:#fff7f7}.status-icon { width:82rpx; height:82rpx; border-radius:30rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:40rpx; }.danger .status-icon{background:#ef4444}.status-main { flex:1; }.status-title { color:#3a2a1f; font-size:34rpx; font-weight:950; }.status-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.5; }.goods-card,.flow-card,.info-card,.safe-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.goods-card { display:flex; gap:16rpx; align-items:center; }.goods-cover { width:132rpx; height:132rpx; border-radius:28rpx; background:linear-gradient(135deg,#fff3e7,#ffe5ef); display:flex; align-items:center; justify-content:center; font-size:50rpx; }.goods-main { flex:1; min-width:0; }.goods-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.goods-desc { margin-top:8rpx; color:#9b7560; font-size:22rpx; }.goods-price { margin-top:10rpx; color:#ff3f8d; font-size:32rpx; font-weight:950; }.arrow { color:#d79262; font-size:38rpx; }.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.flow-row { margin-top:18rpx; display:grid; grid-template-columns:repeat(4, 1fr); gap:8rpx; }.flow-step { text-align:center; color:#c49aac; font-size:19rpx; font-weight:850; }.dot { width:20rpx; height:20rpx; border-radius:50%; background:#ffd9bd; margin:0 auto 8rpx; }.flow-step.done { color:#ff7a45; }.flow-step.done .dot { background:#ff7a45; }.flow-time { margin-top:4rpx; font-size:16rpx; color:#b9856a; }.info-row { min-height:62rpx; display:flex; justify-content:space-between; align-items:center; border-bottom:1rpx solid #ffe5ef; color:#7b5542; font-size:23rpx; }.info-row:last-child { border-bottom:0; }.safe-line { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.5; }.bottom-actions { position:fixed; left:0; right:0; bottom:0; padding:16rpx 22rpx calc(16rpx + env(safe-area-inset-bottom)); background:rgba(255,247,251,.96); border-top:1rpx solid #ffd9bd; display:flex; justify-content:flex-end; gap:12rpx; flex-wrap:wrap; z-index:20; }.action-btn { margin:0; padding:0 22rpx; min-width:132rpx; height:58rpx; line-height:58rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#7b5542; font-size:22rpx; font-weight:900; }.action-btn.primary { background:#ff7a45; border-color:#ff7a45; color:#fff; }
</style>
