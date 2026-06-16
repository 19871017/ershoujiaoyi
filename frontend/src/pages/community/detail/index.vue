<template>
  <view class="page-shell detail-page">
    <view v-if="errorText" class="empty-card ds-card">{{ errorText }}</view>
    <view v-else-if="detailLoading || !detailLoaded" class="empty-card loading-card ds-card">动态详情加载中</view>

    <block v-else-if="detailLoaded">
      <view class="post-card ds-card">
        <view class="post-head">
          <view class="post-author tapable" @click="openAuthorProfile">
            <view class="avatar" :class="{ image: !!authorAvatarUrl }">
              <image v-if="authorAvatarUrl" class="avatar-image" :src="authorAvatarUrl" mode="aspectFill" />
              <text v-else>{{ authorAvatar }}</text>
            </view>
            <view class="author">
              <view class="name">{{ authorName }}</view>
              <view class="meta">{{ topic }} · {{ createdText }} · {{ cityText }}</view>
            </view>
          </view>
          <view class="author-actions">
            <view v-if="!canManagePost" class="message-author tapable" :class="{ disabled: !canMessageAuthor }" @click="messageAuthor">私信</view>
            <view class="follow tapable" :class="{ disabled: !authorFollowLoaded || !!authorFollowError || authorFollowSubmitting }" @click="toggleAuthorFollow">{{ authorFollowButtonText }}</view>
          </view>
        </view>
        <view class="title">{{ postTitle || '动态详情加载中' }}</view>
        <view class="content">{{ postContent }}</view>
        <view v-if="imageSlots.length" class="image-grid">
          <image v-for="(item, index) in imageSlots" :key="item" class="post-image tapable" :src="item" mode="aspectFill" @click="previewPostImage(index)" />
        </view>
        <view class="action-row">
          <view class="tapable" @click="likePost">{{ liked ? '♥' : '♡' }} {{ likeCount }}</view>
          <view>💬 {{ serverCommentCount }}</view>
          <view v-if="!canManagePost" class="tapable" @click="messageAuthor">私信</view>
          <view v-if="canManagePost" class="tapable owner" @click="editCurrentPost">编辑</view>
          <view v-if="!canManagePost" class="tapable" @click="reportPost">举报</view>
          <view v-if="canManagePost" class="tapable danger" :class="{ disabled: deleteSubmitting }" @click="deleteCurrentPost">
            {{ deleteSubmitting ? '删除中' : '删除' }}
          </view>
        </view>
      </view>

      <view v-if="hasRelatedProduct" class="goods-card ds-card tapable" @click="openProduct">
        <view class="goods-icon">物</view>
        <view class="goods-main">
          <view class="goods-title">{{ productTitle }}</view>
          <view class="goods-desc">{{ productDesc }}</view>
        </view>
        <view class="goods-go">查看</view>
      </view>

      <view class="comment-card ds-card">
        <view class="section-title">评论互动</view>
        <view v-if="!comments.length" class="comment-empty">暂无平台评论</view>
        <view v-for="(item, index) in comments" :key="item.id" class="comment-row" :class="{ 'last-comment': index === comments.length - 1 }">
          <view class="comment-avatar" :class="{ image: !!item.avatarUrl }">
            <image v-if="item.avatarUrl" class="avatar-image" :src="item.avatarUrl" mode="aspectFill" />
            <text v-else>{{ item.avatar }}</text>
          </view>
          <view class="comment-main">
            <view class="comment-headline">
              <view class="comment-name">{{ item.name }}</view>
              <view class="comment-report tapable" @click="reportComment(item)">举报</view>
            </view>
            <view class="comment-text">{{ item.text }}</view>
          </view>
        </view>
        <view class="comment-form">
          <input :value="draft" class="comment-input" placeholder="友好交流，别留外部联系方式" @input="updateDraft" @blur="trimDraft" />
          <button class="send-btn" :disabled="commentSubmitting" @click="sendComment">{{ commentSubmitting ? '发送中' : '发送' }}</button>
        </view>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { resolveBackendMediaUrl } from '../../../api/http'
import { createCommunityComment, deleteCommunityPost, getCommunityPostDetail, likeCommunityPost, unlikeCommunityPost, type CommunityCommentResponse, type CommunityPostDetailResponse } from '../../../api/modules/community'
import { followPublicProfile, getMyProfile, getPublicProfile, unfollowPublicProfile } from '../../../api/modules/user'
import {
  firstChar,
  formatDateTime,
  inputValue,
  isValidBackendUserId,
  isValidBackendProductId,
  isValidCommunityCommentNo,
  isValidCommunityPostId,
  toSafeBackendId,
  assertCommunityCommentResponse,
  assertCommunityPostDetail,
  validatedCommunityAvatarUrl,
  validatedCommunityImageUrl,
  type CommentItem
} from './community-detail-helpers'

