<template>
  <view class="page-shell me-page">
    <view class="notice-entry ds-card tapable" @click="openNotification">
      <text>🔔 通知中心</text>
      <text>›</text>
    </view>

    <view class="profile-card ds-card tapable" @click="goProfile">
      <view class="avatar" :class="{ image: !!profile.avatarUrl }">
        <image v-if="profile.avatarUrl" class="avatar-image" :src="profile.avatarUrl" mode="aspectFill" />
        <text v-else>{{ avatarText }}</text>
      </view>
      <view class="profile-main">
        <view class="nickname">{{ profile.nickname }}</view>
        <view class="id-line">{{ genderSymbol }} {{ profile.userNo || '小原圈号待生成' }}</view>
        <view class="tag-row">
          <text class="mini-tag">{{ trustTagText }}</text>
        </view>
      </view>
      <view class="setting">编辑</view>
    </view>

    <view class="wallet-card ds-card tapable" @click="goWallet">
      <view>
        <view class="wallet-label">钱包</view>
        <view class="wallet-value">¥{{ totalAvailable }}</view>
      </view>
      <view class="secondary-btn">查看</view>
    </view>

    <view v-if="canPublish" class="seller-entry-card ds-card tapable" @click="goPublishForm">
      <view class="seller-entry-main">
        <view class="seller-entry-title">我要上新</view>
      </view>
      <view class="seller-entry-action">去发布</view>
    </view>

    <view class="order-status ds-card">
      <view class="section-head">
        <view class="section-title">我的订单</view>
        <view class="section-more tapable" @click="goOrders">全部订单 ›</view>
      </view>
      <view class="order-row">
        <view v-for="item in orderStatus" :key="item.label" class="order-item tapable" @click="goOrders">
          <view class="order-icon">{{ item.icon }}</view>
          <view class="order-label">{{ item.label }}</view>
          <view v-if="item.count" class="badge">{{ item.count }}</view>
        </view>
      </view>
    </view>

    <view class="menu-card ds-card">
      <view v-for="item in menus" :key="item.label" class="menu-item tapable" @click="openMenu(item)">
        <view class="menu-left">
          <text class="menu-icon">{{ item.icon }}</text>
          <view class="menu-label">{{ item.label }}</view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="seller-verify-fab tapable" @click="goVideoVerify">
      <view class="seller-fab-icon">▶</view>
      <view class="seller-fab-copy">
        <view class="seller-fab-title">{{ sellerEntryTitleText }}</view>
        <view class="seller-fab-status">{{ sellerEntryStatusText }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, onMounted, reactive, ref } from 'vue'
import { getMyProfile, type UserProfileResponse } from '../../../api/modules/user'
import { getWalletBalance, type WalletBalanceResponse } from '../../../api/modules/wallet'

const emptyProfile: UserProfileResponse = {
  userId: 0,
  userNo: '',
  nickname: '小原圈用户',
  avatarUrl: '',
  gender: 'goddess',
  mainRole: 'BUYER',
  videoIdentityStatus: 'UNVERIFIED',
  videoVerified: false
}
const profile = reactive<UserProfileResponse>({ ...emptyProfile })
const emptyBalance: WalletBalanceResponse = { rechargeBalance: '--', incomeBalance: '--', frozenBalance: '--', withdrawableBalance: '--' }
const balance = reactive<WalletBalanceResponse>({ ...emptyBalance })

const publishRoles = ['SELLER', 'BOTH']
const canPublish = computed(() => profile.videoVerified && publishRoles.includes(String(profile.mainRole || '').toUpperCase()))
const genderSymbol = computed(() => String(profile.gender || '').toLowerCase() === 'god' ? '♂' : '♀')
const sellerEntryTitleText = computed(() => canPublish.value ? '卖家认证' : '申请卖家认证')
const sellerEntryStatusText = computed(() => {
  const status = String(profile.videoIdentityStatus || 'UNVERIFIED').toUpperCase()
  if (canPublish.value) return '已认证卖家'
  if (status === 'PENDING') return '卖家认证审核中'
  if (status === 'REJECTED') return '认证未通过，可重新申请'
  return '去申请'
})
const orderStatus = computed(() => [
  { icon: '💳', label: '待付款', count: 0 },
  { icon: '📦', label: '待发货', count: 0 },
  { icon: '🧾', label: '待收货', count: 0 },
  { icon: '🌸', label: '售后', count: 0 }
])
const menus = [
  { icon: '👤', label: '编辑资料', url: '/pages/user/profile/index' },
  { icon: '📦', label: '我的订单', url: '/pages/order/list/index' },
  { icon: '💰', label: '钱包账本', url: '/pages/wallet/index' },
  { icon: '🏦', label: '提现审核', url: '/pages/wallet/index?tab=withdraw' },
  { icon: '💳', label: '收款账户', url: '/pages/wallet/accounts/index' },
  { icon: '📍', label: '地址管理', url: '/pages/user/address/index' },
  { icon: '🎁', label: '收到的礼物', url: '/pages/gift/index' },
  { icon: '🛡️', label: '举报与风控', url: '/pages/risk/index' },
  { icon: '⚙️', label: '设置', url: '/pages/system/settings/index' }
]
const avatarText = computed(() => (profile.nickname || '原').slice(0, 1))
const totalAvailable = computed(() => {
  const recharge = Number(balance.rechargeBalance)
  const income = Number(balance.incomeBalance)
  return Number.isFinite(recharge + income) ? (recharge + income).toFixed(2) : '--'
})
const trustTagText = computed(() => canPublish.value ? '已认证卖家' : '普通买家')

