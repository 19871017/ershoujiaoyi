<template>
  <view class="page-shell profile-page">
    <GlobalTicker />

    <view v-if="loadingProfile" class="state-card ds-card">资料加载中...</view>
    <view v-else-if="profileError" class="state-card ds-card error-state">
      <view>{{ profileError }}</view>
      <button class="retry-btn" @click="loadProfile">重新加载</button>
    </view>

    <template v-else>
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
            <button class="id-btn" :disabled="changingUserNo" @click="openUserNoChange">改号</button>
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
    </template>

    <view v-if="userNoDialogVisible" class="dialog-mask">
      <view class="dialog-card ds-card">
        <view class="dialog-title">修改小原圈号</view>
        <view class="dialog-desc">仅支持修改一次；5-20 位，需以字母开头，可包含字母、数字和下划线。</view>
        <input :value="userNoDraft" class="field" maxlength="20" placeholder="例如 Circle_2026" confirm-type="done" @input="updateUserNoDraft" @blur="trimUserNoDraft" />
        <view class="dialog-actions">
          <button class="dialog-btn secondary" :disabled="changingUserNo" @click="closeUserNoChange">取消</button>
          <button class="dialog-btn primary" :disabled="changingUserNo" @click="saveUserNo">{{ changingUserNo ? '提交中...' : '确认改号' }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, updateMyProfile, updateMyUserNo, type UserProfileResponse } from '../../../api/modules/user'
import GlobalTicker from '../../../components/GlobalTicker.vue'

const genders = [{ label: '♀ 女', value: 'goddess' }, { label: '♂ 男', value: 'god' }]
type TextFieldKey = 'nickname' | 'city' | 'bio'

const form = reactive({ userId: 0, userNo: '', avatarUrl: '', nickname: '', gender: 'goddess', mainRole: 'BUYER', city: '', bio: '', videoVerified: false })
const loadingProfile = ref(true)
const profileError = ref('')
const saving = ref(false)
const uploadingAvatar = ref(false)
const avatarChanged = ref(false)
const changingUserNo = ref(false)
const userNoDialogVisible = ref(false)
const userNoDraft = ref('')
const avatarText = computed(() => (form.nickname || '原').slice(0, 1))
const displayNickname = computed(() => form.nickname || '小原圈用户')
const genderSymbol = computed(() => form.gender === 'god' ? '♂' : '♀')
const canEditProfile = computed(() => !loadingProfile.value && !profileError.value)

function showToast(title: string, icon: 'success' | 'none' = 'none') {
  uni.showToast({ title, icon })
}

