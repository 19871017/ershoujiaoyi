const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const failures = []

function read(file) {
  return fs.readFileSync(path.join(root, file), 'utf8')
}

const auditDetailFile = 'src/pages/admin/audit/detail/index.vue'
const auditDetail = read(auditDetailFile)

if (!auditDetail.includes('function isValidAuditNo')) {
  failures.push(`${auditDetailFile}: must use a positive audit number allowlist guard named isValidAuditNo`)
}
if (/startsWith\(['"]preview['"]\)/i.test(auditDetail) || /startsWith\(['"]AUDIT-GOODS-['"]\)/.test(auditDetail)) {
  failures.push(`${auditDetailFile}: must not rely on narrow preview/static-prefix blocklists for auditNo validation`)
}
if (!/if \(!isValidAuditNo\(auditNo\.value\)\)/.test(auditDetail)) {
  failures.push(`${auditDetailFile}: loadDetail must fail closed unless auditNo passes isValidAuditNo(auditNo.value)`)
}
if (!/approveAudit\(auditNo\.value,/.test(auditDetail) || !/rejectAudit\(auditNo\.value,/.test(auditDetail)) {
  failures.push(`${auditDetailFile}: review actions must submit the already route/backend-validated auditNo.value, not mutable DTO fields`)
}

const withdrawDetailFile = 'src/pages/admin/withdraw/detail/index.vue'
const withdrawDetail = read(withdrawDetailFile)

if (!withdrawDetail.includes('function isValidWithdrawalNo')) {
  failures.push(`${withdrawDetailFile}: must use a positive withdrawal number allowlist guard named isValidWithdrawalNo`)
}
if (!withdrawDetail.includes('function isValidAuditNo')) {
  failures.push(`${withdrawDetailFile}: must use a positive audit number allowlist guard named isValidAuditNo`)
}
if (/toLowerCase\(\)\.startsWith\(['"]preview['"]\)/.test(withdrawDetail)) {
  failures.push(`${withdrawDetailFile}: must not rely on narrow preview-prefix blocklists for withdrawal/audit validation`)
}
if (!/if \(!isValidWithdrawalNo\(withdrawNo\.value\)\)/.test(withdrawDetail)) {
  failures.push(`${withdrawDetailFile}: loadWithdrawal must fail closed unless withdrawNo passes isValidWithdrawalNo(withdrawNo.value)`)
}
if (!/if \(!isValidAuditNo\(auditNo\.value\)\)/.test(withdrawDetail)) {
  failures.push(`${withdrawDetailFile}: requireAuditNo must fail closed unless auditNo passes isValidAuditNo(auditNo.value)`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('admin sensitive id guard check passed')
