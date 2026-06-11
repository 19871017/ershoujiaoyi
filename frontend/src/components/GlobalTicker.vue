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
import { getRecentGiftFeed } from '../api/modules/gift'
import { buildGiftText, normalizeTargetUrl, type TickerItem } from './global-ticker-helpers'

const TICKER_HIDDEN_ROUTE_PREFIXES = [
  '/pages/chat/conversation/index',
  '/pages/chat/session-list/index'
] as const
const routeChangeEventName = 'xiaoyuanquan:routechange'
const items = ref<TickerItem[]>([])
const routePath = ref('')
const OPTIONAL_SOURCE_COOLDOWN_MS = 5 * 60_000
type OptionalTickerSource = 'gift'
const sourceCooldownUntil: Record<OptionalTickerSource, number> = {
  gift: 0
}
const tickerHiddenOnRoute = computed(() => isTickerHiddenRoute(routePath.value))
const visible = computed(() => items.value.length > 0 && !tickerHiddenOnRoute.value)
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

function isTickerHiddenRoute(path: string): boolean {
  return TICKER_HIDDEN_ROUTE_PREFIXES.some((prefix) => path.startsWith(prefix))
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function decodeRouteVariants(value: string): string[] {
  const variants = new Set<string>()
  let current = value
  for (let index = 0; index < 3; index += 1) {
    if (!current || variants.has(current)) break
    variants.add(current)
    const decoded = safeDecode(current)
    if (decoded === current) break
    current = decoded
  }
  return Array.from(variants)
}

function extractRoutePath(value: string): string {
  const decoded = safeDecode(value.trim())
  const hashIndex = decoded.indexOf('#')
  const routedValue = hashIndex >= 0 ? decoded.slice(hashIndex + 1) : decoded
  const normalized = normalizeTargetUrl(routedValue.replace(/^#/, ''))
  return normalized.split('?')[0] || ''
}

function currentRouteCandidates(): string[] {
  if (typeof window === 'undefined') return []
  const candidates = new Set<string>()
  const rawValues = [
    window.location.hash.replace(/^#/, ''),
    window.location.pathname,
    `${window.location.pathname}${window.location.search}${window.location.hash}`
  ]
  const params = new URLSearchParams(window.location.search)
  const hashQuery = window.location.hash.split('?')[1]
  if (hashQuery) {
    new URLSearchParams(hashQuery).forEach((value, key) => {
      if (key.toLowerCase().includes('redirect')) rawValues.push(value)
    })
  }
  params.forEach((value, key) => {
    if (key.toLowerCase().includes('redirect')) rawValues.push(value)
  })
  rawValues.forEach((rawValue) => {
    decodeRouteVariants(rawValue).forEach((variant) => {
      const routePath = extractRoutePath(variant)
      if (routePath) candidates.add(routePath)
    })
  })
  return Array.from(candidates)
}

function currentRoutePath(): string {
  const candidates = currentRouteCandidates()
  return candidates.find(isTickerHiddenRoute) || candidates[0] || ''
}

function syncRoutePath() {
  routePath.value = currentRoutePath()
  applyOffset()
}

function currentRouteIsTickerHidden(): boolean {
  return currentRouteCandidates().some(isTickerHiddenRoute)
}

function notifyRouteChanged() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(routeChangeEventName))
}

function installRouteChangeHooks() {
  if (typeof window === 'undefined') return
  const historyApi = window.history as History & {
    __xiaoyuanquanTickerHooked?: boolean
  }
  if (historyApi.__xiaoyuanquanTickerHooked) return
  const originalPushState = historyApi.pushState
  const originalReplaceState = historyApi.replaceState
  historyApi.pushState = function patchedPushState(...args) {
    const result = originalPushState.apply(this, args)
    notifyRouteChanged()
    return result
  }
  historyApi.replaceState = function patchedReplaceState(...args) {
    const result = originalReplaceState.apply(this, args)
    notifyRouteChanged()
    return result
  }
  historyApi.__xiaoyuanquanTickerHooked = true
}

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
  syncRoutePath()
  if (currentRouteIsTickerHidden()) {
    items.value = []
    applyOffset()
    return
  }
  try {
    const gifts = await loadOptionalTickerSource('gift', 'recent gift feed', getRecentGiftFeed, [])
    const giftItems: TickerItem[] = gifts
      .filter((item) => !!item.giftOrderNo && !!item.senderName && !!item.receiverName && !!item.giftName)
      .slice(0, 6)
      .map((item) => ({
        id: `gift-${item.giftOrderNo}`,
        kind: 'gift',
        text: buildGiftText(item),
        targetUrl: item.receiverId ? `/pages/user/public-profile/index?userId=${item.receiverId}` : '/pages/gift/index'
      }))
    items.value = giftItems
    applyOffset()
  } catch (error) {
    console.warn('global ticker refresh failed', error)
    items.value = []
    applyOffset()
  }
}

function tickerKindLabel(_kind: TickerItem['kind']): string {
  return '礼物'
}

function openCurrentItem() {
  const targetUrl = currentItem.value?.targetUrl || '/pages/gift/index'
  const safeTargetUrl = normalizeTickerTargetUrl(targetUrl)
  const route = {
    url: safeTargetUrl,
    fail(error: unknown) {
      console.warn('global ticker navigation failed', { targetUrl: safeTargetUrl, error })
      uni.showToast({ title: '消息页面暂时无法打开，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('global ticker navigation failed', { targetUrl: safeTargetUrl, error })
    uni.showToast({ title: '消息页面暂时无法打开，请稍后重试', icon: 'none' })
  }
}

function normalizeTickerTargetUrl(value?: string | null): string {
  const targetUrl = normalizeTargetUrl(value)
  return targetUrl.startsWith('/pages/user/public-profile/index?') || targetUrl === '/pages/gift/index'
    ? targetUrl
    : '/pages/gift/index'
}

onMounted(() => {
  installRouteChangeHooks()
  syncRoutePath()
  applyOffset()
  void loadTicker()
  refreshTimer = setInterval(() => {
    void loadTicker()
  }, 60_000)
  if (typeof window !== 'undefined') {
    window.addEventListener('hashchange', syncRoutePath)
    window.addEventListener('popstate', syncRoutePath)
    window.addEventListener(routeChangeEventName, syncRoutePath)
  }
})

watch(visible, applyOffset)
onBeforeUnmount(() => {
  clearTimers()
  if (typeof window !== 'undefined') {
    window.removeEventListener('hashchange', syncRoutePath)
    window.removeEventListener('popstate', syncRoutePath)
    window.removeEventListener(routeChangeEventName, syncRoutePath)
  }
  if (typeof document !== 'undefined') {
    document.documentElement.style.setProperty('--global-ticker-offset', '0rpx')
  }
})
</script>

<style scoped lang="scss" src="./global-ticker.scss"></style>
