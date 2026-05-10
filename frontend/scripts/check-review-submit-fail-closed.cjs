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
  ['positive orderNo validation', /isValidOrderNo/]
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

if (failed) process.exit(1)
console.log('review submit real-flow check passed')
