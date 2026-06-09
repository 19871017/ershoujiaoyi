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
            v-for="groupIndex in marqueeGroups"
            :key="`ticker-group-${groupIndex}`"
            class="ticker-group"
          >
            <view
              v-for="item in items"
              :key="`${groupIndex}-${item.id}`"
              class="ticker-line"
              :class="item.kind"
            >
              <text class="ticker-kind">{{ tickerKindLabel(item.kind) }}</text>
              <text class="ticker-text">{{ item.text }}</text>
            </view>
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
import { isSafeNotificationTargetUrl, isTabBarNotificationTargetUrl } from '../pages/notification/notification-helpers'
import { useUserStore } from '../store/modules/user'
import { buildAnnouncementItems, buildGiftText, buildNoticeText, normalizeTargetUrl, type TickerItem } from './global-ticker-helpers'

const userStore = useUserStore()
const items = ref<TickerItem[]>([])
const OPTIONAL_SOURCE_COOLDOWN_MS = 5 * 60_000
type OptionalTickerSource = 'announcement' | 'gift' | 'notice'
const sourceCooldownUntil: Record<OptionalTickerSource, number> = {
  announcement: 0,
  gift: 0,
  notice: 0
}
const visible = computed(() => items.value.length > 0)
const currentItem = computed(() => {
  return items.value[0] ?? null
})
const marqueeGroups = computed(() => (items.value.length ? [0, 1] : []))
const marqueeDurationSeconds = computed(() => {
  const textLength = items.value.reduce((sum, item) => sum + item.text.length, 0)
  return Math.max(24, Math.min(54, Math.round(textLength * 0.62)))
})
const trackStyle = computed(() => ({
  '--ticker-duration': `${marqueeDurationSeconds.value}s`
}) as Record<string, string>)

let refreshTimer: ReturnType<typeof setInterval> | null = null

function applyOffset() {
  if (typeof document === 'undefined') return
  document.documentElement.style.setProperty('--global-ticker-offset', visible.value ? '76rpx' : '0rpx')
}

function clearTimers() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

function sourceInCooldown(source: OptionalTickerSource) {
  return Date.now() < sourceCooldownUntil[source]
}

async function loadOptionalTickerSource<T>(
  source: OptionalTickerSource,
  label: string,
  loader: () => Promise<T>,
  fallback: T
) {
  if (sourceInCooldown(source)) return fallback
  try {
    const result = await loader()
    sourceCooldownUntil[source] = 0
    return result
  } catch (error) {
    sourceCooldownUntil[source] = Date.now() + OPTIONAL_SOURCE_COOLDOWN_MS
    console.warn(`${label} unavailable`, error)
    return fallback
  }
}

async function loadTicker() {
  try {
    const [announcement, gifts, notifications] = await Promise.all([
      loadOptionalTickerSource('announcement', 'announcement ticker', getAnnouncementTicker, null),
      loadOptionalTickerSource('gift', 'recent gift feed', getRecentGiftFeed, []),
      userStore.token ? loadOptionalTickerSource('notice', 'ticker notifications', () => listNotifications('ALL'), []) : Promise.resolve([])
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
    const chatNoticeItems: TickerItem[] = notifications
      .filter((item) => item.type === 'CHAT' && !!buildNoticeText(item))
      .slice(0, 2)
      .map((item) => ({
        id: `notice-${item.notificationNo}`,
        kind: 'notice',
        text: buildNoticeText(item),
        targetUrl: normalizeTickerTargetUrl(item.targetUrl)
      }))
    const noticeItems: TickerItem[] = notifications
      .filter((item) => (item.type === 'SYSTEM' || item.type === 'AUDIT') && !!buildNoticeText(item))
      .slice(0, 3)
      .map((item) => ({
        id: `notice-${item.notificationNo}`,
        kind: 'notice',
        text: buildNoticeText(item),
        targetUrl: normalizeTickerTargetUrl(item.targetUrl)
      }))
    items.value = [...chatNoticeItems, ...giftItems, ...announcementItems, ...noticeItems].slice(0, 6)
    applyOffset()
  } catch (error) {
    console.warn('global ticker refresh failed', error)
    items.value = []
    applyOffset()
  }
}

function tickerKindLabel(kind: TickerItem['kind']): string {
  if (kind === 'gift') return '礼物'
  if (kind === 'announcement') return '公告'
  return '通知'
}

function openCurrentItem() {
  const targetUrl = currentItem.value?.targetUrl || '/pages/notification/index'
  const safeTargetUrl = normalizeTickerTargetUrl(targetUrl)
  const route = {
    url: safeTargetUrl,
    fail(error: unknown) {
      console.warn('global ticker navigation failed', { targetUrl: safeTargetUrl, error })
      uni.showToast({ title: '消息页面暂时无法打开，请稍后重试', icon: 'none' })
    }
  }
  try {
    if (isTabBarNotificationTargetUrl(safeTargetUrl)) uni.switchTab(route)
    else uni.navigateTo(route)
  } catch (error) {
    console.warn('global ticker navigation failed', { targetUrl: safeTargetUrl, error })
    uni.showToast({ title: '消息页面暂时无法打开，请稍后重试', icon: 'none' })
  }
}

function normalizeTickerTargetUrl(value?: string | null): string {
  const targetUrl = normalizeTargetUrl(value)
  return isSafeNotificationTargetUrl(targetUrl) ? targetUrl : '/pages/notification/index'
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
  sourceCooldownUntil.notice = 0
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
