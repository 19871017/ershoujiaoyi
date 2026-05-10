const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const sourcePath = path.join(root, 'src/pages/ranking/index.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

const failures = []

const forbiddenMarkers = [
  'credit: number',
  'verified: boolean',
  '信用 {{ item.credit }}',
  "{{ item.verified ? '已实名' : '待实名' }}",
  "'信用98'",
  "'信用99'"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`ranking page must not expose static trust/identity marker: ${marker}`)
}

if (source.includes("activeType.value === 'deal' ? 'deals'")) {
  failures.push('ranking page must not sort/display a static deal leaderboard as backend-derived transaction data')
}

if (source.includes('const rankings = reactive<RankingUser[]>([') || source.includes("tags: ['") || source.includes('<view class="verify">预览用户</view>') || source.includes('榜单为开发预览数据')) {
  failures.push('ranking page must not render static preview leaderboard users as a substitute for backend ranking data')
}
if (!source.includes('未展示本地预览榜单') || !source.includes('没有后端榜单数据时，本页保持空态')) {
  failures.push('ranking page must fail closed with an explicit empty state when backend ranking data is unavailable')
}
if (!source.includes('const rankings = ref<RankingUser[]>([])')) {
  failures.push('ranking page must initialize leaderboard rows as an empty backend-derived ref')
}
if (!source.includes('listUserRankings(activeGender.value, 20)') || !source.includes("loadError.value = '榜单接口加载失败，未展示本地预览榜单'")) {
  failures.push('ranking page must load backend rankings and fail closed instead of static ranking rows')
}
if (!source.includes('const stats = computed(() => [') || !source.includes("{ value: `${rankings.value.length}`, label: '后端上榜用户' }")) {
  failures.push('ranking page stats must aggregate backend-loaded leaderboard rows only')
}
if (!source.includes('实名、信用、成交等信任指标必须由后端审计数据提供')) {
  failures.push('ranking page must explicitly state that identity/credit/deal trust metrics require backend audit data')
}

const previewTrustCopyPatterns = [
  { label: '平台担保', pattern: /榜单[^\n]*平台担保|交易请走平台担保|平台担保[^\n]*榜单/ },
  { label: '同城可约', pattern: /tags:\s*\[[^\]]*['"]同城可约['"]/ },
  { label: '同城约看', pattern: /tags:\s*\[[^\]]*['"]同城约看['"]/ }
]
for (const { label, pattern } of previewTrustCopyPatterns) {
  if (pattern.test(source)) failures.push(`ranking page preview/static copy must not assert transaction/location trust signal without backend state: ${label}`)
}
if (!source.includes('交易状态以服务端订单、支付和售后记录为准')) {
  failures.push('ranking page safety copy must use neutral backend-record wording for transactions')
}

const forbiddenLocalFollowMarkers = [
  'followed: boolean',
  'followed: true',
  'followed: false'
]
for (const marker of forbiddenLocalFollowMarkers) {
  if (source.includes(marker)) failures.push(`ranking page must not render local/static follow state: ${marker}`)
}
if (source.includes('榜单页不执行本地关注变更，请进入真实主页操作')) {
  failures.push('ranking page should use the existing backend follow/unfollow API instead of a fail-closed follow stub')
}
if (!source.includes('followPublicProfile') || !source.includes('unfollowPublicProfile')) {
  failures.push('ranking page follow action must call backend follow/unfollow APIs when they are available')
}
if (!source.includes('viewerFollows: !!updated.followedByMe') || source.includes('item.viewerFollows = !item.viewerFollows')) {
  failures.push('ranking page follow state must update only from backend followedByMe ack, never local inversion')
}
if (!source.includes('followingIds.value.has(item.id)')) {
  failures.push('ranking page follow action must guard duplicate clicks per backend user id')
}

const navigationRiskMarkers = [
  "receiverId=21",
  "userId=21",
  "PREVIEW-RANKING",
  "SAMPLE-RANKING"
]
for (const marker of navigationRiskMarkers) {
  if (source.includes(marker)) failures.push(`ranking preview users must not be used as real sensitive navigation ids: ${marker}`)
}

if (!source.includes("if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少后端用户编号，未进入会话'")) {
  failures.push('ranking chat action must validate backend user ids before routing into IM')
}
if (!source.includes("if (!item.id || item.id <= 0) return uni.showToast({ title: '缺少后端用户编号，未打开主页'")) {
  failures.push('ranking profile action must validate backend user ids before routing into public profile')
}
if (!source.includes('/pages/chat/conversation/index?receiverId=${item.id}') || !source.includes('/pages/user/public-profile/index?userId=${item.id}')) {
  failures.push('ranking backend-derived rows should link to real chat/profile routes only after id validation')
}

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('ranking page avoids static trust/identity/transaction data')
