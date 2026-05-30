<template>
  <view class="page-shell after-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 订单售后</view>
        <view class="page-title">申请售后/退款</view>
        <view class="page-desc">交易请保留照片、聊天记录和物流材料；售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准。</view>
      </view>
      <view class="hero-icon">🛟</view>
    </view>

    <view v-if="errorText" class="status-card ds-card danger">
      <view class="section-title">暂不能申请售后</view>
      <view class="section-desc danger-text">{{ errorText }}</view>
    </view>

    <view v-else class="form-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">售后类型</view>
          <view class="section-desc">当前订单 {{ orderNo }}，提交后由服务端创建售后单。</view>
        </view>
        <view class="order-chip">订单已校验</view>
      </view>
      <view class="type-row">
        <view v-for="item in types" :key="item" class="type-chip tapable" :class="{ active: type === item }" @click="type = item">{{ item }}</view>
      </view>
      <input :value="amount" class="field" type="digit" placeholder="退款金额" @input="updateApplyField('amount', $event)" />
      <view class="section-title mt">原因</view>
      <view class="reason-grid">
        <view v-for="item in reasons" :key="item" class="reason-chip tapable" :class="{ active: reason === item }" @click="reason = item">{{ item }}</view>
      </view>
      <textarea :value="desc" class="textarea" maxlength="180" placeholder="请说明商品问题、协商过程和期望处理方式" @input="updateApplyField('desc', $event)" />
      <view class="upload tapable" :class="{ busy: uploadingEvidence }" @click="chooseEvidence">
        <view class="upload-title">{{ uploadingEvidence ? '售后票据上传中' : '＋ 上传售后票据' }} {{ images.length }}/{{ maxEvidenceImages }}</view>
        <view class="upload-desc">仅使用后端上传票据返回的材料地址提交。</view>
      </view>
      <view v-if="images.length" class="image-row"><view v-for="(img, index) in images" :key="img" class="image-chip">已上传票据 {{ index + 1 }}</view></view>
      <button class="primary-btn" :disabled="submitting || uploadingEvidence || !orderNo || !!submittedAfterSalesNo" @click="submitApply">{{ submitButtonText }}</button>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">处理规则</view>
      <view class="safe-line">售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准。</view>
      <view class="safe-line">当前页面只提交售后申请；审核结论和公开展示状态以服务端记录为准。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createAfterSales, type AfterSalesResponse } from '../../../api/modules/after-sales'
import { createMediaUploadTicket, uploadMediaTicketFile, type MediaUploadScene } from '../../../api/modules/media'
type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
type ChooseImageFile = { name?: string; type?: string; size?: number }
type ChooseImageResult = { tempFilePaths?: string[]; tempFiles?: ChooseImageFile[] }
type AfterSalesApplyFieldKey = 'amount' | 'desc'

const maxEvidenceImages = 6
const evidenceUploadScene: MediaUploadScene = 'AFTER_SALES_EVIDENCE'
const evidenceStoragePrefix = '/uploads/evidence/after-sales/'
const launchReadinessMarkers = [
  '交易请保留照片、聊天记录和物流材料；售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准。',
  '售后申请已提交，处理进度以后端记录为准。'
]

const backendOrderNoPattern = /^OD-[0-9]{1,10}$/
const orderNo = ref('')
const errorText = ref('')
const submitting = ref(false)
const uploadingEvidence = ref(false)
const submittedAfterSalesNo = ref('')
const types = ['仅退款', '退货退款', '售后协调']
const type = ref('仅退款')
const amount = ref('')
const reasons = ['成色不符', '尺码不符', '未收到货', '物流异常', '其他']
const reason = ref('成色不符')
const desc = ref('')
const images = ref<string[]>([])
const submitButtonText = computed(() => {
  if (submittedAfterSalesNo.value) return '已提交'
  if (submitting.value) return '提交中...'
  return '提交售后申请'
})
function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}
function isValidAfterSalesNo(value: string): boolean {
  return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}
