const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const applyFile = 'src/pages/after-sales/apply/index.vue'
const detailFile = 'src/pages/after-sales/detail/index.vue'
const apply = fs.readFileSync(path.join(root, applyFile), 'utf8')
const detail = fs.readFileSync(path.join(root, detailFile), 'utf8')
const failures = []

const requiredBackendOrderMarkers = [
  'const backendOrderNoPattern = /^OD-[0-9]{1,10}$/',
  'function isValidBackendOrderNo(value: string)',
  'isValidBackendOrderNo(orderNo.value)',
  '缺少有效订单号，请从订单详情发起售后',
  "console.warn('after-sales apply route decode failed'",
  "console.warn('after-sales apply invalid route orderNo'",
  "const submittedAfterSalesNo = ref('')",
  'function isValidRefundAmount(value: unknown): boolean',
  'function assertAfterSalesResponse(response: AfterSalesResponse, expectedOrderNo: string): void',
  "throw new Error('after-sales apply invalid afterSalesNo')",
  "throw new Error('after-sales apply invalid backend orderNo')",
  "throw new Error('after-sales apply orderNo mismatch')",
  "throw new Error('after-sales apply invalid refund amount')",
  "console.warn('after-sales apply evidence upload failed'",
  "console.warn('after-sales apply choose evidence failed'",
  "console.warn('after-sales apply submit failed'",
  "console.warn('after-sales apply submit ignored because submission is already in progress'",
  "console.warn('after-sales apply input invalid'",
  "console.warn('after-sales apply success modal failed'",
  "console.warn('after-sales apply detail redirect failed'",
  'submittedAfterSalesNo.value = response.afterSalesNo',
  "url.startsWith('blob:')",
  "url.startsWith('data:')",
  "lower.includes('%2e')",
  "lower.includes('%2f')",
  "lower.includes('%5c')",
  "url.includes('\\\\')",
  'relativePath.split(\'/\').some(segment => !segment',
]

for (const marker of requiredBackendOrderMarkers) {
  if (!apply.includes(marker)) failures.push(`${applyFile}: missing strict backend orderNo guard marker: ${marker}`)
}

