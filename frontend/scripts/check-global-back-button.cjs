const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const failures = []

function read(file) {
  return fs.readFileSync(path.join(root, file), 'utf8')
}

const componentFile = 'src/components/GlobalBackButton.vue'
const tickerStyleFile = 'src/components/global-ticker.scss'
const mainFile = 'src/main.ts'
const appFile = 'src/App.vue'
const chatPageFile = 'src/pages/chat/conversation/index.vue'
const chatStyleFile = 'src/pages/chat/conversation/style.scss'
const sessionPageFile = 'src/pages/chat/session-list/index.vue'

const componentSource = read(componentFile)
const tickerStyleSource = read(tickerStyleFile)
const mainSource = read(mainFile)
const appSource = read(appFile)
const chatPageSource = read(chatPageFile)
const chatStyleSource = read(chatStyleFile)
const sessionPageSource = read(sessionPageFile)

const requiredComponentSnippets = [
  'const tabPaths = [',
  "'/pages/tabbar/home/index'",
  "'/pages/tabbar/category/index'",
  "'/pages/tabbar/publish/index'",
  "'/pages/tabbar/message/index'",
  "'/pages/tabbar/me/index'",
  "'/pages/chat/conversation/index'",
  "aria-label=\"返回上一页\"",
  'function fallbackTarget()',
  "if (path.startsWith('/pages/chat/conversation/index')) return '/pages/chat/session-list/index'",
  "if (path.startsWith('/pages/chat/session-list/index')) return '/pages/tabbar/message/index'",
  'uni.navigateBack',
  '--global-back-page-offset',
  '--global-back-control-offset',
  "visible.value ? '58px' : '0px'",
  "visible.value ? '2.25rem' : '0'",
  "window.addEventListener('hashchange', syncRoutePath)",
  "window.addEventListener('popstate', syncRoutePath)"
]

for (const snippet of requiredComponentSnippets) {
  if (!componentSource.includes(snippet)) {
    failures.push(`${componentFile}: missing global back behavior snippet: ${snippet}`)
  }
}

if (!mainSource.includes("import GlobalBackButton from './components/GlobalBackButton.vue'") ||
  !mainSource.includes('component: GlobalBackButton') ||
  !mainSource.includes('installGlobalBackButton()')) {
  failures.push(`${mainFile}: GlobalBackButton must be mounted once by H5 bootstrap`)
}

if (!appSource.includes('--global-back-page-offset: 0px') ||
  !appSource.includes('var(--global-back-page-offset)')) {
  failures.push(`${appFile}: page-shell must reserve top space when global back is visible`)
}

if (!chatPageSource.includes('class="back-action tapable"') || !chatPageSource.includes('function goBackToSessions(): void')) {
  failures.push(`${chatPageFile}: chat conversation must use an in-flow back-action because the fixed global back button overlaps peer identity on mobile H5`)
}

if (/var\(--global-back-page-offset,\s*0(?:rpx|px)\)/.test(chatStyleSource)) {
  failures.push(`${chatStyleFile}: chat conversation must not reserve the global fixed back offset when it uses an in-flow header back action`)
}

if (!sessionPageSource.includes('max(var(--global-top-page-offset, 0px), var(--global-ticker-offset, 0px), var(--global-back-page-offset, 0px))')) {
  failures.push(`${sessionPageFile}: fixed-height session list must reserve space for the global back button`)
}

if (/setProperty\('--global-back-page-offset',\s*[^)]*rpx/.test(componentSource)) {
  failures.push(`${componentFile}: CSS variables written from JS must not use rpx because browser CSS cannot evaluate rpx inside calc()`)
}

if (/top:\s*calc\([^;]*var\(--global-ticker-offset/.test(componentSource)) {
  failures.push(`${componentFile}: fixed back button top must use stable px positioning on H5`)
}

if (!tickerStyleSource.includes('left: calc(18rpx + var(--global-back-control-offset, 0px));') ||
  !tickerStyleSource.includes('z-index: 99998;')) {
  failures.push(`${tickerStyleFile}: ticker must avoid the global back button and stay below it in stacking order`)
}

if (!appSource.includes('--global-top-page-offset: 0px') ||
  !appSource.includes('var(--global-top-page-offset)') ||
  appSource.includes('var(--global-ticker-offset) + var(--global-back-page-offset)')) {
  failures.push(`${appFile}: page-shell must use a unified top offset instead of adding ticker and back offsets`)
}

if (/setProperty\('--global-ticker-offset',\s*[^)]*rpx/.test(read('src/components/GlobalTicker.vue'))) {
  failures.push('src/components/GlobalTicker.vue: CSS variables written from JS must not use rpx because browser CSS cannot evaluate rpx inside calc()')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global back button is mounted and unified')
