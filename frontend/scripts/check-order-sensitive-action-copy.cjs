const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/order/detail/index.vue',
  'src/pages/order/list/index.vue',
  'src/pages/order/confirm/index.vue'
]

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
  '平台会把担保资金结算给卖家'
]

const requiredOrderDetailNeutralMarkers = [
  '订单、支付、售后和聊天记录以服务端状态为准',
  '确认收货将调用后端接口完成状态变更'
]

let failed = false
for (const file of files) {
  const absolute = path.join(root, file)
  const content = fs.readFileSync(absolute, 'utf8')
  for (const copy of forbiddenSuccessCopies) {
    if (content.includes(copy)) {
      console.error(`${file}: forbidden fake-success copy found: ${copy}`)
      failed = true
    }
  }
  if (file === 'src/pages/order/confirm/index.vue') {
    for (const copy of forbiddenStaticTrustCopies) {
      if (content.includes(copy)) {
        console.error(`${file}: forbidden pre-order static escrow/trust copy found: ${copy}`)
        failed = true
      }
    }
    for (const marker of ['平台订单创建后再进入支付确认', '支付、售后和聊天记录以服务端订单状态为准']) {
      if (!content.includes(marker)) {
        console.error(`${file}: missing neutral pre-order trust marker: ${marker}`)
        failed = true
      }
    }
  }
  if (file === 'src/pages/order/detail/index.vue') {
    for (const copy of forbiddenOrderDetailTrustCopies) {
      if (content.includes(copy)) {
        console.error(`${file}: forbidden order-detail static escrow/trust copy found: ${copy}`)
        failed = true
      }
    }
    for (const marker of requiredOrderDetailNeutralMarkers) {
      if (!content.includes(marker)) {
        console.error(`${file}: missing neutral order-detail trust marker: ${marker}`)
        failed = true
      }
    }
  }
}

if (failed) {
  process.exit(1)
}

console.log('order sensitive action copy check passed')
