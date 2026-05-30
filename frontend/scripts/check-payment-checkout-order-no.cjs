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
  'function decodeRouteValue(fieldName: string, value: string): string',
  "console.warn('payment checkout route decode failed'",
  "console.warn('payment checkout invalid route orderNo'",
  'function isValidBackendOrderNo(value: string)',
  'function isValidOrderAmount(value: unknown): boolean',
  'isValidBackendOrderNo(orderNo.value)',
  '缺少有效订单号，不能进入收银台',
  'order.value = null',
  "throw new Error('payment checkout invalid backend orderNo')",
  "throw new Error('payment checkout orderNo mismatch')",
  "throw new Error('payment checkout invalid order amount')",
  "console.warn('payment checkout order load failed'",
  "console.warn('payment checkout method navigation failed'",
  "throw new Error('payment checkout invalid paid orderNo')",
  "throw new Error('payment checkout pay orderNo mismatch')",
  "throw new Error('payment checkout refreshed orderNo mismatch')",
  "throw new Error('payment checkout invalid refreshed order amount')",
  "console.warn('payment checkout success modal failed'",
  "console.warn('payment checkout order detail redirect failed'",
  "console.warn('payment checkout wallet pay failed'",
  'paymentRequestSubmitted',
  '支付请求已提交，但暂时无法确认结果，请进入订单详情刷新，勿重复支付。',
]

for (const marker of requiredCheckoutMarkers) {
  if (!checkout.includes(marker)) failures.push(`${checkoutFile}: missing strict backend orderNo fail-closed marker: ${marker}`)
}

if (!checkout.includes('const backendOrderNoPattern = /^OD-[0-9]{1,10}$/')) {
  failures.push(`${checkoutFile}: isValidBackendOrderNo must require backend-generated OD numeric order numbers and reject PREVIEW/UNKNOWN/sample IDs`)
}

if (!checkout.includes('encodeURIComponent(safeOrderNo)')) {
  failures.push(`${checkoutFile}: third-party method navigation must use a validated backend-derived safeOrderNo after detail load`)
}

if (!checkout.includes('payOrder(safeOrderNo)')) {
  failures.push(`${checkoutFile}: wallet payment must use validated safeOrderNo after detail load`)
}

const forbiddenWalletPaymentMarkers = [
  'payOrder(order.value.orderNo)',
  'payOrder(orderNo.value)'
]
for (const marker of forbiddenWalletPaymentMarkers) {
  if (checkout.includes(marker)) failures.push(`${checkoutFile}: wallet payment must not use mutable or route-derived orderNo directly: ${marker}`)
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
  'import { getOrderDetail }',
  'function decodeRouteValue(fieldName: string, value: string): string',
  "console.warn('payment method route decode failed'",
  "console.warn('payment method invalid route method'",
  "console.warn('payment method invalid route orderNo'",
  'routeErrorText',
  '支付方式参数异常，未进入任何第三方支付通道。',
  '订单链接异常，请返回订单详情重新进入支付。',
  'function isValidBackendOrderNo(value: string)',
  'isValidBackendOrderNo(safeOrderNo)',
  '订单号无效，已清除订单信息并返回收银台重新读取',
  "console.warn('payment method clean checkout redirect failed'",
  'await getOrderDetail(safeOrderNo)',
  'orderNo: detail.orderNo',
  "throw new Error('订单编号校验不一致，已阻止返回收银台')",
  "console.warn('payment method checkout redirect failed'",
  "console.warn('payment method order reload failed'",
]
for (const marker of requiredMethodMarkers) {
  if (!method.includes(marker)) failures.push(`${methodFile}: missing route-orderNo neutral/fail-closed marker: ${marker}`)
}

if (!method.includes('return /^OD-[0-9]{1,10}$/.test(value)')) {
  failures.push(`${methodFile}: payment method return flow must require backend-generated OD numeric order numbers before redirecting to checkout`)
}

if (method.includes("method.value = value === 'ALIPAY' ? 'ALIPAY' : 'WECHAT'")) {
  failures.push(`${methodFile}: payment method route must not silently fallback invalid method values to WECHAT`)
}

