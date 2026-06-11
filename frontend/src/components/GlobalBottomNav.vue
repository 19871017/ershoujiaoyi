<template>
  <view v-if="visible" class="global-bottom-nav-wrap">
    <view class="global-bottom-nav ds-card">
      <view
        v-for="item in tabs"
        :key="item.path"
        class="bottom-nav-item tapable"
        :class="{ active: activePath === item.path, publish: item.path === publishPath, 'has-unread': item.path === communityPath && totalUnread > 0 }"
        :data-nav-path="item.path"
        @click.stop="openTab(item.path)"
      >
        <view class="bottom-nav-icon">
          <text>{{ item.badge }}</text>
          <text v-if="item.path === communityPath && totalUnread > 0" class="bottom-nav-unread">{{ displayUnread }}</text>
        </view>
        <text class="bottom-nav-text">{{ item.label }}</text>
      </view>
    </view>
    <view v-if="communityMenuOpen" class="community-switcher ds-card">
      <view class="community-switcher-item tapable" data-community-switcher-action="feed" @click.stop="openCommunityFeed">
        <text class="switcher-icon">社</text>
        <view class="switcher-copy">
          <text class="switcher-title">社区动态</text>
          <text class="switcher-subtitle">看看新帖子</text>
        </view>
      </view>
      <view class="community-switcher-item private tapable" data-community-switcher-action="private" @click.stop="openPrivateChat">
        <text class="switcher-icon">信</text>
        <view class="switcher-copy">
          <text class="switcher-title">私聊</text>
          <text class="switcher-subtitle">{{ privateSubtitle }}</text>
        </view>
        <text v-if="totalUnread > 0" class="switcher-unread">{{ displayUnread }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../api/modules/chat'
import { useUserStore } from '../store/modules/user'

const homePath = '/pages/tabbar/home/index'
const publishPath = '/pages/tabbar/publish/index'
const communityPath = '/pages/tabbar/message/index'
const unreadRefreshMs = 5000
const communitySwitcherEventName = 'xiaoyuanquan:community-switcher'

const tabs = [
  { path: homePath, label: '首页', badge: '首' },
  { path: '/pages/tabbar/category/index', label: '宝贝', badge: '宝' },
  { path: publishPath, label: '商家秀', badge: '秀' },
  { path: communityPath, label: '社区', badge: '社' },
  { path: '/pages/tabbar/me/index', label: '我的', badge: '我' }
] as const

type TabPath = typeof tabs[number]['path']
type NavigateToWithFailure = (options: { url: string; fail?: (error: unknown) => void }) => void

const userStore = useUserStore()
const activePath = ref<TabPath | ''>('')
const communityMenuOpen = ref(false)
const totalUnread = ref(0)
const visible = computed(() => tabs.some((item) => item.path === activePath.value))
const displayUnread = computed(() => totalUnread.value > 99 ? '99+' : String(totalUnread.value))
const privateSubtitle = computed(() => totalUnread.value > 0 ? `${displayUnread.value} 条未读` : '进入会话列表')
const navigateToWithFailure = uni.navigateTo as unknown as NavigateToWithFailure
let unreadTimer: ReturnType<typeof setInterval> | null = null

function isTabPath(value: unknown): value is TabPath {
  return typeof value === 'string' && tabs.some((item) => item.path === value)
}

function normalizePath(path: string): string {
  const value = path.replace(/^#/, '').split('?')[0]
  return value.startsWith('/') ? value : `/${value}`
}

function currentPath(): string {
  if (typeof window === 'undefined') return activePath.value || homePath
  const hashPath = window.location.hash.replace(/^#/, '')
  return normalizePath(hashPath || homePath)
}

function syncActivePath(): void {
  const path = currentPath()
  const matched = tabs.find((item) => item.path === path)
  activePath.value = matched?.path ?? ''
  if (activePath.value !== communityPath) communityMenuOpen.value = false
}

function publishCommunitySwitcherState(open = communityMenuOpen.value): void {
  if (typeof window === 'undefined') return
  document.documentElement.classList.toggle('community-switcher-open', open)
  window.dispatchEvent(new CustomEvent(communitySwitcherEventName, { detail: { open } }))
}

function openTab(path: TabPath): void {
  if (path === communityPath) {
    communityMenuOpen.value = !communityMenuOpen.value
    publishCommunitySwitcherState(communityMenuOpen.value)
    return
  }
  communityMenuOpen.value = false
  if (path === activePath.value) return
  switchToTab(path)
}

function switchToTab(path: TabPath): void {
  const switchTab = uni.switchTab as unknown as (options: {
    url: string
    success?: () => void
    fail?: (error: unknown) => void
  }) => void
  try {
    switchTab({
      url: path,
      success: () => {
        activePath.value = path
      },
      fail: (error: unknown) => {
        console.warn('global bottom nav switchTab failed', { path, error })
        syncActivePath()
        uni.showToast({ title: '暂时无法切换页面', icon: 'none' })
      }
    })
  } catch (error) {
    console.warn('global bottom nav switchTab failed', { path, error })
    syncActivePath()
    uni.showToast({ title: '暂时无法切换页面', icon: 'none' })
  }
}

function openCommunityFeed(): void {
  communityMenuOpen.value = false
  if (activePath.value === communityPath) return
  switchToTab(communityPath)
}

function openPrivateChat(): void {
  communityMenuOpen.value = false
  navigateToWithFailure({
    url: '/pages/chat/session-list/index',
    fail: (error: unknown) => {
      console.warn('global bottom nav private chat navigation failed', { error })
      uni.showToast({ title: '暂时无法打开私聊', icon: 'none' })
    }
  })
}

function handleNativeNavClick(event: MouseEvent): void {
  const target = event.target
  if (!(target instanceof Element)) return
  const wrap = target.closest('.global-bottom-nav-wrap')
  if (!wrap) return
  const switcherTarget = target.closest('[data-community-switcher-action]')
  if (switcherTarget) {
    event.preventDefault()
    event.stopPropagation()
    const action = switcherTarget.getAttribute('data-community-switcher-action')
    if (action === 'feed') openCommunityFeed()
    if (action === 'private') openPrivateChat()
    return
  }
  const navTarget = target.closest('[data-nav-path]')
  if (!navTarget) return
  const path = navTarget.getAttribute('data-nav-path')
  if (!isTabPath(path)) return
  event.preventDefault()
  event.stopPropagation()
  openTab(path)
}

function isValidBackendId(value: unknown): value is number {
  return Number.isSafeInteger(value) && Number(value) > 0
}

function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse {
  if (!value || typeof value !== 'object') throw new Error('bottom nav chat unread invalid response')
  if (!Array.isArray((value as ChatConversationListResponse).conversations)) throw new Error('bottom nav chat unread invalid conversations')
}

function validUnreadCount(item: unknown): number {
  if (!item || typeof item !== 'object') return 0
  const row = item as ChatConversationItem
  if (!isValidBackendId(row.conversationId) || !isValidBackendId(row.peerUserId)) return 0
  if (!Number.isSafeInteger(row.unreadCount) || row.unreadCount < 0) return 0
  return row.unreadCount
}

async function refreshUnreadCount(preserveOnError = true): Promise<void> {
  if (!userStore.token) {
    totalUnread.value = 0
    return
  }
  try {
    const response = await getChatConversations()
    assertConversationListResponse(response)
    totalUnread.value = response.conversations.reduce((sum, item) => sum + validUnreadCount(item), 0)
  } catch (error) {
    console.warn('global bottom nav chat unread load failed', { error })
    if (!preserveOnError) totalUnread.value = 0
  }
}

function startUnreadRefresh(): void {
  if (unreadTimer || !userStore.token) return
  unreadTimer = setInterval(() => { void refreshUnreadCount(true) }, unreadRefreshMs)
}

function stopUnreadRefresh(): void {
  if (!unreadTimer) return
  clearInterval(unreadTimer)
  unreadTimer = null
}

onMounted(() => {
  syncActivePath()
  publishCommunitySwitcherState()
  void refreshUnreadCount(false)
  startUnreadRefresh()
  if (typeof window !== 'undefined') {
    window.addEventListener('hashchange', syncActivePath)
    document.addEventListener('click', handleNativeNavClick)
  }
})

onBeforeUnmount(() => {
  publishCommunitySwitcherState(false)
  stopUnreadRefresh()
  if (typeof window !== 'undefined') {
    window.removeEventListener('hashchange', syncActivePath)
    document.removeEventListener('click', handleNativeNavClick)
  }
})

watch(() => userStore.token, (token) => {
  if (!token) {
    stopUnreadRefresh()
    totalUnread.value = 0
    communityMenuOpen.value = false
    return
  }
  void refreshUnreadCount(false)
  startUnreadRefresh()
})

watch(communityMenuOpen, (open) => {
  publishCommunitySwitcherState(open)
})
</script>

<style scoped>
.global-bottom-nav-wrap {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99999;
  pointer-events: auto;
  width: 100%;
}
.global-bottom-nav {
  width: 100%;
  min-height: calc(64px + env(safe-area-inset-bottom));
  padding: 6px max(8px, env(safe-area-inset-right)) calc(6px + env(safe-area-inset-bottom)) max(8px, env(safe-area-inset-left));
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  gap: 4px;
  border-width: 1rpx 0 0;
  border-radius: 0;
  border-color: rgba(255, 217, 189, .86);
  background: rgba(255, 251, 246, .98);
  box-shadow: 0 -8rpx 24rpx rgba(132, 70, 36, .09), inset 0 1rpx 0 rgba(255,255,255,.9);
  backdrop-filter: blur(16rpx);
}
.bottom-nav-item {
  min-width: 0;
  height: 52px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #9b7560;
  font-weight: 900;
}
.bottom-nav-item.active {
  color: #ff7a45;
  background: linear-gradient(180deg, rgba(255,244,232,.96), rgba(255,235,219,.92));
}
.bottom-nav-icon {
  position: relative;
  width: 26px;
  height: 26px;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff3e7;
  color: #ff7a45;
  font-size: 13px;
  line-height: 1;
  box-shadow: inset 0 0 0 2rpx rgba(255,122,69,.08);
}
.bottom-nav-unread {
  position: absolute;
  top: -7px;
  right: -10px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ff3f8d, #ff6f3f);
  color: #fff;
  font-size: 10px;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 6rpx 16rpx rgba(255, 63, 141, .28), 0 0 0 3rpx rgba(255, 255, 255, .96);
}
.bottom-nav-item.has-unread .bottom-nav-icon {
  background: #fff0f5;
  color: #ff3f8d;
}
.bottom-nav-item.publish .bottom-nav-icon {
  background: radial-gradient(circle at 35% 28%, #fff6c7 0, #ffd36b 28%, #ff7a45 56%, #ff3f8d 100%);
  color: #fff;
  box-shadow: 0 0 0 3rpx rgba(255,255,255,.9), 0 8rpx 18rpx rgba(255,63,141,.25);
}
.bottom-nav-text {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1;
}
.community-switcher {
  position: absolute;
  right: 38px;
  bottom: calc(76px + env(safe-area-inset-bottom));
  width: min(430rpx, calc(100vw - 54rpx));
  padding: 12rpx;
  display: grid;
  gap: 8rpx;
  border-color: rgba(255, 217, 189, .88);
  background: rgba(255, 251, 246, .98);
  box-shadow: 0 18rpx 42rpx rgba(132, 70, 36, .16);
  backdrop-filter: blur(18rpx);
}
.community-switcher::after {
  content: '';
  position: absolute;
  right: 74rpx;
  bottom: -10rpx;
  width: 20rpx;
  height: 20rpx;
  transform: rotate(45deg);
  background: rgba(255, 251, 246, .98);
  border-right: 1rpx solid rgba(255, 217, 189, .88);
  border-bottom: 1rpx solid rgba(255, 217, 189, .88);
}
.community-switcher-item {
  position: relative;
  z-index: 1;
  min-height: 78rpx;
  padding: 10rpx 12rpx;
  border-radius: 22rpx;
  display: grid;
  grid-template-columns: 42rpx minmax(0, 1fr) auto;
  align-items: center;
  gap: 10rpx;
  background: rgba(255, 255, 255, .78);
  color: #4b2d20;
  box-sizing: border-box;
}
.community-switcher-item.private {
  background: linear-gradient(135deg, rgba(255, 243, 247, .98), rgba(255, 238, 226, .96));
}
.switcher-icon {
  width: 42rpx;
  height: 42rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff3e7;
  color: #ff7a45;
  font-size: 18rpx;
  font-weight: 950;
}
.community-switcher-item.private .switcher-icon {
  background: linear-gradient(135deg, #ff3f8d, #ff8b76);
  color: #fff;
}
.switcher-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.switcher-title,
.switcher-subtitle {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.switcher-title {
  color: #3f261a;
  font-size: 22rpx;
  font-weight: 950;
  line-height: 1.1;
}
.switcher-subtitle {
  color: #9b7560;
  font-size: 17rpx;
  font-weight: 780;
  line-height: 1.1;
}
.switcher-unread {
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #ff3f8d;
  color: #fff;
  font-size: 17rpx;
  line-height: 30rpx;
  text-align: center;
  font-weight: 950;
}
@media (max-width: 360px) {
  .global-bottom-nav {
    min-height: calc(62px + env(safe-area-inset-bottom));
    padding: 5px max(7px, env(safe-area-inset-right)) calc(5px + env(safe-area-inset-bottom)) max(7px, env(safe-area-inset-left));
  }
  .bottom-nav-item {
    height: 50px;
    border-radius: 17px;
  }
  .bottom-nav-icon {
    width: 24px;
    height: 24px;
    font-size: 12px;
  }
  .bottom-nav-text { font-size: 11px; }
  .community-switcher {
    right: 28px;
    width: min(390rpx, calc(100vw - 34rpx));
  }
}
</style>
