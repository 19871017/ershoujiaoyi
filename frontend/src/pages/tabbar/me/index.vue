<template>
  <view class="page-shell me-page">
    <view class="notice-entry ds-card tapable" @click="openNotification">
      <text>🔔 通知中心</text>
      <text v-if="notificationUnread > 0" class="entry-badge">{{ displayCount(notificationUnread) }}</text>
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

    <view class="ops-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">运营待办</view>
          <view class="section-desc">{{ opsError || opsSummary }}</view>
        </view>
        <view class="section-more tapable" @click="refreshOperationalSummary">{{ opsLoading ? '同步中' : '刷新' }}</view>
      </view>
      <view class="ops-grid">
        <view class="ops-item tapable" @click="openNotification">
          <view class="ops-value">{{ displayCount(notificationUnread) }}</view>
          <view class="ops-label">通知未读</view>
        </view>
        <view class="ops-item tapable" @click="goSessions">
          <view class="ops-value">{{ displayCount(chatUnread) }}</view>
          <view class="ops-label">私信未读</view>
        </view>
        <view class="ops-item tapable" @click="goOrders">
          <view class="ops-value">{{ displayCount(orderTodoTotal) }}</view>
          <view class="ops-label">订单待处理</view>
        </view>
      </view>
      <view v-if="recentActionNotice" class="ops-action tapable" @click="openRecentActionNotice">
        <view class="ops-action-main">
          <view class="ops-action-kicker">最近待处理</view>
          <view class="ops-action-title">{{ recentActionNotice.title }}</view>
          <view class="ops-action-desc">{{ recentActionNotice.description }}</view>
        </view>
        <view class="ops-action-btn">{{ recentActionNotice.read ? '查看' : '处理' }}</view>
      </view>
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
        <view v-for="item in orderStatus" :key="item.label" class="order-item tapable" @click="openOrderStatus(item.key)">
          <view class="order-icon">{{ item.icon }}</view>
          <view class="order-label">{{ item.label }}</view>
          <view v-if="item.count" class="badge">{{ displayCount(item.count) }}</view>
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
import { computed, reactive, ref } from 'vue'
import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../../../api/modules/chat'
import { listNotifications, markNotificationRead, type NotificationItemResponse } from '../../../api/modules/notification'
import { listOrders, type OrderListItemResponse } from '../../../api/modules/order'
import { getMyProfile, type UserProfileResponse } from '../../../api/modules/user'
import { getWalletBalance, type WalletBalanceResponse } from '../../../api/modules/wallet'
import { assertNotificationItem, isSafeNotificationTargetUrl, isTabBarNotificationTargetUrl, isValidNotificationNo } from '../../notification/notification-helpers'
import { emptyBalance, emptyProfile, menus, orderStatusItems, publishRoles, type MeMenuItem, type OrderStatusKey } from './me-data'

