<template>
  <view class="page-shell risk-detail">
    <view class="hero ds-card">
      <view>
        <view class="kicker">Admin Risk</view>
        <view class="page-title">风控详情</view>
        <view class="page-desc">风控处理统一跳转真实审核详情，避免本地冻结/处理完成假动作。</view>
      </view>
      <view class="hero-icon">🛡️</view>
    </view>
    <view class="section-card ds-card">
      <view class="section-title">需要真实审核单</view>
      <view class="page-desc">若 URL 带有 auditNo，将进入后台审核详情；本页不会执行任何资金/订单动作。</view>
      <button class="primary-btn" @click="goAuditDetail">进入审核详情</button>
    </view>
  </view>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
const auditNo = ref('')
function readQuery(){const pages=getCurrentPages(); const current=pages.length?pages[pages.length-1] as unknown as {options?:Record<string,string>}:undefined; auditNo.value=current?.options?.auditNo || ''}
function goAuditDetail(){if(!auditNo.value)return uni.showToast({title:'缺少真实审核单号',icon:'none'}); uni.redirectTo({url:`/pages/admin/audit/detail/index?auditNo=${encodeURIComponent(auditNo.value)}`})}
onMounted(readQuery)
</script>
<style scoped>.risk-detail{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.section-card{margin-top:18rpx;padding:24rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#3a2a1f;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.section-title{color:#3a2a1f;font-size:30rpx;font-weight:950}.page-desc{margin-top:10rpx;color:#7b5542;font-size:23rpx;line-height:1.5}.primary-btn{margin-top:20rpx;border-radius:999rpx;background:#ff7a45;color:#fff;font-size:25rpx;font-weight:950}</style>
