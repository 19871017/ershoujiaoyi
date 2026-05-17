const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const tickerFile = 'src/components/GlobalTicker.vue'
const httpFile = 'src/api/http.ts'
const globalTickerTag = '<GlobalTicker />'
const globalTickerImport = "import GlobalTicker from '../../../components/GlobalTicker.vue'"
const mainTabPages = [
  'src/pages/tabbar/home/index.vue',
  'src/pages/tabbar/category/index.vue',
  'src/pages/tabbar/publish/index.vue',
  'src/pages/tabbar/message/index.vue',
  'src/pages/tabbar/me/index.vue'
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

if (!tickerSource.includes("const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true'")) {
  failures.push(`${tickerFile}: mock ticker demo must be gated by VITE_ENABLE_MOCK_DATA`)
}

if (!tickerSource.includes('(userStore.token || ENABLE_MOCK_DATA) ? getRecentGiftFeed()')) {
  failures.push(`${tickerFile}: mock mode should show virtual gift ticker without requiring login while production remains token-gated`)
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

for (const pageFile of mainTabPages) {
  const pageSource = fs.readFileSync(path.join(root, pageFile), 'utf8')
  if (!pageSource.includes(globalTickerTag) || !pageSource.includes(globalTickerImport)) {
    failures.push(`${pageFile}: main tab page must mount GlobalTicker directly so the H5 page renders the announcement bar`)
  }
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global ticker prioritizes recent gift feed before announcements and notifications')
