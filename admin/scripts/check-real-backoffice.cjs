const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const requiredFiles = [
  'src/layouts/AdminLayout.vue',
  'src/pages/audit/index.vue',
  'src/pages/audit/detail.vue',
  'src/pages/finance/withdrawals/index.vue',
  'src/pages/orders/index.vue',
  'src/pages/audit-logs/index.vue',
  'src/pages/system/location/index.vue',
  'src/api/modules/admin.ts'
]
const forbidden = [
  { file: 'src/api/http.ts', pattern: /Promise\.resolve\(\{\}\s+as\s+T\)/, message: 'http request must not be empty mock resolver' },
  { file: 'src/api/http.ts', pattern: /X-Admin-Mode|X-Dev-Mode/, message: 'admin HTTP client must not send legacy dev-mode authorization headers' },
  { file: 'src/store/modules/auth.ts', pattern: /X-Admin-Mode|X-Dev-Mode|devAdminEnabled/, message: 'admin auth must not build or accept legacy dev-mode authorization/session flags' },
  { file: 'src/store/modules/auth.ts', pattern: /setToken\(\s*\w+\s*:\s*string\)\s*\{\s*this\.token\s*=|setToken\(\s*\w+\s*:\s*string\)\s*\{\s*persistState/s, message: 'admin auth must not allow local pseudo token writes; login must be backend session derived' },
  { file: 'src/pages/login/index.vue', pattern: /登录页占位|TODO/i, message: 'login page must not be placeholder' },
  { file: 'src/pages/dashboard/index.vue', pattern: /仪表盘占位|TODO|placeholder/i, message: 'dashboard must not be placeholder or derive fake local metrics' },
  { file: 'src/router/index.ts', pattern: /system\/empty/, message: 'router must not route core admin flow to empty placeholder' },
  { file: 'src/pages/finance/withdrawals/index.vue', pattern: /\baccountNo\b|完整账号|伪成功|假成功(?!。)/, message: 'withdrawal page must only use masked account display and explicit failure states' },
  { file: 'src/pages/orders/index.vue', pattern: /预览订单|本地订单样例|假成功(?!。)|伪成功|改价成功|发货成功/, message: 'order page must use backend order detail and avoid local success states' },
  { file: 'src/pages/users/index.vue', pattern: /完整手机号|本地用户样例|假成功|伪成功|密码|令牌/, message: 'user page must only show masked backend data and no local samples' },
  { file: 'src/pages/audit-logs/index.vue', pattern: /accessKey|完整账号|本地样例操作|假成功|伪成功/, message: 'audit logs page must use backend logs and avoid sensitive data/local samples' },
  { file: 'src/pages/system/location/index.vue', pattern: /假成功|伪成功|baiduAk.*\}\}/, message: 'location config page must avoid fake success and secret echo' }
]
const requiredPatterns = [
  { file: 'src/store/modules/auth.ts', pattern: /sessionAllowsPermission/, message: 'admin auth must keep explicit permission/RBAC helper' },
  { file: 'src/store/modules/auth.ts', pattern: /permissions:/, message: 'admin session must persist explicit permissions' },
  { file: 'src/store/modules/auth.ts', pattern: /permission: 'user:read'/, message: 'user routes must require module-specific user:read permission' },
  { file: 'src/store/modules/auth.ts', pattern: /permission: 'order:read'/, message: 'order routes must require module-specific order:read permission' },
  { file: 'src/store/modules/auth.ts', pattern: /permission: 'after-sales:read'/, message: 'after-sales routes must require module-specific after-sales:read permission' },
  { file: 'src/store/modules/auth.ts', pattern: /if \(!session\) return \{ token: '', username: '', session: null \}/, message: 'expired or invalid persisted admin sessions must clear stale local token and username' },
  { file: 'src/layouts/AdminLayout.vue', pattern: /permission: 'user:read'/, message: 'user menu must require user:read rather than broad audit permission' },
  { file: 'src/layouts/AdminLayout.vue', pattern: /ADMIN_DASHBOARD_ACTIONS/, message: 'layout menu must reuse module-specific dashboard action permissions' },
  { file: 'src/pages/dashboard/index.vue', pattern: /dashboardActionsForSession/, message: 'dashboard quick actions must be filtered by explicit module permissions' },
  { file: 'src/pages/audit/index.vue', pattern: /reviewingAuditNo/, message: 'audit workbench must lock duplicate backend review submissions' },
  { file: 'src/pages/audit/index.vue', pattern: /canReviewAuditRecord/, message: 'audit workbench must gate withdrawal audits through finance:review-aware helper' },
  { file: 'src/pages/audit/index.vue', pattern: /finance:review/, message: 'withdrawal audit rows must require explicit finance:review permission' },
  { file: 'src/pages/audit/index.vue', pattern: /approveAdminProduct/, message: 'product audit records must call the backend product approval endpoint, not only generic audit status' },
  { file: 'src/pages/finance/withdrawals/index.vue', pattern: /getAdminWithdrawalList/, message: 'withdrawal page must load backend admin withdrawal list' },
  { file: 'src/pages/finance/withdrawals/index.vue', pattern: /reviewAdminWithdrawal/, message: 'withdrawal page must submit reviews through backend audit API helper' },
  { file: 'src/pages/finance/withdrawals/index.vue', pattern: /canReviewFinance/, message: 'withdrawal review actions must require explicit finance:review permission' },
  { file: 'src/pages/finance/withdrawals/index.vue', pattern: /finance:review/, message: 'withdrawal page must fail closed for read-only finance sessions' },
  { file: 'src/pages/after-sales/index.vue', pattern: /getAdminAfterSalesList/, message: 'after-sales page must load backend admin after-sales list' },
  { file: 'src/pages/after-sales/index.vue', pattern: /reviewAdminAfterSales/, message: 'after-sales page must submit reviews through backend after-sales review API helper' },
  { file: 'src/pages/after-sales/index.vue', pattern: /canReviewAfterSales/, message: 'after-sales review actions must require explicit after-sales:review permission' },
  { file: 'src/pages/after-sales/index.vue', pattern: /afterSalesReviewing/, message: 'after-sales page must lock duplicate backend review submissions' },
  { file: 'src/pages/orders/index.vue', pattern: /getAdminOrderDetail/, message: 'order page must load backend admin order detail' },
  { file: 'src/pages/orders/index.vue', pattern: /getAdminOrderList/, message: 'order page must load backend admin order list' },
  { file: 'src/pages/orders/index.vue', pattern: /listLoading/, message: 'order page must expose backend list loading/fail-closed state' },
  { file: 'src/pages/orders/index.vue', pattern: /onMounted\(\(\) => \{[\s\S]*loadList\(\)/, message: 'order page must auto-load backend order list on entry for testable backoffice readiness' },
  { file: 'src/pages/users/index.vue', pattern: /getAdminUserDetail/, message: 'user page must load backend admin user detail' },
  { file: 'src/pages/users/index.vue', pattern: /searchAdminUsers/, message: 'user page must search backend admin users without local sample lists' },
  { file: 'src/pages/audit-logs/index.vue', pattern: /getAdminAuditLogs/, message: 'audit log page must load backend admin audit logs' },
  { file: 'src/store/modules/auth.ts', pattern: /path: '\/audit-logs'[\s\S]*permission: 'audit:log'/, message: 'dashboard quick actions must expose audit logs only to audit:log sessions' },
  { file: 'src/store/modules/auth.ts', pattern: /path: '\/system\/location'[\s\S]*permission: 'system:config'/, message: 'dashboard quick actions must expose location config only to system:config sessions' }
]

const failures = []
for (const relative of requiredFiles) {
  if (!fs.existsSync(path.join(root, relative))) failures.push(`missing ${relative}`)
}
for (const rule of forbidden) {
  const full = path.join(root, rule.file)
  if (!fs.existsSync(full)) {
    failures.push(`missing ${rule.file}`)
    continue
  }
  const text = fs.readFileSync(full, 'utf8')
  if (rule.pattern.test(text)) failures.push(`${rule.file}: ${rule.message}`)
}
for (const rule of requiredPatterns) {
  const full = path.join(root, rule.file)
  if (!fs.existsSync(full)) {
    failures.push(`missing ${rule.file}`)
    continue
  }
  const text = fs.readFileSync(full, 'utf8')
  if (!rule.pattern.test(text)) failures.push(`${rule.file}: ${rule.message}`)
}

if (failures.length) {
  console.error('[admin-real-backoffice] failed')
  for (const item of failures) console.error(`- ${item}`)
  process.exit(1)
}
console.log('[admin-real-backoffice] ok')