const profile = reactive<UserProfileResponse>({ ...emptyProfile })
const balance = reactive<WalletBalanceResponse>({ ...emptyBalance })
const chatConversations = ref<ChatConversationItem[]>([])
const notifications = ref<NotificationItemResponse[]>([])
const buyerOrders = ref<OrderListItemResponse[]>([])
const sellerOrders = ref<OrderListItemResponse[]>([])
const opsLoading = ref(false)
const opsError = ref('')

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
  ...orderStatusItems.map((item) => ({ ...item, count: orderCountByKey(item.key) }))
])
const notificationUnread = computed(() => notifications.value.filter((item) => !item.read).length)
const recentActionNotice = computed(() => {
  const safeRows = notifications.value.filter((item) => item.targetUrl && isSafeNotificationTargetUrl(item.targetUrl))
  return safeRows.find((item) => !item.read) || safeRows[0] || null
})
const chatUnread = computed(() => chatConversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))
const pendingPayCount = computed(() => buyerOrders.value.filter((item) => item.status === 'PENDING_PAY').length)
const pendingShipCount = computed(() => sellerOrders.value.filter((item) => item.status === 'PAID').length)
const pendingReceiveCount = computed(() => buyerOrders.value.filter((item) => item.status === 'SHIPPED').length)
const buyerAfterSalesCount = computed(() => buyerOrders.value.filter((item) => hasActiveAfterSales(item)).length)
const sellerAfterSalesCount = computed(() => sellerOrders.value.filter((item) => hasActiveAfterSales(item)).length)
const afterSalesCount = computed(() => buyerAfterSalesCount.value + sellerAfterSalesCount.value)
const orderTodoTotal = computed(() => pendingPayCount.value + pendingShipCount.value + pendingReceiveCount.value + afterSalesCount.value)
const opsSummary = computed(() => {
  const total = notificationUnread.value + chatUnread.value + orderTodoTotal.value
  return total > 0 ? `你有 ${displayCount(total)} 项需要关注` : '暂无待处理事项'
})
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
async function refreshOperationalSummary() {
  if (opsLoading.value) return
  opsLoading.value = true
  opsError.value = ''
  try {
    const [noticeRows, chatRows, buyerRows, sellerRows] = await Promise.all([
      listNotifications('ALL', 50),
      getChatConversations(),
      listOrders('buyer', 'ALL'),
      listOrders('seller', 'ALL')
    ])
    assertNotificationList(noticeRows)
    assertConversationListResponse(chatRows)
    assertOrderList(buyerRows)
    assertOrderList(sellerRows)
    notifications.value = noticeRows
    chatConversations.value = chatRows.conversations
    buyerOrders.value = buyerRows
    sellerOrders.value = sellerRows
  } catch (error) {
    notifications.value = []
    chatConversations.value = []
    buyerOrders.value = []
    sellerOrders.value = []
    opsError.value = '运营待办暂时不可用，请稍后刷新'
    console.warn('me operational summary load failed', { error })
  } finally {
    opsLoading.value = false
  }
}
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function openNotification() { uni.navigateTo({ url: '/pages/notification/index' }) }
async function openRecentActionNotice() {
  const item = recentActionNotice.value
  if (!item) return openNotification()
  if (!isValidNotificationNo(item.notificationNo)) return uni.showToast({ title: '通知编号无效，未打开待办', icon: 'none' })
  if (!item.targetUrl || !isSafeNotificationTargetUrl(item.targetUrl)) return uni.showToast({ title: '通知跳转地址无效，未打开页面', icon: 'none' })
  try {
    const read = await markNotificationRead(item.notificationNo)
    assertNotificationItem(read)
    if (read.notificationNo !== item.notificationNo) throw new Error('me notification read response mismatch')
    notifications.value = notifications.value.map((notice) => notice.notificationNo === read.notificationNo ? read : notice)
  } catch (error) {
    console.warn('me notification read mutation failed', { notificationNo: item.notificationNo, error })
    uni.showToast({ title: '已读状态暂时无法更新，请稍后重试', icon: 'none' })
    return
  }
  navigateToNoticeTarget(item)
}
function navigateToNoticeTarget(item: NotificationItemResponse): void {
  if (!item.targetUrl || !isSafeNotificationTargetUrl(item.targetUrl)) return uni.showToast({ title: '通知跳转地址无效，未打开页面', icon: 'none' })
  const route = {
    url: item.targetUrl,
    fail(error: unknown) {
      console.warn('me notification target navigation failed', { notificationNo: item.notificationNo, targetUrl: item.targetUrl, error })
      uni.showToast({ title: '通知页面暂时无法打开，请稍后重试', icon: 'none' })
    }
  }
  try {
    if (isTabBarNotificationTargetUrl(item.targetUrl)) uni.switchTab(route)
    else uni.navigateTo(route)
  } catch (error) {
    console.warn('me notification target navigation failed', { notificationNo: item.notificationNo, targetUrl: item.targetUrl, error })
    uni.showToast({ title: '通知页面暂时无法打开，请稍后重试', icon: 'none' })
  }
}
function goSessions() { uni.navigateTo({ url: '/pages/chat/session-list/index' }) }
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
function openMenu(item: MeMenuItem) {
  if (item.key === 'afterSales') {
    openOrderStatus('afterSales')
    return
  }
  item.url ? uni.navigateTo({ url: item.url }) : showToast(`${item.label}已打开`)
}
function openOrderStatus(key: OrderStatusKey) {
  const filters: Record<OrderStatusKey, { role: 'buyer' | 'seller'; status: 'PENDING_PAY' | 'PAID' | 'SHIPPED' | 'REFUNDING' }> = {
    pendingPay: { role: 'buyer', status: 'PENDING_PAY' },
    pendingShip: { role: 'seller', status: 'PAID' },
    pendingReceive: { role: 'buyer', status: 'SHIPPED' },
    afterSales: {
      role: sellerAfterSalesCount.value > 0 && buyerAfterSalesCount.value === 0 ? 'seller' : 'buyer',
      status: 'REFUNDING'
    }
  }
  const target = filters[key]
  uni.navigateTo({ url: `/pages/order/list/index?role=${target.role}&status=${target.status}` })
}
function orderCountByKey(key: OrderStatusKey): number {
  if (key === 'pendingPay') return pendingPayCount.value
  if (key === 'pendingShip') return pendingShipCount.value
  if (key === 'pendingReceive') return pendingReceiveCount.value
  if (key === 'afterSales') return afterSalesCount.value
  return 0
}
function displayCount(value: number): string {
  const safe = Math.max(0, Math.floor(Number(value) || 0))
  return safe > 99 ? '99+' : String(safe)
}
function hasActiveAfterSales(item: OrderListItemResponse): boolean {
  const status = String(item.afterSalesStatus || '').toUpperCase()
  return !!item.afterSalesNo && status !== 'APPROVED' && status !== 'REJECTED' && status !== 'CANCELLED'
}
function isValidBackendId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}
function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse {
  if (!value || typeof value !== 'object') throw new Error('me invalid chat conversations response')
  const response = value as ChatConversationListResponse
  if (!Array.isArray(response.conversations)) throw new Error('me invalid chat conversations')
  for (const item of response.conversations) {
    if (!isValidBackendId(item.conversationId) || !isValidBackendId(item.peerUserId)) throw new Error('me invalid chat ids')
    if (!Number.isSafeInteger(item.unreadCount) || item.unreadCount < 0) throw new Error('me invalid chat unread')
  }
}
function assertNotificationList(value: unknown): asserts value is NotificationItemResponse[] {
  if (!Array.isArray(value)) throw new Error('me invalid notification list')
  for (const item of value) {
    assertNotificationItem(item)
  }
}
function assertOrderList(value: unknown): asserts value is OrderListItemResponse[] {
  if (!Array.isArray(value)) throw new Error('me invalid order list')
  for (const item of value) {
    if (!item || typeof item !== 'object') throw new Error('me invalid order item')
    if (typeof item.orderNo !== 'string' || !item.orderNo.trim()) throw new Error('me invalid orderNo')
    if (item.status !== 'PENDING_PAY' && item.status !== 'PAID' && item.status !== 'SHIPPED' && item.status !== 'COMPLETED') throw new Error('me invalid order status')
  }
}

onShow(() => {
  void loadProfile()
  void loadWalletBalance()
  void refreshOperationalSummary()
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
