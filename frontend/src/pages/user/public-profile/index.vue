<template>
  <view class="page-shell public-profile">
    <view v-if="profile.videoVerified" class="video-verify-card ds-card">
      <view class="verify-left">
        <view class="video-icon">▶</view>
        <view class="video-title">视频认证卖家</view>
      </view>
      <view class="video-badge">已核验</view>
    </view>

    <view class="hero ds-card">
      <view class="avatar">{{ avatarText }}</view>
      <view class="main">
        <view class="name-row">
          <text class="name">{{ profile.nickname }}</text>
          <text v-if="profile.videoVerified" class="verify video">视频认证</text>
        </view>
        <view class="bio">{{ profile.userNo || '小原圈用户' }}</view>
        <view v-if="profile.videoVerified" class="tag-row">
          <text class="tag">视频认证卖家</text>
        </view>
      </view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">{{ loadError }}</view>

    <view v-if="profileLoaded" class="stats-card ds-card">
      <view class="stat-item">
        <text class="stat-value">{{ compactNumber(profile.followerCount) }}</text>
        <text class="stat-label">粉丝</text>
      </view>
      <view class="stat-item">
        <text class="stat-value">{{ compactNumber(profile.followingCount) }}</text>
        <text class="stat-label">关注</text>
      </view>
      <view class="stat-item charm">
        <text class="stat-value">{{ compactNumber(profile.sellerCharmScore) }}</text>
        <text class="stat-label">魅力值</text>
      </view>
      <view class="stat-item power">
        <text class="stat-value">{{ compactNumber(profile.buyerPowerScore) }}</text>
        <text class="stat-label">实力值</text>
      </view>
    </view>

    <view class="action-row">
      <button class="secondary-btn" @click="toggleFollow">{{ followed ? '已关注' : '关注' }}</button>
      <button class="primary-btn" @click="chat">私信</button>
      <button class="gift-btn" @click="openGift">送礼物</button>
      <button class="report-btn" @click="report">举报</button>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">在售宝贝</view>
      <view v-if="productsError" class="empty-row">{{ productsError }}</view>
      <view v-else-if="!products.length" class="empty-row">暂无在售商品</view>
      <view v-for="item in products" :key="item.productId" class="product-row" @click="openProduct(item.productId)">
        <view class="product-cover">{{ item.coverImageUrl ? '图' : '无图' }}</view>
        <view class="product-main">
          <view class="product-title">{{ item.title }}</view>
          <view class="product-meta">¥{{ item.price }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { followPublicProfile, getPublicProfile, unfollowPublicProfile, type UserProfileResponse } from '../../../api/modules/user'
import { listSellerProducts, type ProductListItemResponse } from '../../../api/modules/product'

const unavailableProfileMessage = '卖家数据暂时不可用，未展示本地卖家样例'
const noBackendProductsMessage = '暂无后端公开在售商品，未展示本地商品样例'
const productLoadFailedMessage = '卖家商品加载失败，未展示本地商品样例'

const userId = ref('')
const loadError = ref('')
const productsError = ref('')
const emptyProfile: UserProfileResponse = {
  userId: 0,
  nickname: '小原圈用户',
  mainRole: 'UNVERIFIED',
  videoIdentityStatus: 'UNVERIFIED',
  videoVerified: false,
  followedByMe: false,
  followerCount: 0,
  followingCount: 0,
  sellerCharmScore: 0,
  buyerPowerScore: 0
}
const profile = reactive<UserProfileResponse>({ ...emptyProfile })
const profileLoaded = ref(false)
const sellerProducts = ref<ProductListItemResponse[]>([])
const followed = computed(() => profile.followedByMe === true)
const avatarText = computed(() => (profile.nickname || '原').slice(-1))
const products = computed(() => sellerProducts.value)

function readQuery(): void {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as unknown as { options?: Record<string, string> } | undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  userId.value = currentPage?.options?.userId || hashParams?.get('userId') || userId.value
}

function isValidBackendUserId(value: string): boolean {
  return /^[1-9]\d*$/.test(value)
}

function resetProfile(): void {
  profileLoaded.value = false
  Object.assign(profile, emptyProfile)
}

function compactNumber(value: number | undefined): string {
  const numberValue = Number(value || 0)
  if (!Number.isFinite(numberValue) || numberValue <= 0) return '0'
  if (numberValue >= 10000) return `${(numberValue / 10000).toFixed(numberValue >= 100000 ? 0 : 1)}万`
  return String(Math.floor(numberValue))
}

function failClosedProducts(message = noBackendProductsMessage): void {
  sellerProducts.value = []
  productsError.value = message
}

function navigateToUserRoute(missingUserIdTitle: string, buildUrl: (backendUserId: string) => string): void {
  if (!isValidBackendUserId(userId.value)) {
    uni.showToast({ title: missingUserIdTitle, icon: 'none' })
    return
  }
  uni.navigateTo({ url: buildUrl(userId.value) })
}

async function loadProfile(): Promise<void> {
  if (!isValidBackendUserId(userId.value)) {
    resetProfile()
    loadError.value = unavailableProfileMessage
    failClosedProducts()
    return
  }
  try {
    const data = await getPublicProfile(userId.value)
    Object.assign(profile, data)
    profileLoaded.value = true
    loadError.value = ''
  } catch {
    resetProfile()
    loadError.value = unavailableProfileMessage
    uni.showToast({ title: '卖家数据暂时不可用', icon: 'none' })
  }
}

async function loadSellerProducts(): Promise<void> {
  if (!isValidBackendUserId(userId.value)) {
    failClosedProducts()
    return
  }
  try {
    sellerProducts.value = await listSellerProducts(userId.value)
    productsError.value = sellerProducts.value.length ? '' : noBackendProductsMessage
  } catch {
    failClosedProducts(productLoadFailedMessage)
  }
}

async function toggleFollow(): Promise<void> {
  if (!isValidBackendUserId(userId.value)) {
    uni.showToast({ title: '缺少真实用户ID，未执行任何关注变更', icon: 'none' })
    return
  }
  const wasFollowing = followed.value
  try {
    const data = wasFollowing ? await unfollowPublicProfile(userId.value) : await followPublicProfile(userId.value)
    Object.assign(profile, data)
    uni.showToast({ title: wasFollowing ? '已取消关注' : '已关注', icon: 'none' })
  } catch {
    uni.showToast({ title: wasFollowing ? '取消关注没有提交成功，未执行本地关注变更' : '关注没有提交成功，未执行本地关注变更', icon: 'none' })
  }
}

function chat(): void {
  navigateToUserRoute('缺少真实用户ID，未进入私信', (backendUserId) => `/pages/chat/conversation/index?receiverId=${backendUserId}`)
}

function openGift(): void {
  navigateToUserRoute('缺少真实用户ID，未进入送礼', (backendUserId) => `/pages/gift/index?mode=send&receiverId=${backendUserId}&sceneType=PROFILE&sceneId=${backendUserId}`)
}

function report(): void {
  navigateToUserRoute('缺少真实用户ID，未进入举报', (backendUserId) => `/pages/report/submit/index?targetType=USER&targetId=${backendUserId}`)
}

function openProduct(productId: number): void {
  if (!productId || productId <= 0) {
    uni.showToast({ title: '缺少后端商品编号，未打开商品详情', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` })
}

onMounted(() => {
  readQuery()
  loadProfile()
  loadSellerProducts()
})
</script>

<style scoped>
.public-profile{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}
.video-verify-card{margin-top:18rpx;padding:22rpx;border-color:#ffb37c;background:linear-gradient(135deg,#fff2e4,#fffaf6);display:flex;align-items:center;justify-content:space-between;gap:16rpx;box-shadow:0 16rpx 34rpx rgba(255,122,69,.14)}
.verify-left{display:flex;align-items:center;gap:16rpx;min-width:0}
.video-icon{width:72rpx;height:72rpx;border-radius:50%;background:linear-gradient(135deg,#ff7a45,#ff3f8d);color:#fff;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:950;box-shadow:0 10rpx 24rpx rgba(255,63,141,.18)}
.video-title{color:#3a2a1f;font-size:30rpx;font-weight:950}
.video-badge{flex-shrink:0;padding:9rpx 14rpx;border-radius:999rpx;background:#fff;color:#ff3f8d;font-size:20rpx;font-weight:950}
.hero,.section-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}
.hero{display:flex;gap:18rpx;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}
.avatar{width:104rpx;height:104rpx;border-radius:50%;background:linear-gradient(135deg,#ff7a45,#ffb08a);color:#fff;display:flex;align-items:center;justify-content:center;font-size:42rpx;font-weight:950}
.main{flex:1;min-width:0}
.name-row{display:flex;align-items:center;gap:10rpx;flex-wrap:wrap}
.name{color:#3a2a1f;font-size:33rpx;font-weight:950}
.verify,.tag{padding:7rpx 12rpx;border-radius:999rpx;background:#fff;color:#ff7a45;font-size:19rpx;font-weight:900}
.verify.video{background:#ff7a45;color:#fff}
.bio{margin-top:8rpx;color:#7b5542;font-size:23rpx;line-height:1.45}
.tag-row{margin-top:10rpx;display:flex;gap:8rpx;flex-wrap:wrap}
.stats-card{margin-top:18rpx;padding:18rpx;border-color:#ffd9bd;display:grid;grid-template-columns:repeat(4,1fr);gap:10rpx;background:linear-gradient(135deg,#fff,#fff8ef)}
.stat-item{min-width:0;padding:14rpx 8rpx;border-radius:22rpx;background:#fffaf6;text-align:center;box-shadow:inset 0 0 0 1rpx rgba(255,217,189,.58)}
.stat-value{display:block;color:#3a2a1f;font-size:30rpx;font-weight:950;line-height:1.1}
.stat-label{display:block;margin-top:8rpx;color:#9b7560;font-size:20rpx;font-weight:900;white-space:nowrap}
.stat-item.charm .stat-value{color:#d94673}
.stat-item.power .stat-value{color:#1d4ed8}
.action-row{margin-top:18rpx;display:grid;grid-template-columns:repeat(4,1fr);gap:10rpx}
.gift-btn,.report-btn{min-height:72rpx;border-radius:999rpx;font-size:22rpx;font-weight:900}
.gift-btn{background:#fff3e7;color:#ff7a45}
.report-btn{background:#fff;color:#9b7560;border:1rpx solid #ffd9bd}
.empty-card,.empty-row{margin-top:18rpx;padding:24rpx;text-align:center;color:#9b7560;border-color:#ffd9bd}
.section-title{color:#3a2a1f;font-size:28rpx;font-weight:950}
.product-row{margin-top:14rpx;padding:16rpx;border-radius:24rpx;background:#fffaf6;display:flex;align-items:center;gap:14rpx}
.product-cover{width:88rpx;height:88rpx;border-radius:22rpx;background:#fff;color:#b9856a;display:flex;align-items:center;justify-content:center;font-size:22rpx;font-weight:900}
.product-main{flex:1;min-width:0}
.product-title{color:#3a2a1f;font-size:24rpx;font-weight:900;line-height:1.45}
.product-meta{margin-top:6rpx;color:#ff7a45;font-size:22rpx;font-weight:900}
</style>
