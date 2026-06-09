<template>
  <view class="page-shell community-page">
    <view class="community-top ds-card">
      <image class="community-top-art" :src="communityHeroBanner" mode="aspectFill" />
      <view class="community-top-mask"></view>
      <view class="community-top-content">
        <view class="community-top-head">
          <view class="community-title-chip">社区</view>
          <view class="community-actions">
            <view class="community-private tapable" @click="goSessions">
              <text class="community-notice-icon">✉</text>
              <text>私信</text>
              <text v-if="totalUnread > 0" class="unread-dot">{{ displayUnread }}</text>
            </view>
            <view class="community-notice tapable" @click="openNotification">
              <text class="community-notice-icon">🔔</text>
              <text>通知</text>
            </view>
          </view>
        </view>
        <view v-if="privateSummaryText" class="private-summary tapable" @click="goSessions">
          <text class="private-summary-icon">私信</text>
          <text class="private-summary-copy">{{ privateSummaryText }}</text>
        </view>
        <view class="topic-grid">
          <view v-for="item in topics" :key="item.title" class="topic-card tapable" :class="{ active: activeTopic === item.title }" @click="selectTopic(item.title)">
            <text class="topic-icon">{{ item.icon }}</text>
            <view class="topic-title">{{ item.title }}</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">{{ loadError }}</view>
    <view v-else-if="loading" class="empty-card ds-card">加载中…</view>

    <view v-for="item in feeds" :key="item.postId" class="feed-card ds-card">
      <view class="feed-head">
        <view class="feed-author tapable" @click.stop="openAuthorProfile(item)">
          <view class="avatar pink" :class="{ image: !!feedAvatarUrl(item) }">
            <image v-if="feedAvatarUrl(item)" class="avatar-img" :src="feedAvatarUrl(item)" mode="aspectFill" />
            <text v-else>{{ feedAvatarText(item) }}</text>
          </view>
          <view class="feed-user">
            <view class="name">{{ feedAuthorName(item) }}</view>
            <view class="time">{{ feedMeta(item) }}</view>
          </view>
        </view>
        <view class="follow tapable" :class="{ followed: followedAuthorIds.has(item.authorId), disabled: followingAuthorId === item.authorId || followedAuthorIds.has(item.authorId) || !isValidBackendUserId(item.authorId) }" @click.stop="followFeedAuthor(item)">
          {{ feedFollowText(item) }}
        </view>
      </view>
      <view class="feed-content tapable" @click="openPost(item)">
        <view class="feed-title">{{ item.title }}</view>
        <view class="feed-text">{{ item.content }}</view>
      </view>
      <view v-if="feedVisibleImageUrls(item).length" class="feed-image-grid tapable" :class="{ single: feedVisibleImageUrls(item).length === 1 }">
        <view v-for="(url, index) in feedVisibleImageUrls(item)" :key="`${item.postId}-${url}`" class="feed-image-cell">
          <image class="feed-image" :src="url" mode="aspectFill" @click.stop="previewFeedImages(item, index)" />
          <view v-if="index === 2 && feedExtraImageCount(item) > 0" class="feed-image-more">+{{ feedExtraImageCount(item) }}</view>
        </view>
      </view>
      <view v-if="item.relatedProductId && item.relatedProductTitle" class="feed-product tapable" @click.stop="openRelatedProduct(item)">
        <view class="feed-product-icon">物</view>
        <view class="feed-product-main">
          <view class="feed-product-title">{{ item.relatedProductTitle }}</view>
          <view class="feed-product-price">{{ relatedProductPriceText(item) }}</view>
        </view>
        <view class="feed-product-go">查看</view>
      </view>
      <view class="feed-actions" @click.stop>
        <view class="feed-action tapable" @click="toggleLikeFeed(item)">{{ item.likedByMe ? '♥' : '♡' }} {{ item.likeCount }}</view>
        <view class="feed-action tapable" @click="openPost(item)">💬 {{ item.commentCount }}</view>
        <view class="feed-action tapable" @click="chatPostAuthor(item)">私信</view>
        <view class="feed-action report tapable" @click="reportFeedPost(item)">举报</view>
      </view>
    </view>

    <view v-if="!loading && !loadError && feeds.length === 0" class="empty-card ds-card community-empty">
      <view class="empty-title">{{ emptyTopicTitle }}</view>
      <view class="empty-copy">还没有社区动态，可以先发一条真实经验，或切换其他话题看看。</view>
      <view class="empty-actions">
        <view class="empty-action primary tapable" @click="openComposer">发布动态</view>
        <view class="empty-action tapable" @click="selectNextTopic">换个话题</view>
      </view>
    </view>

    <view v-if="!communitySwitcherOpen" class="compose-fab tapable" @click="openComposer">＋</view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onHide, onShow, onUnload } from '@dcloudio/uni-app'
