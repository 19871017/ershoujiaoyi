<template>
  <section class="page-shell">
    <div class="page-title">审核详情</div>
    <div class="page-desc">读取持久化审核记录，展示目标、状态与备注。</div>

    <div class="toolbar">
      <RouterLink class="secondary-btn" to="/audit">返回审核工作台</RouterLink>
      <button class="primary-btn" :disabled="loading || !safeAuditNo" @click="load">刷新详情</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">审核详情加载中...</div>
    <div v-else-if="!detail" class="empty">请输入有效审核编号。</div>

    <article v-else class="detail-card">
      <header class="detail-head">
        <div>
          <strong>{{ detail.auditNo }}</strong>
          <span>{{ detail.auditType }} / {{ detail.targetType || '未标注目标' }}</span>
        </div>
        <b :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</b>
      </header>
      <dl class="detail-grid">
        <div><dt>目标编号</dt><dd>{{ detail.targetId || '暂无' }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
        <div><dt>复核时间</dt><dd>{{ detail.reviewedAt || '未复核' }}</dd></div>
        <div><dt>审核备注</dt><dd>{{ detail.reviewRemark || '暂无' }}</dd></div>
      </dl>
      <div v-if="chatTraceLocation || communityTraceLocation || orderTraceLocation || afterSalesTraceLocation" class="toolbar detail-trace-actions">
        <button v-if="chatTraceLocation" class="secondary-btn" @click="openChatTrace">私聊追溯</button>
        <button v-if="communityTraceLocation" class="secondary-btn" @click="openCommunityTrace">社区追溯</button>
        <button v-if="orderTraceLocation" class="secondary-btn" @click="openOrderTrace">订单追溯</button>
        <button v-if="afterSalesTraceLocation" class="secondary-btn" @click="openAfterSalesTrace">售后追溯</button>
      </div>
      <div v-if="videoEvidenceUrl" class="media-panel">
        <strong>视频认证资料</strong>
        <video
          v-if="videoEvidenceBlobUrl"
          class="audit-video"
          :src="videoEvidenceBlobUrl"
          controls
          playsinline
          @loadedmetadata="handleVideoProgress"
          @timeupdate="handleVideoProgress"
          @ended="handleVideoEnded"
        ></video>
        <button v-else class="secondary-btn" :disabled="loadingVideoEvidence" @click="loadVideoEvidence">
          {{ loadingVideoEvidence ? '正在加载视频...' : '授权加载视频' }}
        </button>
        <p v-if="videoEvidenceError" class="media-error">{{ videoEvidenceError }}</p>
      </div>
      <div v-else-if="detail.auditType === 'VIDEO_IDENTITY'" class="media-panel warning">
        <strong>视频认证资料暂不可预览</strong>
        <p>该审核记录没有通过平台上传票据校验，请退回让用户重新提交。</p>
      </div>
      <div v-if="reportEvidenceUrls.length" class="media-panel">
        <strong>举报凭证</strong>
        <div class="evidence-grid">
          <button v-for="(url, index) in reportEvidenceUrls" :key="url" class="evidence-link" :disabled="reportEvidenceLoading[url]" @click="openReportEvidence(url)">
            <span>凭证 {{ index + 1 }}</span>
            <small>{{ reportEvidenceBlobUrls[url] ? '已授权，可新窗口查看' : (reportEvidenceLoading[url] ? '正在加载...' : '授权查看') }}</small>
          </button>
        </div>
        <p v-if="reportEvidenceError" class="media-error">{{ reportEvidenceError }}</p>
      </div>
      <div v-if="reportGuide" class="ops-guide">
        <div class="ops-guide-head">
          <strong>{{ reportGuide.targetLabel }}</strong>
          <span>{{ reportGuide.traceHint }}</span>
        </div>
        <ol>
          <li v-for="step in reportGuide.steps" :key="step">{{ step }}</li>
        </ol>
        <div class="remark-templates">
          <span v-for="template in reportGuide.remarkTemplates" :key="template">{{ template }}</span>
        </div>
      </div>
      <p class="detail-desc">{{ detailSummary }}</p>
      <div v-if="detail.status === 'PENDING'" class="review-panel">
        <label>
          <span>审核备注</span>
          <textarea v-model.trim="reviewRemark" maxlength="200" :disabled="reviewing" placeholder="选填，仅提交给平台审核记录"></textarea>
        </label>
        <p v-if="videoApprovalHint" class="safe-note">{{ videoApprovalHint }}</p>
        <div v-if="canReviewAuditRecord(auth.session, detail.auditType)" class="review-actions">
          <button class="primary-btn" :disabled="reviewing || !detailCanApprove" @click="review('approve')">{{ reviewing ? '提交中...' : '通过审核' }}</button>
          <button class="danger-btn" :disabled="reviewing" @click="review('reject')">拒绝审核</button>
        </div>
        <p v-else class="safe-note">{{ detail.auditType === 'WITHDRAWAL' ? '当前管理员缺少 finance:review 权限，不能处理提现审核。' : '当前管理员缺少 audit:review 权限，不能提交审核。' }}</p>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { approveAdminAudit, getAdminAuditDetail, isValidAdminAuditNo, recordAdminVideoEvidenceProgress, rejectAdminAudit, type AuditRecordResponse } from '../../api'
import { requestBlob } from '../../api/http'
import { canReviewAuditRecord, useAuthStore } from '../../store/modules/auth'
import { afterSalesAuditTraceLocation } from '../after-sales/after-sales-trace-links'
import { chatAuditTraceLocation } from '../chat-trace/chat-trace-links'
import { communityAuditTraceLocation } from '../community-trace/community-trace-links'
import { orderAuditTraceLocation } from '../orders/order-trace-links'
import { canApproveAuditFromDetail, requiresVideoEvidenceBeforeApproval } from './audit-review-policy'
import { extractReportEvidenceUrls, reportEvidenceMediaUrl } from './report-evidence'
import { reportHandlingGuide } from './report-handling'
import { maskSensitiveMediaText } from './sensitive-media-text'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const detail = ref<AuditRecordResponse | null>(null)
const loading = ref(false)
const error = ref('')
const loadingVideoEvidence = ref(false)
const videoEvidenceBlobUrl = ref('')
const videoEvidenceError = ref('')
const videoWatchProgress = ref(0)
const videoWatchProgressMet = ref(false)
const reportingVideoWatchProgress = ref(false)
const reportEvidenceBlobUrls = ref<Record<string, string>>({})
const reportEvidenceLoading = ref<Record<string, boolean>>({})
const reportEvidenceError = ref('')
const reviewing = ref(false)
const reviewRemark = ref('')

const safeAuditNo = computed(() => {
  const value = String(route.params.auditNo || '')
  return isValidAdminAuditNo(value) ? value : ''
})
const videoEvidenceUrl = computed(() => {
  const current = detail.value
  return current?.auditType === 'VIDEO_IDENTITY' && current.videoEvidenceVerified === true && current.videoEvidenceUrl ? current.videoEvidenceUrl : ''
})
const detailSummary = computed(() => {
  const current = detail.value
  if (!current) return ''
  if (current.auditType === 'VIDEO_IDENTITY') return maskSensitiveMediaText(current.description) || '视频认证资料以平台上传票据为准。'
  if (current.auditType === 'REAL_NAME_IDENTITY') return `${maskSensitiveMediaText(current.reason) || '实名认证资料'}；${maskSensitiveMediaText(current.description) || '仅展示脱敏实名摘要。'}`
  return maskSensitiveMediaText(current.reason || current.description) || '无补充说明'
})
const reportEvidenceUrls = computed(() => {
  const current = detail.value
  if (current?.auditType !== 'REPORT') return []
  const structured = Array.isArray(current.reportEvidenceUrls) ? current.reportEvidenceUrls.filter(Boolean) : []
  return structured.length ? structured : extractReportEvidenceUrls(current.description)
})
const reportGuide = computed(() => detail.value ? reportHandlingGuide(detail.value) : null)
const chatTraceLocation = computed(() => detail.value ? chatAuditTraceLocation(detail.value) : null)
const communityTraceLocation = computed(() => detail.value ? communityAuditTraceLocation(detail.value) : null)
const afterSalesTraceLocation = computed(() => detail.value ? afterSalesAuditTraceLocation(detail.value) : null)
const orderTraceLocation = computed(() => detail.value ? orderAuditTraceLocation(detail.value) : null)
const detailCanApprove = computed(() => canApproveAuditFromDetail(detail.value, videoWatchProgressMet.value))
const videoApprovalHint = computed(() => {
  const current = detail.value
  if (!requiresVideoEvidenceBeforeApproval(current)) return ''
  if (!videoEvidenceUrl.value) return '视频认证资料未通过平台上传票据校验：请拒绝并要求用户重新提交。'
  if (videoWatchProgressMet.value) return '已观看达到复核要求，可以按结果通过或拒绝。'
  if (videoEvidenceBlobUrl.value) {
    const percent = Math.floor(videoWatchProgress.value * 100)
    return reportingVideoWatchProgress.value ? '正在记录视频观看进度...' : `视频认证通过前需观看到 80%，当前约 ${percent}%。`
  }
  return '视频认证通过前必须先点击“授权加载视频”，并观看到复核要求进度。'
})

async function load() {
  detail.value = null
  error.value = ''
  clearVideoEvidenceBlob()
  clearReportEvidenceBlobs()
  if (!safeAuditNo.value) {
    error.value = '审核编号无效，请输入正确的审核编号。'
    return
  }
  loading.value = true
  try {
    detail.value = await getAdminAuditDetail(safeAuditNo.value)
  } catch {
    detail.value = null
    error.value = '审核详情加载失败，请确认管理员权限与审核编号。'
  } finally {
    loading.value = false
  }
}

async function loadVideoEvidence() {
  if (!safeAuditNo.value || !videoEvidenceUrl.value) return
  loadingVideoEvidence.value = true
  videoEvidenceError.value = ''
  try {
    const blob = await requestBlob({ url: `/api/admin/audit/${encodeURIComponent(safeAuditNo.value)}/video-evidence` })
    clearVideoEvidenceBlob()
    videoEvidenceBlobUrl.value = URL.createObjectURL(blob)
  } catch (err) {
    console.warn('admin audit video evidence load failed', err)
    videoEvidenceError.value = '视频加载失败，请确认管理员会话与审核权限。'
  } finally {
    loadingVideoEvidence.value = false
  }
}

async function review(action: 'approve' | 'reject') {
  const current = detail.value
  if (!current || reviewing.value) return
  if (!canReviewAuditRecord(auth.session, current.auditType)) {
    error.value = current.auditType === 'WITHDRAWAL' ? '提现审核已阻止：当前管理员缺少 finance:review 权限。' : '审核操作已阻止：当前管理员缺少 audit:review 权限。'
    return
  }
  if (action === 'approve' && !detailCanApprove.value) {
    error.value = videoApprovalHint.value || '审核通过条件不足，未提交平台审核。'
    return
  }
  reviewing.value = true
  error.value = ''
  try {
    const defaultRemark = action === 'approve' ? '后台详情复核通过' : '后台详情复核拒绝'
    const remark = reviewRemark.value.trim() || defaultRemark
    detail.value = action === 'approve'
      ? await approveAdminAudit(current.auditNo, remark)
      : await rejectAdminAudit(current.auditNo, remark)
    reviewRemark.value = ''
  } catch (err) {
    console.warn('admin audit detail review failed', err)
    error.value = '审核操作失败，请确认审核状态、管理员权限与证据查看记录。'
  } finally {
    reviewing.value = false
  }
}

function clearVideoEvidenceBlob() {
  if (videoEvidenceBlobUrl.value) URL.revokeObjectURL(videoEvidenceBlobUrl.value)
  videoEvidenceBlobUrl.value = ''
  videoEvidenceError.value = ''
  videoWatchProgress.value = 0
  videoWatchProgressMet.value = false
  reportingVideoWatchProgress.value = false
}

function handleVideoProgress(event: Event) {
  const video = event.target instanceof HTMLVideoElement ? event.target : null
  if (!video) return
  void updateVideoWatchProgress(video, false)
}

function handleVideoEnded(event: Event) {
  const video = event.target instanceof HTMLVideoElement ? event.target : null
  if (!video) return
  void updateVideoWatchProgress(video, true)
}

async function updateVideoWatchProgress(video: HTMLVideoElement, ended: boolean) {
  if (!safeAuditNo.value || videoWatchProgressMet.value || reportingVideoWatchProgress.value) return
  const current = detail.value
  if (!current || !canReviewAuditRecord(auth.session, current.auditType)) return
  if (!Number.isFinite(video.duration) || video.duration <= 0 || !Number.isFinite(video.currentTime) || video.currentTime < 0) return
  const ratio = Math.min(Math.max(video.currentTime / video.duration, 0), 1)
  videoWatchProgress.value = Math.max(videoWatchProgress.value, ratio)
  if (!ended && ratio < 0.8) return
  reportingVideoWatchProgress.value = true
  try {
    await recordAdminVideoEvidenceProgress(safeAuditNo.value, {
      durationSeconds: video.duration,
      currentTimeSeconds: ended ? video.duration : video.currentTime,
      watchedRatio: ended ? 1 : ratio,
      ended
    })
    videoWatchProgress.value = ended ? 1 : Math.max(videoWatchProgress.value, ratio)
    videoWatchProgressMet.value = true
  } catch (err) {
    console.warn('admin audit video watch progress failed', err)
    videoEvidenceError.value = '视频观看进度记录失败，请确认管理员会话与 audit:review 权限后重新播放。'
  } finally {
    reportingVideoWatchProgress.value = false
  }
}

async function openReportEvidence(url: string) {
  if (!safeAuditNo.value) return
  reportEvidenceError.value = ''
  if (reportEvidenceBlobUrls.value[url]) {
    window.open(reportEvidenceBlobUrls.value[url], '_blank', 'noopener,noreferrer')
    return
  }
  reportEvidenceLoading.value = { ...reportEvidenceLoading.value, [url]: true }
  try {
    const blob = await requestBlob({ url: reportEvidenceMediaUrl(safeAuditNo.value, url) })
    const blobUrl = URL.createObjectURL(blob)
    reportEvidenceBlobUrls.value = { ...reportEvidenceBlobUrls.value, [url]: blobUrl }
    window.open(blobUrl, '_blank', 'noopener,noreferrer')
  } catch (err) {
    console.warn('admin audit report evidence load failed', err)
    reportEvidenceError.value = '举报凭证加载失败，请确认管理员会话与审核权限。'
  } finally {
    reportEvidenceLoading.value = { ...reportEvidenceLoading.value, [url]: false }
  }
}

function clearReportEvidenceBlobs() {
  for (const blobUrl of Object.values(reportEvidenceBlobUrls.value)) {
    URL.revokeObjectURL(blobUrl)
  }
  reportEvidenceBlobUrls.value = {}
  reportEvidenceLoading.value = {}
  reportEvidenceError.value = ''
}

function openAfterSalesTrace() {
  if (!afterSalesTraceLocation.value) {
    error.value = '审核目标无法追溯到售后记录。'
    return
  }
  router.push(afterSalesTraceLocation.value).catch(() => {
    error.value = '售后追溯页面打开失败，请稍后重试。'
  })
}

function openChatTrace() {
  if (!chatTraceLocation.value) {
    error.value = '审核目标无法追溯到私聊记录。'
    return
  }
  router.push(chatTraceLocation.value).catch(() => {
    error.value = '私聊追溯页面打开失败，请稍后重试。'
  })
}

function openCommunityTrace() {
  if (!communityTraceLocation.value) {
    error.value = '审核目标无法追溯到社区记录。'
    return
  }
  router.push(communityTraceLocation.value).catch(() => {
    error.value = '社区追溯页面打开失败，请稍后重试。'
  })
}

function openOrderTrace() {
  if (!orderTraceLocation.value) {
    error.value = '审核目标无法追溯到订单记录。'
    return
  }
  router.push(orderTraceLocation.value).catch(() => {
    error.value = '订单追溯页面打开失败，请稍后重试。'
  })
}

watch(videoEvidenceUrl, () => clearVideoEvidenceBlob())
watch(reportEvidenceUrls, () => clearReportEvidenceBlobs())
onMounted(load)
onBeforeUnmount(() => {
  clearVideoEvidenceBlob()
  clearReportEvidenceBlobs()
})
</script>
