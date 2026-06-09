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

    <view v-if="errorText" class="status-card ds-card error">{{ errorText }}</view>
    <view v-else-if="loading" class="status-card ds-card">私信加载中...</view>
    <view v-else-if="!loading && visibleConversations.length === 0" class="empty-card ds-card" :class="{ filtered: isFilteredEmpty }">
      <view class="empty-icon">{{ isFilteredEmpty ? '搜' : '💌' }}</view>
      <view class="empty-title">{{ isFilteredEmpty ? '没有找到匹配会话' : '还没有私信会话' }}</view>
      <view class="empty-desc">
        {{ isFilteredEmpty ? '换个关键词或筛选，再看看最近联系的人。' : '去社区逛逛，遇到喜欢的宝贝或同好再开始聊天。' }}
      </view>
      <view class="empty-actions">
        <view class="empty-action primary tapable" @click="handleManualRefresh">{{ loading ? '刷新中' : '刷新' }}</view>
        <view v-if="hasActiveFilter" class="empty-action tapable" @click="clearFilters">清空筛选</view>
        <view class="empty-action warm tapable" @click="goCommunity">去社区</view>
      </view>
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
            <text v-for="badge in chatPeerIdentityBadges(item)" :key="badge" class="identity-chip">{{ badge }}</text>
          </view>
        </view>
        <view class="session-side">
          <view v-if="item.unreadCount > 0" class="badge">{{ item.unreadCount }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import { resolveBackendMediaUrl } from '../../../api/http'
import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../../../api/modules/chat'
import { assertChatPeerIdentityFields, chatPeerIdentityBadges } from '../chat-peer'

type Filter = 'ALL' | 'UNREAD' | 'ORDER' | 'GIFT'
const filters: Array<{ label: string; value: Filter }> = [
  { label: '全部', value: 'ALL' },
  { label: '未读', value: 'UNREAD' },
  { label: '订单沟通', value: 'ORDER' },
  { label: '礼物互动', value: 'GIFT' }
]
const communityImageStoragePrefix = '/uploads/community-image/'
const avatarImageStoragePrefix = '/uploads/avatar/'
const conversations = ref<ChatConversationItem[]>([])
let refreshTimer: ReturnType<typeof setInterval> | null = null
const loading = ref(false)
const errorText = ref('')
const keyword = ref('')
const filter = ref<Filter>('ALL')
const totalUnread = computed(() => conversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))
const visibleConversations = computed(() => conversations.value.filter((item) => matchFilter(item, filter.value) && matchKeyword(item)))
const hasActiveFilter = computed(() => keyword.value.length > 0 || filter.value !== 'ALL')
const isFilteredEmpty = computed(() => conversations.value.length > 0 && hasActiveFilter.value && visibleConversations.value.length === 0)

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
  return `${peerName(item)} ${item.lastMessageSummary || ''} ${chatPeerIdentityBadges(item).join(' ')}`.toLowerCase().includes(text)
}

function countByFilter(value: Filter): number {
  return conversations.value.filter((item) => matchFilter(item, value)).length
}

function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse {
  if (!value || typeof value !== 'object') throw new Error('chat session list invalid response')
  const response = value as ChatConversationListResponse
  if (!Array.isArray(response.conversations)) throw new Error('chat session list invalid conversations')
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
  assertChatPeerIdentityFields(item)
}