import { resolveBackendMediaUrl } from '../../../api/http'
import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../../../api/modules/chat'
import { COMMUNITY_TOPICS, isCommunityTopic, likeCommunityPost, listCommunityPosts, unlikeCommunityPost, type CommunityPostResponse, type CommunityTopic } from '../../../api/modules/community'
import { followPublicProfile } from '../../../api/modules/user'
import communityHeroBanner from '../../../assets/community/community-hero-banner.png'

const communitySwitcherEventName = 'xiaoyuanquan:community-switcher'
const topicIcons: Record<CommunityTopic, string> = {
  生活日常: '🌷',
  闲置避坑: '🛡️',
  交易经验: '💬',
  求购心愿: '🎯'
}
const activeTopic = ref<CommunityTopic>(COMMUNITY_TOPICS[0])
const topics = COMMUNITY_TOPICS.map((title) => ({ icon: topicIcons[title], title }))
const feeds = ref<CommunityPostResponse[]>([])
const conversations = ref<ChatConversationItem[]>([])
const loading = ref(false)
const loadError = ref('')
const followingAuthorId = ref<number | null>(null)
const followedAuthorIds = ref<Set<number>>(new Set())
const communitySwitcherOpen = ref(false)
let privateRefreshTimer: ReturnType<typeof setInterval> | null = null
const communityImageStoragePrefix = '/uploads/community-image/'
const avatarImageStoragePrefix = '/uploads/avatar/'
type NavigateToWithFailure = (options: { url: string; fail?: (error: unknown) => void }) => void
const navigateToWithFailure = uni.navigateTo as unknown as NavigateToWithFailure
const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))
const displayUnread = computed(() => totalUnread.value > 99 ? '99+' : String(totalUnread.value))
const latestUnreadConversation = computed(() => conversations.value.find((item) => item.unreadCount > 0))
const privateSummaryText = computed(() => {
  const unread = totalUnread.value
  if (unread <= 0) return ''
  const latest = latestUnreadConversation.value
  const peer = latest ? peerName(latest) : '私信'
  return `${peer} 有新消息 · ${unread} 未读`
})
const emptyTopicTitle = computed(() => `${activeTopic.value}还没有新动态`)

async function loadFeeds() {
  loading.value = true
  loadError.value = ''
  try {
    const rows = await listCommunityPosts(20, activeTopic.value)
    assertCommunityPostList(rows)
    feeds.value = validCommunityPostList(rows)
    syncFollowedAuthorIds(feeds.value)
  } catch (error) {
    console.warn('community feed load failed', { topic: activeTopic.value, error })
    feeds.value = []
    loadError.value = '社区内容暂时不可用，请稍后再来看看'
  } finally {
    loading.value = false
  }
}
function syncFollowedAuthorIds(rows: CommunityPostResponse[]) {
  const next = new Set<number>()
  for (const item of rows) {
    if (item.followedByMe === true && isValidBackendUserId(item.authorId)) {
      next.add(item.authorId)
    }
  }
  followedAuthorIds.value = next
}

function goSessions() { uni.navigateTo({ url: '/pages/chat/session-list/index' }) }
async function loadPrivateSummary(preserveOnError = true) {
  try {
    const response = await getChatConversations()
    assertConversationListResponse(response)
    conversations.value = validPrivateSummaryConversations(response.conversations)
  } catch (error) {
    console.warn('community private summary load failed', { error })
    if (!preserveOnError) conversations.value = []
  }
}

function startPrivateSummaryRefresh() {
  if (privateRefreshTimer) return
  privateRefreshTimer = setInterval(() => { void loadPrivateSummary(true) }, 5000)
}

