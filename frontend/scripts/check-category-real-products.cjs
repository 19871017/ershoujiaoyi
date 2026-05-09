const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/category/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const forbiddenMarkers = [
  'interface DemoProduct',
  'const demoProducts:',
  'demoProducts.filter',
  '奶油白法式连衣裙',
  '小香风玛丽珍鞋 37码',
  '蝴蝶结长袜三双装',
  '粉色腋下包 轻微使用',
  'products.value = demoProducts',
  '换个分类或关键词试试。',
  '同城近',
  "value: 'near'",
  'item.count }} 件',
  'count: 28',
  'count: 34',
  'count: 18',
  'count: 12',
  'count: 16',
  'count: 20',
  'count: 22',
  'count: 15',
  'count: 19',
  'count: 10',
  'count: 21',
  'count: 14'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden category demo-product marker found: ${marker}`)
}

const requiredMarkers = [
  "onMounted",
  "listProducts",
  "type ProductListItemResponse",
  "const products = ref<ProductListItemResponse[]>([])",
  "products.value = remote",
  "products.value = []",
  "商品接口暂时不可用，未展示本地分类宝贝样例",
  "暂未加载到后端分类宝贝",
  "onMounted(loadProducts)"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed category product marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('category page avoids demo product fallbacks and fails closed when product API is unavailable')
