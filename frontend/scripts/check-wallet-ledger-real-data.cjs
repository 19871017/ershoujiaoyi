const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const listFile = 'src/pages/wallet/ledger/index.vue'
const detailFile = 'src/pages/wallet/ledger/detail/index.vue'
const listSource = fs.readFileSync(path.join(root, listFile), 'utf8')
const detailSource = fs.readFileSync(path.join(root, detailFile), 'utf8')
const failures = []

const forbiddenMarkers = [
  'const ledgers = [',
  "LED-001",
  "LED-002",
  "LED-003",
  "LED-004",
  "amount: '137.00'",
  "amount: '18.60'",
  "amount: '88.00'",
  "amount: '66.00'",
  "time: '2026-05-07",
  "time: '2026-05-06"
]

for (const marker of forbiddenMarkers) {
  if (listSource.includes(marker)) failures.push(`${listFile}: forbidden static wallet ledger marker found: ${marker}`)
  if (detailSource.includes(marker)) failures.push(`${detailFile}: forbidden static wallet ledger marker found: ${marker}`)
}

const listRequiredMarkers = [
  'getWalletLedger',
  'WalletLedgerItemResponse',
  '流水加载失败',
  '暂无钱包流水',
  'ledgerList.value = []',
  'businessLabel',
  'formatDateTime'
]

for (const marker of listRequiredMarkers) {
  if (!listSource.includes(marker)) failures.push(`${listFile}: missing backend-derived ledger marker: ${marker}`)
}

const detailRequiredMarkers = [
  'getWalletLedgerDetail',
  'WalletLedgerItemResponse',
  'ledgerNo 缺失，未查询账本详情',
  '账本详情加载失败，未展示本地账本样例',
  'loading',
  'loadMessage',
  'Object.assign(detail, mapLedgerDetail(response))'
]

for (const marker of detailRequiredMarkers) {
  if (!detailSource.includes(marker)) failures.push(`${detailFile}: missing backend-derived ledger detail marker: ${marker}`)
}

const detailForbiddenMarkers = [
  "ledgerNo: '待查询流水'",
  "amount: '0.00'",
  "status: '待后端确认'",
  "请以后端账本详情和对账结果为准",
  "根据入口参数展示账本摘要",
  "if (amount) detail.amount = amount",
  "if (status) detail.status = decodeURIComponent(status)",
  "if (direction) detail.direction = direction === 'CREDIT'"
]

for (const marker of detailForbiddenMarkers) {
  if (detailSource.includes(marker)) failures.push(`${detailFile}: query-derived or placeholder ledger detail marker found: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('wallet ledger list/detail pages use backend data and no static/query-derived ledger samples')
