<template>
  <view class="page-shell me-page">
    <view class="notice-entry ds-card tapable" @click="openNotification">
      <text>🔔 通知中心</text><text>订单、审核、举报处理结果 ›</text>
    </view>

    <view class="video-verify-card ds-card tapable" @click="goVideoVerify">
      <view class="video-left">
        <view class="video-icon">▶</view>
        <view>
          <view class="video-title">视频认证</view>
          <view class="video-desc">认证通过后，其他用户可在你的个人主页顶部看到“视频认证卖家”。</view>
        </view>
      </view>
      <view class="video-status">去完善</view>
    </view>

    <view class="profile-card ds-card">
      <view class="avatar">{{ avatarText }}</view>
      <view class="profile-main">
        <view class="nickname">{{ profile.nickname }}</view>
        <view class="credit">{{ profileStatusText }}</view>
        <view class="tag-row">
          <text class="mini-tag">{{ trustTagText }}</text>
          <text class="mini-tag soft">身份状态以平台审核为准</text>
        </view>
        <view class="state-tip">{{ profileMessage }}</view>
      </view>
      <view class="setting tapable" @click="goSettings">⚙︎</view>
    </view>

    <view class="quick-grid">
      <view v-for="item in quickStats" :key="item.label" class="quick-card ds-card tapable" @click="openQuick(item)">
        <view class="quick-icon">{{ item.icon }}</view>
        <view class="quick-num">{{ item.num }}</view>
        <view class="quick-label">{{ item.label }}</view>
      </view>
    </view>

    <view class="closet-card ds-card tapable" @click="goCloset">
      <view class="closet-left">
        <view class="closet-icon">👗</view>
        <view>
          <view class="closet-title">我的小原圈</view>
          <view class="closet-desc">管理在售、审核中、草稿宝贝</view>
        </view>
      </view>
      <view class="closet-go">进入</view>
    </view>

    <view class="wallet-card ds-card tapable" @click="goWallet">
      <view>
        <view class="wallet-label">钱包可用余额</view>
        <view class="wallet-value">¥{{ totalAvailable }}</view>
        <view class="wallet-sub">冻结 {{ balance.frozenBalance }} · 可提现 {{ balance.withdrawableBalance }}</view>
        <view class="state-tip">{{ walletMessage }}</view>
      </view>
      <view class="secondary-btn">查看</view>
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
          <view>
            <view class="menu-label">{{ item.label }}</view>
            <view v-if="item.desc" class="menu-desc">{{ item.desc }}</view>
          </view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getMyProfile, type UserProfileResponse } from '../../../api/modules/user'
import { getWalletBalance, type WalletBalanceResponse } from '../../../api/modules/wallet'

