<template>
  <view class="page-shell logistics-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 履约跟踪</view>
        <view class="page-title">物流详情</view>
        <view class="page-desc">查看卖家发货、配送/交付方式以服务端订单记录为准。</view>
      </view>
      <view class="hero-icon">🚚</view>
    </view>

    <view v-if="loading" class="status-card ds-card">
      <view class="status-icon">⌛</view>
      <view>
        <view class="section-title">正在读取物流信息</view>
        <view class="safe-line">请稍等，平台正在同步订单履约状态。</view>
      </view>
    </view>
    <view v-else-if="errorText" class="status-card ds-card danger">
      <view class="status-icon">!</view>
      <view>
        <view class="section-title">物流读取失败</view>
        <view class="safe-line">{{ errorText }}</view>
      </view>
    </view>

    <template v-else-if="order">
      <view class="ship-card ds-card">
        <view class="section-head">
          <view>
            <view class="section-title">配送信息</view>
            <view class="section-desc">以下履约信息均来自服务端订单记录。</view>
          </view>
          <view class="ship-chip">{{ shippingTypeText }}</view>
        </view>
        <view class="info-row"><text>订单号</text><text>{{ order.orderNo }}</text></view>
        <view class="info-row"><text>快递公司</text><text>{{ order.shippingCompany || '未填写' }}</text></view>
        <view class="info-row"><text>运单号</text><text>{{ order.trackingNo || '未填写' }}</text></view>
        <view class="info-row"><text>发货备注</text><text>{{ order.shippingRemark || '无' }}</text></view>
      </view>

      <view class="timeline-card ds-card">
        <view class="section-title">履约轨迹</view>
        <view class="section-desc">付款、发货、创建时间按后端订单状态展示。</view>
        <view v-for="item in timeline" :key="item.title" class="timeline-item">
          <view class="dot"></view>
          <view class="track-main">
            <view class="track-title">{{ item.title }}</view>
            <view class="track-desc">{{ item.desc }}</view>
            <view class="track-time">{{ item.time || '待完成' }}</view>
          </view>
        </view>
      </view>
    </template>

    <view class="safe-card ds-card">
      <view class="section-title">收货提醒</view>
      <view class="safe-line">衣物鞋袜类宝贝请先确认成色、尺码、清洁状态，再确认收货。</view>
      <view class="safe-line">如物流异常或商品不符，请保留凭证并申请售后。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getOrderDetail, type OrderDetailResponse } from '../../../api/modules/order'
const launchReadinessMarkers = [
  '配送/交付方式以服务端订单记录为准',
  '订单已创建，后续履约状态以服务端订单、支付和物流记录为准。'
]

const backendOrderNoPattern = /^OD-[0-9]{1,10}$/
const orderNo = ref('')
const order = ref<OrderDetailResponse | null>(null)
const loading = ref(false)
const errorText = ref('')
const shippingTypeText = computed(() => {
  if (order.value?.shippingType === 'MEETUP') return '线下交付'
  if (order.value?.shippingType === 'EXPRESS') return '快递邮寄'
  return '未填写'
})
const timeline = computed(() => {
  const item = order.value
  if (!item) return []
  return [
    { title: item.shippedAt ? '卖家已发货' : '等待卖家发货', desc: item.shippedAt ? '卖家已提交履约信息，配送/交付方式以服务端订单记录为准。' : '付款完成后卖家需要提交真实发货信息。', time: item.shippedAt || '' },
    { title: item.paidAt ? '订单已付款' : '等待付款', desc: item.paidAt ? '订单付款状态来自平台订单详情，履约与结算请以订单状态为准。' : '买家完成付款后才会进入后续履约流程。', time: item.paidAt || '' },
    { title: '订单已创建', desc: '订单已创建，后续履约状态以服务端订单、支付和物流记录为准。', time: item.createdAt }
  ]
})
function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('order logistics route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}
function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeOrderNo = decodeRouteValue('orderNo', current?.options?.orderNo || hashParams?.get('orderNo') || '')
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    console.warn('order logistics invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  orderNo.value = routeOrderNo
}
function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}
async function load(): Promise<void> {
  if (!isValidBackendOrderNo(orderNo.value)) {
    errorText.value = '缺少有效订单号，请从订单详情进入'
    order.value = null
    return
  }

  loading.value = true
  errorText.value = ''

  const safeOrderNo = orderNo.value

  try {
    const detail = await getOrderDetail(safeOrderNo)

    if (!isValidBackendOrderNo(detail.orderNo)) throw new Error('order logistics invalid backend orderNo')
    if (detail.orderNo !== safeOrderNo) throw new Error('order logistics orderNo mismatch')

    order.value = detail
  } catch (error) {
    console.warn('order logistics load failed', { orderNo: safeOrderNo, error })
    order.value = null
    errorText.value = '物流详情读取失败，请从订单详情重新进入'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  readQuery()
  void load()
})
</script>

