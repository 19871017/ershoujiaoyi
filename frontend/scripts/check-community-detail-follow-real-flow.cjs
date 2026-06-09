const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/community/detail/index.vue'
const source = [
  fs.readFileSync(path.join(root, 'src/pages/community/detail/community-detail-helpers.ts'), 'utf8'),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')
const failures = []

const requiredMarkers = [
  "followPublicProfile",
  "unfollowPublicProfile",
  "getPublicProfile",
  "const authorId = ref<number | null>(null)",
  "const authorFollowed = ref(false)",
  "const authorFollowLoaded = ref(false)",
  "const authorFollowError = ref('')",
  "const authorFollowSubmitting = ref(false)",
  "const authorFollowButtonText = computed(() =>",
  "const canMessageAuthor = computed(() => isValidBackendUserId(authorId.value))",
  "authorId.value = detail.authorId || null",
  "await getPublicProfile(safeAuthorId)",
  "async function toggleAuthorFollow()",
  "if (authorFollowSubmitting.value) return",
  "if (authorFollowError.value || !authorFollowLoaded.value)",
  "authorFollowError.value = '作者关注状态暂时不可用，请稍后刷新'",
  "await unfollowPublicProfile(safeAuthorId)",
  "await followPublicProfile(safeAuthorId)",
  "authorFollowed.value = Boolean(profile.followedByMe)",
  "authorFollowed.value = Boolean(updated.followedByMe)",
  "authorFollowSubmitting.value = true",
  "authorFollowSubmitting.value = false",
  "缺少后端作者ID，未执行任何关注变更",
  "关注状态没有提交成功，未执行本地关注变更",
  "function messageAuthor()",
  "作者资料暂时不可用，未进入私信",
  "`/pages/chat/conversation/index?receiverId=${encodeURIComponent(String(authorId.value))}`",
  '<view class="message-author tapable" :class="{ disabled: !canMessageAuthor }" @click="messageAuthor">私信</view>',
  '<view class="tapable" @click="messageAuthor">私信作者</view>'
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
