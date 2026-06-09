const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/community/compose/index.vue'
const productDetailFile = 'src/pages/product/detail/index.vue'
const source = [
  fs.readFileSync(path.join(root, 'src/pages/community/compose/compose-helpers.ts'), 'utf8'),
  fs.readFileSync(path.join(root, file), 'utf8'),
  fs.readFileSync(path.join(root, productDetailFile), 'utf8')
].join('\n')
const failures = []

const forbiddenMarkers = [
  "success: true",
  "uni.showModal({ title: '动态已发布'",
  "内容已进入小原圈社区广场。",
  "发布中...",
  "发布动态'",
  "cancelText: '继续编辑'",
  "form.images.push(ticket.storageUrl)",
  "form.images = form.images.slice(0, 9)",
  "form.images.splice(index, 1)",
  "约看提醒",
  "不要发布手机号、微信、支付宝等联系方式"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden fake-success/static-sensitive compose marker found: ${marker}`)
}

const requiredMarkers = [
  "import { COMMUNITY_TOPICS, createCommunityPost, isCommunityTopic, type CommunityTopic } from '../../../api/modules/community'",
  "submitMessage = ref('')",
  "{{ submitting ? '提交中...' : '提交发布' }}",
  'const topics = COMMUNITY_TOPICS',
  'const form = reactive({ topic: COMMUNITY_TOPICS[0] as CommunityTopic',
  'function selectTopic(topic: CommunityTopic): void',
  '请选择有效社区话题',
  "const issuedUrls: string[] = []",
  "form.images = [...form.images, ...issuedUrls].slice(0, 9)",
  "function removeImage(index: number) { form.images = form.images.filter((_, current) => current !== index) }",
  "submitMessage.value = `已提交发布：${created.postNo || created.postId}`",
  "title: '发布成功'",
  "content: '动态已进入社区，评论、点赞和私信都会以平台记录为准。'",
  "confirmText: '查看动态'",
  "cancelText: '回社区'",
  "success: (res) => redirectAfterPostCreated(res.confirm, created.postId)",
  "console.warn('community compose success modal failed'",
  "function redirectAfterPostCreated(stayOnPost: boolean, postId: number): void",
  "url: `/pages/community/detail/index?postId=${postId}`",
  "url: '/pages/tabbar/message/index'",
  "console.warn('community compose success redirect failed'",
  "console.warn('community compose detail redirect failed'",
  "console.warn('community compose tab redirect failed'",
  'function showPostCreatedNavigationFallback(postId: number): void',
  'submitMessage.value = postId > 0',
  '动态已发布，可到社区查看',
  "发布没有提交成功，未进入社区广场",
  "const relatedProductId = ref<number | null>(null)",
  "function applyRelatedProductRoute(options?: Record<string, string | undefined>): void",
  "function positiveRouteNumber(value?: string): number | null",
  "function toSafeBackendId(value: string): number | null",
  "return toSafeBackendId(decoded)",
  "关联商品编号无效，已取消关联",
  "function clearRelatedProduct(): void",
  "import { getProductDetail, listMyProducts } from '../../../api/modules/product'",
  "function assertRelatedProductForCompose(value: unknown, expectedProductId: number)",
  "if (value.status !== 'PUBLISHED')",
  "动态尚未进入社区，请刷新社区后确认",
  "function normalizeComposeProductPrice(value: unknown): string | null",
  "if (typeof value === 'number')",
  "if (typeof value !== 'string') return null",
  "async function hydrateRelatedProductFromBackend(productId: number): Promise<void>",
  "const product = await getProductDetail(productId)",
  "assertRelatedProductForCompose(product, productId)",
  "relatedProductPrice.value = normalizeComposeProductPrice(product.price)",
  "relatedProductLoading.value",
  "关联商品校验中，请稍后提交",
  "community compose related product load failed",
  "relatedProductId: relatedProductId.value",
  "if (form.title.length < 4) return uni.showToast({ title: '标题至少 4 个字', icon: 'none' })",
  "if (form.content.length < 8) return uni.showToast({ title: '内容至少 8 个字', icon: 'none' })",
  "class=\"related-product\"",
  "class=\"related-clear tapable\"",
  "class=\"related-status\"",
  "平台商品待校验",
  "价格以商品详情为准",
  "const productPickerOpen = ref(false)",
  "const myProductsLoading = ref(false)",
  "const myProductsError = ref('')",
  "const myProductsLoaded = ref(false)",
  "const mySelectableProducts = ref<ComposeSelectableRelatedProduct[]>([])",
  "const productPickerHint = computed(() =>",
  "const productPickerActionText = computed(() =>",
  "function toggleMyProductPicker(): void",
  "async function loadMySelectableProducts(force = false): Promise<void>",
  "const products = await listMyProducts()",
  "mySelectableProducts.value = selectableRelatedProductsForCompose(products)",
  "function selectRelatedProductFromMine(product: ComposeSelectableRelatedProduct): void",
  "void hydrateRelatedProductFromBackend(product.productId)",
  "class=\"product-picker\"",
  "选择我的商品",
  "只展示已通过视频认证的在售真实商品",
  "暂无可关联的认证在售商品",
  "class=\"product-option tapable\"",
  "export interface ComposeSelectableRelatedProduct",
  "function isSelectableRelatedProductForCompose(value: unknown)",
  "function selectableRelatedProductsForCompose(value: unknown): ComposeSelectableRelatedProduct[]",
  "function validatedComposeProductCoverUrl(url: unknown): string | null",
  "const productImageStoragePrefix = '/uploads/product-image/'",
  "coverImageUrl: validatedComposeProductCoverUrl(product.coverImageUrl)",
  "const normalizedPrice = normalizeComposeProductPrice(product.price)",
  "if (!Array.isArray(value))",
  "product.visible === true",
  "product.status === 'ACTIVE'",
  "product.auditState === 'APPROVED'",
  "product.sellerVideoVerified === true",
  "function previewUploadedImages(index: number): void",
  "uni.previewImage({ current, urls })",
  "console.warn('community compose image preview failed'"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing real backend/fail-closed compose marker: ${marker}`)
}

