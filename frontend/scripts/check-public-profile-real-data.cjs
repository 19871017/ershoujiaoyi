const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/user/public-profile/index.vue'
const supportFiles = [
  'src/pages/user/public-profile/profile-integrity.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')

const failures = []

const forbiddenMarkers = [
  "const userId = ref('101')",
  "{ label:'人气', value:profile.videoVerified ? 1280 : 320 }",
  "{ label:'成交', value:profile.videoVerified ? 36 : 8 }",
  "{ label:'信用', value:profile.videoVerified ? 98 : 92 }",
  "const products = reactive([",
  "id:1001",
  "id:1003",
  '奶油白法式连衣裙',
  '蝴蝶结长袜三双装',
  "function openProduct(id:number){uni.navigateTo({url:`/pages/product/detail/index?productId=${id}`})}"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static public-profile trust/product marker found: ${marker}`)
}

const requiredMarkers = [
  "const userId = ref('')",
  '卖家资料暂时不可用',
  "const unavailableProfileMessage = '卖家资料暂时不可用，请稍后再看'",
  "const noBackendProductsMessage = '这位卖家暂时没有公开在售宝贝'",
  'const sellerProducts = ref<ProductListItemResponse[]>([])',
  'listSellerProducts(userId.value)',
  "assertProductList(activeData, 'ACTIVE')",
  "assertProductList(soldData, 'SOLD')",
  '卖家宝贝暂时加载失败，请稍后重试',
  'function openProduct(productId: number): void',
  '商品资料暂时不可用，未打开详情',
  'isValidBackendUserId(userId.value)',
  'function isValidBackendUserId(value: string): boolean',
  'function navigateToUserRoute(missingUserIdTitle: string, buildUrl: (backendUserId: string) => string): void',
  "const followed = computed(() => profile.followedByMe === true)",
  "followedByMe: false",
  'followPublicProfile(userId.value)',
  'unfollowPublicProfile(userId.value)',
  '关注没有提交成功，请稍后重试',
  '取消关注没有提交成功，请稍后重试',
  '用户资料暂时不可用，未完成关注',
  '用户资料暂时不可用，未进入私信',
  '用户资料暂时不可用，未进入送礼',
  '用户资料暂时不可用，未进入举报',
  'v-if="hasIdentityVideo"',
  "const videoIdentityStoragePrefix = '/uploads/video-identity/'",
  "const showcaseImageStoragePrefix = '/uploads/community-image/'",
  "const avatarImageStoragePrefix = '/uploads/avatar/'",
  "const productImageStoragePrefix = '/uploads/product-image/'",
  'function validatedPublicMediaUrl(url: unknown, expectedPrefix: string | string[]): string',
  ':src="identityVideoUrl"',
  ':src="safeAvatarUrl"',
  'function decodeRouteValue(fieldName: string, value: string): string',
  "console.warn('public profile route decode failed'",
  "console.warn('public profile load failed'",
  "console.warn('public profile navigation failed'",
  "console.warn('public profile products load failed'",
  "console.warn('public profile follow mutation failed'",
  "console.warn('public profile product navigation failed'",
  "console.warn('public profile rejected media url'",
  "console.warn('public profile initialize failed'",
  "function assertProductList(value: unknown, expectedStatus: 'ACTIVE' | 'SOLD' = 'ACTIVE'): asserts value is ProductListItemResponse[]",
  'function assertPublicProfile(value: unknown, expectedUserId: string): asserts value is UserProfileResponse',
  "throw new Error('public profile userId mismatch')",
  "throw new Error('public profile video verified mismatch')"
]

const forbiddenStaticTrustTagPatterns = [
  { label: '同城交易', pattern: /<text\s+class="tag">同城交易<\/text>/ },
  { label: '平台担保', pattern: /<text\s+class="tag">平台担保<\/text>/ }
]

for (const { label, pattern } of forbiddenStaticTrustTagPatterns) {
  if (pattern.test(source)) failures.push(`${file}: public-profile must not render static trust/location tag copy without backend-derived seller data: ${label}`)
}

const forbiddenStaticTrustCopyPatterns = [
  { label: '交易请走平台担保', pattern: /交易请走平台担保/ },
  { label: '以平台担保、聊天留痕和订单状态为准', pattern: /以平台担保、聊天留痕和订单状态为准/ }
]

for (const { label, pattern } of forbiddenStaticTrustCopyPatterns) {
  if (pattern.test(source)) failures.push(`${file}: public-profile must not assert static escrow/guarantee copy unless derived from backend order/payment state: ${label}`)
}

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed public-profile marker: ${marker}`)
}

const publicProfileUserVisibleSource = fs.readFileSync(path.join(root, file), 'utf8')
for (const marker of ['后端', '服务端', '本地', '样例', 'demo', 'mock']) {
  if (publicProfileUserVisibleSource.includes(marker)) failures.push(`${file}: user-visible public profile copy must not expose technical/testing wording: ${marker}`)
}

