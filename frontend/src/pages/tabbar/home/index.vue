<template>
  <view class="page-shell home-page">
    <swiper class="banner-swiper" circular autoplay :interval="3600" :duration="520" indicator-dots indicator-color="rgba(255,255,255,.55)" indicator-active-color="#ffffff">
      <swiper-item v-for="item in banners" :key="item.id">
        <view class="banner-card tapable" @click="handleBanner(item.action)">
          <image class="banner-bg" :src="item.imageUrl" mode="aspectFill" />
          <view class="banner-shade"></view>
          <view class="banner-copy">
            <view class="banner-kicker">{{ item.kicker }}</view>
            <view class="banner-title">{{ item.title }}</view>
            <view class="banner-desc">{{ item.description }}</view>
            <view class="banner-cta">{{ item.cta }}</view>
          </view>
        </view>
      </swiper-item>
    </swiper>

    <view class="ranking-entry-row">
      <view class="ranking-entry goddess tapable" @click="openRanking('goddess')">
        <image class="ranking-art" src="/src/static/ranking-goddess.svg" mode="aspectFill" />
        <view class="ranking-overlay"></view>
        <view class="ranking-main">
          <view class="ranking-copy">
            <text class="ranking-title">魅力女神榜</text>
            <text class="ranking-subtitle">CHARM QUEEN</text>
          </view>
        </view>
        <view class="ranking-chip">GO</view>
      </view>
      <view class="ranking-entry god tapable" @click="openRanking('god')">
        <image class="ranking-art" src="/src/static/ranking-god.svg" mode="aspectFill" />
        <view class="ranking-overlay"></view>
        <view class="ranking-main">
          <view class="ranking-copy">
            <text class="ranking-title">锋芒男神榜</text>
            <text class="ranking-subtitle">SHARP KING</text>
          </view>
        </view>
        <view class="ranking-chip">GO</view>
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
import { getHomeBanners, type HomeBannerAction, type HomeBannerResponse } from '../../../api/modules/home'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'

type BannerAction = HomeBannerAction

const launchReadinessMarkers = [
  '暂未加载到后端在售宝贝',
  '商品接口暂时不可用，未展示本地演示宝贝',
  '件后端在售宝贝'
]

const banners = ref<HomeBannerResponse[]>([])

const forumTopics = [
  { icon: '🌷', title: '日常生活', count: '2.1k', id: 1 },
  { icon: '🧺', title: '闲置避坑', count: '896', id: 2 },
  { icon: '💗', title: '圈内经验', count: '518', id: 3 }
]

const loading = ref(false)
const errorMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
const tickerProducts = computed(() => {
  if (products.value.length <= 5) return products.value
  return [...products.value, ...products.value.slice(0, 5)]
})
async function loadBanners() {
  try {
    banners.value = await getHomeBanners()
  } catch (error) {
    banners.value = []
  }
}


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
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function goDetail(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }

function goCloset() { uni.switchTab({ url: '/pages/tabbar/category/index' }) }
function openRanking(tab: 'god' | 'goddess') { uni.navigateTo({ url: `/pages/ranking/index?tab=${tab}` }) }
function handleBanner(action: BannerAction) {
  if (action === 'closet') goCloset()
  if (action === 'ranking') openForum()
  if (action === 'forum') openForum()
  if (action === 'search') openSearch()
}
function openSearch() { uni.navigateTo({ url: '/pages/search/result/index?keyword=%E5%BF%83%E7%88%B1%E4%B9%8B%E7%89%A9' }) }
function openTopic(item: { id: number; title: string }) { uni.navigateTo({ url: `/pages/community/detail/index?postId=${item.id}&topic=${encodeURIComponent(item.title)}` }) }
function openForum() { uni.switchTab({ url: '/pages/tabbar/message/index' }); showToast('已进入') }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function tagFor(title: string) { if (title.includes('裙')) return '衣物'; if (title.includes('鞋')) return '鞋子'; if (title.includes('袜')) return '袜子'; return '小用品' }
function toneClass(id: number) { return `tone-${id % 4}` }
function distanceFor(id: number) { return ['平台记录', '订单为准', '售后为准', '可邮寄'][id % 4] }
onMounted(() => {
  loadBanners()
  loadProducts()
})
</script>

