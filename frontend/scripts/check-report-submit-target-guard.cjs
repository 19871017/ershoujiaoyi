const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const reportFile = 'src/pages/report/submit/index.vue'
const productDetailFile = 'src/pages/product/detail/index.vue'
const orderDetailFile = 'src/pages/order/detail/index.vue'
const afterSalesDetailFile = 'src/pages/after-sales/detail/index.vue'
const communityDetailFile = 'src/pages/community/detail/index.vue'
const communityFeedFile = 'src/pages/tabbar/message/index.vue'
const reportContent = fs.readFileSync(path.join(root, reportFile), 'utf8')
const productDetailContent = fs.readFileSync(path.join(root, productDetailFile), 'utf8')
const orderDetailContent = fs.readFileSync(path.join(root, orderDetailFile), 'utf8')
const afterSalesDetailContent = fs.readFileSync(path.join(root, afterSalesDetailFile), 'utf8')
const communityDetailContent = fs.readFileSync(path.join(root, communityDetailFile), 'utf8')
const communityFeedContent = fs.readFileSync(path.join(root, communityFeedFile), 'utf8')
const failures = []

if (!reportContent.includes('function isValidReportTargetId')) {
  failures.push('report submit page must validate route targetId with a positive backend-id guard before submission')
}

for (const marker of [
  'function decodeRouteValue(fieldName: string, value: string): string',
  'function isValidReportTargetType(value: string): value is ReportTargetType',
  "'COMMUNITY_POST'",
  "'COMMUNITY_COMMENT'",
  "COMMUNITY_POST: /^POST-",
  "COMMUNITY_COMMENT: /^CMT-",
  "console.warn('report submit route decode failed'",
  "console.warn('report submit invalid route target'",
  '缺少有效举报对象，请从商品、社区、聊天、订单、售后或用户页面发起举报',
  "const reportEvidenceStoragePrefix = '/uploads/report-evidence/'",
  'function hasInvalidReportEvidenceUrl(url: unknown): boolean',
  'function hasInvalidTempReportEvidencePath(path: string): boolean',
  'function assertReportResponse(response: AuditRecordResponse, expectedTargetType: ReportTargetType, expectedTargetId: string): void',
  'assertReportResponse(response, safeTargetType, safeTargetId)',
  "console.warn('report evidence upload failed'",
  "console.warn('report evidence picker failed'",
  "console.warn('report submit failed'",
  "console.warn('report submit success modal failed'",
  "console.warn('report notification navigation failed'",
  '票据上传中，请稍后提交',
  '举报已提交，但暂时无法打开通知中心',
  '举报已提交，但确认弹窗无法显示，请到通知中心查看处理进度'
]) {
  if (!reportContent.includes(marker)) failures.push(`report submit page missing fail-closed marker: ${marker}`)
}

