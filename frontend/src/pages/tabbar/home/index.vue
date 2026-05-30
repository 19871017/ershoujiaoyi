<template>
  <view class="page-shell home-page">
    <swiper v-if="visibleBanners.length" class="banner-swiper" circular autoplay :interval="3600" :duration="520" indicator-dots indicator-color="rgba(255,255,255,.55)" indicator-active-color="#ffffff">
      <swiper-item v-for="item in visibleBanners" :key="item.id">
        <view class="banner-card tapable" @click="handleBanner(item.action)">
          <image class="banner-bg" :src="item.imageUrl" mode="aspectFill" />
          <view class="banner-shade"></view>
          <view class="banner-copy">
            <view class="banner-title">{{ item.title }}</view>
          </view>
        </view>
      </swiper-item>
    </swiper>
    <view v-else class="banner-empty ds-card">
      <view class="banner-empty-title">{{ bannerLoadError ? '首页轮播暂时不可用' : '首页轮播待后台配置' }}</view>
      <view class="banner-empty-desc">首页图片以后台轮播配置为准，未获取到服务端配置时不展示本地兜底图。</view>
    </view>

    <view class="ranking-entrance">
      <view v-for="card in rankingCards" :key="card.tab" class="ranking-card tapable" :class="card.themeClass" @click="openRanking(card.tab)">
        <view v-if="card.artwork" class="ranking-art" :style="{ backgroundImage: `url(${card.artwork})` }"></view>
        <view class="ranking-text-mask"></view>
        <view class="ranking-copy">
          <view class="ranking-title">{{ card.title }}</view>
        </view>
      </view>
    </view>

    <view class="section-head">
      <view class="section-title">今日上新 · {{ products.length }} 件后端在售宝贝</view>
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
import { getHomeBanners, type HomeBannerResponse } from '../../../api/modules/home'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'
import {
  CARD_HEIGHT_RPX,
  MANUAL_SCROLL_RESUME_DELAY,
  MIN_SIMULATED_PRODUCTS,
  PRODUCT_COLUMNS,
  ROW_GAP_RPX,
  VISIBLE_ROWS,
  compactPrice,
  formatPublishTime,
  iconFor,
  launchReadinessMarkers,
  rankingCards,
  statusLabel,
  toneClass,
  type BannerAction
} from './home-data'

const banners = ref<HomeBannerResponse[]>([])
const bannerLoadError = ref(false)
const visibleBanners = computed(() => banners.value.filter(item => !!item.imageUrl))
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
  bannerLoadError.value = false
  try {
    banners.value = await getHomeBanners()
  } catch (error) {
    banners.value = []
    bannerLoadError.value = true
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

<style scoped lang="scss" src="./style.scss"></style>
