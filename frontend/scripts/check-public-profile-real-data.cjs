const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/user/public-profile/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

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
  '卖家数据暂时不可用',
  '未展示本地卖家样例',
  'const stats = computed(() => []',
  'const products = computed(() => []',
  'openProductUnavailable',
  '在售商品接口尚未接入，未打开本地样例商品',
  'isValidBackendUserId(userId.value)',
  'function isValidBackendUserId(value: string)',
  'const validUserId = isValidBackendUserId(userId.value)',
  "const followed = computed(() => profile.followedByMe === true)",
  "followedByMe: false",
  '关注接口暂未接通后端，未执行任何关注变更',
  '缺少真实用户ID，未进入私信',
  '缺少真实用户ID，未进入送礼',
  '缺少真实用户ID，未进入举报'
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

if (!/if\s*\(\s*!isValidBackendUserId\(userId\.value\)\s*\)\s*\{[^}]*未展示本地卖家样例/s.test(source)) {
  failures.push(`${file}: loadProfile must fail closed for all invalid route userIds before fetching seller data`)
}

const forbiddenNavigationPatterns = [
  /function chat\(\)\{[^}]*if\(!userId\.value\)/,
  /function openGift\(\)\{[^}]*if\(!userId\.value\)/,
  /function report\(\)\{[^}]*if\(!userId\.value\)/
]

for (const pattern of forbiddenNavigationPatterns) {
  if (pattern.test(source)) failures.push(`${file}: public-profile sensitive navigation must validate positive backend userId, not only non-empty id`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('public profile avoids static trust/product data and fails closed when backend data is unavailable')
