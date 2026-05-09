const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/community/detail/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const requiredMarkers = [
  "followPublicProfile",
  "unfollowPublicProfile",
  "getPublicProfile",
  "const authorId = ref<number | null>(null)",
  "const authorFollowed = ref(false)",
  "authorId.value = detail.authorId || null",
  "await getPublicProfile(safeAuthorId)",
  "async function toggleAuthorFollow()",
  "await unfollowPublicProfile(safeAuthorId)",
  "await followPublicProfile(safeAuthorId)",
  "authorFollowed.value = Boolean(profile.followedByMe)",
  "authorFollowed.value = Boolean(updated.followedByMe)",
  "缺少后端作者ID，未执行任何关注变更",
  "关注状态没有提交成功，未执行本地关注变更"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived author follow marker: ${marker}`)
}

const forbiddenMarkers = [
  "showFollowUnavailable",
  "关注接口暂未接通后端",
  "@click=\"showFollowUnavailable\""
]
for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden fail-closed follow stub remains: ${marker}`)
}

const toggleMatch = source.match(/async function toggleAuthorFollow\([\s\S]*?\n}/)
if (toggleMatch) {
  const body = toggleMatch[0]
  const assignIndex = body.indexOf('authorFollowed.value = Boolean(updated.followedByMe)')
  const followIndex = body.indexOf('await followPublicProfile')
  const unfollowIndex = body.indexOf('await unfollowPublicProfile')
  const firstAwait = [followIndex, unfollowIndex].filter((idx) => idx !== -1).sort((a, b) => a - b)[0]
  if (assignIndex !== -1 && firstAwait !== undefined && assignIndex < firstAwait) {
    failures.push(`${file}: authorFollowed must not mutate before backend follow/unfollow acknowledgement`)
  }
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community detail author follow uses backend profile/follow APIs and fails closed on missing author id')
