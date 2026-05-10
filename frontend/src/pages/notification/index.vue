<template>
  <view class="page-shell notice-page">
    <view class="hero ds-card"><view><view class="kicker">♡ 消息通知</view><view class="page-title">通知中心</view><view class="page-desc">订单、私信、审核、举报处理结果统一查看；通知内容和已读状态以服务端记录为准。</view></view><view class="hero-icon">🔔</view></view>
    <view class="tab-row"><view v-for="item in tabs" :key="item.value" class="chip tapable" :class="{ active: active === item.value }" @click="switchTab(item.value)">{{ item.label }}</view></view>
    <view v-if="loading" class="empty-card ds-card">通知加载中...</view>
    <view v-else-if="loadError" class="empty-card ds-card">{{ loadError }}</view>
    <view v-else-if="filtered.length === 0" class="empty-card ds-card">暂无后端通知</view>
    <view v-else class="notice-list">
      <view v-for="item in filtered" :key="item.notificationNo" class="notice-card ds-card tapable" @click="openNotice(item)">
        <view class="notice-icon">{{ iconFor(item.type) }}</view>
        <view class="main"><view class="title-row"><text class="title">{{ item.title }}</text><text v-if="!item.read" class="dot">未读</text></view><view class="desc">{{ item.description }}</view><view class="time">{{ formatTime(item.createdAt) }}</view></view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listNotifications, markNotificationRead, type NotificationItemResponse, type NotificationType } from '../../api/modules/notification'

type NoticeType='ALL'|NotificationType
const active=ref<NoticeType>('ALL')
const tabs=[{label:'全部',value:'ALL' as const},{label:'订单',value:'ORDER' as const},{label:'私信',value:'CHAT' as const},{label:'审核',value:'AUDIT' as const},{label:'系统',value:'SYSTEM' as const}]
const notices = ref<NotificationItemResponse[]>([])
const loading = ref(false)
const loadError = ref('')
const filtered=computed(()=>active.value==='ALL'?notices.value:notices.value.filter((item)=>item.type===active.value))
async function loadNotifications() {
  loading.value = true
  loadError.value = ''
  try {
    notices.value = await listNotifications(active.value)
  } catch {
    notices.value = []
    loadError.value = '通知接口加载失败，未展示任何本地样例消息'
  } finally { loading.value = false }
}
function switchTab(type: NoticeType) {
  active.value = type
  void loadNotifications()
}
function iconFor(type: NotificationType) {
  return ({ ORDER: '📦', CHAT: '💬', AUDIT: '🛡️', SYSTEM: '🔔' } as Record<NotificationType, string>)[type] ?? '🔔'
}
function formatTime(value: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 16)
}
function isSafeNotificationTargetUrl(value?: string | null) {
  if (!value) return false
  return /^\/pages\/[A-Za-z0-9/_-]+\/index(?:\?[A-Za-z0-9%=&_.:-]+)?$/.test(value)
}
async function openNotice(item: NotificationItemResponse){
  try {
    const read = await markNotificationRead(item.notificationNo)
    notices.value = notices.value.map((notice) => notice.notificationNo === read.notificationNo ? read : notice)
  } catch {
    uni.showToast({ title: '后端已读接口调用失败，未执行本地已读变更', icon: 'none' })
    return
  }
  if (item.targetUrl && isSafeNotificationTargetUrl(item.targetUrl)) {
    uni.navigateTo({url:item.targetUrl})
  } else if (item.targetUrl) {
    uni.showToast({ title: '通知跳转地址无效，未打开页面', icon: 'none' })
  }
}
onMounted(() => { void loadNotifications() })
</script>
<style scoped>
.notice-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.notice-card,.empty-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:36rpx}.tab-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.chip.active{background:#3a2a1f;color:#fff;border-color:#3a2a1f}.notice-card{display:flex;gap:16rpx}.empty-card{color:#9b7560;font-size:24rpx;text-align:center}.notice-icon{width:76rpx;height:76rpx;border-radius:26rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:34rpx}.main{flex:1;min-width:0}.title-row{display:flex;justify-content:space-between;gap:12rpx}.title{color:#3a2a1f;font-size:27rpx;font-weight:950}.dot{padding:5rpx 10rpx;border-radius:999rpx;background:#ff7a45;color:#fff;font-size:18rpx}.desc{margin-top:8rpx;color:#7b5542;font-size:23rpx;line-height:1.45}.time{margin-top:8rpx;color:#b9856a;font-size:20rpx}
</style>
