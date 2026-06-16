const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const failures = []

function readJson(file) {
  return JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'))
}

function pngSize(file) {
  const buffer = fs.readFileSync(path.join(root, file))
  if (buffer.toString('ascii', 1, 4) !== 'PNG') {
    throw new Error(`${file} is not a PNG`)
  }
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20)
  }
}

function requirePng(file, size) {
  if (!fs.existsSync(path.join(root, file))) {
    failures.push(`${file}: missing app logo asset`)
    return
  }
  try {
    const dimensions = pngSize(file)
    if (dimensions.width !== size || dimensions.height !== size) {
      failures.push(`${file}: expected ${size}x${size}, got ${dimensions.width}x${dimensions.height}`)
    }
  } catch (error) {
    failures.push(`${file}: ${error instanceof Error ? error.message : String(error)}`)
  }
}

const manifests = ['manifest.json', 'src/manifest.json']
const indexHtml = fs.readFileSync(path.join(root, 'index.html'), 'utf8')
const webManifest = readJson('static/site.webmanifest')

if (!indexHtml.includes('<link rel="apple-touch-icon" href="/static/app-icons/icon-180.png" />')) {
  failures.push('index.html: missing Apple touch icon link for mobile home-screen installs')
}
if (!indexHtml.includes('<link rel="manifest" href="/static/site.webmanifest" />')) {
  failures.push('index.html: missing web app manifest link')
}
if (!indexHtml.includes('<meta name="theme-color" content="#fff7ed" />')) {
  failures.push('index.html: missing warm brand theme color')
}
if (webManifest.name !== '小原圈' || webManifest.short_name !== '小原圈') {
  failures.push('static/site.webmanifest: app name must stay 小原圈')
}
if (webManifest.start_url !== '/#/' || webManifest.display !== 'standalone') {
  failures.push('static/site.webmanifest: start_url/display must support H5 app-style launch')
}
const manifestIcons = Array.isArray(webManifest.icons) ? webManifest.icons : []
for (const icon of ['/static/app-icons/icon-192.png', '/static/app-icons/icon-512.png']) {
  if (!manifestIcons.some((item) => item?.src === icon)) failures.push(`static/site.webmanifest: missing icon ${icon}`)
}

for (const file of manifests) {
  const manifest = readJson(file)
  if (manifest.name !== '小原圈') failures.push(`${file}: app name must stay 小原圈`)
  if (manifest.id !== 'com.tiklxd09.xiaoyuanquan') failures.push(`${file}: missing stable app id/package placeholder`)
  if (manifest.plus?.distribute?.android?.packagename !== 'com.tiklxd09.xiaoyuanquan') failures.push(`${file}: missing Android package name`)
  if (manifest.plus?.distribute?.ios?.bundleIdentifier !== 'com.tiklxd09.xiaoyuanquan') failures.push(`${file}: missing iOS bundle identifier`)
  const androidPermissions = manifest.plus?.distribute?.android?.permissions || []
  for (const permission of ['android.permission.INTERNET', 'android.permission.CAMERA', 'android.permission.RECORD_AUDIO']) {
    if (!androidPermissions.some((item) => String(item).includes(permission))) failures.push(`${file}: missing Android permission ${permission}`)
  }
  const iosPrivacy = manifest.plus?.distribute?.ios?.privacyDescription || {}
  for (const key of ['NSCameraUsageDescription', 'NSMicrophoneUsageDescription', 'NSPhotoLibraryUsageDescription']) {
    if (!iosPrivacy[key]) failures.push(`${file}: missing iOS privacy description ${key}`)
  }
  if (manifest.h5?.favicon !== 'static/brand/xiaoyuanquan-logo-mark.png') failures.push(`${file}: H5 favicon must use the app logo mark`)
}

for (const file of [
  'src/assets/brand/xiaoyuanquan-logo-mark.png',
  'public/assets/brand/xiaoyuanquan-logo-mark.png',
  'src/static/brand/xiaoyuanquan-logo-mark.png',
  'static/brand/xiaoyuanquan-logo-mark.png'
]) {
  requirePng(file, 512)
}

for (const size of [1024, 512, 192, 180, 167, 152, 144, 120, 96, 87, 80, 76, 72, 58, 48, 40, 29]) {
  requirePng(`src/static/app-icons/icon-${size}.png`, size)
  requirePng(`static/app-icons/icon-${size}.png`, size)
  requirePng(`public/assets/app-icons/icon-${size}.png`, size)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('app brand assets are configured')
