<template>
  <view class="page-shell home-page">
    <GlobalTicker />
    <swiper class="banner-swiper" circular autoplay :interval="3600" :duration="520" indicator-dots indicator-color="rgba(255,255,255,.55)" indicator-active-color="#ffffff">
      <swiper-item v-for="item in decoratedBanners" :key="item.id">
        <view class="banner-card tapable" @click="handleBanner(item.action)">
          <image class="banner-bg" :src="item.imageUrl" mode="aspectFill" />
          <view class="banner-shade"></view>
          <view class="banner-copy">
            <view class="banner-title">{{ item.title }}</view>
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
      <view class="section-title">今日小原圈 {{ products.length }} 件后端在售宝贝</view>
      <view class="secondary-btn small tapable" @click="openSearch">搜宝贝</view>
    </view>

    <view v-if="loading" class="state ds-card">加载宝贝中...</view>
    <view v-else-if="errorMessage" class="state ds-card muted">商品暂时不可用</view>
    <view v-else-if="products.length === 0" class="state ds-card muted">暂无在售宝贝</view>

    <scroll-view
      v-else
      scroll-y
      class="product-marquee"
      :scroll-top="productScrollTop"
      @scroll="handleProductScroll"
      @touchstart="handleUserInteract"
      @touchmove="handleUserInteract"
      @touchend="handleUserInteractEnd"
      @touchcancel="handleUserInteractEnd"
      @mousedown="handleUserInteract"
      @mouseup="handleUserInteractEnd"
      @mouseleave="handleUserInteractEnd"
      @wheel="handleUserInteract"
    >
      <view class="product-grid-track">
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
                  <text class="seller-name">小原圈卖家</text>
                </view>
                <text class="seller-time">{{ formatPublishTime(item.createdAt) }}</text>
              </view>
              <view class="product-grid-bottom">
                <text class="price">¥{{ compactPrice(item.price) }}</text>
              </view>
            </view>
          </view>
          <view v-if="row.length < PRODUCT_COLUMNS" class="product-grid-card product-grid-card--ghost"></view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getHomeBanners, type HomeBannerAction, type HomeBannerResponse } from '../../../api/modules/home'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'
