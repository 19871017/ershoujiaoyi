<template>
  <view class="page-shell detail-page">
    <view v-if="errorText" class="empty-card ds-card">{{ errorText }}</view>

    <view v-else class="post-card ds-card">
      <view class="post-head">
        <view class="avatar">{{ authorAvatar }}</view>
        <view class="author">
          <view class="name">{{ authorName }}</view>
          <view class="meta">{{ topic }} · {{ createdText }} · {{ cityText }}</view>
        </view>
        <view class="follow tapable" @click="showFollowUnavailable">关注</view>
      </view>
      <view class="title">{{ postTitle || '动态详情加载中' }}</view>
      <view class="content">{{ postContent }}</view>
      <view v-if="imageSlots.length" class="image-grid">
        <image v-for="item in imageSlots" :key="item" class="post-image" :src="item" mode="aspectFill" />
      </view>
      <view class="action-row">
        <view class="tapable" @click="likePost">{{ liked ? '♥' : '♡' }} {{ likeCount }}</view>
        <view>💬 {{ comments.length }}</view>
        <view class="tapable" @click="reportPost">举报</view>
      </view>
    </view>

    <view class="goods-card ds-card tapable" @click="openProduct">
      <view class="goods-icon">🛍️</view>
      <view class="goods-main">
        <view class="goods-title">{{ productTitle }}</view>
        <view class="goods-desc">{{ productDesc }}</view>
      </view>
      <view class="goods-go">查看</view>
    </view>

    <view class="comment-card ds-card">
      <view class="section-title">评论互动</view>
      <view v-if="!comments.length" class="comment-empty">暂无后端评论</view>
      <view v-for="item in comments" :key="item.id" class="comment-row">
        <view class="comment-avatar">{{ item.avatar }}</view>
        <view class="comment-main">
          <view class="comment-name">{{ item.name }}</view>
          <view class="comment-text">{{ item.text }}</view>
        </view>
      </view>
      <view class="comment-form">
        <input v-model.trim="draft" class="comment-input" placeholder="友好交流，别留外部联系方式" />
        <button class="send-btn" :disabled="commentSubmitting" @click="sendComment">{{ commentSubmitting ? '发送中' : '发送' }}</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createCommunityComment, getCommunityPostDetail, likeCommunityPost } from '../../../api/modules/community'

interface CommentItem { id: string; avatar: string; name: string; text: string }

const topic = ref('')
const postId = ref('')
const liked = ref(false)
const likeCount = ref(0)
const draft = ref('')
const commentSubmitting = ref(false)
const postTitle = ref('')
const postContent = ref('')
const imageSlots = ref<string[]>([])
const authorName = ref('平台用户')
const authorAvatar = ref('用')
const cityText = ref('城市未公开')
const createdText = ref('--')
const productId = ref<number | null>(null)
const relatedProductTitle = ref('未关联商品')
const relatedProductPrice = ref<string | number | null>(null)
const errorText = ref('')
const comments = reactive<CommentItem[]>([])

