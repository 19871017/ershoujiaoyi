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
  'getPayoutAccount',
  'payoutAccountId',
  '仅使用后端返回的提现账户引用提交审核'
]

const walletNewForbiddenMarkers = [
  'accountNo: maskedAccountNo.value',
  'createWithdrawal({ amount, paymentMethod: withdrawForm.paymentMethod',
  'withdrawal.value?.accountNo'
]

const walletApiSource = fs.readFileSync(path.join(root, 'src/api/modules/wallet.ts'), 'utf8')
const withdrawalResponseBlock = walletApiSource.match(/export interface WithdrawalResponse \{[\s\S]*?\n\}/)?.[0] || ''
if (withdrawalResponseBlock.includes('accountNo: string')) {
  failures.push('src/api/modules/wallet.ts: WithdrawalResponse must expose maskedAccountNo, not raw accountNo')
}
if (!withdrawalResponseBlock.includes('maskedAccountNo: string')) {
  failures.push('src/api/modules/wallet.ts: WithdrawalResponse missing maskedAccountNo')
}

const optionalSources = [
  [walletFile, walletSource]
]
const legacyAdminWithdrawFile = 'src/pages/admin/withdraw/detail/index.vue'
const legacyAdminWithdrawPath = path.join(root, legacyAdminWithdrawFile)
if (fs.existsSync(legacyAdminWithdrawPath)) {
  optionalSources.push([legacyAdminWithdrawFile, fs.readFileSync(legacyAdminWithdrawPath, 'utf8')])
}

for (const marker of walletNewForbiddenMarkers) {
  for (const [sourceFile, source] of optionalSources) {
    if (source.includes(marker)) failures.push(`${sourceFile}: forbidden withdrawal submission or raw withdrawal-account response marker: ${marker}`)
  }
}

const walletNewRequiredMarkers = [
  '请先在账户管理页完成后端提现账户绑定；未执行资金冻结',
  'createWithdrawal({ amount, payoutAccountId: activePayoutAccount.value.payoutAccountId',
  '提现提交失败：未执行本地资金状态变更'
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
  'maskAccountNo(form.no)',
  'form.no)'
]

for (const marker of accountForbiddenMarkers) {
  if (accountSource.includes(marker)) failures.push(`${accountFile}: forbidden raw payout-account collection/preview marker found: ${marker}`)
}

const accountRequiredMarkers = [
  'bindPayoutAccount',
  'getPayoutAccount',
  'form.accountNo = \'\'',
  '不能提交脱敏账号',
  '提现账户绑定失败：未保存本地账号'
]

for (const marker of accountRequiredMarkers) {
  if (!accountSource.includes(marker)) failures.push(`${accountFile}: missing fail-closed payout-account page marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('wallet pages avoid raw payout-account collection and fail closed without backend-owned payout account state')
