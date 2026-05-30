<template>
  <view class="page-shell session-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 私信会话</view>
        <view class="page-title">消息中心</view>
      </view>
      <view class="hero-badge">{{ totalUnread }} 未读</view>
    </view>

    <view class="search-card ds-card">
      <input :value="keyword" class="search-input" placeholder="搜索昵称 / 商品 / 消息" @input="updateKeyword" />
      <button class="refresh-btn" :disabled="loading" @click="handleManualRefresh">
        {{ loading ? '刷新中' : '刷新' }}
      </button>
    </view>

    <view class="filter-row">
      <view v-for="item in filters" :key="item.value" class="filter-chip tapable" :class="{ active: filter === item.value }" @click="filter = item.value">
        {{ item.label }}<text v-if="countByFilter(item.value)"> · {{ countByFilter(item.value) }}</text>
      </view>
    </view>

    <view v-if="errorText" class="status-card error">{{ errorText }}</view>
    <view v-else-if="!loading && visibleConversations.length === 0" class="empty-card ds-card">
      <view class="empty-icon">💌</view>
      <view class="empty-title">暂无平台会话</view>
    </view>

    <view v-else class="session-list">
      <view v-for="item in visibleConversations" :key="item.conversationId" class="session-card ds-card tapable" @click="openConversation(item)">
        <view class="avatar" :class="{ image: !!peerAvatarUrl(item) }">
          <image v-if="peerAvatarUrl(item)" class="avatar-img" :src="peerAvatarUrl(item)" mode="aspectFill" />
          <text v-else>{{ peerAvatar(item) }}</text>
        </view>
        <view class="session-main">
          <view class="session-top">
            <view class="session-title">{{ peerName(item) }}</view>
            <view class="session-time">{{ formatTime(item.updatedAt) }}</view>
          </view>
          <view class="session-summary">{{ item.lastMessageSummary || '还没有消息，打个招呼吧～' }}</view>
          <view class="session-meta">
            <text v-if="scenarioLabel(item.lastMessageSummary)" class="scenario-chip">{{ scenarioLabel(item.lastMessageSummary) }}</text>
            <text>seq {{ item.lastServerSeq }}</text>
            <text>已读 {{ item.readSeq }}</text>
          </view>
        </view>
        <view class="session-side">
          <view v-if="item.unreadCount > 0" class="badge">{{ item.unreadCount }}</view>
          <button class="read-btn" :disabled="markingId === item.conversationId" @click.stop="handleMarkRead(item)">已读</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { getChatConversations, markConversationRead, type ChatConversationItem, type ChatConversationListResponse } from '../../../api/modules/chat'

type Filter = 'ALL' | 'UNREAD' | 'ORDER' | 'GIFT'
const filters: Array<{ label: string; value: Filter }> = [
  { label: '全部', value: 'ALL' },
  { label: '未读', value: 'UNREAD' },
  { label: '订单沟通', value: 'ORDER' },
  { label: '礼物互动', value: 'GIFT' }
]
const communityImageStoragePrefix = '/uploads/community-image/'
const conversations = ref<ChatConversationItem[]>([])
let refreshTimer: ReturnType<typeof setInterval> | null = null
const loading = ref(false)
const errorText = ref('')
const markingId = ref<number | null>(null)
const keyword = ref('')
const filter = ref<Filter>('ALL')
const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))
const visibleConversations = computed(() => conversations.value.filter((item) => matchFilter(item, filter.value) && matchKeyword(item)))

function matchFilter(item: ChatConversationItem, value: Filter): boolean {
  const summary = item.lastMessageSummary || ''
  if (value === 'UNREAD') return item.unreadCount > 0
  if (value === 'ORDER') return /订单|付款|发货|售后|物流/.test(summary)
  if (value === 'GIFT') return /礼物|皇冠|玫瑰|烟花/.test(summary)
  return true
}

function matchKeyword(item: ChatConversationItem): boolean {
  const text = keyword.value.toLowerCase()
  if (!text) return true
  return `${peerName(item)} ${item.lastMessageSummary || ''}`.toLowerCase().includes(text)
}

function countByFilter(value: Filter): number {
  return conversations.value.filter((item) => matchFilter(item, value)).length
}

