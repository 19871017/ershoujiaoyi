<template>
  <view class="page-shell city-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 城市偏好</view>
        <view class="page-title">选择城市</view>
        <view class="page-desc">城市偏好会写入平台个人资料；正式位置状态以平台或系统定位记录为准。</view>
      </view>
      <view class="hero-icon">📍</view>
    </view>

    <view class="search ds-card">
      <text>🔎</text>
      <input v-model.trim="keyword" placeholder="搜索城市，如 深圳" />
    </view>

    <view class="section-card ds-card">
      <view class="section-title">热门城市</view>
      <view class="city-grid">
        <view v-for="city in filteredCities" :key="city" class="city-chip tapable" :class="{ active: selected === city }" @click="selectCity(city)">{{ city }}</view>
      </view>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">位置使用提醒</view>
      <view class="desc">城市偏好写入平台资料后可用于本次页面筛选；订单、支付、发货和售后状态以平台记录为准。</view>
    </view>

    <button class="primary-btn" :disabled="!canConfirm || saving" @click="saveCity">{{ saving ? '保存中...' : '确认城市' }}</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getMyProfile, updateMyProfile } from '../../../api/modules/user'

const keyword = ref('')
const selected = ref('')
const saving = ref(false)
const profile = reactive({ userId: 0, nickname: '', mainRole: 'UNVERIFIED', city: '', bio: '' })
const cities = ['深圳', '广州', '杭州', '成都', '上海', '北京', '南京', '武汉', '长沙', '厦门', '重庆', '苏州']
const filteredCities = computed(() => keyword.value ? cities.filter((item) => item.includes(keyword.value)) : cities)
const canConfirm = computed(() => Boolean(selected.value))
function selectCity(city: string) {
  selected.value = city
}
async function loadProfile() {
  try {
    Object.assign(profile, await getMyProfile())
    selected.value = profile.city || ''
  } catch {
    Object.assign(profile, { userId: 0, nickname: '', mainRole: 'UNVERIFIED', city: '', bio: '' })
    selected.value = ''
    uni.showToast({ title: '资料接口加载失败，未展示页面城市偏好默认内容', icon: 'none' })
  }
}
async function saveCity() {
  if (!canConfirm.value || saving.value) {
    return
  }
  saving.value = true
  try {
    const updated = await updateMyProfile({ nickname: profile.nickname, mainRole: profile.mainRole, city: selected.value, bio: profile.bio })
    Object.assign(profile, updated)
    selected.value = updated.city || ''
    uni.showToast({ title: '城市偏好已保存至平台资料', icon: 'none' })
  } catch {
    uni.showToast({ title: '城市偏好保存失败，未修改平台资料', icon: 'none' })
  } finally {
    saving.value = false
  }
}
onMounted(loadProfile)
</script>
<style scoped>
.city-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.search,.section-card,.safe-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.search{display:flex;align-items:center;gap:14rpx}.search input{flex:1;color:#3a2a1f;font-size:25rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.city-grid{margin-top:18rpx;display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx}.city-chip{height:66rpx;border-radius:999rpx;background:#fffaf6;border:1rpx solid #ffd9bd;color:#7b5542;display:flex;align-items:center;justify-content:center;font-size:23rpx;font-weight:900}.city-chip.active{background:#ff7a45;color:#fff;border-color:#ff7a45}.desc{margin-top:10rpx;color:#9b7560;font-size:23rpx;line-height:1.55}.primary-btn{margin-top:22rpx}
</style>
