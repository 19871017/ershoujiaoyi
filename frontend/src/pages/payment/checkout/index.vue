<template>
  <view class="page-shell checkout-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 安全收银台</view>
        <view class="page-title">确认支付</view>
        <view class="page-desc">支付前再次读取真实订单状态和付款状态。</view>
      </view>
      <view class="hero-icon">💳</view>
    </view>

    <view v-if="loading" class="status-card ds-card">
      <view class="status-icon">⌛</view>
      <view>
        <view class="status-title">正在读取订单</view>
        <view class="status-desc">平台正在同步订单金额和付款状态。</view>
      </view>
    </view>
    <view v-else-if="errorText" class="status-card ds-card danger">
      <view class="status-icon">!</view>
      <view>
        <view class="status-title">无法发起支付</view>
        <view class="status-desc">{{ errorText }}</view>
      </view>
    </view>

    <template v-else-if="order">
      <view class="order-card ds-card">
        <view class="section-title">订单信息</view>
        <view class="info-row"><text>订单号</text><text>{{ order.orderNo }}</text></view>
        <view class="info-row"><text>商品</text><text>{{ order.productTitle }}</text></view>
        <view class="info-row"><text>订单状态</text><text>{{ statusText }}</text></view>
        <view class="info-row"><text>应付金额</text><text>¥{{ order.amount }}</text></view>
      </view>

      <view class="pay-card ds-card">
        <view class="section-title">支付方式</view>
        <view v-for="item in payMethods" :key="item.value" class="pay-method tapable" :class="{ active: payMethod === item.value }" @click="payMethod = item.value">
          <view class="method-icon">{{ item.icon }}</view>
          <view class="method-main">
            <view class="method-title">{{ item.label }}</view>
            <view class="method-desc">{{ item.desc }}</view>
          </view>
          <view class="radio" :class="{ active: payMethod === item.value }">✓</view>
        </view>
      </view>

      <view class="safe-card ds-card">
        <view class="section-title">付款安全提示</view>
        <view class="safe-line">平台交易不要求私下转账，不要相信脱离平台的付款链接。</view>
        <view class="safe-line">支付结果和后续订单流转以平台订单状态为准。</view>
      </view>

      <view v-if="message" class="message ds-card" :class="{ error: isError }">{{ message }}</view>

      <view class="bottom-bar">
        <view>
          <view class="pay-label">需支付</view>
          <view class="pay-amount">¥{{ order.amount }}</view>
        </view>
        <button class="primary-btn submit" :disabled="paying || !canPay" @click="confirmPay">{{ paying ? '支付中...' : '确认支付' }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getOrderDetail, payOrder, type OrderDetailResponse } from '../../../api/modules/order'

type PayMethod = 'WALLET' | 'WECHAT' | 'ALIPAY'
const orderNo = ref('')
const order = ref<OrderDetailResponse | null>(null)
const payMethod = ref<PayMethod>('WALLET')
const loading = ref(false)
const paying = ref(false)
const message = ref('')
const errorText = ref('')
const isError = ref(false)
const payMethods = [
  { value: 'WALLET' as const, icon: '👛', label: '钱包余额', desc: '优先使用可用余额，支付结果以平台订单状态为准' },
  { value: 'WECHAT' as const, icon: '💚', label: '微信支付', desc: '商户支付通道未开通时不会伪造成功' },
  { value: 'ALIPAY' as const, icon: '💙', label: '支付宝', desc: '商户支付通道未开通时不会伪造成功' }
]
const canPay = computed(() => order.value?.status === 'PENDING_PAY')
const statusText = computed(() => {
  const status = order.value?.status
  if (status === 'PENDING_PAY') return '待付款'
  if (status === 'PAID') return '已付款'
  if (status === 'SHIPPED') return '已发货'
  if (status === 'COMPLETED') return '已完成'
  return status || '--'
})
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  orderNo.value = current?.options?.orderNo || hashParams?.get('orderNo') || ''
}
function isValidBackendOrderNo(value: string) {
  return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}