const emptyProfile: UserProfileResponse = { userId: 0, nickname: '小原圈用户', mainRole: 'UNVERIFIED', videoIdentityStatus: 'UNVERIFIED', videoVerified: false }
const profile = reactive<UserProfileResponse>({ ...emptyProfile })
const profileMessage = ref('身份状态以平台审核为准')
const profileLoaded = ref(false)
const emptyBalance: WalletBalanceResponse = { rechargeBalance: '--', incomeBalance: '--', frozenBalance: '--', withdrawableBalance: '--' }
const balance = reactive<WalletBalanceResponse>({ ...emptyBalance })
const walletMessage = ref('钱包余额以服务端为准')
const quickStats = computed(() => [
  { icon: '🎀', num: '--', label: '在售', action: 'closet' },
  { icon: '💗', num: '--', label: '收藏', action: 'favorite' },
  { icon: '⭐', num: profileLoaded.value ? '待计算' : '--', label: '评分', action: 'ranking' }
])
const orderStatus = computed(() => [
  { icon: '💳', label: '待付款', count: 0 },
  { icon: '📦', label: '待发货', count: 0 },
  { icon: '🧾', label: '待收货', count: 0 },
  { icon: '🌸', label: '售后', count: 0 }
])
const menus = [
  { icon: '📦', label: '我的订单', desc: '买到和卖出的宝贝', url: '/pages/order/list/index' },
  { icon: '💰', label: '钱包账本', desc: '余额、流水、提现', url: '/pages/wallet/index' },
  { icon: '🏦', label: '提现审核', desc: '查看提现进度', url: '/pages/wallet/index?tab=withdraw' },
  { icon: '💳', label: '收款账户', desc: '提现银行卡和支付宝账户', url: '/pages/wallet/accounts/index' },
  { icon: '🪪', label: '视频认证', desc: '通过后主页展示视频认证卖家', url: '/pages/user/identity/index?tab=video' },
  { icon: '📍', label: '地址管理', desc: '收货与发货信息以服务端记录为准', url: '/pages/user/address/index' },
  { icon: '🎁', label: '收到的礼物', desc: '礼物分账和收入记录', url: '/pages/gift/index' },
  { icon: '🛡️', label: '举报与风控', desc: '处理交易纠纷和举报', url: '/pages/risk/index' },
  { icon: '⚙️', label: '设置', desc: '账号安全、隐私和开发预览', url: '/pages/system/settings/index' },
  { icon: '🧭', label: '后台管理入口', desc: '审核、配置、风控', url: '/pages/admin/entry/index' }
]
const avatarText = computed(() => (profile.nickname || '原').slice(-1))
const totalAvailable = computed(() => {
  const recharge = Number(balance.rechargeBalance)
  const income = Number(balance.incomeBalance)
  return Number.isFinite(recharge + income) ? (recharge + income).toFixed(2) : '--'
})
const profileStatusText = computed(() => {
  const identityText = profile.mainRole === 'VERIFIED' ? '实名已核验' : '实名待核验'
  const videoText = profile.videoVerified ? '视频认证已通过' : `视频认证${statusLabel(profile.videoIdentityStatus)}`
  return `${identityText} · ${videoText} · 信用分待平台计算`
})
const trustTagText = computed(() => profile.videoVerified ? '视频认证卖家' : '普通用户')
function statusLabel(status: string) {
  return ({ UNVERIFIED: '未提交', PENDING: '审核中', APPROVED: '已通过', REJECTED: '未通过' } as Record<string, string>)[status] ?? '待核验'
}
async function loadProfile() {
  try {
    Object.assign(profile, await getMyProfile())
    profileLoaded.value = true
    profileMessage.value = '资料已从服务端加载，身份状态以平台审核为准'
  } catch {
    Object.assign(profile, emptyProfile)
    profileLoaded.value = false
    profileMessage.value = '资料加载失败，未展示本地认证或信用分假数据'
  }
}
async function loadWalletBalance() {
  try {
    Object.assign(balance, await getWalletBalance())
    walletMessage.value = '钱包余额以服务端为准'
  } catch {
    Object.assign(balance, emptyBalance)
    walletMessage.value = '余额加载失败，未展示本地钱包假数据'
  }
}
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function openNotification() { uni.navigateTo({ url: '/pages/notification/index' }) }
function goWallet() { uni.navigateTo({ url: '/pages/wallet/index' }) }
function goOrders() { uni.navigateTo({ url: '/pages/order/list/index' }) }
function goCloset() { uni.navigateTo({ url: '/pages/closet/index' }) }
function goSettings() { uni.navigateTo({ url: '/pages/system/settings/index' }) }
function goVideoVerify() { uni.navigateTo({ url: '/pages/user/identity/index?tab=video' }) }
function openQuick(item: { action: string; label: string }) {
  if (item.action === 'ranking') uni.navigateTo({ url: '/pages/ranking/index?tab=goddess' })
  else if (item.action === 'favorite') uni.navigateTo({ url: '/pages/favorite/index' })
  else goCloset()
}
function openMenu(item: { label: string; url?: string }) { item.url ? uni.navigateTo({ url: item.url }) : showToast(`${item.label}已打开`) }
onMounted(() => { void loadProfile(); void loadWalletBalance() })
</script>

