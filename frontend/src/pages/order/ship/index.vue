<template>
  <view class="page-shell ship-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 卖家履约</view>
        <view class="page-title">填写发货信息</view>
        <view class="page-desc">付款后请尽快发货，平台会把物流信息同步给买家。</view>
      </view>
      <view class="hero-icon">📦</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">订单 {{ orderNo || '未选择' }}</view>
      <view class="ship-row">
        <view v-for="item in shipTypes" :key="item.value" class="ship-chip tapable" :class="{ active: shipType === item.value }" @click="shipType = item.value">{{ item.label }}</view>
      </view>
      <input v-if="shipType === 'EXPRESS'" v-model.trim="company" class="field" maxlength="24" placeholder="快递公司，例如顺丰/圆通" />
      <input v-if="shipType === 'EXPRESS'" v-model.trim="trackingNo" class="field" maxlength="40" placeholder="运单号" />
      <textarea v-model.trim="remark" class="textarea" maxlength="80" :placeholder="shipType === 'MEETUP' ? '请填写线下交付地点/时间，正式履约状态以后端订单记录为准' : '发货备注，可说明包装、清洁、凭证等'" />
      <button class="primary-btn" :disabled="submitting" @click="submitShip">{{ submitting ? '提交中...' : '确认发货' }}</button>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">发货安全提醒</view>
      <view class="safe-line">请保留发货凭证，避免使用无法追踪的物流方式。</view>
      <view class="safe-line">线下交付建议选择公共地点；订单、发货和售后状态以服务端记录为准。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { shipOrder, type ShippingType } from '../../../api/modules/order'

const orderNo = ref('')
const shipTypes: Array<{ label: string; value: ShippingType }> = [
  { label: '快递邮寄', value: 'EXPRESS' },
  { label: '同城当面交付', value: 'MEETUP' }
]
const shipType = ref<ShippingType>('EXPRESS')
const company = ref('')
const trackingNo = ref('')
const remark = ref('')
const submitting = ref(false)
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  orderNo.value = current?.options?.orderNo || hashParams?.get('orderNo') || ''
}
async function submitShip() {
  if (!orderNo.value) return uni.showToast({ title: '缺少订单号', icon: 'none' })
  if (shipType.value === 'EXPRESS' && (!company.value || !trackingNo.value)) return uni.showToast({ title: '请填写快递公司和运单号', icon: 'none' })
  if (shipType.value === 'MEETUP' && !remark.value) return uni.showToast({ title: '请填写同城交付备注', icon: 'none' })
  submitting.value = true
  try {
    const res = await shipOrder(orderNo.value, { shippingType: shipType.value, shippingCompany: company.value, trackingNo: trackingNo.value, remark: remark.value })
    uni.showModal({ title: '已同步发货', content: `订单已更新为已发货，发货时间：${res.shippedAt || '刚刚'}`, showCancel: true, confirmText: '查看物流', cancelText: '返回订单', success: (modal) => { const url = modal.confirm ? `/pages/order/logistics/index?orderNo=${encodeURIComponent(orderNo.value)}` : `/pages/order/detail/index?orderNo=${encodeURIComponent(orderNo.value)}`; uni.redirectTo({ url }) } })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '发货失败', icon: 'none' })
  } finally { submitting.value = false }
}
onMounted(readQuery)
</script>

<style scoped>
.ship-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }.hero,.form-card,.safe-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.hero { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.ship-row { margin-top:16rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:12rpx; }.ship-chip { padding:16rpx; border-radius:999rpx; text-align:center; background:#fffaf6; border:1rpx solid #ffd9bd; color:#7b5542; font-size:23rpx; font-weight:900; }.ship-chip.active { background:#fff3e7; border-color:#ff7a45; color:#ff7a45; }.field,.textarea { box-sizing:border-box; width:100%; margin-top:16rpx; padding:0 18rpx; border-radius:24rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:24rpx; }.field { height:82rpx; }.textarea { height:130rpx; padding-top:18rpx; }.primary-btn { margin-top:18rpx; }.primary-btn[disabled] { opacity:.65; }.safe-line { margin-top:8rpx; color:#9b7560; font-size:22rpx; line-height:1.55; }
</style>
