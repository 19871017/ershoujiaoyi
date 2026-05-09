const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/favorite/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

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
  '已取消收藏'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static/local favorite marker found: ${marker}`)
}

const requiredMarkers = [
  '收藏列表接口尚未接入',
  '未展示本地收藏样例',
  '未打开本地收藏商品',
  '收藏接口暂未接通后端，未执行任何收藏变更',
  'const favorites = computed(() => []',
  'function openProductUnavailable()'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed favorite marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('favorite page avoids static products and fails closed without backend favorite list')
