<template>
  <view class="page-shell public-profile">
    <view v-if="profileLoaded" class="hero">
      <view class="profile-top">
        <view class="avatar-wrap">
          <view class="avatar" :class="{ image: !!safeAvatarUrl }">
            <image v-if="safeAvatarUrl" class="avatar-image" :src="safeAvatarUrl" mode="aspectFill" />
            <text v-else>{{ avatarText }}</text>
          </view>
          <view v-if="hasIdentityVideo" class="avatar-verify">✓</view>
        </view>
        <view class="main">
          <view class="name-row">
            <text class="gender-mark" :class="profileGender">{{ genderSymbol }}</text>
            <text class="name">{{ profile.nickname }}</text>
            <text v-if="isSellerProfile" class="role-chip">{{ hasIdentityVideo ? '认证卖家' : '卖家' }}</text>
            <text v-if="profileLevel" class="level-chip level-glow" :class="[levelTrackClass, levelGlowClass]">LV.{{ profileLevel.level }} {{ profileLevel.title }}</text>
          </view>
          <view class="meta-line">{{ profileMetaText }}</view>
        </view>
        <button v-if="hasIdentityVideo" class="verify-entry" @click="openVideoPreview">认证视频</button>
      </view>
      <view class="inline-bio">
        <text class="bio-title">简介</text>
        <text class="bio">{{ profileBioText }}</text>
      </view>
      <view class="stats-card">
        <view class="stat-item">
          <text class="stat-label">粉丝</text>
          <text class="stat-value">{{ compactNumber(profile.followerCount) }}</text>
        </view>
        <view class="stat-item">
          <text class="stat-label">关注</text>
          <text class="stat-value">{{ compactNumber(profile.followingCount) }}</text>
        </view>
        <view class="stat-item value-highlight" :class="{ charm: isSellerProfile, power: !isSellerProfile }">
          <text class="stat-kicker">{{ primaryScoreLabel }}</text>
          <text class="stat-value big">{{ compactNumber(primaryScoreValue) }}</text>
        </view>
      </view>
    </view>

    <view v-if="hasIdentityVideo" class="video-verify-card tapable" @click="openVideoPreview">
      <video
        class="verify-video-bg"
        :src="identityVideoUrl"
        muted
        object-fit="cover"
        :controls="false"
        :show-center-play-btn="false"
        :show-play-btn="false"
        :enable-progress-gesture="false"
      />
      <view class="verify-card-shade"></view>
      <view class="verify-copy">
        <view class="verify-kicker">真人认证</view>
        <view class="verify-title">查看认证视频</view>
        <view class="verify-subtitle">平台审核通过后展示</view>
      </view>
      <view class="verify-play">▶</view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">{{ loadError }}</view>

    <view v-if="profileLoaded" class="action-row">
      <button class="secondary-btn" @click="toggleFollow">{{ followed ? '已关注' : '关注' }}</button>
      <button class="primary-btn" @click="chat">私信</button>
      <button class="gift-btn" @click="openGift">送礼物</button>
      <button class="report-btn" @click="report">举报</button>
    </view>

    <view v-if="showcasePhotos.length" class="showcase-card">
      <view class="section-title">照片</view>
      <scroll-view class="showcase-scroll" scroll-x>
        <view class="showcase-strip">
          <image
            v-for="url in showcasePhotos"
            :key="url"
            class="showcase-photo"
            :src="url"
            mode="aspectFill"
            @click="openPhotoPreview(url)"
          />
        </view>
      </scroll-view>
    </view>

    <view v-if="previewPhotoUrl" class="photo-preview" @click="closePhotoPreview">
      <image class="photo-preview-image" :src="previewPhotoUrl" mode="aspectFit" />
    </view>

    <view v-if="videoPreviewOpen" class="video-preview" @click="closeVideoPreview">
      <view class="video-preview-panel" @click.stop>
        <video
          class="video-preview-player"
          :src="identityVideoUrl"
          controls
          autoplay
          object-fit="contain"
          :show-center-play-btn="true"
        />
      </view>
    </view>

    <view v-if="showSellerTradePanel" class="section-card seller-trade-card">
      <view class="section-head compact">
        <view>
          <view class="section-title">卖家宝贝</view>
          <view class="section-subtitle">在售 {{ products.length }} · 已售 {{ soldProducts.length }}</view>
        </view>
        <view class="seller-verified-chip" :class="{ pending: !hasIdentityVideo }">{{ sellerTradeBadgeText }}</view>
      </view>

      <view class="trade-block">
        <view class="trade-block-title">在售商品</view>
        <view v-if="productsError" class="empty-row">暂无在售宝贝</view>
        <view v-else-if="!products.length" class="empty-row">暂无在售商品</view>
        <view v-for="item in products" :key="`active-${item.productId}`" class="product-row" @click="openProduct(item.productId)">
          <view class="product-cover" :class="{ image: !!item.coverImageUrl }">
            <image v-if="item.coverImageUrl" class="product-cover-image" :src="item.coverImageUrl" mode="aspectFill" />
            <text v-else>{{ productIcon(item.title) }}</text>
          </view>
          <view class="product-main">
            <view class="product-title">{{ item.title }}</view>
            <view class="product-meta">¥{{ item.price }}</view>
          </view>
          <view class="product-status active">在售</view>
        </view>
      </view>

      <view class="trade-block sold">
        <view class="trade-block-title">已售历史</view>
        <view v-if="soldProductsError" class="empty-row">暂无已售记录</view>
        <view v-else-if="!soldProducts.length" class="empty-row">暂无已售记录</view>
        <view v-for="item in soldProducts" :key="`sold-${item.productId}`" class="product-row sold-row">
        <view class="product-cover" :class="{ image: !!item.coverImageUrl }">
          <image v-if="item.coverImageUrl" class="product-cover-image" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ productIcon(item.title) }}</text>
        </view>
        <view class="product-main">
          <view class="product-title">{{ item.title }}</view>
          <view class="product-meta">¥{{ item.price }}</view>
        </view>
          <view class="product-status sold">已售</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { followPublicProfile, getMyProfile, getPublicProfile, unfollowPublicProfile, type UserProfileResponse } from '../../../api/modules/user'
