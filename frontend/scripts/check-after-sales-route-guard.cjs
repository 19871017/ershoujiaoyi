const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const applyFile = 'src/pages/after-sales/apply/index.vue'
const detailFile = 'src/pages/after-sales/detail/index.vue'
const apply = fs.readFileSync(path.join(root, applyFile), 'utf8')
const detail = fs.readFileSync(path.join(root, detailFile), 'utf8')
const failures = []

const requiredBackendOrderMarkers = [
  'function isValidBackendOrderNo(value: string)',
  'isValidBackendOrderNo(orderNo.value)',
  '缺少有效订单号，请从订单详情发起售后',
  'return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)',
]

for (const marker of requiredBackendOrderMarkers) {
  if (!apply.includes(marker)) failures.push(`${applyFile}: missing strict backend orderNo guard marker: ${marker}`)
}

const forbiddenApplyMarkers = [
  'if (!orderNo.value) return',
  "current?.options?.orderNo || hashParams?.get('orderNo') || ''",
  'orderNo: orderNo.value,'
]
for (const marker of forbiddenApplyMarkers) {
  if (apply.includes(marker)) failures.push(`${applyFile}: after-sales apply must not trust a non-empty route orderNo directly: ${marker}`)
}

if (!/const\s+validatedOrderNo\s*=\s*orderNo\.value/.test(apply)) {
  failures.push(`${applyFile}: submitApply must snapshot the validated backend orderNo before async createAfterSales`)
}
if (!/orderNo:\s*validatedOrderNo/.test(apply)) {
  failures.push(`${applyFile}: createAfterSales must submit the validated orderNo snapshot, not mutable route state`)
}

const forbiddenApplyTrustCopy = [
  '售后单 ${response.afterSalesNo} 已进入平台审核。',
  '平台售后',
  '平台审核',
  '凭证图片',
]
for (const marker of forbiddenApplyTrustCopy) {
  if (apply.includes(marker)) failures.push(`${applyFile}: avoid static platform-risk/audit or credential copy before backend-derived review state: ${marker}`)
}

const requiredNeutralCopy = [
  '订单售后',
  '交易请保留照片、聊天记录和物流材料；售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准。',
  '售后申请已提交，处理进度以后端记录为准。',
]
for (const marker of requiredNeutralCopy) {
  if (!apply.includes(marker)) failures.push(`${applyFile}: missing neutral after-sales copy marker: ${marker}`)
}

const requiredAfterSalesNoMarkers = [
  'function isValidAfterSalesNo(value: string)',
  'isValidAfterSalesNo(afterSalesNo.value)',
  '缺少有效售后单号，请从售后申请成功页进入',
  'detail.value = null',
]
for (const marker of requiredAfterSalesNoMarkers) {
  if (!detail.includes(marker)) failures.push(`${detailFile}: missing strict afterSalesNo fail-closed marker: ${marker}`)
}
if (!detail.includes('return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)')) {
  failures.push(`${detailFile}: after-sales detail must require canonical AS-* backend afterSalesNo values`)
}

const forbiddenDetailMarkers = [
  'if (!afterSalesNo.value) { errorText.value',
  '平台凭证',
  '平台会核对',
  '平台已通过',
  '平台已驳回',
  '平台处理中',
]
for (const marker of forbiddenDetailMarkers) {
  if (detail.includes(marker)) failures.push(`${detailFile}: after-sales detail must fail closed on canonical IDs and avoid static risk-control copy: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('after-sales route guards and neutral ticket copy check passed')
