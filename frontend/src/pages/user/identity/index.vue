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
      <input :value="form.idNumber" class="input" maxlength="18" type="text" placeholder="18 位身份证号码" @input="updateRealNameField('idNumber', $event)" @blur="normalizeRealNameForm" />
      <view class="upload">
        <view class="upload-icon">＋</view>
        <view>
          <view class="upload-title">提交完整实名资料</view>
          <view class="upload-desc">完整身份证号码只用于平台审核、提现复核和纠纷处理；页面与后台列表只展示脱敏摘要。</view>
        </view>
      </view>
      <button class="primary-btn" :disabled="realNameSubmitDisabled" @click="submit">{{ realNameSubmitButtonText }}</button>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">卖家收款账户</view>
      <view class="status-text">{{ payoutAccountStatusText }}</view>
      <view v-if="activePayoutAccount" class="account-summary">
        <view><text>收款方式</text><text>{{ payoutMethodLabel(activePayoutAccount.paymentMethod) }}</text></view>
        <view><text>收款人</text><text>{{ activePayoutAccount.accountName }}</text></view>
        <view><text>脱敏账号</text><text>{{ activePayoutAccount.maskedAccountNo }}</text></view>
      </view>
      <view class="method-row">
        <view v-for="item in payoutMethods" :key="item" class="method-chip tapable" :class="{ active: payoutForm.paymentMethod === item }" @click="payoutForm.paymentMethod = item">{{ payoutMethodLabel(item) }}</view>
      </view>
      <input :value="payoutForm.accountName" class="input" maxlength="24" placeholder="收款人姓名，需与实名一致" @input="updatePayoutField('accountName', $event)" @blur="trimPayoutField('accountName')" />
      <input :value="payoutForm.accountNo" class="input" maxlength="80" placeholder="推荐填写支付宝账号" @input="updatePayoutField('accountNo', $event)" @blur="trimPayoutField('accountNo')" />
      <button class="primary-btn" :disabled="payoutSubmitting || payoutLoading" @click="submitPayoutAccount">{{ payoutSubmitting ? '提交中...' : '绑定收款账户' }}</button>
      <view v-if="payoutMessage" class="account-message" :class="{ danger: payoutFailed }">{{ payoutMessage }}</view>
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
import { bindPayoutAccount, getPayoutAccount, type PayoutAccountResponse } from '../../../api/modules/wallet'
import {
  assertBackendProfile,
  checks,
  guessVideoContentType,
  hasMaskedAccountMarker,
  hasApprovedVideoIdentity,
  hasInvalidTempVideoPath,
  hasInvalidVideoIdentityDuration,
  isValidChineseIdNumber,
  isPickerCancel,
  normalizedIdNumber,
  validatedVideoIdentityUrl,
  videoNameFromPickerResult,
  videoTypeFromPickerResult,
  type PayoutAccountFieldKey,
  type ChooseVideoResult,
  type RealNameFieldKey
} from './identity-helpers'

const form = reactive({ name: '', idNumber: '' })
const payoutMethods = ['ALIPAY', 'BANK_CARD']
const payoutForm = reactive({ paymentMethod: 'ALIPAY', accountName: '', accountNo: '' })
const activePayoutAccount = ref<PayoutAccountResponse | null>(null)
const videoUrl = ref('')
const uploadingVideo = ref(false)
const submittingVideo = ref(false)
const submittingRealName = ref(false)
const payoutLoading = ref(false)
const payoutSubmitting = ref(false)
const payoutMessage = ref('')
const payoutFailed = ref(false)
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
const payoutAccountStatusText = computed(payoutAccountStatusLabel)
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

function payoutMethodLabel(method: string): string {
  if (method === 'ALIPAY') return '支付宝（推荐）'
  return '银行卡'
}