<style scoped>
.home-page { padding-top:16rpx; background:radial-gradient(circle at 14% 2%, rgba(255,195,128,.30), transparent 26%), linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff5ee 100%); }
.banner-swiper { height:230rpx; border-radius:34rpx; overflow:hidden; }
.banner-card { position:relative; height:230rpx; padding:24rpx 26rpx; border-radius:34rpx; overflow:hidden; display:flex; align-items:center; justify-content:space-between; box-sizing:border-box; box-shadow:0 16rpx 32rpx rgba(255,122,69,.16); background:linear-gradient(135deg,#ff7a45 0%,#ffb36f 48%,#ffe1b8 100%); }
.banner-bg { position:absolute; inset:0; width:100%; height:100%; }
.banner-shade { position:absolute; inset:0; background:linear-gradient(90deg,rgba(42,24,12,.58) 0%,rgba(42,24,12,.26) 54%,rgba(42,24,12,.06) 100%); }
.banner-copy { position:relative; z-index:2; width:68%; color:#fff; }
.banner-kicker { display:inline-flex; padding:6rpx 13rpx; border-radius:999rpx; background:rgba(255,255,255,.22); color:rgba(255,255,255,.94); font-size:19rpx; font-weight:950; backdrop-filter:blur(8rpx); }
.banner-title { margin-top:12rpx; font-size:36rpx; line-height:1.13; font-weight:950; letter-spacing:-1rpx; text-shadow:0 5rpx 14rpx rgba(80,35,18,.18); }
.banner-desc { margin-top:8rpx; width:92%; font-size:21rpx; line-height:1.35; font-weight:750; color:rgba(255,255,255,.88); }
.banner-cta { margin-top:12rpx; display:inline-flex; padding:8rpx 18rpx; border-radius:999rpx; background:#fff; color:#ff6b3a; font-size:20rpx; font-weight:950; box-shadow:0 8rpx 18rpx rgba(80,35,18,.14); }
.ranking-entry-row { margin:16rpx 0 12rpx; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14rpx; }
.ranking-entry { position:relative; min-height:138rpx; padding:16rpx 14rpx 14rpx; border-radius:34rpx; overflow:hidden; display:flex; flex-direction:column; align-items:flex-start; justify-content:space-between; color:#fff; box-sizing:border-box; isolation:isolate; box-shadow:0 18rpx 34rpx rgba(255,122,69,.18); }
.ranking-entry::after { content:''; position:absolute; inset:1rpx; z-index:2; border-radius:33rpx; border:1rpx solid rgba(255,255,255,.45); background:linear-gradient(145deg,rgba(255,255,255,.18),rgba(255,255,255,.02) 42%,rgba(255,255,255,.14)); pointer-events:none; }
.ranking-entry.god { box-shadow:0 18rpx 34rpx rgba(76,103,255,.18); }
.ranking-art { position:absolute; inset:0; z-index:0; width:100%; height:100%; }
.ranking-overlay { position:absolute; inset:0; z-index:1; background:linear-gradient(90deg,rgba(36,16,18,.60) 0%,rgba(36,16,18,.18) 54%,rgba(36,16,18,.04) 100%); }
.ranking-entry.god .ranking-overlay { background:linear-gradient(90deg,rgba(7,12,45,.62) 0%,rgba(7,12,45,.16) 54%,rgba(7,12,45,.04) 100%); }
.ranking-main { position:relative; z-index:3; display:flex; align-items:center; gap:11rpx; min-width:0; }
.ranking-copy { display:flex; flex-direction:column; gap:6rpx; min-width:0; }
.ranking-title { font-size:26rpx; line-height:1.08; font-weight:950; letter-spacing:-.2rpx; text-shadow:0 4rpx 12rpx rgba(0,0,0,.28); }
.ranking-subtitle { font-size:13rpx; line-height:1; font-weight:950; letter-spacing:1.2rpx; color:rgba(255,255,255,.82); text-shadow:0 3rpx 8rpx rgba(0,0,0,.22); }
.ranking-chip { position:relative; z-index:3; margin-left:auto; min-width:42rpx; height:24rpx; padding:0 9rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; color:rgba(255,255,255,.92); background:rgba(255,255,255,.20); box-shadow:inset 0 0 0 1rpx rgba(255,255,255,.32), 0 6rpx 14rpx rgba(0,0,0,.10); font-size:13rpx; line-height:24rpx; font-weight:950; letter-spacing:.8rpx; backdrop-filter:blur(6rpx); }
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

