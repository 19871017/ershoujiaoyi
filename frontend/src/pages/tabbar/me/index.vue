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
import { emptyBalance, emptyProfile, menus, orderStatusItems, publishRoles } from './me-data'

const profile = reactive<UserProfileResponse>({ ...emptyProfile })
const balance = reactive<WalletBalanceResponse>({ ...emptyBalance })

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
  ...orderStatusItems
])
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

<style scoped lang="scss" src="./style.scss"></style>
