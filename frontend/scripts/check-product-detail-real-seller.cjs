const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/product/detail/index.vue'
const supportFiles = [
  'src/pages/product/detail/product-detail-integrity.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')
const productApiFile = 'src/api/modules/product.ts'
const productApiSource = fs.readFileSync(path.join(root, productApiFile), 'utf8')

const failures = []

const forbiddenMarkers = [
  "const seller = { name: '小原圈主理人'",
  'credit: 98',
  'deals: 36',
  "city: '深圳'",
  "response: '通常10分钟回复'",
  "tags: ['已实名', '平台担保', '同城可约看']",
  '信用 {{ seller.credit }}',
  '成交 {{ seller.deals }} 单',
  'seller.tags'
]

const forbiddenRegexes = [
  {
    pattern: /const\s+sellerTags\s*=\s*computed\(\s*\(\)\s*=>\s*\[[^\]]*(?:平台担保|已实名|视频认证|同城交易)[^\]]*\]\s*\)/,
    message: 'sellerTags must not synthesize fixed trust badges such as 平台担保/已实名/视频认证/同城交易 on product detail; render backend-derived tags or an empty neutral list'
  },
  {
    pattern: /const\s+safeRules\s*=\s*\[[\s\S]*?title:\s*['"]平台担保['"][\s\S]*?\]/,
    message: 'transaction safety copy must not present 平台担保 as a static seller/product trust badge without backend escrow state'
  },
  {
    pattern: /担保下单/,
    message: 'product detail primary order CTA must not statically assert escrow/guarantee state; use neutral order confirmation copy until backend payment/escrow state exists'
  },
  {
    pattern: /已阅读平台担保交易规则/,
    message: 'product detail pre-order checklist must not require acknowledgement of static platform escrow rules before backend order/payment state proves escrow'
  }
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static seller trust marker found: ${marker}`)
}

for (const { pattern, message } of forbiddenRegexes) {
  if (pattern.test(source)) failures.push(`${file}: ${message}`)
}

const requiredMarkers = [
  'sellerName',
  'sellerCity',
  'sellerTrustText',
  'sellerTags',
  'sellerId?: number | null',
  'resolveProductSellerContactTarget(detail.value)',
  '商品卖家信息以服务端返回为准',
  '暂无服务端信用/成交统计',
  '平台交易',
  '订单、支付和售后状态以服务端记录为准',
  '沟通内容以服务端会话记录为准',
  '交付与收货状态以服务端订单记录为准',
  'favoriteProduct(detail.value.productId)',
  'unfavoriteProduct(detail.value.productId)',
  'favoriteLoading',
  '收藏已提交后端',
  '取消收藏已提交后端',
  '收藏接口调用失败，未执行本地收藏变更',
  '商品缺少后端 productId，未执行收藏变更',
  "const productImageStoragePrefix = '/uploads/product-image/'",
  "const communityImageStoragePrefix = '/uploads/community-image/'",
  "const videoIdentityStoragePrefix = '/uploads/video-identity/'",
  'function validatedDisplayMediaUrl(url: unknown, expectedPrefix: string, purpose: string): string',
  "console.warn('product detail rejected media url'",
  "const sellerHasVerifiedVideo = computed(() => sellerProfile.value?.videoVerified === true && sellerProfile.value.videoIdentityStatus === 'APPROVED' && !!validatedDisplayMediaUrl(sellerProfile.value?.videoIdentityUrl || '', videoIdentityStoragePrefix, 'seller-video'))",
  "const sellerAvatarUrl = computed(() => validatedDisplayMediaUrl(sellerProfile.value?.avatarUrl || '', communityImageStoragePrefix, 'seller-avatar'))",
  "const urls = (detail.value?.imageUrls || []).filter((url) => validatedDisplayMediaUrl(url, productImageStoragePrefix, 'product-image'))",
  'function decodeRouteValue(fieldName: string, value: string): string',
  "console.warn('product detail route decode failed'",
  'function isValidBackendProductId(value: unknown): boolean',
  'function assertProductDetail(value: unknown): asserts value is ProductDetailResponse',
  'assertProductDetail(productDetail)',
  "throw new Error('product detail productId mismatch')",
  'function assertSellerProfile(value: unknown, sellerId: number): asserts value is UserProfileResponse',
  'assertSellerProfile(profile, Number(sellerId))',
  "throw new Error('product seller profile userId mismatch')",
  "console.warn('product seller profile integrity failed'",
  "throw error",
  "console.warn('product detail load failed'",
  "console.warn('product order confirmation navigation failed'",
  "console.warn('product seller chat navigation failed'",
  "console.warn('product seller profile navigation failed'",
  "console.warn('product report navigation failed'",
  "console.warn('product favorite mutation failed'"
]

const forbiddenFavoritePatterns = [
  {
    pattern: /function\s+toggleFavorite\s*\(\)\s*\{\s*uni\.showToast\(\{\s*title:\s*['"]收藏接口暂未接通后端，未执行任何收藏变更['"]/,
    message: 'product detail favorite action must call the existing backend favorite/unfavorite API instead of fail-closed stub copy'
  },
  {
    pattern: /favorited\.value\s*=\s*!favorited\.value(?![\s\S]*await\s+(?:favoriteProduct|unfavoriteProduct))/,
    message: 'product detail must not locally toggle favorite state without backend acknowledgement'
  }
]

for (const { pattern, message } of forbiddenFavoritePatterns) {
  if (pattern.test(source)) failures.push(`${file}: ${message}`)
}

for (const marker of requiredMarkers) {
  const checkSource = marker === 'sellerId?: number | null' ? productApiSource : source
  const checkFile = marker === 'sellerId?: number | null' ? productApiFile : file
  if (!checkSource.includes(marker)) failures.push(`${checkFile}: missing backend-derived/neutral seller marker: ${marker}`)
}

for (const marker of ["typeof url !== 'string'", "url.startsWith('local://')", "url.startsWith('blob:')", "url.startsWith('data:')", "lower.includes('%2e')", "lower.includes('%2f')", "lower.includes('%5c')", "url.includes('\\\\')", "url.includes('..')", "url.includes('//')", "relativePath.split('/').some", '!url.startsWith(expectedPrefix)']) {
  if (!source.includes(marker)) failures.push(`${file}: product detail must reject unsafe media URL marker before display: ${marker}`)
}

if (!/function assertProductDetail\(value: unknown\): asserts value is ProductDetailResponse[\s\S]*Number\.isSafeInteger\(product\.productId\)[\s\S]*typeof product\.title !== 'string'[\s\S]*Array\.isArray\(product\.imageUrls\)[\s\S]*product\.visible !== true[\s\S]*product\.status !== 'ACTIVE'[\s\S]*product\.auditState !== 'APPROVED'/s.test(source)) {
  failures.push(`${file}: product detail must validate backend product shape and public visibility before rendering`)
}

if (!/function assertSellerProfile\(value: unknown, sellerId: number\): asserts value is UserProfileResponse[\s\S]*Number\.isSafeInteger\(profile\.userId\)[\s\S]*profile\.userId !== Number\(sellerId\)[\s\S]*typeof profile\.videoVerified !== 'boolean'[\s\S]*profile\.videoVerified === true && profile\.videoIdentityStatus !== 'APPROVED'/s.test(source)) {
  failures.push(`${file}: product detail must validate backend seller profile shape before rendering trust state`)
}

if (!source.includes("const routeProductId = decodeRouteValue('productId'") || !source.includes('productId.value = isValidBackendProductId(routeProductId) ? Number(routeProductId) : 0')) {
  failures.push(`${file}: route productId must be decoded fail-closed and validated before product detail requests`)
}

for (const { label, pattern } of [
  { label: 'order confirmation', pattern: /function createAndPay\(\): Promise<void>[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)[\s\S]*ordering\.value = false[\s\S]*console\.warn\('product order confirmation navigation failed'/ },
  { label: 'seller chat', pattern: /function contactSeller\(\): void[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product seller chat navigation failed'/ },
  { label: 'seller profile', pattern: /function openSellerProfile\(\): void[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product seller profile navigation failed'/ },
  { label: 'report', pattern: /function reportProduct\(\): void[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product report navigation failed'/ }
]) {
  if (!pattern.test(source)) failures.push(`${file}: product detail ${label} navigation must handle async and synchronous failures`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('product detail seller trust data is backend-derived or neutral')
