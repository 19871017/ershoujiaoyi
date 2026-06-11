<template>
  <view v-if="visible" class="global-back-button tapable" aria-label="返回上一页" @click.stop="goBack">
    <text aria-hidden="true">‹</text>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const routeChangeEventName = 'xiaoyuanquan:back-routechange'
const tabPaths = [
  '/pages/tabbar/home/index',
  '/pages/tabbar/category/index',
  '/pages/tabbar/publish/index',
  '/pages/tabbar/message/index',
  '/pages/tabbar/me/index'
] as const
const hiddenRoutes = [
  '/pages/auth/login/index',
  '/pages/system/empty/index',
  '/pages/chat/conversation/index'
] as const

const routePath = ref('')
const visible = computed(() => {
  if (!routePath.value) return false
  if (tabPaths.some((path) => routePath.value.startsWith(path))) return false
  return !hiddenRoutes.some((path) => routePath.value.startsWith(path))
})

type NavigateFail = (error: unknown) => void
type NavigateOptions = { url: string; fail?: NavigateFail }
type NavigateBack = (options: { delta?: number; fail?: NavigateFail }) => void
type SwitchTab = (options: NavigateOptions) => void
type RedirectTo = (options: NavigateOptions) => void

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function normalizeRoute(value: string): string {
  let current = value.trim()
  for (let index = 0; index < 3; index += 1) {
    const decoded = safeDecode(current)
    if (decoded === current) break
    current = decoded
  }
  const hashIndex = current.indexOf('#')
  const routedValue = hashIndex >= 0 ? current.slice(hashIndex + 1) : current
  const path = routedValue.replace(/^#/, '').split('?')[0]
  return path.startsWith('/') ? path : `/${path}`
}

function currentRoutePath(): string {
  if (typeof window !== 'undefined') {
    const hashPath = normalizeRoute(window.location.hash.replace(/^#/, ''))
    if (hashPath.startsWith('/pages/')) return hashPath
  }
  try {
    const pages = getCurrentPages()
    const current = pages[pages.length - 1] as { route?: string } | undefined
    return current?.route ? `/${current.route}` : ''
  } catch {
    return ''
  }
}

function syncRoutePath(): void {
  routePath.value = currentRoutePath()
  applyPageOffset()
}

function notifyRouteChanged(): void {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(routeChangeEventName))
}

function installRouteChangeHooks(): void {
  if (typeof window === 'undefined') return
  const historyApi = window.history as History & {
    __xiaoyuanquanBackHooked?: boolean
  }
  if (historyApi.__xiaoyuanquanBackHooked) return
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
  historyApi.__xiaoyuanquanBackHooked = true
}

function applyPageOffset(): void {
  if (typeof document === 'undefined') return
  document.documentElement.style.setProperty('--global-back-page-offset', visible.value ? '2.25rem' : '0')
}

function fallbackTarget(): string {
  const path = routePath.value
  if (path.startsWith('/pages/chat/conversation/index')) return '/pages/chat/session-list/index'
  if (path.startsWith('/pages/chat/session-list/index')) return '/pages/tabbar/message/index'
  if (path.startsWith('/pages/community/')) return '/pages/tabbar/message/index'
  if (path.startsWith('/pages/product/') || path.startsWith('/pages/search/')) return '/pages/tabbar/category/index'
  if (path.startsWith('/pages/order/') || path.startsWith('/pages/wallet/') || path.startsWith('/pages/user/') || path.startsWith('/pages/system/') || path.startsWith('/pages/notification/')) return '/pages/tabbar/me/index'
  if (path.startsWith('/pages/payment/') || path.startsWith('/pages/after-sales/') || path.startsWith('/pages/review/') || path.startsWith('/pages/report/') || path.startsWith('/pages/upload/')) return '/pages/tabbar/me/index'
  if (path.startsWith('/pages/ranking/') || path.startsWith('/pages/gift/') || path.startsWith('/pages/risk/')) return '/pages/tabbar/home/index'
  return '/pages/tabbar/home/index'
}

function openFallback(): void {
  const target = fallbackTarget()
  const tabTarget = tabPaths.some((path) => target === path)
  const fail = (error: unknown) => {
    console.warn('global back fallback failed', { target, error })
    try {
      uni.reLaunch({ url: '/pages/tabbar/home/index' })
    } catch (reLaunchError) {
      console.warn('global back relaunch fallback failed', { error: reLaunchError })
    }
  }
  if (tabTarget) {
    ;(uni.switchTab as unknown as SwitchTab)({ url: target, fail })
    return
  }
  ;(uni.redirectTo as unknown as RedirectTo)({ url: target, fail })
}

function goBack(): void {
  try {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      ;(uni.navigateBack as unknown as NavigateBack)({
        delta: 1,
        fail: openFallback
      })
      return
    }
  } catch (error) {
    console.warn('global back page stack read failed', { error })
  }
  openFallback()
}

onMounted(() => {
  installRouteChangeHooks()
  syncRoutePath()
  if (typeof window !== 'undefined') {
    window.addEventListener('hashchange', syncRoutePath)
    window.addEventListener('popstate', syncRoutePath)
    window.addEventListener(routeChangeEventName, syncRoutePath)
  }
})

watch(visible, applyPageOffset)

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('hashchange', syncRoutePath)
    window.removeEventListener('popstate', syncRoutePath)
    window.removeEventListener(routeChangeEventName, syncRoutePath)
  }
  if (typeof document !== 'undefined') {
    document.documentElement.style.setProperty('--global-back-page-offset', '0')
  }
})
</script>

<style scoped>
.global-back-button {
  position: fixed;
  left: 12px;
  top: 12px;
  z-index: 99998;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6f4938;
  background: rgba(255, 251, 246, .94);
  border: 1rpx solid rgba(255, 217, 189, .82);
  box-shadow: 0 10rpx 24rpx rgba(117, 62, 36, .12);
  backdrop-filter: blur(14rpx);
}

.global-back-button text {
  transform: translateY(-1px);
  font-size: 30px;
  line-height: 1;
  font-weight: 850;
}
</style>
