<template>
  <view class="page-shell identity-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 认证中心</view>
        <view class="page-title">身份与视频认证</view>
        <view class="page-desc">实名用于提现和纠纷处理；视频认证通过后，会在你的个人主页顶部展示给其他用户。</view>
      </view>
      <view class="hero-icon">🪪</view>
    </view>

    <view class="video-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">视频认证</view>
          <view class="status-text">{{ videoStatusHint }}</view>
        </view>
        <view class="status-pill" :class="videoStatusClass">{{ videoStatusText }}</view>
      </view>
      <view class="video-box tapable" :class="{ disabled: videoActionDisabled, selected: hasSelectedVideo }" @click="chooseVideo">
        <view class="video-play">▶</view>
        <view>
          <view class="upload-title">{{ videoUploadTitle }}</view>
          <view class="upload-desc">建议展示本人正脸并口播“小原圈视频认证”；仅平台审核通过后才对外显示认证卖家标识。</view>
          <view class="video-upload-state" :class="{ muted: !hasSelectedVideo }">{{ videoUploadStateText }}</view>
        </view>
      </view>
      <view class="verify-note">提交后会重新读取平台认证记录，审核通过前不会展示认证卖家标识。</view>
      <button class="primary-btn video-submit-btn" :class="{ disabled: videoSubmitDisabled }" :disabled="videoSubmitDisabled" @click="submitVideo">
        {{ videoSubmitButtonText }}
      </button>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">实名认证资料</view>
      <view class="status-text">当前状态：{{ realNameStatusText }}</view>
      <input :value="form.name" class="input" placeholder="真实姓名" @input="updateRealNameField('name', $event)" />
      <input :value="form.idTail" class="input" maxlength="4" type="number" placeholder="证件号码后四位" @input="updateRealNameField('idTail', $event)" />
      <view class="upload">
        <view class="upload-icon">＋</view>
        <view>
          <view class="upload-title">提交最小实名资料</view>
          <view class="upload-desc">仅提交真实姓名和证件后四位，不上传完整证件号；通过后台审核后用于提现和纠纷处理。</view>
        </view>
      </view>
      <button class="primary-btn" :disabled="realNameSubmitDisabled" @click="submit">{{ realNameSubmitButtonText }}</button>
    </view>

    <view class="check-card ds-card">
      <view class="section-title">审核检查项</view>
      <view v-for="item in checks" :key="item" class="check-row">✓ {{ item }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketBlob, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, submitRealNameIdentity, submitVideoIdentity, type UserProfileResponse } from '../../../api/modules/user'
import {
  assertBackendProfile,
  checks,
  guessVideoContentType,
  hasApprovedVideoIdentity,
  hasInvalidTempVideoPath,
  hasInvalidVideoIdentityDuration,
  isPickerCancel,
  validatedVideoIdentityUrl,
  videoNameFromPickerResult,
  videoTypeFromPickerResult,
  type ChooseVideoResult,
  type RealNameFieldKey
} from './identity-helpers'

const form = reactive({ name: '', idTail: '' })
const videoUrl = ref('')
const uploadingVideo = ref(false)
const submittingVideo = ref(false)
const submittingRealName = ref(false)
const profileReady = ref(false)
const profileUnavailable = ref(false)
const profile = reactive<UserProfileResponse>({ userId: 0, nickname: '', mainRole: 'UNVERIFIED', identityStatus: 'UNVERIFIED', videoIdentityStatus: 'UNVERIFIED', videoVerified: false })
const videoStatusText = computed(videoStatusLabel)
const videoStatusClass = computed(videoStatusClassName)
const videoStatusHint = computed(videoStatusDescription)
const hasSelectedVideo = computed(() => Boolean(videoUrl.value))
const videoUploadTitle = computed(videoUploadTitleLabel)
const videoUploadStateText = computed(videoUploadStateLabel)
const videoSubmitButtonText = computed(submitVideoButtonLabel)
const realNameStatusText = computed(realNameStatusLabel)
const realNameSubmitButtonText = computed(submitRealNameButtonLabel)
const videoActionDisabled = computed(() => !profileReady.value || profileUnavailable.value || uploadingVideo.value || hasApprovedVideoIdentity(profile))
const videoSubmitDisabled = computed(() => submittingVideo.value || uploadingVideo.value || !videoUrl.value || !profileReady.value || profileUnavailable.value || hasApprovedVideoIdentity(profile))
const realNameSubmitDisabled = computed(() => submittingRealName.value || !profileReady.value || profileUnavailable.value || profile.identityStatus === 'VERIFIED')

