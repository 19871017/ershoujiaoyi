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
        <view class="section-title">申请内容</view>
        <view class="info-line"><text>类型</text><text>{{ typeText(detail.afterSalesType) }}</text></view>
        <view class="info-line"><text>原因</text><text>{{ detail.reason }}</text></view>
        <view class="desc">{{ detail.description }}</view>
        <view class="evidence-row"><view v-for="img in detail.evidenceUrls" :key="img" class="image-chip">上传票据</view></view>
      </view>

      <view class="timeline-card ds-card">
        <view class="section-title">处理进度</view>
        <view v-for="item in steps" :key="item.title" class="step">
          <view class="dot"></view>
          <view class="step-main">
            <view class="step-title">{{ item.title }}</view>
            <view class="step-desc">{{ item.desc }}</view>
            <view class="step-time">{{ item.time }}</view>
          </view>
        </view>
      </view>

      <view class="action-card ds-card">
        <view class="section-title">继续处理</view>
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
const orderNo = ref('')
const afterSalesNo = ref('')
const loading = ref(false)
const errorText = ref('')
const detail = ref<AfterSalesResponse | null>(null)
const steps = computed(() => {
  if (!detail.value) return []
  return [
    { title: '售后申请已提交', desc: '系统已记录退款原因、金额和已提交票据；售后处理以平台订单、支付、物流、聊天记录和票据记录为准。', time: detail.value.createdAt || '已提交' },
    { title: statusText(detail.value.status), desc: statusDesc(detail.value.status), time: detail.value.status === 'PENDING_REVIEW' ? '等待处理' : '已更新' }
  ]
})
function isValidAfterSalesNo(value: string) {
  return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeAfterSalesNo = current?.options?.afterSalesNo ?? hashParams?.get('afterSalesNo') ?? ''
  const routeOrderNo = current?.options?.orderNo ?? hashParams?.get('orderNo') ?? ''
  afterSalesNo.value = isValidAfterSalesNo(routeAfterSalesNo) ? routeAfterSalesNo : ''
  orderNo.value = routeOrderNo
}
async function loadDetail() {
  detail.value = null
  if (!isValidAfterSalesNo(afterSalesNo.value)) { errorText.value = '缺少有效售后单号，请从售后申请成功页进入'; return }
  loading.value = true; errorText.value = ''
  try { detail.value = await getAfterSalesDetail(afterSalesNo.value); orderNo.value = detail.value.orderNo }
  catch (error) { detail.value = null; errorText.value = error instanceof Error ? error.message : '售后详情读取失败' }
  finally { loading.value = false }
}
function statusText(status: AfterSalesStatus) { const map: Record<AfterSalesStatus,string> = { PENDING_REVIEW:'售后处理中', APPROVED:'售后已通过', REJECTED:'售后已驳回', CANCELLED:'售后已取消' }; return map[status] || status }
function statusDesc(status: AfterSalesStatus) { if (status === 'PENDING_REVIEW') return '处理进度以平台订单、支付、物流、聊天记录和已提交票据为准。'; if (status === 'APPROVED') return '售后申请已通过，请按平台处理结果继续操作。'; if (status === 'REJECTED') return '售后申请已驳回，可补充材料后再沟通。'; return '该售后单已取消。' }
function typeText(type: string) { const map: Record<string,string> = { REFUND_ONLY:'仅退款', RETURN_REFUND:'退货退款', PLATFORM_ARBITRATION:'售后协调' }; return map[type] || type }
function contactSeller() {
  if (!detail.value) return
  const target = resolveSellerContactTarget(detail.value, '售后单缺少有效卖家账号，不能发起聊天')
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${target.receiverId}&orderNo=${encodeURIComponent(detail.value.orderNo)}` })
}
function addEvidence() { if (!orderNo.value) return uni.showToast({ title: '缺少订单号', icon: 'none' }); uni.navigateTo({ url: `/pages/upload/evidence/index?scene=AFTER_SALES_EVIDENCE&orderNo=${encodeURIComponent(orderNo.value)}` }) }
onMounted(() => { readQuery(); void loadDetail() })
</script>
<style scoped>
.after-detail-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }.hero,.status-card,.timeline-card,.action-card,.info-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.hero,.status-card { display:flex; gap:16rpx; align-items:center; }.hero { justify-content:space-between; background:linear-gradient(135deg,#fff,#fff3e7); }.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }.hero-icon,.status-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.danger .status-icon{background:#ef4444}.section-title,.status-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.status-desc,.step-desc { margin-top:6rpx; color:#9b7560; font-size:22rpx; line-height:1.5; }.info-line{margin-top:14rpx;display:flex;justify-content:space-between;color:#7b5542;font-size:23rpx}.desc{margin-top:16rpx;padding:18rpx;border-radius:22rpx;background:#fffaf6;color:#3a2a1f;font-size:23rpx;line-height:1.55}.evidence-row{margin-top:14rpx;display:flex;gap:12rpx;flex-wrap:wrap}.image-chip{padding:12rpx 18rpx;border-radius:999rpx;background:#fff3e7;border:1rpx solid #ffd9bd;color:#ff7a45;font-size:22rpx;font-weight:900}.step { margin-top:18rpx; display:flex; gap:16rpx; }.dot { width:20rpx; height:20rpx; margin-top:8rpx; border-radius:50%; background:#ff7a45; box-shadow:0 0 0 8rpx #fff3e7; }.step-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }.step-time { margin-top:8rpx; color:#c0849d; font-size:20rpx; }.primary-btn,.secondary-btn { margin-top:16rpx; }
</style>
