const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/orders/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const requiredMarkers = [
  'function formatMoney(value: unknown): string',
  'function formatRate(value: unknown): string',
  'function sellerAmountText(item: AdminOrderDetail): string',
  'function platformMarkupText(item: AdminOrderDetail): string',
  'function platformMarkupRateText(item: AdminOrderDetail): string',
  '买家付款',
  '卖家结算',
  '平台加价',
  '加价比例',
  '<td><div class="money-stack">',
  '<strong>买家付款 ¥{{ formatMoney(item.amount) }}</strong>',
  '<span>卖家结算 ¥{{ sellerAmountText(item) }}</span>',
  '<span>平台加价 ¥{{ platformMarkupText(item) }}</span>',
  '<div><dt>买家付款</dt><dd>¥{{ formatMoney(detail.amount) }}</dd></div>',
  '<div><dt>卖家结算</dt><dd>¥{{ sellerAmountText(detail) }}</dd></div>',
  '<div><dt>平台加价</dt><dd>¥{{ platformMarkupText(detail) }}</dd></div>',
  '<div><dt>加价比例</dt><dd>{{ platformMarkupRateText(detail) }}</dd></div>',
  '.money-stack'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing admin order markup display marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('admin order markup display guard passed')
