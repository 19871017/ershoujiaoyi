const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const failures = []

function read(file) {
  return fs.readFileSync(path.join(root, file), 'utf8')
}

const componentFile = 'src/components/GlobalBackButton.vue'
const mainFile = 'src/main.ts'
const appFile = 'src/App.vue'
const chatPageFile = 'src/pages/chat/conversation/index.vue'
const chatStyleFile = 'src/pages/chat/conversation/style.scss'
const sessionPageFile = 'src/pages/chat/session-list/index.vue'

const componentSource = read(componentFile)
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

if (!appSource.includes('--global-back-page-offset: 0rpx') ||
  !appSource.includes('var(--global-back-page-offset)')) {
  failures.push(`${appFile}: page-shell must reserve top space when global back is visible`)
}

if (!chatPageSource.includes('class="back-action tapable"') || !chatPageSource.includes('function goBackToSessions(): void')) {
  failures.push(`${chatPageFile}: chat conversation must use an in-flow back-action because the fixed global back button overlaps peer identity on mobile H5`)
}

if (chatStyleSource.includes('var(--global-back-page-offset, 0rpx)')) {
  failures.push(`${chatStyleFile}: chat conversation must not reserve the global fixed back offset when it uses an in-flow header back action`)
}

if (!sessionPageSource.includes('var(--global-back-page-offset, 0rpx)')) {
  failures.push(`${sessionPageFile}: fixed-height session list must reserve space for the global back button`)
}

if (/setProperty\('--global-back-page-offset',\s*[^)]*rpx/.test(componentSource)) {
  failures.push(`${componentFile}: CSS variables written from JS must not use rpx because browser CSS cannot evaluate rpx inside calc()`)
}

if (/top:\s*calc\([^;]*var\(--global-ticker-offset/.test(componentSource)) {
  failures.push(`${componentFile}: fixed back button top must use stable px positioning on H5`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global back button is mounted and unified')
