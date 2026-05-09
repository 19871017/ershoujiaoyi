<template>
  <view class="page-shell admin-entry">
    <view class="hero ds-card">
      <view class="lock">🛡️</view>
      <view>
        <view class="page-title">后台入口</view>
        <view class="page-desc">开发阶段后台入口已加 Admin Dev Header，正式上线需替换 RBAC 管理员登录。</view>
      </view>
    </view>

    <view class="notice ds-card">
      <view class="notice-title">安全提示</view>
      <view class="notice-text">当前仅用于本地开发预览：审核、提现、定位配置等敏感操作必须走管理员鉴权。</view>
    </view>

    <view class="module-grid">
      <view v-for="item in modules" :key="item.title" class="module-card ds-card tapable" @click="openModule(item)">
        <view class="module-icon">{{ item.icon }}</view>
        <view class="module-title">{{ item.title }}</view>
        <view class="module-desc">{{ item.desc }}</view>
      </view>
    </view>

    <button class="primary-btn" @click="goHome">进入后台首页</button>
  </view>
</template>

<script setup lang="ts">
const modules = [
  { icon: '📋', title: '审核中心', desc: '商品、举报、认证审核', url: '/pages/admin/home/index?tab=audit' },
  { icon: '💸', title: '提现审核', desc: '冻结资金、审核打款', url: '/pages/admin/home/index?tab=withdraw' },
  { icon: '🧭', title: '定位配置', desc: '地图服务和默认城市', url: '/pages/admin/home/index?tab=config' },
  { icon: '📊', title: '风控看板', desc: '订单、举报、异常交易', url: '/pages/admin/home/index?tab=risk' }
]
function goHome() { uni.navigateTo({ url: '/pages/admin/home/index' }) }
function openModule(item: { url: string }) { uni.navigateTo({ url: item.url }) }
</script>

<style scoped>
.admin-entry { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.hero { padding:26rpx; display:flex; gap:18rpx; align-items:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.lock { width:84rpx; height:84rpx; border-radius:28rpx; background:#3a2a1f; display:flex; align-items:center; justify-content:center; font-size:38rpx; }
.notice { margin-top:20rpx; padding:22rpx; border-color:#ffd9bd; }
.notice-title { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.notice-text { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.5; }
.module-grid { margin-top:20rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:14rpx; }
.module-card { min-height:188rpx; padding:22rpx; border-color:#ffd9bd; }
.module-icon { font-size:38rpx; }
.module-title { margin-top:14rpx; color:#3a2a1f; font-size:28rpx; font-weight:950; }
.module-desc { margin-top:8rpx; color:#9b7560; font-size:21rpx; line-height:1.4; }
.primary-btn { margin-top:26rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:28rpx; font-weight:950; }
</style>