function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse {
  if (!value || typeof value !== 'object') throw new Error('chat session list invalid response')
  const response = value as ChatConversationListResponse
  if (!Array.isArray(response.conversations)) throw new Error('chat session list invalid conversations')
  for (const item of response.conversations) assertConversationItem(item)
}

function assertConversationItem(value: unknown): asserts value is ChatConversationItem {
  if (!value || typeof value !== 'object') throw new Error('chat session list invalid item')
  const item = value as ChatConversationItem
  if (!isValidBackendId(item.conversationId) || !isValidBackendId(item.peerUserId)) throw new Error('chat session list invalid ids')
  if (!Number.isSafeInteger(item.lastServerSeq) || item.lastServerSeq < 0) throw new Error('chat session list invalid lastServerSeq')
  if (!Number.isSafeInteger(item.deliveredSeq) || item.deliveredSeq < 0) throw new Error('chat session list invalid deliveredSeq')
  if (!Number.isSafeInteger(item.readSeq) || item.readSeq < 0) throw new Error('chat session list invalid readSeq')
  if (!Number.isSafeInteger(item.unreadCount) || item.unreadCount < 0) throw new Error('chat session list invalid unreadCount')
  if (typeof item.updatedAt !== 'string') throw new Error('chat session list invalid updatedAt')
  if (item.peerNickname != null && typeof item.peerNickname !== 'string') throw new Error('chat session list invalid peerNickname')
  if (item.peerAvatarUrl != null && typeof item.peerAvatarUrl !== 'string') throw new Error('chat session list invalid peerAvatarUrl')
}

