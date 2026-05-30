<template>
  <view class="page-shell notice-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 消息通知</view>
        <view class="page-title">通知中心</view>
        <view class="page-desc">订单、私信、审核和举报处理结果会集中显示。</view>
      </view>
      <view class="hero-icon">🔔</view>
    </view>
    <view class="tab-row">
      <view v-for="item in tabs" :key="item.value" class="chip tapable" :class="{ active: active === item.value }" @click="switchTab(item.value)">{{ item.label }}</view>
    </view>
    <view v-if="loading" class="empty-card ds-card">通知加载中...</view>
    <view v-else-if="loadError" class="empty-card ds-card">
      <view>{{ loadError }}</view>
      <view class="retry-btn tapable" @click="loadNotifications">重新加载</view>
    </view>
    <view v-else-if="filtered.length === 0" class="empty-card ds-card">暂无通知</view>
    <view v-else class="notice-list">
      <view v-for="item in filtered" :key="item.notificationNo" class="notice-card ds-card tapable" @click="openNotice(item)">
        <view class="notice-icon">{{ iconFor(item.type) }}</view>
        <view class="main">
          <view class="title-row">
            <text class="title">{{ item.title }}</text>
            <text v-if="!item.read" class="dot">未读</text>
          </view>
          <view class="desc">{{ item.description }}</view>
          <view class="time">{{ formatTime(item.createdAt) }}</view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listNotifications, markNotificationRead, type NotificationItemResponse } from '../../api/modules/notification'
import {
  assertNotificationItem,
  assertNotificationList,
  filterNotifications,
  formatTime,
  iconFor,
  isSafeNotificationTargetUrl,
  isValidNotificationNo,
  tabs,
  type NoticeType
} from './notification-helpers'

const active = ref<NoticeType>('ALL')
const notices = ref<NotificationItemResponse[]>([])
const loading = ref(false)
const loadError = ref('')
const filtered = computed(() => filterNotifications(notices.value, active.value))

async function loadNotifications() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listNotifications(active.value)
    assertNotificationList(response)
    notices.value = response
  } catch (error) {
    notices.value = []
    console.warn('notification list load failed', { type: active.value, error })
    loadError.value = '通知暂时不可用，请稍后刷新'
  } finally { loading.value = false }
}
function switchTab(type: NoticeType) {
  active.value = type
  void loadNotifications()
}

async function openNotice(item: NotificationItemResponse) {
  if (!isValidNotificationNo(item.notificationNo)) {
    uni.showToast({ title: '通知编号无效，未更新已读状态', icon: 'none' })
    return
  }
  try {
    const read = await markNotificationRead(item.notificationNo)
    assertNotificationItem(read)
    if (read.notificationNo !== item.notificationNo) throw new Error('notification read response mismatch')
    notices.value = notices.value.map((notice) => notice.notificationNo === read.notificationNo ? read : notice)
  } catch (error) {
    console.warn('notification read mutation failed', { notificationNo: item.notificationNo, error })
    uni.showToast({ title: '已读状态暂时无法更新，请稍后重试', icon: 'none' })
    return
  }
  if (item.targetUrl && isSafeNotificationTargetUrl(item.targetUrl)) {
    navigateToNotificationTarget(item)
  } else if (item.targetUrl) {
    uni.showToast({ title: '通知跳转地址无效，未打开页面', icon: 'none' })
  }
}
function navigateToNotificationTarget(item: NotificationItemResponse): void {
  const route = {
    url: item.targetUrl || '',
    fail(error: unknown) {
      console.warn('notification target navigation failed', { notificationNo: item.notificationNo, targetUrl: item.targetUrl, error })
      uni.showToast({ title: '通知页面暂时无法打开，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('notification target navigation failed', { notificationNo: item.notificationNo, targetUrl: item.targetUrl, error })
    uni.showToast({ title: '通知页面暂时无法打开，请稍后重试', icon: 'none' })
  }
}
onMounted(() => { void loadNotifications() })
</script>
<style scoped lang="scss" src="./style.scss"></style>
