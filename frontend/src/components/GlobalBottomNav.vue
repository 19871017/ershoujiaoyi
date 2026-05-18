<template>
  <view v-if="visible" class="global-bottom-nav-wrap">
    <view class="global-bottom-nav ds-card">
      <view
        v-for="item in tabs"
        :key="item.path"
        class="bottom-nav-item tapable"
        :class="{ active: activePath === item.path, publish: item.path === publishPath }"
        @click="openTab(item.path)"
      >
        <view class="bottom-nav-icon">{{ item.badge }}</view>
        <text class="bottom-nav-text">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const homePath = '/pages/tabbar/home/index'
const publishPath = '/pages/tabbar/publish/index'

const tabs = [
  { path: homePath, label: '首页', badge: '首' },
  { path: '/pages/tabbar/category/index', label: '宝贝', badge: '宝' },
  { path: publishPath, label: '商家秀', badge: '秀' },
  { path: '/pages/tabbar/message/index', label: '社区', badge: '社' },
  { path: '/pages/tabbar/me/index', label: '我的', badge: '我' }
] as const

type TabPath = typeof tabs[number]['path']

const activePath = ref<TabPath | ''>('')
const visible = computed(() => tabs.some((item) => item.path === activePath.value))

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
}

function openTab(path: TabPath): void {
  if (path === activePath.value) return
  uni.switchTab({ url: path })
  activePath.value = path
}

onMounted(() => {
  syncActivePath()
  if (typeof window !== 'undefined') {
    window.addEventListener('hashchange', syncActivePath)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('hashchange', syncActivePath)
  }
})
</script>

<style scoped>
.global-bottom-nav-wrap {
  position: fixed;
  left: 18rpx;
  right: 18rpx;
  bottom: calc(170rpx + env(safe-area-inset-bottom));
  z-index: 99999;
  pointer-events: auto;
}
.global-bottom-nav {
  min-height: 82rpx;
  padding: 8rpx 10rpx;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  gap: 4rpx;
  border-radius: 28rpx;
  border-color: rgba(255, 217, 189, .92);
  background: rgba(255, 251, 246, .98);
  box-shadow: 0 -8rpx 28rpx rgba(255, 122, 69, .13), inset 0 1rpx 0 rgba(255,255,255,.9);
  backdrop-filter: blur(16rpx);
}
.bottom-nav-item {
  min-width: 0;
  height: 66rpx;
  border-radius: 22rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3rpx;
  color: #9b7560;
  font-weight: 900;
}
.bottom-nav-item.active {
  color: #ff7a45;
  background: linear-gradient(180deg, rgba(255,244,232,.96), rgba(255,235,219,.92));
}
.bottom-nav-icon {
  width: 30rpx;
  height: 30rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff3e7;
  color: #ff7a45;
  font-size: 17rpx;
  line-height: 1;
  box-shadow: inset 0 0 0 2rpx rgba(255,122,69,.08);
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
  font-size: 18rpx;
  line-height: 1;
}
</style>