async function loadConversations(showLoading = true, preserveOnError = false): Promise<void> {
  if (loading.value) return
  if (showLoading) loading.value = true
  if (!preserveOnError) errorText.value = ''
  try {
    const response = await getChatConversations()
    assertConversationListResponse(response)
    const safeConversations = validConversationItems(response.conversations)
    if (safeConversations.length === 0 && response.conversations.length > 0) {
      throw new Error('chat session list all conversations invalid')
    }
    conversations.value = safeConversations
    errorText.value = ''
  } catch (error) {
    if (!preserveOnError) conversations.value = []
    console.warn('chat session list load failed', { error })
    if (!preserveOnError || conversations.value.length === 0) {
      errorText.value = '会话暂时不可用，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function handleManualRefresh(): void {
  void loadConversations()
}

function clearFilters(): void {
  keyword.value = ''
  filter.value = 'ALL'
}

function goCommunity(): void {
  const route = {
    url: '/pages/tabbar/message/index',
    fail: (error: unknown) => {
      console.warn('chat session community navigation failed', { error })
      uni.showToast({ title: '暂时无法打开社区，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.switchTab(route)
  } catch (error) {
    console.warn('chat session community navigation failed', { error })
    uni.showToast({ title: '暂时无法打开社区，请稍后重试', icon: 'none' })
  }
}

function startConversationRefresh(): void {
  if (refreshTimer) return
  refreshTimer = setInterval(() => { void loadConversations(false, true) }, 5000)
}
function stopConversationRefresh(): void {
  if (!refreshTimer) return
  clearInterval(refreshTimer)
  refreshTimer = null
}

function validConversationItems(items: unknown[]): ChatConversationItem[] {
  const validItems: ChatConversationItem[] = []
  for (const item of items) {
    try {
      assertConversationItem(item)
      validItems.push(item)
    } catch (error) {
      console.warn('chat session invalid conversation isolated', {
        conversationId: typeof item === 'object' && item !== null ? (item as { conversationId?: unknown }).conversationId : undefined,
        peerUserId: typeof item === 'object' && item !== null ? (item as { peerUserId?: unknown }).peerUserId : undefined,
        error
      })
    }
  }
  return validItems
}

function isValidBackendId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function openConversation(item: ChatConversationItem): void {
  if (!isValidBackendId(item.conversationId) || !isValidBackendId(item.peerUserId)) {
    uni.showToast({ title: '暂时无法打开聊天，请稍后重试', icon: 'none' })
    return
  }
  const route = {
    url: `/pages/chat/conversation/index?conversationId=${encodeURIComponent(String(item.conversationId))}&receiverId=${encodeURIComponent(String(item.peerUserId))}`,
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
  if (typeof url !== 'string') return ''
  const matchedPrefix = url.startsWith(communityImageStoragePrefix)
    ? communityImageStoragePrefix
    : (url.startsWith(avatarImageStoragePrefix) ? avatarImageStoragePrefix : '')
  if (!matchedPrefix) return ''
  const lower = url.toLowerCase()
  const relativePath = url.slice(matchedPrefix.length)
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

function peerAvatar(item: ChatConversationItem): string { return (item.peerNickname || '聊').slice(-1) }
function peerAvatarUrl(item: ChatConversationItem): string { return resolveBackendMediaUrl(validatedCommunityImageUrl(item.peerAvatarUrl || '')) }
function peerName(item: ChatConversationItem): string { return item.peerNickname?.trim() || '小原圈用户' }
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
onShow(() => { void loadConversations(false, true) })
onUnload(stopConversationRefresh)
</script>

<style scoped>
.session-page { --session-visible-height:calc(100vh - var(--window-top, 0px) - var(--window-bottom, 0px)); height:var(--session-visible-height); min-height:var(--session-visible-height); padding:calc(18rpx + var(--global-ticker-offset, 0rpx)) 18rpx calc(18rpx + env(safe-area-inset-bottom)); display:flex; flex-direction:column; overflow:hidden; background:radial-gradient(circle at 10% 0%,rgba(255,205,159,.32),transparent 27%),linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
@supports (height: 100dvh) {
  .session-page { --session-visible-height:calc(100dvh - var(--window-top, 0px) - var(--window-bottom, 0px)); }
}
.hero { padding:20rpx; display:flex; justify-content:space-between; gap:16rpx; align-items:flex-start; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); box-shadow:0 12rpx 26rpx rgba(255,122,69,.07); }
.kicker { color:#ff7a45; font-size:20rpx; font-weight:950; }
.hero-badge { padding:9rpx 15rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:20rpx; font-weight:950; }
.search-card { margin-top:12rpx; padding:11rpx; display:flex; gap:10rpx; border-color:#ffd9bd; }
.search-input { flex:1; height:56rpx; padding:0 16rpx; border-radius:999rpx; background:#fffaf6; color:#3a2a1f; font-size:22rpx; }
.refresh-btn { margin:0; width:112rpx; height:56rpx; line-height:56rpx; border-radius:999rpx; background:#3a2a1f; color:#fff; font-size:21rpx; font-weight:950; }
.filter-row { flex:0 0 auto; margin-top:12rpx; display:flex; gap:9rpx; overflow-x:auto; }
.filter-chip { flex:none; padding:10rpx 17rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:20rpx; font-weight:900; }
.filter-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.status-card,.empty-card { margin-top:14rpx; padding:26rpx 20rpx; text-align:center; border-radius:24rpx; color:#9b7560; }
.error { color:#dc2626; }
.empty-card { border-color:#ffd9bd; background:linear-gradient(180deg,#fff 0%,#fff7ef 100%); box-shadow:0 12rpx 28rpx rgba(255,122,69,.07); }
.empty-card.filtered { background:linear-gradient(180deg,#fffdf9 0%,#fff2e6 100%); }
.empty-icon { width:78rpx; height:78rpx; margin:0 auto; border-radius:50%; display:flex; align-items:center; justify-content:center; background:#fff3e7; color:#ff7a45; font-size:42rpx; font-weight:950; box-shadow:inset 0 0 0 1rpx #ffe0c9; }
.empty-title { margin-top:10rpx; color:#3a2a1f; font-size:28rpx; font-weight:950; }
.empty-desc { width:86%; max-width:520rpx; margin:8rpx auto 0; color:#8f6852; font-size:21rpx; line-height:1.45; }
.empty-actions { margin-top:18rpx; display:flex; justify-content:center; gap:10rpx; flex-wrap:wrap; }
.empty-action { min-width:116rpx; height:52rpx; padding:0 18rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; background:#fffaf6; border:1rpx solid #ffd9bd; color:#7b5542; font-size:20rpx; font-weight:950; box-sizing:border-box; }
.empty-action.primary { background:#3a2a1f; border-color:#3a2a1f; color:#fff; }
.empty-action.warm { background:#ff7a45; border-color:#ff7a45; color:#fff; }
.session-list { flex:1; min-height:0; margin-top:12rpx; padding-bottom:12rpx; display:flex; flex-direction:column; gap:11rpx; overflow-y:auto; -webkit-overflow-scrolling:touch; }
.session-card { padding:15rpx; display:flex; align-items:center; gap:13rpx; border-color:#ffd9bd; box-shadow:0 12rpx 26rpx rgba(255,122,69,.065); }
.avatar { width:72rpx; height:72rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:28rpx; font-weight:950; overflow:hidden; }
.avatar.image { background:#fff3e7; }
.avatar-img { width:100%; height:100%; display:block; }
.session-main { min-width:0; flex:1; }
.session-top { display:flex; justify-content:space-between; gap:14rpx; min-width:0; align-items:center; }
.session-title { min-width:0; flex:1; color:#3a2a1f; font-size:24rpx; font-weight:950; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.session-time { flex:0 0 auto; color:#b9856a; font-size:18rpx; white-space:nowrap; }
.session-summary { margin-top:6rpx; overflow:hidden; color:#7b5542; font-size:21rpx; text-overflow:ellipsis; white-space:nowrap; }
.session-meta { margin-top:6rpx; display:flex; gap:9rpx; color:#b9856a; font-size:18rpx; align-items:center; flex-wrap:wrap; }
.scenario-chip { padding:5rpx 10rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-weight:900; }
.identity-chip { padding:5rpx 10rpx; border-radius:999rpx; background:#fffaf6; color:#7b5542; border:1rpx solid #ffe4d1; font-weight:900; }
.session-side { display:flex; flex-direction:column; align-items:flex-end; gap:8rpx; }
.badge { min-width:32rpx; height:32rpx; padding:0 8rpx; border-radius:999rpx; background:#ff3f8d; color:#fff; font-size:19rpx; line-height:32rpx; text-align:center; }
@media (max-width: 360px) {
  .session-page { padding-left:14rpx; padding-right:14rpx; }
  .hero { padding:16rpx; }
  .page-title { font-size:32rpx; }
  .search-card { gap:8rpx; padding:9rpx; }
  .refresh-btn { width:96rpx; font-size:19rpx; }
  .filter-chip { padding:9rpx 14rpx; font-size:19rpx; }
  .session-card { padding:13rpx; gap:10rpx; }
  .avatar { width:64rpx; height:64rpx; font-size:25rpx; }
  .session-title { font-size:23rpx; }
  .session-summary { font-size:20rpx; }
  .session-meta { gap:6rpx; max-height:30rpx; overflow:hidden; flex-wrap:nowrap; }
  .identity-chip,.scenario-chip { max-width:120rpx; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
}
</style>
