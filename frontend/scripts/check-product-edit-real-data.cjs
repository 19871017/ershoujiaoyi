const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/product/edit/index.vue'
const supportFiles = [
  'src/pages/product/edit/product-edit-helpers.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')

const failures = []

const forbiddenMarkers = [
  "const productId = ref('1001')",
  "catch { form.title='奶油白法式连衣裙 只穿过一次'",
  "form.description='适合春夏，细节图完整，支持同城约看。'",
  "form.price='129'",
  "city:'深圳'",
  "form = reactive({ title:'', description:'', price:'', category:'衣物', condition:'几乎全新', city:'深圳'",
  'Number(productId.value)); form.title',
  'updateProduct(Number(productId.value)',
  '@change="form.safeTrade = !form.safeTrade"',
  '@change="form.chatProof = !form.chatProof"',
  "uni.showToast({title:`已添加 ${images.value.length} 张图片`,icon:'none'})",
  "images.value.push(ticket.storageUrl)",
  'function removeImage(url:string){ images.value = images.value.filter(item => item !== url) }',
  '仅走平台担保交易',
  "safeTrade:true, chatProof:true",
  'form.category',
  'form.condition',
  'form.city',
  'const categories =',
  'const conditions ='
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden product-edit demo/default or unsafe local-only marker found: ${marker}`)
}

const requiredMarkers = [
  "const productId = ref('')",
  'isValidBackendProductId',
  "const productImageStoragePrefix = '/uploads/product-image/'",
  "const editableProductStatuses = new Set(['created', 'PENDING_AUDIT', 'ACTIVE'])",
  "const knownProductAuditStates = new Set(['pending', 'PENDING', 'APPROVED', 'REJECTED'])",
  'function decodeRouteValue(fieldName: string, value: string): string',
  "console.warn('product edit route decode failed'",
  "console.warn('product edit route read failed'",
  "loadError.value = '缺少有效商品编号，未加载本地样例商品'",
  "loadError.value = userSafeLoadErrors.has(message) ? message : '商品详情加载失败，未展示本地样例商品'",
  'function assertProductDetail(value: unknown): asserts value is ProductDetailResponse',
  'assertProductDetail(detail)',
  "throw new Error('product edit productId mismatch')",
  'form.title = detail.title',
  'form.description = detail.description ||',
  'form.price = String(detail.price)',
  'images.value = (detail.imageUrls || []).map(validatedProductImageUrl)',
  'createMediaUploadTicket({ scene: \'PRODUCT_IMAGE\'',
  'uploadMediaTicketFile(ticket, path)',
  'function hasInvalidTempImagePath(path: string): boolean',
  'function hasInvalidProductImageUrl(url: unknown): boolean',
  'function validatedProductImageUrl(storageUrl: unknown): string',
  "console.warn('product edit invalid product image url'",
  "console.warn('product edit image upload failed'",
  "console.warn('product edit image picker failed'",
  "console.warn('product edit toast failed'",
  'updateProduct(backendProductId.value',
  'safeImageUrls = images.value.map(validatedProductImageUrl)',
  'imageUrls: safeImageUrls',
  '保存失败时不会展示本地成功状态',
  'product edit media/trade controls are read-only until backend update contract supports them',
  '商品图片上传票据已生成，需提交修改审核后才会更新商品图片',
  '商品图片移除需提交修改审核后生效',
  "function toggleTradePreference(_field: 'serverTradeOnly' | 'serverChatRecord')",
  '交易方式以服务端订单与支付状态为准',
  '聊天记录以服务端会话为准',
  '图片上传中，请稍后提交',
  "if (!/^\\d+(\\.\\d{1,2})?$/.test(form.price) || !Number.isFinite(priceValue)) return '价格格式不正确'",
  "console.warn('product edit success modal failed'",
  "console.warn('product edit detail navigation failed'",
  "console.warn('product edit save failed'"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed/backend-derived product-edit marker: ${marker}`)
}

const requiredProductImageUrlRejectionMarkers = [
  "typeof url !== 'string'",
  "url.startsWith('local://')",
  "url.startsWith('blob:')",
  "url.startsWith('data:')",
  "lower.includes('%2e')",
  "lower.includes('%2f')",
  "lower.includes('%5c')",
  "url.includes('\\\\')",
  "url.includes('..')",
  "url.includes('//')",
  "relativePath.split('/').some"
]

for (const marker of requiredProductImageUrlRejectionMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: product edit must reject unsafe PRODUCT_IMAGE URL marker before display or submit: ${marker}`)
}

if (/v-model(?:\.trim)?=/.test(source)) {
  failures.push(`${file}: product edit form must use explicit :value + @input bindings instead of v-model drift-prone bindings`)
}

if (!/function assertProductDetail\(value: unknown\): asserts value is ProductDetailResponse[\s\S]*Number\.isSafeInteger\(detail\.productId\)[\s\S]*typeof detail\.title !== 'string'[\s\S]*Array\.isArray\(detail\.imageUrls\)[\s\S]*!editableProductStatuses\.has\(String\(detail\.status\)\)[\s\S]*!knownProductAuditStates\.has\(String\(detail\.auditState\)\)[\s\S]*detail\.visible !== true && detail\.status === 'ACTIVE'/s.test(source)) {
  failures.push(`${file}: product edit must validate backend product shape and fail closed on unknown status/audit values before rendering`)
}

if (!/function chooseImage\(\): void[\s\S]*if \(uploadingImages\.value\) return[\s\S]*uploadingImages\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage[\s\S]*createMediaUploadTicket\(\{ scene: 'PRODUCT_IMAGE'[\s\S]*validatedProductImageUrl\(uploaded\.storageUrl\)[\s\S]*showToastSafely\('商品图片上传票据已生成，需提交修改审核后才会更新商品图片'[\s\S]*catch \(error\)[\s\S]*console\.warn\('product edit image upload failed'[\s\S]*fail\(error: unknown\)[\s\S]*uploadingImages\.value = false[\s\S]*console\.warn\('product edit image picker failed'[\s\S]*catch \(error\)[\s\S]*uploadingImages\.value = false[\s\S]*console\.warn\('product edit image picker failed'/s.test(source)) {
  failures.push(`${file}: product edit image picker must use PRODUCT_IMAGE tickets, validate uploaded storage URLs, clear busy state, and log async/synchronous chooser failures without misclassifying success-toast failures`)
}

if (!/async function save\(\): Promise<void>[\s\S]*if \(saving\.value\) return[\s\S]*if \(uploadingImages\.value\)[\s\S]*if \(!backendProductId\.value\)[\s\S]*saving\.value = true[\s\S]*try\s*\{[\s\S]*safeImageUrls = images\.value\.map\(validatedProductImageUrl\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product edit save failed'[\s\S]*await updateProduct\(backendProductId\.value[\s\S]*imageUrls: safeImageUrls[\s\S]*catch \(error\)[\s\S]*console\.warn\('product edit save failed'[\s\S]*try\s*\{\s*uni\.showModal\(modalOptions\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product edit success modal failed'[\s\S]*finally\s*\{\s*saving\.value = false/s.test(source)) {
  failures.push(`${file}: product edit save must block duplicate submits, wait for image uploads, revalidate PRODUCT_IMAGE URLs, isolate backend save failures from post-success UI failures, and handle modal failures`)
}

if (!/function inputValue\(field: TextFieldKey, event: unknown\): string \| undefined[\s\S]*console\.warn\('product edit input event invalid'[\s\S]*function updateTextField\(field: TextFieldKey, event: unknown\): void[\s\S]*if \(value === undefined\)[\s\S]*return[\s\S]*form\[field\] = value/s.test(source)) {
  failures.push(`${file}: product edit input handlers must not silently clear fields on malformed input events`)
}

if (!/function navigateToDetailAfterSave\(\): void[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)[\s\S]*console\.warn\('product edit detail navigation failed'/s.test(source)) {
  failures.push(`${file}: product edit detail navigation must handle async and synchronous failures`)
}

if (!/function initializePage\(\): void[\s\S]*try\s*\{\s*readQuery\(\)[\s\S]*catch \(error\)[\s\S]*productId\.value = ''[\s\S]*loadError\.value = '缺少有效商品编号，未加载本地样例商品'[\s\S]*console\.warn\('product edit route read failed'[\s\S]*void loadDetail\(\)/s.test(source)) {
  failures.push(`${file}: product edit route initialization must fail closed if route reading throws`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('product edit page is backend-derived and fail-closed')
