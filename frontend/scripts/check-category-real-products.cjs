const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/category/index.vue'
const styleFile = 'src/pages/tabbar/category/style.scss'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const styleSource = fs.readFileSync(path.join(root, styleFile), 'utf8')
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
  'count: 14',
  '商品接口暂时不可用',
  '未展示本地分类宝贝样例',
  '暂未加载到后端分类宝贝',
  '胸罩',
  '内裤',
  '比基尼',
  "iconType: 'bra'",
  "iconType: 'panty'",
  "iconType: 'bikini'",
  "type WearIconType = 'bra' | 'panty' | 'bikini'"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden category product marker found: ${marker}`)
}

const forbiddenStyleMarkers = [
  '.wear-icon',
  '.bra-cup',
  '.bra-band',
  '.panty-waist',
  '.panty-body',
  '.bikini-top',
  '.bikini-cup',
  '.bikini-bottom'
]

for (const marker of forbiddenStyleMarkers) {
  if (styleSource.includes(marker)) failures.push(`${styleFile}: forbidden low-quality clothing icon style remains: ${marker}`)
}

const forbiddenUserVisibleText = [
  '接口',
  '后端',
  '本地',
  '样例',
  '演示',
  '测试',
  'demo',
  'mock',
  'Mock',
  'Demo'
]

const template = source.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? ''
for (const marker of forbiddenUserVisibleText) {
  if (template.includes(marker)) failures.push(`${file}: user-visible technical/testing wording found: ${marker}`)
}

const requiredMarkers = [
  "onMounted",
  "onShow",
  "listProducts",
  "type ProductListItemResponse",
  "const products = ref<ProductListItemResponse[]>([])",
  "products.value = remote",
  "products.value = []",
  "const categoryKeywords = computed(() => {",
  "return activeItems.value.flatMap((item) => itemKeywords(item))",
  "keywords.some((item) => text.includes(item))",
  "const videoStatus = String(profile.videoIdentityStatus || '').toUpperCase()",
  "canPublish.value = profile.videoVerified === true && videoStatus === 'APPROVED' && publishRoles.includes(role)",
  "const loadErrorText = '商品暂时不可用，请稍后再来看看'",
  "const emptyCategoryText = '这里暂时还没有上架宝贝'",
  "type CategoryItem = { name: string; icon: string; keywords?: string[] }",
  "name: '全部'",
  "if (currentGroup.value.name === defaultGroup.name) return []",
  "function itemKeywords(item: CategoryItem): string[]",
  "errorText.value = loadErrorText",
  'class="empty-card ds-card category-empty"',
  '换个关键词，或恢复到全部在售宝贝看看。',
  '暂时还没有在售宝贝',
  '新的宝贝上架后会直接出现在这里。',
  'class="publish-inline tapable" @click="goPublishForm"',
  'class="empty-action primary tapable" @click="refreshCategoryData(true)"',
  '刷新看看',
  'class="empty-action primary tapable" @click="resetFilters"',
  'class="empty-action tapable" @click="clearKeyword"',
  'function resetFilters(): void',
  'active.value = defaultGroup.name',
  'function clearKeyword(): void',
  'async function refreshCategoryData(showLoading = false): Promise<void>'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed category product marker: ${marker}`)
}

const refreshMarkers = [
  'const categoryRefreshing = ref(false)',
  'async function refreshCategoryData(showLoading = false): Promise<void>',
  'await Promise.all([loadProducts(), loadPublishPermission()])',
  'onMounted(() => {',
  'void refreshCategoryData(true)',
  'onShow(() => {',
  'void refreshCategoryData(false)'
]

for (const marker of refreshMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing tab-return refresh marker: ${marker}`)
}

const requiredStyleMarkers = [
  'padding-bottom: calc(230rpx + env(safe-area-inset-bottom))',
  'grid-template-columns: repeat(4, minmax(0, 1fr))',
  '.publish-inline {',
  '.product-card {\n  min-width: 0;',
  '.product-bottom {\n  margin-top: 12rpx;\n  align-items: flex-end;',
  '.price {\n  flex: 1 1 auto;\n  min-width: 0;',
  'text-overflow: ellipsis',
  '.detail-pill {\n  flex: 0 0 auto;\n  min-width: 62rpx;',
  '.empty-actions {\n  margin-top: 18rpx;',
  '.empty-action.primary',
  '@media (max-width: 360px)'
]

for (const marker of requiredStyleMarkers) {
  if (!styleSource.includes(marker)) failures.push(`${styleFile}: missing stable small-screen product-card style marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('category page uses real products, fails closed, and avoids technical or sensitive category wording')
