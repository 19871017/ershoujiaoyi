<template>
  <view class="page-shell city-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 城市偏好</view>
        <view class="page-title">选择城市</view>
        <view class="page-desc">定位失败时可先筛选城市偏好；正式位置状态以后端或系统定位记录为准。</view>
      </view>
      <view class="hero-icon">📍</view>
    </view>

    <view class="search ds-card">
      <text>🔎</text>
      <input v-model.trim="keyword" placeholder="搜索城市，如 深圳" />
    </view>

    <view class="section-card ds-card">
      <view class="section-title">热门城市</view>
      <view class="city-grid">
        <view v-for="city in filteredCities" :key="city" class="city-chip tapable" :class="{ active: selected === city }" @click="selectCity(city)">{{ city }}</view>
      </view>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">位置使用提醒</view>
      <view class="desc">城市偏好只影响本次页面筛选；订单、支付、发货和售后状态以服务端记录为准。</view>
    </view>

    <button class="primary-btn" :disabled="!canConfirm" @click="saveCity">确认城市</button>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
const keyword = ref('')
const selected = ref('')
const cities = ['深圳', '广州', '杭州', '成都', '上海', '北京', '南京', '武汉', '长沙', '厦门', '重庆', '苏州']
const filteredCities = computed(() => keyword.value ? cities.filter((item) => item.includes(keyword.value)) : cities)
const canConfirm = computed(() => Boolean(selected.value))
function selectCity(city: string) {
  selected.value = city
}
function saveCity() {
  if (!canConfirm.value) {
    uni.showToast({ title: '请先选择城市', icon: 'none' })
    return
  }
  uni.showModal({
    title: '城市偏好接口尚未接入',
    content: '城市偏好接口尚未接入，未保存为正式位置偏好，当前未执行任何位置变更。请返回首页继续使用后端默认城市。',
    showCancel: false,
    success: () => uni.switchTab({ url: '/pages/tabbar/home/index' })
  })
}
</script>
<style scoped>
.city-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.search,.section-card,.safe-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.search{display:flex;align-items:center;gap:14rpx}.search input{flex:1;color:#3a2a1f;font-size:25rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.city-grid{margin-top:18rpx;display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx}.city-chip{height:66rpx;border-radius:999rpx;background:#fffaf6;border:1rpx solid #ffd9bd;color:#7b5542;display:flex;align-items:center;justify-content:center;font-size:23rpx;font-weight:900}.city-chip.active{background:#ff7a45;color:#fff;border-color:#ff7a45}.desc{margin-top:10rpx;color:#9b7560;font-size:23rpx;line-height:1.55}.primary-btn{margin-top:22rpx}
</style>
