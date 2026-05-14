<template>
  <view class="page-shell community-page">
    <view class="hero ds-card">
      <view class="page-desc">生活日常、交易经验、避坑分享和私信会话。</view>
    </view>

    <view class="notice-card ds-card tapable" @click="openNotification">
      <view class="notice-icon">🔔</view>
      <view class="notice-body">
        <view class="notice-title">消息中心</view>
        <view class="notice-desc">订单、私信、审核和举报处理结果需以平台通知为准。</view>
      </view>
      <view class="notice-cta">通知</view>
    </view>

    <view class="topic-grid">
      <view v-for="item in topics" :key="item.title" class="topic-card ds-card tapable" :class="{ active: activeTopic === item.title }" @click="selectTopic(item.title)">
        <view class="topic-icon">{{ item.icon }}</view>
        <view class="topic-title">{{ item.title }}</view>
      </view>
    </view>

    <view class="filter-row">
      <view v-for="item in filters" :key="item" class="filter tapable" :class="{ active: activeFilter === item }" @click="selectFilter(item)">{{ item }}</view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">{{ loadError }}</view>
    <view v-else-if="loading" class="empty-card ds-card">正在加载内容…</view>

    <view v-for="item in filteredFeeds" :key="item.postId" class="feed-card ds-card">
      <view class="feed-head">
        <view class="avatar pink">{{ avatarOf(item) }}</view>
        <view class="feed-user">
          <view class="name">{{ item.title }}</view>
          <view class="time">{{ formatTime(item.createdAt) }} · {{ item.topic }}</view>
        </view>
        <view class="follow tapable" @click="toggleFollow">关注</view>
      </view>
      <view class="feed-text">{{ item.content }}</view>
      <view class="feed-actions">
        <view class="tapable" @click="toggleLikeFeed(item)">{{ item.likedByMe ? '♥' : '♡' }} {{ item.likeCount }}</view>
        <view class="tapable" @click="openPost(item)">💬 {{ item.commentCount }}</view>
        <view class="tapable" @click="goSessions">私信</view>
      </view>
    </view>

    <view v-if="!loading && !loadError && filteredFeeds.length === 0" class="empty-card ds-card">暂无内容</view>

    <view class="compose-fab tapable" @click="openComposer">＋</view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { likeCommunityPost, listCommunityPosts, unlikeCommunityPost, type CommunityPostResponse } from '../../../api/modules/community'

const filters = ['全部', '生活日常', '闲置避坑', '交易经验', '求购心愿']
const activeFilter = ref('全部')
const activeTopic = ref('生活日常')
const topics = [
  { icon: '🌷', title: '生活日常' },
  { icon: '🧺', title: '闲置避坑' },
  { icon: '💗', title: '交易经验' }
]
const feeds = ref<CommunityPostResponse[]>([])
const loading = ref(false)
const loadError = ref('')
const filteredFeeds = computed(() => feeds.value.filter((item) => {
  const selected = activeFilter.value === '全部' ? activeTopic.value : activeFilter.value
  return item.topic === selected
}))

async function loadFeeds() {
  loading.value = true
  loadError.value = ''
  try {
    feeds.value = await listCommunityPosts(20)
  } catch {
    feeds.value = []
    loadError.value = '社区内容暂时不可用，未展示本地帖子样例'
  } finally {
    loading.value = false
  }
}

