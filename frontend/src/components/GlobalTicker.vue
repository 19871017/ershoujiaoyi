<template>
  <view v-if="visible" class="global-ticker-wrap">
    <view class="global-ticker ds-card tapable" @click="openCurrentItem">
      <view class="ticker-icon" :class="currentItem?.kind === 'gift' ? 'gift' : 'notice'">
        {{ currentItem?.icon }}
      </view>
      <view class="ticker-marquee">
        <view class="ticker-track" :style="trackStyle">
          <view
            v-for="item in renderedItems"
            :key="item.id"
            class="ticker-line"
          >
            {{ item.text }}
          </view>
        </view>
      </view>
      <view class="ticker-arrow">›</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getAnnouncementTicker } from '../api/modules/announcement'
import { getRecentGiftFeed } from '../api/modules/gift'
import { listNotifications, type NotificationItemResponse } from '../api/modules/notification'
import { useUserStore } from '../store/modules/user'

type TickerItem = {
  id: string
  kind: 'announcement' | 'gift' | 'notice'
  icon: string
  text: string
  targetUrl?: string
}

const DEFAULT_ANNOUNCEMENT_ICON = '📣'
const DEFAULT_ANNOUNCEMENT_TARGET_URL = '/pages/notification/index'
const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true'
const userStore = useUserStore()
const items = ref<TickerItem[]>([])
const currentIndex = ref(0)
const visible = computed(() => items.value.length > 0)
const renderedItems = computed(() => {
  if (!items.value.length) return []
  if (items.value.length === 1) return items.value
  return [...items.value, items.value[0]!]
})
const currentItem = computed(() => {
  if (!items.value.length) return null
  return items.value[currentIndex.value % items.value.length] ?? items.value[0]!
})
const trackStyle = computed(() => ({
  transform: `translateY(-${currentIndex.value * 32}rpx)`,
  transition: items.value.length > 1 ? 'transform .42s ease' : 'none'
}))

let rotateTimer: ReturnType<typeof setInterval> | null = null
let resetTimer: ReturnType<typeof setTimeout> | null = null
let refreshTimer: ReturnType<typeof setInterval> | null = null

function applyOffset() {
  if (typeof document === 'undefined') return
  document.documentElement.style.setProperty('--global-ticker-offset', visible.value ? '76rpx' : '0rpx')
}

function clearRotationTimers() {
  if (rotateTimer) clearInterval(rotateTimer)
  if (resetTimer) clearTimeout(resetTimer)
  rotateTimer = null
  resetTimer = null
}

