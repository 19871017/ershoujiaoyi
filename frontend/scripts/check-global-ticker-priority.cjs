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

const forbiddenOrder = 'items.value = [...announcementItems, ...giftItems, ...noticeItems].slice(0, 6)'
const previousGiftFirstOrder = 'items.value = [...giftItems, ...announcementItems, ...noticeItems].slice(0, 6)'
const requiredOrder = 'items.value = [...chatNoticeItems, ...giftItems, ...announcementItems, ...noticeItems].slice(0, 6)'

if (tickerSource.includes(forbiddenOrder)) {
  failures.push(`${tickerFile}: global ticker must not place announcements before gift feed`)
}

if (tickerSource.includes(previousGiftFirstOrder)) {
  failures.push(`${tickerFile}: global ticker must put CHAT notices before gift feed so private-message prompts are not crowded out`)
}

if (!tickerSource.includes(requiredOrder)) {
  failures.push(`${tickerFile}: global ticker must prioritize CHAT notices, then gift feed, then announcements and operational notices`)
}

if (!tickerSource.includes('getRecentGiftFeed')) {
  failures.push(`${tickerFile}: global ticker must use the real recent gift feed`)
}

const giftFeedLoadsDirectly = tickerSource.includes("loadOptionalTickerSource('gift', 'recent gift feed', getRecentGiftFeed, [])")
const giftFeedIsLoginGated = [
  '(userStore.token || ENABLE_MOCK_DATA) ? getRecentGiftFeed()',
  'userStore.token ? getRecentGiftFeed()'
].some((gatedGiftFeedCall) => tickerSource.includes(gatedGiftFeedCall))
const tickerTextScrolls = tickerSource.includes('<text class="ticker-text">{{ item.text }}</text>') && tickerSource.includes('@keyframes ticker-scroll')
const optionalSourcesAreCooledDown = [
  'OPTIONAL_SOURCE_COOLDOWN_MS',
  'sourceCooldownUntil',
  'sourceInCooldown',
  'loadOptionalTickerSource',
  "loadOptionalTickerSource('announcement', 'announcement ticker', getAnnouncementTicker, null)",
  "loadOptionalTickerSource('gift', 'recent gift feed', getRecentGiftFeed, [])"
].every((requiredSnippet) => tickerSource.includes(requiredSnippet))

if (!giftFeedLoadsDirectly) {
  failures.push(`${tickerFile}: recent gift feed must be loaded from the public backend endpoint without login gating`)
}

if (giftFeedIsLoginGated) {
  failures.push(`${tickerFile}: global ticker must not hide recent gift feed behind login-only gating`)
}

if (!tickerTextScrolls) {
  failures.push(`${tickerFile}: ticker text must scroll horizontally so a single pinned announcement still visibly rotates`)
}

if (!optionalSourcesAreCooledDown) {
  failures.push(`${tickerFile}: non-critical ticker sources must use cooldown after failures to avoid repeated 404 polling`)
}

if (
  !tickerSource.includes("const chatNoticeItems: TickerItem[] = notifications") ||
  !tickerSource.includes("item.type === 'CHAT' && !!buildNoticeText(item)") ||
  !tickerSource.includes('.slice(0, 2)') ||
  !tickerSource.includes("item.type === 'SYSTEM' || item.type === 'AUDIT'")
) {
  failures.push(`${tickerFile}: global ticker must include CHAT notifications so private-message prompts appear outside the community page`)
}

if (
  !tickerSource.includes("import { isSafeNotificationTargetUrl, isTabBarNotificationTargetUrl } from '../pages/notification/notification-helpers'") ||
  !tickerSource.includes('function normalizeTickerTargetUrl(value?: string | null): string') ||
  !tickerSource.includes('return isSafeNotificationTargetUrl(targetUrl) ? targetUrl : \'/pages/notification/index\'') ||
  !tickerSource.includes('if (isTabBarNotificationTargetUrl(safeTargetUrl)) uni.switchTab(route)') ||
  !tickerSource.includes('else uni.navigateTo(route)')
) {
  failures.push(`${tickerFile}: global ticker notification navigation must reuse the notification target whitelist and switch tabbar routes safely`)
}

if (!httpMockSource.includes("url === '/api/announcements/ticker'")) {
  failures.push(`${httpFile}: local preview mode must include announcement ticker data`)
}

if (!httpMockSource.includes('enabled: true') || !httpMockSource.includes('欢迎来到小原圈，请通过平台订单流程完成交易')) {
  failures.push(`${httpFile}: local preview announcement ticker must be enabled and use product copy`)
}

if (httpMockSource.includes('演示公告')) {
  failures.push(`${httpFile}: local preview announcement ticker must not expose demo copy to users`)
}

if (!httpMockSource.includes("url === '/api/gifts/recent'") || !httpMockSource.includes('mockRecentGiftFeed()')) {
  failures.push(`${httpFile}: local preview mode must include recent gift feed data for ticker testing`)
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

console.log('global ticker prioritizes chat notices before gift feed, announcements and operational notices')
