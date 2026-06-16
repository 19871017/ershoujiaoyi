const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = {
  list: 'src/pages/order/list/index.vue',
  listStyle: 'src/pages/order/list/style.scss',
  detail: 'src/pages/order/detail/index.vue',
  detailStyle: 'src/pages/order/detail/style.scss'
}

const sources = Object.fromEntries(
  Object.entries(files).map(([key, file]) => [key, fs.readFileSync(path.join(root, file), 'utf8')])
)

const failures = []

const requiredListMarkers = [
  'function displayOrderAmountLabel(item: OrderListItemResponse): string',
  'function displayOrderAmountValue(item: OrderListItemResponse): string',
  'function sellerSettlementText(item: OrderListItemResponse): string',
  'function platformMarkupText(item: OrderListItemResponse): string',
  'item.role === \'seller\'',
  '可结算',
  '应付',
  '卖家结算',
  '平台加价',
  '<view class="goods-price-label">{{ displayOrderAmountLabel(item) }}</view>',
  '<view class="goods-price">¥{{ displayOrderAmountValue(item) }}</view>',
  '<view v-if="item.role === \'seller\'" class="settlement-line">'
]

for (const marker of requiredListMarkers) {
  if (!sources.list.includes(marker)) failures.push(`${files.list}: missing markup-aware order list marker: ${marker}`)
}

const requiredDetailMarkers = [
  'function formatMoneyAmount(value: unknown): string',
  'function formatMarkupRate(value: unknown): string',
  'const sellerAmountText = computed(() => formatMoneyAmount(order.value?.sellerAmount ?? order.value?.amount))',
  'const platformMarkupText = computed(() => formatMoneyAmount(order.value?.platformMarkupAmount ?? 0))',
  'const platformMarkupRateText = computed(() => formatMarkupRate(order.value?.platformMarkupRate))',
  '买家应付',
  '卖家结算',
  '平台加价',
  '加价比例',
  '<view class="goods-price-label">买家应付</view>',
  '<view class="goods-price">¥{{ formatMoneyAmount(order.amount) }}</view>',
  '<view class="goods-subprice">卖家结算 ¥{{ sellerAmountText }} · 平台加价 ¥{{ platformMarkupText }}</view>'
]

for (const marker of requiredDetailMarkers) {
  if (!sources.detail.includes(marker)) failures.push(`${files.detail}: missing markup-aware order detail marker: ${marker}`)
}

const requiredStyleMarkers = {
  [files.listStyle]: [
    '.goods-price-label',
    '.settlement-line',
    '.settlement-line text'
  ],
  [files.detailStyle]: [
    '.goods-price-label',
    '.goods-subprice'
  ]
}

for (const [file, markers] of Object.entries(requiredStyleMarkers)) {
  const key = Object.keys(files).find((name) => files[name] === file)
  const source = sources[key]
  for (const marker of markers) {
    if (!source.includes(marker)) failures.push(`${file}: missing markup display style marker: ${marker}`)
  }
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('order markup display guard passed')
