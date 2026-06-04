<template>
  <view class="page-shell after-detail-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 售后进度</view>
        <view class="page-title">售后详情</view>
        <view class="page-desc">{{ detail ? `售后单 ${detail.afterSalesNo}` : '正在读取平台售后记录' }}</view>
      </view>
      <view class="hero-icon">🛡️</view>
    </view>

    <view v-if="loading" class="status-card ds-card">
      <view class="status-icon">⌛</view>
      <view><view class="status-title">正在加载</view><view class="status-desc">正在从平台读取售后记录。</view></view>
    </view>

    <view v-else-if="errorText" class="status-card ds-card danger">
      <view class="status-icon">!</view>
      <view><view class="status-title">读取失败</view><view class="status-desc">{{ errorText }}</view></view>
    </view>

    <template v-else-if="detail">
      <view class="status-card ds-card">
        <view class="status-icon">🛟</view>
        <view>
          <view class="status-title">{{ statusText(detail.status) }}</view>
          <view class="status-desc">订单 {{ detail.orderNo }} · 退款 ¥{{ detail.refundAmount }}</view>
        </view>
      </view>

      <view class="info-card ds-card">
        <view class="section-head">
          <view>
            <view class="section-title">申请内容</view>
            <view class="section-desc">售后状态、退款金额和票据均来自服务端售后记录。</view>
          </view>
          <view class="status-chip">{{ statusText(detail.status) }}</view>
        </view>
        <view class="info-line"><text>类型</text><text>{{ typeText(detail.afterSalesType) }}</text></view>
        <view class="info-line terminal-info-line"><text>原因</text><text>{{ detail.reason }}</text></view>
        <view class="desc">{{ detail.description }}</view>
        <view class="evidence-row"><view v-for="img in detail.evidenceUrls" :key="img" class="image-chip">上传票据</view></view>
      </view>

      <view class="timeline-card ds-card">
        <view class="section-title">处理进度</view>
        <view class="section-desc">进度文案仅根据后端售后状态渲染，不在前端推断审核结论。</view>
        <view v-for="item in steps" :key="item.title" class="step">
          <view class="dot"></view>
          <view class="step-main">
            <view class="step-title">{{ item.title }}</view>
            <view class="step-desc">{{ item.desc }}</view>
            <view class="step-time">{{ item.time }}</view>
          </view>
        </view>
      </view>

      <view v-if="nextAction" class="next-step-card ds-card">
        <view class="section-head">
          <view>
            <view class="section-title">下一步</view>
            <view class="section-desc">{{ nextAction.desc }}</view>
          </view>
          <view class="status-chip">{{ nextAction.title }}</view>
        </view>
        <view class="next-chip-row">
          <view v-for="chip in nextAction.chips" :key="chip" class="next-chip">{{ chip }}</view>
        </view>
        <button class="primary-btn next-primary" @click="runNextAction">{{ nextAction.primaryLabel }}</button>
      </view>

      <view class="action-card ds-card">
        <view class="section-title">可用操作</view>
        <button class="secondary-btn" @click="openOrderDetail">查看关联订单</button>
        <button class="secondary-btn" @click="contactSeller">联系卖家协商</button>
        <button class="primary-btn" @click="addEvidence">补充上传票据</button>
      </view>
    </template>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAfterSalesDetail, type AfterSalesResponse, type AfterSalesStatus } from '../../../api/modules/after-sales'
