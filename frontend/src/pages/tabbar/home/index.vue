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

    <view class="community-card ds-card">
      <view class="community-head">
        <view class="forum-chip tapable" @click="openForum">进圈逛逛</view>
      </view>
      <view class="rank-row">
        <view v-for="item in ranking" :key="item.title" class="rank-card tapable" @click="openRanking(item.tab)" :class="item.tone">
          <view class="rank-portrait">
            <image class="rank-img" :src="item.image" mode="aspectFill" />
            <text class="rank-crown">{{ item.logo }}</text>
          </view>
          <view class="rank-info">
            <view class="rank-title">{{ item.title }}</view>
            <view class="rank-name">{{ item.name }}</view>
            <view class="rank-score">{{ item.score }}</view>
          </view>
        </view>
      </view>
      <view class="forum-list">
        <view v-for="item in forumTopics" :key="item.title" class="forum-item tapable" @click="openTopic(item)" >
          <text class="forum-icon">{{ item.icon }}</text>
          <text class="forum-title">{{ item.title }}</text>
          <text class="forum-count">{{ item.count }}</text>
        </view>
      </view>
    </view>

    <view class="section-head">
      <view>
        <view class="section-title">今日小原圈</view>
        <view class="section-subtitle">{{ products.length }} 件后端在售宝贝</view>
      </view>
      <view class="secondary-btn small tapable" @click="openSearch">搜宝贝</view>
    </view>

    <view v-if="loading" class="state ds-card">加载宝贝中...</view>
    <view v-else-if="errorMessage" class="state ds-card muted">商品接口暂时不可用，未展示本地演示宝贝</view>
    <view v-else-if="products.length === 0" class="state ds-card muted">暂未加载到后端在售宝贝</view>

    <swiper v-else class="product-ticker" vertical circular autoplay :interval="2600" :duration="650">
      <swiper-item v-for="item in products" :key="item.productId" class="product-slide">
        <view class="product-card ds-card tapable" @click="goDetail(item.productId)">
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
      </swiper-item>
    </swiper>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getHomeBanners, type HomeBannerAction, type HomeBannerResponse } from '../../../api/modules/home'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'

type BannerAction = HomeBannerAction

const banners = ref<HomeBannerResponse[]>([])

const ranking: Array<{ logo: string; title: string; name: string; score: string; tone: string; tab: 'goddess' | 'god'; image: string }> = [
  { logo: '👑', title: '女神榜', name: '樱桃汽水少女', score: '本周 128 人喜欢', tone: 'pink', tab: 'goddess', image: 'https://images.unsplash.com/photo-1614583225154-5fcdda07019e?auto=format&fit=crop&w=420&q=80' },
  { logo: '⭐', title: '男神榜', name: '蓝调漫画少年', score: '本周 96 人喜欢', tone: 'blue', tab: 'god', image: 'https://images.unsplash.com/photo-1620428268482-cf1851a36764?auto=format&fit=crop&w=420&q=80' }
]

const forumTopics = [
  { icon: '🌷', title: '日常生活', count: '2.1k', id: 1 },
  { icon: '🧺', title: '闲置避坑', count: '896', id: 2 },
  { icon: '💗', title: '圈内经验', count: '518', id: 3 }
]

