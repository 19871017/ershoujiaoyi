const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const reportFile = 'src/pages/report/submit/index.vue'
const productDetailFile = 'src/pages/product/detail/index.vue'
const reportContent = fs.readFileSync(path.join(root, reportFile), 'utf8')
const productDetailContent = fs.readFileSync(path.join(root, productDetailFile), 'utf8')
const failures = []

if (!reportContent.includes('function isValidReportTargetId')) {
  failures.push('report submit page must validate route targetId with a positive backend-id guard before submission')
}

if (/targetId\.value\s*===\s*['"]UNKNOWN['"]/.test(reportContent) || /targetId\.value\s*===\s*['"]preview['"]/.test(reportContent)) {
  failures.push('report target guard must not rely on narrow UNKNOWN/preview equality checks')
}

if (!reportContent.includes('/^[1-9]\\d{0,18}$/') || !reportContent.includes('GOODS: /^(GOODS|PRODUCT)-') || !reportContent.includes('ORDER: /^ORDER-')) {
  failures.push('report target guard must allow only positive numeric IDs or canonical typed backend IDs')
}

if (/\|\|\s*\/\^\(GOODS\|ORDER\|CHAT\|USER\|REPORT\)-/.test(reportContent)) {
  failures.push('report target guard must not accept every prefixed ID regardless of target type')
}

for (const forbidden of ['PREVIEW-', 'preview-', 'UNKNOWN', 'SAMPLE-', 'DEMO-']) {
  const displayOnly = `invalid route ids such as ${forbidden}`
  if (reportContent.includes(forbidden) && !reportContent.includes(displayOnly)) {
    failures.push(`report submit page must not accept or special-case invalid route id marker ${forbidden}`)
  }
}

if (!/if \(!isValidReportTargetId\(targetId\.value\)\)/.test(reportContent)) {
  failures.push('submit() must fail closed unless targetId passes isValidReportTargetId(targetId.value)')
}

if (!/submitReport\(\{[\s\S]*targetId:\s*targetId\.value/.test(reportContent)) {
  failures.push('report submission must still send only the validated backend targetId from page state')
}

if (/平台担保/.test(reportContent)) {
  failures.push('report submit page must not show static platform escrow/trust copy unless derived from backend order/payment state')
}

if (!productDetailContent.includes('function isValidProductReportTargetId')) {
  failures.push('product detail report entry must validate productId with a positive backend-id guard before navigation')
}

if (!/if \(!isValidProductReportTargetId\(reportTargetId\)\)/.test(productDetailContent)) {
  failures.push('product detail reportProduct() must fail closed unless reportTargetId passes isValidProductReportTargetId')
}

if (!/targetId=\$\{encodeURIComponent\(String\(reportTargetId\)\)\}/.test(productDetailContent)) {
  failures.push('product detail report navigation must use the validated reportTargetId, not raw route/detail fallback ids')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('report submit target guard check passed')