<style scoped>
.logistics-page { min-height:100vh; padding-top:18rpx; padding-bottom:44rpx; background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%); }
.hero,.ship-card,.timeline-card,.safe-card,.status-card { margin-top:14rpx; padding:20rpx; border-color:rgba(255,217,189,.78); box-shadow:0 14rpx 30rpx rgba(132,70,36,.08); }
.hero,.status-card { display:flex; align-items:center; gap:16rpx; background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96)); }
.hero { margin-top:0; justify-content:space-between; }
.status-card { justify-content:flex-start; }
.ship-card,.timeline-card,.safe-card { background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97)); }
.kicker { color:#df6735; font-size:20rpx; font-weight:950; letter-spacing:.16rpx; }
.hero-icon,.status-icon { width:70rpx; height:70rpx; border-radius:24rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; display:flex; align-items:center; justify-content:center; font-size:32rpx; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); flex:0 0 auto; }
.danger .status-icon { background:#ef4444; box-shadow:0 10rpx 20rpx rgba(239,68,68,.14); }
.section-title { color:#342116; font-size:26rpx; font-weight:950; letter-spacing:.14rpx; }
.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; margin-bottom:8rpx; }
.section-desc { margin-top:7rpx; color:#8f6b57; font-size:20rpx; line-height:1.43; font-weight:650; }
.ship-chip { flex:0 0 auto; padding:7rpx 13rpx; border:1rpx solid rgba(239,111,63,.20); border-radius:999rpx; background:#fff3e7; color:#df6735; font-size:20rpx; font-weight:950; }
.danger { border-color:#fecaca; background:#fff7f7; }
.info-row { min-height:56rpx; display:flex; justify-content:space-between; align-items:center; border-bottom:1rpx solid rgba(255,217,189,.52); color:#7b5542; font-size:21rpx; font-weight:760; gap:16rpx; }
.info-row:last-child { border-bottom:0; }
.info-row text:first-child { flex:0 0 auto; color:#8f6b57; font-weight:850; }
.info-row text:last-child { color:#342116; font-weight:930; text-align:right; }
.timeline-item { margin-top:16rpx; display:flex; gap:14rpx; }
.dot { width:20rpx; height:20rpx; margin-top:8rpx; border-radius:50%; background:#ef6f3f; box-shadow:0 0 0 7rpx rgba(239,111,63,.10); flex:0 0 auto; }
.track-main { flex:1; min-width:0; padding-bottom:16rpx; border-bottom:1rpx solid rgba(255,217,189,.52); }
.timeline-item:last-child .track-main { border-bottom:0; padding-bottom:0; }
.track-title { color:#342116; font-size:24rpx; font-weight:950; letter-spacing:.12rpx; }
.track-desc,.safe-line { margin-top:6rpx; color:#8f6b57; font-size:20rpx; line-height:1.48; font-weight:650; }
.track-time { margin-top:8rpx; color:#b77955; font-size:19rpx; font-weight:850; }
</style>
