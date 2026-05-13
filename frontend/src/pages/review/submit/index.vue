<template>
  <view class="page-shell review-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 交易评价</view>
        <view class="page-title">评价这次交易</view>
        <view class="page-desc">评价提交后以平台订单评价记录为准。</view>
      </view>
      <view class="hero-icon">⭐</view>
    </view>
    <view class="form-card ds-card">
      <view class="section-title">评分</view>
      <view v-for="item in scores" :key="item.key" class="score-row">
        <text>{{ item.label }}</text>
        <view><text v-for="star in 5" :key="star" class="star tapable" :class="{ active: star <= item.value }" @click="item.value = star">★</text></view>
      </view>
      <textarea v-model.trim="content" class="textarea" maxlength="160" placeholder="说说成色、沟通、发货和整体体验" />
      <view class="fail-closed-tip">仅订单完成后的买家可提交一次评价；提交结果以平台订单评价记录为准。</view>
      <button class="primary-btn" :disabled="submitting" @click="submitReview">{{ submitting ? '提交中...' : '提交评价' }}</button>
    </view>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitOrderReview } from '../../../api/modules/order'

const scores = reactive([{ key: 'desc', label: '描述相符', value: 5 }, { key: 'service', label: '沟通服务', value: 5 }, { key: 'ship', label: '发货速度', value: 5 }])
const content = ref('')
const orderNo = ref('')
const submitting = ref(false)

function isValidOrderNo(value: string) {
  return /^OD-[A-Z0-9]{4,}$/.test(value)
}

onLoad((query) => {
  const rawOrderNo = typeof query?.orderNo === 'string' ? decodeURIComponent(query.orderNo) : ''
  orderNo.value = isValidOrderNo(rawOrderNo) ? rawOrderNo : ''
})

async function submitReview() {
  if (!isValidOrderNo(orderNo.value)) return uni.showToast({ title: '缺少有效订单编号，评价未提交', icon: 'none' })
  if (content.value.length < 6) return uni.showToast({ title: '请补充至少6个字评价', icon: 'none' })
  if (submitting.value) return
  submitting.value = true
  try {
    await submitOrderReview(orderNo.value, {
      descriptionScore: scores[0].value,
      serviceScore: scores[1].value,
      shippingScore: scores[2].value,
      content: content.value
    })
    uni.showModal({ title: '评价已提交', content: '评价已写入平台订单评价记录。', showCancel: false, success: () => uni.navigateTo({ url: '/pages/order/list/index' }) })
  } catch {
    uni.showToast({ title: '评价提交失败，请确认订单已完成且未重复评价', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>
<style scoped>.review-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.form-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.section-title{font-weight:950;margin-bottom:16rpx}.score-row{display:flex;align-items:center;justify-content:space-between;padding:16rpx 0;border-bottom:1rpx solid #fff0e5}.star{font-size:36rpx;color:#f0d0bd;margin-left:8rpx}.star.active{color:#ff9f43}.textarea{width:100%;min-height:180rpx;margin-top:18rpx;padding:18rpx;border-radius:22rpx;background:#fff8f0;box-sizing:border-box}.fail-closed-tip{margin-top:14rpx;color:#9b6a4d;font-size:23rpx}.primary-btn{margin-top:20rpx}</style>
