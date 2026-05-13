<template>
  <view class="page-shell report-page">
    <view class="hero ds-card">
      <view class="icon">🛡️</view>
      <view>
        <view class="page-title">提交举报</view>
        <view class="page-desc">用于提交商品、聊天、订单或用户相关问题；举报处理以平台审核记录为准。</view>
      </view>
    </view>

    <view class="target-card ds-card">
      <view class="section-title">举报对象</view>
      <view class="target-row"><text>类型</text><text>{{ targetTypeLabel }}</text></view>
      <view class="target-row"><text>编号</text><text>{{ targetId || '未指定' }}</text></view>
    </view>

    <view class="reason-card ds-card">
      <view class="section-title">举报原因</view>
      <view class="reason-grid">
        <view v-for="item in reasons" :key="item" class="reason-chip tapable" :class="{ active: reason === item }" @click="reason = item">{{ item }}</view>
      </view>
      <textarea v-model="description" class="textarea" placeholder="请补充聊天、商品、订单中的具体问题；处理进度以平台审核记录为准" />
      <view class="upload-box tapable" @click="chooseEvidence">
        <view class="upload-icon">＋</view>
        <view>
          <view class="upload-title">生成上传票据</view>
          <view class="upload-desc">已生成 {{ evidence.length }} 张举报上传票据，上限 6 张</view>
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
      <view class="safe-line">举报处理以平台审核记录为准，上传票据不代表举报已受理。</view>
      <view class="safe-line">如涉及资金，请以平台订单、支付和售后状态为准。</view>
    </view>

    <button class="submit-btn" :disabled="submitting" @click="submit">{{ submitting ? '提交中...' : '提交举报' }}</button>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { submitReport } from '../../../api/modules/audit'
import { createMediaUploadTicket } from '../../../api/modules/media'

const targetType = ref('GOODS')
const targetId = ref('')
const reason = ref('私下交易引导')
const description = ref('')
const evidence = ref<string[]>([])
const submitting = ref(false)
const reasons = ['私下交易引导', '商品描述不符', '疑似假货', '骚扰/辱骂', '虚假定位', '其他风险']
const targetTypeLabel = computed(() => {
  const map: Record<string, string> = { GOODS: '商品', CHAT: '聊天', ORDER: '订单', USER: '用户' }
  return map[targetType.value] || targetType.value
})
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  targetType.value = current?.options?.targetType || hashParams?.get('targetType') || 'GOODS'
  targetId.value = current?.options?.targetId || hashParams?.get('targetId') || ''
}
function guessContentType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}
function filenameFromPath(path: string, index: number) {
  const raw = path.split('/').pop() || `report-evidence-${Date.now()}-${index}.jpg`
  return raw.includes('.') ? raw : `${raw}.jpg`
}
function isValidReportTargetId(value: string, type = targetType.value) {
  if (/^[1-9]\d{0,18}$/.test(value)) return true
  const normalizedType = (type || '').toUpperCase()
  const patterns: Record<string, RegExp> = {
    GOODS: /^(GOODS|PRODUCT)-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    PRODUCT: /^(GOODS|PRODUCT)-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    ORDER: /^ORDER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    CHAT: /^CHAT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    USER: /^USER-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/,
    REPORT: /^REPORT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/
  }
  return patterns[normalizedType]?.test(value) === true
}
function chooseEvidence() {
  const remaining = Math.max(0, 6 - evidence.value.length)
  if (remaining <= 0) { uni.showToast({ title: '上传票据上限为 6 张', icon: 'none' }); return }
  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const paths = res.tempFilePaths.slice(0, remaining)
      uni.showLoading({ title: '校验票据中' })
      try {
        const urls: string[] = []
        for (let i = 0; i < paths.length; i += 1) {
          const path = paths[i] || ''
          if (path.startsWith('local://') || path.includes('placeholder')) {
            throw new Error('invalid report media path')
          }
          const ticket = await createMediaUploadTicket({ scene: 'REPORT_EVIDENCE', contentType: guessContentType(path), fileSize: 600_000, filename: filenameFromPath(path, i) })
          urls.push(ticket.storageUrl)
        }
        evidence.value = [...evidence.value, ...urls].slice(0, 6)
      } catch {
        uni.showToast({ title: '举报上传票据生成失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}
async function submit() {
  if (!reason.value) return uni.showToast({ title: '请选择举报原因', icon: 'none' })
  if (description.value.trim().length < 6) return uni.showToast({ title: '请补充至少 6 个字说明', icon: 'none' })
  if (!isValidReportTargetId(targetId.value)) return uni.showToast({ title: '缺少有效举报对象，未提交举报', icon: 'none' })
  if (evidence.value.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/report-evidence/'))) {
    return uni.showToast({ title: '举报上传票据需先完成平台校验', icon: 'none' })
  }
  submitting.value = true
  try {
    await submitReport({ targetType: targetType.value, targetId: targetId.value, reason: reason.value, description: description.value.trim(), evidenceUrls: evidence.value })
    uni.showModal({ title: '已提交', content: '举报已由平台接收；审核状态、通知和后续处理以平台记录为准。', showCancel: false, success: () => uni.navigateTo({ url: '/pages/notification/index' }) })
  } catch {
    uni.showModal({ title: '提交失败', content: '举报没有提交成功，请检查网络或稍后重试。', showCancel: false })
  } finally { submitting.value = false }
}
onMounted(readQuery)
</script>

<style scoped>
.report-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.target-card,.reason-card,.safe-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.hero { display:flex; gap:18rpx; align-items:center; }
.icon { width:78rpx; height:78rpx; border-radius:28rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }
.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }
.target-row { min-height:58rpx; display:flex; align-items:center; justify-content:space-between; color:#7b5542; font-size:23rpx; border-bottom:1rpx solid #ffe5ef; }
.target-row:last-child { border-bottom:0; }
.reason-grid { margin-top:16rpx; display:grid; grid-template-columns:repeat(2, 1fr); gap:12rpx; }
.reason-chip { min-height:62rpx; border-radius:22rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#9b7560; display:flex; align-items:center; justify-content:center; font-size:22rpx; font-weight:900; }
.reason-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.textarea { width:100%; box-sizing:border-box; height:170rpx; margin-top:16rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:24rpx; }
.upload-box { margin-top:16rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; display:flex; align-items:center; gap:14rpx; }
.upload-icon { width:56rpx; height:56rpx; border-radius:20rpx; background:#fff; color:#ff7a45; display:flex; align-items:center; justify-content:center; font-size:36rpx; }
.upload-title { color:#3a2a1f; font-size:24rpx; font-weight:950; }
.upload-desc,.safe-line { margin-top:6rpx; color:#9b7560; font-size:22rpx; line-height:1.5; }
.evidence-list { margin-top:14rpx; display:flex; flex-direction:column; gap:10rpx; }
.evidence-item { padding:12rpx; border-radius:18rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#7b5542; font-size:20rpx; display:flex; justify-content:space-between; gap:14rpx; }
.evidence-item text:last-child { max-width:470rpx; text-align:right; word-break:break-all; }
.submit-btn { margin-top:24rpx; height:74rpx; line-height:74rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:25rpx; font-weight:950; }
</style>
