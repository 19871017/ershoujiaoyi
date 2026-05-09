<template>
  <view class="page-shell account-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 收款账户</view>
        <view class="page-title">提现账户管理</view>
        <view class="page-desc">提现账户后端绑定尚未接入，当前页面不采集完整账号。</view>
      </view>
      <view class="hero-icon">💳</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">账户安全规则</view>
      <view class="desc">完整收款账号应只提交给后端账户绑定接口，并由服务端保存、脱敏和复核。</view>
      <view class="desc">当前未接入绑定接口时，页面只展示规则说明；未保存账号，也未生成可用于提现的账户引用。</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">绑定状态</view>
      <view class="status-box">后端提现账户绑定接口尚未接入，未提交完整账号。</view>
      <view class="desc">不在前端生成脱敏账户引用；提现页仍会要求后端拥有的账户状态，缺失时 fail-closed。</view>
      <button class="primary-btn" @click="openBackendBindingUnavailable">等待后端绑定接口</button>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">安全提醒</view>
      <view class="desc">平台不会索要支付密码、短信验证码或完整身份证号。收款账户变更后必须重新审核，不走本地伪校验。</view>
    </view>
  </view>
</template>
<script setup lang="ts">
function openBackendBindingUnavailable() {
  uni.showToast({ title: '后端绑定接口未接入，未提交任何账号', icon: 'none' })
}
</script>
<style scoped>.account-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.section-card{margin-top:18rpx;padding:24rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:78rpx;height:78rpx;border-radius:24rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:34rpx}.section-title{color:#3a2a1f;font-size:30rpx;font-weight:950}.desc,.page-desc,.status-box{margin-top:8rpx;color:#9b7560;font-size:22rpx;line-height:1.5}.status-box{padding:16rpx;border-radius:22rpx;background:#fffaf6;color:#3a2a1f;font-weight:900}.primary-btn{margin-top:18rpx;border-radius:999rpx;background:#ff7a45;color:#fff;font-size:25rpx;font-weight:950}</style>