function stopPrivateSummaryRefresh() {
  if (!privateRefreshTimer) return
  clearInterval(privateRefreshTimer)
  privateRefreshTimer = null
}

function syncCommunitySwitcherState(event: Event): void {
  const detail = (event as CustomEvent<{ open?: unknown }>).detail
  communitySwitcherOpen.value = detail?.open === true
}

function openNotification() { uni.navigateTo({ url: '/pages/notification/index' }) }
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function openComposer() { uni.navigateTo({ url: '/pages/community/compose/index' }) }
function selectTopic(title: string) {
  if (!isCommunityTopic(title)) {
    showToast('请选择有效社区话题')
    return
  }
  if (activeTopic.value === title) return
  activeTopic.value = title
  feeds.value = []
  void loadFeeds()
}
function isValidCommunityPostId(value: number | string | null | undefined) { return /^[1-9]\d{0,18}$/.test(String(value || '')) }
function isSafeBackendId(value: unknown): value is number {
  return Number.isSafeInteger(value) && Number(value) > 0
}
function assertCommunityPostList(value: unknown): asserts value is CommunityPostResponse[] {
  if (!Array.isArray(value)) throw new Error('community feed invalid list response')
}
function validCommunityPostList(value: unknown[]): CommunityPostResponse[] {
  const seenPostNos = new Set<string>()
  const validItems: CommunityPostResponse[] = []
  for (const item of value) {
    try {
      assertCommunityPostItem(item, seenPostNos)
      validItems.push(item)
    } catch (error) {
      console.warn('community feed invalid post isolated', { topic: activeTopic.value, error })
    }
  }
  return validItems
}
function assertCommunityPostItem(value: unknown, seenPostNos: Set<string>): asserts value is CommunityPostResponse {
  if (!value || typeof value !== 'object') throw new Error('community feed invalid post response')
  const item = value as CommunityPostResponse
  if (!isSafeBackendId(item.postId)) throw new Error('community feed invalid postId')
  if (typeof item.postNo !== 'string' || !/^POST-[A-Za-z0-9-]{1,80}$/.test(item.postNo) || seenPostNos.has(item.postNo)) throw new Error('community feed invalid postNo')
  seenPostNos.add(item.postNo)
  if (!isSafeBackendId(item.authorId)) throw new Error('community feed invalid authorId')
  if (typeof item.title !== 'string' || !item.title.trim()) throw new Error('community feed invalid title')
  if (typeof item.content !== 'string' || !item.content.trim()) throw new Error('community feed invalid content')
  if (!isCommunityTopic(item.topic)) throw new Error('community feed invalid topic')
  if (item.status !== 'PUBLISHED') throw new Error('community feed invalid status')
  if (!Number.isSafeInteger(item.likeCount) || item.likeCount < 0) throw new Error('community feed invalid likeCount')
  if (!Number.isSafeInteger(item.commentCount) || item.commentCount < 0) throw new Error('community feed invalid commentCount')
  if (typeof item.likedByMe !== 'boolean' || typeof item.followedByMe !== 'boolean') throw new Error('community feed invalid interaction state')
  if (typeof item.createdAt !== 'string' || !item.createdAt.trim()) throw new Error('community feed invalid createdAt')
  item.imageUrls = sanitizeCommunityImageUrls(item.imageUrls, item.postId)
  if (item.authorName != null && typeof item.authorName !== 'string') throw new Error('community feed invalid authorName')
  if (item.authorAvatar != null && typeof item.authorAvatar !== 'string') throw new Error('community feed invalid authorAvatar')
  if (item.city != null && typeof item.city !== 'string') throw new Error('community feed invalid city')
  if (item.relatedProductId != null && !isSafeBackendId(item.relatedProductId)) throw new Error('community feed invalid relatedProductId')
  if (item.relatedProductTitle != null && typeof item.relatedProductTitle !== 'string') throw new Error('community feed invalid relatedProductTitle')
  if (item.relatedProductPrice != null && typeof item.relatedProductPrice !== 'string' && typeof item.relatedProductPrice !== 'number') throw new Error('community feed invalid relatedProductPrice')
}
function openPost(item: CommunityPostResponse) {
  if (!isValidCommunityPostId(item.postId)) {
    showToast('这条动态暂时无法打开，请稍后重试')
    return
  }
  uni.navigateTo({ url: `/pages/community/detail/index?postId=${item.postId}&topic=${encodeURIComponent(item.topic)}` })
}
function chatPostAuthor(item: CommunityPostResponse) {
  if (!isValidBackendUserId(item.authorId)) {
    showToast('作者资料暂时不可用，未进入私信')
    return
  }
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${encodeURIComponent(String(item.authorId))}` })
}
function reportFeedPost(item: CommunityPostResponse) {
  if (!isValidCommunityPostId(item.postId)) {
    showToast('缺少有效动态编号，不能提交举报')
    return
  }
  navigateToReport(`/pages/report/submit/index?targetType=COMMUNITY_POST&targetId=${encodeURIComponent(String(item.postId))}`)
}
function navigateToReport(url: string) {
  try {
    navigateToWithFailure({
      url,
      fail: (error: unknown) => {
        console.warn('community feed report navigation failed', { url, error })
        showToast('暂时无法打开举报页，请稍后重试')
      }
    })
  } catch (error) {
    console.warn('community feed report navigation failed', { url, error })
    showToast('暂时无法打开举报页，请稍后重试')
  }
}
function openAuthorProfile(item: CommunityPostResponse) {
  if (!isValidBackendUserId(item.authorId)) {
    showToast('作者资料暂时不可用，未打开主页')
    return
  }
  uni.navigateTo({ url: `/pages/user/public-profile/index?userId=${encodeURIComponent(String(item.authorId))}` })
}
async function followFeedAuthor(item: CommunityPostResponse) {
  if (!isValidBackendUserId(item.authorId)) {
    showToast('作者资料暂时不可用，未完成关注')
    return
  }
  if (followedAuthorIds.value.has(item.authorId)) return
  if (followingAuthorId.value) return
  followingAuthorId.value = item.authorId
  try {
    const updated = await followPublicProfile(item.authorId)
    if (!updated.followedByMe) throw new Error('community feed follow response not followed')
    followedAuthorIds.value = new Set([...followedAuthorIds.value, item.authorId])
    showToast('已关注')
  } catch (error) {
    console.warn('community feed follow failed', { authorId: item.authorId, postId: item.postId, error })
    showToast('关注没有提交成功，请稍后重试')
  } finally {
    followingAuthorId.value = null
  }
}
function feedFollowText(item: CommunityPostResponse): string {
  if (!isValidBackendUserId(item.authorId)) return '不可关注'
  if (followedAuthorIds.value.has(item.authorId)) return '已关注'
  return followingAuthorId.value === item.authorId ? '处理中' : '关注'
}
function selectNextTopic(): void {
  const currentIndex = COMMUNITY_TOPICS.indexOf(activeTopic.value)
  const nextTopic = COMMUNITY_TOPICS[(currentIndex + 1) % COMMUNITY_TOPICS.length]
  selectTopic(nextTopic)
}
async function toggleLikeFeed(item: CommunityPostResponse) {
  if (!isValidCommunityPostId(item.postId)) {
    showToast('这条动态暂时无法打开，请稍后重试')
    return
  }
  const wasLiked = item.likedByMe
  try {
    const saved = wasLiked ? await unlikeCommunityPost(item.postId) : await likeCommunityPost(item.postId)
    assertCommunityPostItem(saved, new Set())
    if (saved.postId !== item.postId) throw new Error('community feed like response postId mismatch')
    item.likeCount = saved.likeCount
    item.likedByMe = saved.likedByMe
  } catch {
    showToast('点赞没有提交成功，请稍后重试')
  }
}
function feedAuthorName(item: CommunityPostResponse) { return item.authorName?.trim() || `用户 ${item.authorId}` }
function feedAvatarText(item: CommunityPostResponse) { return feedAuthorName(item).slice(0, 1) || '用' }
function feedAvatarUrl(item: CommunityPostResponse) { return resolveBackendMediaUrl(validatedCommunityAvatarUrl(item.authorAvatar || '')) }
function feedMeta(item: CommunityPostResponse) {
  return [formatTime(item.createdAt), item.topic, item.city].filter(Boolean).join(' · ')
}
function feedImageUrls(item: CommunityPostResponse): string[] {
  return (item.imageUrls || [])
    .map((url) => resolveBackendMediaUrl(validatedCommunityImageUrl(url)))
    .filter(Boolean)
}
function feedVisibleImageUrls(item: CommunityPostResponse): string[] {
  return feedImageUrls(item).slice(0, 3)
}
function feedExtraImageCount(item: CommunityPostResponse): number {
  return Math.max(0, feedImageUrls(item).length - 3)
}
function feedPreviewImageUrls(item: CommunityPostResponse): string[] {
  return feedImageUrls(item)
}
function previewFeedImages(item: CommunityPostResponse, index: number): void {
  const urls = feedPreviewImageUrls(item)
  const current = urls[index]
  if (!current) {
    showToast('图片暂时无法预览')
    return
  }
  try {
    uni.previewImage({ current, urls })
  } catch (error) {
    console.warn('community feed image preview failed', { postId: item.postId, index, error })
    showToast('图片预览失败，请稍后重试')
  }
}
function relatedProductPriceText(item: CommunityPostResponse): string {
  const price = item.relatedProductPrice
  if (price === null || price === undefined || price === '') return '价格以商品详情为准'
  return `¥${price}`
}
function openRelatedProduct(item: CommunityPostResponse): void {
  if (!isValidCommunityPostId(item.relatedProductId)) {
    showToast('关联商品暂时不可用')
    return
  }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${encodeURIComponent(String(item.relatedProductId))}` })
}
function formatTime(value: string) { return value ? value.slice(0, 16).replace('T', ' ') : '刚刚' }
function peerName(item: ChatConversationItem) { return item.peerNickname || `用户 ${item.peerUserId}` }
function isValidBackendUserId(value: number | string | null | undefined) { return /^[1-9]\d{0,18}$/.test(String(value || '')) }
function validatedCommunityImageUrl(url: unknown): string {
  return validatedStoredImageUrl(url, [communityImageStoragePrefix])
}
function validatedCommunityAvatarUrl(url: unknown): string {
  return validatedStoredImageUrl(url, [avatarImageStoragePrefix, communityImageStoragePrefix])
}
function sanitizeCommunityImageUrls(imageUrls: unknown, postId: number): string[] {
  if (!Array.isArray(imageUrls)) throw new Error('community feed invalid imageUrls')
  const validUrls: string[] = []
  let isolatedCount = 0
  for (const url of imageUrls) {
    if (typeof url !== 'string') {
      isolatedCount += 1
      continue
    }
    const validUrl = validatedCommunityImageUrl(url)
    if (validUrl) validUrls.push(validUrl)
    else isolatedCount += 1
  }
  if (isolatedCount > 0) {
    console.warn('community feed invalid image isolated', { postId, isolatedCount })
  }
  return validUrls
}
function validatedStoredImageUrl(url: unknown, storagePrefixes: string[]): string {
  if (typeof url !== 'string') return ''
  const storagePrefix = storagePrefixes.find((prefix) => url.startsWith(prefix))
  if (!storagePrefix) return ''
  const lower = url.toLowerCase()
  const relativePath = url.slice(storagePrefix.length)
  const hasInvalidPath = !relativePath ||
    url.startsWith('local://') ||
    url.startsWith('blob:') ||
    url.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    url.includes('\\') ||
    url.includes('..') ||
    url.includes('//') ||
    relativePath.split('/').some((segment) => !segment)
  return hasInvalidPath ? '' : url
}
function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse {
  if (!value || typeof value !== 'object') throw new Error('community private summary invalid response')
  const response = value as ChatConversationListResponse
  if (!Array.isArray(response.conversations)) throw new Error('community private summary invalid conversations')
}
function assertConversationItem(value: unknown): asserts value is ChatConversationItem {
  if (!value || typeof value !== 'object') throw new Error('community private summary invalid item')
  const item = value as ChatConversationItem
  if (!isValidBackendUserId(item.conversationId) || !isValidBackendUserId(item.peerUserId)) throw new Error('community private summary invalid ids')
  for (const field of ['lastServerSeq', 'deliveredSeq', 'readSeq', 'unreadCount'] as const) {
    if (!Number.isSafeInteger(item[field]) || item[field] < 0) throw new Error(`community private summary invalid ${field}`)
  }
  if (typeof item.updatedAt !== 'string') throw new Error('community private summary invalid updatedAt')
  if (item.peerNickname != null && typeof item.peerNickname !== 'string') throw new Error('community private summary invalid peerNickname')
  if (item.peerAvatarUrl != null && typeof item.peerAvatarUrl !== 'string') throw new Error('community private summary invalid peerAvatarUrl')
}
function validPrivateSummaryConversations(items: unknown[]): ChatConversationItem[] {
  const validItems: ChatConversationItem[] = []
  for (const item of items) {
    try {
      assertConversationItem(item)
      validItems.push(item)
    } catch (error) {
      console.warn('community private summary invalid conversation isolated', {
        conversationId: typeof item === 'object' && item !== null ? (item as { conversationId?: unknown }).conversationId : undefined,
        peerUserId: typeof item === 'object' && item !== null ? (item as { peerUserId?: unknown }).peerUserId : undefined,
        error
      })
    }
  }
  return validItems
}

