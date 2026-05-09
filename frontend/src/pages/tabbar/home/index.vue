<template>
  <view class="page-shell home-page">
    <view class="header">
      <view>
        <view class="eyebrow tapable" @click="requestLocation">{{ locationLabel }}</view>
        <view class="title">小原圈</view>
        <view class="subtitle">同城好物、上新发布、圈内互动都在一个圈里</view>
      </view>
      <view class="avatar tapable" @click="goMe">♡</view>
    </view>

    <view class="search ds-card tapable" @click="openSearch">
      <text class="search-icon">🔎</text>
      <text class="search-text">搜连衣裙、鞋子、袜子、包包</text>
      <text class="search-action">找宝贝</text>
    </view>

    <view class="hero ds-card">
      <view class="ring-mark">原</view>
      <view class="hero-main">
        <view class="hero-tag">小原圈 · 今日精选</view>
        <view class="hero-title">进小原圈，附近好物一眼能逛到</view>
        <view class="hero-desc">围绕衣物、鞋袜、小用品，支持圈内互动；交易状态以服务端订单、支付和售后记录为准。</view>
        <view class="hero-tags"><text>圈内互动</text><text>订单记录</text><text>女生好物</text></view>
      </view>
      <view class="hero-side tapable" @click="goCloset">
        <view class="hero-num">小</view>
        <view class="hero-label">进圈子</view>
      </view>
    </view>

    <view class="quick-grid">
      <view v-for="item in quickActions" :key="item.title" class="quick-card ds-card tapable" @click="handleQuick(item.action)">
        <text class="quick-icon">{{ item.icon }}</text>
        <text class="quick-title">{{ item.title }}</text>
      </view>
    </view>

    <view class="closet-strip ds-card tapable" @click="goCloset">
      <view class="closet-logo">👗</view>
      <view class="closet-body">
        <view class="closet-title">我的小原圈</view>
        <view class="closet-desc">衣物、鞋袜、小用品都挂在这里，首页直接进。</view>
      </view>
      <view class="closet-go">进入</view>
    </view>

    <view class="community-card ds-card">
      <view class="community-head">
        <view>
          <view class="section-title">小原圈热度</view>
          <view class="section-subtitle">男神榜、女神榜和圈内动态都在这里</view>
        </view>
        <view class="forum-chip tapable" @click="openForum">进圈逛逛</view>
      </view>
      <view class="rank-row">
        <view v-for="item in ranking" :key="item.title" class="rank-card tapable" @click="openRanking(item.tab)" :class="item.tone">
          <view class="rank-logo">{{ item.logo }}</view>
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
      <view class="secondary-btn small tapable" @click="goCloset">筛选</view>
    </view>

    <view v-if="loading" class="state ds-card">加载宝贝中...</view>
    <view v-else-if="errorMessage" class="state ds-card muted">商品接口暂时不可用，未展示本地演示宝贝</view>
    <view v-else-if="products.length === 0" class="state ds-card muted">暂未加载到后端在售宝贝</view>

    <view class="product-list">
      <view v-for="item in products" :key="item.productId" class="product-card ds-card tapable" @click="goDetail(item.productId)">
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
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'
import { getLocationConfig } from '../../../api/modules/location'

type QuickAction = 'category' | 'wallet' | 'message' | 'publish' | 'ranking' | 'forum' | 'closet'

const quickActions: Array<{ icon: string; title: string; action: QuickAction }> = [
  { icon: '👗', title: '小原圈', action: 'closet' },
  { icon: '👚', title: '衣物', action: 'category' },
  { icon: '👠', title: '鞋袜', action: 'category' },
  { icon: '👜', title: '小用品', action: 'category' },
  { icon: '👑', title: '女神榜', action: 'ranking' },
  { icon: '⭐', title: '男神榜', action: 'ranking' }
]

const ranking: Array<{ logo: string; title: string; name: string; score: string; tone: string; tab: 'goddess' | 'god' }> = [
  { logo: '👑', title: '女神榜', name: '小原圈软糖', score: '本周 128 人喜欢', tone: 'pink', tab: 'goddess' },
  { logo: '⭐', title: '男神榜', name: '温柔收纳家', score: '本周 96 人喜欢', tone: 'blue', tab: 'god' }
]

const forumTopics = [
  { icon: '🎀', title: '穿搭交流', count: '2.1k', id: 1 },
  { icon: '🧺', title: '闲置避坑', count: '896', id: 2 },
  { icon: '💗', title: '圈内经验', count: '518', id: 3 }
]

