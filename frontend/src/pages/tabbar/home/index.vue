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

    <view class="ranking-entrance">
      <view v-for="card in rankingCards" :key="card.tab" class="ranking-card tapable" :class="card.themeClass" @click="openRanking(card.tab)">
        <image v-if="card.artwork" class="ranking-art" :src="card.artwork" mode="aspectFill" />
        <view class="ranking-text-mask"></view>
        <view class="ranking-title">{{ card.title }}</view>
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

    <view v-else class="product-marquee">
      <view class="product-grid-track" :class="{ rolling: shouldRollProducts }" :style="trackStyle">
        <view v-for="(row, rowIndex) in rollingRows" :key="`row-${rowIndex}`" class="product-row">
          <view
            v-for="item in row"
            :key="item.productId"
            class="product-grid-card ds-card tapable"
            @click="goDetail(item.productId)"
          >
            <view class="product-cover-wrap" :class="toneClass(item.productId)">
              <image v-if="item.coverImageUrl" class="product-cover" :src="item.coverImageUrl" mode="aspectFill" />
              <view v-else class="product-cover-fallback">{{ iconFor(item.title) }}</view>
              <view class="product-status-chip">{{ statusLabel(item.status) }}</view>
            </view>

            <view class="product-grid-info">
              <view class="product-grid-title">{{ item.title }}</view>
              <view class="product-grid-seller">
                <view class="seller-badge">
                  <view class="seller-avatar-wrap">
                    <text class="seller-avatar">卖</text>
                  </view>
                  <view class="seller-copy">
                    <text class="seller-name">小原圈卖家</text>
                    <text class="seller-tag">平台认证交易中</text>
                  </view>
                </view>
                <text class="seller-time">{{ formatPublishTime(item.createdAt) }}</text>
              </view>
              <view class="product-grid-bottom">
                <text class="price">¥{{ compactPrice(item.price) }}</text>
                <text class="product-grid-meta">{{ tagFor(item.title) }}</text>
              </view>
            </view>
          </view>
          <view v-if="row.length < PRODUCT_COLUMNS" class="product-grid-card product-grid-card--ghost"></view>
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
type RankingTab = 'goddess' | 'god'
type RankingCard = {
  tab: RankingTab
  themeClass: string
  artwork: string
  title: string
}

const launchReadinessMarkers = [
  '暂未加载到后端在售宝贝',
  '商品接口暂时不可用，未展示本地演示宝贝',
  '件后端在售宝贝'
]

const banners = ref<HomeBannerResponse[]>([])
const rankingArtwork = {
  goddess: '/assets/ranking/ranking-goddess-card.png',
  god: '/assets/ranking/ranking-god-card.png'
}
const rankingCards: RankingCard[] = [
  {
    tab: 'goddess',
    themeClass: 'ranking-goddess',
    artwork: rankingArtwork.goddess,
    title: '魅力女神榜'
  },
  {
    tab: 'god',
    themeClass: 'ranking-god',
    artwork: rankingArtwork.god,
    title: '霸总男神榜'
  }
]

