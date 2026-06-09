<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useUserStore } from './store/modules/user'

const LOGIN_PATH = '/pages/auth/login/index'
const PUBLIC_PREFIXES = [
  '/pages/auth/login/index',
  '/pages/system/privacy/index'
]

let interceptorReady = false
let redirectingToLogin = false

type RouteMethod = 'navigateTo' | 'redirectTo' | 'reLaunch' | 'switchTab'
type RouteArgs = { url?: string }
type UniWithInterceptor = typeof uni & {
  addInterceptor?: (method: RouteMethod, interceptor: { invoke?: (args: RouteArgs) => boolean }) => void
}

function normalizeUrl(url: string) {
  return (url || '').split('?')[0].replace(/^#/, '')
}

function isPublicPage(url: string) {
  const normalized = normalizeUrl(url)
  return PUBLIC_PREFIXES.some((item) => normalized.startsWith(item))
}

function isProtectedPage(url: string) {
  const normalized = normalizeUrl(url)
  return normalized.startsWith('/pages/') && !isPublicPage(normalized)
}

function currentPagePath() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as { route?: string } | undefined
  return current?.route ? `/${current.route}` : ''
}

function ensureLogin(targetUrl?: string) {
  const userStore = useUserStore()
  const rawTarget = (targetUrl || currentPagePath() || '').replace(/^#/, '')
  const currentPath = normalizeUrl(rawTarget)
  if (!currentPath || !isProtectedPage(currentPath) || userStore.token) return true
  if (redirectingToLogin || currentPath === LOGIN_PATH) return false
  redirectingToLogin = true
  uni.redirectTo({
    url: `${LOGIN_PATH}?redirect=${encodeURIComponent(rawTarget)}`
  })
  setTimeout(() => {
    redirectingToLogin = false
  }, 120)
  return false
}

function installRouteInterceptors() {
  if (interceptorReady) return
  const uniWithInterceptor = uni as UniWithInterceptor
  if (typeof uniWithInterceptor.addInterceptor !== 'function') return
  interceptorReady = true
  ;(['navigateTo', 'redirectTo', 'reLaunch', 'switchTab'] as RouteMethod[]).forEach((method) => {
    uniWithInterceptor.addInterceptor?.(method, {
      invoke(args: RouteArgs) {
        const url = typeof args?.url === 'string' ? args.url : ''
        return ensureLogin(url)
      }
    })
  })
}

function ensureInitialLogin(attempt = 0) {
  if (useUserStore().token) return
  const path = currentPagePath()
  if (path) {
    ensureLogin(path)
    return
  }
  if (attempt >= 5) return
  setTimeout(() => ensureInitialLogin(attempt + 1), 80)
}

onLaunch(() => {
  installRouteInterceptors()
  ensureInitialLogin()
})

onShow(() => {
  installRouteInterceptors()
  ensureInitialLogin()
})
</script>

<style lang="scss">
:root {
  --c-bg: #fff7ed;
  --c-surface: #ffffff;
  --c-surface-soft: #fff3e7;
  --c-text: #3a2a1f;
  --c-muted: #9b7560;
  --c-line: #ffd9bd;
  --c-primary: #ff7a45;
  --c-primary-dark: #e85d2a;
  --c-danger: #ef4444;
  --c-success: #10b981;
  --c-warn: #f59e0b;
  --c-peach: #ffc08a;
  --c-lavender: #b99cff;
  --c-ring: #ffb36b;
  --radius-sm: 16rpx;
  --radius-md: 22rpx;
  --radius-lg: 28rpx;
  --shadow-card: 0 8rpx 22rpx rgba(255, 122, 69, .10);
  --global-ticker-offset: 0rpx;
}

page {
  min-height: 100%;
  background: var(--c-bg);
  color: var(--c-text);
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
}

view, text, button, input, textarea { box-sizing: border-box; }
button { margin: 0; padding: 0; border: 0; background: none; line-height: 1.2; }

.page-shell {
  min-height: 100vh;
  padding: calc(18rpx + var(--global-ticker-offset)) 18rpx calc(230rpx + env(safe-area-inset-bottom));
  background: var(--c-bg);
}

.page-title {
  font-size: 36rpx;
  line-height: 1.16;
  font-weight: 900;
  color: var(--c-text);
}

.page-desc {
  margin-top: 6rpx;
  color: var(--c-muted);
  font-size: 23rpx;
  line-height: 1.42;
}

.ds-card {
  background: var(--c-surface);
  border: 1rpx solid var(--c-line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.tapable { cursor: pointer; transition: transform .15s ease, opacity .15s ease; }
.tapable:active { transform: scale(.98); opacity: .82; }

.primary-btn {
  min-height: 76rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: var(--c-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 900;
}

.secondary-btn {
  min-height: 64rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: var(--c-surface-soft);
  color: var(--c-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 800;
}

uni-tabbar {
  display: none !important;
}

.community-switcher-open .compose-fab {
  display: none !important;
}
</style>
