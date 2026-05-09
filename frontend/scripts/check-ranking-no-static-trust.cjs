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

if (!source.includes('榜单为开发预览数据') || !source.includes('实名、信用、成交等信任指标不在本页展示')) {
  failures.push('ranking page must explicitly state that trust/identity/transaction metrics are not displayed without backend data')
}

const previewTrustCopyPatterns = [
  { label: '平台担保', pattern: /榜单[^\n]*平台担保|交易请走平台担保|平台担保[^\n]*榜单/ },
  { label: '同城可约', pattern: /tags:\s*\[[^\]]*['"]同城可约['"]/ },
  { label: '同城约看', pattern: /tags:\s*\[[^\]]*['"]同城约看['"]/ }
]
for (const { label, pattern } of previewTrustCopyPatterns) {
  if (pattern.test(source)) failures.push(`ranking page preview/static copy must not assert transaction/location trust signal without backend state: ${label}`)
}
if (!source.includes('交易请以平台订单、支付和售后状态为准')) {
  failures.push('ranking page safety copy must use neutral backend-record wording for transactions')
}

const forbiddenLocalFollowMarkers = [
  'followed: boolean',
  'item.followed',
  'followed: true',
  'followed: false'
]
for (const marker of forbiddenLocalFollowMarkers) {
  if (source.includes(marker)) failures.push(`ranking page must not render local/static follow state: ${marker}`)
}
if (!source.includes('关注接口暂未接通后端，未执行任何关注变更')) {
  failures.push('ranking page follow action must fail closed with explicit no-change copy')
}

const navigationRiskMarkers = [
  "uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${item.id}` })",
  "uni.navigateTo({ url: `/pages/user/public-profile/index?userId=${item.id}` })"
]
for (const marker of navigationRiskMarkers) {
  if (source.includes(marker)) failures.push(`ranking preview users must not be used as real sensitive navigation ids: ${marker}`)
}

if (!source.includes('榜单预览用户不能作为真实私信对象，未进入会话')) {
  failures.push('ranking chat action must fail closed instead of routing preview ids into IM')
}
if (!source.includes('榜单预览用户不能作为真实主页对象，未打开主页')) {
  failures.push('ranking profile action must fail closed instead of routing preview ids into public profile')
}

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('ranking page avoids static trust/identity/transaction data')
