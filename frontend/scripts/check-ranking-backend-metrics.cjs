const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const sourcePath = path.join(root, 'src/pages/ranking/index.vue')
const source = fs.readFileSync(sourcePath, 'utf8')
const failures = []

if (!source.includes('item.giftScore ?? item.popularityScore')) {
  failures.push('ranking page must render gift score from backend giftScore with popularityScore compatibility fallback')
}
if (/guardian:\s*item\.|popularity:\s*item\.|safetyScore|guardianScore|followerCount/.test(source)) {
  failures.push('ranking page must not render old popularity/safety/guardian/follower metrics')
}
if (source.includes("{ value: 'deal' as const") || source.includes("{ value: 'guardian' as const")) {
  failures.push('ranking page must not keep safety or guardian leaderboard tabs')
}

if (!source.includes('followingIds.value = new Set([...followingIds.value, item.id])')) {
  failures.push('ranking follow action must keep duplicate-submit lock before backend request')
}
if (!source.includes('await followPublicProfile(item.id)') || !source.includes('await unfollowPublicProfile(item.id)')) {
  failures.push('ranking follow action must call backend follow/unfollow APIs instead of local no-op copy')
}
if (!source.includes('!!updated.followedByMe')) {
  failures.push('ranking follow state must refresh from backend returned followedByMe')
}
if (/未执行关注变更/.test(source)) {
  failures.push('ranking page must not keep fail-closed follow copy once backend follow/unfollow APIs are wired')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}
console.log('ranking backend metric guard passed')
