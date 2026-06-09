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
      <view class="banner-empty-title">{{ bannerLoadError ? '首页轮播暂时不可用' : '今天先逛逛新上架' }}</view>
      <view class="banner-empty-desc">新鲜宝贝会在这里陆续亮相，先看看下面的在售好物。</view>
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
      <view class="section-title">今日上新 · {{ products.length }} 件在售宝贝</view>
      <view class="secondary-btn small tapable" @click="openSearch">搜宝贝</view>
    </view>

    <view v-if="loading" class="state ds-card">加载宝贝中...</view>
    <view v-else-if="errorMessage" class="state ds-card muted">宝贝暂时不可用，请稍后再逛</view>
    <view v-else-if="products.length === 0" class="state ds-card muted">暂无在售宝贝</view>

    <scroll-view
      v-else
      class="product-roll"
      scroll-y
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
      <view class="product-grid">
        <view
          v-for="item in products"
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
                <view class="seller-avatar-wrap" :class="{ image: !!sellerAvatarUrl(item) }">
                  <image v-if="sellerAvatarUrl(item)" class="seller-avatar-img" :src="sellerAvatarUrl(item)" mode="aspectFill" />
                  <text v-else class="seller-avatar">{{ sellerInitial(item) }}</text>
                </view>
                <text class="seller-name">{{ sellerDisplayName(item) }}</text>
                <text v-if="item.sellerVideoVerified" class="seller-verified">认</text>
              </view>
              <text class="seller-time">{{ formatPublishTime(item.createdAt) }}</text>
            </view>
            <view class="product-grid-bottom">
              <text class="price">¥{{ compactPrice(item.price) }}</text>
            </view>
          </view>
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
  compactPrice,
  formatPublishTime,
  iconFor,
  launchReadinessMarkers,
  rankingCards,
  sellerAvatarUrl,
  sellerDisplayName,
  sellerInitial,
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
const productScrollTop = ref(0)
const isUserInteracting = ref(false)
const isTouchingProducts = ref(false)
const lastManualScrollAt = ref(0)
const MANUAL_SCROLL_RESUME_DELAY = 6000
const PRODUCT_ROLL_INTERVAL = 3200
const PRODUCT_ROLL_STEP = 160
const shouldRollProducts = computed(() => products.value.length > 4)
const switchTabWithCallbacks = uni.switchTab as (options: { url: string; success?: () => void; fail?: () => void }) => void
let productRollTimer: ReturnType<typeof setInterval> | null = null
let resumeRollTimer: ReturnType<typeof setTimeout> | null = null
let autoScrolling = false

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
    if (!shouldRollProducts.value) productScrollTop.value = 0
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '宝贝加载失败'
    products.value = []
  } finally {
    loading.value = false
  }
}
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function goDetail(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }

function productRollDistance(): number {
  const rows = Math.ceil(products.value.length / 2)
  return Math.max(0, (rows - 2) * 360)
}

function rollProducts(): void {
  if (!shouldRollProducts.value || isUserInteracting.value || isTouchingProducts.value) return
  if (Date.now() - lastManualScrollAt.value < MANUAL_SCROLL_RESUME_DELAY) return
  const distance = productRollDistance()
  if (distance <= 0) return
  const nextTop = productScrollTop.value + PRODUCT_ROLL_STEP
  autoScrolling = true
  productScrollTop.value = nextTop >= distance ? 0 : nextTop
  setTimeout(() => { autoScrolling = false }, 180)
}

function scheduleResumeRoll(): void {
  if (resumeRollTimer) clearTimeout(resumeRollTimer)
  resumeRollTimer = setTimeout(() => {
    isUserInteracting.value = false
    isTouchingProducts.value = false
  }, MANUAL_SCROLL_RESUME_DELAY)
}

function handleUserInteract(): void {
  isUserInteracting.value = true
  isTouchingProducts.value = true
  lastManualScrollAt.value = Date.now()
  scheduleResumeRoll()
}

function handleUserInteractEnd(): void {
  isTouchingProducts.value = false
  lastManualScrollAt.value = Date.now()
  scheduleResumeRoll()
}

function handleProductScroll(event: unknown): void {
  if (autoScrolling) return
  const scrollTop = (event as { detail?: { scrollTop?: unknown } }).detail?.scrollTop
  if (typeof scrollTop === 'number' && Number.isFinite(scrollTop)) productScrollTop.value = Math.max(0, scrollTop)
  lastManualScrollAt.value = Date.now()
  scheduleResumeRoll()
}

function startProductRoll(): void {
  if (productRollTimer) return
  productRollTimer = setInterval(() => {
    if (autoScrolling) return
    rollProducts()
  }, PRODUCT_ROLL_INTERVAL)
}

function stopProductRoll(): void {
  if (productRollTimer) clearInterval(productRollTimer)
  if (resumeRollTimer) clearTimeout(resumeRollTimer)
  productRollTimer = null
  resumeRollTimer = null
}

function goCloset() { uni.switchTab({ url: '/pages/tabbar/category/index' }) }
function handleBanner(action: BannerAction) {
  if (action === 'closet') goCloset()
  if (action === 'ranking') openRanking('goddess')
  if (action === 'community' || action === 'forum') openForum()
  if (action === 'search') openSearch()
}
function openSearch() { uni.navigateTo({ url: '/pages/search/result/index?keyword=%E5%BF%83%E7%88%B1%E4%B9%8B%E7%89%A9' }) }
function openRanking(tab: 'goddess' | 'god') { uni.navigateTo({ url: `/pages/ranking/index?tab=${tab}` }) }
function openForum() {
  switchTabWithCallbacks({
    url: '/pages/tabbar/message/index',
    success: () => showToast('已进入社区'),
    fail: () => showToast('暂时无法进入社区')
  })
}
onMounted(() => {
  void loadBanners()
  void loadProducts()
  startProductRoll()
})

onBeforeUnmount(() => {
  stopProductRoll()
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
