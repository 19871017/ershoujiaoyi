<template>
  <view class="page-shell logistics-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 履约跟踪</view>
        <view class="page-title">物流详情</view>
        <view class="page-desc">查看卖家发货、配送/交付方式以平台订单记录为准。</view>
      </view>
      <view class="hero-icon">🚚</view>
    </view>

    <view v-if="loading" class="ship-card ds-card"><view class="section-title">正在读取物流信息</view><view class="safe-line">请稍等，平台正在同步订单履约状态。</view></view>
    <view v-else-if="errorText" class="ship-card ds-card danger"><view class="section-title">物流读取失败</view><view class="safe-line">{{ errorText }}</view></view>

    <template v-else-if="order">
      <view class="ship-card ds-card">
        <view class="section-title">配送信息</view>
        <view class="info-row"><text>订单号</text><text>{{ order.orderNo }}</text></view>
        <view class="info-row"><text>配送方式</text><text>{{ shippingTypeText }}</text></view>
        <view class="info-row"><text>快递公司</text><text>{{ order.shippingCompany || '未填写' }}</text></view>
        <view class="info-row"><text>运单号</text><text>{{ order.trackingNo || '未填写' }}</text></view>
        <view class="info-row"><text>发货备注</text><text>{{ order.shippingRemark || '无' }}</text></view>
      </view>

      <view class="timeline-card ds-card">
        <view class="section-title">履约轨迹</view>
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

const orderNo = ref('')
const order = ref<OrderDetailResponse | null>(null)
const loading = ref(false)
const errorText = ref('')
const shippingTypeText = computed(() => order.value?.shippingType === 'MEETUP' ? '线下交付' : '快递邮寄')
const timeline = computed(() => {
  const item = order.value
  if (!item) return []
  return [
    { title: item.shippedAt ? '卖家已发货' : '等待卖家发货', desc: item.shippedAt ? '卖家已提交履约信息，配送/交付方式以平台订单记录为准。' : '付款完成后卖家需要提交真实发货信息。', time: item.shippedAt || '' },
    { title: item.paidAt ? '订单已付款' : '等待付款', desc: item.paidAt ? '订单付款状态来自平台订单详情，履约与结算请以订单状态为准。' : '买家完成付款后才会进入后续履约流程。', time: item.paidAt || '' },
    { title: '订单已创建', desc: '订单已创建，后续履约状态以平台订单、支付和物流记录为准。', time: item.createdAt }
  ]
})
function readQuery() { const pages = getCurrentPages(); const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined; const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined; orderNo.value = current?.options?.orderNo || hashParams?.get('orderNo') || '' }
function isValidBackendOrderNo(value: string) { return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value) }
async function load() {
  if (!isValidBackendOrderNo(orderNo.value)) { errorText.value = '缺少有效订单号，请从订单详情进入'; order.value = null; return }
  loading.value = true; errorText.value = ''
  try { order.value = await getOrderDetail(orderNo.value) }
  catch (error) { order.value = null; errorText.value = error instanceof Error ? error.message : '物流详情读取失败' }
  finally { loading.value = false }
}
onMounted(() => { readQuery(); void load() })
</script>

<style scoped>
.logistics-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }.hero,.ship-card,.timeline-card,.safe-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.hero { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.danger{border-color:#fecaca;background:#fff7f7}.info-row { min-height:62rpx; display:flex; justify-content:space-between; align-items:center; border-bottom:1rpx solid #ffe5ef; color:#7b5542; font-size:23rpx; gap:18rpx; }.info-row:last-child { border-bottom:0; }.info-row text:last-child { color:#3a2a1f; font-weight:900; text-align:right; }.timeline-item { position:relative; margin-top:20rpx; display:flex; gap:16rpx; }.dot { width:22rpx; height:22rpx; margin-top:8rpx; border-radius:50%; background:#ff7a45; box-shadow:0 0 0 8rpx #fff3e7; }.track-main { flex:1; padding-bottom:18rpx; border-bottom:1rpx solid #ffe5ef; }.track-title { color:#3a2a1f; font-size:26rpx; font-weight:950; }.track-desc,.safe-line { margin-top:6rpx; color:#9b7560; font-size:22rpx; line-height:1.55; }.track-time { margin-top:8rpx; color:#c0849d; font-size:20rpx; }
</style>