function payoutAccountStatusLabel(): string {
  if (payoutLoading.value) return '正在读取平台收款账户...'
  if (activePayoutAccount.value) return `已绑定 ${payoutMethodLabel(activePayoutAccount.value.paymentMethod)}，提现仍需后台审核。`
  return '推荐先绑定支付宝账号；未绑定前卖家提现会 fail-closed。'
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

function normalizeRealNameForm(): void {
  form.name = form.name.trim()
  form.idNumber = normalizedIdNumber(form.idNumber)
}

function payoutInputValue(field: PayoutAccountFieldKey, event: unknown): string | undefined {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  if (typeof value !== 'string') {
    console.warn('identity payout account input invalid', { field })
    uni.showToast({ title: '收款账户输入读取失败，请重新输入', icon: 'none' })
    return undefined
  }
  return value
}

function updatePayoutField(field: PayoutAccountFieldKey, event: unknown): void {
  const value = payoutInputValue(field, event)
  if (value === undefined) return
  payoutForm[field] = value
}

function trimPayoutField(field: PayoutAccountFieldKey): void {
  payoutForm[field] = payoutForm[field].trim()
}

async function loadPayoutAccount(): Promise<void> {
  payoutLoading.value = true
  payoutFailed.value = false
  try {
    activePayoutAccount.value = await getPayoutAccount()
    payoutMessage.value = activePayoutAccount.value
      ? '已读取平台绑定的脱敏收款账户。'
      : '暂未绑定收款账户，建议优先填写支付宝账号。'
  } catch (error) {
    activePayoutAccount.value = null
    payoutFailed.value = true
    payoutMessage.value = '收款账户暂时无法读取，请稍后重新进入页面确认。'
    console.warn('identity payout account refresh failed', { error })
  } finally {
    payoutLoading.value = false
  }
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
  normalizeRealNameForm()
  if (!form.name || form.name.length < 2) return uni.showToast({ title: '请填写真实姓名', icon: 'none' })
  if (!isValidChineseIdNumber(form.idNumber)) return uni.showToast({ title: '请填写有效的 18 位身份证号码', icon: 'none' })
  if (submittingRealName.value) {
    console.warn('identity real-name submit ignored because submission is already in progress')
    return uni.showToast({ title: '实名认证正在提交，请勿重复点击', icon: 'none' })
  }
  submittingRealName.value = true
  try {
    await submitRealNameIdentity({ realName: form.name.trim(), idNumber: normalizedIdNumber(form.idNumber) })
    form.idNumber = ''
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

async function submitPayoutAccount(): Promise<void> {
  if (payoutSubmitting.value) {
    console.warn('identity payout account submit ignored because submission is already in progress')
    return uni.showToast({ title: '收款账户正在提交，请勿重复点击', icon: 'none' })
  }
  trimPayoutField('accountName')
  trimPayoutField('accountNo')
  payoutMessage.value = ''
  payoutFailed.value = false
  if (!payoutForm.accountName || payoutForm.accountName.length < 2) {
    payoutFailed.value = true
    payoutMessage.value = '请填写真实收款人姓名。'
    return uni.showToast({ title: '请填写真实收款人姓名', icon: 'none' })
  }
  if (!payoutForm.accountNo || hasMaskedAccountMarker(payoutForm.accountNo)) {
    payoutFailed.value = true
    payoutMessage.value = '请填写完整收款账号，不能提交脱敏账号。'
    return uni.showToast({ title: '请填写完整收款账号', icon: 'none' })
  }
  payoutSubmitting.value = true
  try {
    activePayoutAccount.value = await bindPayoutAccount({
      paymentMethod: payoutForm.paymentMethod,
      accountName: payoutForm.accountName,
      accountNo: payoutForm.accountNo
    })
    payoutForm.accountNo = ''
    payoutMessage.value = '收款账户已绑定，页面只保留脱敏账号；提现仍需后台审核。'
    uni.showToast({ title: '收款账户已绑定', icon: 'none' })
  } catch (error) {
    payoutFailed.value = true
    payoutMessage.value = error instanceof Error ? error.message : '收款账户绑定失败，请检查后重试。'
    console.warn('identity payout account submit failed', { paymentMethod: payoutForm.paymentMethod, error })
    uni.showToast({ title: payoutMessage.value, icon: 'none' })
  } finally {
    payoutSubmitting.value = false
  }
}

async function initializeIdentityPage(): Promise<void> {
  await Promise.all([loadProfile(), loadPayoutAccount()])
}

onMounted(initializeIdentityPage)
</script>

<style scoped lang="scss" src="./style.scss"></style>
