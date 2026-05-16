<template>
  <view class="page-shell profile-page">
    <view class="profile-card ds-card">
      <view class="avatar" :class="{ image: !!form.avatarUrl }">
        <image v-if="form.avatarUrl" class="avatar-image" :src="form.avatarUrl" mode="aspectFill" />
        <text v-else>{{ avatarText }}</text>
      </view>
      <view class="profile-main">
        <view class="nickname">{{ displayNickname }}</view>
        <view class="profile-desc">{{ genderSymbol }} {{ form.userNo || '小原圈号待生成' }}</view>
      </view>
    </view>

    <view class="form-card ds-card">
      <view class="avatar-entry tapable" @click="chooseAvatar">
        <text>头像</text>
        <text>›</text>
      </view>
      <input v-model.trim="form.nickname" class="field" maxlength="16" placeholder="昵称" />
      <view class="id-row">
        <input :value="form.userNo || ''" class="field disabled id-field" disabled placeholder="小原圈号" />
        <button class="id-btn" @click="showIdRule">改号</button>
      </view>
      <view class="gender-row">
        <view v-for="item in genders" :key="item.value" class="gender-chip tapable" :class="{ active: form.gender === item.value }" @click="form.gender = item.value">
          {{ item.label }}
        </view>
      </view>
      <input v-model.trim="form.city" class="field" maxlength="24" placeholder="所在城市" />
      <textarea v-model.trim="form.bio" class="field bio-field" maxlength="60" placeholder="个人简介" />
      <button class="primary-btn" :disabled="saving" @click="saveProfile">{{ saving ? '保存中...' : '保存' }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, updateMyProfile } from '../../../api/modules/user'

const genders = [{ label: '♀ 女', value: 'goddess' }, { label: '♂ 男', value: 'god' }]
const form = reactive({ userId: 0, userNo: '', avatarUrl: '', nickname: '', gender: 'goddess', mainRole: 'BUYER', city: '', bio: '', videoVerified: false })
const saving = ref(false)
const avatarChanged = ref(false)
const avatarText = computed(() => (form.nickname || '原').slice(0, 1))
const displayNickname = computed(() => form.nickname || '小原圈用户')
const genderSymbol = computed(() => form.gender === 'god' ? '♂' : '♀')

function showToast(title: string, icon: 'success' | 'none' = 'none') {
  uni.showToast({ title, icon })
}

function showIdRule() {
  showToast('暂不可改号')
}

function fileNameFromPath(path: string) {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'avatar.jpg'
  return last.includes('.') ? last : `${last}.jpg`
}

function imageContentType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}

function chooseAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    async success(result) {
      const path = result.tempFilePaths?.[0]
      if (!path) return
      if (path.startsWith('local://') || path.includes('placeholder')) {
        showToast('头像暂不可用')
        return
      }
      try {
        const ticket = await createMediaUploadTicket({
          scene: 'COMMUNITY_IMAGE',
          contentType: imageContentType(path),
          fileSize: 10_000_000,
          filename: fileNameFromPath(path)
        })
        const uploaded = await uploadMediaTicketFile(ticket, path)
        form.avatarUrl = uploaded.storageUrl
        avatarChanged.value = true
        showToast('头像已更新', 'success')
      } catch {
        showToast('头像暂不可用')
      }
    }
  })
}

async function saveProfile() {
  if (saving.value) return
  if (!form.nickname) { showToast('昵称不能为空'); return }
  saving.value = true
  try {
    const profile = await updateMyProfile({
      nickname: form.nickname,
      avatarUrl: avatarChanged.value ? form.avatarUrl : undefined,
      gender: form.gender,
      mainRole: form.videoVerified ? form.mainRole : 'BUYER',
      city: form.city,
      bio: form.bio
    })
    Object.assign(form, {
      userId: profile.userId,
      userNo: profile.userNo || '',
      avatarUrl: profile.avatarUrl || '',
      nickname: profile.nickname || '',
      gender: profile.gender || 'goddess',
      mainRole: profile.mainRole || 'BUYER',
      city: profile.city || '',
      bio: profile.bio || '',
      videoVerified: profile.videoVerified
    })
    avatarChanged.value = false
    showToast('已保存', 'success')
  } catch {
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

async function loadProfile() {
  try {
    const profile = await getMyProfile()
    Object.assign(form, {
      userId: profile.userId,
      userNo: profile.userNo || '',
      avatarUrl: profile.avatarUrl || '',
      nickname: profile.nickname || '',
      gender: profile.gender || 'goddess',
      mainRole: profile.videoVerified ? (profile.mainRole || 'BUYER') : 'BUYER',
      city: profile.city || '',
      bio: profile.bio || '',
      videoVerified: profile.videoVerified
    })
    avatarChanged.value = false
  } catch {
    showToast('资料暂不可用')
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.profile-card,.form-card { margin-top:22rpx; padding:24rpx; border-color:#ffd9bd; }
.profile-card { display:flex; gap:18rpx; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }
.avatar { width:96rpx; height:96rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:40rpx; font-weight:950; overflow:hidden; }
.avatar.image { background:#fff; }
.avatar-image { width:100%; height:100%; display:block; }
.profile-main { flex:1; min-width:0; }
.nickname { color:#3a2a1f; font-size:34rpx; font-weight:950; }
.profile-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; }
.avatar-entry { margin-top:18rpx; min-height:74rpx; padding:0 20rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; display:flex; align-items:center; justify-content:space-between; color:#3a2a1f; font-size:24rpx; font-weight:900; }
.field { box-sizing:border-box; width:100%; margin-top:18rpx; padding:20rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; color:#3a2a1f; font-size:26rpx; }
.bio-field { min-height:132rpx; line-height:1.45; }
.field.disabled { color:#b9856a; }
.id-row { display:flex; gap:12rpx; align-items:center; }
.id-field { flex:1; }
.id-btn { margin-top:18rpx; width:132rpx; height:76rpx; line-height:76rpx; border-radius:20rpx; background:#fff3e7; color:#ff7a45; font-size:24rpx; font-weight:900; }
.gender-row { margin-top:18rpx; display:flex; gap:12rpx; flex-wrap:wrap; }
.gender-chip { flex:1; padding:12rpx 18rpx; border-radius:999rpx; border:1rpx solid #ffd9bd; background:#fff; color:#9b7560; font-size:22rpx; font-weight:900; text-align:center; }
.gender-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.primary-btn { margin-top:24rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:27rpx; font-weight:950; }
</style>