onMounted(() => {
  void loadFeeds()
  void loadPrivateSummary(false)
  startPrivateSummaryRefresh()
  if (typeof window !== 'undefined') {
    window.addEventListener(communitySwitcherEventName, syncCommunitySwitcherState)
  }
})
onShow(() => {
  void loadFeeds()
  void loadPrivateSummary(true)
  startPrivateSummaryRefresh()
})
onHide(() => {
  communitySwitcherOpen.value = false
  stopPrivateSummaryRefresh()
})
onUnload(() => {
  communitySwitcherOpen.value = false
  stopPrivateSummaryRefresh()
  if (typeof window !== 'undefined') {
    window.removeEventListener(communitySwitcherEventName, syncCommunitySwitcherState)
  }
})
</script>

<style scoped>
.community-page {
  position: relative;
  min-height: 100vh;
  padding-top: 18rpx;
  padding-bottom: calc(230rpx + env(safe-area-inset-bottom));
  background:
    radial-gradient(circle at 14% 0%, rgba(255, 202, 150, .28), transparent 28%),
    radial-gradient(circle at 86% 18%, rgba(255, 226, 214, .42), transparent 24%),
    linear-gradient(180deg, #fff8f0 0%, #fffdfa 52%, #fff5ee 100%);
}

.community-top,
.feed-card,
.empty-card {
  border-color: rgba(255, 217, 189, .78);
  box-shadow: 0 16rpx 32rpx rgba(132, 70, 36, .085);
}

.community-top {
  position: relative;
  min-height: 274rpx;
  padding: 18rpx;
  overflow: hidden;
  background: linear-gradient(135deg, #fff8f0 0%, #fff1e3 100%);
}

.community-top-art,
.community-top-mask {
  position: absolute;
  inset: 0;
}

.community-top-art {
  width: 100%;
  height: 100%;
}

.community-top-mask {
  background: linear-gradient(115deg, rgba(255, 250, 244, .97) 0%, rgba(255, 241, 227, .82) 44%, rgba(255, 194, 163, .16) 100%);
}

.community-top-content {
  position: relative;
  z-index: 2;
  min-height: 238rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.community-top-head,
.feed-head,
.community-notice,
.community-private,
.topic-card,
.avatar,
.compose-fab {
  display: flex;
  align-items: center;
}

.community-top-head {
  justify-content: space-between;
  gap: 10rpx;
  flex-wrap: wrap;
}

.community-actions {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex: 0 0 auto;
  max-width: 100%;
}

.community-title-chip {
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .88);
  color: #4b2d20;
  font-size: 24rpx;
  font-weight: 950;
  letter-spacing: 1.6rpx;
  box-shadow: 0 10rpx 22rpx rgba(132, 70, 36, .09);
  border: 1rpx solid rgba(255, 217, 189, .54);
}

.community-notice {
  gap: 6rpx;
  min-width: 96rpx;
  min-height: 48rpx;
  padding: 0 14rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  font-size: 19rpx;
  font-weight: 920;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .20);
  box-sizing: border-box;
  justify-content: center;
  white-space: nowrap;
}

.community-private {
  position: relative;
  gap: 6rpx;
  min-width: 96rpx;
  min-height: 48rpx;
  padding: 0 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .9);
  color: #4b2d20;
  font-size: 19rpx;
  font-weight: 920;
  border: 1rpx solid rgba(255, 217, 189, .62);
  box-shadow: 0 10rpx 22rpx rgba(132, 70, 36, .09);
  box-sizing: border-box;
  justify-content: center;
  white-space: nowrap;
}

