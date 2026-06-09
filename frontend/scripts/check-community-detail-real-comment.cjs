const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const detailPath = path.join(root, 'src/pages/community/detail/index.vue')
const detailPageSource = fs.readFileSync(detailPath, 'utf8')
const source = [
  fs.readFileSync(path.join(root, 'src/pages/community/detail/community-detail-helpers.ts'), 'utf8'),
  detailPageSource
].join('\n')
const templateSource = detailPageSource.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? ''

const failures = []

if (!source.includes('createCommunityComment')) {
  failures.push('community detail must call createCommunityComment instead of local-only comment append')
}

if (!source.includes('unlikeCommunityPost')) {
  failures.push('community detail must call unlikeCommunityPost for persisted unlike instead of local-only liked=false toggles')
}

const createCallIndex = source.indexOf('createCommunityComment(')
const applyAfterCreatePattern = /const saved = await createCommunityComment\(numericPostId, content\)[\s\S]*assertCommunityCommentResponse\(saved\)[\s\S]*const refreshed = await getCommunityPostDetail\(numericPostId\)[\s\S]*assertCommunityPostDetail\(refreshed, numericPostId\)[\s\S]*applyCommunityPostDetail\(refreshed\)/.test(source)
if (createCallIndex === -1 || !applyAfterCreatePattern) {
  failures.push('community detail must refresh backend detail after comment creation so duplicate comments do not inflate local comment count')
}
if (source.includes('comments.push(')) {
  failures.push('community detail must not blindly append comments locally; backend may return an existing duplicate comment without increasing commentCount')
}
if (!source.includes('const serverCommentCount = ref(0)') || !templateSource.includes('{{ serverCommentCount }}') || !source.includes('serverCommentCount.value = detail.commentCount')) {
  failures.push('community detail comment count must render backend commentCount instead of comments.length')
}

if (!/提交失败|评论发送失败|没有发送成功/.test(source)) {
  failures.push('community detail must show explicit failure copy when comment API fails')
}

if (!source.includes("if (draft.value.length < 4) return uni.showToast({ title: '评论至少 4 个字', icon: 'none' })")) {
  failures.push('community detail comment length must match backend minimum of 4 chars before request')
}

if (!/disabled=.*commentSubmitting|:disabled="commentSubmitting"/.test(source)) {
  failures.push('community detail send button should be disabled while comment submission is in flight')
}

if (!source.includes('getCommunityPostDetail')) {
  failures.push('community detail must load post detail from backend instead of rendering static author/content/product data')
}

const realAuthorMarkers = [
  'function assertCommunityPostDetail(value: unknown, expectedPostId: number): asserts value is CommunityPostDetailResponse',
  'function assertCommunityCommentResponse(value: unknown): asserts value is CommunityCommentResponse',
  'assertCommunityPostDetail(detail, numericPostId)',
  'assertCommunityCommentResponse(saved)',
  'assertCommunityPostDetail(saved, numericPostId)',
  "throw new Error('community detail postId mismatch')",
  'detail.imageUrls = sanitizeCommunityDetailImageUrls(detail.imageUrls, detail.postId)',
  'export function sanitizeCommunityDetailImageUrls(imageUrls: unknown, postId: number): string[]',
  "console.warn('community detail invalid image isolated'",
  "throw new Error('community comment invalid backend response')",
  'authorAvatarUrl.value = resolveBackendMediaUrl(validatedCommunityAvatarUrl(detail.authorAvatar || \'\'))',
  '<view class="post-author tapable" @click="openAuthorProfile">',
  'function openAuthorProfile()',
  "`/pages/user/public-profile/index?userId=${encodeURIComponent(String(authorId.value))}`",
  'function messageAuthor()',
  "`/pages/chat/conversation/index?receiverId=${encodeURIComponent(String(authorId.value))}`",
  '<view class="tapable" @click="messageAuthor">私信作者</view>',
  '<view class="message-author tapable" :class="{ disabled: !canMessageAuthor }" @click="messageAuthor">私信</view>',
  '作者资料暂时不可用，未打开主页',
  '作者资料暂时不可用，未进入私信',
  '作者资料暂时不可用，未完成关注',
  '关注状态没有提交成功，未执行本地关注变更',
  'name: item.authorName || `用户 ${item.authorId}`',
  'avatarUrl: resolveBackendMediaUrl(validatedCommunityAvatarUrl(item.authorAvatar || \'\'))',
  'function toCommentItem(item: CommunityCommentResponse): CommentItem',
  'comments.splice(0, comments.length, ...(detail.comments || []).map(toCommentItem))',
  'function validatedCommunityImageUrl(url: unknown): string',
  'function validatedCommunityAvatarUrl(url: unknown): string',
  'function validatedStoredImageUrl(url: unknown, storagePrefixes: string[]): string',
  "export const avatarImageStoragePrefix = '/uploads/avatar/'",
  'function previewPostImage(index: number): void',
  'uni.previewImage({ current, urls: imageSlots.value })',
  "console.warn('community detail image preview failed'",
  '@click="previewPostImage(index)"'
]

for (const marker of realAuthorMarkers) {
  if (!source.includes(marker)) {
    failures.push(`community detail must render real author/commenter profile marker: ${marker}`)
  }
}

if (source.includes('name: `用户 ${item.authorId}`') || source.includes('avatar: firstChar(String(item.authorId))')) {
  failures.push('community detail comments must not rely only on authorId for commenter identity')
}

