<template>
  <view class="page-shell seller-page">
    <swiper v-if="visibleShowcaseBanners.length" class="showcase-swiper" circular autoplay :interval="3600" :duration="520" indicator-dots indicator-color="rgba(255,255,255,.52)" indicator-active-color="#fff8df">
      <swiper-item v-for="item in visibleShowcaseBanners" :key="item.id">
        <view class="goddess-top ds-card">
          <image class="showcase-bg" :src="item.imageUrl" mode="aspectFill" />
          <view class="goddess-shade"></view>
        </view>
      </swiper-item>
    </swiper>
    <view v-else class="showcase-empty ds-card">
      <view class="showcase-empty-title">{{ showcaseLoadError ? '商家秀轮播暂时不可用' : '商家秀顶部轮播待后台配置' }}</view>
      <view class="showcase-empty-desc">顶部图片以后台轮播配置为准，未获取到服务端配置时不展示本地兜底图。</view>
    </view>

    <view class="podium-panel ds-card">
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
      <view class="section-title">精品认证商家</view>
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

    <view v-if="loading" class="empty-card ds-card">
      <view class="empty-title">加载认证商家中...</view>
    </view>

    <view v-else-if="loadError" class="empty-card ds-card">
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
import { getMerchantShowcaseBanners, type HomeBannerResponse } from '../../../api/modules/home'
import { listUserRankings, type UserRankingResponse } from '../../../api/modules/ranking'

interface MerchantShowUser {
  id: number
  rank: number
  avatar: string
  avatarUrl: string
  name: string
  city: string
  giftScore: number
}

const showcaseBanners = ref<HomeBannerResponse[]>([])
const merchantRoles: readonly string[] = ['SELLER', 'BOTH']
const merchants = ref<MerchantShowUser[]>([])
const loading = ref(false)
const loadError = ref(false)
const showcaseLoadError = ref(false)
const podiumList = computed(() => {
  const top = merchants.value.slice(0, 3)
  return top.length === 3 ? [top[1]!, top[0]!, top[2]!] : top
})
const spotlightMerchants = computed(() => merchants.value.slice(0, 2))
const visibleShowcaseBanners = computed(() => showcaseBanners.value.slice(0, 3))

onMounted(() => {
  void loadShowcaseBanners()
  void loadData()
})

