const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const projectRoot = path.resolve(root, '..')
const failures = []

function read(file) {
  return fs.readFileSync(path.join(projectRoot, file), 'utf8')
}

function pngSize(file) {
  const absolute = path.join(root, file)
  if (!fs.existsSync(absolute)) {
    failures.push(`${file}: missing default avatar asset`)
    return null
  }
  const buffer = fs.readFileSync(absolute)
  if (buffer.toString('ascii', 1, 4) !== 'PNG') {
    failures.push(`${file}: default avatar must be a PNG`)
    return null
  }
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

for (const file of [
  'public/assets/profile/default-avatar-goddess.png',
  'public/assets/profile/default-avatar-god.png',
  'dist/build/h5/assets/profile/default-avatar-goddess.png',
  'dist/build/h5/assets/profile/default-avatar-god.png',
  'src/assets/profile/default-avatar-goddess.png',
  'src/assets/profile/default-avatar-god.png'
]) {
  const size = pngSize(file)
  if (size && (size.width !== 512 || size.height !== 512)) {
    failures.push(`${file}: expected 512x512, got ${size.width}x${size.height}`)
  }
}

const defaultAvatar = read('frontend/src/utils/default-avatar.ts')
for (const marker of [
  "/assets/profile/default-avatar-goddess.png",
  "/assets/profile/default-avatar-god.png",
  "export function isDefaultAvatarUrl",
  "export function avatarUrlWithGenderFallback"
]) {
  if (!defaultAvatar.includes(marker)) failures.push(`frontend default avatar helper missing marker: ${marker}`)
}

const backendAuth = read('backend/src/main/java/com/secondhand/platform/modules/auth/application/AuthApplicationService.java')
for (const marker of [
  'avatar_url',
  'defaultAvatarUrl(gender)',
  '/assets/profile/default-avatar-goddess.png',
  '/assets/profile/default-avatar-god.png'
]) {
  if (!backendAuth.includes(marker)) failures.push(`backend registration missing default avatar marker: ${marker}`)
}

const httpClient = read('frontend/src/api/http.ts')
if (!httpClient.includes("if (url.startsWith('/assets/')) return url")) {
  failures.push('frontend http client must keep /assets default avatar URLs same-origin')
}

for (const file of [
  'frontend/src/pages/user/profile/profile-helpers.ts',
  'frontend/src/pages/user/public-profile/profile-integrity.ts',
  'frontend/src/pages/chat/conversation/chat-conversation-helpers.ts',
  'frontend/src/pages/community/detail/community-detail-helpers.ts',
  'frontend/src/pages/tabbar/message/index.vue',
  'frontend/src/pages/chat/session-list/index.vue',
  'frontend/src/pages/product/detail/product-detail-integrity.ts',
  'frontend/src/pages/tabbar/home/home-data.ts'
]) {
  const source = read(file)
  if (!source.includes('isDefaultAvatarUrl')) {
    failures.push(`${file}: must whitelist only the two built-in default avatar asset URLs`)
  }
}

for (const file of [
  'frontend/src/pages/tabbar/me/index.vue',
  'frontend/src/pages/user/profile/index.vue',
  'frontend/src/pages/chat/conversation/index.vue',
  'frontend/src/pages/chat/session-list/index.vue'
]) {
  const source = read(file)
  if (!source.includes('avatarUrlWithGenderFallback')) {
    failures.push(`${file}: must fallback empty avatar by gender`)
  }
}

if (failures.length) {
  console.error('default avatar asset check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('gender default avatar assets and fallbacks are configured')