function clearVideoTrustState(): void {
  profile.identityStatus = 'UNVERIFIED'
  profile.videoIdentityStatus = 'UNVERIFIED'
  profile.videoVerified = false
  profile.videoIdentityUrl = ''
  videoUrl.value = ''
}

function syncSubmittedVideoUrlFromProfile(): void {
  if (profile.videoIdentityStatus !== 'PENDING' && !hasApprovedVideoIdentity(profile)) return
  videoUrl.value = validatedVideoIdentityUrl(profile.videoIdentityUrl)
}

function realNameStatusLabel(): string {
  if (!profileReady.value || profileUnavailable.value) return '状态不可用'
  if (profile.identityStatus === 'VERIFIED') return '已通过'
  if (profile.identityStatus === 'PENDING') return '审核中'
  if (profile.identityStatus === 'REJECTED') return '已拒绝'
  return '未认证'
}

function videoStatusLabel(): string {
  if (!profileReady.value || profileUnavailable.value) return '状态不可用'
  if (hasApprovedVideoIdentity(profile)) return '已通过'
  switch (profile.videoIdentityStatus) {
    case 'PENDING':
      return '审核中'
    case 'REJECTED':
      return '已拒绝'
    default:
      return '未认证'
  }
}

function videoStatusClassName(): string {
  if (!profileReady.value || profileUnavailable.value) return 'unavailable'
  if (hasApprovedVideoIdentity(profile)) return 'approved'
  switch (profile.videoIdentityStatus) {
    case 'PENDING':
      return 'pending'
    case 'REJECTED':
      return 'rejected'
    default:
      return 'unverified'
  }
}

function videoStatusDescription(): string {
  if (!profileReady.value || profileUnavailable.value) return '平台认证状态暂时不可用，请稍后重新进入页面查看。'
  if (hasApprovedVideoIdentity(profile)) return '已通过视频认证，其他用户可在你的主页看到认证卖家标识。'
  switch (profile.videoIdentityStatus) {
    case 'PENDING':
      return '资料已提交，平台正在审核；通过前不会对外展示。'
    case 'REJECTED':
      return '上次资料未通过，可以重新上传清晰的真人短片。'
    default:
      return '上传真人短片后提交审核，通过前不会对外展示。'
  }
}

function videoUploadTitleLabel(): string {
  if (uploadingVideo.value) return '视频上传中...'
  if (videoUrl.value) return '认证视频已上传'
  return '录制/上传 10 秒真人认证视频'
}

function videoUploadStateLabel(): string {
  if (uploadingVideo.value) return '正在上传到平台，请稍候'
  if (videoUrl.value) return '认证视频已选择/已上传，可重新选择'
  return '未选择认证视频'
}

function submitVideoButtonLabel(): string {
  if (submittingVideo.value) return '提交中...'
  if (uploadingVideo.value) return '上传中...'
  if (!profileReady.value || profileUnavailable.value) return '认证状态不可用'
  if (hasApprovedVideoIdentity(profile)) return '视频认证已通过'
  if (!videoUrl.value) return '请先选择认证视频'
  return '提交视频认证'
}

function submitRealNameButtonLabel(): string {
  if (submittingRealName.value) return '提交中...'
  if (!profileReady.value || profileUnavailable.value) return '认证状态不可用'
  if (profile.identityStatus === 'VERIFIED') return '实名认证已通过'
  if (profile.identityStatus === 'PENDING') return '重新提交实名资料'
  if (profile.identityStatus === 'REJECTED') return '重新提交实名认证'
  return '提交实名认证'
}

function inputValue(field: RealNameFieldKey, event: unknown): string | undefined {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  if (typeof value !== 'string') {
    console.warn('identity real-name input invalid', { field })
    uni.showToast({ title: '输入内容读取失败，请重新输入', icon: 'none' })
    return undefined
  }
  return value.trim()
}

function updateRealNameField(field: RealNameFieldKey, event: unknown): void {
  const value = inputValue(field, event)
  if (value === undefined) return
  form[field] = value
}

async function readH5TempVideoBlob(tempFilePath: string): Promise<Blob | undefined> {
  if (!tempFilePath.toLowerCase().startsWith('blob:')) return undefined
  if (typeof fetch !== 'function') throw new Error('当前环境不支持读取视频文件，请换用 MP4 文件或稍后重试')
  const response = await fetch(tempFilePath)
  if (!response.ok) throw new Error('视频文件读取失败，请重新选择')
  return response.blob()
}