if (!/createCommunityPost\(\{[\s\S]*relatedProductId: relatedProductId\.value[\s\S]*\}\)/.test(source)) {
  failures.push(`${file}: compose submit must send only relatedProductId for backend related-product validation`)
}

if (!/showModalWithFailure\(\{[\s\S]*success: \(res\) => redirectAfterPostCreated\(res\.confirm, created\.postId\),[\s\S]*fail: \(error: unknown\) => \{[\s\S]*showPostCreatedNavigationFallback\(created\.postId\)/s.test(source)) {
  failures.push(`${file}: compose success modal must have a fail callback that preserves the backend-created post state`)
}

if (!/function redirectAfterPostCreated\(stayOnPost: boolean, postId: number\): void[\s\S]*redirectToWithFailure\(\{[\s\S]*fail: \(error: unknown\) => \{[\s\S]*showPostCreatedNavigationFallback\(postId\)[\s\S]*switchTabWithFailure\(\{[\s\S]*fail: \(error: unknown\) => \{[\s\S]*showPostCreatedNavigationFallback\(postId\)[\s\S]*catch \(error\)[\s\S]*showPostCreatedNavigationFallback\(postId\)/s.test(source)) {
  failures.push(`${file}: compose post-success navigation must handle redirectTo/switchTab asynchronous and synchronous failures`)
}

if (/createCommunityPost\(\{[\s\S]*(?:relatedProductTitle|relatedProductPrice)[\s\S]*\}\)/.test(source)) {
  failures.push(`${file}: compose submit must not send route product title/price as trusted backend facts`)
}

if (/options\?\.productTitle|options\?\.productPrice|safeRouteDisplayText|safeRoutePrice/.test(source)) {
  failures.push(`${file}: compose related product UI must not trust route product title/price; load product detail from backend by productId`)
}

if (/mockProduct|demoProduct|placeholderProduct|fakeProduct|const mySelectableProducts = ref<ComposeSelectableRelatedProduct\[\]>\(\s*\[\s*\{/.test(source)) {
  failures.push(`${file}: compose product picker must not seed fake/demo selectable products`)
}

if (!/function selectableRelatedProductsForCompose\(value: unknown\): ComposeSelectableRelatedProduct\[\][\s\S]*if \(!Array\.isArray\(value\)\)[\s\S]*value\.filter\(isSelectableRelatedProductForCompose\)\.map/s.test(source)) {
  failures.push(`${file}: compose product picker must validate and filter the real my-products response before rendering`)
}

if (!/function normalizeComposeProductPrice\(value: unknown\): string \| null[\s\S]*typeof value === 'number'[\s\S]*Number\.isFinite\(value\)[\s\S]*typeof value !== 'string'[\s\S]*value\.trim\(\)/s.test(source)) {
  failures.push(`${file}: related product price must normalize both backend JSON numbers and strings before display`)
}

if (/product\.price\.trim\(\)/.test(source)) {
  failures.push(`${file}: related product price must not call trim directly because backend may serialize BigDecimal as a JSON number`)
}

if (!/function isSelectableRelatedProductForCompose\(value: unknown\)[\s\S]*Number\.isSafeInteger\(product\.productId\)[\s\S]*product\.visible === true[\s\S]*product\.status === 'ACTIVE'[\s\S]*product\.auditState === 'APPROVED'[\s\S]*product\.sellerVideoVerified === true/s.test(source)) {
  failures.push(`${file}: compose product picker must only expose visible ACTIVE APPROVED products from video-verified sellers`)
}

if (!/async function loadMySelectableProducts\(force = false\): Promise<void>[\s\S]*const products = await listMyProducts\(\)[\s\S]*mySelectableProducts\.value = selectableRelatedProductsForCompose\(products\)[\s\S]*myProductsError\.value/s.test(source)) {
  failures.push(`${file}: compose product picker must load real mine products and fail closed on errors`)
}

if (!/function selectRelatedProductFromMine\(product: ComposeSelectableRelatedProduct\): void[\s\S]*relatedProductId\.value = product\.productId[\s\S]*relatedProductTitle\.value = '平台商品待校验'[\s\S]*relatedProductPrice\.value = null[\s\S]*void hydrateRelatedProductFromBackend\(product\.productId\)/s.test(source)) {
  failures.push(`${file}: selecting a mine product must reuse the relatedProductId and backend detail validation path`)
}

if (!/url: `\/pages\/community\/compose\/index\?productId=\$\{encodeURIComponent\(String\(detail\.value\.productId\)\)\}&productTitle=\$\{encodeURIComponent\(detail\.value\.title\)\}&productPrice=\$\{encodeURIComponent\(String\(detail\.value\.price\)\)\}`/.test(source)) {
  failures.push(`${file}: product detail must navigate to compose with productId plus display title/price while compose still validates backend detail before submit`)
}

if (!/function toSafeBackendId\(value: string\): number \| null[\s\S]*Number\.isSafeInteger\(numeric\)[\s\S]*function positiveRouteNumber\(value\?: string\): number \| null[\s\S]*return toSafeBackendId\(decoded\)/s.test(source)) {
  failures.push(`${file}: route productId must reject unsafe integers before Number() conversion`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community compose page only shows publish success after backend createPost and avoids static sensitive/local mutation copy')
