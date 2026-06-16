<template>
  <view class="page-shell profile-page">
    <view v-if="loadingProfile" class="state-card ds-card">资料加载中...</view>
    <view v-else-if="profileError" class="state-card ds-card error-state">
      <view>{{ profileError }}</view>
      <button class="retry-btn" @click="loadProfile">重新加载</button>
    </view>

    <template v-else>
      <view class="profile-card ds-card">
        <view class="avatar tapable" :class="{ image: !!displayAvatarUrl }" @click="chooseAvatar">
          <image v-if="displayAvatarUrl" class="avatar-image" :src="displayAvatarUrl" mode="aspectFill" />
          <text v-else>{{ avatarText }}</text>
          <view class="avatar-badge">换</view>
        </view>
        <view class="profile-main">
          <view class="nickname">{{ displayNickname }}</view>
          <view class="profile-desc">{{ genderSymbol }} {{ form.userNo || '小原圈号待生成' }}</view>
          <view class="profile-hint">{{ uploadingAvatar ? '头像上传中...' : '点头像更换，保存后生效' }}</view>
        </view>
      </view>

      <view class="form-card ds-card">
        <view class="form-title">编辑个人资料</view>
        <view class="form-subtitle">头像与照片秀会先完成平台上传票据校验，保存后同步到公开主页。</view>
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
        <view v-if="hasApprovedVideoIdentity" class="form-field">
          <view class="field-label">商家照片秀</view>
          <view class="showcase-hint">最多 6 张，使用平台上传票据保存；公开主页只展示已审核认证商家的照片秀。</view>
          <view class="showcase-grid">
            <view v-for="(url, index) in uploadedShowcaseUrls" :key="url" class="showcase-tile">
              <image class="showcase-image" :src="url" mode="aspectFill" />
              <view class="remove-photo tapable" @click="removeShowcasePhoto(index)">×</view>
            </view>
            <view v-if="uploadedShowcaseUrls.length < MAX_SHOWCASE_PHOTOS" class="showcase-add tapable" @click="chooseShowcasePhotos">
              <view class="showcase-plus">＋</view>
              <view>{{ uploadingShowcase ? '上传中' : '添加照片' }}</view>
            </view>
          </view>
        </view>
        <view v-else class="seller-photo-tip">视频认证审核通过后，可上传商家照片秀并展示在公开个人主页。</view>
        <button class="primary-btn" :disabled="saving || uploadingAvatar || uploadingShowcase" @click="saveProfile">{{ saving ? '保存中...' : '保存资料' }}</button>
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
import { getMyProfile, updateMyProfile, updateMyUserNo, type UpdateUserProfileRequest, type UserProfileResponse } from '../../../api/modules/user'
import { avatarUrlWithGenderFallback } from '../../../utils/default-avatar'
import {
  MAX_SHOWCASE_PHOTOS,
  assertBackendProfile,
  assertProfileSaveResponse,
  assertUserNoResponse,
  filterStoredShowcaseUrls,
  genders,
  imageFilesFromChooseResult,
  isValidCommunityImageUrl,
  storedAvatarUrl,
  uploadedCommunityImageUrl,
  uploadedCommunityImageUrls,
  type ChosenImageFile
} from './profile-helpers'

type TextFieldKey = 'nickname' | 'city' | 'bio'

const form = reactive({ userId: 0, userNo: '', avatarUrl: '', nickname: '', mainRole: 'BUYER', gender: 'goddess', city: '', bio: '', videoIdentityStatus: 'UNVERIFIED', videoVerified: false, showcaseImageUrls: [] as string[] })
const loadingProfile = ref(true)
const profileError = ref('')
const saving = ref(false)
const uploadingAvatar = ref(false)
const uploadingShowcase = ref(false)
const avatarChanged = ref(false)
const showcaseChanged = ref(false)
const changingUserNo = ref(false)
const userNoDialogVisible = ref(false)
const userNoDraft = ref('')
const avatarText = computed(() => (form.nickname || '原').slice(0, 1))
const displayAvatarUrl = computed(() => avatarUrlWithGenderFallback(form.avatarUrl, form.gender))
const displayNickname = computed(() => form.nickname || '小原圈用户')
const genderSymbol = computed(() => form.gender === 'god' ? '♂' : '♀')
const canEditProfile = computed(() => !loadingProfile.value && !profileError.value)
const hasApprovedVideoIdentity = computed(() => form.videoVerified && form.videoIdentityStatus === 'APPROVED')
const uploadedShowcaseUrls = computed(() => form.showcaseImageUrls.filter((url) => isValidCommunityImageUrl(url)))

