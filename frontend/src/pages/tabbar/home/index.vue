<template>
  <view class="page-shell home-page">
    <view class="brand-hero tapable" @click="openSearch" aria-label="XYQ brand banner">
      <view class="hero-orbit hero-orbit-a"></view>
      <view class="hero-orbit hero-orbit-b"></view>
      <view class="hero-glow"></view>
      <view class="hero-logo">
        <view class="logo-ring">
          <text class="logo-letter">XYQ</text>
        </view>
      </view>
      <view class="hero-visual">
        <view class="visual-card card-main"></view>
        <view class="visual-card card-soft"></view>
        <view class="visual-dot dot-a"></view>
        <view class="visual-dot dot-b"></view>
        <view class="visual-line line-a"></view>
        <view class="visual-line line-b"></view>
      </view>
    </view>

    <view class="section-head">
      <view>
        <view class="section-title">今日小原圈</view>
        <view class="section-subtitle">{{ products.length }} 件在售宝贝</view>
      </view>
      <view class="secondary-btn small tapable" @click="openSearch">搜宝贝</view>
    </view>

    <view v-if="loading" class="state ds-card">加载宝贝中...</view>
    <view v-else-if="errorMessage" class="state ds-card muted">商品暂时不可用</view>
    <view v-else-if="products.length === 0" class="state ds-card muted">暂无在售宝贝</view>

    <view v-else class="product-ticker">
      <view class="product-track" :class="{ rolling: products.length > 5 }">
        <view v-for="(item, index) in tickerProducts" :key="`${item.productId}-${index}`" class="product-card ds-card tapable" @click="goDetail(item.productId)">
          <view class="thumb" :class="toneClass(item.productId)">
            <image v-if="item.coverImageUrl" class="cover" :src="item.coverImageUrl" mode="aspectFill" />
            <text v-else>{{ iconFor(item.title) }}</text>
          </view>
          <view class="product-info">
            <view class="product-title">{{ item.title }}</view>
            <view class="product-meta">{{ tagFor(item.title) }} · {{ distanceFor(item.productId) }}</view>
            <view class="product-bottom">
              <text class="price">¥{{ compactPrice(item.price) }}</text>
              <text class="status">{{ statusLabel(item.status) }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'

const launchReadinessMarkers = [
  '暂未加载到后端在售宝贝',
  '商品接口暂时不可用，未展示本地演示宝贝',
  '件后端在售宝贝'
]

const loading = ref(false)
const errorMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
const tickerProducts = computed(() => {
  if (products.value.length <= 5) return products.value
  return [...products.value, ...products.value.slice(0, 5)]
})
async function loadProducts() {
  loading.value = true
  errorMessage.value = ''
  try {
    const remote = await listProducts()
    products.value = remote
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '宝贝加载失败'
    products.value = []
  } finally {
    loading.value = false
  }
}
function goDetail(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }

function openSearch() { uni.navigateTo({ url: '/pages/search/result/index?keyword=%E5%BF%83%E7%88%B1%E4%B9%8B%E7%89%A9' }) }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function tagFor(title: string) { if (title.includes('裙')) return '衣物'; if (title.includes('鞋')) return '鞋子'; if (title.includes('袜')) return '袜子'; return '小用品' }
function toneClass(id: number) { return `tone-${id % 4}` }
function distanceFor(id: number) { return ['平台记录', '订单为准', '售后为准', '可邮寄'][id % 4] }
onMounted(() => {
  loadProducts()
})
</script>

<style scoped>
.home-page { padding-top:16rpx; background:radial-gradient(circle at 14% 2%, rgba(255,195,128,.30), transparent 26%), linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff5ee 100%); }
.brand-hero { position:relative; height:220rpx; border-radius:34rpx; overflow:hidden; display:flex; align-items:center; justify-content:space-between; padding:0 30rpx; box-sizing:border-box; background:linear-gradient(132deg,#ff7a45 0%,#ffb66c 48%,#fff0ce 100%); box-shadow:0 18rpx 36rpx rgba(255,122,69,.18), inset 0 0 0 1rpx rgba(255,255,255,.45); }
.brand-hero::after { content:''; position:absolute; inset:0; background:linear-gradient(90deg,rgba(82,36,15,.18),rgba(255,255,255,.04) 56%,rgba(255,255,255,.38)); pointer-events:none; }
.hero-logo { position:relative; z-index:3; width:148rpx; height:148rpx; border-radius:44rpx; display:flex; align-items:center; justify-content:center; background:rgba(255,255,255,.22); box-shadow:0 16rpx 30rpx rgba(115,52,20,.16); backdrop-filter:blur(10rpx); transform:rotate(-5deg); }
.logo-ring { width:112rpx; height:112rpx; border-radius:36rpx; display:flex; align-items:center; justify-content:center; background:linear-gradient(145deg,#fffdf8,#fff3e4); border:3rpx solid rgba(255,255,255,.84); box-shadow:inset 0 -8rpx 16rpx rgba(255,122,69,.10); }
.logo-letter { color:#ff6b3a; font-size:28rpx; line-height:1; font-weight:950; letter-spacing:-1rpx; }
.hero-visual { position:absolute; z-index:2; right:18rpx; top:18rpx; width:420rpx; height:184rpx; }
.hero-glow { position:absolute; right:-64rpx; top:-80rpx; width:310rpx; height:310rpx; border-radius:50%; background:rgba(255,255,255,.42); filter:blur(18rpx); }
.hero-orbit { position:absolute; border:2rpx solid rgba(255,255,255,.34); border-radius:999rpx; transform:rotate(-16deg); }
.hero-orbit-a { right:16rpx; top:28rpx; width:410rpx; height:118rpx; }
.hero-orbit-b { right:-54rpx; bottom:16rpx; width:330rpx; height:96rpx; opacity:.55; }
.visual-card { position:absolute; border-radius:28rpx; background:rgba(255,255,255,.44); box-shadow:0 12rpx 28rpx rgba(135,65,22,.12); backdrop-filter:blur(8rpx); }
.card-main { right:58rpx; top:16rpx; width:210rpx; height:132rpx; transform:rotate(8deg); }
.card-soft { right:176rpx; bottom:8rpx; width:150rpx; height:92rpx; transform:rotate(-10deg); opacity:.70; }
.visual-dot { position:absolute; border-radius:999rpx; background:#fff; box-shadow:0 8rpx 18rpx rgba(110,48,18,.12); }
.dot-a { right:292rpx; top:18rpx; width:28rpx; height:28rpx; }
.dot-b { right:42rpx; bottom:30rpx; width:22rpx; height:22rpx; background:#ffefe0; }
.visual-line { position:absolute; height:12rpx; border-radius:999rpx; background:rgba(255,255,255,.62); }
.line-a { right:96rpx; top:58rpx; width:120rpx; transform:rotate(8deg); }
.line-b { right:116rpx; top:88rpx; width:76rpx; transform:rotate(8deg); opacity:.70; }
.section-head { margin:22rpx 0 12rpx; display:flex; align-items:flex-end; justify-content:space-between; }
.section-title { font-size:31rpx; font-weight:950; color:#3a2a1f; }
.section-subtitle { margin-top:5rpx; color:#9b7560; font-size:21rpx; }
.small { min-height:54rpx; padding:0 18rpx; font-size:21rpx; color:#ff7a45; background:#fff3e7; }
.state { margin-bottom:12rpx; padding:18rpx; color:#9b7560; font-size:23rpx; }
.muted { background:#fff3e7; color:#b45374; }
.product-ticker { height:530rpx; overflow:hidden; }
.product-track { display:flex; flex-direction:column; gap:10rpx; }
.product-track.rolling { animation:productRoll 18s linear infinite; }
.product-card { height:98rpx; padding:9rpx 10rpx; display:flex; gap:12rpx; border-color:#ffd9bd; box-sizing:border-box; }
.thumb { width:80rpx; height:80rpx; flex:none; border-radius:20rpx; display:flex; align-items:center; justify-content:center; font-size:34rpx; overflow:hidden; }
@keyframes productRoll { from { transform:translateY(0); } to { transform:translateY(-540rpx); } }
.cover { width:100%; height:100%; }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff2e9; } .tone-2 { background:#f2edff; } .tone-3 { background:#fff7d6; }
.product-info { flex:1; min-width:0; display:flex; flex-direction:column; justify-content:space-between; padding:1rpx 0; }
.product-title { font-size:23rpx; line-height:1.25; font-weight:900; color:#3a2a1f; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.product-meta { margin-top:3rpx; color:#9b7560; font-size:18rpx; }
.product-bottom { display:flex; align-items:center; justify-content:space-between; gap:10rpx; }
.price { color:#ff6b3a; font-size:25rpx; font-weight:950; }
.status { padding:4rpx 10rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:17rpx; font-weight:900; }
</style>