function clearTimers() {
  clearRotationTimers()
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

function startRotation() {
  clearRotationTimers()
  if (items.value.length <= 1) return
  rotateTimer = setInterval(() => {
    currentIndex.value += 1
    if (currentIndex.value >= items.value.length) {
      resetTimer = setTimeout(() => {
        currentIndex.value = 0
      }, 450)
    }
  }, 3200)
}

function buildGiftText(item: { senderName: string; receiverName: string; giftName: string; quantity?: number }) {
  const quantity = Math.max(1, Number(item.quantity || 1))
  return `${item.senderName} 送给 ${item.receiverName} ${item.giftName}${quantity > 1 ? ` ×${quantity}` : ''}`
}

function buildNoticeText(item: NotificationItemResponse) {
  return item.title?.trim() || item.description?.trim() || '你有一条新通知'
}

function normalizeTargetUrl(url?: string | null) {
  if (!url) return ''
  const value = url.trim()
  return value.startsWith('/') ? value : ''
}

function buildAnnouncementItems(announcement: Awaited<ReturnType<typeof getAnnouncementTicker>> | null) {
  if (!announcement?.enabled) return []
  const text = announcement.text?.trim()
  if (!text) return []
  return [{
    id: `announcement-${announcement.updatedAt || text}`,
    kind: 'announcement' as const,
    icon: announcement.icon || DEFAULT_ANNOUNCEMENT_ICON,
    text,
    targetUrl: normalizeTargetUrl(announcement.targetUrl) || DEFAULT_ANNOUNCEMENT_TARGET_URL
  }]
}

async function loadTicker() {
  try {
    const [announcement, gifts, notifications] = await Promise.all([
      getAnnouncementTicker().catch((error) => {
        console.warn('announcement ticker unavailable', error)
        return null
      }),
      (userStore.token || ENABLE_MOCK_DATA) ? getRecentGiftFeed().catch((error) => {
        console.warn('recent gift feed unavailable', error)
        return []
      }) : Promise.resolve([]),
      userStore.token ? listNotifications('ALL').catch((error) => {
        console.warn('ticker notifications unavailable', error)
        return []
      }) : Promise.resolve([])
    ])
    const announcementItems: TickerItem[] = buildAnnouncementItems(announcement)
    const giftItems: TickerItem[] = gifts
      .filter((item) => !!item.giftOrderNo && !!item.senderName && !!item.receiverName && !!item.giftName)
      .slice(0, 4)
      .map((item) => ({
        id: `gift-${item.giftOrderNo}`,
        kind: 'gift',
        icon: item.giftIcon || '🎁',
        text: buildGiftText(item),
        targetUrl: item.receiverId ? `/pages/user/public-profile/index?userId=${item.receiverId}` : '/pages/gift/index'
      }))
    const noticeItems: TickerItem[] = notifications
      .filter((item) => (item.type === 'SYSTEM' || item.type === 'AUDIT') && !!buildNoticeText(item))
      .slice(0, 4)
      .map((item) => ({
        id: `notice-${item.notificationNo}`,
        kind: 'notice',
        icon: '🔔',
        text: buildNoticeText(item),
        targetUrl: normalizeTargetUrl(item.targetUrl) || '/pages/notification/index'
      }))
    items.value = [...giftItems, ...announcementItems, ...noticeItems].slice(0, 6)
    currentIndex.value = 0
    applyOffset()
    startRotation()
  } catch (error) {
    console.warn('global ticker refresh failed', error)
    items.value = []
    currentIndex.value = 0
    applyOffset()
    clearRotationTimers()
  }
}

function openCurrentItem() {
  const targetUrl = currentItem.value?.targetUrl || '/pages/notification/index'
  if (!targetUrl.startsWith('/')) return
  uni.navigateTo({ url: targetUrl })
}

onMounted(() => {
  applyOffset()
  void loadTicker()
  refreshTimer = setInterval(() => {
    void loadTicker()
  }, 60_000)
})

watch(visible, applyOffset)
watch(() => userStore.token, () => {
  void loadTicker()
})

onBeforeUnmount(() => {
  clearTimers()
  if (typeof document !== 'undefined') {
    document.documentElement.style.setProperty('--global-ticker-offset', '0rpx')
  }
})
</script>

<style scoped>
.global-ticker-wrap {
  position: fixed;
  left: 18rpx;
  right: 18rpx;
  top: calc(env(safe-area-inset-top) + 12rpx);
  z-index: 999;
}
.global-ticker {
  min-height: 64rpx;
  padding: 10rpx 16rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  border-color: rgba(255, 217, 189, .94);
  background: rgba(255, 250, 244, .94);
  backdrop-filter: blur(14rpx);
  box-shadow: 0 14rpx 34rpx rgba(255, 122, 69, .14);
}
.ticker-icon {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  flex: 0 0 auto;
}
.ticker-icon.gift {
  background: linear-gradient(135deg, #ff7a45, #ff4d8f);
}
.ticker-icon.notice {
  background: linear-gradient(135deg, #ffb15d, #ff7a45);
}
.ticker-marquee {
  flex: 1;
  min-width: 0;
  height: 32rpx;
  overflow: hidden;
}
.ticker-track {
  display: flex;
  flex-direction: column;
}
.ticker-line {
  height: 32rpx;
  line-height: 32rpx;
  color: #5a3526;
  font-size: 22rpx;
  font-weight: 900;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ticker-arrow {
  color: #d79262;
  font-size: 28rpx;
  font-weight: 900;
}
</style>
