const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/after-sales/detail/index.vue',
  'src/pages/after-sales/apply/index.vue',
  'src/pages/upload/evidence/index.vue',
  'src/pages/user/identity/index.vue',
  'src/pages/product/publish/index.vue',
  'src/pages/community/compose/index.vue'
]

const supportFilesByFile = {
  'src/pages/user/identity/index.vue': [
    'src/pages/user/identity/identity-helpers.ts'
  ],
  'src/pages/product/publish/index.vue': [
    'src/pages/product/publish/publish-integrity.ts'
  ],
  'src/pages/community/compose/index.vue': [
    'src/pages/community/compose/compose-helpers.ts'
  ]
}

function readSource(file) {
  const supportContent = (supportFilesByFile[file] || [])
    .map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8'))
    .join('\n')
  return [supportContent, fs.readFileSync(path.join(root, file), 'utf8')].filter(Boolean).join('\n')
}

const contents = Object.fromEntries(files.map((file) => [file, readSource(file)]))
const backendRoot = path.resolve(root, '..', 'backend')
const backendUserService = fs.readFileSync(path.join(backendRoot, 'src/main/java/com/secondhand/platform/modules/user/application/UserApplicationService.java'), 'utf8')
const backendUserProfileResponse = fs.readFileSync(path.join(backendRoot, 'src/main/java/com/secondhand/platform/modules/user/UserProfileResponse.java'), 'utf8')
const backendUserServiceTest = fs.readFileSync(path.join(backendRoot, 'src/test/java/com/secondhand/platform/modules/user/application/UserApplicationServiceTest.java'), 'utf8')
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
if (!afterSalesApply.includes('售后票据已上传')) {
  failures.push('after-sales apply picker should say only that media files were uploaded before after-sales submission')
}
if (!afterSalesApply.includes('请至少上传一张售后票据')) {
  failures.push('after-sales apply validation should ask for upload tickets before final after-sales submission')
}

