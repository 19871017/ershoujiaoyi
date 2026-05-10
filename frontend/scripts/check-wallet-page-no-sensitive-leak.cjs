const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const walletFile = 'src/pages/wallet/index.vue'
const accountFile = 'src/pages/wallet/accounts/index.vue'
const walletSource = fs.readFileSync(path.join(root, walletFile), 'utf8')
const accountSource = fs.readFileSync(path.join(root, accountFile), 'utf8')
const failures = []

const walletForbiddenMarkers = [
  "const withdrawForm = reactive({ amount: '20.00'",
  "const rechargeForm = reactive({ amount: '10.00'",
  "accountNo: ''",
  "v-model.trim=\"withdrawForm.accountNo\"",
  'placeholder="收款账号 / 支付宝 / 银行卡"',
  'accountNo: withdrawForm.accountNo',
  '请填写收款人和收款账号'
]

for (const marker of walletForbiddenMarkers) {
  if (walletSource.includes(marker)) failures.push(`${walletFile}: forbidden raw payout-account collection marker found: ${marker}`)
}

const walletRequiredMarkers = [
  "withdrawForm = reactive({ amount: ''",
  'maskedAccountNo',
  'wallet/accounts/index',
  '提现账户绑定接口尚未接入，当前未提交提现审核',
  '提现页不采集完整收款账号',
  '收款账号明文只在账户管理页提交瞬间处理'
]

const walletNewForbiddenMarkers = [
  'accountNo: maskedAccountNo.value',
  'createWithdrawal({ amount, paymentMethod: withdrawForm.paymentMethod',
  '提现已提交审核：'
]

for (const marker of walletNewForbiddenMarkers) {
  if (walletSource.includes(marker)) failures.push(`${walletFile}: forbidden withdrawal submission without backend-owned payout-account reference: ${marker}`)
}

const walletNewRequiredMarkers = [
  '提现账户绑定接口尚未接入，当前未提交提现审核',
  '提现账户接口未接入，未执行资金冻结',
  'return'
]

for (const marker of walletNewRequiredMarkers) {
  if (!walletSource.includes(marker)) failures.push(`${walletFile}: missing withdrawal fail-closed marker: ${marker}`)
}

for (const marker of walletRequiredMarkers) {
  if (!walletSource.includes(marker)) failures.push(`${walletFile}: missing fail-closed masked payout-account marker: ${marker}`)
}

const accountForbiddenMarkers = [
  'v-model.trim="form.no"',
  "no: ''",
  '账号/银行卡号，提交后页面仅显示脱敏尾号',
  '收款账号明文只在提交瞬间发往后端',
  'maskAccountNo(form.no)',
  'form.no)'
]

for (const marker of accountForbiddenMarkers) {
  if (accountSource.includes(marker)) failures.push(`${accountFile}: forbidden raw payout-account collection/preview marker found: ${marker}`)
}

const accountRequiredMarkers = [
  '提现账户后端绑定尚未接入，当前页面不采集完整账号',
  '未保存账号，也未生成可用于提现的账户引用',
  'openBackendBindingUnavailable',
  '后端提现账户绑定接口尚未接入，未提交完整账号',
  '不在前端生成脱敏账户引用'
]

for (const marker of accountRequiredMarkers) {
  if (!accountSource.includes(marker)) failures.push(`${accountFile}: missing fail-closed payout-account page marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('wallet pages avoid raw payout-account collection and fail closed without backend-owned payout account state')
