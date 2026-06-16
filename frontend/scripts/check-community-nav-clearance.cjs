const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

function fail(message) {
  console.error(`[community-nav-clearance] ${message}`)
  process.exit(1)
}

function assertIncludes(source, snippet, message) {
  if (!source.includes(snippet)) {
    fail(message)
  }
}

function extractRule(source, selector) {
  const start = source.indexOf(selector)
  if (start < 0) {
    fail(`Missing CSS rule: ${selector}`)
  }
  const open = source.indexOf('{', start)
  if (open < 0) {
    fail(`Missing CSS block for: ${selector}`)
  }
  let depth = 0
  for (let index = open; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(open + 1, index)
      }
    }
  }
  fail(`Unclosed CSS block for: ${selector}`)
}

function extractRules(source, selector) {
  const rules = []
  let offset = 0
  while (offset < source.length) {
    const start = source.indexOf(selector, offset)
    if (start < 0) break
    const open = source.indexOf('{', start)
    if (open < 0) break
    let depth = 0
    let end = -1
    for (let index = open; index < source.length; index += 1) {
      if (source[index] === '{') depth += 1
      if (source[index] === '}') {
        depth -= 1
        if (depth === 0) {
          end = index
          break
        }
      }
    }
    if (end < 0) {
      fail(`Unclosed CSS block for: ${selector}`)
    }
    rules.push(source.slice(open + 1, end))
    offset = end + 1
  }
  if (rules.length === 0) {
    fail(`Missing CSS rule: ${selector}`)
  }
  return rules
}

const appStyle = read('src/App.vue')
const bottomNav = read('src/components/GlobalBottomNav.vue')
const communityPage = read('src/pages/tabbar/message/index.vue')
const composeStyle = read('src/pages/community/compose/style.scss')

assertIncludes(
  appStyle,
  '--global-bottom-nav-clearance: calc(var(--global-bottom-nav-height) + 18px);',
  'Global bottom nav clearance variable must be defined in App.vue.'
)
assertIncludes(
  appStyle,
  '--global-bottom-nav-height: calc(64px + env(safe-area-inset-bottom));',
  'Global bottom nav height must include safe-area inset.'
)
assertIncludes(
  bottomNav,
  'z-index: 99999;',
  'Bottom nav z-index changed; re-check overlay stacking before shipping.'
)
assertIncludes(
  bottomNav,
  '--global-bottom-nav-clearance: calc(var(--global-bottom-nav-height) + 18px);',
  'Small-screen bottom nav override must preserve the clearance variable.'
)

const fabRule = extractRules(communityPage, '.compose-fab').find((rule) => /position:\s*fixed;/.test(rule)) || ''
if (!/bottom:\s*calc\(var\(--global-bottom-nav-clearance/.test(fabRule)) {
  fail('Community compose FAB bottom must use --global-bottom-nav-clearance.')
}
if (!/right:\s*32rpx;/.test(fabRule)) {
  fail('Community compose FAB right offset changed; verify mobile floating layout.')
}

const overlayRule = extractRule(composeStyle, '.post-success-overlay')
if (!/z-index:\s*100010;/.test(overlayRule)) {
  fail('Post success overlay must stay above the global bottom nav z-index.')
}
if (/padding:[^;]*var\(--global-bottom-nav-clearance/s.test(overlayRule)) {
  fail('Post success overlay should be centered and must not depend on bottom nav clearance.')
}
if (!/align-items:\s*center;/.test(overlayRule)) {
  fail('Post success overlay should be vertically centered on screen.')
}
if (!/justify-content:\s*center;/.test(overlayRule)) {
  fail('Post success overlay should be horizontally centered on screen.')
}

console.log('[community-nav-clearance] ok')