const loading = ref(false)
const errorMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
const locationLabel = ref('请选择城市 · 手动选择')

async function loadLocationConfig() {
  try {
    const config = await getLocationConfig()
    const city = config.defaultCity || '请选择城市'
    locationLabel.value = `${city} · 手动选择`
  } catch (error) {
    locationLabel.value = '请选择城市 · 手动选择'
  }
}

async function requestLocation() {
  uni.navigateTo({ url: '/pages/location/city/index' })
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
function goMe() { uni.switchTab({ url: '/pages/tabbar/me/index' }) }
function goDetail(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }
function handleQuick(action: QuickAction) {
  if (action === 'closet') goCloset()
  if (action === 'category') uni.switchTab({ url: '/pages/tabbar/category/index' })
  if (action === 'message') uni.switchTab({ url: '/pages/tabbar/message/index' })
  if (action === 'publish') uni.switchTab({ url: '/pages/tabbar/publish/index' })
  if (action === 'ranking') openRanking('goddess')
  if (action === 'forum') openForum()
  if (action === 'wallet') uni.navigateTo({ url: '/pages/wallet/index' })
}
function goCloset() { uni.switchTab({ url: '/pages/tabbar/category/index' }) }
function openSearch() { uni.navigateTo({ url: '/pages/search/result/index?keyword=%E8%BF%9E%E8%A1%A3%E8%A3%99' }) }
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
  loadLocationConfig()
  loadProducts()
})
</script>