function chooseVideo(): void {
  if (!profileReady.value || profileUnavailable.value) return uni.showToast({ title: '认证状态暂时不可用，请稍后重新进入页面查看', icon: 'none' })
  if (hasApprovedVideoIdentity(profile)) return uni.showToast({ title: '视频认证已通过', icon: 'none' })
  if (uploadingVideo.value) return uni.showToast({ title: '视频上传中，请稍后再选', icon: 'none' })
  uploadingVideo.value = true
  try {
    uni.chooseVideo({
      sourceType: ['camera', 'album'],
      compressed: true,
      maxDuration: 10,
      async success(res: ChooseVideoResult) {
        let contentType = 'video/mp4'
        let fileSize = 1
        let filename = 'video-identity.mp4'
        try {
          if (!res.tempFilePath || hasInvalidTempVideoPath(res.tempFilePath)) {
            videoUrl.value = ''
            console.warn('identity video picker returned invalid temp path', { scene: 'VIDEO_IDENTITY', tempFilePath: res.tempFilePath, size: res.size })
            uni.showToast({ title: '视频资料无效，请重新选择', icon: 'none' })
            return
          }
          if (hasInvalidVideoIdentityDuration(res.duration)) {
            videoUrl.value = ''
            console.warn('identity video picker returned invalid duration', { scene: 'VIDEO_IDENTITY', duration: res.duration })
            uni.showToast({ title: '视频认证视频请控制在 10 秒以内', icon: 'none' })
            return
          }
          const h5Blob = await readH5TempVideoBlob(res.tempFilePath)
          contentType = guessVideoContentType(res.tempFilePath, videoTypeFromPickerResult(res, h5Blob))
          fileSize = Math.max(1, h5Blob?.size ?? res.size ?? 1)
          filename = videoNameFromPickerResult(res, contentType)
          uni.showLoading({ title: '上传视频中' })
          const ticket = await createMediaUploadTicket({
            scene: 'VIDEO_IDENTITY',
            contentType,
            fileSize,
            filename
          })
          const uploaded = h5Blob
            ? await uploadMediaTicketBlob(ticket, new Blob([h5Blob], { type: contentType }), filename)
            : await uploadMediaTicketFile(ticket, res.tempFilePath)
          videoUrl.value = validatedVideoIdentityUrl(uploaded.storageUrl)
          uni.showToast({ title: '已生成上传票据', icon: 'none' })
        } catch (error) {
          videoUrl.value = ''
          console.warn('identity video upload failed', { scene: 'VIDEO_IDENTITY', contentType, fileSize, filename, error })
          uni.showToast({ title: error instanceof Error ? error.message : '视频上传票据创建失败', icon: 'none' })
        } finally {
          uploadingVideo.value = false
          uni.hideLoading()
        }
      },
      fail(error: unknown) {
        uploadingVideo.value = false
        const cancelled = isPickerCancel(error)
        if (!cancelled) console.warn('identity video picker failed', { error })
        uni.showToast({ title: cancelled ? '未选择视频认证资料' : '无法打开视频选择器，请检查相册或相机权限后重试', icon: 'none' })
      }
    })
  } catch (error) {
    uploadingVideo.value = false
    console.warn('identity video picker failed', { error })
    uni.showToast({ title: '无法打开视频选择器，请检查相册或相机权限后重试', icon: 'none' })
  }
}

async function loadProfile(): Promise<boolean> {
  try {
    const backendProfile = await getMyProfile()
    assertBackendProfile(backendProfile)
    Object.assign(profile, backendProfile)
    syncSubmittedVideoUrlFromProfile()
    profileReady.value = true
    profileUnavailable.value = false
    return true
  } catch (error) {
    clearVideoTrustState()
    profileReady.value = false
    profileUnavailable.value = true
    console.warn('identity profile refresh failed; cleared video trust state', { error })
    uni.showToast({ title: '认证状态暂时不可用，已隐藏认证通过状态，请稍后重试', icon: 'none' })
    return false
  }
}