type NavigateToWithFailure = (options: { url: string; fail?: (error: unknown) => void }) => void
type RedirectToWithFailure = (options: { url: string; fail?: (error: unknown) => void }) => void
type SwitchTabWithFailure = (options: { url: string; fail?: (error: unknown) => void }) => void

const topic = ref('')
const postId = ref('')
const liked = ref(false)
const likeCount = ref(0)
const draft = ref('')
const commentSubmitting = ref(false)
const postTitle = ref('')
const postContent = ref('')
const imageSlots = ref<string[]>([])
const authorId = ref<number | null>(null)
const currentUserId = ref<number | null>(null)
const authorFollowed = ref(false)
const authorFollowLoaded = ref(false)
const authorFollowError = ref('')
const authorFollowSubmitting = ref(false)
const authorName = ref('平台用户')
const authorAvatar = ref('用')
const authorAvatarUrl = ref('')
const cityText = ref('城市未公开')
const createdText = ref('--')
const productId = ref<number | null>(null)
const relatedProductTitle = ref('未关联商品')
const relatedProductPrice = ref<string | number | null>(null)
const errorText = ref('')
const detailLoading = ref(false)
const detailLoaded = ref(false)
const deleteSubmitting = ref(false)
const comments = reactive<CommentItem[]>([])
const serverCommentCount = ref(0)
const navigateToWithFailure = uni.navigateTo as unknown as NavigateToWithFailure
const redirectToWithFailure = uni.redirectTo as unknown as RedirectToWithFailure
const switchTabWithFailure = uni.switchTab as unknown as SwitchTabWithFailure
const authorFollowButtonText = computed(() => {
  if (authorFollowError.value) return '关注状态暂不可用'
  if (authorFollowSubmitting.value) return '处理中'
  if (!authorFollowLoaded.value) return '关注状态加载中'
  return authorFollowed.value ? '已关注' : '关注'
})
const canMessageAuthor = computed(() => isValidBackendUserId(authorId.value))
const canManagePost = computed(() =>
  detailLoaded.value &&
  currentUserId.value !== null &&
  authorId.value === currentUserId.value &&
  isValidCommunityPostId(postId.value) &&
  !deleteSubmitting.value
)

const hasRelatedProduct = computed(() => productId.value !== null && isValidBackendProductId(String(productId.value)))
const productTitle = computed(() => `关联商品：${relatedProductTitle.value || '平台商品'}`)
const productDesc = computed(() => {
  const price = relatedProductPrice.value === null || relatedProductPrice.value === undefined || relatedProductPrice.value === '' ? '价格以平台为准' : `¥${relatedProductPrice.value}`
  return `点开查看商品详情 · ${price}`
})

function toCommentItem(item: CommunityCommentResponse): CommentItem {
  return {
    id: item.commentNo,
    avatar: firstChar(item.authorName || `用户 ${item.authorId}`),
    avatarUrl: resolveBackendMediaUrl(validatedCommunityAvatarUrl(item.authorAvatar || '')),
    name: item.authorName || `用户 ${item.authorId}`,
    text: item.content
  }
}

