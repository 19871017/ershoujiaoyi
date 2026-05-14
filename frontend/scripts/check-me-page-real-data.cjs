const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/me/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  '信用分 98',
  '已实名 · 视频待认证 · 成交 36 单',
  '平台担保卖家',
  "const quickStats = [",
  "const orderStatus = [",
  '¥8,620.50',
  '冻结 ¥126.00 · 可提现 ¥3,280.00',
  "{ icon: '💳', label: '待付款', count: 1 }",
  "{ icon: '📦', label: '待发货', count: 2 }",
  "{ icon: '🧾', label: '待收货', count: 1 }"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static account/trust marker found: ${marker}`)
}

const requiredMarkers = [
  'getMyProfile',
  'getWalletBalance',
  '身份状态以平台审核为准',
  '钱包余额以服务端为准',
  '资料暂时不可用，请稍后刷新',
  '余额暂时不可用，请稍后重试'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived/fail-closed marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('me page uses backend-derived profile/wallet data and avoids static trust markers')
