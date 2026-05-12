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
  'src/pages/operators/index.vue',
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
  { file: 'src/pages/operators/index.vue', pattern: /假成功|伪成功|本地授权样例|root:all|X-Admin-Mode|X-Dev-Mode/, message: 'operator grant page must use backend-only permission endpoints and avoid local/fake authorization' },
  { file: 'src/pages/operators/index.vue', pattern: /<button[^>]*:disabled="[^"]*selectedPermissions\.length === 0/, message: 'operator clear-all save button must not be disabled when no permissions are selected' },
  { file: 'src/pages/system/location/index.vue', pattern: /假成功|伪成功|baiduAk.*\}\}/, message: 'location config page must avoid fake success and secret echo' }
]
const requiredPatterns = [
  { file: 'src/store/modules/auth.ts', pattern: /sessionAllowsPermission/, message: 'admin auth must keep explicit permission/RBAC helper' },
  { file: 'src/store/modules/auth.ts', pattern: /permissions:/, message: 'admin session must persist explicit permissions' },
  { file: 'src/store/modules/auth.ts', pattern: /permission: 'user:read'/, message: 'user routes must require module-specific user:read permission' },
  { file: 'src/store/modules/auth.ts', pattern: /permission: 'order:read'/, message: 'order routes must require module-specific order:read permission' },
  { file: 'src/store/modules/auth.ts', pattern: /permission: 'after-sales:read'/, message: 'after-sales routes must require module-specific after-sales:read permission' },
  { file: 'src/store/modules/auth.ts', pattern: /if \(!session\) \{[\s\S]*sessionStorage\.removeItem\(STORAGE_KEY\)[\s\S]*return \{ token: '', username: '', session: null \}[\s\S]*\}/, message: 'expired or invalid persisted admin sessions must clear stale local token and username' },
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
  { file: 'src/pages/finance/withdrawals/index.vue', pattern: /reviewConfirmText\.value !== expectedReviewConfirmText\.value[\s\S]*return[\s\S]*reviewing\.value = true[\s\S]*reviewAdminWithdrawal/, message: 'withdrawal review must require explicit second confirmation text before approve or reject submit' },
  { file: 'src/pages/after-sales/index.vue', pattern: /getAdminAfterSalesList/, message: 'after-sales page must load backend admin after-sales list' },
  { file: 'src/pages/after-sales/index.vue', pattern: /reviewAdminAfterSales/, message: 'after-sales page must submit reviews through backend after-sales review API helper' },
  { file: 'src/pages/after-sales/index.vue', pattern: /canReviewAfterSales/, message: 'after-sales review actions must require explicit after-sales:review permission' },
  { file: 'src/pages/after-sales/index.vue', pattern: /afterSalesReviewing/, message: 'after-sales page must lock duplicate backend review submissions' },
  { file: 'src/pages/after-sales/index.vue', pattern: /afterSalesConfirmText\.value !== expectedAfterSalesConfirmText\.value[\s\S]*return[\s\S]*afterSalesReviewing\.value = true[\s\S]*reviewAdminAfterSales/, message: 'after-sales review must require explicit second confirmation text before approve or reject submit' },
  { file: 'src/pages/after-sales/index.vue', pattern: /afterSalesConfirmText\.value = ''[\s\S]*reviewRemark\.value = ''[\s\S]*await loadDetail/, message: 'after-sales detail switching must clear stale confirmation and review remark before loading another record' },
  { file: 'src/pages/after-sales/index.vue', pattern: /detail\.value = null[\s\S]*afterSalesConfirmText\.value = ''[\s\S]*reviewRemark\.value = ''[\s\S]*if \(!isValidAdminAfterSalesNo/, message: 'after-sales detail reload must clear stale confirmation and review remark before validation/backend fetch' },
  { file: 'src/pages/after-sales/index.vue', pattern: /reviewAdminAfterSales\(safeNo, action, reviewRemark\.value\)[\s\S]{0,400}reviewRemark\.value = ''[\s\S]{0,120}afterSalesConfirmText\.value = ''[\s\S]{0,120}\} catch/, message: 'after-sales successful review must clear confirmation and review remark inside the backend submit success flow' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<textarea[^>]*v-model\.trim="reviewRemark"[^>]*:disabled="afterSalesReviewing \|\| !canReviewDetail"/, message: 'after-sales review remark input must be disabled while submitting or without review permission/state' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<input[^>]*v-model\.trim="afterSalesConfirmText"[^>]*:disabled="afterSalesReviewing \|\| !canReviewDetail"/, message: 'after-sales review confirmation input must be disabled while submitting or without review permission/state' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<select[^>]*v-model="statusFilter"[^>]*:disabled="loadingList \|\| afterSalesReviewing"/, message: 'after-sales status filter must be disabled while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<button class="primary-btn" :disabled="loadingList \|\| afterSalesReviewing" @click="loadList"/, message: 'after-sales list refresh must be disabled while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<button class="link-btn" :disabled="afterSalesReviewing" @click="selectDetail\(row\.afterSalesNo\)"/, message: 'after-sales row detail switching must be disabled while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<input v-model\.trim="afterSalesNo"[^>]*:disabled="afterSalesReviewing"/, message: 'after-sales manual detail input must be disabled while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /<button class="primary-btn" :disabled="loadingDetail \|\| afterSalesReviewing \|\| !afterSalesNo"/, message: 'after-sales manual detail query must be disabled while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /async function loadList\(\) \{[\s\S]{0,80}if \(afterSalesReviewing\.value\) return/, message: 'after-sales list reload handler must short-circuit while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /async function selectDetail\(no: string\) \{[\s\S]{0,80}if \(afterSalesReviewing\.value\) return/, message: 'after-sales detail switching handler must short-circuit while a backend review is submitting' },
  { file: 'src/pages/after-sales/index.vue', pattern: /async function loadDetail\(\) \{[\s\S]{0,80}if \(afterSalesReviewing\.value\) return/, message: 'after-sales detail reload handler must short-circuit while a backend review is submitting' },
  { file: 'src/pages/orders/index.vue', pattern: /getAdminOrderDetail/, message: 'order page must load backend admin order detail' },
  { file: 'src/pages/orders/index.vue', pattern: /getAdminOrderList/, message: 'order page must load backend admin order list' },
  { file: 'src/pages/orders/index.vue', pattern: /listLoading/, message: 'order page must expose backend list loading/fail-closed state' },
  { file: 'src/pages/orders/index.vue', pattern: /onMounted\(\(\) => \{[\s\S]*loadList\(\)/, message: 'order page must auto-load backend order list on entry for testable backoffice readiness' },
  { file: 'src/pages/users/index.vue', pattern: /getAdminUserDetail/, message: 'user page must load backend admin user detail' },
  { file: 'src/pages/users/index.vue', pattern: /searchAdminUsers/, message: 'user page must search backend admin users without local sample lists' },
  { file: 'src/pages/audit-logs/index.vue', pattern: /getAdminAuditLogs/, message: 'audit log page must load backend admin audit logs' },
  { file: 'src/store/modules/auth.ts', pattern: /path: '\/audit-logs'[\s\S]*permission: 'audit:log'/, message: 'dashboard quick actions must expose audit logs only to audit:log sessions' },
  { file: 'src/store/modules/auth.ts', pattern: /path: '\/operators'[\s\S]*permission: 'operator:grant'/, message: 'dashboard quick actions must expose operator grants only to operator:grant sessions' },
  { file: 'src/router/index.ts', pattern: /path: 'operators'/, message: 'router must expose operator grant route through independent admin only' },
  { file: 'src/store/modules/auth.ts', pattern: /pattern: \/\^\\\/operators/, message: 'operator grant routes must require operator:grant permission' },
  { file: 'src/pages/operators/index.vue', pattern: /getAdminOperatorPermissions/, message: 'operator grant page must load backend operator permissions' },
  { file: 'src/pages/operators/index.vue', pattern: /updateAdminOperatorPermissions/, message: 'operator grant page must save via backend operator permission endpoint' },
  { file: 'src/pages/operators/index.vue', pattern: /isValidAdminUserId/, message: 'operator grant page must validate positive backend user ids before fetch' },
  { file: 'src/pages/operators/index.vue', pattern: /operator:grant/, message: 'operator grant page must state and depend on explicit operator:grant permission' },
  { file: 'src/pages/operators/index.vue', pattern: /selectedPermissions\.length === 0 \? '清空授权' : '保存授权'/, message: 'operator grant page must allow empty selection to clear all assignable permissions' },
  { file: 'src/pages/operators/index.vue', pattern: /selectedPermissions\.value\.length === 0 && clearAllConfirmText\.value !== '清空授权'[\s\S]*return/, message: 'operator grant page must require explicit second confirmation text before audited clear-all submit' },
  { file: 'src/pages/operators/index.vue', pattern: /清空全部可分配权限[\s\S]*空权限数组[\s\S]*审计日志/, message: 'operator grant page must show explicit audited clear-all warning copy' },
  { file: 'src/api/modules/admin.ts', pattern: /isValidAdminOperatorPermission/, message: 'admin API wrapper must validate assignable operator permissions before fetch' },
  { file: 'src/pages/system/location/index.vue', pattern: /getAdminLocationConfig/, message: 'location config page must load backend admin location config' },
  { file: 'src/pages/system/location/index.vue', pattern: /locationConfirmText\.value !== '保存位置配置'[\s\S]*return[\s\S]*saving\.value = true[\s\S]*updateAdminLocationConfig/, message: 'location config save must require explicit second confirmation text before backend system config mutation' }
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
