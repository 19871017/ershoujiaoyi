const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const readJson = (relative) => JSON.parse(fs.readFileSync(path.join(root, relative), 'utf8'))
const rootPagesRaw = fs.readFileSync(path.join(root, 'pages.json'), 'utf8')
const srcPagesRaw = fs.readFileSync(path.join(root, 'src/pages.json'), 'utf8')

let failed = false
function fail(message) {
  console.error(message)
  failed = true
}

if (rootPagesRaw !== srcPagesRaw) fail('frontend/pages.json and frontend/src/pages.json differ')

const pagesConfig = readJson('src/pages.json')
const configuredPaths = pagesConfig.pages.map((page) => page.path)
const actualIndexPaths = []
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full)
    else if (entry.isFile() && entry.name === 'index.vue') {
      actualIndexPaths.push(path.relative(path.join(root, 'src'), full).replace(/\\/g, '/').replace(/\.vue$/, ''))
    }
  }
}
walk(path.join(root, 'src/pages'))
actualIndexPaths.sort()
const configuredSorted = [...configuredPaths].sort()

if (configuredSorted.length !== actualIndexPaths.length) fail(`route count mismatch: pages.json=${configuredSorted.length}, index.vue=${actualIndexPaths.length}`)
for (const route of configuredSorted) {
  if (!actualIndexPaths.includes(route)) fail(`configured route missing index.vue: ${route}`)
}
for (const route of actualIndexPaths) {
  if (!configuredSorted.includes(route)) fail(`index.vue missing in pages.json: ${route}`)
}

const tabLabels = pagesConfig.tabBar.list.map((item) => item.text).join('/')
if (tabLabels !== '首页/宝贝/上新/社区/我的') fail(`unexpected tab labels: ${tabLabels}`)

const tabPaths = new Set(pagesConfig.tabBar.list.map((item) => item.pagePath))
for (const page of pagesConfig.pages) {
  if (tabPaths.has(page.path) && page.style?.navigationBarTitleText !== '') {
    fail(`tab page title must be empty: ${page.path}`)
  }
}

if (failed) process.exit(1)
console.log('pages consistency check passed')