if (!method.includes('function decodeRouteValue(fieldName: string, value: string): string') || !method.includes("console.warn('payment method route decode failed'") || !/orderNo\.value\s*=\s*routeOrderNo/.test(method)) {
  failures.push(`${methodFile}: route orderNo must be decoded fail-closed with diagnostics before validation`)
}

if (!method.includes('isValidBackendOrderNo(detail.orderNo)')) {
  failures.push(`${methodFile}: backend-returned detail.orderNo must be validated before checkout redirect`)
}

if (/navigateBack\(\{\s*delta:\s*1\s*\}\)/.test(method)) {
  failures.push(`${methodFile}: invalid payment method route orderNo must not navigateBack to a stale checkout carrying the same unsafe route params`)
}

if (!method.includes("url: '/pages/payment/checkout/index'") || !method.includes('uni.redirectTo(cleanRoute)')) {
  failures.push(`${methodFile}: invalid payment method route orderNo must redirect to a clean checkout entry without propagating route orderNo`)
}

if (!/async function backToCheckout\(\)(?:: Promise<void>)?\s*\{[\s\S]*const safeOrderNo = orderNo\.value[\s\S]*isValidBackendOrderNo\(safeOrderNo\)[\s\S]*await getOrderDetail\(safeOrderNo\)[\s\S]*detail\.orderNo !== safeOrderNo[\s\S]*orderNo: detail\.orderNo[\s\S]*encodeURIComponent\(checkoutRoute\.orderNo\)/s.test(method)) {
  failures.push(`${methodFile}: backToCheckout must rehydrate backend order detail with validated safeOrderNo, verify returned orderNo, and redirect with backend-derived detail.orderNo`)
}

const requiredLogisticsMarkers = [
  'function isValidBackendOrderNo(value: string): boolean',
  'isValidBackendOrderNo(orderNo.value)',
  '缺少有效订单号，请从订单详情进入',
  'order.value = null',
  'isValidBackendOrderNo(detail.orderNo)',
  "console.warn('order logistics route decode failed'",
  "console.warn('order logistics invalid route orderNo'",
  "throw new Error('order logistics invalid backend orderNo')",
  "throw new Error('order logistics orderNo mismatch')",
  "console.warn('order logistics load failed'"
]
for (const marker of requiredLogisticsMarkers) {
  if (!logistics.includes(marker)) failures.push(`${logisticsFile}: logistics page must validate backend orderNo and clear order state before loading: ${marker}`)
}

if (!logistics.includes('function decodeRouteValue(fieldName: string, value: string): string') || !/catch \(error\)\s*\{[\s\S]*console\.warn\('order logistics route decode failed'[\s\S]*return ''[\s\S]*\}/.test(logistics) || !/orderNo\.value\s*=\s*routeOrderNo/.test(logistics)) {
  failures.push(`${logisticsFile}: route orderNo must be decoded fail-closed with diagnostics before validation`)
}

if (!logistics.includes('const backendOrderNoPattern = /^OD-[0-9]{1,10}$/') && !logistics.includes('return /^OD-[0-9]{1,10}$/.test(value)')) {
  failures.push(`${logisticsFile}: logistics page must require backend-generated OD numeric order numbers`)
}

if (!/async function load\(\)(?:: Promise<void>)?\s*\{[\s\S]*if \(!isValidBackendOrderNo\(orderNo\.value\)\)\s*\{[\s\S]*errorText\.value = '缺少有效订单号，请从订单详情进入'[\s\S]*order\.value = null[\s\S]*return[\s\S]*\}[\s\S]*const safeOrderNo = orderNo\.value[\s\S]*const detail = await getOrderDetail\(safeOrderNo\)[\s\S]*if \(!isValidBackendOrderNo\(detail\.orderNo\)\) throw new Error\('order logistics invalid backend orderNo'\)[\s\S]*if \(detail\.orderNo !== safeOrderNo\) throw new Error\('order logistics orderNo mismatch'\)[\s\S]*order\.value = detail/s.test(logistics)) {
  failures.push(`${logisticsFile}: logistics load must return before backend calls for invalid route orderNo and must validate backend detail.orderNo before display`)
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
