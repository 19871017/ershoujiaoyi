<template>
  <view class="page-shell review-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 交易评价</view>
        <view class="page-title">评价这次交易</view>
        <view class="page-desc">评价接口尚未接入，当前仅允许本地编辑草稿。</view>
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
      <view class="fail-closed-tip">评价接口尚未接入：提交不会更新信用分或榜单热度，也不会展示为真实交易评价。</view>
      <button class="primary-btn" @click="submitReview">校验评价草稿</button>
    </view>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
const scores = reactive([{ key: 'desc', label: '描述相符', value: 5 }, { key: 'service', label: '沟通服务', value: 5 }, { key: 'ship', label: '发货速度', value: 5 }])
const content = ref('')
function submitReview(){ if(content.value.length < 6) return uni.showToast({title:'请补充至少6个字评价',icon:'none'}); uni.showModal({title:'评价接口尚未接入',content:'评价草稿已通过本地校验，但不会更新信用分或榜单热度。请等待后端评价接口接入后再提交正式评价。',showCancel:true,confirmText:'返回订单',cancelText:'继续编辑',success:(res)=>{ if(res.confirm) uni.navigateTo({ url: '/pages/order/list/index' }) }}) }
</script>
<style scoped>.review-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.form-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.score-row{min-height:74rpx;display:flex;align-items:center;justify-content:space-between;color:#3a2a1f;font-size:25rpx;font-weight:900;border-bottom:1rpx solid #ffe5ef}.star{font-size:34rpx;color:#ffd9bd;margin-left:6rpx}.star.active{color:#ffb000}.textarea{box-sizing:border-box;width:100%;height:150rpx;margin-top:18rpx;padding:18rpx;border-radius:24rpx;background:#fffaf6;border:1rpx solid #ffd9bd;color:#3a2a1f;font-size:24rpx}.fail-closed-tip{margin-top:14rpx;padding:14rpx 16rpx;border-radius:20rpx;background:#fff3e7;color:#8a4a1f;font-size:23rpx;line-height:1.55}.primary-btn{margin-top:18rpx}</style>