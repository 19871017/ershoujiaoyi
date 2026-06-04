const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/order/detail/index.vue',
  'src/pages/order/list/index.vue',
  'src/pages/order/confirm/index.vue',
  'src/pages/order/logistics/index.vue',
  'src/pages/order/ship/index.vue'
]
const supportFilesByFile = {
  'src/pages/order/detail/index.vue': [
    'src/pages/order/detail/order-detail-helpers.ts'
  ],
  'src/pages/order/list/index.vue': [
    'src/pages/order/list/order-list-helpers.ts'
  ],
  'src/pages/order/confirm/index.vue': [
    'src/pages/order/confirm/order-confirm-helpers.ts'
  ],
  'src/pages/order/ship/index.vue': [
    'src/pages/order/ship/order-ship-helpers.ts'
  ]
}

const forbiddenSuccessCopies = [
  '已提醒卖家发货',
  '取消订单成功',
  '再次购买成功',
  '接口接入中'
]

const forbiddenStaticTrustCopies = [
  '平台担保下单',
  '资金先进入平台担保账户',
  '我同意资金先进入平台担保账户'
]

const forbiddenOrderDetailTrustCopies = [
  '平台担保提示',
  '资金先进入平台担保账户',
  '平台已担保资金',
  '平台会把担保资金结算给卖家',
  '资金已进入结算流程',
  '平台会根据聊天、订单和凭证协助处理',
  '凭证协助处理'
]

const forbiddenOrderLogisticsTrustCopies = [
  '同城交付',
  '小原圈交易流程'
]

const requiredOrderDetailNeutralMarkers = [
  '订单、支付、售后和聊天记录以服务端状态为准',
  '确认收货将调用后端接口完成状态变更'
]

const requiredOrderConfirmBackendMarkers = [
  "const productImageStoragePrefix = '/uploads/product-image/'",
  'const backendProductIdPattern = /^[1-9][0-9]{0,9}$/',
  'const backendOrderNoPattern = /^OD-[0-9]{1,10}$/',
  'function decodeRouteValue(fieldName: string, value: string): string',
  "console.warn('order confirm route decode failed'",
  "console.warn('order confirm invalid route productId'",
  'function isValidBackendProductId(value: unknown): boolean',
  'function isValidBackendOrderNo(value: string): boolean',
  'function isValidOrderAmount(value: unknown): boolean',
  'function validatedProductImageUrl(url: string): string',
  "console.warn('order confirm rejected product image url'",
  "throw new Error('order confirm productId mismatch')",
  "console.warn('order confirm product load failed'",
  "console.warn('order confirm address load failed'",
  'addressLoadFailed',
  '收货信息读取失败',
  "console.warn('order confirm product navigation failed'",
  "console.warn('order confirm address navigation failed'",
  'function assertBackendOrderForCheckout(order: CreateOrderResponse, expectedProductId: number): void',
  'assertBackendOrderForCheckout(order, safeProductId)',
  'if (order.productId !== expectedProductId || order.goodsId !== expectedProductId)',
  'if (!isValidOrderAmount(order.productPrice))',
  'const amount = String(order.productPrice)',
  "console.warn('order confirm checkout navigation failed'",
  "console.warn('order confirm create order failed'",
  "console.warn('order confirm create failure modal failed'",
  '未返回有效订单号不会进入收银台'
]