import GlobalTicker from '../../../components/GlobalTicker.vue'
import homeBannerCloset from '../../../assets/home/home-banner-closet.png'
import homeBannerRanking from '../../../assets/home/home-banner-ranking.png'
import homeBannerCommunity from '../../../assets/home/home-banner-community.png'
import rankingGoddessArtwork from '../../../assets/ranking/ranking-goddess-desktop.png'
import rankingGodArtwork from '../../../assets/ranking/ranking-god-desktop.png'

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
const homeBannerArtwork: Record<BannerAction, string> = {
  closet: homeBannerCloset,
  ranking: homeBannerRanking,
  forum: homeBannerCommunity,
  search: homeBannerCloset,
  none: homeBannerCommunity
}
const decoratedBanners = computed(() => banners.value.map((item) => ({
  ...item,
  imageUrl: homeBannerArtwork[item.action] || item.imageUrl
})))
const rankingArtwork = {
  goddess: rankingGoddessArtwork,
  god: rankingGodArtwork
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
const MANUAL_SCROLL_RESUME_DELAY = 6000
const loading = ref(false)
const errorMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
const isUserInteracting = ref(false)
const isTouchingProducts = ref(false)
const lastManualScrollAt = ref(0)
const productScrollTop = ref(0)
let resumeRollTimer: ReturnType<typeof setTimeout> | null = null
let productRollTimer: ReturnType<typeof setInterval> | null = null
let autoScrollTimer: ReturnType<typeof setTimeout> | null = null
let autoScrolling = false
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
const productRollDistancePx = computed(() => {
  if (!shouldRollProducts.value) return 0
  const rows = productRows.value.length
  return rpxToPx(rows * CARD_HEIGHT_RPX + Math.max(0, rows - 1) * ROW_GAP_RPX)
})
const productRollStepPx = computed(() => rpxToPx(CARD_HEIGHT_RPX + ROW_GAP_RPX))
function rpxToPx(value: number) {
  return (uni as unknown as { upx2px: (size: number) => number }).upx2px(value)
}
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
    startProductRoll()
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
function scheduleResumeRoll() {
  if (resumeRollTimer) clearTimeout(resumeRollTimer)
  resumeRollTimer = setTimeout(() => {
    if (isTouchingProducts.value || Date.now() - lastManualScrollAt.value < MANUAL_SCROLL_RESUME_DELAY) {
      scheduleResumeRoll()
      return
    }
    isUserInteracting.value = false
  }, MANUAL_SCROLL_RESUME_DELAY)
}
function handleUserInteract() {
  if (!shouldRollProducts.value) return
  isTouchingProducts.value = true
  lastManualScrollAt.value = Date.now()
  isUserInteracting.value = true
  scheduleResumeRoll()
}
function handleUserInteractEnd() {
  if (!shouldRollProducts.value) return
  isTouchingProducts.value = false
  lastManualScrollAt.value = Date.now()
  scheduleResumeRoll()
}
function handleProductScroll(event: { detail?: { scrollTop?: number } }) {
  const nextTop = event.detail?.scrollTop
  if (typeof nextTop === 'number') productScrollTop.value = nextTop
  if (autoScrolling) return
  handleUserInteract()
}
function startProductRoll() {
  if (productRollTimer) clearInterval(productRollTimer)
  if (!shouldRollProducts.value) return
  productRollTimer = setInterval(() => {
    if (!shouldRollProducts.value || isUserInteracting.value || isTouchingProducts.value) return
    if (Date.now() - lastManualScrollAt.value < MANUAL_SCROLL_RESUME_DELAY) return
    const distance = productRollDistancePx.value
    if (distance <= 0) return
    const nextTop = productScrollTop.value + productRollStepPx.value
    autoScrolling = true
    productScrollTop.value = nextTop >= distance ? 0 : nextTop
    if (autoScrollTimer) clearTimeout(autoScrollTimer)
    autoScrollTimer = setTimeout(() => { autoScrolling = false }, 80)
  }, 2600)
}
onMounted(() => {
  void loadBanners()
  void loadProducts()
})
onBeforeUnmount(() => {
  if (resumeRollTimer) clearTimeout(resumeRollTimer)
  if (productRollTimer) clearInterval(productRollTimer)
  if (autoScrollTimer) clearTimeout(autoScrollTimer)
})
</script>

<style scoped>
.home-page { padding-top:16rpx; background:radial-gradient(circle at 14% 2%, rgba(255,195,128,.30), transparent 26%), linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff5ee 100%); }
.banner-swiper { height:230rpx; border-radius:34rpx; overflow:hidden; }
.banner-card { position:relative; height:230rpx; padding:24rpx 26rpx; border-radius:34rpx; overflow:hidden; display:flex; align-items:center; justify-content:space-between; box-sizing:border-box; box-shadow:0 16rpx 32rpx rgba(255,122,69,.16); background:linear-gradient(135deg,#ff7a45 0%,#ffb36f 48%,#ffe1b8 100%); }
.banner-bg { position:absolute; inset:0; width:100%; height:100%; }
.banner-shade { position:absolute; inset:0; background:linear-gradient(90deg,rgba(42,24,12,.58) 0%,rgba(42,24,12,.26) 54%,rgba(42,24,12,.06) 100%); }
.banner-copy { position:relative; z-index:2; width:68%; color:#fff; }
.banner-title { font-size:40rpx; line-height:1.13; font-weight:950; letter-spacing:-1rpx; text-shadow:0 5rpx 14rpx rgba(80,35,18,.18); }
.ranking-entrance { margin:18rpx 0 12rpx; display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16rpx; }
.ranking-card { position:relative; height:300rpx; padding:18rpx; border-radius:30rpx; overflow:hidden; box-sizing:border-box; display:flex; flex-direction:column; justify-content:flex-end; box-shadow:0 16rpx 30rpx rgba(80,35,18,.13); border:1rpx solid rgba(255,255,255,.74); isolation:isolate; }
.ranking-goddess { background:linear-gradient(145deg,#ff6f9a 0%,#ff9f5f 50%,#ffd76b 100%); }
.ranking-god { background:linear-gradient(145deg,#111a44 0%,#1d4ed8 54%,#9b5cff 100%); }
.ranking-art { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; }
.ranking-goddess .ranking-art { object-position:center 22%; }
.ranking-god .ranking-art { object-position:center 20%; }
.ranking-text-mask { position:absolute; left:0; right:0; bottom:0; height:45%; z-index:1; pointer-events:none; background:linear-gradient(180deg,rgba(255,246,230,0) 0%,rgba(255,246,230,.58) 62%,rgba(255,246,230,.86) 100%); }
.ranking-god .ranking-text-mask { background:linear-gradient(180deg,rgba(238,245,255,0) 0%,rgba(238,245,255,.50) 62%,rgba(238,245,255,.82) 100%); }
.ranking-title { position:absolute; z-index:3; left:16rpx; bottom:16rpx; max-width:244rpx; padding:10rpx 18rpx; border-radius:999rpx; color:#8a3b16; font-size:31rpx; line-height:1.05; font-weight:950; letter-spacing:1.2rpx; font-family:"STSong","Songti SC","PingFang SC",serif; white-space:nowrap; background:rgba(255,250,236,.88); box-shadow:inset 0 0 0 1rpx rgba(255,255,255,.72); }
.ranking-goddess .ranking-title { color:#a63252; background:rgba(255,246,230,.88); }
.ranking-god .ranking-title { color:#1d4ed8; background:rgba(238,245,255,.86); }
.section-head { margin:22rpx 0 12rpx; display:flex; align-items:center; justify-content:space-between; }
.section-title { font-size:31rpx; font-weight:950; color:#3a2a1f; }
.small { min-height:54rpx; padding:0 18rpx; font-size:21rpx; color:#ff7a45; background:#fff3e7; }
.state { margin-bottom:12rpx; padding:18rpx; color:#9b7560; font-size:23rpx; }
.muted { background:#fff3e7; color:#b45374; }
.product-marquee { height:1016rpx; overflow:hidden; }
.product-grid-track { display:flex; flex-direction:column; gap:16rpx; }
.product-row { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16rpx; }
.product-grid-card { padding:12rpx; border-color:#ffd9bd; box-sizing:border-box; border-radius:28rpx; background:linear-gradient(180deg,rgba(255,255,255,.98) 0%,rgba(255,246,238,.98) 100%); box-shadow:0 18rpx 28rpx rgba(255,140,84,.10); }
.product-grid-card--ghost { visibility:hidden; pointer-events:none; }
.product-cover-wrap { position:relative; width:100%; height:196rpx; border-radius:22rpx; overflow:hidden; display:flex; align-items:center; justify-content:center; }
.product-cover { width:100%; height:100%; }
.product-cover-fallback { width:100%; height:100%; display:flex; align-items:center; justify-content:center; font-size:68rpx; }
.product-status-chip { position:absolute; right:12rpx; bottom:12rpx; padding:6rpx 14rpx; border-radius:999rpx; background:rgba(255,255,255,.94); color:#ff7a45; font-size:18rpx; font-weight:900; box-shadow:0 6rpx 16rpx rgba(80,35,18,.10); }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff2e9; } .tone-2 { background:#f2edff; } .tone-3 { background:#fff7d6; }
.product-grid-info { display:flex; flex-direction:column; gap:10rpx; padding:12rpx 4rpx 2rpx; }
.product-grid-title { min-height:64rpx; font-size:24rpx; line-height:1.34; font-weight:900; color:#3a2a1f; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.product-grid-seller { display:flex; align-items:center; justify-content:space-between; gap:10rpx; }
.seller-badge { display:inline-flex; align-items:center; gap:10rpx; min-width:0; padding:8rpx 12rpx 8rpx 8rpx; border-radius:999rpx; background:linear-gradient(135deg,rgba(255,243,231,.96) 0%,rgba(255,234,220,.92) 100%); box-shadow:inset 0 0 0 1rpx rgba(255,165,120,.30), 0 8rpx 18rpx rgba(255,138,83,.12); }
.seller-avatar-wrap { width:34rpx; height:34rpx; border-radius:50%; background:linear-gradient(135deg,#ff8e5e 0%,#ffb27d 100%); display:flex; align-items:center; justify-content:center; flex:0 0 auto; box-shadow:0 6rpx 12rpx rgba(255,122,69,.18); }
.seller-avatar { color:#fff; font-size:18rpx; font-weight:950; }
.seller-name { max-width:140rpx; color:#7a4e31; font-size:19rpx; font-weight:900; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.seller-time { flex:0 0 auto; color:#9b7560; font-size:19rpx; font-weight:700; }
.product-grid-bottom { display:flex; align-items:flex-end; justify-content:space-between; gap:12rpx; }
.price { color:#ff7a45; font-size:30rpx; font-weight:950; }
</style>

