const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/after-sales/detail/index.vue',
  'src/pages/after-sales/apply/index.vue',
  'src/pages/upload/evidence/index.vue',
  'src/pages/user/identity/index.vue',
  'src/pages/tabbar/publish/index.vue',
  'src/pages/community/compose/index.vue'
]

const contents = Object.fromEntries(files.map((file) => [file, fs.readFileSync(path.join(root, file), 'utf8')]))
const failures = []

const afterSalesDetail = contents['src/pages/after-sales/detail/index.vue']
if (afterSalesDetail.includes('scene=AFTER_SALES&')) {
  failures.push('after-sales detail must link to canonical scene=AFTER_SALES_EVIDENCE, not alias scene=AFTER_SALES')
}

const afterSalesApply = contents['src/pages/after-sales/apply/index.vue']
if (afterSalesApply.includes("uni.showToast({ title: `已保存 ${images.value.length} 张凭证`, icon: 'none' })")) {
  failures.push('after-sales apply picker must not claim evidence is saved after upload ticket creation only')
}
for (const forbiddenAfterSalesCopy of ['上传凭证', '>凭证<', '售后凭证', '凭证图片无效', '请至少上传一张售后凭证', '凭证需先完成平台上传票据校验', '凭证上传票据创建失败']) {
  if (afterSalesApply.includes(forbiddenAfterSalesCopy)) {
    failures.push(`after-sales apply must describe picker records as upload tickets, not accepted evidence/credentials before final business submission: ${forbiddenAfterSalesCopy}`)
  }
}
if (!afterSalesApply.includes('已生成上传票据')) {
  failures.push('after-sales apply picker should say only that upload tickets were generated')
}
if (!afterSalesApply.includes('请至少生成一张售后上传票据')) {
  failures.push('after-sales apply validation should ask for upload tickets before final after-sales submission')
}

const uploadEvidence = contents['src/pages/upload/evidence/index.vue']
for (const canonical of ['AFTER_SALES_EVIDENCE', 'REPORT_EVIDENCE', 'VIDEO_IDENTITY', 'PRODUCT_IMAGE', 'CHAT_IMAGE']) {
  if (!uploadEvidence.includes(canonical)) {
    failures.push(`upload evidence page must understand canonical media scene ${canonical}`)
  }
}

if (uploadEvidence.includes("type Scene='AFTER_SALES'|'REPORT'|'IDENTITY'|'PRODUCT'|'CHAT'")) {
  failures.push('upload evidence scene type must not be alias-only')
}

if (uploadEvidence.includes("uni.showToast({title:`已保存 ${images.value.length} 张凭证`,icon:'none'})")) {
  failures.push('upload evidence picker must not claim evidence is saved after ticket creation only')
}

if (!uploadEvidence.includes('已生成上传票据')) {
  failures.push('upload evidence picker should say only that upload tickets were generated')
}

if (uploadEvidence.includes("uni.showModal({title:'凭证已保存'")) {
  failures.push('upload evidence submit must not claim evidence is saved without a business API submission')
}

for (const forbiddenSubmitCopy of ['保存凭证', '保存中...', '安全凭证', '上传凭证', '保存前会先向服务端申请上传票据']) {
  if (uploadEvidence.includes(forbiddenSubmitCopy)) {
    failures.push(`upload evidence standalone page must not label ticket/local validation as a saved/uploaded evidence action: ${forbiddenSubmitCopy}`)
  }
}

const forbiddenStandaloneTrustCopyPatterns = [
  { label: '平台担保', pattern: /上传票据仅用于平台担保/ },
  { label: '风控', pattern: /上传票据仅用于[^'\n]*风控/ },
  { label: '聊天证据', pattern: /上传票据仅用于[^'\n]*聊天证据/ }
]
for (const { label, pattern } of forbiddenStandaloneTrustCopyPatterns) {
  if (pattern.test(uploadEvidence)) {
    failures.push(`upload evidence standalone page must not assert ${label} trust/business usage before a business API accepts the ticket`)
  }
}
if (!uploadEvidence.includes('上传票据仅完成服务端签发与本地校验')) {
  failures.push('upload evidence standalone page should use neutral ticket/local-validation copy instead of trust/business guarantees')
}

if (!uploadEvidence.includes('上传票据')) {
  failures.push('upload evidence standalone page should consistently call generated records upload tickets, not saved credentials/evidence')
}

if (!uploadEvidence.includes('校验票据')) {
  failures.push('upload evidence action button should describe ticket validation, not saving evidence')
}

if (!uploadEvidence.includes('上传票据已生成')) {
  failures.push('upload evidence submit should describe ticket/local validation completion, not business success')
}

const publicMediaPages = [
  { file: 'src/pages/tabbar/publish/index.vue', scene: 'PRODUCT_IMAGE', prefix: '/uploads/product-image/' },
  { file: 'src/pages/community/compose/index.vue', scene: 'COMMUNITY_IMAGE', prefix: '/uploads/community-image/' }
]
for (const { file, scene, prefix } of publicMediaPages) {
  const page = contents[file]
  if (!page.includes(`scene: '${scene}'`)) {
    failures.push(`${file} must create upload tickets with canonical ${scene} scene`)
  }
  if (!page.includes(`!url.startsWith('${prefix}')`)) {
    failures.push(`${file} must validate storage URLs against expected ${prefix} ticket prefix before business submission`)
  }
  for (const forbiddenCopy of ['已选择 ${', '已选择 ', '上传凭证创建失败', '上传凭证校验']) {
    if (page.includes(forbiddenCopy)) {
      failures.push(`${file} picker copy must describe server-issued upload tickets, not local selection/upload credentials: ${forbiddenCopy}`)
    }
  }
  if (!page.includes('已生成上传票据')) {
    failures.push(`${file} picker should say only that upload tickets were generated before business submission`)
  }
  if (!page.includes('上传票据创建失败') || !page.includes('上传票据校验')) {
    failures.push(`${file} validation/failure copy should consistently use 上传票据 wording`)
  }
}

const identityPage = contents['src/pages/user/identity/index.vue']
if (identityPage.includes("uni.navigateTo({ url: '/pages/upload/evidence/index?scene=IDENTITY' })")) {
  failures.push('identity real-name evidence must not route through legacy scene=IDENTITY; use canonical VIDEO_IDENTITY ticket wording or fail closed until a real-name evidence scene exists')
}
if (identityPage.includes("imageCount.value = Math.max(imageCount.value, 1)")) {
  failures.push('identity real-name picker must not locally increment evidence count before a server-issued upload ticket/storage URL exists')
}
if (identityPage.includes("title: '实名认证已提交'")) {
  failures.push('identity real-name submit must not claim submission without a backend business API')
}
if (!identityPage.includes('实名认证接口尚未接入')) {
  failures.push('identity real-name submit should fail closed with explicit backend-missing copy')
}
if (identityPage.includes("uni.showToast({ title: '已生成上传凭证', icon: 'none' })")) {
  failures.push('identity video picker must say upload ticket, not saved/uploaded credential, because business submission has not happened yet')
}
if (!identityPage.includes('已生成上传票据')) {
  failures.push('identity video picker should say only that a VIDEO_IDENTITY upload ticket was generated')
}
if (identityPage.includes("profile.videoIdentityStatus = 'PENDING'") || identityPage.includes('profile.videoVerified = false')) {
  failures.push('identity video submit must not locally mutate trust-status fields; rehydrate server-derived audit state after successful submission')
}
if (!identityPage.includes('await loadProfile()')) {
  failures.push('identity video submit should refresh server-derived profile state after backend accepts the audit submission')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('upload evidence canonical-scene check passed')