.unread-dot {
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 7rpx;
  border-radius: 999rpx;
  background: #ff3f8d;
  color: #fff;
  font-size: 17rpx;
  line-height: 28rpx;
  text-align: center;
  font-weight: 950;
}

.community-notice-icon {
  font-size: 22rpx;
}

.private-summary {
  margin: 10rpx 0 12rpx;
  padding: 10rpx 13rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, .82);
  color: #4b2d20;
  font-size: 20rpx;
  font-weight: 900;
  border: 1rpx solid rgba(255, 217, 189, .58);
  box-shadow: 0 10rpx 22rpx rgba(132, 70, 36, .075);
}

.private-summary-icon {
  padding: 5rpx 10rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  font-size: 17rpx;
  font-weight: 950;
}

.private-summary-copy {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.time {
  color: #8f6b57;
  font-size: 21rpx;
  line-height: 1.35;
  font-weight: 650;
}

.topic-grid {
  display: flex;
  gap: 10rpx;
  overflow-x: auto;
  padding-bottom: 2rpx;
  scrollbar-width: none;
}

.topic-grid::-webkit-scrollbar {
  display: none;
}

.topic-card {
  flex: 0 0 auto;
  min-width: 146rpx;
  min-height: 68rpx;
  padding: 0 16rpx;
  flex-direction: row;
  justify-content: center;
  gap: 8rpx;
  border: 1rpx solid rgba(255, 255, 255, .76);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .78);
  backdrop-filter: blur(10rpx);
  box-shadow: 0 10rpx 20rpx rgba(132, 70, 36, .075);
  box-sizing: border-box;
}

