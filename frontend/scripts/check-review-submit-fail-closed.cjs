const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const reviewPage = 'src/pages/review/submit/index.vue'
const orderApi = 'src/api/modules/order.ts'
const reviewContent = fs.readFileSync(path.join(root, reviewPage), 'utf8')
const orderApiContent = fs.readFileSync(path.join(root, orderApi), 'utf8')

const forbiddenCopies = [
  '评价已填写',
  '后续接入评价接口后再更新信用分和榜单热度',
  "uni.showModal({title:'评价已填写'",
  '评价接口尚未接入',
  '校验评价草稿',
  '本地编辑草稿',
  '不会更新信用分或榜单热度',
  '不会展示为真实交易评价'
]

const requiredPagePatterns = [
  ['submit helper import', /submitOrderReview/],
  ['route orderNo hydration', /onLoad\(\(query\)/],
  ['backend submit call', /await\s+submitOrderReview\(/],
  ['duplicate submit lock', /submitting\.value/],
  ['server success copy', /评价已提交/],
  ['positive orderNo validation', /isValidOrderNo/],
  ['positive reviewNo validation', /isValidReviewNo/],
  ['backend review response assertion', /function\s+assertBackendReviewResponse\(response: OrderReviewResponse, expectedOrderNo: string\): void/],
  ['backend reviewNo assertion', /review submit invalid backend reviewNo/],
  ['backend orderNo assertion', /review submit invalid backend orderNo/],
  ['backend orderNo mismatch assertion', /review submit orderNo mismatch/],
  ['backend reviewer assertion', /review submit invalid reviewerId/],
  ['backend reviewee assertion', /review submit invalid revieweeId/],
  ['backend score assertion', /review submit invalid backend score/],
  ['success modal failure log', /console\.warn\('review submit success modal failed'/],
  ['submit failure log', /console\.warn\('review submit failed'/],
  ['redirect failure log', /console\.warn\('review submit redirect failed'/],
  ['post-review order detail redirect', /\/pages\/order\/detail\/index\?orderNo=\$\{encodeURIComponent\(orderNoSnapshot\)\}/],
  ['post-review completed order list redirect', /\/pages\/order\/list\/index\?role=buyer&status=COMPLETED/]
]

const requiredApiPatterns = [
  ['review request type', /interface\s+SubmitOrderReviewRequest/],
  ['review response type', /interface\s+OrderReviewResponse/],
  ['review submit endpoint', /post<OrderReviewResponse>\(`\/api\/orders\/\$\{encodeURIComponent\(orderNo\)\}\/review`/]
]

let failed = false
for (const copy of forbiddenCopies) {
  if (reviewContent.includes(copy)) {
    console.error(`${reviewPage}: forbidden fail-closed/fake-success review copy remains after real review API wiring: ${copy}`)
    failed = true
  }
}

for (const [label, pattern] of requiredPagePatterns) {
  if (!pattern.test(reviewContent)) {
    console.error(`${reviewPage}: missing real review submit wiring: ${label}`)
    failed = true
  }
}

for (const [label, pattern] of requiredApiPatterns) {
  if (!pattern.test(orderApiContent)) {
    console.error(`${orderApi}: missing order review API contract: ${label}`)
    failed = true
  }
}

if (!/const response = await submitOrderReview\(safeOrderNo,[\s\S]*assertBackendReviewResponse\(response, safeOrderNo\)[\s\S]*showReviewSuccessModal\(response\)/s.test(reviewContent)) {
  console.error(`${reviewPage}: review submit must validate backend response before showing success or navigating`)
  failed = true
}

if (!/function showReviewSuccessModal\(response: OrderReviewResponse\): void[\s\S]*showCancel: true[\s\S]*confirmText: '查看订单'[\s\S]*cancelText: '订单列表'[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('review submit success modal failed'[\s\S]*redirectAfterReview\(safeOrderNo, true\)[\s\S]*success\(modal: \{ confirm\?: boolean \}\)[\s\S]*redirectAfterReview\(safeOrderNo, modal\.confirm === true\)/s.test(reviewContent)) {
  console.error(`${reviewPage}: success modal must offer order detail/list choices and fail closed to order detail`)
  failed = true
}

if (!/function redirectAfterReview\(orderNoSnapshot: string, showDetail: boolean\): void[\s\S]*const target = showDetail \? `\/pages\/order\/detail\/index\?orderNo=\$\{encodeURIComponent\(orderNoSnapshot\)\}` : '\/pages\/order\/list\/index\?role=buyer&status=COMPLETED'[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('review submit redirect failed'[\s\S]*uni\.redirectTo\(route\)/s.test(reviewContent)) {
  console.error(`${reviewPage}: post-review redirect must return to order detail/list with navigation failure handling`)
  failed = true
}

if (failed) process.exit(1)
console.log('review submit real-flow check passed')
