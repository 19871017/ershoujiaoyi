const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const failures = []

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

function requireMarker(file, source, marker) {
  if (!source.includes(marker)) {
    failures.push(`${file}: missing marker: ${marker}`)
  }
}

const communityFeedFile = 'src/pages/tabbar/message/index.vue'
const communityDetailFile = 'src/pages/community/detail/index.vue'
const communityManageFile = 'src/pages/community/manage/index.vue'
const meDataFile = 'src/pages/tabbar/me/me-data.ts'
const communityApiFile = 'src/api/modules/community.ts'

const communityFeed = read(communityFeedFile)
const communityDetail = read(communityDetailFile)
const communityManage = read(communityManageFile)
const meData = read(meDataFile)
const communityApi = read(communityApiFile)

for (const marker of [
  'listMyCommunityPosts',
  'updateCommunityPost',
  'deleteCommunityPost',
  "'/api/community/posts/mine'",
  '`/api/community/posts/${normalizeCommunityPostId(postId)}`'
]) {
  requireMarker(communityApiFile, communityApi, marker)
}

for (const marker of [
  'getMyProfile',
  'deleteCommunityPost',
  'const currentUserId = ref<number | null>(null)',
  'const canManageFeedPost = (item: CommunityPostResponse) =>',
  'v-if="canManageFeedPost(item)" class="feed-action owner tapable"',
  'v-if="canManageFeedPost(item)" class="feed-action danger tapable"',
  'v-if="!canManageFeedPost(item)" class="feed-action tapable" @click="chatPostAuthor(item)">私信</view>',
  'v-if="!canManageFeedPost(item)" class="feed-action report tapable" @click="reportFeedPost(item)">举报</view>',
  '@click.stop="editFeedPost(item)"',
  '@click.stop="deleteFeedPost(item)"',
  'async function loadCurrentCommunityUser',
  'function editFeedPost(item: CommunityPostResponse)',
  'async function deleteFeedPost(item: CommunityPostResponse)',
  'await deleteCommunityPost(item.postId)',
  "feeds.value = feeds.value.filter((row) => row.postId !== item.postId)",
  "url: `/pages/community/compose/index?mode=edit&postId=${encodeURIComponent(String(item.postId))}`"
]) {
  requireMarker(communityFeedFile, communityFeed, marker)
}

for (const marker of [
  'getMyProfile',
  'deleteCommunityPost',
  'const currentUserId = ref<number | null>(null)',
  'const deleteSubmitting = ref(false)',
  'const canManagePost = computed(() =>',
  'v-if="canManagePost" class="tapable owner" @click="editCurrentPost">编辑</view>',
  'v-if="canManagePost" class="tapable danger" :class="{ disabled: deleteSubmitting }" @click="deleteCurrentPost"',
  'v-if="!canManagePost" class="tapable" @click="messageAuthor">私信</view>',
  'v-if="!canManagePost" class="tapable" @click="reportPost">举报</view>',
  '@click="editCurrentPost"',
  '@click="deleteCurrentPost"',
  'async function loadCurrentUserForManagement',
  'function editCurrentPost()',
  'async function deleteCurrentPost()',
  'await deleteCommunityPost(numericPostId)',
  "url: `/pages/community/compose/index?mode=edit&postId=${encodeURIComponent(String(numericPostId))}`",
  "url: '/pages/community/manage/index'"
]) {
  requireMarker(communityDetailFile, communityDetail, marker)
}

for (const marker of [
  'listMyCommunityPosts',
  'deleteCommunityPost',
  'editPost(item)',
  'confirmDeletePost',
  "url: `/pages/community/compose/index?mode=edit&postId=${encodeURIComponent(String(item.postId))}`"
]) {
  requireMarker(communityManageFile, communityManage, marker)
}

requireMarker(meDataFile, meData, "label: '我的帖子'")
requireMarker(meDataFile, meData, "url: '/pages/community/manage/index'")

if (communityFeed.includes('class="feed-owner-actions"')) {
  failures.push(`${communityFeedFile}: own-post edit/delete actions should live in feed-actions beside the report position, not in a separate owner row`)
}
if (communityFeed.includes('私信作者')) {
  failures.push(`${communityFeedFile}: feed copy should use 私信, not 私信作者`)
}
if (!/v-if="!canManageFeedPost\(item\)" class="feed-action report tapable" @click="reportFeedPost\(item\)">举报<\/view>/.test(communityFeed)) {
  failures.push(`${communityFeedFile}: report action must be hidden for own posts`)
}
if (!/v-if="!canManageFeedPost\(item\)" class="feed-action tapable" @click="chatPostAuthor\(item\)">私信<\/view>/.test(communityFeed)) {
  failures.push(`${communityFeedFile}: private-message action must be hidden for own posts and labeled 私信`)
}

if (communityDetail.includes('私信作者')) {
  failures.push(`${communityDetailFile}: detail copy should use 私信, not 私信作者`)
}
if (communityDetail.includes('class="owner-action-row"')) {
  failures.push(`${communityDetailFile}: own-post edit/delete actions should live in the main action row, not a separate owner row`)
}
if (!/v-if="!canManagePost" class="tapable" @click="messageAuthor">私信<\/view>/.test(communityDetail)) {
  failures.push(`${communityDetailFile}: detail private-message action must be hidden for own posts and labeled 私信`)
}
if (!/v-if="!canManagePost" class="tapable" @click="reportPost">举报<\/view>/.test(communityDetail)) {
  failures.push(`${communityDetailFile}: detail report action must be hidden for own posts`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community user post management check passed')