function navigateToNotificationAfterVideoSubmit(): void {
  const route = {
    url: '/pages/notification/index',
    fail: (error: unknown) => {
      console.warn('identity notification navigation failed', { error })
      uni.showToast({ title: '视频认证已提交，但暂时无法打开通知页', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('identity notification navigation failed', { error })
    uni.showToast({ title: '视频认证已提交，但暂时无法打开通知页', icon: 'none' })
  }
}

async function submitVideo(): Promise<void> {
  if (!profileReady.value || profileUnavailable.value) return uni.showToast({ title: '认证状态暂时不可用，请稍后重新进入页面查看', icon: 'none' })
  if (hasApprovedVideoIdentity(profile)) return uni.showToast({ title: '视频认证已通过', icon: 'none' })
  if (uploadingVideo.value) return uni.showToast({ title: '视频上传中，请稍后提交', icon: 'none' })
  if (!videoUrl.value) return uni.showToast({ title: '请先上传视频认证资料', icon: 'none' })
  if (submittingVideo.value) {
    console.warn('identity video submit ignored because submission is already in progress')
    return uni.showToast({ title: '视频认证正在提交，请勿重复点击', icon: 'none' })
  }
  submittingVideo.value = true
  try {
    const safeVideoUrl = validatedVideoIdentityUrl(videoUrl.value)
    await submitVideoIdentity({ videoUrl: safeVideoUrl, description: '小原圈真人视频认证' })
    const refreshed = await loadProfile()
    if (!refreshed) return uni.showToast({ title: '认证提交结果暂时无法校验，请稍后重新进入页面确认后再操作', icon: 'none' })
    const modalOptions = {
      title: '视频认证已提交',
      content: '资料已由平台接收，当前认证状态已重新读取平台资料；通过后，其他用户可在你的个人主页顶部看到“视频认证卖家”。',
      showCancel: false,
      fail: (error: unknown) => {
        console.warn('identity video submit success modal failed', { error })
        uni.showToast({ title: '视频认证已提交，请稍后查看审核状态', icon: 'none' })
      },
      success: navigateToNotificationAfterVideoSubmit
    }
    try {
      uni.showModal(modalOptions)
    } catch (error) {
      console.warn('identity video submit success modal failed', { error })
      uni.showToast({ title: '视频认证已提交，请稍后查看审核状态', icon: 'none' })
    }
  } catch (error) {
    console.warn('identity video submit failed', { hasVideoUrl: Boolean(videoUrl.value), error })
    uni.showToast({ title: error instanceof Error ? error.message : '视频认证提交失败', icon: 'none' })
  } finally {
    submittingVideo.value = false
  }
}

async function submit(): Promise<void> {
  if (!profileReady.value || profileUnavailable.value) return uni.showToast({ title: '认证状态暂时不可用，请稍后重新进入页面查看', icon: 'none' })
  if (profile.identityStatus === 'VERIFIED') return uni.showToast({ title: '实名认证已通过', icon: 'none' })
  if (!form.name || form.name.length < 2) return uni.showToast({ title: '请填写真实姓名', icon: 'none' })
  if (!/^\d{4}$/.test(form.idTail)) return uni.showToast({ title: '请填写证件号码后四位', icon: 'none' })
  if (submittingRealName.value) {
    console.warn('identity real-name submit ignored because submission is already in progress')
    return uni.showToast({ title: '实名认证正在提交，请勿重复点击', icon: 'none' })
  }
  submittingRealName.value = true
  try {
    await submitRealNameIdentity({ realName: form.name.trim(), idTail: form.idTail.trim() })
    const refreshed = await loadProfile()
    if (!refreshed) return uni.showToast({ title: '实名提交结果暂时无法校验，请稍后重新进入页面确认后再操作', icon: 'none' })
    const modalOptions = {
      title: '实名认证已提交',
      content: '资料已由平台接收，当前实名状态已重新读取平台记录；审核通过后会同步用于提现和纠纷处理。',
      showCancel: false,
      fail(error: unknown) {
        console.warn('identity real-name submit success modal failed', { error })
        uni.showToast({ title: '实名认证已提交，请稍后查看审核状态', icon: 'none' })
      }
    }
    try {
      uni.showModal(modalOptions)
    } catch (error) {
      console.warn('identity real-name submit success modal failed', { error })
      uni.showToast({ title: '实名认证已提交，请稍后查看审核状态', icon: 'none' })
    }
  } catch (error) {
    console.warn('identity real-name submit failed', { error })
    uni.showToast({ title: error instanceof Error ? error.message : '实名认证提交失败', icon: 'none' })
  } finally {
    submittingRealName.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped lang="scss" src="./style.scss"></style>
