const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/home/index.vue'
const styleFile = 'src/pages/tabbar/home/style.scss'
const pageSource = fs.readFileSync(path.join(root, file), 'utf8')
const styleSource = fs.readFileSync(path.join(root, styleFile), 'utf8')
const supportFiles = [
  'src/pages/tabbar/home/home-data.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  pageSource
].join('\n')

const failures = []

const forbiddenMarkers = [
  'const demoProducts:',
  'GIRL-DRESS-1001',
  'GIRL-SHOES-1002',
  'GIRL-SOCKS-1003',
  'GIRL-BAG-1004',
  'products.value = remote.length ? remote : demoProducts',
  'products.value = demoProducts',
  '后端未启动，已展示本地演示宝贝',
  '可爱展示宝贝',
  'MIN_SIMULATED_PRODUCTS',
  'displayProducts',
  'repeated.push',
  'duplicateRows',
  '[...productRows.value, ...',
  'product-marquee',
  'product-grid-track',
  'product-row',
  'rollingRows',
  'productRollTimer',
  'startProductRoll',
  'shouldRollProducts',
  'MANUAL_SCROLL_RESUME_DELAY',
  '后台配置',
  '待后台配置',
  '小原圈卖家'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden home demo-product fallback marker found: ${marker}`)
}

const requiredMarkers = [
  'const products = ref<ProductListItemResponse[]>([])',
  'products.value = remote',
  'products.value = []',
  '今日上新 · {{ products.length }} 件在售宝贝',
  '宝贝暂时不可用，请稍后再逛',
  '暂无在售宝贝',
  '新鲜宝贝会在这里陆续亮相',
  'v-for="item in products"',
  ':key="item.productId"',
  'sellerDisplayName(item)',
  'sellerAvatarUrl(item)',
  'sellerInitial(item)',
  'item.sellerVideoVerified',
  '卖家资料待同步'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed home product marker: ${marker}`)
}

for (const marker of ['后端', '服务端', '演示']) {
  if (pageSource.includes(marker)) failures.push(`${file}: user-visible home copy must not expose technical/test wording: ${marker}`)
}

if (!/padding-bottom:\s*calc\(230rpx \+ env\(safe-area-inset-bottom\)\)/.test(styleSource)) {
  failures.push(`${styleFile}: home tabbar page must preserve the global bottom-nav safe area`)
}

if (!/\.product-grid\s*\{[\s\S]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/.test(styleSource)) {
  failures.push(`${styleFile}: home products should render as a natural two-column grid`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('home page avoids demo product fallbacks and fails closed when product API is unavailable')