import { devRuntimeUserId, isDevRuntimeEnabled, resolveBackendMediaUrl } from '../../../api/http'
import { listSellerProducts, listSellerSoldProducts, type ProductListItemResponse } from '../../../api/modules/product'
import {
  assertProductList,
  assertPublicProfile,
  avatarImageStoragePrefix,
  compactNumber,
  decodeRouteValue,
  goddessLevelTitles,
  godLevelTitles,
  hasApprovedPublicSellerVideo,
  isValidBackendUserId,
  levelGlow,
  levelThresholds,
  noBackendProductsMessage,
  productIcon,
  productImageStoragePrefix,
  productLoadFailedMessage,
  showcaseImageStoragePrefix,
  soldProductLoadFailedMessage,
  unavailableProfileMessage,
  validatedPublicMediaUrl,
  videoIdentityStoragePrefix
} from './profile-integrity'

const userId = ref('')
const currentUserId = ref('')
const loadError = ref('')
const productsError = ref('')
const soldProductsError = ref('')
const emptyProfile: UserProfileResponse = {
  userId: 0,
  nickname: '小原圈用户',
  mainRole: 'UNVERIFIED',
  identityStatus: 'UNVERIFIED',
  videoIdentityStatus: 'UNVERIFIED',
  videoVerified: false,
  followedByMe: false,
  followerCount: 0,
  followingCount: 0,
  sellerCharmScore: 0,
  buyerPowerScore: 0,
  showcaseImageUrls: [],
  level: null
}
const profile = reactive<UserProfileResponse>({ ...emptyProfile })
const profileLoaded = ref(false)
const sellerProducts = ref<ProductListItemResponse[]>([])
const sellerSoldProducts = ref<ProductListItemResponse[]>([])
const followed = computed(() => profile.followedByMe === true)
const avatarText = computed(() => (profile.nickname || '原').slice(-1))
const safeAvatarUrl = computed(() => resolveBackendMediaUrl(validatedPublicMediaUrl(profile.avatarUrl || '', [avatarImageStoragePrefix, showcaseImageStoragePrefix])))
const products = computed(() => sellerProducts.value.map((item) => ({ ...item, coverImageUrl: resolveBackendMediaUrl(validatedPublicMediaUrl(item.coverImageUrl, productImageStoragePrefix)) })))
const soldProducts = computed(() => sellerSoldProducts.value.map((item) => ({ ...item, coverImageUrl: resolveBackendMediaUrl(validatedPublicMediaUrl(item.coverImageUrl, productImageStoragePrefix)) })))
const isSellerProfile = computed(() => ['SELLER', 'BOTH'].includes((profile.mainRole || '').toUpperCase()))
const profileGender = computed(() => profile.gender === 'god' ? 'god' : 'goddess')
const genderSymbol = computed(() => profileGender.value === 'god' ? '♂' : '♀')
const hasApprovedSellerVideo = computed(() => profileLoaded.value && isSellerProfile.value && profile.videoVerified === true && profile.videoIdentityStatus === 'APPROVED' && hasApprovedPublicSellerVideo(profile))
const showSellerTradePanel = computed(() => profileLoaded.value && isSellerProfile.value)
const identityVideoUrl = computed(() => hasApprovedSellerVideo.value ? resolveBackendMediaUrl(validatedPublicMediaUrl(profile.videoIdentityUrl || '', videoIdentityStoragePrefix)) : '')
const hasIdentityVideo = computed(() => !!identityVideoUrl.value)
const sellerTradeBadgeText = computed(() => hasIdentityVideo.value ? '认证卖家' : '卖家资料')
const showcasePhotos = computed(() => profileLoaded.value ? (profile.showcaseImageUrls || []).map((url) => resolveBackendMediaUrl(validatedPublicMediaUrl(url, showcaseImageStoragePrefix))).filter((url) => !!url) : [])
const primaryScoreLabel = computed(() => isSellerProfile.value ? '魅力值' : '实力值')
const primaryScoreValue = computed(() => isSellerProfile.value ? profile.sellerCharmScore : profile.buyerPowerScore)
const primaryScoreHint = computed(() => isSellerProfile.value ? '收礼 1 元 = 1 分' : '消费/送礼 1 元 = 1 分')
const profileLevel = computed(() => profile.level || buildFallbackLevel())
const levelTrackClass = computed(() => profileLevel.value?.track === 'POWER' ? 'power' : 'charm')
const levelGlowClass = computed(() => levelGlow(profileLevel.value?.level))
const previewPhotoUrl = ref('')
const videoPreviewOpen = ref(false)
const profileBioText = computed(() => {
  const bio = String(profile.bio || '').trim()
  return bio || '暂未填写个人简介'
})
const profileMetaText = computed(() => {
  const ageText = profile.age ? `${profile.age}岁` : ''
  const ipText = profile.ipLocation ? `IP属地 ${profile.ipLocation}` : ''
  const parts = [ageText, ipText, profile.userNo].map((item) => String(item || '').trim()).filter(Boolean)
  return parts.join(' · ') || '小原圈'
})
const isSelfProfile = computed(() => isValidBackendUserId(userId.value) && userId.value === currentUserId.value)