const requiredOrderIdGuardMarkers = {
  'src/pages/order/detail/index.vue': [
    'const backendOrderNoPattern = /^OD-[0-9]{1,10}$/',
    'function decodeRouteValue(fieldName: string, value: string): string',
    "console.warn('order detail route decode failed'",
    "console.warn('order detail invalid route orderNo'",
    'function isValidBackendOrderNo(value: string)',
    'function isValidAfterSalesNo(value: string): boolean',
    'function isKnownAfterSalesStatus(value: unknown): boolean',
    'function isValidOrderAmount(value: unknown): boolean',
    'function assertBackendOrderDetail(detail: OrderDetailResponse, expectedOrderNo: string): void',
    'function isOrderRole(value: unknown): value is OrderRole',
    'function actionsForOrderDetail(order: OrderDetailResponse, displayStatus: OrderListStatus): string[]',
    "throw new Error('order detail invalid backend orderNo')",
    "throw new Error('order detail orderNo mismatch')",
    "throw new Error('order detail invalid order amount')",
    "throw new Error('order detail invalid role')",
    "throw new Error('order detail invalid afterSalesNo')",
    "throw new Error('order detail invalid afterSalesStatus')",
    '<view v-if="showAfterSalesSummary" class="after-sales-card ds-card">',
    'const showAfterSalesSummary = computed(() => !!order.value?.afterSalesNo)',
    'const afterSalesNextStep = computed(() => {',
    'function openAfterSalesDetail(): void',
    "console.warn('order detail invalid after-sales trace target'",
    'if (!isValidBackendOrderNo(orderNo.value))',
    "console.warn('order detail load failed'",
    "console.warn('order detail checkout navigation failed'",
    "console.warn('order detail ship navigation failed'",
    "console.warn('order detail logistics navigation failed'",
    "console.warn('order detail after-sales apply navigation failed'",
    "console.warn('order detail confirm receipt failed'",
    "console.warn('order detail product navigation failed'",
    "console.warn('order detail report navigation failed'"
  ],
  'src/pages/order/list/index.vue': [
    'const backendOrderNoPattern = /^OD-[0-9]{1,10}$/',
    'function isValidBackendOrderNo(value: string): boolean',
    'function isValidOrderAmount(value: unknown): boolean',
    'function isValidBackendProductId(value: unknown): boolean',
    'function isValidAfterSalesNo(value: string): boolean',
    'function decodeRouteValue(fieldName: string, value: string): string',
    "console.warn('order list route decode failed'",
    'function isOrderRole(value: string): value is OrderRole',
    'function isStatusTab(value: string): value is StatusTab',
    'function readRouteFilters(): void',
    "console.warn('order list invalid route role'",
    "console.warn('order list invalid route status'",
    'function assertBackendOrderListItem(item: OrderListItemResponse): void',
    "throw new Error('order list invalid backend orderNo')",
    "throw new Error('order list invalid order amount')",
    "throw new Error('order list invalid productId')",
    "throw new Error('order list invalid afterSalesNo')",
    'list.forEach(assertBackendOrderListItem)',
    '<view v-if="showAfterSalesTrace" class="after-sales-trace ds-card">',
    'const afterSalesTraceOrders = computed(() => filteredOrders.value.filter((item) => displayStatus(item) === \'REFUNDING\' && !!item.afterSalesNo))',
    'const showAfterSalesTrace = computed(() => !loading.value && !errorText.value && status.value === \'REFUNDING\' && afterSalesTraceOrders.value.length > 0)',
    'function openFirstAfterSalesTrace(): void',
    'function openAfterSalesDetail(item: OrderListItemResponse): void',
    "console.warn('order list invalid after-sales trace target'",
    "console.warn('order list load failed'",
    'if (!isValidBackendOrderNo(item.orderNo))',
    "console.warn('order list checkout navigation failed'",
    "console.warn('order list ship navigation failed'",
    "console.warn('order list logistics navigation failed'",
    "console.warn('order list after-sales apply navigation failed'",
    "console.warn('order list confirm receipt failed'",
    "console.warn('order list detail navigation failed'"
  ],
  'src/pages/order/ship/index.vue': [
    'const backendOrderNoPattern = /^OD-[0-9]{1,10}$/',
    'function decodeRouteValue(fieldName: string, value: string): string',
    "console.warn('order ship route decode failed'",
    "console.warn('order ship invalid route orderNo'",
    'function isValidBackendOrderNo(value: string): boolean',
    'function assertBackendShipResponse(response: ShipOrderResponse, expectedOrderNo: string): void',
    "throw new Error('order ship invalid backend orderNo')",
    "throw new Error('order ship orderNo mismatch')",
    "throw new Error('order ship invalid backend status')",
    "throw new Error('order ship missing shippedAt')",
    'const safeOrderNo = orderNo.value',
    'const safeCompany = company.value.trim()',
    'const safeTrackingNo = trackingNo.value.trim()',
    'const safeRemark = remark.value.trim()',
    'assertBackendShipResponse(response, safeOrderNo)',
    "console.warn('order ship submit failed'",
    "console.warn('order ship success modal failed'",
    "console.warn('order ship redirect failed'"
  ]
}

let failed = false

function reportIssue(message) {
  console.error(message)
  failed = true
}

function rejectIncludedMarkers(file, content, markers, description) {
  for (const marker of markers) {
    if (content.includes(marker)) {
      reportIssue(`${file}: ${description}: ${marker}`)
    }
  }
}

function requireIncludedMarkers(file, content, markers, description) {
  for (const marker of markers) {
    if (!content.includes(marker)) {
      reportIssue(`${file}: ${description}: ${marker}`)
    }
  }
}

function requirePattern(file, content, pattern, message) {
  if (!pattern.test(content)) {
    reportIssue(`${file}: ${message}`)
  }
}

