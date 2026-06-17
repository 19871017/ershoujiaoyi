const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/message/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const templateSource = source.match(/<template>([\s\S]*?)<\/template>/)?.[1] ?? ''

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
  "import { onHide, onReachBottom, onShow, onUnload } from '@dcloudio/uni-app'",
  "import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../../../api/modules/chat'",
  'const COMMUNITY_FEED_PAGE_SIZE = 20',
  'const activeTopic = ref<CommunityTopic>(COMMUNITY_TOPICS[0])',
  'const topics = COMMUNITY_TOPICS.map((title) => ({ icon: topicIcons[title], title }))',
  'const feeds = ref<CommunityPostResponse[]>([])',
  'const conversations = ref<ChatConversationItem[]>([])',
  'const loadingMore = ref(false)',
  'const followingAuthorId = ref<number | null>(null)',
  'const followedAuthorIds = ref<Set<number>>(new Set())',
  'const hasMoreFeeds = ref(false)',
  'const nextFeedCursor = ref<string | null>(null)',
  'let feedRequestSeq = 0',
  'const requestSeq = ++feedRequestSeq',
  'const requestedTopic = activeTopic.value',
  'await listCommunityPostPage({ limit: COMMUNITY_FEED_PAGE_SIZE, topic: requestedTopic })',
  'if (!isActiveFeedRequest(requestSeq, requestedTopic)) return',
  'function isActiveFeedRequest(requestSeq: number, requestedTopic: CommunityTopic): boolean',
  'async function loadMoreFeeds()',
  'await listCommunityPostPage({ limit: COMMUNITY_FEED_PAGE_SIZE, topic: requestedTopic, cursor })',
  'if (loading.value || loadingMore.value || !hasMoreFeeds.value) return',
  'appendValidCommunityPostList(page.posts)',
  "console.warn('community feed duplicate post isolated'",
  "console.warn('community feed load more failed'",
  'onReachBottom(() => {',
  'assertCommunityPostList(rows)',
  'function assertCommunityPostPageResponse(value: unknown): asserts value is CommunityPostPageResponse',
  "if (typeof page.hasMore !== 'boolean') throw new Error('community feed invalid hasMore')",
  'feeds.value = validCommunityPostList(rows)',
  'syncFollowedAuthorIds(feeds.value)',
  "console.warn('community feed load failed'",
  'function syncFollowedAuthorIds(rows: CommunityPostResponse[])',
  'const next = new Set<number>(followedAuthorIds.value)',
  'if (item.followedByMe === true && isValidBackendUserId(item.authorId))',
  'await getChatConversations()',
  'function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse',
  'function assertConversationItem(value: unknown): asserts value is ChatConversationItem',
  'function validPrivateSummaryConversations(items: unknown[]): ChatConversationItem[]',
  'validPrivateSummaryConversations(response.conversations)',
  "console.warn('community private summary invalid conversation isolated'",
  'const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))',
  'const privateSummaryText = computed(() => {',
  'class="private-summary-copy"',
  'const emptyTopicTitle = computed(() => `${activeTopic.value}还没有新动态`)',
  "console.warn('community private summary load failed'",
  'function startPrivateSummaryRefresh()',
  'setInterval(() => { void loadPrivateSummary(true) }, 5000)',
  '社区内容暂时不可用，请稍后再来看看',
  '点赞没有提交成功，请稍后重试',
  'async function followFeedAuthor(item: CommunityPostResponse)',
  'await followPublicProfile(item.authorId)',
  '已关注',
  '关注没有提交成功，请稍后重试',
  'const updated = await followPublicProfile(item.authorId)',
  "if (!updated.followedByMe) throw new Error('community feed follow response not followed')",
  'followedAuthorIds.value = new Set([...followedAuthorIds.value, item.authorId])',
  'function feedFollowText(item: CommunityPostResponse): string',
  "if (!isValidBackendUserId(item.authorId)) return '不可关注'",
  "if (followedAuthorIds.value.has(item.authorId)) return '已关注'",
  "{{ feedFollowText(item) }}",
  ':class="{ followed: followedAuthorIds.has(item.authorId), disabled: followingAuthorId === item.authorId || followedAuthorIds.has(item.authorId) || !isValidBackendUserId(item.authorId) }"',
  'function selectNextTopic(): void',
  'const nextTopic = COMMUNITY_TOPICS[(currentIndex + 1) % COMMUNITY_TOPICS.length]',
  'class="empty-card ds-card community-empty"',
  '{{ emptyTopicTitle }}',
  '还没有社区动态，可以先发一条真实经验，或切换其他话题看看。',
  'class="empty-action primary tapable" @click="openComposer"',
  'class="empty-action tapable" @click="selectNextTopic"',
  'function openAuthorProfile(item: CommunityPostResponse)',
  "`/pages/user/public-profile/index?userId=${encodeURIComponent(String(item.authorId))}`",
  '<view class="feed-author tapable" @click.stop="openAuthorProfile(item)">',
  'function isValidCommunityPostId(value: number | string | null | undefined)',
  'function appendValidCommunityPostList(value: unknown[]): CommunityPostResponse[]',
  'function assertCommunityPostList(value: unknown): asserts value is CommunityPostResponse[]',
  'function assertCommunityPostItem(value: unknown, seenPostNos: Set<string>): asserts value is CommunityPostResponse',
  'community feed invalid ip location',
  'IP属地',
  'item.ipLocation',
  "if (item.status !== 'PUBLISHED') throw new Error('community feed invalid status')",
  'item.imageUrls = sanitizeCommunityImageUrls(item.imageUrls, item.postId)',
  'function sanitizeCommunityImageUrls(imageUrls: unknown, postId: number): string[]',
  "console.warn('community feed invalid image isolated'",
  'function isValidBackendUserId(value: number | string | null | undefined)',
  'function validCommunityPostList(value: unknown[]): CommunityPostResponse[]',
  "console.warn('community feed invalid post isolated'",
  'function openPost(item: CommunityPostResponse)',
  'function chatPostAuthor(item: CommunityPostResponse)',
  'function reportFeedPost(item: CommunityPostResponse)',
  'type NavigateToWithFailure = (options: { url: string; fail?: (error: unknown) => void }) => void',
  'const navigateToWithFailure = uni.navigateTo as unknown as NavigateToWithFailure',
  'if (!isCommunityTopic(title))',
  '请选择有效社区话题',
  'function feedAuthorName(item: CommunityPostResponse)',
  'function feedAvatarUrl(item: CommunityPostResponse)',
  'function validatedCommunityImageUrl(url: unknown): string',
  'function validatedCommunityAvatarUrl(url: unknown): string',
  'function validatedStoredImageUrl(url: unknown, storagePrefixes: string[]): string',
  "const avatarImageStoragePrefix = '/uploads/avatar/'",
  "resolveBackendMediaUrl(validatedCommunityAvatarUrl(item.authorAvatar || ''))",
  'function feedImageUrls(item: CommunityPostResponse): string[]',
  'resolveBackendMediaUrl(validatedCommunityImageUrl(url))',
  'function feedVisibleImageUrls(item: CommunityPostResponse): string[]',
  'function feedPreviewImageUrls(item: CommunityPostResponse): string[]',
  'function previewFeedImages(item: CommunityPostResponse, index: number): void',
  'uni.previewImage({ current, urls })',
  "console.warn('community feed image preview failed'",
  'function feedExtraImageCount(item: CommunityPostResponse): number',
  'class="feed-image-grid tapable"',
  'class="feed-image-cell"',
  'function relatedProductPriceText(item: CommunityPostResponse): string',
  'function openRelatedProduct(item: CommunityPostResponse): void',
  'class="feed-product tapable"',
  'item.relatedProductId && item.relatedProductTitle',
  "`/pages/product/detail/index?productId=${encodeURIComponent(String(item.relatedProductId))}`",
  '关联商品暂时不可用',
  '{{ feedAuthorName(item) }}',
  '{{ feedMeta(item) }}',
  '<view class="feed-title">{{ item.title }}</view>',
  '作者资料暂时不可用，未进入私信',
  '缺少有效动态编号，不能提交举报',
  '暂时无法打开举报页，请稍后重试',
  '作者资料暂时不可用，未打开主页',
  '作者资料暂时不可用，未完成关注',
  '`/pages/chat/conversation/index?receiverId=${encodeURIComponent(String(item.authorId))}`',
  'targetType=COMMUNITY_POST&targetId=${encodeURIComponent(String(item.postId))}',
  "console.warn('community feed report navigation failed'",
  'class="feed-action report tapable" @click="reportFeedPost(item)"',
  '这条动态暂时无法打开，请稍后重试',
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