import { resolveSellerContactTarget } from '../../../api/modules/order-contact'
const backendOrderNoPattern = /^OD-[0-9]{1,10}$/
const evidenceStoragePrefix = '/uploads/evidence/after-sales/'
const orderNo = ref('')
const afterSalesNo = ref('')
const loading = ref(false)
const errorText = ref('')
const detail = ref<AfterSalesResponse | null>(null)
type NextActionType = 'order' | 'chat' | 'evidence'
interface NextAction {
  title: string
  desc: string
  chips: string[]
  primaryLabel: string
  primaryAction: NextActionType
}
const steps = computed(() => {
  if (!detail.value) return []
  return [
    { title: '售后申请已提交', desc: '系统已记录退款原因、金额和已提交票据；售后处理以平台订单、支付、物流、聊天记录和票据记录为准。', time: detail.value.createdAt || '已提交' },
    { title: statusText(detail.value.status), desc: statusDesc(detail.value.status), time: detail.value.status === 'PENDING_REVIEW' ? '等待处理' : '已更新' }
  ]
})
const nextAction = computed<NextAction | null>(() => {
  if (!detail.value) return null
  const commonEvidence = '保留照片、聊天记录和物流材料'
  if (detail.value.status === 'PENDING_REVIEW') {
    return {
      title: '等待处理',
      desc: '售后单已进入处理队列，可先补充票据或联系卖家协商；最终进度以服务端订单、支付、物流、聊天记录和售后记录为准。',
      chips: ['补充票据', '联系卖家', commonEvidence],
      primaryLabel: '补充票据',
      primaryAction: 'evidence'
    }
  }
  if (detail.value.status === 'APPROVED') {
    return {
      title: '查看结果',
      desc: '售后申请已通过，请查看关联订单和售后处理记录；后续结果以服务端订单、支付、物流和售后记录为准。',
      chips: ['查看关联订单', '确认处理记录', commonEvidence],
      primaryLabel: '查看关联订单',
      primaryAction: 'order'
    }
  }
  if (detail.value.status === 'REJECTED') {
    return {
      title: '补充协商',
      desc: '售后申请已驳回，如仍需处理，可补充票据并联系卖家继续协商；不要在聊天外完成交易或退款约定。',
      chips: ['补充票据', '联系卖家', '保留协商记录'],
      primaryLabel: '联系卖家协商',
      primaryAction: 'chat'
    }
  }
  return {
    title: '已取消',
    desc: '该售后单已取消，可查看关联订单确认当前订单状态，必要时再联系卖家沟通后续处理。',
    chips: ['查看关联订单', '确认订单状态', '保留沟通记录'],
    primaryLabel: '查看关联订单',
    primaryAction: 'order'
  }
})
function isValidAfterSalesNo(value: string): boolean {
  return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}
