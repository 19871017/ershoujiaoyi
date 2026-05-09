<template>
  <view class="page-shell public-profile">
    <view v-if="profile.videoVerified" class="video-verify-card ds-card">
      <view class="verify-left">
        <view class="video-icon">▶</view>
        <view>
          <view class="video-title">视频认证卖家</view>
          <view class="video-desc">真人视频核验已通过，头像、本人和卖家身份已由平台审核。</view>
        </view>
      </view>
      <view class="video-badge">已核验</view>
    </view>

    <view class="hero ds-card">
      <view class="avatar">{{ avatarText }}</view>
      <view class="main">
        <view class="name-row">
          <text class="name">{{ profile.nickname }}</text>
          <text class="verify">{{ profile.mainRole === 'VERIFIED' ? '已实名' : '待实名' }}</text>
          <text v-if="profile.videoVerified" class="verify video">视频认证</text>
        </view>
        <view class="bio">{{ profile.videoVerified ? '已通过真人视频核验的小原圈卖家' : '小原圈用户，交易请以平台订单、支付和售后状态为准。' }}</view>
        <view class="tag-row">
          <text v-if="profile.videoVerified" class="tag">视频认证卖家</text>
          <text class="tag neutral">交易请以平台订单与聊天留痕为准</text>
        </view>
      </view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">{{ loadError }}</view>

    <view class="stat-grid">
      <view v-for="item in stats" :key="item.label" class="stat-card ds-card"><view class="stat-value">{{ item.value }}</view><view class="stat-label">{{ item.label }}</view></view>
    </view>

    <view class="action-row">
      <button class="secondary-btn" @click="toggleFollow">{{ followed ? '已关注' : '关注' }}</button>
      <button class="primary-btn" @click="chat">私信</button>
      <button class="gift-btn" @click="openGift">送礼物</button>
      <button class="report-btn" @click="report">举报</button>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">在售宝贝</view>
      <view class="empty-row" @click="openProductUnavailable">卖家商品接口尚未接入，未展示本地商品样例</view>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">交易安全</view>
      <view class="desc">视频认证仅代表平台完成真人核验；如涉及交易，请以平台订单、支付、售后状态和服务端聊天记录为准。</view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { followPublicProfile, getPublicProfile, type UserProfileResponse } from '../../../api/modules/user'
