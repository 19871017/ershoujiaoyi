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
.community-page {
  position: relative;
  min-height: 100vh;
  padding-top: 18rpx;
  padding-bottom: 150rpx;
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
  min-height: 344rpx;
  padding: 20rpx;
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
  min-height: 304rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.community-top-head,
.feed-head,
.community-notice,
.topic-card,
.avatar,
.compose-fab {
  display: flex;
  align-items: center;
}

.community-top-head {
  justify-content: space-between;
  gap: 16rpx;
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
  gap: 8rpx;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  font-size: 20rpx;
  font-weight: 920;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .20);
}

.community-notice-icon {
  font-size: 22rpx;
}

.time {
  color: #8f6b57;
  font-size: 21rpx;
  line-height: 1.35;
  font-weight: 650;
}

.topic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
}

.topic-card {
  min-height: 104rpx;
  padding: 12rpx 10rpx;
  flex-direction: column;
  justify-content: center;
  gap: 7rpx;
  border: 1rpx solid rgba(255, 255, 255, .76);
  border-radius: 30rpx;
  background: rgba(255, 255, 255, .78);
  backdrop-filter: blur(10rpx);
  box-shadow: 0 12rpx 24rpx rgba(132, 70, 36, .08);
}

.topic-card.active {
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 238, 228, .98));
  border-color: rgba(239, 111, 63, .66);
  box-shadow: 0 16rpx 28rpx rgba(255, 122, 69, .14);
}

.topic-icon {
  font-size: 29rpx;
}

.topic-title {
  color: #342116;
  font-size: 21rpx;
  font-weight: 920;
  letter-spacing: .15rpx;
}

.feed-card {
  margin-top: 14rpx;
  padding: 18rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(255, 248, 242, .97));
}

.feed-head {
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

.feed-text {
  margin-top: 17rpx;
  color: #3a261a;
  font-size: 23rpx;
  font-weight: 650;
  line-height: 1.62;
  letter-spacing: .08rpx;
}

.feed-actions {
  margin-top: 15rpx;
  display: flex;
  gap: 22rpx;
  color: #8f6b57;
  font-size: 20rpx;
  font-weight: 850;
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