const productTitle = computed(() => productId.value ? `关联商品：${relatedProductTitle.value || '后端商品'}` : '该动态没有关联商品')
const productDesc = computed(() => {
  if (!productId.value) return '关联商品必须由动态详情接口返回，当前不打开本地样例商品'
  const price = relatedProductPrice.value === null || relatedProductPrice.value === undefined || relatedProductPrice.value === '' ? '价格以后端为准' : `¥${relatedProductPrice.value}`
  return `商品编号 ${productId.value} · ${price}`
})

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  topic.value = current?.options?.topic || hashParams?.get('topic') || ''
  postId.value = current?.options?.postId || hashParams?.get('postId') || ''
}
function firstChar(value: string | undefined) { return value?.trim()?.slice(0, 1) || '用' }
function formatDateTime(value: string | undefined) { return value ? value.replace('T', ' ').slice(0, 16) : '--' }
function isValidCommunityPostId(value: string) { return /^[1-9]\d{0,18}$/.test(value) }
async function loadDetail() {
  if (!isValidCommunityPostId(postId.value)) {
    errorText.value = '缺少有效动态编号，动态详情接口未加载，未展示本地样例'
    return
  }
  const numericPostId = Number(postId.value)
  try {
    const detail = await getCommunityPostDetail(numericPostId)
    topic.value = detail.topic || topic.value
    postTitle.value = detail.title
    postContent.value = detail.content
    imageSlots.value = detail.imageUrls || []
    liked.value = Boolean(detail.likedByMe)
    likeCount.value = detail.likeCount || 0
    authorName.value = detail.authorName || `用户 ${detail.authorId}`
    authorAvatar.value = detail.authorAvatar || firstChar(authorName.value)
    cityText.value = detail.city || '城市未公开'
    createdText.value = formatDateTime(detail.createdAt)
    productId.value = detail.relatedProductId || null
    relatedProductTitle.value = detail.relatedProductTitle || '后端商品'
    relatedProductPrice.value = detail.relatedProductPrice ?? null
    comments.splice(0, comments.length, ...(detail.comments || []).map((item) => ({
      id: item.commentNo,
      avatar: firstChar(String(item.authorId)),
      name: `用户 ${item.authorId}`,
      text: item.content
    })))
  } catch {
    errorText.value = '动态详情加载失败，未展示静态作者、评论或关联商品样例'
  }
}
async function sendComment() {
  if (draft.value.length < 2) return uni.showToast({ title: '评论至少 2 个字', icon: 'none' })
  if (!isValidCommunityPostId(postId.value)) {
    uni.showToast({ title: '缺少有效动态编号，不能发送评论', icon: 'none' })
    return
  }
  const numericPostId = Number(postId.value)
  const content = draft.value
  commentSubmitting.value = true
  try {
    const saved = await createCommunityComment(numericPostId, content)
    comments.push({ id: saved.commentNo, avatar: firstChar(String(saved.authorId)), name: `用户 ${saved.authorId}`, text: saved.content })
    draft.value = ''
  } catch {
    uni.showModal({ title: '提交失败', content: '评论没有发送成功，请检查网络或稍后重试。', showCancel: false })
  } finally {
    commentSubmitting.value = false
  }
}
function openProduct() {
  if (!productId.value) {
    uni.showToast({ title: '缺少关联商品，未打开本地样例商品', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId.value}` })
}
function showFollowUnavailable() { uni.showToast({ title: '关注接口暂未接通后端，未执行任何关注变更', icon: 'none' }) }
async function likePost() {
  if (!isValidCommunityPostId(postId.value)) {
    uni.showToast({ title: '缺少有效动态编号，不能点赞', icon: 'none' })
    return
  }
  const numericPostId = Number(postId.value)
  try {
    const saved = await likeCommunityPost(numericPostId)
    liked.value = true
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
  uni.navigateTo({ url: `/pages/report/submit/index?targetType=POST&targetId=${encodeURIComponent(postId.value)}` })
}
onMounted(() => { readQuery(); loadDetail() })
</script>

<style scoped>
.detail-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.post-card,.goods-card,.comment-card,.empty-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.empty-card { color:#9b7560; font-size:24rpx; line-height:1.6; }
.post-head,.goods-card,.comment-row,.comment-form { display:flex; align-items:center; gap:14rpx; }
.avatar,.comment-avatar { width:72rpx; height:72rpx; border-radius:50%; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:28rpx; font-weight:950; }
.author,.goods-main,.comment-main { flex:1; min-width:0; }.name,.section-title,.goods-title,.comment-name { color:#3a2a1f; font-weight:950; }
.meta,.goods-desc,.comment-empty { margin-top:5rpx; color:#9b7560; font-size:21rpx; }
.follow { padding:9rpx 16rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:20rpx; font-weight:900; }
.title { margin-top:20rpx; color:#3a2a1f; font-size:34rpx; font-weight:950; line-height:1.3; }
.content { margin-top:14rpx; color:#604050; font-size:26rpx; line-height:1.65; }
.image-grid { margin-top:16rpx; display:grid; grid-template-columns:repeat(3,1fr); gap:12rpx; }.post-image { width:100%; height:160rpx; border-radius:24rpx; background:#fff3e7; }
.action-row { margin-top:18rpx; display:flex; gap:30rpx; color:#9b7560; font-size:23rpx; font-weight:900; }
.goods-icon { width:72rpx; height:72rpx; border-radius:24rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.goods-go { color:#ff7a45; font-size:22rpx; font-weight:950; }
.comment-row { align-items:flex-start; padding:18rpx 0; border-bottom:1rpx solid #ffe5ef; }.comment-text { margin-top:6rpx; color:#7b5542; font-size:23rpx; line-height:1.5; }
.comment-form { margin-top:16rpx; }.comment-input { flex:1; height:68rpx; padding:0 18rpx; border-radius:999rpx; background:#fffaf6; border:1rpx solid #ffd9bd; font-size:23rpx; }.send-btn { width:102rpx; height:68rpx; line-height:68rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:23rpx; font-weight:950; }
</style>
