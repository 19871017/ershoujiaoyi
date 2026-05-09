<template>
  <view class="page-shell session-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 私信会话</view>
        <view class="page-title">消息中心</view>
        <view class="page-desc">买卖沟通、订单提醒和圈内互动都在这里。</view>
      </view>
      <view class="hero-badge">{{ totalUnread }} 未读</view>
    </view>

    <view class="search-card ds-card">
      <input v-model.trim="keyword" class="search-input" placeholder="搜索昵称 / 商品 / 消息" />
      <button class="refresh-btn" :disabled="loading" @click="loadConversations">
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
      <view class="empty-title">暂无服务端会话</view>
      <view class="empty-desc">会话列表仅展示后端返回记录；聊天记录以服务端会话为准。</view>
    </view>

    <view v-else class="session-list">
      <view v-for="item in visibleConversations" :key="item.conversationId" class="session-card ds-card tapable" @click="openConversation(item)">
        <view class="avatar">{{ peerAvatar(item.peerUserId) }}</view>
        <view class="session-main">
          <view class="session-top">
            <view class="session-title">{{ peerName(item.peerUserId) }}</view>
            <view class="session-time">{{ formatTime(item.updatedAt) }}</view>
          </view>
          <view class="session-summary">{{ item.lastMessageSummary || '还没有消息，打个招呼吧～' }}</view>
          <view class="session-meta">
            <text>seq {{ item.lastServerSeq }}</text>
            <text>送达 {{ item.deliveredSeq }}</text>
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
import { getChatConversations, markConversationRead, type ChatConversationItem } from '../../../api/modules/chat'

type Filter = 'ALL' | 'UNREAD' | 'ORDER'
const filters: Array<{ label: string; value: Filter }> = [
  { label: '全部', value: 'ALL' },
  { label: '未读', value: 'UNREAD' },
  { label: '订单沟通', value: 'ORDER' }
]
const conversations = ref<ChatConversationItem[]>([])
const loading = ref(false)
const errorText = ref('')
const markingId = ref<number | null>(null)
const keyword = ref('')
const filter = ref<Filter>('ALL')
const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))
const visibleConversations = computed(() => conversations.value.filter((item) => matchFilter(item, filter.value) && matchKeyword(item)))
function matchFilter(item: ChatConversationItem, value: Filter) {
  if (value === 'UNREAD') return item.unreadCount > 0
  if (value === 'ORDER') return /订单|付款|发货|售后|物流/.test(item.lastMessageSummary || '')
  return true
}
function matchKeyword(item: ChatConversationItem) {
  const text = keyword.value.toLowerCase()
  if (!text) return true
  return `${peerName(item.peerUserId)} ${item.lastMessageSummary || ''}`.toLowerCase().includes(text)
}
function countByFilter(value: Filter) { return conversations.value.filter((item) => matchFilter(item, value)).length }
async function loadConversations() {
  if (loading.value) return
  loading.value = true
  errorText.value = ''
  try {
    const response = await getChatConversations()
    conversations.value = response.conversations
  } catch {
    conversations.value = []
    errorText.value = '会话接口暂时不可用，未展示任何示例会话'
  } finally {
    loading.value = false
  }
}
async function handleMarkRead(item: ChatConversationItem) {
  if (markingId.value) return
  markingId.value = item.conversationId
  try {
    const response = await markConversationRead(item.conversationId, { readSeq: item.lastServerSeq })
    item.readSeq = response.readSeq
    item.unreadCount = response.unreadCount
  } catch {
    uni.showToast({ title: '已读接口暂时不可用，未执行任何会话变更', icon: 'none' })
  } finally {
    markingId.value = null
  }
}
function openConversation(item: ChatConversationItem) {
  uni.navigateTo({ url: `/pages/chat/conversation/index?conversationId=${item.conversationId}&peerUserId=${item.peerUserId}` })
}
function peerAvatar(peerUserId: number) { return String(peerUserId).slice(-1) }
function peerName(peerUserId: number) { return `用户 ${peerUserId}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(5, 16) : '--' }
onMounted(loadConversations)
</script>

<style scoped>
.session-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.hero { padding:26rpx; display:flex; justify-content:space-between; gap:20rpx; align-items:flex-start; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }
.hero-badge { padding:12rpx 18rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:22rpx; font-weight:950; }
.search-card { margin-top:18rpx; padding:14rpx; display:flex; gap:12rpx; border-color:#ffd9bd; }
.search-input { flex:1; height:64rpx; padding:0 18rpx; border-radius:999rpx; background:#fffaf6; color:#3a2a1f; font-size:25rpx; }
.refresh-btn { margin:0; width:132rpx; height:64rpx; line-height:64rpx; border-radius:999rpx; background:#3a2a1f; color:#fff; font-size:22rpx; font-weight:950; }
.filter-row { margin-top:16rpx; display:flex; gap:12rpx; overflow-x:auto; }
.filter-chip { flex:none; padding:13rpx 20rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:22rpx; font-weight:900; }
.filter-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.status-card,.empty-card { margin-top:20rpx; padding:34rpx 24rpx; text-align:center; border-radius:28rpx; color:#9b7560; }
.error { color:#dc2626; }
.empty-icon { font-size:56rpx; }
.empty-title { margin-top:12rpx; color:#3a2a1f; font-size:30rpx; font-weight:950; }
.empty-desc { margin-top:8rpx; color:#9b7560; font-size:24rpx; }
.session-list { margin-top:18rpx; display:flex; flex-direction:column; gap:16rpx; }
.session-card { padding:20rpx; display:flex; align-items:center; gap:16rpx; border-color:#ffd9bd; }
.avatar { width:84rpx; height:84rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:32rpx; font-weight:950; }
.session-main { min-width:0; flex:1; }
.session-top { display:flex; justify-content:space-between; gap:16rpx; }
.session-title { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.session-time { color:#b9856a; font-size:20rpx; }
.session-summary { margin-top:8rpx; overflow:hidden; color:#7b5542; font-size:24rpx; text-overflow:ellipsis; white-space:nowrap; }
.session-meta { margin-top:8rpx; display:flex; gap:12rpx; color:#b9856a; font-size:19rpx; }
.session-side { display:flex; flex-direction:column; align-items:flex-end; gap:10rpx; }
.badge { min-width:34rpx; height:34rpx; padding:0 8rpx; border-radius:999rpx; background:#ff3f8d; color:#fff; font-size:20rpx; line-height:34rpx; text-align:center; }
.read-btn { margin:0; width:84rpx; height:48rpx; line-height:48rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:950; }
</style>