const PRODUCT_COLUMNS = 2
const VISIBLE_ROWS = 3
const MIN_SIMULATED_PRODUCTS = 20
const CARD_HEIGHT_RPX = 328
const ROW_GAP_RPX = 16
const loading = ref(false)
const errorMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
const displayProducts = computed(() => {
  if (products.value.length === 0) return []
  if (products.value.length >= MIN_SIMULATED_PRODUCTS) return products.value
  const repeated: ProductListItemResponse[] = []
  while (repeated.length < MIN_SIMULATED_PRODUCTS) {
    repeated.push(...products.value)
  }
  return repeated.slice(0, MIN_SIMULATED_PRODUCTS)
})
const productRows = computed(() => {
  const rows: ProductListItemResponse[][] = []
  for (let index = 0; index < displayProducts.value.length; index += PRODUCT_COLUMNS) {
    rows.push(displayProducts.value.slice(index, index + PRODUCT_COLUMNS))
  }
  return rows
})
const shouldRollProducts = computed(() => productRows.value.length > VISIBLE_ROWS)
const duplicateRows = computed(() => {
  if (!shouldRollProducts.value) return []
  return productRows.value.slice(0, VISIBLE_ROWS)
})
const rollingRows = computed(() => {
  if (!shouldRollProducts.value) return productRows.value
  return [...productRows.value, ...duplicateRows.value]
})
const trackStyle = computed(() => {
  if (!shouldRollProducts.value) return {}
  const translateRows = productRows.value.length
  const translateRpx = translateRows * CARD_HEIGHT_RPX + Math.max(0, translateRows - 1) * ROW_GAP_RPX
  return {
    '--product-roll-distance': `-${translateRpx}rpx`,
    '--product-roll-duration': `${Math.max(14, productRows.value.length * 3.6)}s`
  }
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
function handleBanner(action: BannerAction) {
  if (action === 'closet') goCloset()
  if (action === 'ranking') openRanking('goddess')
  if (action === 'forum') openForum()
  if (action === 'search') openSearch()
}
function openSearch() { uni.navigateTo({ url: '/pages/search/result/index?keyword=%E5%BF%83%E7%88%B1%E4%B9%8B%E7%89%A9' }) }
function openRanking(tab: 'goddess' | 'god') { uni.navigateTo({ url: `/pages/ranking/index?tab=${tab}` }) }
function openForum() { uni.switchTab({ url: '/pages/tabbar/message/index' }); showToast('已进入') }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function tagFor(title: string) { if (title.includes('裙')) return '衣物'; if (title.includes('鞋')) return '鞋履'; if (title.includes('袜')) return '袜品'; return '闲置好物' }
function toneClass(id: number) { return `tone-${id % 4}` }
function formatPublishTime(createdAt: string) {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return '刚刚上新'
  const diffHours = Math.max(0, (Date.now() - date.getTime()) / (1000 * 60 * 60))
  if (diffHours < 1) return '刚刚上新'
  if (diffHours < 24) return `${Math.floor(diffHours)} 小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays} 天前`
  return `${date.getMonth() + 1}/${date.getDate()} 上新`
}
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
.ranking-entrance { margin:18rpx 0 12rpx; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16rpx; }
.ranking-card { position:relative; min-height:218rpx; padding:16rpx; border-radius:30rpx; overflow:hidden; box-sizing:border-box; display:flex; flex-direction:column; justify-content:space-between; box-shadow:0 16rpx 30rpx rgba(80,35,18,.13); border:1rpx solid rgba(255,255,255,.74); isolation:isolate; }
.ranking-goddess { background:linear-gradient(145deg,#ff6f9a 0%,#ff9f5f 50%,#ffd76b 100%); }
.ranking-god { background:linear-gradient(145deg,#111a44 0%,#1d4ed8 54%,#9b5cff 100%); }
.ranking-art { position:absolute; inset:0; width:100%; height:100%; }
.ranking-text-mask { position:absolute; left:0; top:0; width:72%; height:52%; z-index:1; pointer-events:none; background:linear-gradient(135deg,rgba(45,21,12,.42) 0%,rgba(45,21,12,.20) 58%,rgba(45,21,12,0) 100%); }
.ranking-god .ranking-text-mask { background:linear-gradient(135deg,rgba(4,12,36,.44) 0%,rgba(4,12,36,.20) 58%,rgba(4,12,36,0) 100%); }
.ranking-title { position:absolute; z-index:3; left:18rpx; top:16rpx; max-width:210rpx; color:#fff8d8; font-size:33rpx; line-height:1.05; font-weight:950; letter-spacing:1.6rpx; font-family:"STSong","Songti SC","PingFang SC",serif; white-space:nowrap; text-shadow:0 2rpx 0 rgba(120,54,12,.72),0 0 10rpx rgba(255,224,136,.86),0 8rpx 18rpx rgba(0,0,0,.45); }
.ranking-goddess .ranking-title { color:#fff2c3; text-shadow:0 2rpx 0 rgba(143,52,64,.72),0 0 12rpx rgba(255,210,128,.90),0 8rpx 18rpx rgba(90,28,34,.42); }
.ranking-god .ranking-title { color:#fff1b8; text-shadow:0 2rpx 0 rgba(31,60,132,.76),0 0 12rpx rgba(191,219,254,.90),0 8rpx 18rpx rgba(2,8,23,.48); }
.section-head { margin:22rpx 0 12rpx; display:flex; align-items:flex-end; justify-content:space-between; }
.section-title { font-size:31rpx; font-weight:950; color:#3a2a1f; }
.section-subtitle { margin-top:5rpx; color:#9b7560; font-size:21rpx; }
.small { min-height:54rpx; padding:0 18rpx; font-size:21rpx; color:#ff7a45; background:#fff3e7; }
.state { margin-bottom:12rpx; padding:18rpx; color:#9b7560; font-size:23rpx; }
.muted { background:#fff3e7; color:#b45374; }
.product-marquee { height:1016rpx; overflow:hidden; }
.product-grid-track { display:flex; flex-direction:column; gap:16rpx; will-change:transform; }
.product-grid-track.rolling { animation:productGridRoll var(--product-roll-duration,18s) linear infinite; }
.product-row { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16rpx; }
.product-grid-card { padding:12rpx; border-color:#ffd9bd; box-sizing:border-box; border-radius:28rpx; background:linear-gradient(180deg,rgba(255,255,255,.98) 0%,rgba(255,246,238,.98) 100%); box-shadow:0 18rpx 28rpx rgba(255,140,84,.10); }
.product-grid-card--ghost { visibility:hidden; pointer-events:none; }
.product-cover-wrap { position:relative; width:100%; height:196rpx; border-radius:22rpx; overflow:hidden; display:flex; align-items:center; justify-content:center; }
.product-cover { width:100%; height:100%; }
.product-cover-fallback { width:100%; height:100%; display:flex; align-items:center; justify-content:center; font-size:68rpx; }
.product-status-chip { position:absolute; right:12rpx; bottom:12rpx; padding:6rpx 14rpx; border-radius:999rpx; background:rgba(255,255,255,.94); color:#ff7a45; font-size:18rpx; font-weight:900; box-shadow:0 6rpx 16rpx rgba(80,35,18,.10); }
@keyframes productGridRoll { from { transform:translateY(0); } to { transform:translateY(var(--product-roll-distance,-1032rpx)); } }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff2e9; } .tone-2 { background:#f2edff; } .tone-3 { background:#fff7d6; }
.product-grid-info { display:flex; flex-direction:column; gap:10rpx; padding:12rpx 4rpx 2rpx; }
.product-grid-title { min-height:64rpx; font-size:24rpx; line-height:1.34; font-weight:900; color:#3a2a1f; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.product-grid-seller { display:flex; align-items:center; justify-content:space-between; gap:10rpx; }
.seller-badge { display:inline-flex; align-items:center; gap:10rpx; min-width:0; padding:8rpx 12rpx 8rpx 8rpx; border-radius:999rpx; background:linear-gradient(135deg,rgba(255,243,231,.96) 0%,rgba(255,234,220,.92) 100%); box-shadow:inset 0 0 0 1rpx rgba(255,165,120,.30), 0 8rpx 18rpx rgba(255,138,83,.12); }
.seller-avatar-wrap { width:42rpx; height:42rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#ffd1a6 0%,#ffb280 45%,#ff7b62 100%); box-shadow:0 8rpx 18rpx rgba(255,111,97,.28); }
.seller-avatar { width:34rpx; height:34rpx; border-radius:999rpx; display:inline-flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#ff9a62 0%,#ff6f61 100%); color:#fff; font-size:18rpx; font-weight:950; box-shadow:0 4rpx 10rpx rgba(255,111,97,.24); }
.seller-copy { min-width:0; display:flex; flex-direction:column; gap:2rpx; }
.seller-name { min-width:0; color:#7b4d35; font-size:22rpx; line-height:1.1; font-weight:950; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.seller-tag { color:#c56b47; font-size:16rpx; line-height:1.1; font-weight:800; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.seller-time { flex:none; color:#b08a73; font-size:18rpx; font-weight:700; }
.product-grid-bottom { display:flex; align-items:flex-end; justify-content:space-between; gap:12rpx; }
.price { color:#ff6b3a; font-size:29rpx; font-weight:950; }
.product-grid-meta { color:#9b7560; font-size:19rpx; font-weight:700; }
</style>

