<template>
  <view class="page-shell after-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 平台售后</view>
        <view class="page-title">申请售后/退款</view>
        <view class="page-desc">衣物鞋袜类交易请保留照片、聊天记录和物流凭证。</view>
      </view>
      <view class="hero-icon">🛟</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">售后类型</view>
      <view class="type-row">
        <view v-for="item in types" :key="item" class="type-chip tapable" :class="{ active: type === item }" @click="type = item">{{ item }}</view>
      </view>
      <input v-model="amount" class="field" type="digit" placeholder="退款金额" />
      <view class="section-title mt">原因</view>
      <view class="reason-grid">
        <view v-for="item in reasons" :key="item" class="reason-chip tapable" :class="{ active: reason === item }" @click="reason = item">{{ item }}</view>
      </view>
      <textarea v-model.trim="desc" class="textarea" maxlength="180" placeholder="请说明商品问题、协商过程和期望处理方式" />
      <view class="upload tapable" @click="chooseEvidence">＋ 上传凭证 {{ images.length }}/6</view>
      <view v-if="images.length" class="image-row"><view v-for="img in images" :key="img" class="image-chip">凭证</view></view>
      <button class="primary-btn" :disabled="submitting" @click="submitApply">{{ submitting ? '提交中...' : '提交售后申请' }}</button>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">处理规则</view>
      <view class="safe-line">售后处理以服务端订单、支付、物流、聊天记录和已提交票据为准。</view>
      <view class="safe-line">当前页面只提交售后申请；审核结论和公开展示状态以后端记录为准。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createAfterSales } from '../../../api/modules/after-sales'
import { createMediaUploadTicket } from '../../../api/modules/media'
const orderNo = ref('')
const submitting = ref(false)
const types = ['仅退款', '退货退款', '协商处理']
const type = ref('仅退款')
const amount = ref('')
const reasons = ['成色不符', '尺码不符', '未收到货', '物流异常', '其他']
const reason = ref('成色不符')
const desc = ref('')
const images = ref<string[]>([])
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  orderNo.value = current?.options?.orderNo || hashParams?.get('orderNo') || ''
}
function chooseEvidence() {
  const remain = Math.max(1, 6 - images.value.length)
  uni.chooseImage({ count: remain, sizeType: ['compressed'], sourceType: ['album', 'camera'], async success(res) {
    try {
      for (const path of res.tempFilePaths.slice(0, remain)) {
        if (path.startsWith('local://') || path.includes('placeholder')) throw new Error('凭证图片无效，请重新选择')
        const ticket = await createMediaUploadTicket({ scene: 'AFTER_SALES_EVIDENCE', contentType: imageContentType(path), fileSize: 300_000, filename: fileNameFromPath(path) })
        images.value.push(ticket.storageUrl)
      }
      images.value = images.value.slice(0, 6)
      uni.showToast({ title: `已生成上传票据 ${images.value.length} 张，提交后才会进入售后审核`, icon: 'none' })
    } catch (error) {
      uni.showToast({ title: error instanceof Error ? error.message : '凭证上传票据创建失败', icon: 'none' })
    }
  }, fail() { uni.showToast({ title: '请在手机端选择凭证图片', icon: 'none' }) } })
}
function fileNameFromPath(path: string) { const clean = path.split('?')[0] || ''; const last = clean.split('/').pop() || 'after-sales-evidence.jpg'; return last.includes('.') ? last : `${last}.jpg` }
function imageContentType(path: string) { const lower = path.toLowerCase(); if (lower.endsWith('.png')) return 'image/png'; if (lower.endsWith('.webp')) return 'image/webp'; return 'image/jpeg' }
function validate() {
  if (!orderNo.value) return '缺少订单号，请从订单详情发起售后'
  if (!amount.value || Number(amount.value) <= 0) return '请填写退款金额'
  if (!desc.value || desc.value.length < 8) return '请补充至少8个字的问题说明'
  if (!images.value.length) return '请至少上传一张售后凭证'
  if (images.value.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/evidence/after-sales/'))) return '凭证需先完成平台上传票据校验'
  return ''
}
async function submitApply() {
  const message = validate()
  if (message) return uni.showToast({ title: message, icon: 'none' })
  submitting.value = true
  try {
    const response = await createAfterSales({ orderNo: orderNo.value, afterSalesType: type.value, refundAmount: amount.value, reason: reason.value, description: desc.value, evidenceUrls: images.value })
    uni.showModal({ title: '售后申请已提交', content: `售后单 ${response.afterSalesNo} 已进入平台审核。`, showCancel: false, success: () => uni.redirectTo({ url: `/pages/after-sales/detail/index?afterSalesNo=${encodeURIComponent(response.afterSalesNo)}&orderNo=${encodeURIComponent(orderNo.value)}` }) })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '售后申请提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
onMounted(readQuery)
</script>

<style scoped>
.after-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }.hero,.form-card,.safe-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.hero { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.mt { margin-top:20rpx; }.type-row,.reason-grid,.image-row { margin-top:14rpx; display:flex; gap:12rpx; flex-wrap:wrap; }.type-chip,.reason-chip,.upload,.image-chip { padding:14rpx 18rpx; border-radius:999rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#7b5542; font-size:22rpx; font-weight:900; }.type-chip.active,.reason-chip.active { background:#fff3e7; border-color:#ff7a45; color:#ff7a45; }.field,.textarea { box-sizing:border-box; width:100%; margin-top:16rpx; padding:0 18rpx; border-radius:24rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:24rpx; }.field { height:82rpx; }.textarea { height:150rpx; padding-top:18rpx; }.upload { margin-top:16rpx; text-align:center; }.primary-btn { margin-top:18rpx; }.safe-line { margin-top:8rpx; color:#9b7560; font-size:22rpx; line-height:1.55; }
</style>
