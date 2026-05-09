<template>
  <view class="page-shell favorite-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 我的心愿夹</view>
        <view class="page-title">我的收藏</view>
        <view class="page-desc">收藏列表接口尚未接入，当前不展示本地收藏样例。</view>
      </view>
      <view class="hero-icon">💗</view>
    </view>
    <view class="filter-row">
      <view v-for="item in filters" :key="item" class="filter-chip tapable" :class="{ active: active === item }" @click="active = item">{{ item }}</view>
    </view>
    <view class="empty-card ds-card" @click="openProductUnavailable">收藏列表接口尚未接入，未展示本地收藏样例</view>
    <view class="fav-grid">
      <view v-for="item in filtered" :key="item.id" class="fav-card ds-card tapable" @click="openProductUnavailable">
        <view class="cover">{{ item.icon }}</view>
        <view class="title">{{ item.title }}</view>
        <view class="meta">{{ item.city }} · {{ item.status }}</view>
        <view class="bottom"><text>¥{{ item.price }}</text><button class="mini-btn" @click.stop="unfav">取消</button></view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
const filters = ['全部', '衣物', '鞋袜', '小用品']
const active = ref('全部')
const favorites = computed(() => [] as { id: number; icon: string; title: string; category: string; city: string; price: string; status: string }[])
const filtered = computed(() => active.value === '全部' ? favorites.value : favorites.value.filter((item) => item.category === active.value))
function openProductUnavailable() { uni.showToast({ title: '收藏列表接口尚未接入，未打开本地收藏商品', icon: 'none' }) }
function unfav() { uni.showToast({ title: '收藏接口暂未接通后端，未执行任何收藏变更', icon: 'none' }) }
</script>
<style scoped>
.favorite-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.fav-card,.empty-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.filter-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.filter-chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.filter-chip.active{background:#3a2a1f;color:#fff}.empty-card{color:#9b7560;font-size:24rpx;line-height:1.5;text-align:center;background:#fffdfa}.fav-grid{margin-top:16rpx;display:grid;grid-template-columns:repeat(2,1fr);gap:14rpx}.cover{height:180rpx;border-radius:28rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:58rpx}.title{margin-top:12rpx;color:#3a2a1f;font-size:25rpx;font-weight:950}.meta{margin-top:6rpx;color:#9b7560;font-size:20rpx}.bottom{margin-top:10rpx;display:flex;align-items:center;justify-content:space-between;color:#ff3f8d;font-size:28rpx;font-weight:950}.mini-btn{margin:0;padding:0 16rpx;height:46rpx;line-height:46rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#7b5542;font-size:20rpx}
</style>