const hasFailClosedRouteDecode =
  /function decodeRouteValue\(fieldName: string, value: string\): string\s*\{[\s\S]*?catch \(error\)\s*\{[\s\S]*?console\.warn\('after-sales apply route decode failed'[\s\S]*?return ''[\s\S]*?\}[\s\S]*?\}\s*function assertAfterSalesResponse/.test(apply) &&
  /const routeOrderNo\s*=\s*decodeRouteValue\('orderNo',/.test(apply)

if (!hasFailClosedRouteDecode) {
  failures.push(`${applyFile}: route orderNo must be decoded fail-closed with diagnostics before validation`)
}

const forbiddenApplyMarkers = [
  'if (!orderNo.value) return',
  "current?.options?.orderNo || hashParams?.get('orderNo') || ''",
  'orderNo: orderNo.value,',
  'v-model=',
  'v-model.trim='
]
for (const marker of forbiddenApplyMarkers) {
  if (apply.includes(marker)) failures.push(`${applyFile}: after-sales apply must not trust a non-empty route orderNo directly: ${marker}`)
}

if (!/const\s+safeOrderNo\s*=\s*orderNo\.value/.test(apply)) {
  failures.push(`${applyFile}: submitApply must snapshot the validated backend orderNo before async createAfterSales`)
}
if (!/orderNo:\s*safeOrderNo/.test(apply)) {
  failures.push(`${applyFile}: createAfterSales must submit the validated orderNo snapshot, not mutable route state`)
}
if (!apply.includes('function isValidAfterSalesNo(value: string): boolean') || !apply.includes('assertAfterSalesResponse(response, safeOrderNo)') || !apply.includes("throw new Error('after-sales apply invalid evidence url')")) {
  failures.push(`${applyFile}: submitApply must validate backend-returned afterSalesNo/orderNo/refundAmount/evidenceUrls before showing success or redirecting`)
}
if (!/async function submitApply\(\)(?:: Promise<void>)?\s*\{[\s\S]*if \(submitting\.value\) \{[\s\S]*console\.warn\('after-sales apply submit ignored because submission is already in progress'[\s\S]*const safeOrderNo = orderNo\.value[\s\S]*const safeRefundAmount = validateRefundAmount\(\)[\s\S]*const safeDescription = desc\.value\.trim\(\)[\s\S]*const response = await createAfterSales\(\{ orderNo: safeOrderNo[\s\S]*assertAfterSalesResponse\(response, safeOrderNo\)[\s\S]*submittedAfterSalesNo\.value = response\.afterSalesNo[\s\S]*try\s*\{\s*uni\.showModal\(modalOptions\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('after-sales apply success modal failed'[\s\S]*redirectToAfterSalesDetail\(response\.afterSalesNo, safeOrderNo\)[\s\S]*console\.warn\('after-sales apply submit failed'/s.test(apply)) {
  failures.push(`${applyFile}: submitApply must use validated snapshots, block duplicate submits, validate backend response, and treat success-modal failures as submitted`)
}
if (!/function chooseEvidence\(\): void\s*\{[\s\S]*uploadingEvidence\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage\([\s\S]*catch \(error\)\s*\{[\s\S]*uploadingEvidence\.value = false[\s\S]*console\.warn\('after-sales apply choose evidence failed'/s.test(apply)) {
  failures.push(`${applyFile}: evidence chooser must clear busy state and log if uni.chooseImage throws synchronously`)
}
if (!/function redirectToAfterSalesDetail\(afterSalesNo: string, safeOrderNo: string\): void\s*\{[\s\S]*console\.warn\('after-sales apply detail redirect failed'[\s\S]*try\s*\{\s*uni\.redirectTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('after-sales apply detail redirect failed'/s.test(apply)) {
  failures.push(`${applyFile}: after-sales success redirect must handle async and synchronous navigation failures`)
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
  'const safeRouteOrderNo = isValidBackendOrderNo(orderNo.value) ? orderNo.value : undefined',
  'detail.value = null',
  'function assertAfterSalesDetailResponse(response: AfterSalesResponse, expectedAfterSalesNo: string, expectedOrderNo?: string): void',
  "throw new Error('after-sales detail invalid backend afterSalesNo')",
  "throw new Error('after-sales detail afterSalesNo mismatch')",
  "throw new Error('after-sales detail invalid backend orderNo')",
  "throw new Error('after-sales detail orderNo mismatch')",
  "throw new Error('after-sales detail invalid refund amount')",
  "throw new Error('after-sales detail invalid backend status')",
  "throw new Error('after-sales detail invalid sellerId')",
  "throw new Error('after-sales detail invalid evidence url')",
  "console.warn('after-sales detail invalid route afterSalesNo'",
  "console.warn('after-sales detail invalid route orderNo'",
  '缺少有效订单号，不能补充票据',
  '暂时无法打开关联订单，请稍后重试',
  '暂时无法打开聊天，请稍后重试',
  '暂时无法打开票据上传页，请稍后重试',
  "console.warn('after-sales order navigation failed'",
  "console.warn('after-sales chat navigation failed'",
  "console.warn('after-sales evidence navigation failed'",
  "console.warn('after-sales report navigation failed'",
  "console.warn('after-sales detail initialize failed'",
  "return map[status] || '未知状态'",
  "return map[type] || '未知类型'",
  "url.startsWith('blob:')",
  "url.startsWith('data:')",
  "typeof url !== 'string'",
  'response.evidenceUrls.length === 0',
  "url.includes('..')",
  "url.includes('//')",
  "url.includes('\\\\')",
  "lower.includes('%2e')",
  "lower.includes('%2f')",
  "lower.includes('%5c')",
  'relativePath.split(\'/\').some(segment => !segment',
  '^\\/uploads\\/evidence\\/after-sales\\/[A-Za-z0-9][A-Za-z0-9._/-]*$',
]
for (const marker of requiredAfterSalesNoMarkers) {
  if (!detail.includes(marker)) failures.push(`${detailFile}: missing strict afterSalesNo fail-closed marker: ${marker}`)
}
if (!detail.includes('return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)')) {
  failures.push(`${detailFile}: after-sales detail must require canonical AS-* backend afterSalesNo values`)
}
if (!detail.includes('const backendOrderNoPattern = /^OD-[0-9]{1,10}$/')) {
  failures.push(`${detailFile}: after-sales detail must require backend-generated OD numeric order numbers before ticket/chat navigation`)
}
if (!detail.includes("const evidenceStoragePrefix = '/uploads/evidence/after-sales/'") || !detail.includes('!url.startsWith(evidenceStoragePrefix)')) {
  failures.push(`${detailFile}: after-sales detail must reject non-ticket evidence URLs before display`)
}
for (const marker of ["lower.includes('%2e')", "lower.includes('%2f')", "lower.includes('%5c')", "url.includes('\\\\')", "relativePath.split('/').some(segment => !segment"]) {
  if (!detail.includes(marker)) failures.push(`${detailFile}: after-sales detail evidence URL validation must reject encoded traversal, backslashes, and empty path segments: ${marker}`)
}
const detailHasFailClosedRouteDecode =
  /function decodeRouteValue\(fieldName: string, value: string\): string\s*\{[\s\S]*?catch \(error\)\s*\{[\s\S]*?console\.warn\('after-sales detail route decode failed'[\s\S]*?return ''[\s\S]*?\}[\s\S]*?\}\s*function readQuery/.test(detail) &&
  /const routeAfterSalesNo\s*=\s*decodeRouteValue\('afterSalesNo',/.test(detail) &&
  /const routeOrderNo\s*=\s*decodeRouteValue\('orderNo',/.test(detail)
if (!detailHasFailClosedRouteDecode) {
  failures.push(`${detailFile}: route afterSalesNo/orderNo must be decoded fail-closed before validation`)
}
if (!/async function loadDetail\(\)(?:: Promise<void>)?\s*\{[\s\S]*if \(!isValidAfterSalesNo\(afterSalesNo\.value\)\)[\s\S]*const safeAfterSalesNo = afterSalesNo\.value[\s\S]*const safeRouteOrderNo = isValidBackendOrderNo\(orderNo\.value\) \? orderNo\.value : undefined[\s\S]*const response = await getAfterSalesDetail\(safeAfterSalesNo\)[\s\S]*assertAfterSalesDetailResponse\(response, safeAfterSalesNo, safeRouteOrderNo\)[\s\S]*detail\.value = response[\s\S]*orderNo\.value = response\.orderNo[\s\S]*console\.warn\('after-sales detail load failed'/s.test(detail)) {
  failures.push(`${detailFile}: loadDetail must fail closed on afterSalesNo, validate backend detail response, and log failures before display`)
}
if (!/function assertAfterSalesDetailResponse\(response: AfterSalesResponse, expectedAfterSalesNo: string, expectedOrderNo\?: string\): void\s*\{[\s\S]*if \(expectedOrderNo && response\.orderNo !== expectedOrderNo\) throw new Error\('after-sales detail orderNo mismatch'\)/s.test(detail)) {
  failures.push(`${detailFile}: after-sales detail must reject stale route orderNo when present without blocking backend-derived detail links`)
}
if (!/function contactSeller\(\): void\s*\{[\s\S]*if \(!isValidAfterSalesNo\(currentDetail\.afterSalesNo\)\)[\s\S]*if \(!isValidBackendOrderNo\(currentDetail\.orderNo\)\)[\s\S]*try\s*\{[\s\S]*const target = resolveSellerContactTarget\(currentDetail[\s\S]*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('after-sales chat navigation failed'/s.test(detail)) {
  failures.push(`${detailFile}: after-sales chat navigation must validate IDs and handle prep/async/synchronous failures`)
}
if (!/function openOrderDetail\(\): void\s*\{[\s\S]*if \(!isValidAfterSalesNo\(currentDetail\.afterSalesNo\)\)[\s\S]*if \(!isValidBackendOrderNo\(currentDetail\.orderNo\)\)[\s\S]*\/pages\/order\/detail\/index\?orderNo=\$\{encodeURIComponent\(currentDetail\.orderNo\)\}[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('after-sales order navigation failed'/s.test(detail)) {
  failures.push(`${detailFile}: after-sales detail must let users return to the associated order with validated IDs and navigation failure handling`)
}
if (!/function addEvidence\(\): void\s*\{[\s\S]*if \(!currentDetail \|\| !isValidAfterSalesNo\(currentDetail\.afterSalesNo\)\)[\s\S]*if \(!isValidBackendOrderNo\(currentDetail\.orderNo\)\)[\s\S]*scene=AFTER_SALES_EVIDENCE[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('after-sales evidence navigation failed'/s.test(detail)) {
  failures.push(`${detailFile}: after-sales evidence navigation must use validated backend detail IDs and handle async/synchronous failures`)
}
if (!/function reportAfterSales\(\): void\s*\{[\s\S]*if \(!currentDetail \|\| !isValidAfterSalesNo\(currentDetail\.afterSalesNo\)\)[\s\S]*if \(!isValidBackendOrderNo\(currentDetail\.orderNo\)\)[\s\S]*targetType=AFTER_SALES&targetId=\$\{encodeURIComponent\(safeAfterSalesNo\)\}[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('after-sales report navigation failed'/s.test(detail)) {
  failures.push(`${detailFile}: after-sales report navigation must use validated backend afterSalesNo/orderNo and handle async/synchronous failures`)
}
if (!/async function initializeDetail\(\): Promise<void>\s*\{[\s\S]*try\s*\{[\s\S]*readQuery\(\)[\s\S]*await loadDetail\(\)[\s\S]*catch \(error\)\s*\{[\s\S]*detail\.value = null[\s\S]*loading\.value = false[\s\S]*console\.warn\('after-sales detail initialize failed'[\s\S]*errorText\.value = '售后详情读取失败，请从售后申请记录重新进入'/s.test(detail)) {
  failures.push(`${detailFile}: after-sales detail initialization must fail closed if route parsing throws synchronously`)
}

const requiredDetailNextStepMarkers = [
  'const nextAction = computed<NextAction | null>',
  '售后单已进入处理队列，可先补充票据或联系卖家协商；最终进度以服务端订单、支付、物流、聊天记录和售后记录为准。',
  '售后申请已通过，请查看关联订单和售后处理记录；后续结果以服务端订单、支付、物流和售后记录为准。',
  '售后申请已驳回，如仍需处理，可补充票据并联系卖家继续协商；不要在聊天外完成交易或退款约定。',
  '该售后单已取消，可查看关联订单确认当前订单状态，必要时再联系卖家沟通后续处理。',
  'function runNextAction(): void',
  "if (action === 'order') return openOrderDetail()",
  "if (action === 'chat') return contactSeller()",
  "if (action === 'evidence') return addEvidence()",
  '可用操作',
]
for (const marker of requiredDetailNextStepMarkers) {
  if (!detail.includes(marker)) failures.push(`${detailFile}: missing status-specific after-sales next-step marker: ${marker}`)
}

const forbiddenDetailMarkers = [
  'if (!afterSalesNo.value) { errorText.value',
  '平台凭证',
  '平台会核对',
  '平台已通过',
  '平台已驳回',
  '平台处理中',
  '退款已到账',
  '退款已处理',
  '原路退回',
  '支付通道已完成',
  '系统自动退款',
]
for (const marker of forbiddenDetailMarkers) {
  if (detail.includes(marker)) failures.push(`${detailFile}: after-sales detail must fail closed on canonical IDs and avoid static risk-control copy: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('after-sales route guards and neutral ticket copy check passed')
