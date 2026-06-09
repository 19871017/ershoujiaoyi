const fs = require('fs')
const path = require('path')

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

function expectSource(label, source, pattern, message) {
  const matched = pattern instanceof RegExp ? pattern.test(source) : source.includes(pattern)
  if (!matched) failures.push(`${label}: ${message}`)
}

function rejectSource(label, source, pattern, message) {
  const matched = pattern instanceof RegExp ? pattern.test(source) : source.includes(pattern)
  if (matched) failures.push(`${label}: ${message}`)
}

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
  /export function hasApprovedVideoIdentity\(value: UserProfileResponse\): boolean\s*\{[\s\S]*value\.videoVerified === true[\s\S]*value\.videoIdentityStatus === 'APPROVED'[\s\S]*!!validatedVideoIdentityUrl\(value\.videoIdentityUrl\)/,
  'identity approved helper must require videoVerified, APPROVED status, and canonical VIDEO_IDENTITY URL'
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
