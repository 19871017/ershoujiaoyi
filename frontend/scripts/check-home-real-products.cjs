const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/home/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

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
  '可爱展示宝贝'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden home demo-product fallback marker found: ${marker}`)
}

const requiredMarkers = [
  'const products = ref<ProductListItemResponse[]>([])',
  'products.value = remote',
  'products.value = []',
  '暂未加载到后端在售宝贝',
  '商品接口暂时不可用，未展示本地演示宝贝',
  '件后端在售宝贝'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed home product marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('home page avoids demo product fallbacks and fails closed when product API is unavailable')
