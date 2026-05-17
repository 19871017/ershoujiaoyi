<template>
  <view v-if="visible" class="global-ticker-wrap">
    <view class="global-ticker ds-card tapable" :class="currentItem?.kind === 'gift' ? 'gift' : 'notice'" @click="openCurrentItem">
      <view class="ticker-speaker" aria-hidden="true">
        <view class="speaker-body" />
        <view class="speaker-mouth" />
        <view class="speaker-wave one" />
        <view class="speaker-wave two" />
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
  position: relative;
  min-height: 68rpx;
  padding: 10rpx 18rpx 10rpx 14rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
  border: 1rpx solid rgba(255, 212, 183, .96);
  border-radius: 999rpx;
  background: linear-gradient(135deg, rgba(255,255,255,.96) 0%, rgba(255,246,236,.96) 54%, rgba(255,239,226,.94) 100%);
  backdrop-filter: blur(16rpx);
  box-shadow: 0 16rpx 38rpx rgba(255, 122, 69, .16), inset 0 1rpx 0 rgba(255,255,255,.95), inset 0 -1rpx 0 rgba(255,177,93,.16);
  overflow: hidden;
}
.global-ticker::before {
  content: "";
  position: absolute;
  inset: 2rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(255,255,255,.66);
  pointer-events: none;
}
.global-ticker::after {
  content: "";
  position: absolute;
  left: -42rpx;
  top: -58rpx;
  width: 158rpx;
  height: 158rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255,255,255,.88), rgba(255,180,118,.24) 54%, rgba(255,122,69,0) 72%);
  pointer-events: none;
}
.global-ticker.gift {
  border-color: rgba(255, 151, 173, .58);
  box-shadow: 0 16rpx 38rpx rgba(255, 63, 141, .16), inset 0 1rpx 0 rgba(255,255,255,.95), inset 0 -1rpx 0 rgba(255,105,150,.16);
}
.ticker-speaker {
  position: relative;
  z-index: 1;
  width: 48rpx;
  height: 48rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #ff7a45 0%, #ffb15d 100%);
  box-shadow: 0 9rpx 18rpx rgba(255,122,69,.22), inset 0 1rpx 0 rgba(255,255,255,.48);
  flex: 0 0 auto;
}
.global-ticker.gift .ticker-speaker {
  background: linear-gradient(135deg, #ff4d8f 0%, #ff9f5f 100%);
}
.speaker-body {
  position: absolute;
  left: 10rpx;
  top: 18rpx;
  width: 10rpx;
  height: 14rpx;
  border-radius: 4rpx;
  background: #fff7ed;
}
.speaker-mouth {
  position: absolute;
  left: 19rpx;
  top: 14rpx;
  width: 16rpx;
  height: 22rpx;
  clip-path: polygon(0 28%, 100% 0, 100% 100%, 0 72%);
  background: #fff7ed;
}
.speaker-wave {
  position: absolute;
  border: 3rpx solid rgba(255,255,255,.92);
  border-left: 0;
  border-top-color: transparent;
  border-bottom-color: transparent;
  border-radius: 0 999rpx 999rpx 0;
}
.speaker-wave.one {
  right: 8rpx;
  top: 17rpx;
  width: 8rpx;
  height: 14rpx;
}
.speaker-wave.two {
  right: 4rpx;
  top: 13rpx;
  width: 14rpx;
  height: 22rpx;
  opacity: .72;
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