for (const marker of ['后端', '服务端', '本地', '样例', 'demo', 'mock']) {
  if (templateSource.includes(marker)) failures.push(`community detail user-visible copy must not expose technical/testing wording: ${marker}`)
}

const forbiddenStaticMarkers = [
  '小原圈软糖',
  '梨涡裙摆',
  '奶油白法式连衣裙',
  'productId=1001',
  'likeCount = ref(128)',
  '平台担保 · 同城可约看 · ¥129'
]
for (const marker of forbiddenStaticMarkers) {
  if (source.includes(marker)) {
    failures.push(`community detail must not contain static/demo trust, product, comment or like marker: ${marker}`)
  }
}

if (!/errorText|loadError/.test(source) || !/动态详情加载失败|动态详情接口/.test(source)) {
  failures.push('community detail must fail closed with explicit backend load error copy')
}
if (!source.includes('const detailLoading = ref(false)') || !source.includes('const detailLoaded = ref(false)') || !templateSource.includes('v-else-if="detailLoading || !detailLoaded"') || !templateSource.includes('v-else-if="detailLoaded"')) {
  failures.push('community detail must hide post actions and comment form behind a real detail loading gate before backend detail validation passes')
}

if (!/relatedProductId|productId\.value/.test(source) || !/缺少关联商品|没有关联商品|关联商品/.test(source)) {
  failures.push('community detail related product navigation must come from backend detail and fail closed when missing')
}

if (/v-model(?:\.trim)?=/.test(source)) {
  failures.push('community detail comment input must use explicit :value + @input bindings instead of v-model drift-prone bindings')
}

const inputMarkers = [
  '<input :value="draft" class="comment-input"',
  '@input="updateDraft"',
  '@blur="trimDraft"',
  'function updateDraft(event: unknown): void',
  'function trimDraft(): void',
  'inputValue(event)'
]
for (const marker of inputMarkers) {
  if (!source.includes(marker)) failures.push(`community detail comment input missing explicit input marker: ${marker}`)
}

const relatedProductMarkers = [
  'productId.value = detail.relatedProductId || null',
  "relatedProductTitle.value = detail.relatedProductTitle || '平台商品'",
  'relatedProductPrice.value = detail.relatedProductPrice ?? null',
  'const productTitle = computed(() => `关联商品：${relatedProductTitle.value || \'平台商品\'}`)',
  'const hasRelatedProduct = computed(() => productId.value !== null && isValidBackendProductId(String(productId.value)))',
  '<view v-if="hasRelatedProduct" class="goods-card ds-card tapable" @click="openProduct">',
  "const price = relatedProductPrice.value === null || relatedProductPrice.value === undefined || relatedProductPrice.value === '' ? '价格以平台为准' : `¥${relatedProductPrice.value}`"
]
for (const marker of relatedProductMarkers) {
  if (!source.includes(marker)) failures.push(`community detail related product marker missing: ${marker}`)
}
if (templateSource.includes('class="goods-card ds-card" :class="{ tapable: hasRelatedProduct, disabled: !hasRelatedProduct }"')) {
  failures.push('community detail must not show a disabled related-product card when backend detail has no relatedProductId')
}

if (!source.includes('function isValidCommunityPostId')) {
  failures.push('community detail must use a positive backend post id validator before loading, liking, reporting, or commenting')
}
if (!source.includes('function toSafeBackendId(value: string): number | null') || !source.includes('Number.isSafeInteger(numeric)')) {
  failures.push('community detail backend id validator must reject unsafe integers before Number() conversion')
}
if (!source.includes('function isValidBackendProductId(value: string): boolean')) {
  failures.push('community detail related product must use a product id validator instead of reusing post id semantics')
}

if (/postId\.value\s*===\s*['"]preview['"]/.test(source) || /postId\.value\s*===\s*['"]UNKNOWN['"]/.test(source)) {
  failures.push('community detail must not rely on narrow preview/UNKNOWN equality checks for sensitive post actions')
}

if (!/if \(!isValidCommunityPostId\(postId\.value\)\)/.test(source)) {
  failures.push('community detail load path must fail closed unless postId passes isValidCommunityPostId(postId.value)')
}

for (const functionName of ['sendComment', 'likePost', 'reportPost']) {
  const match = source.match(new RegExp(`function ${functionName}\\([\\s\\S]*?\\n}`))
  if (!match || !match[0].includes('isValidCommunityPostId(postId.value)')) {
    failures.push(`community detail ${functionName} must validate the route-derived postId with isValidCommunityPostId before the sensitive action`)
  }
}

const likeMatch = source.match(/async function likePost\([\s\S]*?\n}/)
if (!likeMatch || !likeMatch[0].includes('await unlikeCommunityPost') || !likeMatch[0].includes('await likeCommunityPost')) {
  failures.push('community detail likePost must use backend like/unlike APIs for both state changes')
}
if (/liked\.value\s*=\s*!liked\.value[\s\S]{0,120}await (?:un)?likeCommunityPost/.test(source)) {
  failures.push('community detail must not flip liked state before the backend like/unlike request succeeds')
}
if (!source.includes('liked.value = Boolean(saved.likedByMe)')) {
  failures.push('community detail must render liked state from backend response likedByMe instead of locally inverting current state')
}
if (!source.includes('likeCount.value = saved.likeCount')) {
  failures.push('community detail must render like count from backend response after like/unlike')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community detail comment flow is backend-persisted and fails closed')
