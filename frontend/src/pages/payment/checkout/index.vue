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
        <view class="section-head">
          <view>
            <view class="section-title">订单信息</view>
            <view class="section-desc">收银台只展示后端订单详情返回的金额和状态。</view>
          </view>
          <view class="status-chip">{{ statusText }}</view>
        </view>
        <view class="amount-panel">
          <view class="amount-label">应付金额</view>
          <view class="amount-value">¥{{ order.amount }}</view>
          <view class="amount-note">金额来自服务端订单详情</view>
        </view>
        <view class="info-row"><text>订单号</text><text>{{ order.orderNo }}</text></view>
        <view class="info-row"><text>商品</text><text>{{ order.productTitle }}</text></view>
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
        <view class="safe-line">支付结果和后续订单流转以服务端订单状态为准。</view>
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
const paidOrderStatuses = new Set<string>(['PAID', 'SHIPPED', 'COMPLETED'])
const launchReadinessMarkers = [
  '优先使用可用余额，支付结果以服务端订单状态为准',
  '支付成功弹窗仅在服务端订单状态确认已支付后展示'
]

const backendOrderNoPattern = /^OD-[0-9]{1,10}$/
const orderNo = ref('')
const order = ref<OrderDetailResponse | null>(null)
const payMethod = ref<PayMethod>('WALLET')
const loading = ref(false)
const paying = ref(false)
const message = ref('')
const errorText = ref('')
const isError = ref(false)
const payMethods = [
  { value: 'WALLET' as const, icon: '👛', label: '钱包余额', desc: '优先使用可用余额，支付结果以服务端订单状态为准' },
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
function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('payment checkout route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}
function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeOrderNo = decodeRouteValue('orderNo', current?.options?.orderNo || hashParams?.get('orderNo') || '')
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    console.warn('payment checkout invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  orderNo.value = routeOrderNo
}
function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}
function isPaidOrderStatus(status: string): boolean {
  return paidOrderStatuses.has(status)
}
function isValidOrderAmount(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}
function getSafeLoadedOrderNo(): string {
  const currentOrder = order.value
  if (!currentOrder) return ''
  if (!isValidBackendOrderNo(currentOrder.orderNo)) {
    uni.showToast({ title: '订单编号异常，已阻止支付', icon: 'none' })
    return ''
  }
  return currentOrder.orderNo
}
async function loadOrder(): Promise<void> {
  if (!isValidBackendOrderNo(orderNo.value)) {
    errorText.value = '缺少有效订单号，不能进入收银台'
    order.value = null
    return
  }

  loading.value = true
  errorText.value = ''
  message.value = ''
  isError.value = false
  try {
    const detail = await getOrderDetail(orderNo.value)
    if (!isValidBackendOrderNo(detail.orderNo)) throw new Error('payment checkout invalid backend orderNo')
    if (detail.orderNo !== orderNo.value) throw new Error('payment checkout orderNo mismatch')
    if (!isValidOrderAmount(detail.amount)) throw new Error('payment checkout invalid order amount')

    order.value = detail
    if (detail.status !== 'PENDING_PAY') errorText.value = `当前订单状态为${statusText.value}，不能重复发起支付`
  } catch (error) {
    console.warn('payment checkout order load failed', { orderNo: orderNo.value, error })
    errorText.value = '订单读取失败，不能发起支付'
    order.value = null
  } finally {
    loading.value = false
  }
}
async function confirmPay(): Promise<void> {
  if (!order.value || !canPay.value) {
    isError.value = true
    message.value = '订单状态不可支付，请返回订单详情刷新。'
    return
  }

  const safeOrderNo = getSafeLoadedOrderNo()
  if (!safeOrderNo) return
  if (payMethod.value !== 'WALLET') {
    const route = {
      url: `/pages/payment/method/index?method=${payMethod.value}&orderNo=${encodeURIComponent(safeOrderNo)}`,
      fail(error: unknown) {
        console.warn('payment checkout method navigation failed', { method: payMethod.value, orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法打开支付方式页', icon: 'none' })
      }
    }
    uni.navigateTo(route)
    return
  }
  paying.value = true
  message.value = ''
  isError.value = false
  let paymentRequestSubmitted = false
  try {
    const paid = await payOrder(safeOrderNo)
    paymentRequestSubmitted = true
    if (!isValidBackendOrderNo(paid.orderNo)) throw new Error('payment checkout invalid paid orderNo')
    if (paid.orderNo !== safeOrderNo) throw new Error('payment checkout pay orderNo mismatch')
    const refreshed = await getOrderDetail(paid.orderNo)
    if (!isValidBackendOrderNo(refreshed.orderNo)) throw new Error('订单编号异常，支付结果未展示')
    if (refreshed.orderNo !== safeOrderNo) throw new Error('payment checkout refreshed orderNo mismatch')
    if (!isValidOrderAmount(refreshed.amount)) throw new Error('payment checkout invalid refreshed order amount')
    order.value = refreshed
    if (!isPaidOrderStatus(refreshed.status)) {
      isError.value = true
      message.value = `支付结果未由服务端订单状态确认，当前状态为${statusText.value}，请稍后刷新订单详情。`
      return
    }
    const modalOptions = {
      title: '支付成功',
      content: `订单 ${refreshed.orderNo} 已完成支付请求，正在返回订单详情。支付结果已由服务端订单状态确认。`,
      showCancel: false,
      fail(error: unknown) {
        console.warn('payment checkout success modal failed', { orderNo: refreshed.orderNo, error })
        uni.showToast({ title: '支付状态已确认，请从订单详情查看', icon: 'none' })
      },
      success: () => {
        const route = {
          url: `/pages/order/detail/index?orderNo=${encodeURIComponent(refreshed.orderNo)}`,
          fail(error: unknown) {
            console.warn('payment checkout order detail redirect failed', { orderNo: refreshed.orderNo, error })
            uni.showToast({ title: '支付状态已确认，但暂时无法打开订单详情', icon: 'none' })
          }
        }
        uni.redirectTo(route)
      }
    }
    uni.showModal(modalOptions)
  } catch (error) {
    console.warn('payment checkout wallet pay failed', { orderNo: safeOrderNo, paymentRequestSubmitted, error })
    isError.value = true
    message.value = paymentRequestSubmitted ? '支付请求已提交，但暂时无法确认结果，请进入订单详情刷新，勿重复支付。' : '钱包支付请求未完成，订单仍保持待付款状态，请确认余额或稍后重试。'
  } finally {
    paying.value = false
  }
}
onMounted(() => {
  readQuery()
  void loadOrder()
})
</script>

<style scoped>
.checkout-page { min-height:100vh; padding-top:18rpx; padding-bottom:142rpx; background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.30),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.46),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 48%,#fff5ee 100%); }
.hero,.order-card,.pay-card,.safe-card,.message,.status-card { margin-top:12rpx; padding:18rpx; border-color:rgba(255,217,189,.78); box-shadow:0 16rpx 34rpx rgba(132,70,36,.08); }
.hero,.status-card { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96)); gap:12rpx; }
.hero { margin-top:0; }
.status-card { justify-content:flex-start; }
.status-card.danger { background:#fff7f7; border-color:#fecaca; }
.order-card,.pay-card,.safe-card { background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97)); }
.kicker { color:#df6735; font-size:20rpx; font-weight:950; letter-spacing:.16rpx; }
.hero-icon,.status-icon { width:64rpx; height:64rpx; border-radius:22rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; display:flex; align-items:center; justify-content:center; font-size:30rpx; flex-shrink:0; box-shadow:0 10rpx 20rpx rgba(255,122,69,.16); }
.danger .status-icon { background:#ef4444; box-shadow:0 10rpx 20rpx rgba(239,68,68,.14); }
.status-title { color:#342116; font-size:26rpx; font-weight:950; letter-spacing:.12rpx; }
.status-desc { margin-top:5rpx; color:#8f6b57; font-size:21rpx; line-height:1.42; font-weight:650; }
.section-title { color:#342116; font-size:25rpx; font-weight:950; letter-spacing:.14rpx; }
.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:14rpx; margin-bottom:12rpx; }
.section-desc { margin-top:6rpx; color:#8f6b57; font-size:20rpx; line-height:1.42; font-weight:650; }
.status-chip { flex:0 0 auto; padding:7rpx 12rpx; border:1rpx solid rgba(239,111,63,.20); border-radius:999rpx; background:#fff3e7; color:#df6735; font-size:20rpx; font-weight:950; }
.amount-panel { margin:10rpx 0 8rpx; padding:16rpx; border:1rpx solid rgba(255,217,189,.64); border-radius:24rpx; background:linear-gradient(135deg,#fff8ef,#fff0e5); text-align:center; box-shadow:inset 0 0 0 1rpx rgba(255,255,255,.68); }
.amount-label { color:#8f6b57; font-size:20rpx; font-weight:850; }
.amount-value { margin-top:5rpx; color:#df6735; font-size:40rpx; font-weight:950; letter-spacing:.2rpx; }
.amount-note { margin-top:5rpx; color:#a8785e; font-size:18rpx; font-weight:750; }
.info-row { min-height:50rpx; display:flex; justify-content:space-between; align-items:center; border-bottom:1rpx solid rgba(255,217,189,.54); color:#7b5542; font-size:21rpx; gap:18rpx; }
.info-row:last-child { border-bottom:0; }
.info-row text:last-child { color:#342116; font-weight:900; text-align:right; }
.pay-method { margin-top:10rpx; padding:14rpx; border-radius:22rpx; border:1rpx solid rgba(255,217,189,.78); background:linear-gradient(180deg,#fffdf9,#fff8f1); display:flex; align-items:center; gap:11rpx; }
.pay-method.active { border-color:rgba(239,111,63,.62); background:linear-gradient(135deg,#fff7ed,#ffeddd); box-shadow:0 10rpx 22rpx rgba(255,122,69,.10); }
.method-icon { width:48rpx; height:48rpx; border-radius:18rpx; background:rgba(255,255,255,.92); display:flex; align-items:center; justify-content:center; font-size:24rpx; box-shadow:inset 0 0 0 1rpx rgba(255,217,189,.46); }
.method-main { flex:1; min-width:0; }
.method-title { color:#342116; font-size:23rpx; font-weight:950; }
.method-desc,.safe-line { margin-top:6rpx; color:#8f6b57; font-size:19rpx; line-height:1.4; font-weight:650; }
.radio { width:30rpx; height:30rpx; border-radius:50%; border:1rpx solid rgba(255,217,189,.80); color:transparent; display:flex; align-items:center; justify-content:center; flex-shrink:0; font-size:18rpx; font-weight:950; }
.radio.active { background:linear-gradient(135deg,#ef6f3f,#ff8b76); border-color:#ef6f3f; color:#fffaf4; }
.message { color:#15803d; font-size:21rpx; line-height:1.45; background:rgba(240,253,244,.94); border-color:rgba(34,197,94,.16); }
.message.error { color:#be123c; background:#fff1f2; border-color:rgba(244,63,94,.14); }
.bottom-bar { position:fixed; left:0; right:0; bottom:0; padding:12rpx 18rpx calc(12rpx + env(safe-area-inset-bottom)); background:rgba(255,250,246,.96); border-top:1rpx solid rgba(255,217,189,.78); display:flex; justify-content:space-between; align-items:center; gap:14rpx; z-index:20; backdrop-filter:blur(16rpx); box-shadow:0 -14rpx 32rpx rgba(132,70,36,.09); }
.pay-label { color:#8f6b57; font-size:20rpx; font-weight:800; }
.pay-amount { color:#df6735; font-size:34rpx; font-weight:950; letter-spacing:.2rpx; }
.submit { width:260rpx; height:54rpx; line-height:54rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; box-shadow:0 10rpx 20rpx rgba(255,122,69,.15); }
</style>
