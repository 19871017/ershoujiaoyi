<template>
  <view class="page-shell community-page">
    <view class="community-top ds-card">
      <image class="community-top-art" :src="communityHeroBanner" mode="aspectFill" />
      <view class="community-top-mask"></view>
      <view class="community-top-content">
        <view class="community-top-head">
          <view class="community-title-chip">社区</view>
          <view class="community-notice tapable" @click="openNotification">
            <text class="community-notice-icon">🔔</text>
            <text>通知</text>
          </view>
        </view>
        <view class="topic-grid">
          <view v-for="item in topics" :key="item.title" class="topic-card tapable" :class="{ active: activeTopic === item.title }" @click="selectTopic(item.title)">
            <view class="topic-icon">{{ item.icon }}</view>
            <view class="topic-title">{{ item.title }}</view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">{{ loadError }}</view>
    <view v-else-if="loading" class="empty-card ds-card">加载中…</view>

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
import communityHeroBanner from '../../../assets/community/community-hero-banner.png'

const activeTopic = ref('生活日常')
const topics = [
  { icon: '🌷', title: '生活日常' },
  { icon: '🛡️', title: '闲置避坑' },
  { icon: '💬', title: '交易经验' },
  { icon: '🎯', title: '求购心愿' }
]
const feeds = ref<CommunityPostResponse[]>([])
const loading = ref(false)
const loadError = ref('')
const filteredFeeds = computed(() => feeds.value.filter((item) => item.topic === activeTopic.value))

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
function selectTopic(title: string) {
  if (activeTopic.value === title) return
  activeTopic.value = title
}
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
    showToast('缺少有效动态编号，未打开动态详情')
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
.community-top { position:relative; margin-top:4rpx; overflow:hidden; min-height:332rpx; padding:18rpx; border-color:#ffd2b4; background:linear-gradient(135deg,#fff8f0 0%,#fff1e3 100%); box-shadow:0 18rpx 42rpx rgba(255,132,92,.14); }
.community-top-art { position:absolute; inset:0; width:100%; height:100%; }
.community-top-mask { position:absolute; inset:0; background:linear-gradient(115deg,rgba(255,248,240,.96) 0%,rgba(255,241,227,.8) 42%,rgba(255,194,163,.18) 100%); }
.community-top-content { position:relative; z-index:2; display:flex; min-height:296rpx; flex-direction:column; justify-content:space-between; }
.community-top-head { display:flex; align-items:center; justify-content:space-between; gap:16rpx; }
.community-title-chip { padding:10rpx 20rpx; border-radius:999rpx; background:rgba(255,255,255,.82); color:#5a3526; font-size:24rpx; font-weight:950; letter-spacing:2rpx; box-shadow:0 10rpx 24rpx rgba(255,144,98,.12); }
.community-notice { display:flex; align-items:center; gap:8rpx; padding:10rpx 18rpx; border-radius:999rpx; background:rgba(255,122,69,.92); color:#fff; font-size:20rpx; font-weight:900; box-shadow:0 12rpx 28rpx rgba(255,122,69,.26); }
.community-notice-icon { font-size:22rpx; }
.time { color:#9b7560; font-size:21rpx; }
.topic-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14rpx; }
.topic-card { min-height:98rpx; padding:12rpx 10rpx; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6rpx; border:2rpx solid rgba(255,255,255,.7); border-radius:28rpx; background:rgba(255,255,255,.72); backdrop-filter:blur(10rpx); box-shadow:0 12rpx 24rpx rgba(255,166,120,.14); }
.topic-card.active { background:linear-gradient(135deg,rgba(255,255,255,.96),rgba(255,236,224,.98)); border-color:#ff7a45; box-shadow:0 18rpx 30rpx rgba(255,122,69,.18); }
.topic-icon { font-size:28rpx; }
.topic-title { font-size:21rpx; color:#3a2a1f; font-weight:950; }
.feed-card { margin-top:12rpx; padding:16rpx; border-color:#ffd9bd; }
.feed-head { display:flex; align-items:center; gap:10rpx; }
.avatar { width:58rpx; height:58rpx; border-radius:50%; color:#fff; display:flex; align-items:center; justify-content:center; font-weight:950; }.avatar.pink { background:#ff7a45; }
.feed-user { flex:1; min-width:0; }.name { font-weight:950; color:#3a2a1f; font-size:23rpx; }
.follow { padding:9rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
.feed-text { margin-top:16rpx; color:#3a2a1f; font-size:23rpx; line-height:1.55; }
.feed-actions { margin-top:12rpx; display:flex; gap:20rpx; color:#9b7560; font-size:20rpx; font-weight:900; }
.empty-card { margin-top:18rpx; padding:24rpx; text-align:center; color:#9b7560; border-color:#ffd9bd; }
.compose-fab { position:fixed; right:32rpx; bottom:calc(128rpx + env(safe-area-inset-bottom)); z-index:30; width:92rpx; height:92rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#ff7a45,#ff3f8d); color:#fff; font-size:54rpx; line-height:1; font-weight:800; box-shadow:0 16rpx 34rpx rgba(255,85,95,.32); border:4rpx solid rgba(255,255,255,.9); }
</style>
