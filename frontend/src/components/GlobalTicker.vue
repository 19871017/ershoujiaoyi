<template>
  <view v-if="visible" class="global-ticker-wrap">
    <view class="global-ticker ds-card tapable" :class="{ gift: currentItem?.kind === 'gift' }" @click="openCurrentItem">
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
            <text class="ticker-text">{{ item.text }}</text>
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
import { listNotifications } from '../api/modules/notification'
import { useUserStore } from '../store/modules/user'
import { buildAnnouncementItems, buildGiftText, buildNoticeText, normalizeTargetUrl, type TickerItem } from './global-ticker-helpers'

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

async function loadTicker() {
  try {
    const [announcement, gifts, notifications] = await Promise.all([
      getAnnouncementTicker().catch((error) => {
        console.warn('announcement ticker unavailable', error)
        return null
      }),
      getRecentGiftFeed().catch((error) => {
        console.warn('recent gift feed unavailable', error)
        return []
      }),
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
        text: buildGiftText(item),
        targetUrl: item.receiverId ? `/pages/user/public-profile/index?userId=${item.receiverId}` : '/pages/gift/index'
      }))
    const noticeItems: TickerItem[] = notifications
      .filter((item) => (item.type === 'SYSTEM' || item.type === 'AUDIT') && !!buildNoticeText(item))
      .slice(0, 4)
      .map((item) => ({
        id: `notice-${item.notificationNo}`,
        kind: 'notice',
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

<style scoped lang="scss" src="./global-ticker.scss"></style>
