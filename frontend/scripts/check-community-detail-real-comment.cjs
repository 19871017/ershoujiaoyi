const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const detailPath = path.join(root, 'src/pages/community/detail/index.vue')
const source = fs.readFileSync(detailPath, 'utf8')

const failures = []

if (!source.includes('createCommunityComment')) {
  failures.push('community detail must call createCommunityComment instead of local-only comment append')
}

if (!source.includes('unlikeCommunityPost')) {
  failures.push('community detail must call unlikeCommunityPost for persisted unlike instead of local-only liked=false toggles')
}

const createCallIndex = source.indexOf('createCommunityComment(')
const pushIndex = source.indexOf('comments.push(')
if (pushIndex === -1 || (createCallIndex !== -1 && pushIndex < createCallIndex)) {
  failures.push('community detail must only append comments after backend confirms persistence')
}

if (!/提交失败|评论发送失败|没有发送成功/.test(source)) {
  failures.push('community detail must show explicit failure copy when comment API fails')
}

if (!/disabled=.*commentSubmitting|:disabled="commentSubmitting"/.test(source)) {
  failures.push('community detail send button should be disabled while comment submission is in flight')
}

if (!source.includes('getCommunityPostDetail')) {
  failures.push('community detail must load post detail from backend instead of rendering static author/content/product data')
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

if (!/relatedProductId|productId\.value/.test(source) || !/缺少关联商品|没有关联商品|关联商品/.test(source)) {
  failures.push('community detail related product navigation must come from backend detail and fail closed when missing')
}

if (!source.includes('function isValidCommunityPostId')) {
  failures.push('community detail must use a positive backend post id validator before loading, liking, reporting, or commenting')
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