async function loadShowcaseBanners(): Promise<void> {
  showcaseLoadError.value = false
  try {
    showcaseBanners.value = await getMerchantShowcaseBanners()
  } catch {
    showcaseBanners.value = []
    showcaseLoadError.value = true
  }
}

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const rows = await listUserRankings('goddess', 'week', 100)
    merchants.value = rows.filter(isCertifiedMerchant).map(toMerchantShowUser)
  } catch {
    merchants.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function isCertifiedMerchant(item: UserRankingResponse): boolean {
  return merchantRoles.includes(String(item.mainRole || '').toUpperCase()) && item.videoVerified === true
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

function medalFor(rank: number): string {
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

function rankTitle(rank: number): string {
  if (rank === 1) return '榜首'
  if (rank === 2) return '第二名'
  if (rank === 3) return '第三名'
  return `第 ${rank} 名`
}

function openProfile(item: MerchantShowUser): void {
  if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少平台用户编号', icon: 'none' })
  uni.navigateTo({ url: `/pages/user/public-profile/index?userId=${item.id}` })
}

</script>

<style scoped>
.seller-page{min-height:100vh;padding-top:18rpx;padding-bottom:34rpx;background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.25),transparent 28%),radial-gradient(circle at 86% 14%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 50%,#fff5ee 100%)}
.showcase-swiper{height:520rpx;border-radius:38rpx;overflow:hidden;box-shadow:0 18rpx 42rpx rgba(97,54,38,.14)}
.showcase-empty{min-height:218rpx;padding:28rpx;border-color:rgba(255,217,189,.78);background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.95));display:flex;flex-direction:column;justify-content:center;gap:12rpx;box-shadow:0 14rpx 30rpx rgba(132,70,36,.075)}
.showcase-empty-title{color:#3a261a;font-size:28rpx;font-weight:950;letter-spacing:.2rpx}
.showcase-empty-desc{color:#8f6b57;font-size:22rpx;line-height:1.58;font-weight:650}
.goddess-top{position:relative;overflow:hidden;height:520rpx;border:0;background-color:#2f1722}
.showcase-bg{width:100%;height:100%;display:block}
.goddess-shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(18,8,18,.08) 0%,rgba(18,8,18,.06) 48%,rgba(44,23,16,.32) 100%);pointer-events:none}
.goddess-top:before{content:"";position:absolute;inset:18rpx;border:1rpx solid rgba(255,242,204,.34);border-radius:34rpx;pointer-events:none}
.podium-panel{margin-top:16rpx;padding:17rpx;border-color:rgba(255,217,189,.78);background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.95));box-shadow:0 16rpx 32rpx rgba(132,70,36,.08)}
.podium-row{position:relative;z-index:1;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));align-items:end;gap:12rpx}
.podium-item{position:relative;min-height:204rpx;padding:30rpx 10rpx 16rpx;border-radius:31rpx;background:rgba(255,255,255,.78);border:1rpx solid rgba(255,255,255,.86);display:flex;flex-direction:column;align-items:center;box-shadow:0 12rpx 26rpx rgba(132,70,36,.075);backdrop-filter:blur(12rpx)}
.podium-item.rank-1{min-height:236rpx;background:linear-gradient(180deg,rgba(255,255,255,.96),rgba(255,239,218,.90));box-shadow:0 18rpx 40rpx rgba(255,122,69,.16);transform:translateY(-8rpx)}
.rank-ribbon{position:absolute;top:10rpx;left:50%;transform:translateX(-50%);height:29rpx;padding:0 13rpx;border-radius:999rpx;background:rgba(58,38,26,.88);color:#fffaf4;font-size:17rpx;font-weight:950;line-height:29rpx;letter-spacing:.8rpx;white-space:nowrap}
.crown{margin-top:7rpx;font-size:33rpx;line-height:1;filter:drop-shadow(0 5rpx 9rpx rgba(255,122,69,.18))}
.podium-avatar{margin-top:8rpx;width:84rpx;height:84rpx;border-radius:50%;background:linear-gradient(135deg,#ef6f3f,#ffb08a);color:#fffaf4;display:flex;align-items:center;justify-content:center;font-size:34rpx;font-weight:950;border:5rpx solid rgba(255,255,255,.92);box-shadow:0 11rpx 22rpx rgba(255,122,69,.17);overflow:hidden}
.podium-avatar.image{background:#fff}
.avatar-image{width:100%;height:100%;display:block}
.podium-name{margin-top:10rpx;max-width:100%;color:#342116;font-size:22rpx;font-weight:950;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;letter-spacing:.12rpx}
.podium-score{margin-top:6rpx;color:#b85f32;font-size:19rpx;font-weight:900}
.podium-glow{margin-top:8rpx;padding:5rpx 11rpx;border-radius:999rpx;background:linear-gradient(135deg,#ef6f3f,#ff8b76);color:#fffaf4;font-size:16rpx;font-weight:950;box-shadow:0 8rpx 16rpx rgba(255,122,69,.16)}
.merchant-head{margin-top:24rpx;display:flex;align-items:center;justify-content:space-between}
.section-title{color:#342116;font-size:31rpx;font-weight:950;font-family:STSong,Songti SC,serif;letter-spacing:1.1rpx;text-shadow:0 8rpx 18rpx rgba(132,70,36,.08)}
.merchant-count{min-width:54rpx;height:44rpx;padding:0 14rpx;border-radius:999rpx;background:rgba(255,255,255,.92);border:1rpx solid rgba(255,217,189,.78);color:#df6735;display:flex;align-items:center;justify-content:center;font-size:22rpx;font-weight:950;box-shadow:0 10rpx 20rpx rgba(132,70,36,.07)}
.spotlight-row{margin-top:14rpx;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14rpx}
.spotlight-card{padding:16rpx;border-color:rgba(255,217,189,.76);background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96));display:flex;align-items:center;gap:12rpx;box-shadow:0 14rpx 28rpx rgba(132,70,36,.075);overflow:hidden}
.spotlight-avatar{width:72rpx;height:72rpx;border-radius:50%;background:linear-gradient(135deg,#ef6f3f,#ffb08a);color:#fffaf4;display:flex;align-items:center;justify-content:center;font-size:28rpx;font-weight:950;overflow:hidden;flex:0 0 auto;box-shadow:0 9rpx 18rpx rgba(255,122,69,.15)}
.spotlight-avatar.image{background:#fff}
.spotlight-main{flex:1;min-width:0;display:flex;flex-direction:column;gap:6rpx}
.spotlight-name{color:#342116;font-size:24rpx;font-weight:950;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;letter-spacing:.12rpx}
.spotlight-meta{color:#8f6b57;font-size:20rpx;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.spotlight-score{flex:0 0 auto;padding:8rpx 12rpx;border-radius:999rpx;background:rgba(255,243,231,.96);color:#df6735;font-size:20rpx;font-weight:950;border:1rpx solid rgba(255,195,150,.44)}
.empty-card{margin-top:18rpx;padding:26rpx;border-color:rgba(255,217,189,.78);background:rgba(255,255,255,.94);box-shadow:0 14rpx 28rpx rgba(132,70,36,.07)}
.empty-title{color:#342116;font-size:28rpx;font-weight:950}
.retry-btn{margin-top:18rpx;height:62rpx;line-height:62rpx;border-radius:999rpx;background:linear-gradient(135deg,#ef6f3f,#ff8b76);color:#fffaf4;border:1rpx solid rgba(239,111,63,.72);font-size:22rpx;font-weight:950;box-shadow:0 12rpx 24rpx rgba(255,122,69,.17)}
.merchant-grid{margin-top:16rpx;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx}
.merchant-card{position:relative;overflow:hidden;padding:16rpx;border-color:rgba(255,217,189,.78);background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97));box-shadow:0 16rpx 32rpx rgba(132,70,36,.08)}
.merchant-cover{height:166rpx;border-radius:30rpx;background:radial-gradient(circle at 24% 20%,rgba(255,255,255,.88),rgba(255,255,255,0) 24%),linear-gradient(135deg,#fff1e8 0%,#ffdcca 55%,#fff8ed 100%);display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden}
.merchant-cover:before{content:"";position:absolute;right:-38rpx;top:-48rpx;width:160rpx;height:160rpx;border-radius:50%;background:rgba(255,255,255,.48)}
.merchant-cover:after{content:"";position:absolute;left:-38rpx;bottom:-54rpx;width:150rpx;height:120rpx;border-radius:50%;background:rgba(239,111,63,.11);filter:blur(4rpx)}
.merchant-sparkle{position:absolute;right:18rpx;top:18rpx;color:#ef6f3f;font-size:30rpx;text-shadow:0 0 14rpx rgba(255,255,255,.95);z-index:2}
.merchant-rank{position:absolute;left:12rpx;top:12rpx;height:34rpx;padding:0 12rpx;border-radius:999rpx;background:rgba(255,255,255,.86);color:#b85f32;font-size:18rpx;font-weight:950;line-height:34rpx;z-index:2;box-shadow:0 7rpx 14rpx rgba(132,70,36,.08)}
.merchant-avatar{position:relative;z-index:1;width:108rpx;height:108rpx;border-radius:50%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#ef6f3f,#ffb08a);color:#fffaf4;font-size:40rpx;font-weight:950;border:6rpx solid rgba(255,255,255,.90);box-shadow:0 14rpx 28rpx rgba(255,122,69,.17);overflow:hidden}
.merchant-avatar.image{background:#fff}
.verified-badge{position:absolute;right:12rpx;bottom:12rpx;height:40rpx;padding:0 14rpx;border-radius:999rpx;background:rgba(58,38,26,.88);color:#fffaf4;font-size:18rpx;font-weight:900;display:flex;align-items:center;box-shadow:0 8rpx 16rpx rgba(58,38,26,.12)}
.merchant-title-row{margin-top:14rpx;display:flex;align-items:center;gap:8rpx;min-width:0}
.merchant-name{flex:1;min-width:0;color:#342116;font-size:26rpx;font-weight:950;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;letter-spacing:.1rpx}
.merchant-seal{flex-shrink:0;padding:4rpx 9rpx;border-radius:999rpx;background:rgba(255,243,231,.96);color:#df6735;font-size:17rpx;font-weight:950;border:1rpx solid rgba(255,195,150,.42)}
.merchant-meta{margin-top:6rpx;color:#8f6b57;font-size:21rpx;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.merchant-score{margin-top:10rpx;color:#df6735;font-size:24rpx;font-weight:950}
</style>
