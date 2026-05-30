<template>
  <view class="page-shell detail-page">
    <view v-if="loading" class="ds-card state">加载中...</view>

    <view v-else-if="detail" class="detail-body">
      <view class="hero-card ds-card">
        <view class="hero" :class="toneClass(detail.productId)">
          <image v-if="activeImage" class="hero-img" :src="activeImage" mode="aspectFill" />
          <text v-else>{{ iconFor(detail.title) }}</text>
        </view>
        <view class="thumb-row">
          <view v-for="(img, index) in displayImages" :key="index" class="thumb tapable" :class="{ active: index === activeImageIndex }" @click="activeImageIndex = index">
            <image v-if="img" :src="img" mode="aspectFill" />
            <text v-else>{{ iconFor(detail.title) }}</text>
          </view>
        </view>
      </view>

      <view class="info-card ds-card">
        <view class="title-row">
          <view class="title">{{ detail.title }}</view>
          <view class="favorite tapable" :class="{ active: favorited }" @click="toggleFavorite">{{ favorited ? '♥' : '♡' }}</view>
        </view>
        <view class="price">¥{{ compactPrice(detail.price) }}</view>
        <view class="meta-row">
          <view class="pill">{{ statusText }}</view>
          <view class="pill green">{{ auditText }}</view>
          <view class="pill soft">{{ sellerCity }}</view>
        </view>
        <view class="desc">{{ detail.description || '暂无描述' }}</view>
      </view>

      <view class="seller-card ds-card tapable" @click="openSellerProfile">
        <view class="seller-avatar" :class="{ image: !!sellerAvatarUrl }">
          <image v-if="sellerAvatarUrl" class="seller-avatar-img" :src="sellerAvatarUrl" mode="aspectFill" />
          <text v-else>{{ sellerName.slice(0, 1) }}</text>
        </view>
        <view class="seller-main">
          <view class="seller-name">{{ sellerName }}</view>
          <view class="seller-desc">{{ sellerTrustText }}</view>
          <view class="tag-row">
            <text v-for="tag in sellerTags" :key="tag" class="mini-tag">{{ tag }}</text>
            <text v-if="!sellerTags.length" class="mini-tag muted">认证状态以服务端资料为准</text>
          </view>
        </view>
        <button class="mini-btn" @click.stop="contactSeller">私信</button>
      </view>

      <view class="rule-card ds-card">
        <view class="section-title">交易保障</view>
        <view class="rule-line">{{ detail.tradeRule }}</view>
        <view class="safe-grid">
          <view v-for="item in safeRules" :key="item.title" class="safe-item">
            <view class="safe-icon">{{ item.icon }}</view>
            <view>
              <view class="safe-title">{{ item.title }}</view>
              <view class="safe-desc">{{ item.desc }}</view>
            </view>
          </view>
        </view>
      </view>

      <view class="action-panel ds-card">
        <view class="panel-title">购买前确认</view>
        <view v-for="item in confirmItems" :key="item.key" class="confirm-row tapable" @click="item.checked = !item.checked">
          <view :class="['check', { active: item.checked }]">✓</view>
          <view>{{ item.label }}</view>
        </view>
      </view>

      <view v-if="orderMessage" class="message ds-card">{{ orderMessage }}</view>

      <view class="bottom-actions">
        <button class="icon-btn" @click="shareProduct">分享</button>
        <button class="icon-btn" @click="reportProduct">举报</button>
        <button class="secondary-btn action" @click="contactSeller">私信卖家</button>
        <button class="primary-btn action" :disabled="ordering" @click="createAndPay">{{ ordering ? '处理中...' : '确认订单' }}</button>
      </view>
    </view>

    <view v-else class="ds-card state error">{{ errorMessage || '商品不存在' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { favoriteProduct, getProductDetail, unfavoriteProduct, type ProductDetailResponse } from '../../../api/modules/product'
import { resolveProductSellerContactTarget } from '../../../api/modules/order-contact'
import { getPublicProfile, type UserProfileResponse } from '../../../api/modules/user'
import {
  assertProductDetail,
  assertSellerProfile,
  compactNumber,
  compactPrice,
  communityImageStoragePrefix,
  decodeRouteValue,
  defaultConfirmItems,
  iconFor,
  isFiniteNumber,
  isPositiveIntegerId,
  isValidBackendProductId,
  isValidBackendUserId,
  productImageStoragePrefix,
  safeRules,
  sellerProfileFailedText,
  sellerProfileFallbackText,
  toneClass,
  validatedDisplayMediaUrl,
  videoIdentityStoragePrefix
} from './product-detail-integrity'

const productId = ref<number>(0)
const loading = ref(false)
const ordering = ref(false)
const errorMessage = ref('')
const orderMessage = ref('')
const detail = ref<ProductDetailResponse | null>(null)
const sellerProfile = ref<UserProfileResponse | null>(null)
const sellerProfileLoadFailed = ref(false)
const activeImageIndex = ref(0)
const favorited = ref(false)
const favoriteLoading = ref(false)
const confirmItems = reactive(defaultConfirmItems.map((item) => ({ ...item })))
const displayImages = computed(() => {
  const urls = (detail.value?.imageUrls || []).filter((url) => validatedDisplayMediaUrl(url, productImageStoragePrefix, 'product-image'))
  return urls.length ? urls : ['', '', '']
})
const activeImage = computed(() => displayImages.value[activeImageIndex.value] || '')
const statusText = computed(() => detail.value?.status === 'created' ? '在售' : detail.value?.status || '未知')
const auditText = computed(() => detail.value?.auditState === 'pending' ? '审核中' : detail.value?.auditState || '审核状态')
const sellerName = computed(() => {
  if (sellerProfile.value?.nickname) return sellerProfile.value.nickname
  if (detail.value?.sellerId) return `卖家 ${detail.value.sellerId}`
  return '商品卖家'
})
const sellerCity = computed(() => sellerProfile.value?.city || '卖家城市以服务端资料为准')
const sellerAvatarUrl = computed(() => validatedDisplayMediaUrl(sellerProfile.value?.avatarUrl || '', communityImageStoragePrefix, 'seller-avatar'))
const sellerIsSellerProfile = computed(() => ['SELLER', 'BOTH'].includes((sellerProfile.value?.mainRole || '').toUpperCase()))
const sellerScoreLabel = computed(() => sellerIsSellerProfile.value ? '魅力值' : '实力值')
const sellerScoreValue = computed(() => sellerIsSellerProfile.value ? sellerProfile.value?.sellerCharmScore : sellerProfile.value?.buyerPowerScore)
const sellerTrustText = computed(() => {
  if (sellerProfileLoadFailed.value) return sellerProfileFailedText
  if (!sellerProfile.value || !isFiniteNumber(sellerProfile.value.followerCount) || !isFiniteNumber(sellerScoreValue.value)) return sellerProfileFallbackText
  return `粉丝 ${compactNumber(sellerProfile.value.followerCount)} · ${sellerScoreLabel.value} ${compactNumber(sellerScoreValue.value)}`
})
const sellerHasVerifiedVideo = computed(() => sellerProfile.value?.videoVerified === true && sellerProfile.value.videoIdentityStatus === 'APPROVED' && !!validatedDisplayMediaUrl(sellerProfile.value?.videoIdentityUrl || '', videoIdentityStoragePrefix, 'seller-video'))
const sellerTags = computed<string[]>(() => sellerHasVerifiedVideo.value ? ['视频认证卖家'] : [])
function readProductId(): void {
  const pages = getCurrentPages()
  const current = pages.length > 0 ? (pages[pages.length - 1] as unknown as { options?: Record<string, string> }) : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeProductId = decodeRouteValue('productId', current?.options?.productId ?? hashParams?.get('productId') ?? '')
  productId.value = isValidBackendProductId(routeProductId) ? Number(routeProductId) : 0
}
function resetSellerProfile(): void {
  sellerProfile.value = null
  sellerProfileLoadFailed.value = false
}
async function loadSellerProfile(sellerId: number | null | undefined): Promise<void> {
  resetSellerProfile()
  if (!isValidBackendUserId(sellerId)) return
  try {
    const profile = await getPublicProfile(Number(sellerId))
    assertSellerProfile(profile, Number(sellerId))
    sellerProfile.value = profile
  } catch (error) {
    if (error instanceof Error && error.message === 'product seller profile userId mismatch') {
      console.warn('product seller profile integrity failed', { productId: productId.value, sellerId, error })
      throw error
    }
    console.warn('product seller profile unavailable', { productId: productId.value, sellerId, error })
    sellerProfile.value = null
    sellerProfileLoadFailed.value = true
  }
}
async function loadDetail(): Promise<void> {
  if (!productId.value) { errorMessage.value = '缺少商品ID'; return }
  loading.value = true
  errorMessage.value = ''
  resetSellerProfile()
  try {
    const productDetail = await getProductDetail(productId.value)
    assertProductDetail(productDetail)
    if (productDetail.productId !== productId.value) throw new Error('product detail productId mismatch')
    detail.value = productDetail
    activeImageIndex.value = 0
    favorited.value = productDetail.favoritedByMe === true
    await loadSellerProfile(productDetail.sellerId)
  } catch (error) {
    console.warn('product detail load failed', { productId: productId.value, error })
    errorMessage.value = error instanceof Error ? error.message : '商品详情加载失败，请稍后重试'
    detail.value = null
    favorited.value = false
    resetSellerProfile()
  } finally { loading.value = false }
}
async function createAndPay(): Promise<void> {
  if (!detail.value) return
  const unconfirmed = confirmItems.find((item) => !item.checked)
  if (unconfirmed) { uni.showToast({ title: '请先完成购买前确认', icon: 'none' }); return }
  ordering.value = true
  orderMessage.value = ''
  const route = {
    url: `/pages/order/confirm/index?productId=${encodeURIComponent(String(detail.value.productId))}`,
    fail: (error: unknown) => {
      console.warn('product order confirmation navigation failed', { productId: detail.value?.productId, error })
      orderMessage.value = '确认订单页面打开失败，请稍后重试'
    },
    complete: () => { ordering.value = false }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    ordering.value = false
    console.warn('product order confirmation navigation failed', { productId: detail.value?.productId, error })
    orderMessage.value = '确认订单页面打开失败，请稍后重试'
  }
}
function contactSeller(): void {
  if (!detail.value) return
  const target = resolveProductSellerContactTarget(detail.value)
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  const route = {
    url: `/pages/chat/conversation/index?receiverId=${encodeURIComponent(target.receiverId)}&productId=${encodeURIComponent(String(detail.value.productId))}`,
    fail: (error: unknown) => {
      console.warn('product seller chat navigation failed', { productId: detail.value?.productId, receiverId: target.receiverId, error })
      uni.showToast({ title: '暂时无法打开私信，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('product seller chat navigation failed', { productId: detail.value?.productId, receiverId: target.receiverId, error })
    uni.showToast({ title: '暂时无法打开私信，请稍后重试', icon: 'none' })
  }
}
function openSellerProfile(): void {
  const sellerId = detail.value?.sellerId
  if (!isValidBackendUserId(sellerId)) {
    uni.showToast({ title: '缺少真实卖家ID，未打开主页', icon: 'none' })
    return
  }
  if (!sellerProfile.value) {
    uni.showToast({ title: '卖家资料暂时不可用，未打开主页', icon: 'none' })
    return
  }
  const route = {
    url: `/pages/user/public-profile/index?userId=${encodeURIComponent(String(sellerId))}`,
    fail: (error: unknown) => {
      console.warn('product seller profile navigation failed', { productId: detail.value?.productId, sellerId, error })
      uni.showToast({ title: '暂时无法打开卖家主页，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('product seller profile navigation failed', { productId: detail.value?.productId, sellerId, error })
    uni.showToast({ title: '暂时无法打开卖家主页，请稍后重试', icon: 'none' })
  }
}
function isValidProductReportTargetId(value: unknown): boolean {
  return isPositiveIntegerId(value)
}
function reportProduct(): void {
  const reportTargetId = detail.value?.productId ?? productId.value
  if (!isValidProductReportTargetId(reportTargetId)) {
    uni.showToast({ title: '缺少有效商品编号，不能提交举报', icon: 'none' })
    return
  }
  const route = {
    url: `/pages/report/submit/index?targetType=GOODS&targetId=${encodeURIComponent(String(reportTargetId))}`,
    fail: (error: unknown) => {
      console.warn('product report navigation failed', { productId: reportTargetId, error })
      uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('product report navigation failed', { productId: reportTargetId, error })
    uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
  }
}
function shareProduct(): void { uni.showToast({ title: '分享功能暂时不可用，请稍后重试', icon: 'none' }) }
async function toggleFavorite(): Promise<void> {
  if (!detail.value?.productId || detail.value.productId <= 0) {
    uni.showToast({ title: '商品缺少后端 productId，未执行收藏变更', icon: 'none' })
    return
  }
  if (favoriteLoading.value) return
  favoriteLoading.value = true
  const wasFavorited = favorited.value
  try {
    if (wasFavorited) {
      await unfavoriteProduct(detail.value.productId)
      favorited.value = false
      uni.showToast({ title: '取消收藏已提交后端', icon: 'none' })
    } else {
      await favoriteProduct(detail.value.productId)
      favorited.value = true
      uni.showToast({ title: '收藏已提交后端', icon: 'none' })
    }
  } catch (error) {
    console.warn('product favorite mutation failed', { productId: detail.value?.productId, wasFavorited, error })
    uni.showToast({ title: '收藏接口调用失败，未执行本地收藏变更', icon: 'none' })
  } finally {
    favoriteLoading.value = false
  }
}
onMounted(() => { readProductId(); loadDetail() })
</script>

<style scoped lang="scss" src="./style.scss"></style>
