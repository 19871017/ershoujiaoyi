const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/message/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  'const feeds = reactive([',
  "name: '小原圈软糖'",
  "name: '平台审核'",
  "name: '同城约看小助手'",
  "name: '买家小陈'",
  '奶油白连衣裙搭浅色长袜真的很温柔',
  '3 条圈内互动',
  "likes: 128",
  "comments: 18",
  "followed: true"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static community feed marker found: ${marker}`)
}

const requiredMarkers = [
  "import { likeCommunityPost, listCommunityPosts, unlikeCommunityPost, type CommunityPostResponse } from '../../../api/modules/community'",
  "import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../../../api/modules/chat'",
  'const feeds = ref<CommunityPostResponse[]>([])',
  'const conversations = ref<ChatConversationItem[]>([])',
  'await listCommunityPosts(20)',
  'await getChatConversations()',
  'function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse',
  'function assertConversationItem(value: unknown): asserts value is ChatConversationItem',
  'const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))',
  'const privateSummaryText = computed(() => {',
  "console.warn('community private summary load failed'",
  'function startPrivateSummaryRefresh()',
  'setInterval(() => { void loadPrivateSummary(true) }, 5000)',
  '社区内容暂时不可用，未展示本地帖子样例',
  '点赞没有提交成功，未执行本地点赞变更',
  '关注接口暂未接通后端，未执行任何关注变更',
  'function isValidCommunityPostId(value: number | string | null | undefined)',
  'function isValidBackendUserId(value: number | string | null | undefined)',
  'function openPost(item: CommunityPostResponse)',
  'function chatPostAuthor(item: CommunityPostResponse)',
  '缺少真实作者ID，未进入私信',
  '`/pages/chat/conversation/index?receiverId=${encodeURIComponent(String(item.authorId))}`',
  '缺少有效动态编号，未打开动态详情',
  'postId=${item.postId}',
  'async function toggleLikeFeed(item: CommunityPostResponse)',
  'const wasLiked = item.likedByMe',
  'const saved = wasLiked ? await unlikeCommunityPost(item.postId) : await likeCommunityPost(item.postId)',
  'item.likeCount = saved.likeCount',
  'item.likedByMe = saved.likedByMe',
  "{{ item.likedByMe ? '♥' : '♡' }} {{ item.likeCount }}"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing real-data/fail-closed community feed marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community tab feed uses backend posts and avoids static interaction/trust samples')