<style scoped>
.me-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.notice-entry{margin-bottom:18rpx;padding:18rpx 22rpx;display:flex;align-items:center;justify-content:space-between;border-color:#ffd9bd;color:#3a2a1f;font-size:24rpx;font-weight:950;background:#fff;}
.video-verify-card{margin-bottom:18rpx;padding:22rpx;border-color:#ffb37c;background:linear-gradient(135deg,#fff2e4,#fffaf6);display:flex;align-items:center;justify-content:space-between;gap:16rpx;box-shadow:0 16rpx 34rpx rgba(255,122,69,.14)}
.video-left{display:flex;align-items:center;gap:16rpx;min-width:0}.video-icon{width:70rpx;height:70rpx;border-radius:50%;background:linear-gradient(135deg,#ff7a45,#ff3f8d);color:#fff;display:flex;align-items:center;justify-content:center;font-size:25rpx;font-weight:950}.video-title{color:#3a2a1f;font-size:30rpx;font-weight:950}.video-desc{margin-top:6rpx;color:#8a5f48;font-size:22rpx;line-height:1.45}.video-status{flex-shrink:0;padding:10rpx 16rpx;border-radius:999rpx;background:#fff;color:#ff3f8d;font-size:21rpx;font-weight:950}
.profile-card { padding:26rpx; display:flex; align-items:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.avatar { width:92rpx; height:92rpx; margin-right:20rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:40rpx; font-weight:950; box-shadow:0 12rpx 26rpx rgba(255,122,69,.22); }
.profile-main { flex:1; min-width:0; }
.nickname { font-size:34rpx; font-weight:950; color:#3a2a1f; }
.credit { margin-top:8rpx; color:#9b7560; font-size:24rpx; }
.state-tip { margin-top:8rpx; color:#b9856a; font-size:20rpx; line-height:1.35; }
.tag-row { margin-top:12rpx; display:flex; gap:10rpx; flex-wrap:wrap; }
.mini-tag { padding:7rpx 12rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:19rpx; font-weight:900; }
.mini-tag.soft { background:#fff; color:#ff7a45; }
.setting { width:58rpx; height:58rpx; border-radius:50%; background:#fff; color:#9b7560; display:flex; align-items:center; justify-content:center; }
.quick-grid { margin-top:18rpx; display:grid; grid-template-columns:repeat(3, 1fr); gap:14rpx; }
.quick-card { padding:18rpx 8rpx; text-align:center; border-color:#ffd9bd; }
.quick-icon { font-size:32rpx; }
.quick-num { margin-top:6rpx; font-size:30rpx; font-weight:950; color:#ff7a45; }
.quick-label { margin-top:4rpx; color:#9b7560; font-size:22rpx; }
.closet-card { margin-top:20rpx; padding:20rpx; display:flex; align-items:center; justify-content:space-between; border-color:#ffd9bd; }
.closet-left { display:flex; align-items:center; gap:16rpx; }
.closet-icon { width:72rpx; height:72rpx; border-radius:26rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; font-size:34rpx; }
.closet-title { font-size:28rpx; font-weight:950; color:#3a2a1f; }
.closet-desc { margin-top:5rpx; color:#9b7560; font-size:22rpx; }
.closet-go { padding:10rpx 18rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:21rpx; font-weight:950; }
.wallet-card { margin-top:20rpx; padding:24rpx; display:flex; justify-content:space-between; align-items:center; border-color:#ffd9bd; }
.wallet-label { color:#9b7560; font-size:23rpx; font-weight:800; }
.wallet-value { margin-top:8rpx; color:#3a2a1f; font-size:42rpx; font-weight:950; }
.wallet-sub { margin-top:8rpx; color:#b9856a; font-size:22rpx; }
.order-status { margin-top:20rpx; padding:22rpx; border-color:#ffd9bd; }
.section-head { display:flex; align-items:center; justify-content:space-between; }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.section-more { color:#ff7a45; font-size:22rpx; font-weight:900; }
.order-row { margin-top:18rpx; display:grid; grid-template-columns:repeat(4, 1fr); gap:10rpx; }
.order-item { position:relative; min-height:96rpx; border-radius:24rpx; background:#fffaf6; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6rpx; }
.order-icon { font-size:30rpx; }
.order-label { color:#7b5542; font-size:20rpx; font-weight:900; }
.badge { position:absolute; top:8rpx; right:12rpx; min-width:28rpx; height:28rpx; padding:0 7rpx; border-radius:999rpx; background:#ff3f8d; color:#fff; font-size:18rpx; line-height:28rpx; text-align:center; }
.menu-card { margin-top:20rpx; overflow:hidden; border-color:#ffd9bd; }
.menu-item { min-height:100rpx; padding:0 24rpx; border-bottom:1rpx solid #ffd9bd; display:flex; justify-content:space-between; align-items:center; color:#3a2a1f; font-weight:850; }
.menu-item:last-child { border-bottom:0; }
.menu-left { display:flex; align-items:center; gap:16rpx; }
.menu-icon { width:44rpx; text-align:center; font-size:28rpx; }
.menu-label { font-size:26rpx; font-weight:950; }
.menu-desc { margin-top:5rpx; color:#b9856a; font-size:21rpx; font-weight:650; }
.arrow { color:#d79262; font-size:34rpx; }
</style>
