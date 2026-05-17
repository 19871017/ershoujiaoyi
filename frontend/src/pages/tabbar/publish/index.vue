<template>
  <view class="page-shell seller-page">
    <GlobalTicker />
    <view class="goddess-top ds-card">
      <view class="goddess-bg" />
      <view class="goddess-head">
        <view class="head-copy">
          <view class="page-title">商家秀</view>
          <view class="goddess-label">认证女神榜 TOP 3</view>
          <view class="verify-strip">
            <text>视频认证已通过</text>
            <text>按后端榜单展示</text>
          </view>
        </view>
        <button class="publish-btn" @click="goPublishForm">我要上新</button>
      </view>
      <view class="podium-row">
        <view
          v-for="item in podiumList"
          :key="item.id"
          class="podium-item tapable"
          :class="`rank-${item.rank}`"
          @click="openProfile(item)"
        >
          <view class="rank-ribbon">{{ rankTitle(item.rank) }}</view>
          <view class="crown">{{ medalFor(item.rank) }}</view>
          <view class="podium-avatar" :class="{ image: !!item.avatarUrl }">
            <image v-if="item.avatarUrl" class="avatar-image" :src="item.avatarUrl" mode="aspectFill" />
            <text v-else>{{ item.avatar }}</text>
          </view>
          <view class="podium-name">{{ item.name }}</view>
          <view class="podium-score">收礼 {{ item.giftScore }}</view>
          <view class="podium-glow">认证商家</view>
        </view>
      </view>
    </view>

    <view class="merchant-head">
      <view class="section-title">认证商家</view>
      <view class="merchant-count">{{ merchants.length }}</view>
    </view>

    <view class="spotlight-row">
      <view v-for="item in spotlightMerchants" :key="`spot-${item.id}`" class="spotlight-card ds-card tapable" @click="openProfile(item)">
        <view class="spotlight-avatar" :class="{ image: !!item.avatarUrl }">
          <image v-if="item.avatarUrl" class="avatar-image" :src="item.avatarUrl" mode="aspectFill" />
          <text v-else>{{ item.avatar }}</text>
        </view>
        <view class="spotlight-main">
          <view class="spotlight-name">{{ item.name }}</view>
          <view class="spotlight-meta">{{ item.city }}</view>
        </view>
        <view class="spotlight-score">收礼 {{ item.giftScore }}</view>
      </view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">
      <view class="empty-title">商家秀暂时不可用</view>
      <button class="retry-btn" @click="loadData">重试</button>
    </view>

    <view v-else-if="!merchants.length" class="empty-card ds-card">
      <view class="empty-title">暂无认证商家</view>
    </view>

    <view v-else class="merchant-grid">
      <view v-for="item in merchants" :key="item.id" class="merchant-card ds-card tapable" @click="openProfile(item)">
        <view class="merchant-cover">
          <view class="merchant-sparkle">✦</view>
          <view class="merchant-rank">#{{ item.rank }}</view>
          <view class="merchant-avatar" :class="{ image: !!item.avatarUrl }">
            <image v-if="item.avatarUrl" class="avatar-image" :src="item.avatarUrl" mode="aspectFill" />
            <text v-else>{{ item.avatar }}</text>
          </view>
          <view class="verified-badge">已认证</view>
        </view>
        <view class="merchant-title-row">
          <view class="merchant-name">{{ item.name }}</view>
          <view class="merchant-seal">认证</view>
        </view>
        <view class="merchant-meta">{{ item.city }}</view>
        <view class="merchant-score">收礼 {{ item.giftScore }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listUserRankings, type UserRankingResponse } from '../../../api/modules/ranking'
import { getMyProfile } from '../../../api/modules/user'
import GlobalTicker from '../../../components/GlobalTicker.vue'

interface MerchantShowUser {
  id: number
  rank: number
  avatar: string
  avatarUrl: string
  name: string
  city: string
  giftScore: number
}