function showToast(title: string, icon: 'success' | 'none' = 'none') {
  uni.showToast({ title, icon })
}

function assertSameProfile(profile: UserProfileResponse, action: string): void {
  if (form.userId > 0 && profile.userId !== form.userId) {
    console.warn('profile response userId mismatch', { action, currentUserId: form.userId, responseUserId: profile.userId })
    throw new Error('资料响应异常，请重新加载后再试')
  }
}

function applyProfile(profile: UserProfileResponse) {
  const approvedVideoIdentity = profile.videoVerified && profile.videoIdentityStatus === 'APPROVED'
  Object.assign(form, {
    userId: profile.userId,
    userNo: profile.userNo || '',
    avatarUrl: storedAvatarUrl(profile.avatarUrl || ''),
    nickname: profile.nickname || '',
    mainRole: approvedVideoIdentity ? (profile.mainRole || 'BUYER') : 'BUYER',
    gender: profile.gender || 'goddess',
    city: profile.city || '',
    bio: profile.bio || '',
    videoIdentityStatus: profile.videoIdentityStatus,
    videoVerified: profile.videoVerified,
    showcaseImageUrls: approvedVideoIdentity ? filterStoredShowcaseUrls(profile.showcaseImageUrls || []) : []
  })
}

function inputValue(field: TextFieldKey | 'userNo', event: unknown): string | undefined {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  if (typeof value !== 'string') {
    console.warn('profile input event invalid', { field })
    showToast('输入内容读取失败，请重新输入')
    return undefined
  }
  return value
}

function updateTextField(field: TextFieldKey, event: unknown) {
  const value = inputValue(field, event)
  if (value === undefined) return
  form[field] = value
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
  const value = inputValue('userNo', event)
  if (value === undefined) return
  userNoDraft.value = value
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
    assertBackendProfile(profile)
    assertSameProfile(profile, 'userNo')
    assertUserNoResponse(profile, userNoDraft.value)
    applyProfile(profile)
    avatarChanged.value = false
    showcaseChanged.value = false
    userNoDialogVisible.value = false
    showToast('小原圈号已更新', 'success')
  } catch (error) {
    console.warn('userNo change failed', { userNo: userNoDraft.value, error })
    showToast(error instanceof Error ? error.message : '改号失败，请稍后重试')
  } finally {
    changingUserNo.value = false
  }
}

async function uploadCommunityImage(imageFile: ChosenImageFile, purpose: 'avatar' | 'showcase') {
  const path = imageFile.path
  const ticket = await createMediaUploadTicket({
    scene: 'COMMUNITY_IMAGE',
    contentType: imageFile.contentType,
    fileSize: imageFile.fileSize,
    filename: imageFile.filename
  })
  const uploaded = await uploadMediaTicketFile(ticket, path)
  return uploadedCommunityImageUrl(uploaded.storageUrl, purpose)
}

function chooseAvatar() {
  if (!canEditProfile.value || uploadingAvatar.value) return
  uploadingAvatar.value = true
  try {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      async success(result) {
        const avatarFile = imageFilesFromChooseResult(result, 'avatar')[0]
        if (!avatarFile) {
          uploadingAvatar.value = false
          showToast('头像暂不可用')
          return
        }
        try {
          form.avatarUrl = await uploadCommunityImage(avatarFile, 'avatar')
          avatarChanged.value = true
          showToast('头像已上传，保存后生效', 'success')
        } catch (error) {
          console.warn('profile avatar upload failed', { filename: avatarFile.filename, contentType: avatarFile.contentType, fileSize: avatarFile.fileSize, error })
          showToast(error instanceof Error ? error.message : '头像暂不可用')
        } finally {
          uploadingAvatar.value = false
        }
      },
      fail(error: unknown) {
        uploadingAvatar.value = false
        const message = String((error as { errMsg?: string })?.errMsg || '')
        console.warn('profile avatar picker failed', { error })
        showToast(message.includes('cancel') ? '未选择头像' : '无法打开头像选择器，请检查相册或相机权限后重试')
      }
    })
  } catch (error) {
    uploadingAvatar.value = false
    console.warn('profile avatar picker failed', { error })
    showToast('无法打开头像选择器，请检查相册或相机权限后重试')
  }
}

