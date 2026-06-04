<template>
  <view class="page-shell report-page">
    <view class="hero ds-card">
      <view class="icon">🛡️</view>
      <view>
        <view class="page-title">提交举报</view>
        <view class="page-desc">用于提交商品、聊天、订单、售后或用户相关问题；举报处理以服务端审核记录为准。</view>
      </view>
    </view>

    <view class="target-card ds-card">
      <view class="section-title">举报对象</view>
      <view class="target-row"><text>类型</text><text>{{ targetTypeLabel }}</text></view>
      <view class="target-row"><text>编号</text><text>{{ targetId || '未指定' }}</text></view>
      <view v-if="routeError" class="target-error">{{ routeError }}</view>
    </view>

    <view class="reason-card ds-card">
      <view class="section-title">举报原因</view>
      <view class="reason-grid">
        <view v-for="item in reasons" :key="item" class="reason-chip tapable" :class="{ active: reason === item }" @click="reason = item">{{ item }}</view>
      </view>
      <textarea v-model="description" class="textarea" placeholder="请补充聊天、商品、订单中的具体问题；处理进度以服务端审核记录为准" />
      <view class="upload-box tapable" :class="{ busy: uploadingEvidence }" @click="chooseEvidence">
        <view class="upload-icon">＋</view>
        <view>
          <view class="upload-title">{{ uploadingEvidence ? '票据上传中' : '上传票据' }} {{ evidence.length }}/{{ maxEvidenceImages }}</view>
          <view class="upload-desc">证据文件上传成功后，提交举报时才会随表单提交。</view>
        </view>
      </view>
      <view v-if="evidence.length" class="evidence-list">
        <view v-for="(url, index) in evidence" :key="url" class="evidence-item">
          <text>票据 {{ index + 1 }}</text>
          <text>{{ url }}</text>
        </view>
      </view>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">处理说明</view>
      <view class="safe-line">举报处理以服务端审核记录为准，上传票据不代表业务已受理。</view>
      <view class="safe-line">如涉及资金，请以服务端订单、支付和售后状态为准。</view>
    </view>

    <button class="submit-btn" :disabled="submitting || uploadingEvidence || !!routeError" @click="submit">{{ submitting ? '提交中...' : '提交举报' }}</button>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { submitReport, type AuditRecordResponse, type AuditStatus } from '../../../api/modules/audit'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'

type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
type ReportTargetType = 'GOODS' | 'PRODUCT' | 'CHAT' | 'ORDER' | 'AFTER_SALES' | 'USER' | 'REPORT'
type ChooseImageFile = { name?: string; type?: string; size?: number }
type ChooseImageResult = { tempFilePaths?: string[]; tempFiles?: ChooseImageFile[] }

const launchReadinessMarkers = [
  '举报上传票据需先完成服务端校验',
  '无效举报证据链接未提交',
  '举报处理以服务端审核记录为准'
]

