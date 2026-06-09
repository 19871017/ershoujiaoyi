const fs = require('fs')
const path = require('path')
const vm = require('vm')
const ts = require('typescript')

const root = path.resolve(__dirname, '..')
const files = {
  publicProfile: 'src/pages/user/public-profile/index.vue',
  publicProfileIntegrity: 'src/pages/user/public-profile/profile-integrity.ts',
  productDetail: 'src/pages/product/detail/index.vue',
  productDetailIntegrity: 'src/pages/product/detail/product-detail-integrity.ts',
  identity: 'src/pages/user/identity/index.vue',
  identityHelpers: 'src/pages/user/identity/identity-helpers.ts',
  http: 'src/api/http.ts'
}

function read(file) {
  return fs.readFileSync(path.join(root, file), 'utf8')
}

const sources = Object.fromEntries(Object.entries(files).map(([key, file]) => [key, read(file)]))
const failures = []

function loadIdentityHelpersForFixture() {
  const helperFile = path.join(root, files.identityHelpers)
  const helperSource = fs.readFileSync(helperFile, 'utf8')
    .replace(/import\s+type\s+\{[\s\S]*?\}\s+from\s+['"][^'"]+['"]\s*/g, '')
  const compiled = ts.transpileModule(helperSource, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  }).outputText
  const sandbox = {
    exports: {},
    console: { ...console, warn: () => {} },
    require,
    module: { exports: {} }
  }
  sandbox.module.exports = sandbox.exports
  vm.runInNewContext(compiled, sandbox, { filename: helperFile })
  return sandbox.module.exports
}

function expectSource(label, source, pattern, message) {
  const matched = pattern instanceof RegExp ? pattern.test(source) : source.includes(pattern)
  if (!matched) failures.push(`${label}: ${message}`)
}

function rejectSource(label, source, pattern, message) {
  const matched = pattern instanceof RegExp ? pattern.test(source) : source.includes(pattern)
  if (matched) failures.push(`${label}: ${message}`)
}

function expectThrows(label, fn, message) {
  try {
    fn()
    failures.push(`${label}: ${message}`)
  } catch {
    // expected
  }
}

function assertIdentityHelperBehavior() {
  const label = files.identityHelpers
  const helpers = loadIdentityHelpersForFixture()
  const {
    assertBackendProfile,
    guessVideoContentType,
    hasApprovedVideoIdentity,
    hasInvalidTempVideoPath,
    validatedVideoIdentityUrl,
    videoIdentityStoragePrefix
  } = helpers
  if (
    typeof assertBackendProfile !== 'function' ||
    typeof guessVideoContentType !== 'function' ||
    typeof hasApprovedVideoIdentity !== 'function' ||
    typeof hasInvalidTempVideoPath !== 'function' ||
    typeof validatedVideoIdentityUrl !== 'function' ||
    videoIdentityStoragePrefix !== '/uploads/video-identity/'
  ) {
    failures.push(`${label}: missing executable identity video helper exports`)
    return
  }

  const baseProfile = {
    userId: 8187306280,
    nickname: '视频认证测试',
    mainRole: 'SELLER',
    identityStatus: 'UNVERIFIED',
    videoIdentityStatus: 'UNVERIFIED',
    videoVerified: false
  }
  const canonicalUrl = '/uploads/video-identity/8187306280/identity.mp4'
  const approvedProfile = { ...baseProfile, videoIdentityStatus: 'APPROVED', videoVerified: true, videoIdentityUrl: canonicalUrl }
  const pendingProfile = { ...baseProfile, videoIdentityStatus: 'PENDING', videoVerified: false, videoIdentityUrl: canonicalUrl }
  const rejectedProfile = { ...baseProfile, videoIdentityStatus: 'REJECTED', videoVerified: false, videoIdentityUrl: canonicalUrl }

  try {
    assertBackendProfile(approvedProfile)
    assertBackendProfile(pendingProfile)
    assertBackendProfile(rejectedProfile)
    if (hasApprovedVideoIdentity(approvedProfile) !== true) failures.push(`${label}: approved profile should expose approved video identity`)
    if (hasApprovedVideoIdentity(pendingProfile) !== false) failures.push(`${label}: pending profile must not expose approved video identity`)
    if (hasApprovedVideoIdentity(rejectedProfile) !== false) failures.push(`${label}: rejected profile must not expose approved video identity`)
    if (hasApprovedVideoIdentity({ ...approvedProfile, videoVerified: false }) !== false) failures.push(`${label}: APPROVED status without videoVerified must not expose video identity`)
    if (hasApprovedVideoIdentity({ ...approvedProfile, videoIdentityUrl: '/uploads/avatar/8187306280/avatar.png' }) !== false) failures.push(`${label}: non VIDEO_IDENTITY URL must not expose approved video identity`)
    if (validatedVideoIdentityUrl(canonicalUrl) !== canonicalUrl) failures.push(`${label}: canonical VIDEO_IDENTITY URL should be accepted`)
    if (hasInvalidTempVideoPath('blob:https://old.tiklxd09.club/video') !== false) failures.push(`${label}: H5 blob picker path should remain usable before ticket upload`)
    if (hasInvalidTempVideoPath('local://video.mp4') !== true) failures.push(`${label}: local placeholder video path must be rejected`)
    if (guessVideoContentType('blob:https://old.tiklxd09.club/video') !== 'video/mp4') failures.push(`${label}: H5 blob video without MIME should default to MP4`)
  } catch (error) {
    failures.push(`${label}: executable helper fixture failed: ${error instanceof Error ? error.message : String(error)}`)
  }

  expectThrows(label, () => assertBackendProfile({ ...baseProfile, videoIdentityStatus: 'PENDING', videoIdentityUrl: '' }), 'pending profile without video URL must fail closed')
  expectThrows(label, () => assertBackendProfile({ ...baseProfile, videoIdentityStatus: 'PENDING', videoVerified: true, videoIdentityUrl: canonicalUrl }), 'videoVerified true with non-approved status must fail closed')
  expectThrows(label, () => validatedVideoIdentityUrl('blob:https://old.tiklxd09.club/video'), 'blob video URL must not be treated as uploaded VIDEO_IDENTITY media')
  expectThrows(label, () => validatedVideoIdentityUrl('/uploads/video-identity/%2e%2e/evil.mp4'), 'encoded traversal VIDEO_IDENTITY URL must fail closed')
  expectThrows(label, () => guessVideoContentType('identity.webm'), 'WebM video identity upload should remain unsupported')
}

