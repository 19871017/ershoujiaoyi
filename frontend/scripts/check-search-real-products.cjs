const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/search/result/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const forbiddenMarkers = [
  'const products = [',
  '奶油白法式连衣裙',
  '小香风玛丽珍鞋 37码',
  '蝴蝶结长袜三双装',
  '粉色腋下包 轻微使用',
  '担保交易',
  '搜索 ${keyword.value}',
  '展示全部宝贝',
  'openProduct(id:number)'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden search demo/fake trust marker found: ${marker}`)
}

const requiredMarkers = [
  "import { computed, onMounted, ref } from 'vue'",
  "import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'",
  "const products = ref<ProductListItemResponse[]>([])",
  "const loading = ref(false)",
  "const loadMessage = ref('')",
  "products.value = remote",
  "products.value = []",
  "商品接口暂时不可用，未展示本地搜索宝贝样例",
  "仅展示后端返回的在售商品",
  "onMounted(() => { readQuery(); loadProducts() })"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed search product marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('search result page avoids demo product fallbacks and fails closed when product API is unavailable')