function chooseShowcasePhotos() {
  if (!canEditProfile.value || uploadingShowcase.value || !hasApprovedVideoIdentity.value) return
  const remaining = MAX_SHOWCASE_PHOTOS - uploadedShowcaseUrls.value.length
  if (remaining <= 0) return
  uploadingShowcase.value = true
  try {
    uni.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      async success(result) {
        const files = imageFilesFromChooseResult(result, 'seller-showcase')
        if (!files.length) {
          uploadingShowcase.value = false
          showToast('照片暂不可用')
          return
        }
        try {
          const urls = await Promise.all(files.map((file) => uploadCommunityImage(file, 'showcase')))
          form.showcaseImageUrls = Array.from(new Set([...uploadedShowcaseUrls.value, ...urls])).slice(0, MAX_SHOWCASE_PHOTOS)
          showcaseChanged.value = true
          showToast('照片已上传，保存后展示', 'success')
        } catch (error) {
          console.warn('profile showcase upload failed', { count: files.length, error })
          showToast(error instanceof Error ? error.message : '照片上传失败')
        } finally {
          uploadingShowcase.value = false
        }
      },
      fail(error: unknown) {
        uploadingShowcase.value = false
        const message = String((error as { errMsg?: string })?.errMsg || '')
        console.warn('profile showcase picker failed', { error })
        showToast(message.includes('cancel') ? '未选择照片' : '无法打开照片选择器，请检查相册或相机权限后重试')
      }
    })
  } catch (error) {
    uploadingShowcase.value = false
    console.warn('profile showcase picker failed', { error })
    showToast('无法打开照片选择器，请检查相册或相机权限后重试')
  }
}

function removeShowcasePhoto(index: number) {
  form.showcaseImageUrls = uploadedShowcaseUrls.value.filter((_, currentIndex) => currentIndex !== index)
  showcaseChanged.value = true
}

async function saveProfile() {
  if (!canEditProfile.value || saving.value || uploadingAvatar.value || uploadingShowcase.value) return
  trimTextField('nickname')
  trimTextField('city')
  trimTextField('bio')
  if (!form.nickname) { showToast('昵称不能为空'); return }
  saving.value = true
  try {
    const safeAvatarUrl = avatarChanged.value ? uploadedCommunityImageUrl(form.avatarUrl, 'avatar') : undefined
    const safeShowcaseUrls = hasApprovedVideoIdentity.value && showcaseChanged.value ? uploadedCommunityImageUrls(form.showcaseImageUrls) : undefined
    const profilePayload: UpdateUserProfileRequest = {
      nickname: form.nickname,
      avatarUrl: safeAvatarUrl,
      gender: form.gender,
      mainRole: hasApprovedVideoIdentity.value ? form.mainRole : 'BUYER',
      city: form.city,
      bio: form.bio
    }
    if (safeShowcaseUrls) profilePayload.showcaseImageUrls = safeShowcaseUrls
    const profile = await updateMyProfile(profilePayload)
    assertBackendProfile(profile)
    assertSameProfile(profile, 'saveProfile')
    assertProfileSaveResponse(profile, profilePayload, safeAvatarUrl, safeShowcaseUrls)
    applyProfile(profile)
    avatarChanged.value = false
    showcaseChanged.value = false
    showToast('已保存', 'success')
  } catch (error) {
    console.warn('profile save failed', { userId: form.userId, avatarChanged: avatarChanged.value, showcaseChanged: showcaseChanged.value, error })
    showToast(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function loadProfile() {
  loadingProfile.value = true
  profileError.value = ''
  try {
    const profile = await getMyProfile()
    assertBackendProfile(profile)
    applyProfile(profile)
    avatarChanged.value = false
    showcaseChanged.value = false
  } catch (error) {
    profileError.value = '资料暂不可用，请稍后重试'
    console.warn('profile load failed', { error })
    showToast('资料暂不可用')
  } finally {
    loadingProfile.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped lang="scss" src="./style.scss"></style>
