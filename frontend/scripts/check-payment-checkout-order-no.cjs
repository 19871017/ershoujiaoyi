const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const checkoutFile = 'src/pages/payment/checkout/index.vue'
const methodFile = 'src/pages/payment/method/index.vue'
const logisticsFile = 'src/pages/order/logistics/index.vue'
const checkout = fs.readFileSync(path.join(root, checkoutFile), 'utf8')
const method = fs.readFileSync(path.join(root, methodFile), 'utf8')
const logistics = fs.readFileSync(path.join(root, logisticsFile), 'utf8')
const failures = []

const forbiddenCheckoutMarkers = [
  "orderNo.value.startsWith('PREVIEW')",
  'orderNo.value.startsWith("PREVIEW")',
  "!orderNo.value || orderNo.value.startsWith('PREVIEW')",
]

for (const marker of forbiddenCheckoutMarkers) {
  if (checkout.includes(marker)) failures.push(`${checkoutFile}: payment checkout must not only block PREVIEW order numbers: ${marker}`)
}

const requiredCheckoutMarkers = [
  'function isValidBackendOrderNo(value: string)',
  'isValidBackendOrderNo(orderNo.value)',
  '缺少有效订单号，不能进入收银台',
  'order.value = null',
]

for (const marker of requiredCheckoutMarkers) {
  if (!checkout.includes(marker)) failures.push(`${checkoutFile}: missing strict backend orderNo fail-closed marker: ${marker}`)
}

if (!checkout.includes('return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)')) {
  failures.push(`${checkoutFile}: isValidBackendOrderNo must require canonical backend order numbers like ORD-xxxx and reject PREVIEW/UNKNOWN/sample IDs`)
}

if (!checkout.includes('encodeURIComponent(order.value.orderNo)')) {
  failures.push(`${checkoutFile}: third-party method navigation must use backend-derived order.value.orderNo after detail load`)
}

const forbiddenCheckoutTrustCopy = [
  '资金进入平台担保流程',
  '担保金额',
  '平台担保交易',
  '资金流入平台担保账户',
  '已进入平台担保'
]
for (const marker of forbiddenCheckoutTrustCopy) {
  if (checkout.includes(marker)) failures.push(`${checkoutFile}: checkout must not assert escrow/guarantee state unless backend exposes explicit escrow/payment custody state: ${marker}`)
}

const requiredCheckoutNeutralCopy = [
  '支付前再次读取真实订单状态和付款状态。',
  '应付金额',
  '平台交易不要求私下转账',
  '优先使用可用余额，支付结果以服务端订单状态为准',
  '已完成支付请求，正在返回订单详情。'
]
for (const marker of requiredCheckoutNeutralCopy) {
  if (!checkout.includes(marker)) failures.push(`${checkoutFile}: missing neutral backend-state payment copy: ${marker}`)
}

const forbiddenMethodMarkers = [
  '<view v-if="orderNo" class="order-card ds-card">',
  '<text>{{ orderNo }}</text>',
  '返回收银台选择钱包余额',
]
for (const marker of forbiddenMethodMarkers) {
  if (method.includes(marker)) failures.push(`${methodFile}: third-party payment method page must not display or trust route orderNo directly: ${marker}`)
}

const requiredMethodMarkers = [
  '订单号需返回安全收银台重新读取',
  '支付方式页不展示路由传入的订单号',
  'function isValidBackendOrderNo(value: string)',
  'isValidBackendOrderNo(orderNo.value)',
  '订单号无效，已阻止返回收银台',
]
for (const marker of requiredMethodMarkers) {
  if (!method.includes(marker)) failures.push(`${methodFile}: missing route-orderNo neutral/fail-closed marker: ${marker}`)
}

if (!method.includes('return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)')) {
  failures.push(`${methodFile}: payment method return flow must require canonical backend order numbers before redirecting to checkout`)
}

if (!/function backToCheckout\(\)\{\s*if\s*\(\s*isValidBackendOrderNo\(orderNo\.value\)\s*\)/s.test(method)) {
  failures.push(`${methodFile}: backToCheckout must validate route orderNo with isValidBackendOrderNo before redirecting`)
}

const requiredLogisticsMarkers = [
  'function isValidBackendOrderNo(value: string)',
  'isValidBackendOrderNo(orderNo.value)',
  '缺少有效订单号，请从订单详情进入',
  'order.value = null'
]
for (const marker of requiredLogisticsMarkers) {
  if (!logistics.includes(marker)) failures.push(`${logisticsFile}: logistics page must validate backend orderNo and clear order state before loading: ${marker}`)
}

const forbiddenLogisticsMarkers = [
  'if (!orderNo.value)',
  "平台担保中",
  "买家完成付款后进入平台担保流程。"
]
for (const marker of forbiddenLogisticsMarkers) {
  if (logistics.includes(marker)) failures.push(`${logisticsFile}: logistics page must not trust non-empty route orderNo or infer escrow copy from local timeline: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('payment checkout validates backend order numbers and payment method page does not expose route orderNo')