const communityImport = source.match(/import \{([\s\S]*?)\} from '..\/..\/..\/api\/modules\/community'/)?.[1] ?? ''
for (const marker of [
  'COMMUNITY_TOPICS',
  'isCommunityTopic',
  'likeCommunityPost',
  'listCommunityPostPage',
  'unlikeCommunityPost',
  'type CommunityPostPageResponse',
  'type CommunityPostResponse',
  'type CommunityTopic'
]) {
  if (!communityImport.includes(marker)) failures.push(`${file}: missing community API import member: ${marker}`)
}

const userImport = source.match(/import \{([\s\S]*?)\} from '..\/..\/..\/api\/modules\/user'/)?.[1] ?? ''
if (!userImport.includes('followPublicProfile')) {
  failures.push(`${file}: missing user API import member: followPublicProfile`)
}

if (source.includes('<view class="name">{{ item.title }}</view>')) {
  failures.push(`${file}: feed author name must use authorName, not post title`)
}

if (source.includes('const filteredFeeds = computed(() => feeds.value.filter')) {
  failures.push(`${file}: topic tabs must request backend-filtered posts instead of filtering only the latest local page`)
}

if (source.includes('await listCommunityPosts(20, activeTopic.value)')) {
  failures.push(`${file}: community tab must use the paginated backend feed endpoint, not fixed first-page listCommunityPosts`)
}