const uploadEvidence = contents['src/pages/upload/evidence/index.vue']
for (const canonical of ['AFTER_SALES_EVIDENCE', 'REPORT_EVIDENCE', 'VIDEO_IDENTITY', 'PRODUCT_IMAGE', 'COMMUNITY_IMAGE', 'CHAT_IMAGE']) {
  if (!uploadEvidence.includes(canonical)) {
    failures.push(`upload evidence page must understand canonical media scene ${canonical}`)
  }
}
if (!uploadEvidence.includes("{label:'社区',value:'COMMUNITY_IMAGE' as const}")) {
  failures.push('upload evidence scene picker must expose COMMUNITY_IMAGE so community image tickets can be generated without legacy aliases')
}
if (!uploadEvidence.includes("{label:'实名视频',value:'VIDEO_IDENTITY' as const}")) {
  failures.push('upload evidence scene picker must expose VIDEO_IDENTITY explicitly')
}
if (!uploadEvidence.includes('function chooseVideoIdentity()')) {
  failures.push('upload evidence page must use uni.chooseVideo for VIDEO_IDENTITY instead of routing video verification through chooseImage')
}
if (!uploadEvidence.includes("createMediaUploadTicket({ scene: 'VIDEO_IDENTITY'")) {
  failures.push('upload evidence page must create VIDEO_IDENTITY tickets with the canonical scene')
}
if (!uploadEvidence.includes('视频文件已上传 1 条') || !uploadEvidence.includes('视频上传票据创建失败')) {
  failures.push('upload evidence VIDEO_IDENTITY flow should upload the selected file and keep ticket creation failure copy explicit')
}
for (const prefix of ['/uploads/evidence/after-sales/', '/uploads/report-evidence/', '/uploads/video-identity/', '/uploads/product-image/', '/uploads/community-image/', '/uploads/chat-image/']) {
  if (!uploadEvidence.includes(prefix)) failures.push(`upload evidence page must validate uploaded storage URLs against backend prefix ${prefix}`)
}
for (const marker of ['const sceneSnapshot = scene.value', 'const sceneSnapshot: Scene = \'VIDEO_IDENTITY\'', 'validatedStorageUrl(sceneSnapshot, uploaded.storageUrl)', "throw new Error('未选择到有效媒体图片，请重新选择')", 'if (scene.value !== sceneSnapshot)', '文件上传中，请稍后切换场景', 'upload evidence invalid storageUrl', 'upload evidence result modal failed', '媒体文件已上传，但结果弹窗无法显示，请稍后查看页面记录', "console.warn('upload evidence invalid route scene'", "console.warn('upload evidence invalid route orderNo'", "console.warn('upload evidence initialize failed'", "console.warn('upload evidence image upload failed'", "console.warn('upload evidence video upload failed'", "storageUrl.startsWith('blob:')", "storageUrl.startsWith('data:')", "lower.includes('%2e')", "lower.includes('%2f')", "lower.includes('%5c')", "storageUrl.includes('\\\\')", "storageUrl.includes('..')", "storageUrl.includes('//')", "relativePath.split('/').some", 'const fatalRouteError=ref(false)', 'function isPickerCancel(error: unknown): boolean', 'function hasInvalidTempMediaPath(path: string): boolean', 'function hasInvalidStorageUrl(sceneValue: Scene, storageUrl: unknown): boolean']) {
  if (!uploadEvidence.includes(marker)) failures.push(`upload evidence page must fail closed on upload route, scene drift, storage URL, or empty picker result: ${marker}`)
}
if (!/function decodeRouteValue\(fieldName: string, value: string\): \{ ok: boolean; value: string \}[\s\S]*console\.warn\('upload evidence route decode failed'[\s\S]*return \{ ok: false, value: '' \}/.test(uploadEvidence) || !uploadEvidence.includes("errorText.value = '上传入口参数无效，请返回上一页重新进入'")) {
  failures.push('upload evidence route params must decode fail-closed with diagnostics')
}
if (!/function readQuery\(\): void\s*\{[\s\S]*const rawScene[\s\S]*const rawOrderNo[\s\S]*if \(!sceneParam\.ok \|\| !orderParam\.ok\)\s*\{[\s\S]*fatalRouteError\.value = true[\s\S]*errorText\.value = '上传入口参数无效，请返回上一页重新进入'[\s\S]*if \(sceneParam\.value && !routeScene\)[\s\S]*fatalRouteError\.value = true[\s\S]*errorText\.value = '上传场景无效，请返回上一页重新进入'[\s\S]*if \(orderParam\.value && !isValidBackendOrderNo\(orderParam\.value\)\)[\s\S]*fatalRouteError\.value = true[\s\S]*errorText\.value = '订单号格式无效，请从售后详情重新进入补充票据'[\s\S]*orderNo\.value = isValidBackendOrderNo\(orderParam\.value\) \? orderParam\.value : ''[\s\S]*refreshSceneError\(\)/s.test(uploadEvidence)) {
  failures.push('upload evidence readQuery must validate route scene/orderNo fail-closed before upload actions')
}
if (!/function refreshSceneError\(\): void\s*\{[\s\S]*if \(fatalRouteError\.value\) return[\s\S]*function selectScene\(value: Scene\): void\s*\{[\s\S]*if \(fatalRouteError\.value\) return uni\.showToast/s.test(uploadEvidence)) {
  failures.push('upload evidence fatal route errors must not be cleared by scene switching')
}
if (!/function chooseImage\(\): void\s*\{[\s\S]*uploading\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage[\s\S]*catch \(error\)\s*\{[\s\S]*uploading\.value = false[\s\S]*console\.warn\('upload evidence choose image failed'/s.test(uploadEvidence)) {
  failures.push('upload evidence image chooser must clear busy state and log synchronous chooser failures')
}
if (!/function chooseVideoIdentity\(\): void\s*\{[\s\S]*uploading\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseVideo[\s\S]*catch \(error\)\s*\{[\s\S]*uploading\.value = false[\s\S]*console\.warn\('upload evidence choose video failed'/s.test(uploadEvidence)) {
  failures.push('upload evidence video chooser must clear busy state and log synchronous chooser failures')
}
if (!/function submit\(\): void\s*\{[\s\S]*if \(saving\.value\) return[\s\S]*images\.value\.some\(url => hasInvalidStorageUrl\(scene\.value, url\)\)[\s\S]*fail:\(error: unknown\)=>\{[\s\S]*媒体文件已上传，但结果弹窗无法显示，请稍后查看页面记录[\s\S]*try\s*\{\s*uni\.showModal\(modalOptions\)[\s\S]*catch \(error\)\s*\{[\s\S]*saving\.value = false[\s\S]*console\.warn\('upload evidence result modal failed'[\s\S]*媒体文件已上传，但结果弹窗无法显示，请稍后查看页面记录/s.test(uploadEvidence)) {
  failures.push('upload evidence submit must validate final storage URLs and handle async/synchronous result modal failures with uploaded-media fallback copy')
}
if (!/function initializeUploadEvidence\(\): void\s*\{[\s\S]*try\s*\{[\s\S]*readQuery\(\)[\s\S]*catch \(error\)\s*\{[\s\S]*fatalRouteError\.value = true[\s\S]*console\.warn\('upload evidence initialize failed'[\s\S]*errorText\.value = '上传入口参数无效，请返回上一页重新进入'/s.test(uploadEvidence)) {
  failures.push('upload evidence initialize catch must set fatalRouteError before showing route error')
}

if (uploadEvidence.includes("type Scene='AFTER_SALES'|'REPORT'|'IDENTITY'|'PRODUCT'|'CHAT'")) {
  failures.push('upload evidence scene type must not be alias-only')
}

if (uploadEvidence.includes("uni.showToast({title:`已保存 ${images.value.length} 张凭证`,icon:'none'})")) {
  failures.push('upload evidence picker must not claim evidence is saved after ticket creation only')
}

if (!uploadEvidence.includes('媒体文件已上传')) {
  failures.push('upload evidence picker should say only that media files were uploaded before business submission')
}

if (uploadEvidence.includes("uni.showModal({title:'凭证已保存'")) {
  failures.push('upload evidence submit must not claim evidence is saved without a business API submission')
}

for (const forbiddenSubmitCopy of ['保存凭证', '保存中...', '安全凭证', '上传凭证', '凭证图片无效', '请先选择凭证图片', '凭证需先完成平台上传票据校验', '凭证上传票据创建失败', '平台上传票据', '保存前会先向服务端申请上传票据']) {
  if (uploadEvidence.includes(forbiddenSubmitCopy)) {
    failures.push(`upload evidence standalone page must not label ticket/local validation as a saved/uploaded evidence action or platform-accepted evidence: ${forbiddenSubmitCopy}`)
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
if (!uploadEvidence.includes('媒体文件已完成平台上传')) {
  failures.push('upload evidence standalone page should describe platform upload completion without trust/business guarantees')
}

if (!uploadEvidence.includes('上传媒体文件')) {
  failures.push('upload evidence standalone page should consistently describe media upload, not saved credentials/evidence')
}

if (!uploadEvidence.includes('校验上传结果')) {
  failures.push('upload evidence action button should describe upload-result validation, not saving evidence')
}

if (!uploadEvidence.includes('媒体文件已上传')) {
  failures.push('upload evidence submit should describe uploaded media completion, not business success')
}

const publicMediaPages = [
  { file: 'src/pages/product/publish/index.vue', scene: 'PRODUCT_IMAGE', prefix: '/uploads/product-image/' },
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
  if (!page.includes('已上传')) {
    failures.push(`${file} picker should say only that media files were uploaded before business submission`)
  }
  if (!page.includes('上传失败') || !page.includes('上传票据校验')) {
    failures.push(`${file} validation/failure copy should consistently require uploaded ticket URLs before business submission`)
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
for (const marker of ['const videoIdentityStoragePrefix = \'/uploads/video-identity/\'', 'const profileReady = ref(false)', 'const profileUnavailable = ref(false)', 'function hasApprovedVideoIdentity(value: UserProfileResponse): boolean', 'function clearVideoTrustState(): void', 'function assertBackendProfile(value: unknown): asserts value is UserProfileResponse', 'function hasInvalidTempVideoPath(path: string): boolean', 'function validatedVideoIdentityUrl(storageUrl: unknown): string', 'validatedVideoIdentityUrl(uploaded.storageUrl)', 'const safeVideoUrl = validatedVideoIdentityUrl(videoUrl.value)', 'assertBackendProfile(backendProfile)', 'hasApprovedVideoIdentity(profile)', '视频上传中，请稍后提交', 'identity video invalid storageUrl', 'identity video upload failed', 'identity video picker failed', 'identity video picker returned invalid temp path', 'identity profile refresh failed; cleared video trust state', 'identity real-name unavailable modal failed', 'identity real-name draft modal failed', 'identity real-name input invalid', 'identity video submit ignored because submission is already in progress', 'const refreshed = await loadProfile()', 'identity notification navigation failed', 'identity video submit failed', 'identity video submit success modal failed', '认证提交结果暂时无法校验', "lower.startsWith('blob:')", "lower.startsWith('data:')", "storageUrl.startsWith('local://')", "storageUrl.startsWith('blob:')", "storageUrl.startsWith('data:')", "lower.includes('placeholder')", "lower.includes('%2e')", "lower.includes('%2f')", "lower.includes('%5c')", "storageUrl.includes('\\\\')", "relativePath.split('/').some"] ) {
  if (!identityPage.includes(marker)) failures.push(`identity video flow must validate VIDEO_IDENTITY storage URL before state/submission and surface async failures: ${marker}`)
}
if (/v-model(?:\.trim)?=/.test(identityPage)) {
  failures.push('identity page forms must use explicit :value + @input bindings instead of v-model drift-prone bindings')
}
if (!/function inputValue\(field: RealNameFieldKey, event: unknown\): string \| undefined[\s\S]*\| null \| undefined\)\?\.detail\?\.value[\s\S]*console\.warn\('identity real-name input invalid'[\s\S]*function updateRealNameField\(field: RealNameFieldKey, event: unknown\): void[\s\S]*if \(value === undefined\) return[\s\S]*form\[field\] = value/s.test(identityPage)) {
  failures.push('identity real-name inputs must not silently clear fields or throw on malformed input events')
}
if (!/function assertBackendProfile\(value: unknown\): asserts value is UserProfileResponse[\s\S]*const requiresVideoUrl = backendProfile\.videoVerified === true \|\| backendProfile\.videoIdentityStatus === 'PENDING'[\s\S]*if \(requiresVideoUrl && !backendProfile\.videoIdentityUrl\) throw new Error\('identity backend video status missing videoIdentityUrl'\)[\s\S]*if \(backendProfile\.videoIdentityUrl\) validatedVideoIdentityUrl\(backendProfile\.videoIdentityUrl\)/s.test(identityPage)) {
  failures.push('identity backend profile validation must reject pending/verified video states without a canonical videoIdentityUrl')
}
if (identityPage.includes("backendProfile.videoIdentityStatus === 'APPROVED' || backendProfile.videoIdentityStatus === 'PENDING'")) {
  failures.push('identity own-profile validation must not require hidden non-seller APPROVED video URLs when videoVerified is false')
}
if (!/function hasInvalidTempVideoPath\(path: string\): boolean[\s\S]*lower\.startsWith\('local:\/\/'\)[\s\S]*lower\.startsWith\('blob:'\)[\s\S]*lower\.startsWith\('data:'\)[\s\S]*lower\.includes\('%2e'\)[\s\S]*lower\.includes\('%2f'\)[\s\S]*lower\.includes\('%5c'\)[\s\S]*path\.includes\('\\\\'\)[\s\S]*path\.includes\('\.\.'\)[\s\S]*path\.includes\('\/\/'\)/s.test(identityPage)) {
  failures.push('identity video temp path must reject local/blob/data/placeholder/traversal values before VIDEO_IDENTITY ticket upload')
}
if (!/async function loadProfile\(\): Promise<boolean>\s*\{[\s\S]*const backendProfile = await getMyProfile\(\)[\s\S]*assertBackendProfile\(backendProfile\)[\s\S]*Object\.assign\(profile, backendProfile\)[\s\S]*profileReady\.value = true[\s\S]*catch \(error\)\s*\{[\s\S]*clearVideoTrustState\(\)[\s\S]*profileUnavailable\.value = true[\s\S]*console\.warn\('identity profile refresh failed; cleared video trust state'/s.test(identityPage)) {
  failures.push('identity profile refresh must validate backend audit state and clear stale trust state on failure')
}
if (!/function chooseVideo\(\): void\s*\{[\s\S]*uploadingVideo\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseVideo[\s\S]*catch \(error\)\s*\{[\s\S]*uploadingVideo\.value = false[\s\S]*console\.warn\('identity video picker failed'/s.test(identityPage)) {
  failures.push('identity video picker must clear busy state and log synchronous chooser failures')
}
if (!/function navigateToNotificationAfterVideoSubmit\(\): void\s*\{[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('identity notification navigation failed'/s.test(identityPage)) {
  failures.push('identity video submit notification navigation must handle async and synchronous failures')
}
if (!/async function submitVideo\(\): Promise<void>\s*\{[\s\S]*if \(!profileReady\.value \|\| profileUnavailable\.value\)[\s\S]*if \(hasApprovedVideoIdentity\(profile\)\)[\s\S]*const safeVideoUrl = validatedVideoIdentityUrl\(videoUrl\.value\)[\s\S]*submitVideoIdentity\(\{ videoUrl: safeVideoUrl, description:[\s\S]*const refreshed = await loadProfile\(\)[\s\S]*if \(!refreshed\) return uni\.showToast[\s\S]*try\s*\{\s*uni\.showModal\(modalOptions\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('identity video submit success modal failed'[\s\S]*console\.warn\('identity video submit failed'/s.test(identityPage)) {
  failures.push('identity video submit must require available backend state, validate storage URL, refresh backend state, and stop normal success flow when refresh fails')
}
if (!/function submit\(\): void\s*\{[\s\S]*const modalOptions[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('identity real-name draft modal failed'[\s\S]*try\s*\{\s*uni\.showModal\(modalOptions\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('identity real-name draft modal failed'/s.test(identityPage)) {
  failures.push('identity real-name draft validation modal must handle async and synchronous failures')
}
const forbiddenLocalIdentityTrustPatterns = [
  { label: 'local pending status assignment', pattern: /profile\.videoIdentityStatus\s*=\s*['"]PENDING['"]/ },
  { label: 'local approved status assignment', pattern: /profile\.videoIdentityStatus\s*=\s*['"]APPROVED['"]/ },
  { label: 'local videoVerified true assignment', pattern: /profile\.videoVerified\s*=\s*true/ },
  { label: 'local Object.assign pending or approved status', pattern: /Object\.assign\(profile,\s*\{[^}]*videoIdentityStatus:\s*['"](?:PENDING|APPROVED)['"]/s },
  { label: 'local Object.assign videoVerified true', pattern: /Object\.assign\(profile,\s*\{[^}]*videoVerified:\s*true/s }
]
for (const { label, pattern } of forbiddenLocalIdentityTrustPatterns) {
  if (pattern.test(identityPage)) failures.push(`identity video submit must not locally claim video trust state: ${label}`)
}
if (!/function clearVideoTrustState\(\): void\s*\{[\s\S]*profile\.videoIdentityStatus = 'UNVERIFIED'[\s\S]*profile\.videoVerified = false[\s\S]*videoUrl\.value = ''[\s\S]*\}/s.test(identityPage)) {
  failures.push('identity profile refresh failures must clear stale video trust state fail-closed')
}
if (!identityPage.includes('await loadProfile()')) {
  failures.push('identity video submit should refresh server-derived profile state after backend accepts the audit submission')
}
if (!/try\s*\{\s*const safeVideoUrl = validatedVideoIdentityUrl\(videoUrl\.value\)[\s\S]*submitVideoIdentity/.test(identityPage)) {
  failures.push('identity video submit should catch final storage URL validation failures before calling the audit API')
}
if (!identityPage.includes('submitVideoIdentity({ videoUrl: safeVideoUrl, description:')) {
  failures.push('identity video submit should send only the validated server-issued videoUrl plus description, not client identity/trust fields')
}
if (/submitVideoIdentity\(\{[^}]*\b(userId|senderId|buyerId|sellerId|admin|role|identityStatus|videoIdentityStatus|videoVerified)\b/s.test(identityPage)) {
  failures.push('identity video submit must not include client-supplied identity/trust fields in the audit request body')
}

if (!backendUserService.includes("currentUserProfile(Long userId) {\n        return loadProfile(userId, null, true);")) {
  failures.push('backend current user profile must expose pending VIDEO_IDENTITY URL for self refresh validation')
}
if (!/public UserProfileResponse publicProfile\(Long userId\)[\s\S]*return loadProfile\(userId, null\);[\s\S]*public UserProfileResponse publicProfile\(Long userId, Long viewerId\)[\s\S]*return loadProfile\(userId, viewerId\);/.test(backendUserService)) {
  failures.push('backend public profile must not expose pending VIDEO_IDENTITY URLs')
}
if (!backendUserService.includes("CASE WHEN video_identity.reason LIKE '/uploads/video-identity/%' THEN video_identity.reason ELSE NULL END AS video_identity_url")) {
  failures.push('backend profile video identity URL must require canonical /uploads/video-identity/ media prefix')
}
for (const marker of ['private boolean isCanonicalVideoIdentityUrl(String url)', 'url.startsWith(VIDEO_IDENTITY_STORAGE_PREFIX)', 'lower.contains("placeholder")', 'lower.contains("%2e")', 'lower.contains("%2f")', 'lower.contains("%5c")', 'url.contains("\\\\")', 'url.contains("..")', 'url.contains("//")', 'segment.isBlank()']) {
  if (!backendUserService.includes(marker)) failures.push(`backend profile video identity URL must reject malformed canonical-prefix video URLs before exposure: ${marker}`)
}
if (!/boolean sellerApprovedVideo = approvedVideo && VIDEO_IDENTITY_PUBLIC_ROLES\.contains\(mainRole\.toUpperCase\(Locale\.ROOT\)\) && storedVideoIdentityUrl != null/.test(backendUserService)) {
  failures.push('backend public seller video trust must require canonical videoIdentityUrl before exposing verified video state')
}
if (!/boolean pendingOwnVideo = exposePendingVideoIdentityUrl && "PENDING"\.equals\(videoStatus\) && storedVideoIdentityUrl != null/.test(backendUserService)) {
  failures.push('backend self pending video URL exposure must require canonical videoIdentityUrl')
}
if (!/String responseVideoStatus = sellerApprovedVideo \|\| pendingOwnVideo \? videoStatus : "UNVERIFIED"/.test(backendUserService)) {
  failures.push('backend public profile must hide pending/untrusted video audit status as UNVERIFIED')
}
if (!/List<UserRankingResponse> rows = jdbcTemplate\.query[\s\S]*VIDEO_IDENTITY_PUBLIC_ROLES\.contains\(mainRole\.toUpperCase\(Locale\.ROOT\)\)[\s\S]*isCanonicalVideoIdentityUrl\(rawVideoIdentityUrl\)[\s\S]*String responseVideoStatus = approvedVideo \? "APPROVED" : "UNVERIFIED"[\s\S]*new UserRankingResponse/s.test(backendUserService)) {
  failures.push('backend public ranking must hide pending/untrusted video audit status and require canonical approved video URL')
}
if (!backendUserProfileResponse.includes('this.videoIdentityUrl = (videoVerified || "PENDING".equals(this.videoIdentityStatus)) ? videoIdentityUrl : null;')) {
  failures.push('backend profile response must allow self PENDING video URL while hiding unverified non-pending video URLs')
}
for (const marker of ['pendingSelf.getVideoIdentityUrl()', 'profileShouldHideNonCanonicalVideoIdentityAuditUrls', 'profileShouldHideMalformedCanonicalVideoIdentityAuditUrls', '/uploads/community-image/not-video.jpg', '/uploads/product-image/not-video.jpg', '/uploads/video-identity/../community-image/leak.mp4', '/uploads/video-identity/%2e%2e/secret.mp4', '/uploads/video-identity/placeholder.mp4', 'AUDIT-VIDEO-RANKING-APPROVED', 'assertEquals("UNVERIFIED", pendingRow.getVideoIdentityStatus())']) {
  if (!backendUserServiceTest.includes(marker)) failures.push(`backend user profile tests must guard identity video URL contract: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('upload evidence canonical-scene check passed')