function applyProfile(profile: UserProfileResponse) {
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

function openUserNoChange() {
  if (!canEditProfile.value) return
  userNoDraft.value = form.userNo || ''
  userNoDialogVisible.value = true
}

function closeUserNoChange() {
  if (changingUserNo.value) return
  userNoDialogVisible.value = false
}

function updateUserNoDraft(event: unknown) {
  userNoDraft.value = inputValue(event)
}

function trimUserNoDraft() {
  userNoDraft.value = userNoDraft.value.trim()
}

async function saveUserNo() {
  if (changingUserNo.value) return
  trimUserNoDraft()
  if (!/^[A-Za-z][A-Za-z0-9_]{4,19}$/.test(userNoDraft.value)) {
    showToast('小原圈号格式不正确')
    return
  }
  if (userNoDraft.value === form.userNo) {
    userNoDialogVisible.value = false
    return
  }
  changingUserNo.value = true
  try {
    const profile = await updateMyUserNo({ userNo: userNoDraft.value })
    applyProfile(profile)
    avatarChanged.value = false
    userNoDialogVisible.value = false
    showToast('小原圈号已更新', 'success')
  } catch (error) {
    console.warn('userNo change failed', error)
    showToast('改号失败，请检查是否已改过或被占用')
  } finally {
    changingUserNo.value = false
  }
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
  if (!canEditProfile.value || uploadingAvatar.value) return
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
        showToast('头像已上传，保存后生效', 'success')
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
  if (!canEditProfile.value || saving.value || uploadingAvatar.value) return
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
    applyProfile(profile)
    avatarChanged.value = false
    showToast('已保存', 'success')
  } catch {
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

async function loadProfile() {
  loadingProfile.value = true
  profileError.value = ''
  try {
    const profile = await getMyProfile()
    applyProfile(profile)
    avatarChanged.value = false
  } catch {
    profileError.value = '资料暂不可用，请稍后重试'
    showToast('资料暂不可用')
  } finally {
    loadingProfile.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.profile-page button::after { border:0; }
.state-card { margin-top:18rpx; padding:28rpx; border-color:#ffd9bd; color:#9b7560; font-size:24rpx; font-weight:900; text-align:center; background:#fffaf6; }
.error-state { color:#b45374; }
.retry-btn { margin-top:18rpx; width:220rpx; height:70rpx; line-height:70rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:23rpx; font-weight:950; }
.profile-card,.form-card { margin-top:18rpx; padding:24rpx; border-color:#ffd9bd; box-shadow:0 14rpx 30rpx rgba(255,122,69,.10); }
.profile-card { display:flex; gap:18rpx; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }
.avatar { position:relative; width:104rpx; height:104rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:42rpx; font-weight:950; overflow:hidden; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); }
.avatar.image { background:#fff; }
.avatar-image { width:100%; height:100%; display:block; }
.avatar-badge { position:absolute; right:0; bottom:0; width:36rpx; height:36rpx; border-radius:50%; background:#ff7a45; color:#fff; border:3rpx solid #fff; display:flex; align-items:center; justify-content:center; font-size:17rpx; font-weight:950; }
.profile-main { flex:1; min-width:0; }
.nickname { color:#3a2a1f; font-size:34rpx; font-weight:950; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.profile-desc { margin-top:8rpx; color:#9b7560; font-size:22rpx; font-weight:800; }
.form-card { background:rgba(255,255,255,.96); }
.form-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.form-field { margin-top:18rpx; }
.field-label { color:#7b5542; font-size:22rpx; font-weight:950; }
.field-help { margin-top:6rpx; color:#b9856a; font-size:20rpx; font-weight:700; line-height:1.35; }
.avatar-entry { margin-top:16rpx; min-height:88rpx; padding:0 20rpx; border-radius:22rpx; border:1rpx solid #ffd9bd; background:#fffaf6; display:flex; align-items:center; justify-content:space-between; gap:18rpx; color:#3a2a1f; }
.avatar-action { flex-shrink:0; padding:8rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:21rpx; font-weight:950; }
.field { box-sizing:border-box; width:100%; min-height:78rpx; margin-top:9rpx; padding:18rpx 20rpx; border-radius:20rpx; border:1rpx solid #ffd9bd; background:#fffaf6; color:#3a2a1f; font-size:25rpx; line-height:1.3; }
.bio-field { min-height:142rpx; line-height:1.45; }
.field.disabled { color:#b9856a; background:#fff7ef; }
.id-row { display:flex; gap:12rpx; align-items:center; }
.id-field { flex:1; min-width:0; }
.id-btn { margin-top:9rpx; width:124rpx; height:78rpx; line-height:78rpx; border-radius:20rpx; background:#fff3e7; color:#ff7a45; font-size:23rpx; font-weight:900; }
.dialog-mask { position:fixed; inset:0; z-index:1000; padding:32rpx; display:flex; align-items:center; justify-content:center; background:rgba(58,42,31,.42); box-sizing:border-box; }
.dialog-card { width:100%; padding:28rpx; border-color:#ffd9bd; background:#fffdfa; }
.dialog-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.dialog-desc { margin-top:10rpx; color:#9b7560; font-size:22rpx; line-height:1.5; }
.dialog-actions { margin-top:22rpx; display:flex; gap:14rpx; }
.dialog-btn { flex:1; height:76rpx; line-height:76rpx; border-radius:999rpx; font-size:24rpx; font-weight:950; }
.dialog-btn.secondary { background:#fff3e7; color:#9b7560; }
.dialog-btn.primary { background:#ff7a45; color:#fff; }
.gender-row { margin-top:10rpx; display:flex; gap:12rpx; flex-wrap:wrap; }
.gender-chip { flex:1; min-width:180rpx; padding:14rpx 18rpx; border-radius:999rpx; border:1rpx solid #ffd9bd; background:#fff; color:#9b7560; font-size:22rpx; font-weight:900; text-align:center; }
.gender-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; box-shadow:0 8rpx 18rpx rgba(255,122,69,.16); }
.primary-btn { margin-top:26rpx; height:82rpx; line-height:82rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:27rpx; font-weight:950; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); }
.primary-btn[disabled] { opacity:.62; }
</style>