const merchants = ref<MerchantShowUser[]>([])
const loadError = ref(false)
const canPublish = ref(false)
const podiumList = computed(() => {
  const top = merchants.value.slice(0, 3)
  return top.length === 3 ? [top[1]!, top[0]!, top[2]!] : top
})
const spotlightMerchants = computed(() => merchants.value.slice(0, 2))

onMounted(() => {
  void loadData()
  void loadPublishPermission()
})

async function loadData() {
  loadError.value = false
  try {
    const rows = await listUserRankings('goddess', 'week', 100)
    merchants.value = rows.filter(isCertifiedMerchant).map(toMerchantShowUser)
  } catch {
    merchants.value = []
    loadError.value = true
  }
}

function isSellerRole(role: string) {
  return role === 'SELLER' || role === 'BOTH'
}

function isCertifiedMerchant(item: UserRankingResponse) {
  return isSellerRole(String(item.mainRole || '').toUpperCase()) && item.videoVerified === true
}

function toMerchantShowUser(item: UserRankingResponse): MerchantShowUser {
  return {
    id: item.userId,
    rank: item.rank,
    avatar: (item.nickname || '商').slice(0, 1),
    avatarUrl: item.avatarUrl || '',
    name: item.nickname || '认证商家',
    city: item.city || '小原圈',
    giftScore: item.giftScore ?? item.popularityScore ?? 0
  }
}

function medalFor(rank: number) {
  switch (rank) {
    case 1:
      return '👑'
    case 2:
      return '💎'
    case 3:
      return '🌟'
    default:
      return `#${rank}`
  }
}

function rankTitle(rank: number) {
  if (rank === 1) return 'TOP 1'
  if (rank === 2) return 'TOP 2'
  if (rank === 3) return 'TOP 3'
  return `TOP ${rank}`
}

function openProfile(item: MerchantShowUser) {
  if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少平台用户编号', icon: 'none' })
  uni.navigateTo({ url: `/pages/user/public-profile/index?userId=${item.id}` })
}

async function loadPublishPermission() {
  try {
    const profile = await getMyProfile()
    canPublish.value = !!profile.videoVerified && isSellerRole(String(profile.mainRole || '').toUpperCase())
  } catch {
    canPublish.value = false
  }
}

function goPublishForm() {
  if (!canPublish.value) {
    uni.showToast({ title: '请先完成卖家认证', icon: 'none' })
    uni.navigateTo({ url: '/pages/user/identity/index?tab=video' })
    return
  }
  uni.navigateTo({ url: '/pages/product/publish/index' })
}
</script>