async function loadOrder() {
  if (!isValidBackendOrderNo(orderNo.value)) { errorText.value = '缺少有效订单号，不能进入收银台'; order.value = null; return }
  loading.value = true; errorText.value = ''; message.value = ''; isError.value = false
  try {
    const detail = await getOrderDetail(orderNo.value)
    order.value = detail
    if (detail.status !== 'PENDING_PAY') errorText.value = `当前订单状态为${statusText.value}，不能重复发起支付`
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '订单读取失败，不能发起支付'
    order.value = null
  } finally { loading.value = false }
}
async function confirmPay() {
  if (!order.value || !canPay.value) { isError.value = true; message.value = '订单状态不可支付，请返回订单详情刷新。'; return }
  if (payMethod.value !== 'WALLET') {
    uni.navigateTo({ url: `/pages/payment/method/index?method=${payMethod.value}&orderNo=${encodeURIComponent(order.value.orderNo)}` })
    return
  }
  paying.value = true
  message.value = ''
  isError.value = false
  try {
    const paid = await payOrder(order.value.orderNo)
    order.value = await getOrderDetail(paid.orderNo || order.value.orderNo)
    uni.showModal({
      title: '支付成功',
      content: `订单 ${order.value.orderNo} 已完成支付请求，正在返回订单详情。`,
      showCancel: false,
      success: () => uni.redirectTo({ url: `/pages/order/detail/index?orderNo=${encodeURIComponent(order.value?.orderNo || '')}` })
    })
  } catch (error) {
    isError.value = true
    message.value = error instanceof Error ? error.message : '钱包支付请求未完成，订单仍保持待付款状态，请确认余额或稍后重试。'
  } finally { paying.value = false }
}
onMounted(() => { readQuery(); void loadOrder() })
</script>

<style scoped>
.checkout-page { padding-bottom:124rpx; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.order-card,.pay-card,.safe-card,.message,.status-card { margin-top:12rpx; padding:16rpx; border-color:#ffd9bd; }
.hero,.status-card { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); gap:10rpx; }
.status-card { justify-content:flex-start; }
.status-card.danger { background:#fff7f7; border-color:#fecaca; }
.kicker { color:#ff7a45; font-size:20rpx; font-weight:950; }
.hero-icon,.status-icon { width:62rpx; height:62rpx; border-radius:22rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:30rpx; flex-shrink:0; }
.danger .status-icon { background:#ef4444; }
.status-title { color:#3a2a1f; font-size:26rpx; font-weight:950; }
.status-desc { margin-top:5rpx; color:#9b7560; font-size:21rpx; line-height:1.38; }
.section-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.info-row { min-height:48rpx; display:flex; justify-content:space-between; align-items:center; border-bottom:1rpx solid #ffe5ef; color:#7b5542; font-size:21rpx; gap:18rpx; }
.info-row:last-child { border-bottom:0; }
.info-row text:last-child { color:#3a2a1f; font-weight:900; text-align:right; }
.pay-method { margin-top:10rpx; padding:12rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; display:flex; align-items:center; gap:10rpx; }
.pay-method.active { border-color:#ff7a45; background:#fff3e7; }
.method-icon { width:46rpx; height:46rpx; border-radius:18rpx; background:#fff; display:flex; align-items:center; justify-content:center; font-size:24rpx; }
.method-main { flex:1; min-width:0; }
.method-title { color:#3a2a1f; font-size:23rpx; font-weight:950; }
.method-desc,.safe-line { margin-top:6rpx; color:#9b7560; font-size:19rpx; line-height:1.35; }
.radio { width:30rpx; height:30rpx; border-radius:50%; border:1rpx solid #ffd9bd; color:transparent; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.radio.active { background:#ff7a45; border-color:#ff7a45; color:#fff; }
.message { color:#15803d; font-size:21rpx; }
.message.error { color:#be123c; background:#fff1f2; }
.bottom-bar { position:fixed; left:0; right:0; bottom:0; padding:12rpx 18rpx calc(12rpx + env(safe-area-inset-bottom)); background:rgba(255,247,237,.96); border-top:1rpx solid #ffd9bd; display:flex; justify-content:space-between; align-items:center; gap:14rpx; z-index:20; }
.pay-label { color:#9b7560; font-size:20rpx; }
.pay-amount { color:#ff3f8d; font-size:34rpx; font-weight:950; }
.submit { width:260rpx; height:54rpx; line-height:54rpx; }
</style>