const forbiddenFakeSuccessPatterns = [
  { label: 'direct local follow state assignment', pattern: /profile\.followedByMe\s*=(?!=)/ },
  { label: 'direct local follower count mutation', pattern: /profile\.followerCount\s*(?:\+\+|--|[+\-]=|=\s*profile\.followerCount\s*[+\-])/ },
  { label: 'direct local following count mutation', pattern: /profile\.followingCount\s*(?:\+\+|--|[+\-]=|=\s*profile\.followingCount\s*[+\-])/ },
  { label: 'fallback non-empty product array assignment', pattern: /sellerProducts\.value\s*=\s*\[\s*\{/ },
  { label: 'inline local profile success assignment', pattern: /Object\.assign\(profile,\s*\{(?!\s*\.\.emptyProfile)/s },
  { label: 'local profile spread success assignment', pattern: /Object\.assign\(profile,\s*\{\s*\.\.profile/s }
]

for (const { label, pattern } of forbiddenFakeSuccessPatterns) {
  if (pattern.test(source)) failures.push(`${file}: forbidden fake public-profile success pattern found: ${label}`)
}

const invalidLoadGuards = [
  "startsWith('PREVIEW')",
  "startsWith(\"PREVIEW\")",
  "userId.value === '0'",
  'userId.value === "0"',
  '/^\\d+$/.test(userId.value)'
]

for (const marker of invalidLoadGuards) {
  if (source.includes(marker)) failures.push(`${file}: public-profile load guard must use the shared positive backend user id validator, not partial marker: ${marker}`)
}

if (!/if\s*\(\s*!isValidBackendUserId\(userId\.value\)\s*\)\s*\{[^}]*resetProfile\(\)[^}]*failClosedProducts\(\)/s.test(source) || !source.includes('loadError.value = unavailableProfileMessage')) {
  failures.push(`${file}: loadProfile must fail closed for all invalid route userIds before fetching seller data`)
}

if (!source.includes("const routeUserId = decodeRouteValue('userId'") || !source.includes('userId.value = isValidBackendUserId(routeUserId) ? routeUserId :')) {
  failures.push(`${file}: route userId must be decoded fail-closed and validated before profile/product requests`)
}

if (!source.includes('assertPublicProfile(data, userId.value)') || !source.includes("if (String(backendProfile.userId) !== expectedUserId) throw new Error('public profile userId mismatch')")) {
  failures.push(`${file}: public profile must validate backend-returned userId before assigning profile state`)
}

if (!/function assertPublicProfile\(value: unknown, expectedUserId: string\): asserts value is UserProfileResponse[\s\S]*Number\.isSafeInteger\(backendProfile\.userId\)[\s\S]*typeof backendProfile\.nickname !== 'string'[\s\S]*typeof backendProfile\.videoVerified !== 'boolean'[\s\S]*backendProfile\.videoVerified === true && !validatedPublicMediaUrl\(backendProfile\.videoIdentityUrl, videoIdentityStoragePrefix\)[\s\S]*backendProfile\.showcaseImageUrls\.some\(\(url\) => typeof url !== 'string' \|\| !validatedPublicMediaUrl\(url, showcaseImageStoragePrefix\)\)[\s\S]*typeof backendProfile\.followedByMe !== 'boolean'/s.test(source)) {
  failures.push(`${file}: public profile must validate backend profile shape and approved media URLs before rendering trust/profile state`)
}

if (!/const products = computed\(\(\) => sellerProducts\.value\.map[\s\S]*coverImageUrl: resolveBackendMediaUrl\(validatedPublicMediaUrl\(item\.coverImageUrl, productImageStoragePrefix\)\)/s.test(source)) {
  failures.push(`${file}: public profile product computed state must validate coverImageUrl against PRODUCT_IMAGE media prefix`)
}

if (!/const hasApprovedSellerVideo = computed\(\(\) =>[\s\S]*profileLoaded\.value[\s\S]*isSellerProfile\.value[\s\S]*profile\.videoVerified === true[\s\S]*profile\.videoIdentityStatus === 'APPROVED'[\s\S]*hasApprovedPublicSellerVideo\(profile\)/s.test(source)) {
  failures.push(`${file}: public profile video trust state must require loaded seller profile and APPROVED backend video identity`)
}

if (!/const identityVideoUrl = computed\(\(\) => hasApprovedSellerVideo\.value \? resolveBackendMediaUrl\(validatedPublicMediaUrl\(profile\.videoIdentityUrl \|\| '', videoIdentityStoragePrefix\)\) : ''\)/s.test(source)) {
  failures.push(`${file}: public profile approved video URL must be validated against VIDEO_IDENTITY prefix before display`)
}

if (!source.includes("export const avatarImageStoragePrefix = '/uploads/avatar/'")) {
  failures.push(`${file}: public profile must define canonical AVATAR media prefix for real user avatars`)
}

if (!/function validatedPublicMediaUrl\(url: unknown, expectedPrefix: string \| string\[\]\): string[\s\S]*const prefixes = Array\.isArray\(expectedPrefix\) \? expectedPrefix : \[expectedPrefix\][\s\S]*const matchedPrefix = prefixes\.find\(\(prefix\) => url\.startsWith\(prefix\)\)[\s\S]*!matchedPrefix/s.test(source)) {
  failures.push(`${file}: public profile media URL validator must support explicit multi-prefix validation without weakening video/showcase prefixes`)
}

if (!/const safeAvatarUrl = computed\(\(\) => resolveBackendMediaUrl\(validatedPublicMediaUrl\(profile\.avatarUrl \|\| '', \[avatarImageStoragePrefix, showcaseImageStoragePrefix\]\)\)\)/s.test(source)) {
  failures.push(`${file}: public profile avatar URL must be validated against AVATAR or legacy COMMUNITY_IMAGE prefix before display`)
}

if (!/function assertProductList\(value: unknown, expectedStatus: 'ACTIVE' \| 'SOLD' = 'ACTIVE'\): asserts value is ProductListItemResponse\[\][\s\S]*Number\.isSafeInteger\(product\.productId\)[\s\S]*typeof product\.title !== 'string'[\s\S]*typeof product\.price !== 'string'[\s\S]*expectedStatus === 'ACTIVE' && product\.visible !== true[\s\S]*product\.status !== expectedStatus[\s\S]*product\.auditState !== 'APPROVED'[\s\S]*assertProductList\(activeData, 'ACTIVE'\)[\s\S]*sellerProducts\.value = data/s.test(source)) {
  failures.push(`${file}: public profile must validate backend product list shape and public visibility before rendering seller products`)
}

if (!/async function toggleFollow\(\): Promise<void>\s*\{[\s\S]*!profileLoaded\.value[\s\S]*assertPublicProfile\(data, userId\.value\)[\s\S]*Object\.assign\(profile, data\)/s.test(source)) {
  failures.push(`${file}: public profile follow mutation must require loaded backend profile and validate returned user state before assigning`)
}

if (!/async function initializePublicProfile\(\): Promise<void>\s*\{[\s\S]*try\s*\{[\s\S]*readQuery\(\)[\s\S]*if \(await loadProfile\(\)\) await loadSellerProducts\(\)[\s\S]*catch \(error\)\s*\{[\s\S]*resetProfile\(\)[\s\S]*failClosedProducts\(\)[\s\S]*console\.warn\('public profile initialize failed'/s.test(source)) {
  failures.push(`${file}: public profile must load seller products only after profile id validation succeeds and fail closed on initialization errors`)
}

if (!source.includes("url.startsWith('local://')") || !source.includes("url.startsWith('blob:')") || !source.includes("url.startsWith('data:')") || !source.includes('const matchedPrefix = prefixes.find((prefix) => url.startsWith(prefix))') || !source.includes('const relativePath = matchedPrefix ? url.slice(matchedPrefix.length) :') || !source.includes('!matchedPrefix') || !source.includes("lower.includes('%2e')") || !source.includes("url.includes('\\\\')") || !source.includes("console.warn('public profile rejected media url'")) {
  failures.push(`${file}: public profile must reject local/blob/data/placeholder/traversal media and enforce backend media prefixes before public display with diagnostics`)
}

if (!source.includes('coverImageUrl: resolveBackendMediaUrl(validatedPublicMediaUrl(item.coverImageUrl, productImageStoragePrefix))')) {
  failures.push(`${file}: public profile product cover images must be validated against backend PRODUCT_IMAGE media prefix before display`)
}

if (!/const showcasePhotos = computed\(\(\) => profileLoaded\.value \?[\s\S]*profile\.showcaseImageUrls[\s\S]*resolveBackendMediaUrl\(validatedPublicMediaUrl\(url, showcaseImageStoragePrefix\)\)[\s\S]*\.filter\(\(url\) => !!url\)/s.test(source)) {
  failures.push(`${file}: public profile showcase photos must only render after backend profile loads and must validate COMMUNITY_IMAGE URLs`)
}

if (source.includes(':src="profile.avatarUrl"') || !source.includes(':src="safeAvatarUrl"')) {
  failures.push(`${file}: public profile avatar must be validated against backend COMMUNITY_IMAGE media prefix before display`)
}

const forbiddenNavigationPatterns = [
  /function chat\(\)\{[^}]*if\(!userId\.value\)/,
  /function openGift\(\)\{[^}]*if\(!userId\.value\)/,
  /function report\(\)\{[^}]*if\(!userId\.value\)/
]

for (const pattern of forbiddenNavigationPatterns) {
  if (pattern.test(source)) failures.push(`${file}: public-profile sensitive navigation must validate positive backend userId, not only non-empty id`)
}

if (!/function navigateToUserRoute\(missingUserIdTitle: string, buildUrl: \(backendUserId: string\) => string\): void\s*\{[\s\S]*!profileLoaded\.value[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('public profile navigation failed'/s.test(source)) {
  failures.push(`${file}: public-profile user navigation must require loaded backend profile and handle async/synchronous failures`)
}
if (!/function openProduct\(productId: number\): void\s*\{[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('public profile product navigation failed'/s.test(source)) {
  failures.push(`${file}: public-profile product navigation must handle async and synchronous failures`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('public profile avoids static trust/product data and fails closed when backend data is unavailable')
