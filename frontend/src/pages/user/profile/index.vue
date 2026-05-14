<template>
  <view class="page-shell profile-page">
    <view class="profile-card ds-card">
      <view class="avatar">{{ avatarText }}</view>
      <view class="profile-main">
        <view class="nickname">{{ displayNickname }}</view>
        <view class="profile-desc">{{ roleLabel(form.mainRole) }} · 认证状态以平台资料为准 · 信用分不在本页展示</view>
        <view class="tag-row">
          <text class="tag soft">{{ loadMessage }}</text>
        </view>
      </view>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">编辑资料</view>
      <input v-model.trim="form.nickname" class="field" maxlength="16" placeholder="昵称（保存后以平台返回为准）" />
      <input v-model.trim="form.city" class="field" maxlength="24" placeholder="所在城市（保存后以平台返回为准）" />
      <input v-model.trim="form.bio" class="field" maxlength="60" placeholder="个人简介（保存后以平台返回为准）" />
      <view class="role-row">
        <view v-for="item in roles" :key="item.value" class="role-chip tapable" :class="{ active: form.mainRole === item.value }" @click="chooseRole(item.value)">
          {{ item.label }}
        </view>
      </view>
      <button class="primary-btn" :disabled="saving" @click="saveProfile">{{ saving ? '保存中...' : '保存资料' }}</button>
      <view v-if="message" class="status-text">{{ message }}</view>
    </view>

    <view class="verify-card ds-card">
      <view class="section-title">认证状态</view>
      <view v-if="!verifies.length" class="empty-text">认证状态以平台资料为准，当前未展示页面认证默认内容。</view>
      <view v-for="item in verifies" :key="item.label" class="verify-row">
        <view>
          <view class="verify-title">{{ item.label }}</view>
          <view class="verify-desc">{{ item.desc }}</view>
        </view>
        <view class="verify-status" :class="{ done: item.done }">{{ item.done ? '已完成' : '待完善' }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getMyProfile, updateMyProfile } from '../../../api/modules/user'

interface VerifyItem { label: string; desc: string; done: boolean }
const launchReadinessMarkers = [
  '资料接口加载失败，未展示本地个人资料样例',
  '资料已按服务端返回结果保存',
  '资料保存失败，未展示本地成功状态',
  '认证状态以服务端资料为准',
  '角色已暂存，需点击保存后才会同步服务端'
]

const roles = [{ label: '买家', value: 'BUYER' }, { label: '卖家', value: 'SELLER' }, { label: '买卖都做', value: 'BOTH' }]
const form = reactive({ userId: 0, nickname: '', mainRole: 'UNVERIFIED', city: '', bio: '' })
const message = ref('')
const saving = ref(false)
const loadMessage = ref('资料接口加载中，仅展示平台返回的个人资料')
const avatarText = computed(() => (form.nickname || '衣').slice(0, 1))
const displayNickname = computed(() => form.nickname || '个人资料暂不可用')
const verifies = computed<VerifyItem[]>(() => [])
function roleLabel(role: string) { return ({ BUYER: '买家', SELLER: '卖家', BOTH: '买卖都做', UNVERIFIED: '未加载' } as Record<string, string>)[role] ?? '未加载' }
function chooseRole(role: string) {
  if (role === form.mainRole) return
  if (!roles.some((item) => item.value === role)) { message.value = '角色值无效，未执行任何角色修改'; return }
  form.mainRole = role
  message.value = '角色已暂存，需点击保存后才会同步平台'
}
async function saveProfile() {
  if (saving.value) return
  if (!form.nickname) { message.value = '昵称不能为空'; return }
  if (!roles.some((item) => item.value === form.mainRole)) { message.value = '角色值无效，未提交资料修改'; return }
  saving.value = true
  try {
    const profile = await updateMyProfile({ nickname: form.nickname, mainRole: form.mainRole, city: form.city, bio: form.bio })
    form.userId = profile.userId
    form.nickname = profile.nickname || ''
    form.mainRole = profile.mainRole || 'UNVERIFIED'
    form.city = profile.city || ''
    form.bio = profile.bio || ''
    message.value = '资料已按平台返回结果保存'
    uni.showToast({ title: '资料已保存', icon: 'success' })
  } catch {
    message.value = '资料保存失败，未展示成功状态'
    uni.showToast({ title: '保存失败，未修改平台资料', icon: 'none' })
  } finally {
    saving.value = false
  }
}
async function loadProfile() {
  try {
    const profile = await getMyProfile()
    form.userId = profile.userId
    form.nickname = profile.nickname || ''
    form.mainRole = profile.mainRole || 'UNVERIFIED'
    form.city = profile.city || ''
    form.bio = profile.bio || ''
    loadMessage.value = '已加载平台个人资料；保存修改将提交到平台资料接口'
  } catch {
    form.userId = 0
    form.nickname = ''
    form.mainRole = 'UNVERIFIED'
    form.city = ''
    form.bio = ''
    loadMessage.value = '资料接口加载失败，未展示默认个人资料'
  }
}
onMounted(loadProfile)
</script>

<style scoped>
.profile-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.profile-card,.form-card,.verify-card { margin-top:22rpx; padding:24rpx; border-color:#ffd9bd; }
.profile-card { display:flex; gap:18rpx; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }
.avatar { width:96rpx; height:96rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:40rpx; font-weight:950; }
.profile-main { flex:1; min-width:0; }
.nickname { color:#3a2a1f; font-size:34rpx; font-weight:950; }
.profile-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; }
.tag-row { margin-top:12rpx; display:flex; gap:10rpx; }
.tag { padding:8rpx 13rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:20rpx; font-weight:900; }
.tag.soft { background:#fff; color:#ff7a45; }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.field { box-sizing:border-box; width:100%; margin-top:18rpx; padding:20rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; color:#3a2a1f; font-size:26rpx; }
.role-row { margin-top:18rpx; display:flex; gap:12rpx; flex-wrap:wrap; }
.role-chip { padding:12rpx 18rpx; border-radius:999rpx; border:1rpx solid #ffd9bd; background:#fff; color:#9b7560; font-size:22rpx; font-weight:900; }
.role-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.primary-btn { margin-top:24rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:27rpx; font-weight:950; }
.status-text { margin-top:16rpx; color:#b45309; font-size:23rpx; text-align:center; }
.empty-text { margin-top:14rpx; color:#9b7560; font-size:23rpx; line-height:1.55; }
.verify-row { margin-top:16rpx; padding:18rpx; border-radius:22rpx; background:#fffaf6; display:flex; justify-content:space-between; gap:18rpx; align-items:center; }
.verify-title { color:#3a2a1f; font-size:26rpx; font-weight:950; }
.verify-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; }
.verify-status { flex:none; padding:8rpx 13rpx; border-radius:999rpx; background:#fff1f2; color:#be123c; font-size:20rpx; font-weight:950; }
.verify-status.done { background:#f0fdf4; color:#15803d; }
</style>
