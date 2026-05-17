<template>
  <view class="page-shell profile-page">
    <view class="profile-card ds-card">
      <view class="avatar tapable" :class="{ image: !!form.avatarUrl }" @click="chooseAvatar">
        <image v-if="form.avatarUrl" class="avatar-image" :src="form.avatarUrl" mode="aspectFill" />
        <text v-else>{{ avatarText }}</text>
        <view class="avatar-badge">换</view>
      </view>
      <view class="profile-main">
        <view class="nickname">{{ displayNickname }}</view>
        <view class="profile-desc">{{ genderSymbol }} {{ form.userNo || '小原圈号待生成' }}</view>
      </view>
    </view>

    <view class="form-card ds-card">
      <view class="form-title">编辑个人资料</view>
      <view class="avatar-entry tapable" @click="chooseAvatar">
        <view>
          <view class="field-label">头像</view>
          <view class="field-help">点击上传新头像，保存后同步到我的主页</view>
        </view>
        <text class="avatar-action">{{ uploadingAvatar ? '上传中' : '更换' }}</text>
      </view>
      <view class="form-field">
        <view class="field-label">昵称</view>
        <input :value="form.nickname" class="field" maxlength="16" placeholder="请输入昵称" confirm-type="next" @input="updateTextField('nickname', $event)" @blur="trimTextField('nickname')" />
      </view>
      <view class="form-field">
        <view class="field-label">小原圈号</view>
        <view class="id-row">
          <input :value="form.userNo || ''" class="field disabled id-field" disabled placeholder="小原圈号由系统生成" />
          <button class="id-btn" @click="showIdRule">改号</button>
        </view>
      </view>
      <view class="form-field">
        <view class="field-label">性别展示</view>
        <view class="gender-row">
          <view v-for="item in genders" :key="item.value" class="gender-chip tapable" :class="{ active: form.gender === item.value }" @click="form.gender = item.value">
            {{ item.label }}
          </view>
        </view>
      </view>
      <view class="form-field">
        <view class="field-label">所在城市</view>
        <input :value="form.city" class="field" maxlength="24" placeholder="填写城市/区域" confirm-type="next" @input="updateTextField('city', $event)" @blur="trimTextField('city')" />
      </view>
      <view class="form-field">
        <view class="field-label">个人简介</view>
        <textarea :value="form.bio" class="field bio-field" maxlength="60" placeholder="介绍一下你自己" @input="updateTextField('bio', $event)" @blur="trimTextField('bio')" />
      </view>
      <button class="primary-btn" :disabled="saving || uploadingAvatar" @click="saveProfile">{{ saving ? '保存中...' : '保存资料' }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, updateMyProfile } from '../../../api/modules/user'

const genders = [{ label: '♀ 女', value: 'goddess' }, { label: '♂ 男', value: 'god' }]
type TextFieldKey = 'nickname' | 'city' | 'bio'

const form = reactive({ userId: 0, userNo: '', avatarUrl: '', nickname: '', gender: 'goddess', mainRole: 'BUYER', city: '', bio: '', videoVerified: false })
const saving = ref(false)
const uploadingAvatar = ref(false)
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

function inputValue(event: unknown) {
  const target = event as { detail?: { value?: unknown } }
  const value = target.detail?.value
  return typeof value === 'string' ? value : ''
}

function updateTextField(field: TextFieldKey, event: unknown) {
  form[field] = inputValue(event)
}

function trimTextField(field: TextFieldKey) {
  form[field] = form[field].trim()
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
  if (uploadingAvatar.value) return
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
      uploadingAvatar.value = true
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
      } finally {
        uploadingAvatar.value = false
      }
    },
    fail() {
      showToast('未选择头像')
    }
  })
}

async function saveProfile() {
  if (saving.value || uploadingAvatar.value) return
  trimTextField('nickname')
  trimTextField('city')
  trimTextField('bio')
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
.avatar { position:relative; width:96rpx; height:96rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:40rpx; font-weight:950; overflow:hidden; }
.avatar.image { background:#fff; }
.avatar-image { width:100%; height:100%; display:block; }
.avatar-badge { position:absolute; right:0; bottom:0; width:34rpx; height:34rpx; border-radius:50%; background:#ff7a45; color:#fff; border:3rpx solid #fff; display:flex; align-items:center; justify-content:center; font-size:17rpx; font-weight:950; }
.profile-main { flex:1; min-width:0; }
.nickname { color:#3a2a1f; font-size:34rpx; font-weight:950; }
.profile-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; }
.form-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.form-field { margin-top:18rpx; }
.field-label { color:#7b5542; font-size:22rpx; font-weight:950; }
.field-help { margin-top:6rpx; color:#b9856a; font-size:20rpx; font-weight:700; }
.avatar-entry { margin-top:16rpx; min-height:86rpx; padding:0 20rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; display:flex; align-items:center; justify-content:space-between; gap:18rpx; color:#3a2a1f; }
.avatar-action { flex-shrink:0; padding:8rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:21rpx; font-weight:950; }
.field { box-sizing:border-box; width:100%; margin-top:8rpx; padding:20rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; color:#3a2a1f; font-size:26rpx; }
.bio-field { min-height:132rpx; line-height:1.45; }
.field.disabled { color:#b9856a; }
.id-row { display:flex; gap:12rpx; align-items:center; }
.id-field { flex:1; }
.id-btn { margin-top:8rpx; width:132rpx; height:76rpx; line-height:76rpx; border-radius:20rpx; background:#fff3e7; color:#ff7a45; font-size:24rpx; font-weight:900; }
.gender-row { margin-top:10rpx; display:flex; gap:12rpx; flex-wrap:wrap; }
.gender-chip { flex:1; padding:12rpx 18rpx; border-radius:999rpx; border:1rpx solid #ffd9bd; background:#fff; color:#9b7560; font-size:22rpx; font-weight:900; text-align:center; }
.gender-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.primary-btn { margin-top:24rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:27rpx; font-weight:950; }
.primary-btn[disabled] { opacity:.62; }
</style>