if (!source.includes('class="feed-card"') || !source.includes('class="feed-content tapable" @click="openPost(item)"') || !source.includes('<view class="feed-actions" @click.stop>') || !source.includes('class="feed-action tapable"')) {
  failures.push(`${file}: feed item must avoid whole-item action misclicks by scoping detail navigation and styling explicit action pills`)
}

if (!/function reportFeedPost\(item: CommunityPostResponse\)[\s\S]*if \(!isValidCommunityPostId\(item\.postId\)\)[\s\S]*targetType=COMMUNITY_POST&targetId=\$\{encodeURIComponent\(String\(item\.postId\)\)\}/s.test(source)) {
  failures.push(`${file}: feed report entry must validate backend post id and use COMMUNITY_POST target type before opening report page`)
}

if (!/function navigateToReport\(url: string\)[\s\S]*navigateToWithFailure\(\{[\s\S]*fail: \(error: unknown\)[\s\S]*console\.warn\('community feed report navigation failed'[\s\S]*catch \(error\)[\s\S]*暂时无法打开举报页，请稍后重试/s.test(source)) {
  failures.push(`${file}: feed report navigation must handle async and synchronous navigation failures`)
}

if (!source.includes('void loadFeeds()') || !/function selectTopic\(title: string\)[\s\S]*activeTopic\.value = title[\s\S]*void loadFeeds\(\)/s.test(source)) {
  failures.push(`${file}: changing community topic must reload backend-filtered posts`)
}

if (!/function selectTopic\(title: string\)[\s\S]*activeTopic\.value = title[\s\S]*feeds\.value = \[\][\s\S]*hasMoreFeeds\.value = false[\s\S]*nextFeedCursor\.value = null[\s\S]*void loadFeeds\(\)/s.test(source)) {
  failures.push(`${file}: changing community topic must clear old posts and reset pagination before loading the new backend-filtered topic`)
}

if (!/function selectTopic\(title: string\)[\s\S]*activeTopic\.value = title[\s\S]*feedRequestSeq \+= 1[\s\S]*loading\.value = false[\s\S]*loadingMore\.value = false[\s\S]*void loadFeeds\(\)/s.test(source)) {
  failures.push(`${file}: changing community topic must cancel stale feed requests and reset loading state before reloading`)
}

if (/function avatarOf\(item: CommunityPostResponse\)[\s\S]*item\.title/.test(source)) {
  failures.push(`${file}: feed avatar must use authorName/authorAvatar, not title`)
}

const followMatch = source.match(/async function followFeedAuthor\(item: CommunityPostResponse\)[\s\S]*?\n}/)
if (followMatch) {
  const body = followMatch[0]
  const ackIndex = body.indexOf('await followPublicProfile')
  const assignIndex = body.indexOf('followedAuthorIds.value = new Set')
  if (assignIndex !== -1 && ackIndex !== -1 && assignIndex < ackIndex) {
    failures.push(`${file}: feed follow must not display followed before backend acknowledgement`)
  }
}

if (!/onShow\(\(\) => \{[\s\S]*void loadFeeds\(\)[\s\S]*void loadPrivateSummary\(true\)/s.test(source)) {
  failures.push(`${file}: community tab must refresh backend feed onShow so newly published posts and counters are visible after returning to the tab`)
}

if (!/async function loadPrivateSummary\(preserveOnError = true\)[\s\S]*assertConversationListResponse\(response\)[\s\S]*conversations\.value = validPrivateSummaryConversations\(response\.conversations\)[\s\S]*if \(!preserveOnError\) conversations\.value = \[\]/s.test(source)) {
  failures.push(`${file}: community private summary must isolate malformed conversation rows and only clear the summary on foreground hard failures`)
}

if (!/async function loadFeeds\(\)[\s\S]*const requestSeq = \+\+feedRequestSeq[\s\S]*const requestedTopic = activeTopic\.value[\s\S]*listCommunityPostPage\(\{ limit: COMMUNITY_FEED_PAGE_SIZE, topic: requestedTopic \}\)[\s\S]*if \(!isActiveFeedRequest\(requestSeq, requestedTopic\)\) return[\s\S]*assertCommunityPostPageResponse\(page\)[\s\S]*assertCommunityPostList\(rows\)[\s\S]*feeds\.value = validCommunityPostList\(rows\)[\s\S]*hasMoreFeeds\.value = page\.hasMore[\s\S]*nextFeedCursor\.value = page\.nextCursor[\s\S]*syncFollowedAuthorIds\(feeds\.value\)[\s\S]*if \(feedRequestSeq === requestSeq\) loading\.value = false/s.test(source)) {
  failures.push(`${file}: community feed must ignore stale topic responses while preserving valid backend posts`)
}

if (!/function assertCommunityPostList\(value: unknown\): asserts value is CommunityPostResponse\[\]\s*\{[\s\S]*Array\.isArray\(value\)[\s\S]*\}\s*function validCommunityPostList\(value: unknown\[\]\): CommunityPostResponse\[\][\s\S]*assertCommunityPostItem\(item, seenPostNos\)[\s\S]*validItems\.push\(item\)[\s\S]*console\.warn\('community feed invalid post isolated'/s.test(source)) {
  failures.push(`${file}: community feed list validation must fail closed only for non-array responses and isolate malformed post rows`)
}

if (!/async function loadMoreFeeds\(\)[\s\S]*if \(loading\.value \|\| loadingMore\.value \|\| !hasMoreFeeds\.value\) return[\s\S]*const requestSeq = feedRequestSeq[\s\S]*const requestedTopic = activeTopic\.value[\s\S]*const cursor = nextFeedCursor\.value[\s\S]*listCommunityPostPage\(\{ limit: COMMUNITY_FEED_PAGE_SIZE, topic: requestedTopic, cursor \}\)[\s\S]*if \(!isActiveFeedRequest\(requestSeq, requestedTopic\)\) return[\s\S]*appendValidCommunityPostList\(page\.posts\)[\s\S]*feeds\.value = \[\.\.\.feeds\.value, \.\.\.nextRows\][\s\S]*loadMoreError\.value = '继续加载失败，点我重试'/s.test(source)) {
  failures.push(`${file}: community feed load more must use backend cursor, ignore stale responses, append valid rows, dedupe, and preserve existing posts on failure`)
}

if (!/function syncFollowedAuthorIds\(rows: CommunityPostResponse\[\]\)[\s\S]*const next = new Set<number>\(followedAuthorIds\.value\)[\s\S]*item\.followedByMe === true[\s\S]*followedAuthorIds\.value = next/s.test(source)) {
  failures.push(`${file}: community feed follow state must preserve locally confirmed follows across pagination refreshes`)
}

if (!/function appendValidCommunityPostList\(value: unknown\[\]\): CommunityPostResponse\[\][\s\S]*existingPostNos[\s\S]*existingPostIds[\s\S]*validCommunityPostList\(value\)[\s\S]*console\.warn\('community feed duplicate post isolated'/s.test(source)) {
  failures.push(`${file}: community feed pagination must dedupe appended posts by postNo and postId`)
}

if (!/function validPrivateSummaryConversations\(items: unknown\[\]\): ChatConversationItem\[\][\s\S]*assertConversationItem\(item\)[\s\S]*validItems\.push\(item\)[\s\S]*console\.warn\('community private summary invalid conversation isolated'/s.test(source)) {
  failures.push(`${file}: community private summary must preserve valid conversations when one backend row is malformed`)
}

if (!/\.private-summary-copy\s*\{[\s\S]*min-width:\s*0;[\s\S]*white-space:\s*nowrap;[\s\S]*text-overflow:\s*ellipsis;/s.test(source)) {
  failures.push(`${file}: private message summary must truncate long nicknames instead of overflowing`)
}

for (const marker of ['后端', '服务端', '本地', '样例', 'demo', 'mock']) {
  if (templateSource.includes(marker)) failures.push(`${file}: user-visible community feed copy must not expose technical/testing wording: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community tab feed uses backend posts and avoids static interaction/trust samples')