.topic-card.active {
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 238, 228, .98));
  border-color: rgba(239, 111, 63, .66);
  box-shadow: 0 16rpx 28rpx rgba(255, 122, 69, .14);
}

.topic-icon {
  font-size: 25rpx;
}

.topic-title {
  color: #342116;
  font-size: 20rpx;
  font-weight: 920;
  letter-spacing: .15rpx;
  white-space: nowrap;
}

.feed-card {
  margin-top: 14rpx;
  padding: 18rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(255, 248, 242, .97));
}

.feed-head {
  gap: 12rpx;
}

.feed-author {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.avatar {
  width: 66rpx;
  height: 66rpx;
  border-radius: 50%;
  color: #fffaf4;
  justify-content: center;
  font-size: 25rpx;
  font-weight: 950;
  box-shadow: 0 10rpx 20rpx rgba(255, 122, 69, .16);
  flex: 0 0 auto;
}

.avatar.pink {
  background: linear-gradient(135deg, #ef6f3f, #ffb08a);
}

.avatar.image {
  overflow: hidden;
  background: #fff3e7;
}

.avatar-img {
  width: 100%;
  height: 100%;
}

.feed-user {
  flex: 1;
  min-width: 0;
}

.name {
  color: #342116;
  font-size: 24rpx;
  font-weight: 950;
  line-height: 1.34;
  letter-spacing: .12rpx;
}

.feed-title {
  margin-top: 18rpx;
  color: #342116;
  font-size: 27rpx;
  font-weight: 950;
  line-height: 1.42;
  letter-spacing: .1rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.feed-content {
  padding: 2rpx 0 4rpx;
}

.follow {
  padding: 9rpx 17rpx;
  border-radius: 999rpx;
  background: rgba(255, 243, 231, .94);
  color: #df6735;
  font-size: 20rpx;
  font-weight: 920;
  border: 1rpx solid rgba(255, 195, 150, .52);
  flex: 0 0 auto;
}

.follow.disabled {
  opacity: .62;
}

.follow.followed {
  color: #7c5f4e;
  background: rgba(255, 255, 255, .86);
  border-color: rgba(255, 217, 189, .74);
}

.feed-text {
  margin-top: 17rpx;
  color: #3a261a;
  font-size: 23rpx;
  font-weight: 650;
  line-height: 1.62;
  letter-spacing: .08rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.feed-image-grid {
  margin-top: 16rpx;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8rpx;
}

.feed-image-grid.single {
  grid-template-columns: minmax(0, 1fr);
}

.feed-image-cell {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: 18rpx;
  overflow: hidden;
  background: #fff3e7;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, .72);
}

.feed-image-grid.single .feed-image-cell {
  aspect-ratio: 16 / 9;
}

.feed-image {
  width: 100%;
  height: 100%;
}

.feed-image-more {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fffaf4;
  font-size: 28rpx;
  font-weight: 950;
  background: rgba(58, 38, 26, .42);
}

.feed-product {
  margin-top: 14rpx;
  padding: 12rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  border-radius: 22rpx;
  background: linear-gradient(135deg, rgba(255, 246, 238, .96), rgba(255, 238, 228, .92));
  border: 1rpx solid rgba(255, 195, 150, .46);
}

.feed-product-icon {
  width: 42rpx;
  height: 42rpx;
  line-height: 42rpx;
  border-radius: 14rpx;
  text-align: center;
  color: #fffaf4;
  background: linear-gradient(135deg, #ef6f3f, #ff9f7a);
  font-size: 18rpx;
  font-weight: 950;
  flex: 0 0 auto;
}

.feed-product-main {
  flex: 1;
  min-width: 0;
}

.feed-product-title {
  color: #4b2d20;
  font-size: 21rpx;
  font-weight: 920;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feed-product-price,
.feed-product-go {
  color: #df6735;
  font-size: 19rpx;
  font-weight: 880;
}

.feed-product-price {
  margin-top: 3rpx;
}

.feed-product-go {
  flex: 0 0 auto;
}

.feed-actions {
  margin-top: 15rpx;
  display: flex;
  gap: 10rpx;
  justify-content: space-between;
  color: #8f6b57;
  font-size: 20rpx;
  font-weight: 850;
}

.feed-action {
  min-height: 50rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 243, 231, .78);
  color: #7b5542;
  box-sizing: border-box;
}

.feed-action.report {
  color: #9c5e43;
  background: rgba(255, 255, 255, .7);
  border: 1rpx solid rgba(255, 217, 189, .62);
}

.empty-card {
  margin-top: 18rpx;
  padding: 28rpx 24rpx;
  text-align: center;
  color: #8f6b57;
  background: rgba(255, 255, 255, .94);
  line-height: 1.5;
  font-size: 23rpx;
  font-weight: 650;
}

.community-empty {
  padding: 34rpx 24rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(255, 247, 240, .96));
}

.empty-title {
  color: #342116;
  font-size: 27rpx;
  font-weight: 950;
}

.empty-copy {
  max-width: 520rpx;
  margin: 10rpx auto 0;
  color: #8f6b57;
  font-size: 22rpx;
  font-weight: 720;
  line-height: 1.55;
}

.empty-actions {
  margin-top: 18rpx;
  display: flex;
  justify-content: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.empty-action {
  min-width: 150rpx;
  min-height: 58rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  background: rgba(255, 243, 231, .94);
  color: #7b5542;
  border: 1rpx solid rgba(255, 195, 150, .52);
  font-size: 21rpx;
  font-weight: 920;
}

.empty-action.primary {
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  border-color: rgba(239, 111, 63, .62);
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .14);
}

.compose-fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(128rpx + env(safe-area-inset-bottom));
  z-index: 30;
  width: 92rpx;
  height: 92rpx;
  border-radius: 50%;
  justify-content: center;
  background: linear-gradient(135deg, #3a261a, #ef6f3f);
  color: #fff8df;
  font-size: 54rpx;
  font-weight: 850;
  line-height: 1;
  box-shadow: 0 18rpx 38rpx rgba(58, 42, 31, .22);
  border: 3rpx solid rgba(255, 255, 255, .9);
}
</style>
