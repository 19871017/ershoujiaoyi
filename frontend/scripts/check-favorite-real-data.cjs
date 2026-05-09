const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/favorite/index.vue'
const apiFile = 'src/api/modules/product.ts'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const apiSource = fs.readFileSync(path.join(root, apiFile), 'utf8')

const failures = []

const forbiddenMarkers = [
  'const favorites = reactive([',
  '奶油白法式连衣裙',
  '小香风玛丽珍鞋',
  '粉色腋下包',
  "id: 1001",
  "id: 1002",
  "id: 1004",
  "status: '在售'",
  "status: '可聊价'",
  "status: '同城可约看'",
  'openProduct(item.id)',
  'favorites.splice',
  '收藏列表接口尚未接入',
  '收藏接口暂未接通后端'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static/local favorite marker found: ${marker}`)
}

const requiredMarkers = [
  "import { computed, onMounted, ref } from 'vue'",
  "listFavoriteProducts, unfavoriteProduct, type ProductListItemResponse",
  'const favorites = ref<ProductListItemResponse[]>([])',
  'favorites.value = await listFavoriteProducts()',
  'favorites.value = []',
  '收藏列表接口加载失败，未展示本地收藏样例',
  '后端取消收藏失败，未执行本地收藏变更',
  'if (!productId || productId <= 0)',
  'onMounted(loadFavorites)'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived favorite marker: ${marker}`)
}

if (!apiSource.includes("listFavoriteProducts()") || !apiSource.includes("'/api/products/favorites'")) {
  failures.push(`${apiFile}: missing backend favorite list API wrapper`)
}

if (!apiSource.includes('unfavoriteProduct(productId: number)') || !apiSource.includes('del<void>(`/api/products/${productId}/favorite`)')) {
  failures.push(`${apiFile}: missing backend favorite removal API wrapper`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('favorite page uses backend favorite list/removal and avoids static local favorites')
