const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const tickerFile = 'src/components/GlobalTicker.vue'
const httpFile = 'src/api/http.ts'
const mockDataFile = 'src/api/mock-data.ts'
const globalTickerTag = '<GlobalTicker />'
const appGlobalTickerImport = "import GlobalTicker from './components/GlobalTicker.vue'"
const pageGlobalTickerImport = "import GlobalTicker from '../../../components/GlobalTicker.vue'"
const mainGlobalTickerMount = 'createVueApp(target.component).use(pinia).mount(container)'
const mainSource = fs.readFileSync(path.join(root, 'src/main.ts'), 'utf8')
const mainInstallsGlobalTicker = /app\.use\(pinia\)\s+installGlobalTicker\(\)[\s\S]*?return/.test(mainSource)
const appSource = fs.readFileSync(path.join(root, 'src/App.vue'), 'utf8')
const globalTickerPages = [
  'src/pages/tabbar/home/index.vue',
  'src/pages/tabbar/category/index.vue',
  'src/pages/tabbar/publish/index.vue',
  'src/pages/tabbar/message/index.vue',
  'src/pages/tabbar/me/index.vue',
  'src/pages/user/profile/index.vue'
]
const tickerSupportFiles = [
  'src/components/global-ticker-helpers.ts',
  'src/components/global-ticker.scss'
]
const tickerSource = [
  fs.readFileSync(path.join(root, tickerFile), 'utf8'),
  ...tickerSupportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8'))
].join('\n')
const httpSource = fs.readFileSync(path.join(root, httpFile), 'utf8')
const httpMockSource = [
  httpSource,
  fs.readFileSync(path.join(root, mockDataFile), 'utf8')
].join('\n')
const failures = []

const requiredGiftOnlyOrder = 'items.value = giftItems'
const forbiddenTickerSources = [
  'getAnnouncementTicker',
  'listNotifications',
  'buildAnnouncementItems',
  'buildNoticeText',
  'chatNoticeItems',
  'announcementItems',
  'noticeItems',
  "item.type === 'CHAT'",
  "item.type === 'SYSTEM'",
  "item.type === 'AUDIT'",
  '/api/announcements/ticker',
  'ticker notifications'
]

if (!tickerSource.includes(requiredGiftOnlyOrder)) {
  failures.push(`${tickerFile}: global ticker must render only the latest recent gift feed items`)
}

for (const forbiddenSource of forbiddenTickerSources) {
  if (tickerSource.includes(forbiddenSource)) {
    failures.push(`${tickerFile}: global ticker must not mix announcement, system, audit, or private chat notifications: ${forbiddenSource}`)
  }
}

if (!tickerSource.includes('getRecentGiftFeed')) {
  failures.push(`${tickerFile}: global ticker must use the real recent gift feed`)
}

const giftFeedLoadsDirectly = tickerSource.includes("loadOptionalTickerSource('gift', 'recent gift feed', getRecentGiftFeed, [])")
const giftFeedIsLoginGated = [
  '(userStore.token || ENABLE_MOCK_DATA) ? getRecentGiftFeed()',
  'userStore.token ? getRecentGiftFeed()'
].some((gatedGiftFeedCall) => tickerSource.includes(gatedGiftFeedCall))
const tickerTextScrolls = [
  'const marqueeGroups = computed(() => (items.value.length ? [0, 1] : []))',
  "v-for=\"groupIndex in marqueeGroups\"",
  'class="ticker-group"',
  "'--ticker-duration'",
  '<text class="ticker-kind">{{ tickerKindLabel(item.kind) }}</text>',
  '<text class="ticker-text">{{ item.text }}</text>',
  '.ticker-track',
  'animation: ticker-scroll var(--ticker-duration) linear infinite',
  '@keyframes ticker-scroll',
  'translate3d(-50%, 0, 0)'
].every((requiredSnippet) => tickerSource.includes(requiredSnippet))
const optionalSourcesAreCooledDown = [
  'OPTIONAL_SOURCE_COOLDOWN_MS',
  'sourceCooldownUntil',
  'sourceInCooldown',
  'loadOptionalTickerSource',
  "loadOptionalTickerSource('gift', 'recent gift feed', getRecentGiftFeed, [])"
].every((requiredSnippet) => tickerSource.includes(requiredSnippet))

if (!giftFeedLoadsDirectly) {
  failures.push(`${tickerFile}: recent gift feed must be loaded from the public backend endpoint without login gating`)
}

if (giftFeedIsLoginGated) {
  failures.push(`${tickerFile}: global ticker must not hide recent gift feed behind login-only gating`)
}

if (!tickerTextScrolls) {
  failures.push(`${tickerFile}: ticker must use a continuous horizontal marquee with duplicated groups and kind labels`)
}

if (!optionalSourcesAreCooledDown) {
  failures.push(`${tickerFile}: gift ticker source must use cooldown after failures to avoid repeated 404 polling`)
}

if (!httpMockSource.includes("url === '/api/gifts/recent'") || !httpMockSource.includes('mockRecentGiftFeed()')) {
  failures.push(`${httpFile}: local preview mode must include recent gift feed data for ticker testing`)
}

if (/giftItems[\s\S]*\.(sort|reverse)\(/.test(tickerSource)) {
  failures.push(`${tickerFile}: recent gift feed order must stay backend latest-first and must not be resorted in the ticker`)
}

if (
  !tickerSource.includes("'/pages/chat/conversation/index'") ||
  !tickerSource.includes("'/pages/chat/session-list/index'") ||
  !tickerSource.includes('const tickerHiddenOnRoute = computed(() => isTickerHiddenRoute(routePath.value))') ||
  !tickerSource.includes('const visible = computed(() => items.value.length > 0 && !tickerHiddenOnRoute.value)') ||
  !tickerSource.includes('currentRouteIsTickerHidden()') ||
  !tickerSource.includes('decodeRouteVariants') ||
  !tickerSource.includes('installRouteChangeHooks') ||
  !tickerSource.includes("window.addEventListener('popstate', syncRoutePath)") ||
  !tickerSource.includes("window.addEventListener(routeChangeEventName, syncRoutePath)") ||
  !tickerSource.includes("window.addEventListener('hashchange', syncRoutePath)") ||
  !tickerSource.includes("window.removeEventListener('hashchange', syncRoutePath)")
) {
  failures.push(`${tickerFile}: global gift ticker must be hidden on private chat pages, including decoded redirect and history-driven route changes`)
}

if (appSource.includes(globalTickerTag) || appSource.includes(appGlobalTickerImport)) {
  failures.push('src/App.vue: GlobalTicker must not rely on App.vue template rendering in uni-app H5')
}

if (!mainSource.includes(appGlobalTickerImport) || !mainSource.includes('component: GlobalTicker') || !mainSource.includes('installGlobalComponent(globalTickerTarget)') || !mainSource.includes(mainGlobalTickerMount) || !mainInstallsGlobalTicker) {
  failures.push('src/main.ts: GlobalTicker must be mounted once by the H5 app bootstrap')
}

for (const pageFile of globalTickerPages) {
  const pageSource = fs.readFileSync(path.join(root, pageFile), 'utf8')
  if (pageSource.includes(globalTickerTag) || pageSource.includes(pageGlobalTickerImport)) {
    failures.push(`${pageFile}: GlobalTicker is app-level only; page-level mounts duplicate the global bar`)
  }
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global ticker renders latest gift feed only')