function isValidRefundAmount(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}
function inputValue(field: AfterSalesApplyFieldKey, event: unknown): string | undefined {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  if (typeof value !== 'string') {
    console.warn('after-sales apply input invalid', { field })
    uni.showToast({ title: '输入内容读取失败，请重新输入', icon: 'none' })
    return undefined
  }
  return value.trim()
}
function updateApplyField(field: AfterSalesApplyFieldKey, event: unknown): void {
  const value = inputValue(field, event)
  if (value === undefined) return
  if (field === 'amount') amount.value = value
  if (field === 'desc') desc.value = value
}
function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('after-sales apply route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}
function assertAfterSalesResponse(response: AfterSalesResponse, expectedOrderNo: string): void {
  if (!isValidAfterSalesNo(response.afterSalesNo)) throw new Error('after-sales apply invalid afterSalesNo')
  if (!isValidBackendOrderNo(response.orderNo)) throw new Error('after-sales apply invalid backend orderNo')
  if (response.orderNo !== expectedOrderNo) throw new Error('after-sales apply orderNo mismatch')
  if (!isValidRefundAmount(response.refundAmount)) throw new Error('after-sales apply invalid refund amount')
  if (!Array.isArray(response.evidenceUrls) || response.evidenceUrls.length === 0 || response.evidenceUrls.some(hasInvalidEvidenceUrl)) throw new Error('after-sales apply invalid evidence url')
}
function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const rawRouteOrderNo = current?.options?.orderNo ?? hashParams?.get('orderNo') ?? ''
  const routeOrderNo = decodeRouteValue('orderNo', rawRouteOrderNo)
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    console.warn('after-sales apply invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  if (!isValidBackendOrderNo(routeOrderNo)) {
    orderNo.value = ''
    errorText.value = '缺少有效订单号，请从订单详情发起售后'
    return
  }
  orderNo.value = routeOrderNo
  errorText.value = ''
}
function chooseEvidence(): void {
  if (uploadingEvidence.value) {
    uni.showToast({ title: '售后票据上传中，请稍后再选', icon: 'none' })
    return
  }
  const remain = maxEvidenceImages - images.value.length
  if (remain <= 0) {
    uni.showToast({ title: `售后票据最多上传 ${maxEvidenceImages} 张`, icon: 'none' })
    return
  }
  uploadingEvidence.value = true
  try {
    uni.chooseImage({
      count: remain,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      async success(res: ChooseImageResult) {
        try {
          const paths = (res.tempFilePaths || []).slice(0, remain)
          if (!paths.length) throw new Error('未选择到有效票据图片，请重新选择')

          const uploadedUrls: string[] = []
          for (const [index, path] of paths.entries()) {
            uploadedUrls.push(await uploadEvidenceImage(path, res.tempFiles?.[index]))
          }
          images.value = images.value.concat(uploadedUrls).slice(0, maxEvidenceImages)
          uni.showToast({ title: `售后票据已上传 ${images.value.length} 张，提交后才会进入售后处理`, icon: 'none' })
        } catch (error) {
          console.warn('after-sales apply evidence upload failed', { error })
          uni.showToast({ title: '售后票据上传失败，请重新选择', icon: 'none' })
        } finally {
          uploadingEvidence.value = false
        }
      },
      fail(error: unknown) {
        uploadingEvidence.value = false
        console.warn('after-sales apply choose evidence failed', { error })
        uni.showToast({ title: '请在手机端选择票据图片', icon: 'none' })
      }
    })
  } catch (error) {
    uploadingEvidence.value = false
    console.warn('after-sales apply choose evidence failed', { error })
    uni.showToast({ title: '请在手机端选择票据图片', icon: 'none' })
  }
}
async function uploadEvidenceImage(path: string, file?: ChooseImageFile): Promise<string> {
  if (hasInvalidTempEvidencePath(path)) throw new Error('票据图片无效，请重新选择')
  const contentType = imageContentType(path, file?.type)
  const ticket = await createMediaUploadTicket({ scene: evidenceUploadScene, contentType, fileSize: imageFileSize(file), filename: fileNameFromPath(file?.name || path, imageFallbackName(contentType)) })
  const uploaded = await uploadMediaTicketFile(ticket, path)
  const storageUrl = uploaded.storageUrl
  if (!storageUrl || hasInvalidEvidenceUrl(storageUrl)) throw new Error('票据图片未完成平台上传校验，请重新选择')
  return storageUrl
}
function fileNameFromPath(path: string, fallbackName = 'after-sales-evidence.jpg'): string {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || fallbackName
  return last.includes('.') ? last : fallbackName
}
function isImageContentType(value: string | undefined): value is ImageContentType {
  return value === 'image/png' || value === 'image/webp' || value === 'image/jpeg'
}
function imageContentType(path: string, fallbackType?: string): ImageContentType {
  const normalizedType = fallbackType?.toLowerCase()
  if (isImageContentType(normalizedType)) return normalizedType
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}
function imageFileSize(file?: ChooseImageFile): number {
  return Math.max(1, Math.min(Number(file?.size || 600_000), 10_000_000))
}
function imageFallbackName(contentType: ImageContentType): string {
  switch (contentType) {
    case 'image/png':
      return 'after-sales-evidence.png'
    case 'image/webp':
      return 'after-sales-evidence.webp'
    default:
      return 'after-sales-evidence.jpg'
  }
}
function hasInvalidTempEvidencePath(path: string): boolean {
  const lower = path.toLowerCase()
  return !path ||
    lower.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..')
}
function hasInvalidEvidenceUrl(url: unknown): boolean {
  if (typeof url !== 'string') return true
  const lower = url.toLowerCase()
  if (!url.startsWith(evidenceStoragePrefix)) return true
  if (url.startsWith('local://') || url.startsWith('blob:') || url.startsWith('data:')) return true
  if (url.includes('\\') || url.includes('..') || url.includes('//')) return true
  if (lower.includes('placeholder') || lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c')) return true
  const relativePath = url.slice(evidenceStoragePrefix.length)
  return !relativePath || relativePath.split('/').some(segment => !segment || segment === '..')
}
function validateRefundAmount(): string {
  if (!amount.value.trim()) return ''
  const numeric = Number(amount.value)
  return isValidRefundAmount(numeric) ? String(numeric) : ''
}
function validate(): string {
  const safeRefundAmount = validateRefundAmount()
  const safeDescription = desc.value.trim()
  if (uploadingEvidence.value) return '售后票据上传中，请稍后提交'
  if (!isValidBackendOrderNo(orderNo.value)) return '缺少有效订单号，请从订单详情发起售后'
  if (!safeRefundAmount) return '请填写有效退款金额'
  if (safeDescription.length < 8) return '请补充至少8个字的问题说明'
  if (!images.value.length) return '请至少上传一张售后票据'
  if (images.value.some(hasInvalidEvidenceUrl)) return '售后票据需先完成平台上传票据校验'
  return ''
}
async function submitApply(): Promise<void> {
  if (submitting.value) {
    console.warn('after-sales apply submit ignored because submission is already in progress')
    uni.showToast({ title: '售后申请正在提交，请勿重复点击', icon: 'none' })
    return
  }
  if (submittedAfterSalesNo.value) {
    const safeSubmittedOrderNo = orderNo.value
    if (isValidBackendOrderNo(safeSubmittedOrderNo)) redirectToAfterSalesDetail(submittedAfterSalesNo.value, safeSubmittedOrderNo)
    return
  }
  const message = validate()
  if (message) {
    uni.showToast({ title: message, icon: 'none' })
    return
  }
  const safeOrderNo = orderNo.value
  const safeRefundAmount = validateRefundAmount()
  const safeDescription = desc.value.trim()
  submitting.value = true
  try {
    const response = await createAfterSales({ orderNo: safeOrderNo, afterSalesType: type.value, refundAmount: safeRefundAmount, reason: reason.value, description: safeDescription, evidenceUrls: images.value })
    assertAfterSalesResponse(response, safeOrderNo)
    submittedAfterSalesNo.value = response.afterSalesNo
    const modalOptions = {
      title: '售后申请已提交',
      content: `售后单 ${response.afterSalesNo} 已创建。售后申请已提交，处理进度以后端记录为准。`,
      showCancel: false,
      fail(error: unknown) {
        console.warn('after-sales apply success modal failed', { orderNo: safeOrderNo, afterSalesNo: response.afterSalesNo, error })
        redirectToAfterSalesDetail(response.afterSalesNo, safeOrderNo)
      },
      success() {
        redirectToAfterSalesDetail(response.afterSalesNo, safeOrderNo)
      }
    }
    try {
      uni.showModal(modalOptions)
    } catch (error) {
      console.warn('after-sales apply success modal failed', { orderNo: safeOrderNo, afterSalesNo: response.afterSalesNo, error })
      redirectToAfterSalesDetail(response.afterSalesNo, safeOrderNo)
    }
  } catch (error) {
    console.warn('after-sales apply submit failed', { orderNo: safeOrderNo, error })
    uni.showToast({ title: '售后申请提交失败，请检查订单状态后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
function redirectToAfterSalesDetail(afterSalesNo: string, safeOrderNo: string): void {
  const route = {
    url: `/pages/after-sales/detail/index?afterSalesNo=${encodeURIComponent(afterSalesNo)}&orderNo=${encodeURIComponent(safeOrderNo)}`,
    fail(error: unknown) {
      console.warn('after-sales apply detail redirect failed', { orderNo: safeOrderNo, afterSalesNo, error })
      uni.showToast({ title: '售后申请已提交，但暂时无法打开详情', icon: 'none' })
    }
  }
  try {
    uni.redirectTo(route)
  } catch (error) {
    console.warn('after-sales apply detail redirect failed', { orderNo: safeOrderNo, afterSalesNo, error })
    uni.showToast({ title: '售后申请已提交，但暂时无法打开详情', icon: 'none' })
  }
}
onMounted(readQuery)
</script>

<style scoped>
.after-page { min-height:100vh; padding-top:18rpx; padding-bottom:44rpx; background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%); }
.hero,.form-card,.safe-card,.status-card { margin-top:14rpx; padding:20rpx; border-color:rgba(255,217,189,.78); box-shadow:0 14rpx 30rpx rgba(132,70,36,.08); }
.hero { margin-top:0; display:flex; justify-content:space-between; align-items:center; gap:16rpx; background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96)); }
.form-card,.safe-card,.status-card { background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97)); }
.status-card.danger { border-color:#fecaca; background:#fff7f7; }
.kicker { color:#df6735; font-size:20rpx; font-weight:950; letter-spacing:.16rpx; }
.hero-icon { width:70rpx; height:70rpx; border-radius:24rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; display:flex; align-items:center; justify-content:center; font-size:32rpx; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); flex:0 0 auto; }
.section-title { color:#342116; font-size:26rpx; font-weight:950; letter-spacing:.16rpx; }
.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; margin-bottom:4rpx; }
.section-desc { margin-top:7rpx; color:#8f6b57; font-size:20rpx; line-height:1.43; font-weight:650; }
.danger-text { color:#be123c; }
.order-chip { flex:0 0 auto; padding:8rpx 14rpx; border:1rpx solid rgba(239,111,63,.20); border-radius:999rpx; background:#fff3e7; color:#df6735; font-size:20rpx; font-weight:950; }
.mt { margin-top:18rpx; }
.type-row,.reason-grid,.image-row { margin-top:12rpx; display:flex; gap:10rpx; flex-wrap:wrap; }
.type-chip,.reason-chip,.upload,.image-chip { padding:12rpx 16rpx; border-radius:999rpx; background:rgba(255,250,246,.96); border:1rpx solid rgba(255,217,189,.78); color:#7b5542; font-size:20rpx; font-weight:900; }
.type-chip.active,.reason-chip.active { background:#fff3e7; border-color:rgba(239,111,63,.66); color:#df6735; box-shadow:0 8rpx 16rpx rgba(255,122,69,.10); }
.field,.textarea { box-sizing:border-box; width:100%; margin-top:14rpx; padding:0 16rpx; border-radius:24rpx; background:linear-gradient(180deg,#fffdf9,#fff8f1); border:1rpx solid rgba(255,217,189,.78); color:#342116; font-size:22rpx; font-weight:650; }
.field { height:74rpx; }
.textarea { height:136rpx; padding-top:16rpx; line-height:1.48; }
.upload { margin-top:14rpx; width:100%; text-align:center; color:#df6735; background:linear-gradient(180deg,#fff7ef,#fff2e7); border-color:rgba(255,195,150,.66); box-sizing:border-box; }
.upload.busy { opacity:.72; }
.upload-title { font-size:21rpx; font-weight:950; }
.upload-desc { margin-top:5rpx; color:#8f6b57; font-size:19rpx; line-height:1.35; font-weight:650; }
.image-chip { padding:10rpx 14rpx; font-size:20rpx; color:#8f6b57; }
.primary-btn { margin-top:18rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); }
.safe-line { margin-top:8rpx; color:#8f6b57; font-size:20rpx; line-height:1.48; font-weight:650; }
</style>