async function loadConversations(showLoading = true): Promise<void> {
  if (loading.value) return
  if (showLoading) loading.value = true
  errorText.value = ''
  try {
    const response = await getChatConversations()
    assertConversationListResponse(response)
    conversations.value = response.conversations
  } catch (error) {
    conversations.value = []
    console.warn('chat session list load failed', { error })
    errorText.value = '会话暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

function handleManualRefresh(): void {
  void loadConversations()
}

function startConversationRefresh(): void {
  if (refreshTimer) return
  refreshTimer = setInterval(() => { void loadConversations(false) }, 10000)
}
function stopConversationRefresh(): void {
  if (!refreshTimer) return
  clearInterval(refreshTimer)
  refreshTimer = null
}

async function handleMarkRead(item: ChatConversationItem): Promise<void> {
  if (markingId.value) return
  markingId.value = item.conversationId
  try {
    const requestedReadSeq = item.lastServerSeq
    const response = await markConversationRead(item.conversationId, { readSeq: requestedReadSeq })
    if (response.conversationId !== item.conversationId || response.readSeq !== requestedReadSeq || response.readSeq > item.lastServerSeq || !Number.isSafeInteger(response.unreadCount) || response.unreadCount < 0) throw new Error('chat session read response invalid')
    item.readSeq = response.readSeq
    item.unreadCount = response.unreadCount
  } catch (error) {
    console.warn('chat session read mutation failed', { conversationId: item.conversationId, error })
    uni.showToast({ title: '已读状态暂时不可更新，请稍后重试', icon: 'none' })
  } finally {
    markingId.value = null
  }
}

function isValidBackendId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function openConversation(item: ChatConversationItem): void {
  if (!isValidBackendId(item.conversationId) || !isValidBackendId(item.peerUserId)) {
    uni.showToast({ title: '缺少有效会话用户，暂不能打开聊天', icon: 'none' })
    return
  }
  const route = {
    url: `/pages/chat/conversation/index?conversationId=${encodeURIComponent(String(item.conversationId))}&peerUserId=${encodeURIComponent(String(item.peerUserId))}`,
    fail: (error: unknown) => {
      console.warn('chat session conversation navigation failed', { conversationId: item.conversationId, peerUserId: item.peerUserId, error })
      uni.showToast({ title: '暂时无法打开聊天，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('chat session conversation navigation failed', { conversationId: item.conversationId, peerUserId: item.peerUserId, error })
    uni.showToast({ title: '暂时无法打开聊天，请稍后重试', icon: 'none' })
  }
}

function validatedCommunityImageUrl(url: unknown): string {
  if (typeof url !== 'string' || !url.startsWith(communityImageStoragePrefix)) return ''
  const lower = url.toLowerCase()
  const relativePath = url.slice(communityImageStoragePrefix.length)
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

function updateKeyword(event: unknown): void {
  const value = (event as { detail?: { value?: unknown } }).detail?.value
  if (typeof value !== 'string') {
    console.warn('chat session keyword input invalid')
    return
  }
  keyword.value = value.trim()
}

function peerAvatar(item: ChatConversationItem): string { return (item.peerNickname || String(item.peerUserId)).slice(-1) }
function peerAvatarUrl(item: ChatConversationItem): string { return validatedCommunityImageUrl(item.peerAvatarUrl || '') }
function peerName(item: ChatConversationItem): string { return item.peerNickname || `用户 ${item.peerUserId}` }
function scenarioLabel(summary?: string): string {
  if (!summary) return ''
  if (/礼物|皇冠|玫瑰|烟花/.test(summary)) return '礼物互动'
  if (/售后|物流/.test(summary)) return '售后沟通'
  if (/订单|发货|付款/.test(summary)) return '订单沟通'
  return ''
}
function formatTime(value: string): string { return value ? value.replace('T', ' ').slice(5, 16) : '--' }

onMounted(() => {
  void loadConversations()
  startConversationRefresh()
})
onShow(() => { void loadConversations(false) })
onUnload(stopConversationRefresh)
</script>

<style scoped>
.session-page { background:radial-gradient(circle at 10% 0%,rgba(255,205,159,.32),transparent 27%),linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.hero { padding:20rpx; display:flex; justify-content:space-between; gap:16rpx; align-items:flex-start; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); box-shadow:0 12rpx 26rpx rgba(255,122,69,.07); }
.kicker { color:#ff7a45; font-size:20rpx; font-weight:950; }
.hero-badge { padding:9rpx 15rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:20rpx; font-weight:950; }
.search-card { margin-top:12rpx; padding:11rpx; display:flex; gap:10rpx; border-color:#ffd9bd; }
.search-input { flex:1; height:56rpx; padding:0 16rpx; border-radius:999rpx; background:#fffaf6; color:#3a2a1f; font-size:22rpx; }
.refresh-btn { margin:0; width:112rpx; height:56rpx; line-height:56rpx; border-radius:999rpx; background:#3a2a1f; color:#fff; font-size:21rpx; font-weight:950; }
.filter-row { margin-top:12rpx; display:flex; gap:9rpx; overflow-x:auto; }
.filter-chip { flex:none; padding:10rpx 17rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:20rpx; font-weight:900; }
.filter-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.status-card,.empty-card { margin-top:14rpx; padding:26rpx 20rpx; text-align:center; border-radius:24rpx; color:#9b7560; }
.error { color:#dc2626; }
.empty-icon { font-size:54rpx; }
.empty-title { margin-top:10rpx; color:#3a2a1f; font-size:28rpx; font-weight:950; }
.session-list { margin-top:12rpx; display:flex; flex-direction:column; gap:11rpx; }
.session-card { padding:15rpx; display:flex; align-items:center; gap:13rpx; border-color:#ffd9bd; box-shadow:0 12rpx 26rpx rgba(255,122,69,.065); }
.avatar { width:72rpx; height:72rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:28rpx; font-weight:950; overflow:hidden; }
.avatar.image { background:#fff3e7; }
.avatar-img { width:100%; height:100%; display:block; }
.session-main { min-width:0; flex:1; }
.session-top { display:flex; justify-content:space-between; gap:14rpx; }
.session-title { color:#3a2a1f; font-size:24rpx; font-weight:950; }
.session-time { color:#b9856a; font-size:18rpx; }
.session-summary { margin-top:6rpx; overflow:hidden; color:#7b5542; font-size:21rpx; text-overflow:ellipsis; white-space:nowrap; }
.session-meta { margin-top:6rpx; display:flex; gap:9rpx; color:#b9856a; font-size:18rpx; align-items:center; flex-wrap:wrap; }
.scenario-chip { padding:5rpx 10rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-weight:900; }
.session-side { display:flex; flex-direction:column; align-items:flex-end; gap:8rpx; }
.badge { min-width:32rpx; height:32rpx; padding:0 8rpx; border-radius:999rpx; background:#ff3f8d; color:#fff; font-size:19rpx; line-height:32rpx; text-align:center; }
.read-btn { margin:0; width:76rpx; height:42rpx; line-height:42rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:19rpx; font-weight:950; }
</style>
