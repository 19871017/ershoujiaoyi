const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/product/detail/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  "const seller = { name: '小原圈主理人'",
  'credit: 98',
  'deals: 36',
  "city: '深圳'",
  "response: '通常10分钟回复'",
  "tags: ['已实名', '平台担保', '同城可约看']",
  '信用 {{ seller.credit }}',
  '成交 {{ seller.deals }} 单',
  'seller.tags'
]

const forbiddenRegexes = [
  {
    pattern: /const\s+sellerTags\s*=\s*computed\(\s*\(\)\s*=>\s*\[[^\]]*(?:平台担保|已实名|视频认证|同城交易)[^\]]*\]\s*\)/,
    message: 'sellerTags must not synthesize fixed trust badges such as 平台担保/已实名/视频认证/同城交易 on product detail; render backend-derived tags or an empty neutral list'
  },
  {
    pattern: /const\s+safeRules\s*=\s*\[[\s\S]*?title:\s*['"]平台担保['"][\s\S]*?\]/,
    message: 'transaction safety copy must not present 平台担保 as a static seller/product trust badge without backend escrow state'
  },
  {
    pattern: /担保下单/,
    message: 'product detail primary order CTA must not statically assert escrow/guarantee state; use neutral order confirmation copy until backend payment/escrow state exists'
  },
  {
    pattern: /已阅读平台担保交易规则/,
    message: 'product detail pre-order checklist must not require acknowledgement of static platform escrow rules before backend order/payment state proves escrow'
  }
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static seller trust marker found: ${marker}`)
}

for (const { pattern, message } of forbiddenRegexes) {
  if (pattern.test(source)) failures.push(`${file}: ${message}`)
}

const requiredMarkers = [
  'sellerName',
  'sellerCity',
  'sellerTrustText',
  'sellerTags',
  '商品卖家信息以服务端返回为准',
  '暂无服务端信用/成交统计',
  '平台交易',
  '订单、支付和售后状态以服务端记录为准'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived/neutral seller marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('product detail seller trust data is backend-derived or neutral')