<style scoped>
.seller-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 48%,#fff6ef 100%);padding-bottom:28rpx}
.goddess-top{position:relative;overflow:hidden;margin-top:18rpx;padding:26rpx 22rpx 28rpx;border-color:#ffd6c9;background:linear-gradient(135deg,#fff7fb 0%,#ffeef4 42%,#fff4e6 100%);box-shadow:0 24rpx 58rpx rgba(255,91,145,.13)}
.goddess-bg{position:absolute;right:-42rpx;top:-56rpx;width:330rpx;height:330rpx;border-radius:50%;background:radial-gradient(circle at 35% 32%,rgba(255,255,255,.92),rgba(255,179,203,.38) 38%,rgba(255,122,69,.18) 64%,rgba(255,122,69,0) 74%)}
.goddess-top:before{content:"";position:absolute;inset:18rpx;border:1rpx solid rgba(255,255,255,.56);border-radius:34rpx;pointer-events:none}
.goddess-top:after{content:"";position:absolute;left:-80rpx;bottom:-120rpx;width:300rpx;height:220rpx;border-radius:50%;background:rgba(255,192,203,.18);filter:blur(8rpx)}
.goddess-head{position:relative;z-index:1;display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx}
.head-copy{display:flex;flex-direction:column;gap:12rpx;min-width:0}
.page-title{color:#3a2a1f;font-size:42rpx;font-weight:950;letter-spacing:2rpx;font-family:STSong,Songti SC,serif;text-shadow:0 4rpx 16rpx rgba(255,255,255,.62)}
.goddess-label{width:max-content;height:44rpx;padding:0 16rpx;border-radius:999rpx;background:rgba(255,255,255,.66);border:1rpx solid rgba(255,214,201,.9);display:flex;align-items:center;color:#c56a37;font-size:20rpx;font-weight:950;box-shadow:0 10rpx 22rpx rgba(255,122,69,.08)}
.verify-strip{display:flex;flex-wrap:wrap;gap:8rpx}
.verify-strip text{height:34rpx;padding:0 12rpx;border-radius:999rpx;background:rgba(255,255,255,.58);border:1rpx solid rgba(255,214,201,.74);color:#8a4b2f;font-size:17rpx;font-weight:950;line-height:34rpx;box-shadow:0 8rpx 16rpx rgba(255,122,69,.07)}
.publish-btn{margin:0;padding:0 22rpx;height:58rpx;line-height:58rpx;border-radius:999rpx;background:#3a2a1f;color:#fff;font-size:22rpx;font-weight:950;flex-shrink:0;box-shadow:0 14rpx 30rpx rgba(58,42,31,.18)}
.podium-row{position:relative;z-index:1;margin-top:24rpx;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));align-items:end;gap:12rpx}
.podium-item{position:relative;min-height:204rpx;padding:28rpx 10rpx 16rpx;border-radius:32rpx;background:rgba(255,255,255,.76);border:1rpx solid rgba(255,255,255,.82);display:flex;flex-direction:column;align-items:center;box-shadow:0 16rpx 34rpx rgba(111,78,55,.09);backdrop-filter:blur(12rpx)}
.podium-item.rank-1{min-height:236rpx;background:linear-gradient(180deg,rgba(255,255,255,.94) 0%,rgba(255,239,214,.88) 100%);box-shadow:0 24rpx 48rpx rgba(255,122,69,.2);transform:translateY(-8rpx)}
.rank-ribbon{position:absolute;top:10rpx;left:50%;transform:translateX(-50%);height:28rpx;padding:0 12rpx;border-radius:999rpx;background:rgba(58,42,31,.88);color:#fff;font-size:16rpx;font-weight:950;line-height:28rpx;letter-spacing:.6rpx}
.crown{margin-top:6rpx;font-size:34rpx;line-height:1;filter:drop-shadow(0 6rpx 10rpx rgba(255,122,69,.2))}
.podium-avatar{margin-top:8rpx;width:84rpx;height:84rpx;border-radius:50%;background:linear-gradient(135deg,#ff8aa8,#ffbe6e);color:#fff;display:flex;align-items:center;justify-content:center;font-size:34rpx;font-weight:950;border:5rpx solid rgba(255,255,255,.9);box-shadow:0 12rpx 24rpx rgba(255,91,145,.22);overflow:hidden}
.podium-avatar.image{background:#fff}
.avatar-image{width:100%;height:100%;display:block}
.podium-name{margin-top:10rpx;max-width:100%;color:#3a2a1f;font-size:22rpx;font-weight:950;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.podium-score{margin-top:6rpx;color:#c56a37;font-size:19rpx;font-weight:900}
.podium-glow{margin-top:8rpx;padding:5rpx 10rpx;border-radius:999rpx;background:linear-gradient(135deg,#ff7a45,#ff3f8d);color:#fff;font-size:16rpx;font-weight:950;box-shadow:0 8rpx 18rpx rgba(255,63,141,.18)}
.merchant-head{margin-top:24rpx;display:flex;align-items:center;justify-content:space-between}
.section-title{color:#3a2a1f;font-size:31rpx;font-weight:950;font-family:STSong,Songti SC,serif;letter-spacing:1rpx}
.merchant-count{min-width:54rpx;height:44rpx;padding:0 14rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#ff7a45;display:flex;align-items:center;justify-content:center;font-size:22rpx;font-weight:950}
.spotlight-row{margin-top:14rpx;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14rpx}
.spotlight-card{padding:16rpx;border-color:#ffd9bd;background:linear-gradient(135deg,#fff,#fff4ea);display:flex;align-items:center;gap:12rpx;box-shadow:0 16rpx 32rpx rgba(255,122,69,.08);overflow:hidden}
.spotlight-avatar{width:72rpx;height:72rpx;border-radius:50%;background:linear-gradient(135deg,#ff7a9d,#ffb15d);color:#fff;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:950;overflow:hidden;flex:0 0 auto}
.spotlight-avatar.image{background:#fff}
.spotlight-main{flex:1;min-width:0;display:flex;flex-direction:column;gap:6rpx}
.spotlight-name{color:#3a2a1f;font-size:24rpx;font-weight:950;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.spotlight-meta{color:#9b7560;font-size:20rpx;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.spotlight-score{flex:0 0 auto;padding:8rpx 12rpx;border-radius:999rpx;background:#fff3e7;color:#ff3f8d;font-size:20rpx;font-weight:950}
.empty-card{margin-top:18rpx;padding:26rpx;border-color:#ffd9bd;background:#fff}
.empty-title{color:#3a2a1f;font-size:28rpx;font-weight:950}
.retry-btn{margin-top:18rpx;height:62rpx;line-height:62rpx;border-radius:999rpx;background:#ff7a45;color:#fff;border:1rpx solid #ff7a45;font-size:22rpx;font-weight:950}
.merchant-grid{margin-top:16rpx;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx}
.merchant-card{position:relative;overflow:hidden;padding:16rpx;border-color:#ffd9bd;background:linear-gradient(180deg,#fff 0%,#fff6ef 100%);box-shadow:0 18rpx 40rpx rgba(111,78,55,.08)}
.merchant-cover{height:166rpx;border-radius:30rpx;background:radial-gradient(circle at 24% 20%,rgba(255,255,255,.86),rgba(255,255,255,0) 24%),linear-gradient(135deg,#fff0f6 0%,#ffe1cd 52%,#fff8ed 100%);display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden}
.merchant-cover:before{content:"";position:absolute;right:-38rpx;top:-48rpx;width:160rpx;height:160rpx;border-radius:50%;background:rgba(255,255,255,.48)}
.merchant-cover:after{content:"";position:absolute;left:-38rpx;bottom:-54rpx;width:150rpx;height:120rpx;border-radius:50%;background:rgba(255,122,157,.13);filter:blur(4rpx)}
.merchant-sparkle{position:absolute;right:18rpx;top:18rpx;color:#ff8aa8;font-size:30rpx;text-shadow:0 0 14rpx rgba(255,255,255,.95);z-index:2}
.merchant-rank{position:absolute;left:12rpx;top:12rpx;height:34rpx;padding:0 12rpx;border-radius:999rpx;background:rgba(255,255,255,.82);color:#c56a37;font-size:18rpx;font-weight:950;line-height:34rpx;z-index:2}
.merchant-avatar{position:relative;z-index:1;width:108rpx;height:108rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#ff7a9d 0%,#ffb15d 100%);color:#fff;font-size:40rpx;font-weight:950;border:6rpx solid rgba(255,255,255,.88);box-shadow:0 16rpx 32rpx rgba(255,91,145,.2);overflow:hidden}
.merchant-avatar.image{background:#fff}
.verified-badge{position:absolute;right:12rpx;bottom:12rpx;height:40rpx;padding:0 14rpx;border-radius:999rpx;background:rgba(58,42,31,.88);color:#fff;font-size:18rpx;font-weight:900;display:flex;align-items:center}
.merchant-title-row{margin-top:14rpx;display:flex;align-items:center;gap:8rpx;min-width:0}
.merchant-name{flex:1;min-width:0;color:#3a2a1f;font-size:26rpx;font-weight:950;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.merchant-seal{flex-shrink:0;padding:4rpx 8rpx;border-radius:999rpx;background:#fff3e7;color:#ff7a45;font-size:17rpx;font-weight:950}
.merchant-meta{margin-top:6rpx;color:#9b7560;font-size:21rpx;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.merchant-score{margin-top:10rpx;color:#ff3f8d;font-size:24rpx;font-weight:950}
</style>