const maxEvidenceImages = 6
const reportEvidenceStoragePrefix = '/uploads/report-evidence/'
const targetType = ref<ReportTargetType>('GOODS')
const targetId = ref('')
const routeError = ref('')
const reason = ref('私下交易引导')
const description = ref('')
const evidence = ref<string[]>([])
const uploadingEvidence = ref(false)
const submitting = ref(false)
const reasons = ['私下交易引导', '商品描述不符', '疑似假货', '骚扰/辱骂', '虚假定位', '其他风险']
const targetTypeLabel = computed(() => {
  const map: Record<ReportTargetType, string> = { GOODS: '商品', PRODUCT: '商品', CHAT: '聊天', ORDER: '订单', AFTER_SALES: '售后', USER: '用户', REPORT: '举报' }
  return map[targetType.value] || targetType.value
})
function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('report submit route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}
function isValidReportTargetType(value: string): value is ReportTargetType {
  return value === 'GOODS' || value === 'PRODUCT' || value === 'CHAT' || value === 'ORDER' || value === 'AFTER_SALES' || value === 'USER' || value === 'REPORT'
}
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const rawTargetType = current?.options?.targetType ?? hashParams?.get('targetType') ?? 'GOODS'
  const rawTargetId = current?.options?.targetId ?? hashParams?.get('targetId') ?? ''
  const routeTargetType = decodeRouteValue('targetType', rawTargetType).toUpperCase()
  const routeTargetId = decodeRouteValue('targetId', rawTargetId)
  if (!isValidReportTargetType(routeTargetType) || !isValidReportTargetId(routeTargetId, routeTargetType)) {
    console.warn('report submit invalid route target', { targetType: routeTargetType, targetIdLength: routeTargetId.length, targetIdPreview: routeTargetId.slice(0, 24) })
    routeError.value = '缺少有效举报对象，请从商品、聊天、订单、售后或用户页面发起举报'
    targetId.value = ''
    return
  }
  targetType.value = routeTargetType
  targetId.value = routeTargetId
  routeError.value = ''
}
function isImageContentType(value: string | undefined): value is ImageContentType {
  return value === 'image/png' || value === 'image/webp' || value === 'image/jpeg'
}
function guessContentType(path: string, fallbackType?: string): ImageContentType {
  const normalizedType = fallbackType?.toLowerCase()
  if (isImageContentType(normalizedType)) return normalizedType
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}
function filenameFromPath(path: string, index: number, fallbackName = `report-evidence-${Date.now()}-${index}.jpg`) {
  const raw = path.split('/').pop() || fallbackName
  return raw.includes('.') ? raw : fallbackName
}
function imageFileSize(file?: ChooseImageFile) {
  return Math.max(1, Math.min(Number(file?.size || 600_000), 10_000_000))
}
function imageFallbackName(contentType: ImageContentType, index: number) {
  if (contentType === 'image/png') return `report-evidence-${index}.png`
  if (contentType === 'image/webp') return `report-evidence-${index}.webp`
  return `report-evidence-${index}.jpg`
}
function isValidReportTargetId(value: string, type = targetType.value) {
  const normalizedType = (type || '').toUpperCase()
  if (normalizedType !== 'AFTER_SALES' && /^[1-9]\d{0,18}$/.test(value)) return true
  const patterns: Record<string, RegExp> = {
    GOODS: /^(GOODS|PRODUCT)-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    PRODUCT: /^(GOODS|PRODUCT)-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    ORDER: /^(ORDER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}|OD-[1-9][0-9]{0,9})$/,
    AFTER_SALES: /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    CHAT: /^CHAT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    USER: /^USER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    REPORT: /^REPORT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/
  }
  return patterns[normalizedType]?.test(value) === true
}
function hasInvalidTempReportEvidencePath(path: string): boolean {
  const lower = path.toLowerCase()
  return !path || path.startsWith('local://') || path.startsWith('data:') || path.includes('placeholder') || lower.includes('placeholder')
}
function hasInvalidReportEvidenceUrl(url: unknown): boolean {
  if (typeof url !== 'string') return true
  const lower = url.toLowerCase()
  const relativePath = url.startsWith(reportEvidenceStoragePrefix) ? url.slice(reportEvidenceStoragePrefix.length) : ''
  return !relativePath ||
    url.startsWith('local://') ||
    url.startsWith('blob:') ||
    url.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    url.includes('\\') ||
    url.includes('..') ||
    url.includes('//') ||
    relativePath.split('/').some(segment => !segment)
}
function isPickerCancel(error: unknown): boolean {
  return typeof error === 'object' && error !== null && String((error as { errMsg?: unknown }).errMsg || '').toLowerCase().includes('cancel')
}
function isValidAuditStatus(value: unknown): value is AuditStatus {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED'
}
function isValidAuditNo(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{6,64}$/.test(value)
}
function assertReportResponse(response: AuditRecordResponse, expectedTargetType: ReportTargetType, expectedTargetId: string): void {
  if (!isValidAuditNo(response.auditNo)) throw new Error('report invalid auditNo')
  if (!Number.isSafeInteger(response.userId) || response.userId <= 0) throw new Error('report invalid userId')
  if (response.targetType !== expectedTargetType) throw new Error('report targetType mismatch')
  if (response.targetId !== expectedTargetId) throw new Error('report targetId mismatch')
  if (!response.reason) throw new Error('report invalid reason')
  if (!isValidAuditStatus(response.status)) throw new Error('report invalid audit status')
}
function chooseEvidence() {
  if (uploadingEvidence.value) { uni.showToast({ title: '票据上传中，请稍后再选', icon: 'none' }); return }
  const remaining = Math.max(0, maxEvidenceImages - evidence.value.length)
  if (remaining <= 0) { uni.showToast({ title: `上传票据上限为 ${maxEvidenceImages} 张`, icon: 'none' }); return }
  uploadingEvidence.value = true
  try {
    uni.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res: ChooseImageResult) => {
        const paths = (res.tempFilePaths || []).slice(0, remaining)
        uni.showLoading({ title: '校验票据中' })
        try {
          if (!paths.length) throw new Error('未选择到有效票据图片，请重新选择')
          const urls: string[] = []
          const files = res.tempFiles || []
          for (let i = 0; i < paths.length; i += 1) {
            const path = paths[i] || ''
            if (hasInvalidTempReportEvidencePath(path)) throw new Error('举报票据图片无效，请重新选择')
            const file = files[i]
            const contentType = guessContentType(path, file?.type)
            const ticket = await createMediaUploadTicket({ scene: 'REPORT_EVIDENCE', contentType, fileSize: imageFileSize(file), filename: filenameFromPath(file?.name || path, i, imageFallbackName(contentType, i)) })
            const uploaded = await uploadMediaTicketFile(ticket, path)
            if (hasInvalidReportEvidenceUrl(uploaded.storageUrl)) throw new Error('举报上传票据需先完成服务端校验')
            urls.push(uploaded.storageUrl)
          }
          evidence.value = [...evidence.value, ...urls].slice(0, maxEvidenceImages)
          uni.showToast({ title: `票据已上传 ${evidence.value.length} 张，提交举报后才会进入审核`, icon: 'none' })
        } catch (error) {
          console.warn('report evidence upload failed', { error })
          uni.showToast({ title: error instanceof Error ? error.message : '举报证据上传失败，请重新选择图片后再试', icon: 'none' })
        } finally {
          uploadingEvidence.value = false
          uni.hideLoading()
        }
      },
      fail: (error: unknown) => {
        uploadingEvidence.value = false
        console.warn('report evidence picker failed', { cancelled: isPickerCancel(error), error })
        uni.showToast({ title: isPickerCancel(error) ? '已取消选择图片' : '无法选择举报票据，请检查相册权限后重试', icon: 'none' })
      }
    })
  } catch (error) {
    uploadingEvidence.value = false
    console.warn('report evidence picker failed', { error })
    uni.showToast({ title: '无法打开图片选择器，请检查相册权限后重试', icon: 'none' })
  }
}
async function submit() {
  if (submitting.value) return
  if (uploadingEvidence.value) return uni.showToast({ title: '票据上传中，请稍后提交', icon: 'none' })
  if (routeError.value) return uni.showToast({ title: routeError.value, icon: 'none' })
  if (!reason.value) return uni.showToast({ title: '请选择举报原因', icon: 'none' })
  if (description.value.trim().length < 6) return uni.showToast({ title: '请补充至少 6 个字说明', icon: 'none' })
  if (!isValidReportTargetId(targetId.value)) return uni.showToast({ title: '缺少有效举报对象，未提交举报', icon: 'none' })
  if (evidence.value.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/report-evidence/') || hasInvalidReportEvidenceUrl(url))) {
    return uni.showToast({ title: '举报上传票据需先完成服务端校验，无效举报证据链接未提交', icon: 'none' })
  }
  const safeTargetType = targetType.value
  const safeTargetId = targetId.value
  const safeDescription = description.value.trim()
  submitting.value = true
  try {
    const response = await submitReport({ targetType: targetType.value, targetId: targetId.value, reason: reason.value, description: safeDescription, evidenceUrls: evidence.value })
    assertReportResponse(response, safeTargetType, safeTargetId)
    showReportSuccessModal(response)
  } catch (error) {
    console.warn('report submit failed', { targetType: safeTargetType, targetId: safeTargetId, error })
    uni.showModal({ title: '提交失败', content: '举报没有提交成功，请检查网络或稍后重试。', showCancel: false })
  } finally { submitting.value = false }
}
function showReportSuccessModal(response: AuditRecordResponse): void {
  const modalOptions = {
    title: '已提交',
    content: `举报 ${response.auditNo} 已由服务端接收；审核状态、通知和后续处理以服务端审核记录为准。`,
    showCancel: false,
    fail(error: unknown) {
      console.warn('report submit success modal failed', { auditNo: response.auditNo, error })
      uni.showToast({ title: '举报已提交，但确认弹窗无法显示，请到通知中心查看处理进度', icon: 'none' })
    },
    success() {
      const route = {
        url: '/pages/notification/index',
        fail(error: unknown) {
          console.warn('report notification navigation failed', { auditNo: response.auditNo, error })
          uni.showToast({ title: '举报已提交，但暂时无法打开通知中心', icon: 'none' })
        }
      }
      try {
        uni.navigateTo(route)
      } catch (error) {
        console.warn('report notification navigation failed', { auditNo: response.auditNo, error })
        uni.showToast({ title: '举报已提交，但暂时无法打开通知中心', icon: 'none' })
      }
    }
  }
  try {
    uni.showModal(modalOptions)
  } catch (error) {
    console.warn('report submit success modal failed', { auditNo: response.auditNo, error })
    uni.showToast({ title: '举报已提交，但确认弹窗无法显示，请到通知中心查看处理进度', icon: 'none' })
  }
}
onMounted(readQuery)
</script>

