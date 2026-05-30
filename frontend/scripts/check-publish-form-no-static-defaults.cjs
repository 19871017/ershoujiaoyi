const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const publishPath = path.join(root, 'src/pages/product/publish/index.vue')
const supportFiles = [
  'src/pages/product/publish/publish-integrity.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(publishPath, 'utf8')
].join('\n')
const formBlock = source.match(/const form = reactive\(\{[\s\S]*?\n\}\)/)?.[0] || ''

const failures = []

function assertNo(pattern, message, haystack = source) {
  if (pattern.test(haystack)) failures.push(message)
}

if (!formBlock) failures.push('publish page form state was not found')
assertNo(/\btitle:\s*['"][^'"]+['"]/, 'publish form must not prefill a product title; user-created listings must start empty', formBlock)
assertNo(/\bdescription:\s*['"][^'"]+['"]/, 'publish form must not prefill a product description/demo copy', formBlock)
assertNo(/\bprice:\s*['"][^'"]+['"]/, 'publish form must not prefill a product price', formBlock)
assertNo(/\blocation:\s*['"][^'"]+['"]/, 'publish form must not prefill a static location/trade signal', formBlock)
assertNo(/tradeRule:\s*['"]平台担保['"]/, 'publish form must not default to platform escrow/trust wording before backend-derived order/payment state', formBlock)
assertNo(/const\s+tradeOptions\s*=\s*\[[^\]]*(平台担保|同城约看)[^\]]*\]/, 'publish trade options must not present static escrow/location trust signals as seller-selectable product facts')
assertNo(/placeholder=["'][^"']*(同城可约|平台担保)[^"']*["']/, 'publish placeholders must not suggest static location/escrow trust signals')
assertNo(/奶油白法式连衣裙|小原圈断舍离|只穿过一次/, 'publish page contains demo listing copy that could be submitted as real inventory')

if (!/imageUrls:\s*\[\]\s+as\s+string\[\]/.test(source)) {
  failures.push('publish imageUrls should start as an empty array and only contain storage URLs from PRODUCT_IMAGE tickets')
}

if (!/createProduct\(/.test(source) || !/createMediaUploadTicket\(/.test(source) || !/uploadMediaTicketFile\(/.test(source)) {
  failures.push('publish page must continue to use real product API and completed media upload flow')
}

for (const marker of [
  "const productImageStoragePrefix = '/uploads/product-image/'",
  'function assertBackendProfile(value: unknown): asserts value is UserProfileResponse',
  "profile.videoVerified === true && profile.videoIdentityStatus === 'APPROVED'",
  'function hasInvalidTempImagePath(path: string): boolean',
  'function hasInvalidProductImageUrl(url: unknown): boolean',
  'function validatedProductImageUrl(storageUrl: unknown): string',
  "console.warn('product publish invalid product image url'",
  "console.warn('product publish image upload failed'",
  "console.warn('product publish image picker failed'",
  "console.warn('product publish profile load failed'",
  "console.warn('product publish profile refresh failed'",
  "console.warn('product publish submit failed'",
  "console.warn('product publish success modal failed'",
  "console.warn('product publish notification navigation failed'",
  "console.warn('product publish identity navigation failed'",
  '图片上传中，请稍后提交',
  '商品资料已提交后端审核，审核状态以服务端记录和通知为准。'
]) {
  if (!source.includes(marker)) failures.push(`publish page missing fail-closed marker: ${marker}`)
}

for (const marker of ["typeof url !== 'string'", "url.startsWith('local://')", "url.startsWith('blob:')", "url.startsWith('data:')", "lower.includes('%2e')", "lower.includes('%2f')", "lower.includes('%5c')", "url.includes('\\\\')", "url.includes('..')", "url.includes('//')", "relativePath.split('/').some"]) {
  if (!source.includes(marker)) failures.push(`publish page must reject unsafe PRODUCT_IMAGE URL marker: ${marker}`)
}

if (/v-model(?:\.trim)?=/.test(source)) {
  failures.push('publish form must use explicit :value + @input bindings instead of v-model drift-prone bindings')
}

if (!/function choosePhotos\(\)[\s\S]*uploadingPhotos\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage[\s\S]*fail\(error: unknown\)[\s\S]*uploadingPhotos\.value = false[\s\S]*catch \(error\)[\s\S]*console\.warn\('product publish image picker failed'/s.test(source)) {
  failures.push('publish image picker must clear busy state and log async/synchronous chooser failures')
}

if (!/function submitProduct\(\)[\s\S]*if \(submitting\.value\) return[\s\S]*if \(uploadingPhotos\.value\)[\s\S]*submitting\.value = true[\s\S]*const profile = await getMyProfile\(\)[\s\S]*assertBackendProfile\(profile\)[\s\S]*publishReady\.value = resolvePublishPermission\(profile\)[\s\S]*if \(!publishReady\.value\)[\s\S]*submitting\.value = false[\s\S]*const safeImageUrls = form\.imageUrls\.map\(validatedProductImageUrl\)[\s\S]*imageUrls: safeImageUrls[\s\S]*try\s*\{\s*uni\.showModal\(modalOptions\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product publish success modal failed'/s.test(source)) {
  failures.push('publish submit must block duplicate submits, refresh backend seller permission, wait for image uploads, revalidate PRODUCT_IMAGE URLs, and handle modal failures')
}

if (failures.length) {
  console.error('check-publish-form-no-static-defaults failed:')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log('check-publish-form-no-static-defaults passed')