<style scoped>
.home-page { padding-top: 26rpx; background:radial-gradient(circle at 14% 2%, rgba(255,195,128,.38), transparent 28%), linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff5ee 100%); }
.header { display:flex; align-items:center; justify-content:space-between; gap:24rpx; }
.eyebrow { display:inline-flex; padding:9rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; border:1rpx solid #ffd9bd; font-size:22rpx; font-weight:900; }
.title { margin-top:14rpx; font-size:56rpx; line-height:1.02; font-weight:950; letter-spacing:-1.2rpx; color:#3a2a1f; }
.subtitle { margin-top:12rpx; color:#9b7560; font-size:24rpx; font-weight:700; }
.avatar { width:78rpx; height:78rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffc08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; font-weight:900; box-shadow:0 12rpx 26rpx rgba(255,122,69,.28); }
.search { margin-top:24rpx; min-height:92rpx; padding:0 18rpx 0 24rpx; display:flex; align-items:center; gap:14rpx; border-color:#ffd9bd; }
.search-icon { color:#ff7a45; font-size:30rpx; }
.search-text { flex:1; min-width:0; color:#9b7560; font-size:26rpx; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.search-action { padding:15rpx 22rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:24rpx; font-weight:900; }
.hero { position:relative; margin-top:22rpx; padding:30rpx; display:flex; align-items:center; gap:24rpx; overflow:hidden; background:linear-gradient(135deg,#fff 0%,#fff1df 56%,#ffe1c2 100%); border-color:#ffd9bd; }
.ring-mark { position:absolute; right:116rpx; top:-20rpx; width:150rpx; height:150rpx; border-radius:50%; border:18rpx solid rgba(255,122,69,.12); color:rgba(255,122,69,.13); display:flex; align-items:center; justify-content:center; font-size:66rpx; font-weight:950; }
.hero-main { position:relative; z-index:1; flex:1; min-width:0; }
.hero-tag { color:#ff7a45; font-size:22rpx; font-weight:950; }
.hero-title { margin-top:10rpx; font-size:36rpx; line-height:1.23; font-weight:950; color:#3a2a1f; }
.hero-desc { margin-top:10rpx; color:#9b7560; font-size:24rpx; line-height:1.5; }
.hero-tags { margin-top:14rpx; display:flex; flex-wrap:wrap; gap:8rpx; }
.hero-tags text { padding:7rpx 12rpx; border-radius:999rpx; background:rgba(255,255,255,.72); color:#b35d2f; font-size:19rpx; font-weight:900; }
.hero-side { position:relative; z-index:1; width:132rpx; height:132rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffc08a); color:#fff; display:flex; flex-direction:column; align-items:center; justify-content:center; box-shadow:0 14rpx 30rpx rgba(255,122,69,.25); }
.hero-num { font-size:46rpx; font-weight:950; }
.hero-label { margin-top:2rpx; font-size:21rpx; color:rgba(255,255,255,.9); font-weight:850; }
.quick-grid { margin-top:22rpx; display:grid; grid-template-columns:repeat(6, 1fr); gap:10rpx; }
.quick-card { min-height:104rpx; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:7rpx; border-color:#ffd9bd; background:#fff; border-radius:26rpx; }
.quick-icon { font-size:31rpx; }
.quick-title { font-size:19rpx; color:#7b5542; font-weight:900; }
.closet-strip { margin-top:18rpx; padding:18rpx; display:flex; align-items:center; gap:16rpx; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.closet-logo { width:66rpx; height:66rpx; border-radius:24rpx; display:flex; align-items:center; justify-content:center; background:#fff; font-size:34rpx; box-shadow:0 8rpx 18rpx rgba(255,122,69,.12); }
.closet-body { flex:1; min-width:0; }
.closet-title { font-size:27rpx; font-weight:950; color:#3a2a1f; }
.closet-desc { margin-top:5rpx; color:#9b7560; font-size:21rpx; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.closet-go { padding:10rpx 18rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:21rpx; font-weight:950; }
.community-card { margin-top:22rpx; padding:22rpx; border-color:#ffd9bd; background:linear-gradient(180deg,#fff,#fffaf6); }
.community-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }
.forum-chip { flex:none; padding:12rpx 18rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:22rpx; font-weight:950; box-shadow:0 8rpx 18rpx rgba(255,122,69,.2); }
.rank-row { margin-top:18rpx; display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:14rpx; }
.rank-card { min-height:118rpx; padding:16rpx; border-radius:30rpx; display:flex; align-items:center; gap:14rpx; border:1rpx solid #ffd9bd; }
.rank-card.pink { background:linear-gradient(135deg,#fff3e7,#ffe5f0); }
.rank-card.blue { background:linear-gradient(135deg,#f2edff,#eef7ff); }
.rank-logo { width:58rpx; height:58rpx; border-radius:22rpx; background:#fff; display:flex; align-items:center; justify-content:center; font-size:31rpx; box-shadow:0 8rpx 18rpx rgba(255,122,69,.12); }
.rank-info { flex:1; min-width:0; }
.rank-title { font-size:24rpx; font-weight:950; color:#3a2a1f; }
.rank-name { margin-top:5rpx; font-size:21rpx; color:#7b5542; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.rank-score { margin-top:4rpx; font-size:19rpx; color:#b9856a; }
.forum-list { margin-top:16rpx; display:flex; gap:10rpx; overflow:hidden; }
.forum-item { flex:1; min-width:0; min-height:72rpx; padding:10rpx 8rpx; border-radius:24rpx; background:#fff3e7; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3rpx; }
.forum-icon { font-size:24rpx; }
.forum-title { font-size:20rpx; color:#7b5542; font-weight:900; }
.forum-count { font-size:18rpx; color:#b9856a; }
.section-head { margin:30rpx 0 16rpx; display:flex; align-items:flex-end; justify-content:space-between; }
.section-title { font-size:36rpx; font-weight:950; color:#3a2a1f; }
.section-subtitle { margin-top:6rpx; color:#9b7560; font-size:23rpx; }
.small { min-height:60rpx; padding:0 22rpx; font-size:23rpx; color:#ff7a45; background:#fff3e7; }
.state { margin-bottom:16rpx; padding:22rpx; color:#9b7560; font-size:24rpx; }
.muted { background:#fff3e7; color:#b45374; }
.product-list { display:flex; flex-direction:column; gap:16rpx; }
.product-card { padding:16rpx; display:flex; gap:18rpx; border-color:#ffd9bd; }
.thumb { width:168rpx; height:168rpx; flex:none; border-radius:34rpx; display:flex; align-items:center; justify-content:center; font-size:58rpx; overflow:hidden; }
.cover { width:100%; height:100%; }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff2e9; } .tone-2 { background:#f2edff; } .tone-3 { background:#fff7d6; }
.product-info { flex:1; min-width:0; display:flex; flex-direction:column; justify-content:space-between; padding:2rpx 0; }
.product-title { font-size:29rpx; line-height:1.35; font-weight:900; color:#3a2a1f; }
.product-meta { margin-top:10rpx; color:#9b7560; font-size:23rpx; }
.product-bottom { display:flex; align-items:center; justify-content:space-between; gap:12rpx; }
.price { color:#ff6b3a; font-size:34rpx; font-weight:950; }
.status { padding:7rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:21rpx; font-weight:900; }
</style>