function readQuery(): void {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as unknown as { options?: Record<string, string> } | undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeUserId = decodeRouteValue('userId', currentPage?.options?.userId ?? hashParams?.get('userId') ?? '')
  userId.value = isValidBackendUserId(routeUserId) ? routeUserId : ''
}

async function resolveCurrentUserId(): Promise<void> {
  try {
    const data = await getMyProfile()
    currentUserId.value = Number.isSafeInteger(data.userId) && data.userId > 0 ? String(data.userId) : ''
  } catch (error) {
    const fallbackUserId = canUseLocalUserFallback() ? String(devRuntimeUserId() || '') : ''
    currentUserId.value = isValidBackendUserId(fallbackUserId) ? fallbackUserId : ''
    console.warn('public profile current user resolve failed', { error })
  }
}

function canUseLocalUserFallback(): boolean {
  if (isDevRuntimeEnabled()) return true
  if (typeof window === 'undefined' || !window.location?.hostname) return false
  return ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname)
}

function resetProfile(): void {
  profileLoaded.value = false
  Object.assign(profile, emptyProfile)
}

function buildFallbackLevel() {
  const power = profileGender.value === 'god'
  const score = Math.max(0, Math.floor(Number(power ? profile.buyerPowerScore : profile.sellerCharmScore) || 0))
  let level = 1
  for (let index = 0; index < levelThresholds.length; index += 1) {
    if (score >= levelThresholds[index]) level = index + 1
  }
  return {
    level,
    title: (power ? godLevelTitles : goddessLevelTitles)[level - 1],
    track: power ? 'POWER' : 'CHARM',
    score
  }
}