for (const file of files) {
  const absolute = path.join(root, file)
  const supportContent = (supportFilesByFile[file] || [])
    .map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8'))
    .join('\n')
  const content = [supportContent, fs.readFileSync(absolute, 'utf8')].filter(Boolean).join('\n')
  rejectIncludedMarkers(file, content, forbiddenSuccessCopies, 'forbidden fake-success copy found')
  if (file === 'src/pages/order/confirm/index.vue') {
    rejectIncludedMarkers(file, content, forbiddenStaticTrustCopies, 'forbidden pre-order static escrow/trust copy found')
    requireIncludedMarkers(file, content, ['平台订单创建后再进入支付确认', '支付、售后和聊天记录以服务端订单状态为准'], 'missing neutral pre-order trust marker')
    requireIncludedMarkers(file, content, requiredOrderConfirmBackendMarkers, 'missing fail-closed backend order-confirm marker')
    const requiredProductImageGuards = [
      "url.startsWith('local://')",
      "url.startsWith('blob:')",
      '!url.startsWith(productImageStoragePrefix)'
    ]
    if (requiredProductImageGuards.some((marker) => !content.includes(marker))) {
      reportIssue(`${file}: order confirm must reject local/blob/placeholder media and enforce backend product image prefixes before display`)
    }
    if (content.includes('const parsed = Number(raw)') || content.includes('productId.value = Number.isFinite(parsed)')) {
      reportIssue(`${file}: route productId must be decoded fail-closed and validated before product/order requests`)
    }
  }
  if (file === 'src/pages/order/detail/index.vue') {
    rejectIncludedMarkers(file, content, forbiddenOrderDetailTrustCopies, 'forbidden order-detail static escrow/trust copy found')
    requireIncludedMarkers(file, content, requiredOrderDetailNeutralMarkers, 'missing neutral order-detail trust marker')
    requirePattern(
      file,
      content,
      /async function loadDetail\(\)(?:: Promise<void>)?\s*\{[\s\S]*if \(!isValidBackendOrderNo\(orderNo\.value\)\)\s*\{[\s\S]*errorText\.value = '缺少有效订单号，请从订单列表进入'[\s\S]*order\.value = null[\s\S]*return[\s\S]*\}[\s\S]*const safeOrderNo = orderNo\.value[\s\S]*const detail = await getOrderDetail\(safeOrderNo\)[\s\S]*assertBackendOrderDetail\(detail, safeOrderNo\)[\s\S]*order\.value = detail/s,
      'order detail load must return before backend calls for invalid route orderNo and validate backend detail before display'
    )
    requirePattern(
      file,
      content,
      /async function confirmOrderReceipt\(\)(?:: Promise<void>)?\s*\{[\s\S]*const safeOrderNo = validatedOrderNo\('订单编号无效，未确认收货'\)[\s\S]*const detail = await confirmReceipt\(safeOrderNo\)[\s\S]*assertBackendOrderDetail\(detail, safeOrderNo\)[\s\S]*order\.value = detail[\s\S]*console\.warn\('order detail confirm receipt failed'/s,
      'confirm receipt must use validated backend orderNo, validate backend response, and log failures before user copy'
    )
    requirePattern(
      file,
      content,
      /function actionsForOrderDetail\(order: OrderDetailResponse, displayStatus: OrderListStatus\): string\[\]\s*\{[\s\S]*displayStatus === 'PAID'[\s\S]*order\.role === 'seller' \? \['去发货', '联系买家'\] : \['提醒发货', '联系卖家', '申请售后'\][\s\S]*displayStatus === 'SHIPPED'[\s\S]*order\.role === 'buyer' \? \['确认收货', '查看物流', '申请售后'\] : \['查看物流', '联系买家'\][\s\S]*displayStatus === 'COMPLETED'[\s\S]*order\.role === 'buyer' \? \['评价', '申请售后', '联系卖家'\] : \['联系买家'\]/s,
      'order detail actions must be role-aware so seller notification leads to shipping and only buyer can confirm receipt/apply after-sales'
    )
    requirePattern(
      file,
      content,
      /else if \(action === '去发货'\)\s*\{[\s\S]*\/pages\/order\/ship\/index\?orderNo=\$\{encodedOrderNo\}[\s\S]*console\.warn\('order detail ship navigation failed'/s,
      'order detail seller paid action must navigate to the real shipping page with a validated orderNo'
    )
    requirePattern(
      file,
      content,
      /function openAfterSalesDetail\(\): void\s*\{[\s\S]*if \(!isValidBackendOrderNo\(currentOrder\.orderNo\)\)[\s\S]*if \(!currentOrder\.afterSalesNo\)[\s\S]*if \(!isValidAfterSalesNo\(currentOrder\.afterSalesNo\)\)[\s\S]*console\.warn\('order detail invalid after-sales trace target'[\s\S]*const safeOrderNo = currentOrder\.orderNo[\s\S]*const safeAfterSalesNo = currentOrder\.afterSalesNo[\s\S]*\/pages\/after-sales\/detail\/index\?afterSalesNo=\$\{encodeURIComponent\(safeAfterSalesNo\)\}&orderNo=\$\{encodeURIComponent\(safeOrderNo\)\}[\s\S]*console\.warn\('order detail after-sales detail navigation failed'/s,
      'order detail after-sales summary/detail navigation must validate backend orderNo and canonical afterSalesNo before navigation'
    )
    requirePattern(
      file,
      content,
      /function reportOrder\(\): void\s*\{[\s\S]*if \(!currentOrder \|\| !isValidBackendOrderNo\(currentOrder\.orderNo\)\)[\s\S]*const safeOrderNo = currentOrder\.orderNo[\s\S]*\/pages\/report\/submit\/index\?targetType=ORDER&targetId=\$\{encodeURIComponent\(safeOrderNo\)\}[\s\S]*console\.warn\('order detail report navigation failed'/s,
      'order detail report entry must use validated backend orderNo and handle navigation failures'
    )
    requirePattern(
      file,
      content,
      /const afterSalesNextStep = computed\(\(\) => \{[\s\S]*APPROVED[\s\S]*REJECTED[\s\S]*CANCELLED[\s\S]*售后处理中，请保留聊天、物流和票据材料，进度以平台售后详情为准。/s,
      'order detail after-sales summary must show status-specific next steps without implying real refund-channel handling'
    )
  }
  if (file === 'src/pages/order/list/index.vue') {
    requirePattern(
      file,
      content,
      /function readRouteFilters\(\): void\s*\{[\s\S]*const routeRole = decodeRouteValue\('role'[\s\S]*const routeStatus = decodeRouteValue\('status'[\s\S]*if \(routeRole && !isOrderRole\(routeRole\)\)[\s\S]*console\.warn\('order list invalid route role'[\s\S]*else if \(isOrderRole\(routeRole\)\)[\s\S]*role\.value = routeRole[\s\S]*if \(routeStatus && !isStatusTab\(routeStatus\)\)[\s\S]*console\.warn\('order list invalid route status'[\s\S]*else if \(isStatusTab\(routeStatus\)\)[\s\S]*status\.value = routeStatus/s,
      'order list must decode route role/status fail-closed before applying filters'
    )
    requirePattern(
      file,
      content,
      /function openAfterSalesDetail\(item: OrderListItemResponse\): void\s*\{[\s\S]*if \(!isValidBackendOrderNo\(item\.orderNo\)\)[\s\S]*if \(!item\.afterSalesNo\)[\s\S]*if \(!isValidAfterSalesNo\(item\.afterSalesNo\)\)[\s\S]*console\.warn\('order list invalid after-sales trace target'[\s\S]*const safeOrderNo = item\.orderNo[\s\S]*const safeAfterSalesNo = item\.afterSalesNo[\s\S]*\/pages\/after-sales\/detail\/index\?afterSalesNo=\$\{encodeURIComponent\(safeAfterSalesNo\)\}&orderNo=\$\{encodeURIComponent\(safeOrderNo\)\}[\s\S]*console\.warn\('order list after-sales detail navigation failed'/s,
      'order list after-sales trace/detail navigation must validate backend orderNo and canonical afterSalesNo before navigation'
    )
    requirePattern(
      file,
      content,
      /function openFirstAfterSalesTrace\(\): void\s*\{[\s\S]*const target = afterSalesTraceOrders\.value\[0\][\s\S]*if \(!target\) return uni\.showToast\(\{ title: '暂无可追踪售后'[\s\S]*openAfterSalesDetail\(target\)/s,
      'order list after-sales trace header must fail closed when no valid target exists'
    )
    requirePattern(
      file,
      content,
      /async function loadOrders\(\)(?:: Promise<void>)?\s*\{[\s\S]*const list = await listOrders\(role\.value, 'ALL'\)[\s\S]*list\.forEach\(assertBackendOrderListItem\)[\s\S]*orders\.value = list[\s\S]*console\.warn\('order list load failed'/s,
      'order list must validate backend list items before display and log load failures'
    )
    requirePattern(
      file,
      content,
      /function handleAction\(item: OrderListItemResponse, action: string\): void\s*\{[\s\S]*if \(!isValidBackendOrderNo\(item\.orderNo\)\)[\s\S]*const safeOrderNo = item\.orderNo[\s\S]*if \(action === '去付款'\)[\s\S]*isValidOrderAmount\(item\.amount\)[\s\S]*isValidBackendProductId\(item\.productId\)[\s\S]*console\.warn\('order list checkout navigation failed'[\s\S]*else if \(action === '申请售后'\)[\s\S]*isValidOrderAmount\(item\.amount\)/s,
      'order list actions must use validated backend orderNo and amount/product guards before sensitive navigation'
    )
    requirePattern(
      file,
      content,
      /function actionsFor\(item: OrderListItemResponse\): string\[\]\s*\{[\s\S]*current === 'PENDING_PAY'[\s\S]*item\.role === 'buyer' \? \['去付款'\] : \['联系买家'\][\s\S]*current === 'PAID' && item\.role === 'seller'[\s\S]*\['去发货', '联系买家'\][\s\S]*current === 'SHIPPED'[\s\S]*item\.role === 'buyer' \? \['确认收货', '查看物流', '申请售后'\] : \['查看物流', '联系买家'\][\s\S]*current === 'COMPLETED'[\s\S]*item\.role === 'buyer' \? \['评价', '申请售后', '联系卖家'\] : \['联系买家'\]/s,
      'order list actions must be role-aware so sellers do not see buyer-only confirm receipt or after-sales actions'
    )
    requirePattern(
      file,
      content,
      /async function confirmFromList\(item: OrderListItemResponse\)(?:: Promise<void>)?\s*\{[\s\S]*const safeOrderNo = item\.orderNo[\s\S]*const detail = await confirmReceipt\(safeOrderNo\)[\s\S]*if \(!isValidBackendOrderNo\(detail\.orderNo\)\) throw new Error\('order list invalid confirmed orderNo'\)[\s\S]*if \(detail\.orderNo !== safeOrderNo\) throw new Error\('order list confirmed orderNo mismatch'\)[\s\S]*console\.warn\('order list confirm receipt failed'/s,
      'order list confirm receipt must validate backend response before showing success'
    )
  }
  if (file === 'src/pages/order/logistics/index.vue') {
    rejectIncludedMarkers(file, content, forbiddenOrderLogisticsTrustCopies, 'forbidden order-logistics static location/trust copy found')
    requireIncludedMarkers(file, content, ['配送/交付方式以服务端订单记录为准', '订单已创建，后续履约状态以服务端订单、支付和物流记录为准。'], 'missing neutral logistics marker')
  }
  if (file === 'src/pages/order/ship/index.vue') {
    requirePattern(
      file,
      content,
      /function readQuery\(\): void\s*\{[\s\S]*const routeOrderNo = decodeRouteValue\('orderNo'[\s\S]*if \(routeOrderNo && !isValidBackendOrderNo\(routeOrderNo\)\)[\s\S]*if \(!isValidBackendOrderNo\(routeOrderNo\)\)\s*\{[\s\S]*orderNo\.value = ''[\s\S]*errorText\.value = '缺少有效订单号，请从订单列表进入发货'[\s\S]*return[\s\S]*\}[\s\S]*orderNo\.value = routeOrderNo/s,
      'order ship route orderNo must be decoded with diagnostics and fail closed before submit'
    )
    requirePattern(
      file,
      content,
      /async function submitShip\(\)(?:: Promise<void>)?\s*\{[\s\S]*const safeOrderNo = orderNo\.value[\s\S]*const safeCompany = company\.value\.trim\(\)[\s\S]*const response = await shipOrder\(safeOrderNo[\s\S]*assertBackendShipResponse\(response, safeOrderNo\)[\s\S]*uni\.showModal\(modalOptions\)[\s\S]*console\.warn\('order ship submit failed'/s,
      'order ship submit must use validated orderNo, trimmed user input, validate backend response, and log failures'
    )
    requirePattern(
      file,
      content,
      /function redirectAfterShip\(orderNoSnapshot: string, showLogistics: boolean\): void\s*\{[\s\S]*const route = \{[\s\S]*console\.warn\('order ship redirect failed'[\s\S]*uni\.redirectTo\(route\)/s,
      'order ship redirect must handle navigation failures after successful backend update'
    )
  }
  requireIncludedMarkers(file, content, requiredOrderIdGuardMarkers[file] || [], 'missing positive backend order number guard marker')
}

if (failed) {
  process.exit(1)
}

console.log('order sensitive action copy check passed')