if (/targetId\.value\s*===\s*['"]UNKNOWN['"]/.test(reportContent) || /targetId\.value\s*===\s*['"]preview['"]/.test(reportContent)) {
  failures.push('report target guard must not rely on narrow UNKNOWN/preview equality checks')
}

if (!reportContent.includes('/^[1-9]\\d{0,18}$/') || !reportContent.includes('GOODS: /^(GOODS|PRODUCT)-') || !reportContent.includes('COMMUNITY_POST: /^POST-') || !reportContent.includes('COMMUNITY_COMMENT: /^CMT-') || !reportContent.includes('ORDER: /^(ORDER-') || !reportContent.includes('OD-[1-9][0-9]{0,9}') || !reportContent.includes('AFTER_SALES: /^AS-')) {
  failures.push('report target guard must allow only positive numeric IDs, canonical typed backend IDs, backend OD order numbers, or AS after-sales numbers')
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

if (!/function readQuery\(\)[\s\S]*decodeRouteValue\('targetType'[\s\S]*decodeRouteValue\('targetId'[\s\S]*!isValidReportTargetType\(routeTargetType\) \|\| !isValidReportTargetId\(routeTargetId, routeTargetType\)[\s\S]*routeError\.value = '缺少有效举报对象，请从商品、社区、聊天、订单、售后或用户页面发起举报'/s.test(reportContent)) {
  failures.push('report route params must decode and validate targetType/targetId fail-closed before submission')
}

if (!/submitReport\(\{[\s\S]*targetId:\s*targetId\.value/.test(reportContent)) {
  failures.push('report submission must still send only the validated backend targetId from page state')
}

for (const forbiddenCopy of [
  '平台担保',
  '平台会结合聊天记录',
  '你提交的凭证处理',
  '添加凭证截图',
  '已签发',
  '签发凭证中',
  '凭证 {{ index + 1 }}',
  '最多 6 张凭证',
  '举报凭证票据签发失败',
  '凭证票据',
  '平台处理私下交易',
  '便于平台审核处理',
  '平台会尽快审核处理',
  '处理结果会在消息中心同步'
]) {
  if (reportContent.includes(forbiddenCopy)) {
    failures.push(`report submit page must not show static trust/business-success evidence copy: ${forbiddenCopy}`)
  }
}

if (!/上传票据['"}]?[\s\S]*\{\{ evidence\.length \}\}\/\{\{ maxEvidenceImages \}\}/.test(reportContent) || !reportContent.includes('举报处理以服务端审核记录为准')) {
  failures.push('report submit page must use neutral upload-ticket and backend-record copy')
}

if (!reportContent.includes('校验票据中') || !reportContent.includes('票据 {{ index + 1 }}')) {
  failures.push('report submit page must consistently call generated media records upload tickets, not accepted evidence credentials')
}

if (!/path\.startsWith\(['"]local:\/\/['"]\)/.test(reportContent) || !/path\.includes\(['"]placeholder['"]\)/.test(reportContent)) {
  failures.push('report evidence picker must reject local placeholder media paths before requesting REPORT_EVIDENCE upload tickets')
}

if (!/evidence\.value\.some\(url =>[\s\S]*!url\.startsWith\(['"]\/uploads\/report-evidence\/['"]\)[\s\S]*hasInvalidReportEvidenceUrl\(url\)/.test(reportContent)) {
  failures.push('report submit must fail closed unless every evidence URL is a server-issued REPORT_EVIDENCE storage URL')
}

if (!/function hasInvalidReportEvidenceUrl\(url: unknown\): boolean[\s\S]*url\.startsWith\(reportEvidenceStoragePrefix\)[\s\S]*url\.startsWith\('blob:'\)[\s\S]*url\.startsWith\('data:'\)[\s\S]*lower\.includes\('%2e'\)[\s\S]*relativePath\.split\('\/'\)\.some/s.test(reportContent)) {
  failures.push('report evidence storage URLs must reject non-canonical REPORT_EVIDENCE paths, blob/data/local, and traversal values')
}

if (!reportContent.includes('举报上传票据需先完成服务端校验')) {
  failures.push('report submit should explain that invalid report evidence URLs were not submitted')
}

if (!/function chooseEvidence\(\)[\s\S]*uploadingEvidence\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage[\s\S]*fail: \(error: unknown\)[\s\S]*uploadingEvidence\.value = false[\s\S]*catch \(error\)[\s\S]*uploadingEvidence\.value = false/s.test(reportContent)) {
  failures.push('report evidence picker must clear busy state and log async/synchronous chooser failures')
}

if (!/async function submit\(\)[\s\S]*const response = await submitReport\(\{[\s\S]*targetType:\s*targetType\.value[\s\S]*targetId:\s*targetId\.value[\s\S]*assertReportResponse\(response, safeTargetType, safeTargetId\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('report submit failed'/s.test(reportContent)) {
  failures.push('report submit must validate backend audit response and log failed submissions')
}

if (!/function showReportSuccessModal\(response: AuditRecordResponse\): void[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('report submit success modal failed'[\s\S]*举报已提交，但确认弹窗无法显示，请到通知中心查看处理进度[\s\S]*const route = \{[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('report notification navigation failed'[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)[\s\S]*举报已提交，但确认弹窗无法显示，请到通知中心查看处理进度/s.test(reportContent)) {
  failures.push('report submit success modal and notification navigation must handle async and synchronous failures')
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

if (!/function reportOrder\(\): void\s*\{[\s\S]*if \(!currentOrder \|\| !isValidBackendOrderNo\(currentOrder\.orderNo\)\)[\s\S]*targetType=ORDER&targetId=\$\{encodeURIComponent\(safeOrderNo\)\}[\s\S]*console\.warn\('order detail report navigation failed'/s.test(orderDetailContent)) {
  failures.push('order detail report entry must use validated OD backend orderNo and handle navigation failures before opening report page')
}

if (!/function reportAfterSales\(\): void\s*\{[\s\S]*if \(!currentDetail \|\| !isValidAfterSalesNo\(currentDetail\.afterSalesNo\)\)[\s\S]*targetType=AFTER_SALES&targetId=\$\{encodeURIComponent\(safeAfterSalesNo\)\}[\s\S]*console\.warn\('after-sales report navigation failed'/s.test(afterSalesDetailContent)) {
  failures.push('after-sales detail report entry must use validated AS backend afterSalesNo and handle navigation failures before opening report page')
}

if (!/function reportPost\(\)[\s\S]*if \(!isValidCommunityPostId\(postId\.value\)\)[\s\S]*targetType=COMMUNITY_POST&targetId=\$\{encodeURIComponent\(postId\.value\)\}/s.test(communityDetailContent)) {
  failures.push('community detail report entry must use validated backend post id and COMMUNITY_POST target type before opening report page')
}

if (!/function reportComment\(item: CommentItem\)[\s\S]*if \(!item \|\| !isValidCommunityCommentNo\(item\.id\)\)[\s\S]*targetType=COMMUNITY_COMMENT&targetId=\$\{encodeURIComponent\(item\.id\)\}/s.test(communityDetailContent)) {
  failures.push('community detail comment report entry must use validated backend commentNo and COMMUNITY_COMMENT target type before opening report page')
}

if (!/function reportFeedPost\(item: CommunityPostResponse\)[\s\S]*if \(!isValidCommunityPostId\(item\.postId\)\)[\s\S]*targetType=COMMUNITY_POST&targetId=\$\{encodeURIComponent\(String\(item\.postId\)\)\}/s.test(communityFeedContent)) {
  failures.push('community feed report entry must use validated backend post id and COMMUNITY_POST target type before opening report page')
}

if (!/function navigateToReport\(url: string\)[\s\S]*navigateToWithFailure\(\{[\s\S]*fail: \(error: unknown\)[\s\S]*console\.warn\('community feed report navigation failed'[\s\S]*catch \(error\)[\s\S]*暂时无法打开举报页，请稍后重试/s.test(communityFeedContent)) {
  failures.push('community feed report navigation must handle async and synchronous navigation failures')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('report submit target guard check passed')
