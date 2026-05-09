<template>
  <view class="page-shell notice-page">
    <view class="hero ds-card"><view><view class="kicker">♡ 消息通知</view><view class="page-title">通知中心</view><view class="page-desc">订单、私信、审核、举报处理结果统一查看。</view></view><view class="hero-icon">🔔</view></view>
    <view class="tab-row"><view v-for="item in tabs" :key="item.value" class="chip tapable" :class="{ active: active === item.value }" @click="active = item.value">{{ item.label }}</view></view>
    <view v-if="loading" class="empty-card ds-card">通知加载中...</view>
    <view v-else-if="loadError" class="empty-card ds-card">{{ loadError }}</view>
    <view v-else-if="filtered.length === 0" class="empty-card ds-card">暂无通知</view>
    <view v-else class="notice-list">
      <view v-for="item in filtered" :key="item.id" class="notice-card ds-card tapable" @click="openNotice(item)">
        <view class="notice-icon">{{ item.icon }}</view>
        <view class="main"><view class="title-row"><text class="title">{{ item.title }}</text><text v-if="!item.read" class="dot">未读</text></view><view class="desc">{{ item.desc }}</view><view class="time">{{ item.time }}</view></view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
type NoticeType='all'|'order'|'chat'|'audit'|'system'
interface NoticeItem { id: string | number; type: Exclude<NoticeType, 'all'>; icon: string; title: string; desc: string; time: string; read: boolean; url: string }
const active=ref<NoticeType>('all')
const tabs=[{label:'全部',value:'all' as const},{label:'订单',value:'order' as const},{label:'私信',value:'chat' as const},{label:'审核',value:'audit' as const},{label:'系统',value:'system' as const}]
const notices = ref<NoticeItem[]>([])
const loading = ref(false)
const loadError = ref('通知接口暂未接入，未展示任何本地样例消息')
const filtered=computed(()=>active.value==='all'?notices.value:notices.value.filter((item)=>item.type===active.value))
function openNotice(item: NoticeItem){
  uni.showToast({ title: '未调用后端已读接口，未执行任何通知状态变更', icon: 'none' })
  if (item.url) uni.navigateTo({url:item.url})
}
</script>
<style scoped>
.notice-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.notice-card,.empty-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:36rpx}.tab-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.chip.active{background:#3a2a1f;color:#fff;border-color:#3a2a1f}.notice-card{display:flex;gap:16rpx}.empty-card{color:#9b7560;font-size:24rpx;text-align:center}.notice-icon{width:76rpx;height:76rpx;border-radius:26rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:34rpx}.main{flex:1;min-width:0}.title-row{display:flex;justify-content:space-between;gap:12rpx}.title{color:#3a2a1f;font-size:27rpx;font-weight:950}.dot{padding:5rpx 10rpx;border-radius:999rpx;background:#ff7a45;color:#fff;font-size:18rpx}.desc{margin-top:8rpx;color:#7b5542;font-size:23rpx;line-height:1.45}.time{margin-top:8rpx;color:#b9856a;font-size:20rpx}
</style>