function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}
function isValidRefundAmount(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}
function isValidUserId(value: unknown): boolean {
  return Number.isSafeInteger(value) && Number(value) > 0
}
function isValidAfterSalesStatus(value: string): value is AfterSalesStatus {
  return value === 'PENDING_REVIEW' || value === 'APPROVED' || value === 'REJECTED' || value === 'CANCELLED'
}
function hasInvalidEvidenceUrl(url: unknown): boolean {
  if (typeof url !== 'string') return true
  const lower = url.toLowerCase()
  if (!url.startsWith(evidenceStoragePrefix)) return true
  if (url.startsWith('local://') || url.startsWith('blob:') || url.startsWith('data:')) return true
  if (url.includes('\\') || url.includes('..') || url.includes('//')) return true
  if (lower.includes('placeholder') || lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c')) return true
  const relativePath = url.slice(evidenceStoragePrefix.length)
  if (!relativePath || relativePath.split('/').some(segment => !segment || segment === '..')) return true
  return !/^\/uploads\/evidence\/after-sales\/[A-Za-z0-9][A-Za-z0-9._/-]*$/.test(url)
}
function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('after-sales detail route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}
function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeAfterSalesNo = decodeRouteValue('afterSalesNo', current?.options?.afterSalesNo ?? hashParams?.get('afterSalesNo') ?? '')
  const routeOrderNo = decodeRouteValue('orderNo', current?.options?.orderNo ?? hashParams?.get('orderNo') ?? '')
  if (routeAfterSalesNo && !isValidAfterSalesNo(routeAfterSalesNo)) {
    console.warn('after-sales detail invalid route afterSalesNo', { rawLength: routeAfterSalesNo.length, rawPreview: routeAfterSalesNo.slice(0, 24) })
  }
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    console.warn('after-sales detail invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  afterSalesNo.value = isValidAfterSalesNo(routeAfterSalesNo) ? routeAfterSalesNo : ''
  orderNo.value = isValidBackendOrderNo(routeOrderNo) ? routeOrderNo : ''
}
function assertAfterSalesDetailResponse(response: AfterSalesResponse, expectedAfterSalesNo: string, expectedOrderNo?: string): void {
  if (!isValidAfterSalesNo(response.afterSalesNo)) {
    throw new Error('after-sales detail invalid backend afterSalesNo')
  }
  if (response.afterSalesNo !== expectedAfterSalesNo) {
    throw new Error('after-sales detail afterSalesNo mismatch')
  }
  if (!isValidBackendOrderNo(response.orderNo)) {
    throw new Error('after-sales detail invalid backend orderNo')
  }
  if (expectedOrderNo && response.orderNo !== expectedOrderNo) throw new Error('after-sales detail orderNo mismatch')
  if (!isValidRefundAmount(response.refundAmount)) {
    throw new Error('after-sales detail invalid refund amount')
  }
  if (!isValidAfterSalesStatus(response.status)) {
    throw new Error('after-sales detail invalid backend status')
  }
  if (!isValidUserId(response.sellerId)) {
    throw new Error('after-sales detail invalid sellerId')
  }
  if (!Array.isArray(response.evidenceUrls) || response.evidenceUrls.length === 0 || response.evidenceUrls.some(hasInvalidEvidenceUrl)) {
    throw new Error('after-sales detail invalid evidence url')
  }
}
async function loadDetail(): Promise<void> {
  detail.value = null
  if (!isValidAfterSalesNo(afterSalesNo.value)) {
    errorText.value = '缺少有效售后单号，请从售后申请成功页进入'
    return
  }
  const safeAfterSalesNo = afterSalesNo.value
  const safeRouteOrderNo = isValidBackendOrderNo(orderNo.value) ? orderNo.value : undefined
  loading.value = true
  errorText.value = ''
  try {
    const response = await getAfterSalesDetail(safeAfterSalesNo)
    assertAfterSalesDetailResponse(response, safeAfterSalesNo, safeRouteOrderNo)
    detail.value = response
    orderNo.value = response.orderNo
  } catch (error) {
    detail.value = null
    console.warn('after-sales detail load failed', { afterSalesNo: safeAfterSalesNo, orderNo: safeRouteOrderNo, error })
    errorText.value = '售后详情读取失败，请稍后重试或从售后申请记录重新进入'
  } finally {
    loading.value = false
  }
}
function statusText(status: AfterSalesStatus): string {
  const map: Record<AfterSalesStatus, string> = { PENDING_REVIEW: '售后处理中', APPROVED: '售后已通过', REJECTED: '售后已驳回', CANCELLED: '售后已取消' }
  return map[status] || '未知状态'
}
function statusDesc(status: AfterSalesStatus): string {
  if (status === 'PENDING_REVIEW') return '处理进度以服务端订单、支付、物流、聊天记录和已提交票据为准。'
  if (status === 'APPROVED') return '售后申请已通过，请按服务端处理结果继续操作。'
  if (status === 'REJECTED') return '售后申请已驳回，可补充材料后再沟通。'
  return '该售后单已取消。'
}
function typeText(type: string): string {
  const map: Record<string, string> = { REFUND_ONLY: '仅退款', RETURN_REFUND: '退货退款', PLATFORM_ARBITRATION: '售后协调' }
  return map[type] || '未知类型'
}
function runNextAction(): void {
  const action = nextAction.value?.primaryAction
  if (action === 'order') return openOrderDetail()
  if (action === 'chat') return contactSeller()
  if (action === 'evidence') return addEvidence()
}
function contactSeller(): void {
  const currentDetail = detail.value
  if (!currentDetail) return
  if (!isValidAfterSalesNo(currentDetail.afterSalesNo)) return uni.showToast({ title: '售后单号异常，不能发起聊天', icon: 'none' })
  if (!isValidBackendOrderNo(currentDetail.orderNo)) return uni.showToast({ title: '订单编号异常，不能发起售后聊天', icon: 'none' })
  try {
    const target = resolveSellerContactTarget(currentDetail, '售后单缺少有效卖家账号，不能发起聊天')
    if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
    const route = {
      url: `/pages/chat/conversation/index?receiverId=${encodeURIComponent(target.receiverId)}&orderNo=${encodeURIComponent(currentDetail.orderNo)}`,
      fail: (error: unknown) => {
        console.warn('after-sales chat navigation failed', { afterSalesNo: currentDetail.afterSalesNo, orderNo: currentDetail.orderNo, receiverId: target.receiverId, error })
        uni.showToast({ title: '暂时无法打开聊天，请稍后重试', icon: 'none' })
      }
    }
    uni.navigateTo(route)
  } catch (error) {
    console.warn('after-sales chat navigation failed', { afterSalesNo: currentDetail.afterSalesNo, orderNo: currentDetail.orderNo, error })
    uni.showToast({ title: '暂时无法打开聊天，请稍后重试', icon: 'none' })
  }
}
function openOrderDetail(): void {
  const currentDetail = detail.value
  if (!currentDetail) return
  if (!isValidAfterSalesNo(currentDetail.afterSalesNo)) return uni.showToast({ title: '售后单号异常，未打开订单', icon: 'none' })
  if (!isValidBackendOrderNo(currentDetail.orderNo)) return uni.showToast({ title: '订单编号异常，未打开订单', icon: 'none' })
  const route = {
    url: `/pages/order/detail/index?orderNo=${encodeURIComponent(currentDetail.orderNo)}`,
    fail: (error: unknown) => {
      console.warn('after-sales order navigation failed', { afterSalesNo: currentDetail.afterSalesNo, orderNo: currentDetail.orderNo, error })
      uni.showToast({ title: '暂时无法打开关联订单，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('after-sales order navigation failed', { afterSalesNo: currentDetail.afterSalesNo, orderNo: currentDetail.orderNo, error })
    uni.showToast({ title: '暂时无法打开关联订单，请稍后重试', icon: 'none' })
  }
}
function addEvidence(): void {
  const currentDetail = detail.value
  if (!currentDetail || !isValidAfterSalesNo(currentDetail.afterSalesNo)) return uni.showToast({ title: '缺少有效售后单号，不能补充票据', icon: 'none' })
  if (!isValidBackendOrderNo(currentDetail.orderNo)) return uni.showToast({ title: '缺少有效订单号，不能补充票据', icon: 'none' })
  const route = {
    url: `/pages/upload/evidence/index?scene=AFTER_SALES_EVIDENCE&orderNo=${encodeURIComponent(currentDetail.orderNo)}`,
    fail: (error: unknown) => {
      console.warn('after-sales evidence navigation failed', { afterSalesNo: currentDetail.afterSalesNo, orderNo: currentDetail.orderNo, error })
      uni.showToast({ title: '暂时无法打开票据上传页，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('after-sales evidence navigation failed', { afterSalesNo: currentDetail.afterSalesNo, orderNo: currentDetail.orderNo, error })
    uni.showToast({ title: '暂时无法打开票据上传页，请稍后重试', icon: 'none' })
  }
}
async function initializeDetail(): Promise<void> {
  try {
    readQuery()
    await loadDetail()
  } catch (error) {
    detail.value = null
    loading.value = false
    console.warn('after-sales detail initialize failed', { error })
    errorText.value = '售后详情读取失败，请从售后申请记录重新进入'
  }
}
onMounted(() => { void initializeDetail() })
</script>
<style scoped>
.after-detail-page { min-height:100vh; padding-top:18rpx; padding-bottom:44rpx; background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%); }
.hero,.status-card,.timeline-card,.action-card,.info-card,.next-step-card { margin-top:14rpx; padding:20rpx; border-color:rgba(255,217,189,.78); box-shadow:0 14rpx 30rpx rgba(132,70,36,.08); }
.hero,.status-card { display:flex; gap:14rpx; align-items:center; background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96)); }
.hero { margin-top:0; justify-content:space-between; }
.timeline-card,.action-card,.info-card,.next-step-card { background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97)); }
.kicker { color:#df6735; font-size:20rpx; font-weight:950; letter-spacing:.16rpx; }
.hero-icon,.status-icon { width:70rpx; height:70rpx; border-radius:24rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; display:flex; align-items:center; justify-content:center; font-size:32rpx; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); flex:0 0 auto; }
.danger { border-color:#fecaca; background:#fff7f7; }
.danger .status-icon { background:#ef4444; box-shadow:0 12rpx 24rpx rgba(239,68,68,.14); }
.section-title,.status-title { color:#342116; font-size:26rpx; font-weight:950; letter-spacing:.16rpx; }
.section-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; margin-bottom:6rpx; }
.section-desc { margin-top:7rpx; color:#8f6b57; font-size:20rpx; line-height:1.43; font-weight:650; }
.status-chip { flex:0 0 auto; padding:8rpx 14rpx; border:1rpx solid rgba(239,111,63,.20); border-radius:999rpx; background:#fff3e7; color:#df6735; font-size:20rpx; font-weight:950; }
.status-desc,.step-desc { margin-top:6rpx; color:#8f6b57; font-size:20rpx; line-height:1.48; font-weight:650; }
.info-line { min-height:48rpx; display:flex; justify-content:space-between; align-items:center; gap:16rpx; border-bottom:1rpx solid rgba(255,217,189,.52); color:#7b5542; font-size:21rpx; font-weight:760; }
.terminal-info-line { border-bottom:0; }
.info-line text:last-child { color:#342116; font-weight:930; text-align:right; }
.desc { margin-top:14rpx; padding:16rpx; border-radius:24rpx; background:linear-gradient(180deg,#fffdf9,#fff8f1); border:1rpx solid rgba(255,217,189,.64); color:#342116; font-size:21rpx; line-height:1.55; font-weight:650; }
.evidence-row { margin-top:12rpx; display:flex; gap:10rpx; flex-wrap:wrap; }
.image-chip { padding:10rpx 14rpx; border-radius:999rpx; background:#fff3e7; border:1rpx solid rgba(239,111,63,.24); color:#df6735; font-size:20rpx; font-weight:900; box-shadow:0 8rpx 16rpx rgba(255,122,69,.08); }
.next-chip-row { display:flex; gap:10rpx; flex-wrap:wrap; margin-top:14rpx; }
.next-chip { padding:10rpx 14rpx; border-radius:999rpx; background:#fffaf4; border:1rpx solid rgba(255,217,189,.82); color:#7b5542; font-size:20rpx; font-weight:900; }
.next-primary { margin-top:16rpx; }
.step { margin-top:16rpx; display:flex; gap:14rpx; }
.step-main { flex:1; min-width:0; padding-bottom:2rpx; }
.dot { width:18rpx; height:18rpx; margin-top:7rpx; border-radius:50%; background:#ef6f3f; box-shadow:0 0 0 7rpx rgba(239,111,63,.10); flex:0 0 auto; }
.step-title { color:#342116; font-size:23rpx; font-weight:950; letter-spacing:.12rpx; }
.step-time { margin-top:7rpx; color:#b77955; font-size:19rpx; font-weight:850; }
.action-card .secondary-btn,.action-card .primary-btn { margin-top:14rpx; }
.action-card .primary-btn { background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; box-shadow:0 12rpx 24rpx rgba(255,122,69,.16); }
</style>