const userId = ref('')
const loadError = ref('')
const profile = reactive<UserProfileResponse>({ userId: 0, nickname: '小原圈用户', mainRole: 'UNVERIFIED', videoIdentityStatus: 'UNVERIFIED', videoVerified: false, followedByMe: false })
const followed = computed(() => profile.followedByMe === true)
const avatarText = computed(() => (profile.nickname || '原').slice(-1))
const stats = computed(() => [] as { label: string; value: string | number }[])
const products = computed(() => [] as never[])
void products
function readQuery(){const pages=getCurrentPages(); const current=pages.length?pages[pages.length-1] as unknown as {options?:Record<string,string>}:undefined; const hash=typeof window!=='undefined'?new URLSearchParams(window.location.hash.split('?')[1]||''):undefined; userId.value=current?.options?.userId||hash?.get('userId')||userId.value}
function isValidBackendUserId(value: string) { return /^[1-9]\d*$/.test(value) }
async function loadProfile(){
  if (!isValidBackendUserId(userId.value)) { loadError.value = '卖家数据暂时不可用，未展示本地卖家样例'; return }
  try{const data=await getPublicProfile(userId.value); Object.assign(profile,data); loadError.value = ''}
  catch(e){loadError.value = '卖家数据暂时不可用，未展示本地卖家样例'; uni.showToast({title:'卖家资料暂时不可用',icon:'none'})}
}
async function toggleFollow(){
  if (!isValidBackendUserId(userId.value)) { uni.showToast({title:'缺少真实用户ID，未执行任何关注变更',icon:'none'}); return }
  if (followed.value) { uni.showToast({title:'已关注状态来自后端，暂未接入取消关注',icon:'none'}); return }
  try { const data = await followPublicProfile(userId.value); Object.assign(profile, data); uni.showToast({title:'关注状态已同步',icon:'none'}) }
  catch { uni.showToast({title:'关注没有提交成功，未执行本地关注变更',icon:'none'}) }
}
function chat(){const validUserId = isValidBackendUserId(userId.value); if(!validUserId){uni.showToast({title:'缺少真实用户ID，未进入私信',icon:'none'});return} uni.navigateTo({url:`/pages/chat/conversation/index?receiverId=${userId.value}`})}
function openGift(){const validUserId = isValidBackendUserId(userId.value); if(!validUserId){uni.showToast({title:'缺少真实用户ID，未进入送礼',icon:'none'});return} uni.navigateTo({url:`/pages/gift/index?mode=send&receiverId=${userId.value}&sceneType=PROFILE&sceneId=${userId.value}`})}
function report(){const validUserId = isValidBackendUserId(userId.value); if(!validUserId){uni.showToast({title:'缺少真实用户ID，未进入举报',icon:'none'});return} uni.navigateTo({url:`/pages/report/submit/index?targetType=USER&targetId=${userId.value}`})}
function openProductUnavailable(){uni.showToast({title:'在售商品接口尚未接入，未打开本地样例商品',icon:'none'})}
onMounted(()=>{readQuery(); loadProfile()})
</script>
<style scoped>
.public-profile{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.video-verify-card{margin-top:18rpx;padding:22rpx;border-color:#ffb37c;background:linear-gradient(135deg,#fff2e4,#fffaf6);display:flex;align-items:center;justify-content:space-between;gap:16rpx;box-shadow:0 16rpx 34rpx rgba(255,122,69,.14)}.verify-left{display:flex;align-items:center;gap:16rpx;min-width:0}.video-icon{width:72rpx;height:72rpx;border-radius:50%;background:linear-gradient(135deg,#ff7a45,#ff3f8d);color:#fff;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:950;box-shadow:0 10rpx 24rpx rgba(255,63,141,.18)}.video-title{color:#3a2a1f;font-size:30rpx;font-weight:950}.video-desc{margin-top:6rpx;color:#8a5f48;font-size:22rpx;line-height:1.45}.video-badge{flex-shrink:0;padding:9rpx 14rpx;border-radius:999rpx;background:#fff;color:#ff3f8d;font-size:20rpx;font-weight:950}.hero,.stat-card,.section-card,.safe-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;gap:18rpx;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.avatar{width:104rpx;height:104rpx;border-radius:50%;background:linear-gradient(135deg,#ff7a45,#ffb08a);color:#fff;display:flex;align-items:center;justify-content:center;font-size:42rpx;font-weight:950}.main{flex:1;min-width:0}.name-row{display:flex;align-items:center;gap:10rpx;flex-wrap:wrap}.name{color:#3a2a1f;font-size:33rpx;font-weight:950}.verify,.tag{padding:7rpx 12rpx;border-radius:999rpx;background:#fff;color:#ff7a45;font-size:19rpx;font-weight:900}.verify.video{background:#ff7a45;color:#fff}.bio{margin-top:8rpx;color:#7b5542;font-size:23rpx;line-height:1.45}.tag-row{margin-top:10rpx;display:flex;gap:8rpx;flex-wrap:wrap}.stat-grid{margin-top:16rpx;display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx}.stat-card{text-align:center}.stat-value{color:#ff3f8d;font-size:31rpx;font-weight:950}.stat-label{margin-top:5rpx;color:#9b7560;font-size:21rpx}.action-row{margin-top:18rpx;display:grid;grid-template-columns:repeat(4,1fr);gap:10rpx}.primary-btn,.secondary-btn,.gift-btn,.report-btn{height:78rpx;border-radius:999rpx;border:0;font-size:26rpx;font-weight:950}.primary-btn{background:linear-gradient(135deg,#ff7a45,#ff9f7a);color:#fff}.secondary-btn{background:#fff2e8;color:#ff7a45}.gift-btn{background:#fff7ed;color:#f97316;border:1rpx solid #fed7aa}.report-btn{background:#fff;color:#9b7560;border:1rpx solid #ffd9bd}.section-title{color:#3a2a1f;font-size:28rpx;font-weight:950}.product-row{margin-top:16rpx;display:flex;align-items:center;gap:14rpx}.cover{width:82rpx;height:82rpx;border-radius:22rpx;background:#fff1e5;display:flex;align-items:center;justify-content:center;font-size:34rpx}.product-main{flex:1;min-width:0}.product-title{color:#3a2a1f;font-size:25rpx;font-weight:900}.product-meta,.desc{margin-top:6rpx;color:#8a5f48;font-size:22rpx;line-height:1.45}.price{color:#ff3f8d;font-size:27rpx;font-weight:950}
</style>
