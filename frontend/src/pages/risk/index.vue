<template>
  <view class="page-shell risk-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 安全中心</view>
        <view class="page-title">举报与风控</view>
        <view class="page-desc">用户端只保留举报说明和安全提示；审核、提现复核、配置等操作统一迁移到独立管理后台。</view>
      </view>
      <view class="hero-icon">🛡️</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">用户可做什么</view>
      <view class="policy">遇到私下转账、诱导加外部联系方式、虚假商品、交易纠纷或异常售后，请从用户主页、商品详情、聊天或订单详情发起举报。</view>
      <button class="primary-btn" @click="goReport">查看举报入口说明</button>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">平台处理原则</view>
      <view class="policy">举报、认证、提现复核和异常交易会进入平台管理后台，由管理员按审计记录处理；用户端不再暴露后台审核入口。</view>
      <view class="safe-tags">
        <text>拒绝私下转账</text>
        <text>保留聊天证据</text>
        <text>通过订单售后处理</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
function goReport() { uni.showToast({ title: '请从用户、商品、聊天或订单详情页发起举报', icon: 'none' }) }
</script>

<style scoped>
.risk-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }.hero,.section-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.hero { display:flex; justify-content:space-between; background:linear-gradient(135deg,#fff,#fff3e7); }.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#3a2a1f; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.policy { margin-top:10rpx; color:#7b5542; font-size:23rpx; line-height:1.6; word-break:break-all; }.safe-tags { margin-top:18rpx; display:flex; gap:10rpx; flex-wrap:wrap; }.safe-tags text { padding:8rpx 12rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }.primary-btn { margin-top:18rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:25rpx; font-weight:950; }
</style>