function failClosedProducts(message = noBackendProductsMessage): void {
  sellerProducts.value = []
  productsError.value = message
}

function failClosedSoldProducts(message = ''): void {
  sellerSoldProducts.value = []
  soldProductsError.value = message
}

function navigateToUserRoute(missingUserIdTitle: string, buildUrl: (backendUserId: string) => string): void {
  if (!isValidBackendUserId(userId.value) || !profileLoaded.value) {
    uni.showToast({ title: missingUserIdTitle, icon: 'none' })
    return
  }
  const route = {
    url: buildUrl(userId.value),
    fail: (error: unknown) => {
      console.warn('public profile navigation failed', { userId: userId.value, error })
      uni.showToast({ title: '暂时无法打开目标页面，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('public profile navigation failed', { userId: userId.value, error })
    uni.showToast({ title: '暂时无法打开目标页面，请稍后重试', icon: 'none' })
  }
}

function switchToMe(title = '这是你自己的主页'): void {
  uni.showToast({ title, icon: 'none' })
  try {
    uni.switchTab({ url: '/pages/tabbar/me/index' })
  } catch (error) {
    console.warn('public profile switch to me failed', { error })
    uni.reLaunch({ url: '/pages/tabbar/me/index' })
  }
}

async function loadProfile(): Promise<boolean> {
  if (!isValidBackendUserId(userId.value)) {
    resetProfile()
    loadError.value = unavailableProfileMessage
    failClosedProducts()
    failClosedSoldProducts()
    return false
  }
  try {
    const data = await getPublicProfile(userId.value)
    assertPublicProfile(data, userId.value)
    Object.assign(profile, data)
    profileLoaded.value = true
    loadError.value = ''
    return true
  } catch (error) {
    resetProfile()
    failClosedProducts()
    failClosedSoldProducts()
    loadError.value = unavailableProfileMessage
    console.warn('public profile load failed', { userId: userId.value, error })
    uni.showToast({ title: '卖家资料暂时不可用', icon: 'none' })
    return false
  }
}

async function loadSellerProducts(): Promise<void> {
  if (!isValidBackendUserId(userId.value)) {
    failClosedProducts()
    failClosedSoldProducts()
    return
  }
  if (!showSellerTradePanel.value) {
    failClosedProducts('')
    failClosedSoldProducts()
    return
  }
  try {
    const [activeData, soldData] = await Promise.all([
      listSellerProducts(userId.value),
      listSellerSoldProducts(userId.value)
    ])
    assertProductList(activeData, 'ACTIVE')
    assertProductList(soldData, 'SOLD')
    const data = activeData
    sellerProducts.value = data
    productsError.value = sellerProducts.value.length ? '' : noBackendProductsMessage
    sellerSoldProducts.value = soldData
    soldProductsError.value = ''
  } catch (error) {
    console.warn('public profile products load failed', { userId: userId.value, error })
    failClosedProducts(productLoadFailedMessage)
    failClosedSoldProducts(soldProductLoadFailedMessage)
  }
}

async function toggleFollow(): Promise<void> {
  if (isSelfProfile.value) {
    switchToMe('不能关注自己')
    return
  }
  if (!isValidBackendUserId(userId.value) || !profileLoaded.value) {
    uni.showToast({ title: '用户资料暂时不可用，未完成关注', icon: 'none' })
    return
  }
  const wasFollowing = followed.value
  try {
    const data = wasFollowing ? await unfollowPublicProfile(userId.value) : await followPublicProfile(userId.value)
    assertPublicProfile(data, userId.value)
    Object.assign(profile, data)
    uni.showToast({ title: wasFollowing ? '已取消关注' : '已关注', icon: 'none' })
  } catch (error) {
    console.warn('public profile follow mutation failed', { userId: userId.value, wasFollowing, error })
    uni.showToast({ title: wasFollowing ? '取消关注没有提交成功，请稍后重试' : '关注没有提交成功，请稍后重试', icon: 'none' })
  }
}

function chat(): void {
  if (isSelfProfile.value) {
    switchToMe('不能给自己发私信')
    return
  }
  navigateToUserRoute('用户资料暂时不可用，未进入私信', (backendUserId) => `/pages/chat/conversation/index?receiverId=${backendUserId}`)
}

function openGift(): void {
  if (isSelfProfile.value) {
    switchToMe('不能给自己送礼物')
    return
  }
  navigateToUserRoute('用户资料暂时不可用，未进入送礼', (backendUserId) => `/pages/gift/index?mode=send&receiverId=${backendUserId}&sceneType=PROFILE&sceneId=${backendUserId}`)
}

function report(): void {
  navigateToUserRoute('用户资料暂时不可用，未进入举报', (backendUserId) => `/pages/report/submit/index?targetType=USER&targetId=${backendUserId}`)
}

function openPhotoPreview(url: string): void {
  previewPhotoUrl.value = url
}

function closePhotoPreview(): void {
  previewPhotoUrl.value = ''
}

function openVideoPreview(): void {
  if (!hasIdentityVideo.value) return
  videoPreviewOpen.value = true
}

function closeVideoPreview(): void {
  videoPreviewOpen.value = false
}

function openProduct(productId: number): void {
  if (!productId || productId <= 0) {
    uni.showToast({ title: '商品资料暂时不可用，未打开详情', icon: 'none' })
    return
  }
  const route = {
    url: `/pages/product/detail/index?productId=${encodeURIComponent(String(productId))}`,
    fail: (error: unknown) => {
      console.warn('public profile product navigation failed', { productId, error })
      uni.showToast({ title: '暂时无法打开商品详情，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('public profile product navigation failed', { productId, error })
    uni.showToast({ title: '暂时无法打开商品详情，请稍后重试', icon: 'none' })
  }
}

async function initializePublicProfile(): Promise<void> {
  try {
    readQuery()
    await resolveCurrentUserId()
    if (isSelfProfile.value) {
      switchToMe()
      return
    }
    if (await loadProfile()) await loadSellerProducts()
  } catch (error) {
    resetProfile()
    failClosedProducts()
    loadError.value = unavailableProfileMessage
    console.warn('public profile initialize failed', { error })
  }
}

onMounted(() => { void initializePublicProfile() })
</script>

<style scoped lang="scss" src="./style.scss"></style>