<style scoped>
.report-page { min-height:100vh; padding-top:16rpx; padding-bottom:40rpx; background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%); }
.hero,.target-card,.reason-card,.safe-card { margin-top:14rpx; padding:20rpx; border-color:rgba(255,217,189,.78); box-shadow:0 14rpx 28rpx rgba(132,70,36,.08); }
.hero { margin-top:0; gap:16rpx; background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96)); }
.hero,.target-row,.upload-box { display:flex; align-items:center; }
.target-card,.reason-card,.safe-card { background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97)); }
.icon,.upload-icon,.reason-chip { display:flex; align-items:center; justify-content:center; }
.icon { width:70rpx; height:70rpx; border-radius:24rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; font-size:32rpx; box-shadow:0 12rpx 24rpx rgba(255,122,69,.17); flex:0 0 auto; }
.section-title { color:#342116; font-size:26rpx; font-weight:950; letter-spacing:.16rpx; }
.target-row { min-height:52rpx; justify-content:space-between; gap:16rpx; color:#7b5542; font-size:22rpx; font-weight:760; border-bottom:1rpx solid rgba(255,217,189,.52); }
.target-row:last-child { border-bottom:0; }
.target-row text:first-child { flex:0 0 auto; color:#8f6b57; font-weight:850; }
.target-row text:last-child { color:#342116; font-weight:930; text-align:right; word-break:break-all; }
.target-error { margin-top:12rpx; padding:10rpx 12rpx; border-radius:18rpx; background:#fff1f2; color:#be123c; font-size:20rpx; line-height:1.42; font-weight:800; }
.reason-grid { margin-top:14rpx; display:grid; grid-template-columns:repeat(2, 1fr); gap:10rpx; }
.reason-chip { min-height:56rpx; border-radius:20rpx; background:rgba(255,250,246,.96); border:1rpx solid rgba(255,217,189,.78); color:#7b5542; text-align:center; font-size:21rpx; font-weight:900; }
.reason-chip.active { background:#fff3e7; color:#df6735; border-color:rgba(239,111,63,.66); box-shadow:0 8rpx 16rpx rgba(255,122,69,.10); }
.textarea,.evidence-item { background:linear-gradient(180deg,#fffdf9,#fff8f1); }
.textarea { box-sizing:border-box; width:100%; height:150rpx; margin-top:14rpx; padding:16rpx; border-radius:22rpx; border:1rpx solid rgba(255,217,189,.78); color:#342116; font-size:22rpx; font-weight:650; line-height:1.5; }
.upload-box { margin-top:14rpx; padding:16rpx; border-radius:22rpx; background:linear-gradient(180deg,#fff7ef,#fff2e7); border:1rpx solid rgba(255,195,150,.66); gap:12rpx; }
.upload-box.busy { opacity:.72; }
.upload-icon { width:52rpx; height:52rpx; border-radius:18rpx; background:rgba(255,255,255,.92); color:#df6735; font-size:32rpx; font-weight:900; box-shadow:inset 0 0 0 1rpx rgba(255,217,189,.46); flex:0 0 auto; }
.upload-title { color:#342116; font-size:22rpx; font-weight:950; }
.upload-desc,.safe-line { margin-top:6rpx; color:#8f6b57; font-size:20rpx; line-height:1.45; font-weight:650; }
.evidence-list { margin-top:12rpx; display:flex; flex-direction:column; gap:9rpx; }
.evidence-item { padding:11rpx 13rpx; border-radius:16rpx; border:1rpx solid rgba(255,217,189,.66); color:#7b5542; font-size:19rpx; font-weight:760; display:flex; justify-content:space-between; gap:12rpx; }
.evidence-item text:last-child { max-width:470rpx; color:#342116; text-align:right; word-break:break-all; }
.submit-btn { margin-top:20rpx; height:68rpx; line-height:68rpx; border-radius:999rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; font-size:23rpx; font-weight:950; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); }
</style>