async function loadProfile() {
  try {
    Object.assign(profile, await getMyProfile())
  } catch {
    Object.assign(profile, emptyProfile)
  }
}
async function loadWalletBalance() {
  try {
    Object.assign(balance, await getWalletBalance())
  } catch {
    Object.assign(balance, emptyBalance)
  }
}
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function openNotification() { uni.navigateTo({ url: '/pages/notification/index' }) }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }) }
function goOrders() { uni.navigateTo({ url: '/pages/order/list/index' }) }
function goProfile() { uni.navigateTo({ url: '/pages/user/profile/index' }) }
function goVideoVerify() { uni.navigateTo({ url: '/pages/user/identity/index?tab=video' }) }
function goPublishForm() {
  if (!canPublish.value) {
    uni.showToast({ title: '请先完成卖家认证', icon: 'none' })
    uni.navigateTo({ url: '/pages/user/identity/index?tab=video' })
    return
  }
  uni.navigateTo({ url: '/pages/product/publish/index' })
}
function openMenu(item: { label: string; url?: string }) { item.url ? uni.navigateTo({ url: item.url }) : showToast(`${item.label}已打开`) }

onMounted(() => {
  void loadWalletBalance()
})

onShow(() => {
  void loadProfile()
})
</script>

<style scoped>
.me-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.notice-entry{margin-top:14rpx;margin-bottom:14rpx;padding:14rpx 18rpx;display:flex;align-items:center;justify-content:space-between;border-color:#ffd9bd;color:#3a2a1f;font-size:22rpx;font-weight:950;background:#fff;}
.profile-card { padding:20rpx; display:flex; align-items:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.avatar { width:76rpx; height:76rpx; margin-right:16rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:34rpx; font-weight:950; box-shadow:0 8rpx 20rpx rgba(255,122,69,.18); overflow:hidden; }
.avatar.image { background:#fff; }
.avatar-image { width:100%; height:100%; display:block; }
.profile-main { flex:1; min-width:0; }
.nickname { font-size:30rpx; font-weight:950; color:#3a2a1f; }
.id-line { margin-top:6rpx; color:#b9856a; font-size:20rpx; font-weight:800; }
.tag-row { margin-top:9rpx; display:flex; gap:8rpx; flex-wrap:wrap; }
.mini-tag { padding:6rpx 10rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:18rpx; font-weight:900; }
.setting { flex-shrink:0; padding:8rpx 14rpx; border-radius:999rpx; background:#fff; color:#9b7560; font-size:20rpx; font-weight:950; }
.wallet-card { margin-top:14rpx; padding:18rpx; display:flex; justify-content:space-between; align-items:center; border-color:#ffd9bd; }
.wallet-label { color:#9b7560; font-size:21rpx; font-weight:800; }
.wallet-value { margin-top:5rpx; color:#3a2a1f; font-size:34rpx; font-weight:950; }
.order-status { margin-top:14rpx; padding:16rpx; border-color:#ffd9bd; }
.section-head { display:flex; align-items:center; justify-content:space-between; }
.section-title { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.section-more { color:#ff7a45; font-size:21rpx; font-weight:900; }
.order-row { margin-top:12rpx; display:grid; grid-template-columns:repeat(4, 1fr); gap:8rpx; }
.order-item { position:relative; min-height:78rpx; border-radius:20rpx; background:#fffaf6; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:4rpx; }
.order-icon { font-size:26rpx; }
.order-label { color:#7b5542; font-size:19rpx; font-weight:900; }
.badge { position:absolute; top:6rpx; right:9rpx; min-width:26rpx; height:26rpx; padding:0 6rpx; border-radius:999rpx; background:#ff3f8d; color:#fff; font-size:17rpx; line-height:26rpx; text-align:center; }
.menu-card { margin-top:14rpx; overflow:hidden; border-color:#ffd9bd; }
.menu-item { min-height:82rpx; padding:0 18rpx; border-bottom:1rpx solid #ffd9bd; display:flex; justify-content:space-between; align-items:center; color:#3a2a1f; font-weight:850; }
.menu-item:last-child { border-bottom:0; }
.menu-left { display:flex; align-items:center; gap:12rpx; }
.menu-icon { width:38rpx; text-align:center; font-size:25rpx; }
.menu-label { font-size:24rpx; font-weight:950; }
.arrow { color:#d79262; font-size:30rpx; }
.seller-verify-fab{position:fixed;right:22rpx;bottom:calc(132rpx + env(safe-area-inset-bottom));z-index:30;min-width:206rpx;max-width:280rpx;padding:12rpx 16rpx 12rpx 12rpx;border-radius:999rpx;background:rgba(255,122,69,.72);backdrop-filter:blur(18rpx);box-shadow:0 14rpx 30rpx rgba(255,122,69,.22),inset 0 0 0 1rpx rgba(255,255,255,.42);display:flex;align-items:center;gap:10rpx;color:#fff;}
.seller-fab-icon{width:42rpx;height:42rpx;border-radius:50%;background:rgba(255,255,255,.28);display:flex;align-items:center;justify-content:center;font-size:17rpx;font-weight:950;flex:0 0 auto;}
.seller-fab-copy{min-width:0;}
.seller-fab-title{font-size:22rpx;font-weight:950;line-height:1.05;}
.seller-fab-status{margin-top:4rpx;max-width:196rpx;font-size:16rpx;font-weight:850;line-height:1.05;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;opacity:.92;}
</style>