assertIdentityHelperBehavior()

expectSource(
  files.publicProfileIntegrity,
  sources.publicProfileIntegrity,
  /export function hasApprovedPublicSellerVideo\(profile: UserProfileResponse\): boolean\s*\{[\s\S]*profile\.videoVerified === true[\s\S]*profile\.videoIdentityStatus === 'APPROVED'[\s\S]*!!validatedPublicMediaUrl\(profile\.videoIdentityUrl \|\| '', videoIdentityStoragePrefix\)/,
  'approved seller video helper must require videoVerified, APPROVED status, and canonical VIDEO_IDENTITY URL'
)
expectSource(
  files.publicProfile,
  sources.publicProfile,
  /const hasApprovedSellerVideo = computed\(\(\) =>[\s\S]*profileLoaded\.value[\s\S]*isSellerProfile\.value[\s\S]*profile\.videoVerified === true[\s\S]*profile\.videoIdentityStatus === 'APPROVED'[\s\S]*hasApprovedPublicSellerVideo\(profile\)/,
  'public-profile verified card/badge/video gate must require loaded seller profile plus approved helper'
)
expectSource(files.publicProfile, sources.publicProfile, 'v-if="hasIdentityVideo"', 'public-profile trust card/badge/video must render only through hasIdentityVideo')
expectSource(files.publicProfile, sources.publicProfile, ':src="identityVideoUrl"', 'public-profile video src must come from validated identityVideoUrl')
expectSource(
  files.publicProfile,
  sources.publicProfile,
  /const identityVideoUrl = computed\(\(\) => hasApprovedSellerVideo\.value \? resolveBackendMediaUrl\(validatedPublicMediaUrl\(profile\.videoIdentityUrl \|\| '', videoIdentityStoragePrefix\)\) : ''\)/,
  'public-profile must resolve a VIDEO_IDENTITY URL only after the approved gate passes'
)

expectSource(
  files.productDetailIntegrity,
  sources.productDetailIntegrity,
  /export function hasApprovedSellerVideoIdentity\(profile: UserProfileResponse\): boolean\s*\{[\s\S]*profile\.videoVerified === true[\s\S]*profile\.videoIdentityStatus === 'APPROVED'[\s\S]*!!validatedDisplayMediaUrl\(profile\.videoIdentityUrl \|\| '', videoIdentityStoragePrefix, 'seller-video'\)/,
  'product detail approved seller helper must require videoVerified, APPROVED status, and canonical VIDEO_IDENTITY URL'
)
expectSource(
  files.productDetail,
  sources.productDetail,
  /const sellerHasVerifiedVideo = computed\(\(\) =>[\s\S]*sellerProfile\.value\?\.videoVerified === true[\s\S]*sellerProfile\.value\.videoIdentityStatus === 'APPROVED'[\s\S]*validatedDisplayMediaUrl\(sellerProfile\.value\?\.videoIdentityUrl \|\| '', videoIdentityStoragePrefix, 'seller-video'\)[\s\S]*hasApprovedSellerVideoIdentity\(sellerProfile\.value\)/,
  'product detail seller tag gate must require approved helper and validated VIDEO_IDENTITY URL'
)
expectSource(files.productDetail, sources.productDetail, "sellerHasVerifiedVideo.value ? ['视频认证卖家'] : []", 'product detail must not expose video seller tag outside sellerHasVerifiedVideo')

expectSource(
  files.identityHelpers,
  sources.identityHelpers,
  /export function hasApprovedVideoIdentity\(value: UserProfileResponse\): boolean\s*\{[\s\S]*value\.videoVerified !== true \|\| value\.videoIdentityStatus !== 'APPROVED'[\s\S]*return false[\s\S]*try[\s\S]*!!validatedVideoIdentityUrl\(value\.videoIdentityUrl\)[\s\S]*catch[\s\S]*return false/,
  'identity approved helper must require videoVerified, APPROVED status, and fail closed on non-canonical VIDEO_IDENTITY URL'
)
expectSource(
  files.identityHelpers,
  sources.identityHelpers,
  /function validatedVideoIdentityUrl\(storageUrl: unknown\): string[\s\S]*videoIdentityStoragePrefix[\s\S]*storageUrl\.startsWith\('local:\/\/'\)[\s\S]*storageUrl\.startsWith\('blob:'\)[\s\S]*storageUrl\.startsWith\('data:'\)[\s\S]*lower\.includes\('placeholder'\)[\s\S]*relativePath\.split\('\/'\)\.some/,
  'identity VIDEO_IDENTITY URL validator must reject local, blob, data, placeholder, traversal, and empty relative paths'
)
expectSource(
  files.identityHelpers,
  sources.identityHelpers,
  /export function guessVideoContentType\(path: string, fallbackType\?: unknown\): string[\s\S]*const normalized = normalizedVideoContentType\(fallbackType\)[\s\S]*if \(normalized\) return normalized[\s\S]*if \(lower\.startsWith\('blob:'\)\) return 'video\/mp4'[\s\S]*if \(lower\.endsWith\('\.mp4'\)\) return 'video\/mp4'/,
  'identity H5 blob video without MIME must default to MP4 and rely on backend content validation instead of rejecting a valid picker result'
)
expectSource(files.http, sources.http, '当前环境不支持媒体文件上传', 'generic blob upload must not show voice-only unsupported copy during video identity upload')
expectSource(files.http, sources.http, '上传失败，请重新选择媒体文件后再试', 'generic blob upload must not show voice-only retry copy during video identity upload')
rejectSource(files.http, sources.http, '当前环境不支持语音文件上传', 'generic blob upload must not mention voice-only upload support')
rejectSource(files.http, sources.http, '上传失败，请重新录制语音后再试', 'generic blob upload must not mention voice-only retry flow')
expectSource(
  files.identity,
  sources.identity,
  /if \(hasApprovedVideoIdentity\(profile\)\) return '已通过'[\s\S]*switch \(profile\.videoIdentityStatus\)[\s\S]*case 'PENDING':[\s\S]*return '审核中'[\s\S]*case 'REJECTED':[\s\S]*return '已拒绝'/,
  'identity status text must keep pending/rejected separate from approved'
)
expectSource(
  files.identity,
  sources.identity,
  /if \(hasApprovedVideoIdentity\(profile\)\) return 'approved'[\s\S]*switch \(profile\.videoIdentityStatus\)[\s\S]*case 'PENDING':[\s\S]*return 'pending'[\s\S]*case 'REJECTED':[\s\S]*return 'rejected'/,
  'identity status class must keep pending/rejected/default states out of approved styling'
)

for (const { label, source } of [
  { label: files.publicProfile, source: sources.publicProfile },
  { label: files.productDetail, source: sources.productDetail },
  { label: files.identity, source: sources.identity }
]) {
  rejectSource(label, source, /videoIdentityStatus\s*===\s*['"]PENDING['"][\s\S]{0,120}(?:认证卖家|认证视频|VIDEO VERIFIED|approved|已通过)/, 'pending video identity must not display verified seller/video approved UI')
  rejectSource(label, source, /videoIdentityStatus\s*===\s*['"]REJECTED['"][\s\S]{0,120}(?:认证卖家|认证视频|VIDEO VERIFIED|approved|已通过)/, 'rejected video identity must not display verified seller/video approved UI')
  rejectSource(label, source, /videoVerified\s*===\s*true(?![\s\S]{0,180}videoIdentityStatus\s*===\s*['"]APPROVED['"])/, 'videoVerified alone must not be enough to display verified seller/video UI')
  rejectSource(label, source, /videoIdentityStatus\s*===\s*['"]APPROVED['"](?![\s\S]{0,220}(?:videoVerified\s*===\s*true|hasApproved))/, 'APPROVED status alone must not be enough to display verified seller/video UI')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('seller video identity display is gated by APPROVED + videoVerified + canonical VIDEO_IDENTITY URL')