const loading = ref(false)
const errorMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
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
function handleBanner(action: BannerAction) {
  if (action === 'closet') goCloset()
  if (action === 'ranking') openRanking('goddess')
  if (action === 'forum') openForum()
  if (action === 'search') openSearch()
}
function openSearch() { uni.navigateTo({ url: '/pages/search/result/index?keyword=%E5%BF%83%E7%88%B1%E4%B9%8B%E7%89%A9' }) }
function openTopic(item: { id: number; title: string }) { uni.navigateTo({ url: `/pages/community/detail/index?postId=${item.id}&topic=${encodeURIComponent(item.title)}` }) }
function openRanking(tab: 'goddess' | 'god') { uni.navigateTo({ url: `/pages/ranking/index?tab=${tab}` }) }
function openForum() { uni.switchTab({ url: '/pages/tabbar/message/index' }); showToast('已进入社区') }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function tagFor(title: string) { if (title.includes('裙')) return '衣物'; if (title.includes('鞋')) return '鞋子'; if (title.includes('袜')) return '袜子'; return '小用品' }
function toneClass(id: number) { return `tone-${id % 4}` }
function distanceFor(id: number) { return ['服务端记录', '订单为准', '售后为准', '可邮寄'][id % 4] }
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
.community-card { margin-top:16rpx; padding:16rpx; border-color:#ffd9bd; background:linear-gradient(180deg,#fff,#fffaf6); }
.community-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12rpx; }
.forum-chip { flex:none; padding:10rpx 16rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:20rpx; font-weight:950; box-shadow:0 6rpx 14rpx rgba(255,122,69,.16); }
.rank-row { margin-top:14rpx; display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:10rpx; }
.rank-card { position:relative; min-height:154rpx; padding:12rpx; border-radius:26rpx; display:flex; align-items:flex-end; gap:12rpx; overflow:hidden; border:1rpx solid rgba(255,217,189,.92); box-shadow:0 10rpx 22rpx rgba(90,50,28,.08); }
.rank-card::after { content:''; position:absolute; inset:0; background:linear-gradient(180deg,rgba(255,255,255,.16),rgba(255,255,255,.82)); pointer-events:none; }
.rank-card.pink { background:linear-gradient(135deg,#fff0f6,#ffc4dd 52%,#fff8e7); }
.rank-card.blue { background:linear-gradient(135deg,#edf5ff,#b8d7ff 54%,#f3ecff); }
.rank-portrait { position:relative; z-index:1; width:86rpx; height:112rpx; flex:none; border-radius:24rpx; overflow:hidden; background:#fff; box-shadow:0 10rpx 18rpx rgba(80,35,18,.14); border:3rpx solid rgba(255,255,255,.84); }
.rank-img { width:100%; height:100%; }
.rank-crown { position:absolute; right:-2rpx; top:-2rpx; width:34rpx; height:34rpx; border-radius:0 20rpx 0 18rpx; background:rgba(255,255,255,.92); display:flex; align-items:center; justify-content:center; font-size:19rpx; }
.rank-info { position:relative; z-index:1; flex:1; min-width:0; padding-bottom:2rpx; }
.rank-title { display:inline-flex; padding:5rpx 12rpx; border-radius:999rpx; background:rgba(255,255,255,.76); font-size:20rpx; font-weight:950; color:#3a2a1f; }
.rank-name { margin-top:8rpx; font-size:23rpx; color:#4a3225; font-weight:950; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.rank-score { margin-top:5rpx; font-size:18rpx; color:#9a6a55; font-weight:800; }
.forum-list { margin-top:12rpx; display:flex; gap:8rpx; overflow:hidden; }
.forum-item { flex:1; min-width:0; min-height:62rpx; padding:8rpx 6rpx; border-radius:20rpx; background:#fff3e7; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2rpx; }
.forum-icon { font-size:22rpx; }
.forum-title { font-size:19rpx; color:#7b5542; font-weight:900; }
.forum-count { font-size:17rpx; color:#b9856a; }
.section-head { margin:22rpx 0 12rpx; display:flex; align-items:flex-end; justify-content:space-between; }
.section-title { font-size:31rpx; font-weight:950; color:#3a2a1f; }
.section-subtitle { margin-top:5rpx; color:#9b7560; font-size:21rpx; }
.small { min-height:54rpx; padding:0 18rpx; font-size:21rpx; color:#ff7a45; background:#fff3e7; }
.state { margin-bottom:12rpx; padding:18rpx; color:#9b7560; font-size:23rpx; }
.muted { background:#fff3e7; color:#b45374; }
.product-ticker { height:162rpx; overflow:hidden; }
.product-slide { height:162rpx; }
.product-card { height:150rpx; padding:12rpx; display:flex; gap:14rpx; border-color:#ffd9bd; box-sizing:border-box; }
.thumb { width:126rpx; height:126rpx; flex:none; border-radius:26rpx; display:flex; align-items:center; justify-content:center; font-size:48rpx; overflow:hidden; }
.cover { width:100%; height:100%; }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff2e9; } .tone-2 { background:#f2edff; } .tone-3 { background:#fff7d6; }
.product-info { flex:1; min-width:0; display:flex; flex-direction:column; justify-content:space-between; padding:1rpx 0; }
.product-title { font-size:27rpx; line-height:1.3; font-weight:900; color:#3a2a1f; }
.product-meta { margin-top:7rpx; color:#9b7560; font-size:21rpx; }
.product-bottom { display:flex; align-items:center; justify-content:space-between; gap:10rpx; }
.price { color:#ff6b3a; font-size:30rpx; font-weight:950; }
.status { padding:6rpx 12rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
</style>

