const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const tickerFile = 'src/components/GlobalTicker.vue'
const httpFile = 'src/api/http.ts'
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
const tickerSource = fs.readFileSync(path.join(root, tickerFile), 'utf8')
const httpSource = fs.readFileSync(path.join(root, httpFile), 'utf8')
const failures = []

const forbiddenOrder = 'items.value = [...announcementItems, ...giftItems, ...noticeItems].slice(0, 6)'
const requiredOrder = 'items.value = [...giftItems, ...announcementItems, ...noticeItems].slice(0, 6)'

if (tickerSource.includes(forbiddenOrder)) {
  failures.push(`${tickerFile}: global ticker must not place announcements before gift feed`)
}

if (!tickerSource.includes(requiredOrder)) {
  failures.push(`${tickerFile}: global ticker must prioritize gift feed before announcements and notices`)
}

if (!tickerSource.includes('getRecentGiftFeed')) {
  failures.push(`${tickerFile}: global ticker must use the real recent gift feed`)
}

const giftFeedLoadsDirectly = tickerSource.includes('getRecentGiftFeed().catch((error) => {')
const giftFeedIsLoginGated = [
  '(userStore.token || ENABLE_MOCK_DATA) ? getRecentGiftFeed()',
  'userStore.token ? getRecentGiftFeed()'
].some((gatedGiftFeedCall) => tickerSource.includes(gatedGiftFeedCall))
const tickerTextScrolls = tickerSource.includes('<text class="ticker-text">{{ item.text }}</text>') && tickerSource.includes('@keyframes ticker-scroll')

if (!giftFeedLoadsDirectly) {
  failures.push(`${tickerFile}: recent gift feed must be loaded from the public backend endpoint without login gating`)
}

if (giftFeedIsLoginGated) {
  failures.push(`${tickerFile}: global ticker must not hide recent gift feed behind login-only gating`)
}

if (!tickerTextScrolls) {
  failures.push(`${tickerFile}: ticker text must scroll horizontally so a single pinned announcement still visibly rotates`)
}

if (!httpSource.includes("url === '/api/announcements/ticker'")) {
  failures.push(`${httpFile}: mock mode must include announcement ticker data for demo testing`)
}

if (!httpSource.includes('enabled: true') || !httpSource.includes('演示公告')) {
  failures.push(`${httpFile}: mock announcement ticker must be enabled and clearly marked as demo copy`)
}

if (!httpSource.includes("url === '/api/gifts/recent'") || !httpSource.includes('mockRecentGiftFeed()')) {
  failures.push(`${httpFile}: mock mode must include recent gift feed data for ticker demo testing`)
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

console.log('global ticker prioritizes recent gift feed before announcements and notifications')
