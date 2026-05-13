<template>
  <view class="page-shell empty-page">
    <view class="empty-card ds-card">
      <view class="empty-icon">{{ icon }}</view>
      <view class="empty-title">{{ title }}</view>
      <view class="empty-desc">{{ desc }}</view>
      <button class="primary-btn" @click="goHome">回到首页</button>
      <button class="secondary-btn" @click="goPublish">去上新宝贝</button>
    </view>

    <view class="tips ds-card">
      <view class="tip-title">你还可以</view>
      <view v-for="item in tips" :key="item" class="tip-row">{{ item }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
const icon = '🧺'
const title = '这里还没有内容'
const desc = '可以先去首页看看推荐宝贝，或者把闲置衣物、鞋袜、小用品发布出来。'
const tips = ['完善个人资料，便于平台记录同步', '交易相关状态以订单、支付和售后记录为准', '遇到异常沟通，请保留平台聊天记录并走正式举报入口']
function goHome() { uni.switchTab({ url: '/pages/tabbar/home/index' }) }
function goPublish() { uni.switchTab({ url: '/pages/tabbar/publish/index' }) }
</script>

<style scoped>
.empty-page { min-height:100vh; display:flex; flex-direction:column; justify-content:center; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.empty-card,.tips { padding:34rpx 26rpx; text-align:center; border-color:#ffd9bd; }
.empty-icon { font-size:88rpx; }
.empty-title { margin-top:16rpx; color:#3a2a1f; font-size:34rpx; font-weight:950; }
.empty-desc { margin-top:10rpx; color:#9b7560; font-size:24rpx; line-height:1.55; }
.primary-btn,.secondary-btn { margin-top:22rpx; border-radius:999rpx; font-size:26rpx; font-weight:950; }
.primary-btn { background:#ff7a45; color:#fff; }
.secondary-btn { background:#fff3e7; color:#ff7a45; }
.tips { margin-top:22rpx; text-align:left; }
.tip-title { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.tip-row { margin-top:14rpx; padding:15rpx 18rpx; border-radius:20rpx; background:#fffaf6; color:#7b5542; font-size:23rpx; }
</style>