function applyCommunityPostDetail(detail: CommunityPostDetailResponse): void {
  topic.value = detail.topic || topic.value
  postTitle.value = detail.title
  postContent.value = detail.content
  imageSlots.value = (detail.imageUrls || []).map((url) => resolveBackendMediaUrl(validatedCommunityImageUrl(url))).filter(Boolean)
  liked.value = Boolean(detail.likedByMe)
  likeCount.value = detail.likeCount || 0
  serverCommentCount.value = detail.commentCount
  authorId.value = detail.authorId || null
  authorName.value = detail.authorName || `用户 ${detail.authorId}`
  authorAvatarUrl.value = resolveBackendMediaUrl(validatedCommunityAvatarUrl(detail.authorAvatar || ''))
  authorAvatar.value = firstChar(authorName.value)
  cityText.value = detail.city || '城市未公开'
  createdText.value = formatDateTime(detail.createdAt)
  authorFollowed.value = Boolean(detail.followedByMe)
  authorFollowLoaded.value = true
  authorFollowError.value = ''
  productId.value = detail.relatedProductId || null
  relatedProductTitle.value = detail.relatedProductTitle || '平台商品'
  relatedProductPrice.value = detail.relatedProductPrice ?? null
  comments.splice(0, comments.length, ...(detail.comments || []).map(toCommentItem))
}

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  topic.value = current?.options?.topic || hashParams?.get('topic') || ''
  postId.value = current?.options?.postId || hashParams?.get('postId') || ''
}
async function loadDetail() {
  detailLoading.value = true
  detailLoaded.value = false
  errorText.value = ''
  if (!isValidCommunityPostId(postId.value)) {
    errorText.value = '缺少有效动态编号，动态详情暂时不可用'
    detailLoading.value = false
    return
  }
  const numericPostId = toSafeBackendId(postId.value)
  if (!numericPostId) {
    errorText.value = '缺少有效动态编号，动态详情暂时不可用'
    detailLoading.value = false
    return
  }
  try {
    const detail = await getCommunityPostDetail(numericPostId)
    assertCommunityPostDetail(detail, numericPostId)
    applyCommunityPostDetail(detail)
    detailLoaded.value = true
    void hydrateAuthorFollowState()
  } catch {
    errorText.value = '动态详情加载失败，请稍后重试'
  } finally {
    detailLoading.value = false
  }
}
async function loadCurrentUserForManagement(): Promise<void> {
  try {
    const profile = await getMyProfile()
    currentUserId.value = Number.isSafeInteger(profile.userId) && profile.userId > 0 ? profile.userId : null
  } catch (error) {
    currentUserId.value = null
    console.warn('community detail current user load failed', { error })
  }
}
async function hydrateAuthorFollowState() {
  authorFollowError.value = ''
  authorFollowSubmitting.value = false
  if (!isValidBackendUserId(authorId.value)) {
    authorFollowed.value = false
    authorFollowLoaded.value = false
    authorFollowError.value = '缺少后端作者ID，未执行任何关注变更'
    return
  }
  const hadDetailFollowState = authorFollowLoaded.value
  const safeAuthorId = Number(authorId.value)
  try {
    const profile = await getPublicProfile(safeAuthorId)
    authorFollowed.value = Boolean(profile.followedByMe)
    authorFollowLoaded.value = true
  } catch (error) {
    if (!hadDetailFollowState) {
      authorFollowed.value = false
      authorFollowLoaded.value = false
      authorFollowError.value = '作者关注状态暂时不可用，请稍后刷新'
    }
    console.warn('community author follow state load failed', { authorId: safeAuthorId, error })
  }
}
async function sendComment() {
  trimDraft()
  if (draft.value.length < 4) return uni.showToast({ title: '评论至少 4 个字', icon: 'none' })
  if (!isValidCommunityPostId(postId.value)) {
    uni.showToast({ title: '缺少有效动态编号，不能发送评论', icon: 'none' })
    return
  }
  const numericPostId = toSafeBackendId(postId.value)
  if (!numericPostId) {
    uni.showToast({ title: '缺少有效动态编号，不能发送评论', icon: 'none' })
    return
  }
  const content = draft.value
  commentSubmitting.value = true
  try {
    const saved = await createCommunityComment(numericPostId, content)
    assertCommunityCommentResponse(saved)
    draft.value = ''
    try {
      const refreshed = await getCommunityPostDetail(numericPostId)
      assertCommunityPostDetail(refreshed, numericPostId)
      applyCommunityPostDetail(refreshed)
    } catch (refreshError) {
      console.warn('community detail refresh after comment failed', { postId: numericPostId, refreshError })
      uni.showModal({ title: '评论已提交', content: '评论已发送，列表刷新暂时失败，稍后重新进入会同步显示。', showCancel: false })
    }
  } catch {
    uni.showModal({ title: '提交失败', content: '评论没有发送成功，请检查网络或稍后重试。', showCancel: false })
  } finally {
    commentSubmitting.value = false
  }
}
function updateDraft(event: unknown): void {
  const value = inputValue(event)
  if (value === undefined) {
    uni.showToast({ title: '输入内容读取失败，请重新输入', icon: 'none' })
    return
  }
  draft.value = value
}
function trimDraft(): void {
  draft.value = draft.value.trim()
}
function openProduct() {
  if (!hasRelatedProduct.value || !productId.value) {
    uni.showToast({ title: '缺少关联商品，暂无法打开商品详情', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId.value}` })
}
function editCurrentPost() {
  const numericPostId = toSafeBackendId(postId.value)
  if (!canManagePost.value || !numericPostId) {
    uni.showToast({ title: '这条动态当前不能编辑', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/community/compose/index?mode=edit&postId=${encodeURIComponent(String(numericPostId))}` })
}
async function deleteCurrentPost() {
  const numericPostId = toSafeBackendId(postId.value)
  if (!canManagePost.value || !numericPostId || deleteSubmitting.value) {
    uni.showToast({ title: '这条动态当前不能删除', icon: 'none' })
    return
  }
  deleteSubmitting.value = true
  try {
    const deleted = await deleteCommunityPost(numericPostId)
    if (deleted.status !== 'DELETED') throw new Error('community detail delete response invalid')
    uni.showToast({ title: '动态已删除', icon: 'none' })
    redirectToWithFailure({
      url: '/pages/community/manage/index',
      fail: () => switchTabWithFailure({ url: '/pages/tabbar/message/index' })
    })
  } catch (error) {
    console.warn('community detail delete post failed', { postId: numericPostId, error })
    uni.showToast({ title: error instanceof Error ? error.message : '删除失败，请稍后重试', icon: 'none' })
  } finally {
    deleteSubmitting.value = false
  }
}
function previewPostImage(index: number): void {
  const current = imageSlots.value[index]
  if (!current) {
    uni.showToast({ title: '图片暂时无法预览', icon: 'none' })
    return
  }
  try {
    uni.previewImage({ current, urls: imageSlots.value })
  } catch (error) {
    console.warn('community detail image preview failed', { postId: postId.value, index, error })
    uni.showToast({ title: '图片预览失败，请稍后重试', icon: 'none' })
  }
}
async function toggleAuthorFollow() {
  if (authorFollowSubmitting.value) return
  if (authorFollowError.value || !authorFollowLoaded.value) {
    uni.showToast({ title: authorFollowError.value || '关注状态加载中，请稍后重试', icon: 'none' })
    return
  }
  if (!isValidBackendUserId(authorId.value)) {
    uni.showToast({ title: '作者资料暂时不可用，未完成关注', icon: 'none' })
    return
  }
  const safeAuthorId = Number(authorId.value)
  const wasFollowing = authorFollowed.value
  authorFollowSubmitting.value = true
  try {
    const updated = wasFollowing ? await unfollowPublicProfile(safeAuthorId) : await followPublicProfile(safeAuthorId)
    authorFollowed.value = Boolean(updated.followedByMe)
    authorFollowLoaded.value = true
    authorFollowError.value = ''
    uni.showToast({ title: wasFollowing ? '已取消关注' : '已关注', icon: 'none' })
  } catch {
    uni.showToast({ title: '关注状态没有提交成功，未执行本地关注变更', icon: 'none' })
  } finally {
    authorFollowSubmitting.value = false
  }
}
function messageAuthor() {
  if (!canMessageAuthor.value) {
    uni.showToast({ title: '作者资料暂时不可用，未进入私信', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${encodeURIComponent(String(authorId.value))}` })
}
function openAuthorProfile() {
  if (!isValidBackendUserId(authorId.value)) {
    uni.showToast({ title: '作者资料暂时不可用，未打开主页', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/user/public-profile/index?userId=${encodeURIComponent(String(authorId.value))}` })
}
async function likePost() {
  if (!isValidCommunityPostId(postId.value)) {
    uni.showToast({ title: '缺少有效动态编号，不能点赞', icon: 'none' })
    return
  }
  const numericPostId = toSafeBackendId(postId.value)
  if (!numericPostId) {
    uni.showToast({ title: '缺少有效动态编号，不能点赞', icon: 'none' })
    return
  }
  try {
    const saved = liked.value ? await unlikeCommunityPost(numericPostId) : await likeCommunityPost(numericPostId)
    assertCommunityPostDetail(saved, numericPostId)
    liked.value = Boolean(saved.likedByMe)
    likeCount.value = saved.likeCount
  } catch {
    uni.showModal({ title: '点赞失败', content: '点赞没有提交成功，请检查网络或稍后重试。', showCancel: false })
  }
}
function reportPost() {
  if (!isValidCommunityPostId(postId.value)) {
    uni.showToast({ title: '缺少有效动态编号，不能提交举报', icon: 'none' })
    return
  }
  navigateToReport(`/pages/report/submit/index?targetType=COMMUNITY_POST&targetId=${encodeURIComponent(postId.value)}`)
}
function reportComment(item: CommentItem) {
  if (!item || !isValidCommunityCommentNo(item.id)) {
    uni.showToast({ title: '缺少有效评论编号，不能提交举报', icon: 'none' })
    return
  }
  navigateToReport(`/pages/report/submit/index?targetType=COMMUNITY_COMMENT&targetId=${encodeURIComponent(item.id)}`)
}
function navigateToReport(url: string) {
  try {
    navigateToWithFailure({
      url,
      fail: (error: unknown) => {
        console.warn('community report navigation failed', { url, error })
        uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
      }
    })
  } catch (error) {
    console.warn('community report navigation failed', { url, error })
    uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
  }
}
onMounted(() => {
  readQuery()
  void loadCurrentUserForManagement()
  void loadDetail()
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
