const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/closet/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  'const products = reactive([',
  '奶油白法式连衣裙',
  '小香风玛丽珍鞋',
  '蝴蝶结长袜三双装',
  '粉色腋下包',
  "id: 1001",
  "id: 1002",
  "id: 1003",
  "id: 1004",
  "status: 'ON_SALE' as ProductStatus",
  "status: 'AUDITING' as ProductStatus",
  "status: 'DRAFT' as ProductStatus",
  "status: 'SOLD' as ProductStatus",
  'activeTab = tab.value',
  'openDetail(item.id)',
  'editItem(item)'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static/local closet marker found: ${marker}`)
}

const requiredMarkers = [
  'import { computed, onMounted, ref } from',
  'listProducts',
  'ProductListItemResponse',
  'const products = ref<ProductListItemResponse[]>([])',
  'await listProducts()',
  'sellerProductListUnavailable',
  '无法加载后端商品列表，未展示本地商品样例',
  '商品列表为空或暂未接入卖家专属筛选',
  '商品缺少后端 productId，未打开本地商品详情',
  '商品缺少后端 productId，未进入本地编辑页',
  '上下架暂未接通后端，未执行任何商品变更'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived/fail-closed closet marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('closet page uses backend product list and fails closed without static seller products')