function goSessions() { uni.navigateTo({ url: '/pages/chat/session-list/index' }) }
function openNotification() { uni.navigateTo({ url: '/pages/notification/index' }) }
function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
function openComposer() { uni.navigateTo({ url: '/pages/community/compose/index' }) }
function selectTopic(title: string) { activeTopic.value = title; activeFilter.value = '全部' }
function selectFilter(title: string) { activeFilter.value = title; if (title !== '全部') activeTopic.value = title }
function isValidCommunityPostId(value: number | string | null | undefined) { return /^[1-9]\d{0,18}$/.test(String(value || '')) }
function openPost(item: CommunityPostResponse) {
  if (!isValidCommunityPostId(item.postId)) {
    showToast('缺少有效动态编号，未打开动态详情')
    return
  }
  uni.navigateTo({ url: `/pages/community/detail/index?postId=${item.postId}&topic=${encodeURIComponent(item.topic)}` })
}
function toggleFollow() { showToast('关注接口暂未接通后端，未执行任何关注变更') }
async function toggleLikeFeed(item: CommunityPostResponse) {
  if (!isValidCommunityPostId(item.postId)) {
    showToast('缺少有效动态编号，未执行点赞变更')
    return
  }
  const wasLiked = item.likedByMe
  try {
    const saved = wasLiked ? await unlikeCommunityPost(item.postId) : await likeCommunityPost(item.postId)
    item.likeCount = saved.likeCount
    item.likedByMe = saved.likedByMe
  } catch {
    showToast('点赞没有提交成功，未执行本地点赞变更')
  }
}
function avatarOf(item: CommunityPostResponse) { return (item.title || item.topic || '原').slice(0, 1) }
function formatTime(value: string) { return value ? value.slice(0, 16).replace('T', ' ') : '刚刚' }

onMounted(loadFeeds)
</script>

<style scoped>
.community-page { position:relative; min-height:100vh; padding-bottom:150rpx; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.hero { padding:14rpx; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.notice-card { margin-top:14rpx; padding:16rpx; display:flex; align-items:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.notice-icon { width:58rpx; height:58rpx; margin-right:12rpx; border-radius:20rpx; background:#fff; display:flex; align-items:center; justify-content:center; font-size:23rpx; box-shadow:0 8rpx 18rpx rgba(255,122,69,.12); }
.notice-body { flex:1; min-width:0; }
.notice-title { font-size:23rpx; font-weight:950; color:#3a2a1f; }
.notice-desc,.time { color:#9b7560; font-size:21rpx; }
.notice-desc { margin-top:5rpx; }
.notice-cta { padding:10rpx 16rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:20rpx; font-weight:900; }
.topic-grid { margin-top:14rpx; display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12rpx; }
.topic-card { min-height:92rpx; padding:10rpx 6rpx; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:5rpx; border-color:#ffd9bd; background:#fff; }
.topic-card.active { background:#fff3e7; border-color:#ff7a45; }
.topic-icon { font-size:27rpx; }.topic-title { font-size:20rpx; color:#3a2a1f; font-weight:950; }
.filter-row { margin-top:14rpx; display:flex; gap:12rpx; }
.filter { padding:10rpx 18rpx; border-radius:999rpx; background:#fff3e7; color:#9b7560; font-size:21rpx; font-weight:900; }.filter.active { background:#ff7a45; color:#fff; }
.feed-card { margin-top:12rpx; padding:16rpx; border-color:#ffd9bd; }
.feed-head { display:flex; align-items:center; gap:10rpx; }
.avatar { width:58rpx; height:58rpx; border-radius:50%; color:#fff; display:flex; align-items:center; justify-content:center; font-weight:950; }.avatar.blue { background:#60a5fa; } .avatar.dark { background:#3a2a1f; } .avatar.gold { background:#f59e0b; } .avatar.pink { background:#ff7a45; }
.feed-user { flex:1; min-width:0; }.name { font-weight:950; color:#3a2a1f; font-size:23rpx; }
.follow { padding:9rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
.feed-text { margin-top:16rpx; color:#3a2a1f; font-size:23rpx; line-height:1.55; }
.feed-actions { margin-top:12rpx; display:flex; gap:20rpx; color:#9b7560; font-size:20rpx; font-weight:900; }
.empty-card { margin-top:18rpx; padding:24rpx; text-align:center; color:#9b7560; border-color:#ffd9bd; }
.compose-fab { position:fixed; right:32rpx; bottom:calc(128rpx + env(safe-area-inset-bottom)); z-index:30; width:92rpx; height:92rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#ff7a45,#ff3f8d); color:#fff; font-size:54rpx; line-height:1; font-weight:800; box-shadow:0 16rpx 34rpx rgba(255,85,95,.32); border:4rpx solid rgba(255,255,255,.9); }
</style>
