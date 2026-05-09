<template>
  <view class="page-shell audit-detail">
    <view class="hero ds-card">
      <view>
        <view class="kicker">Admin Review</view>
        <view class="page-title">审核详情</view>
        <view class="page-desc">从后台读取真实审核单；拒绝静态样例、预览占位和本地假审核。</view>
      </view>
      <view class="hero-icon">🛡️</view>
    </view>

    <view v-if="loading" class="detail-card ds-card status-card">审核单加载中...</view>
    <view v-else-if="loadError" class="detail-card ds-card status-card error">{{ loadError }}</view>
    <template v-else>
      <view class="detail-card ds-card">
        <view class="section-title">{{ audit.auditNo }}</view>
        <view class="row"><text>审核类型</text><text>{{ typeLabel(audit.auditType) }}</text></view>
        <view class="row"><text>目标对象</text><text>{{ audit.targetType }} #{{ audit.targetId }}</text></view>
        <view class="row"><text>提交用户</text><text>User {{ audit.userId }}</text></view>
        <view class="row"><text>当前状态</text><text class="status">{{ statusLabel(audit.status) }}</text></view>
        <view class="row"><text>创建时间</text><text>{{ formatDateTime(audit.createdAt) }}</text></view>
        <view v-if="audit.reviewedAt" class="row"><text>审核时间</text><text>{{ formatDateTime(audit.reviewedAt) }}</text></view>
      </view>

      <view class="detail-card ds-card">
        <view class="section-title">说明与证据</view>
        <view class="desc">{{ audit.reason }}：{{ safeDescription }}</view>
        <view v-if="evidences.length" class="evidence-row">
          <view v-for="item in evidences" :key="item" class="evidence">{{ item }}</view>
        </view>
        <view v-else class="empty-text">暂无服务端凭证 URL</view>
      </view>

      <view class="detail-card ds-card">
        <view class="section-title">风控提示</view>
        <view v-for="item in riskTips" :key="item" class="risk-line">{{ item }}</view>
        <textarea v-model.trim="remark" class="field area" placeholder="填写审核备注：通过原因、拒绝原因、复核依据" />
      </view>

      <view class="action-row">
        <button class="secondary-btn" :disabled="reviewing || audit.status !== 'PENDING'" @click="reject">拒绝</button>
        <button class="primary-btn" :disabled="reviewing || audit.status !== 'PENDING'" @click="approve">通过</button>
      </view>
      <view v-if="message" class="detail-card ds-card status-card" :class="{ error: isError }">{{ message }}</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { approveAudit, getAdminAuditDetail, rejectAudit } from '../../../../api/modules/admin'
import type { AuditRecordResponse } from '../../../../api/modules/audit'

const emptyAudit: AuditRecordResponse = {
  auditNo: '',
  auditType: '',
  userId: 0,
  targetType: '',
  targetId: '',
  reason: '',
  description: '',
  status: 'PENDING',
  reviewRemark: undefined,
  createdAt: ''
}
const auditNo = ref('')
const remark = ref('')
const loading = ref(false)
const reviewing = ref(false)
const loadError = ref('')
const message = ref('')
const isError = ref(false)
const audit = reactive<AuditRecordResponse>({ ...emptyAudit })

const safeDescription = computed(() => audit.description || '无补充说明')
const evidences = computed<string[]>(() => {
  const matches = (audit.description || '').match(/\/uploads\/[^,\s，]+/g)
  return Array.from(new Set<string>(matches || [])).slice(0, 6)
})
const riskTips = computed(() => {
  const tips = ['审核动作必须走后台接口并记录审核备注', '详情页只展示服务端返回的审核单，不使用静态样例']
  if (audit.auditType === 'REPORT') tips.push('举报凭证必须来自 REPORT_EVIDENCE 上传票据 URL')
  if (audit.auditType === 'VIDEO_IDENTITY') tips.push('视频认证通过后才允许公开展示视频认证卖家')
  if (audit.auditType === 'WITHDRAWAL') tips.push('提现通过/拒绝会同步冻结资金出款或解冻流水')
  return tips
})

function assignAudit(data: AuditRecordResponse) {
  Object.assign(audit, data)
}
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  auditNo.value = current?.options?.auditNo || hash?.get('auditNo') || ''
}
function isValidAuditNo(value: string) {
  return /^AU-[A-Z0-9][A-Z0-9-]{3,63}$/i.test(value.trim())
}
function validateAuditNo() {
  if (!isValidAuditNo(auditNo.value)) {
    loadError.value = '缺少有效审核单号，已阻断静态样例或预览占位审核单'
    return false
  }
  return true
}
async function loadDetail() {
  readQuery()
  if (!isValidAuditNo(auditNo.value)) {
    validateAuditNo()
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    assignAudit(await getAdminAuditDetail(auditNo.value))
  } catch {
    loadError.value = '审核详情加载失败：请从后台审核列表进入，不能使用本地样例或伪审核单'
  } finally { loading.value = false }
}
function approve() { void review(true) }
function reject() { void review(false) }
async function review(ok: boolean) {
  if (audit.status !== 'PENDING') {
    message.value = '该审核单已处理，不能重复审核'
    isError.value = true
    return
  }
  reviewing.value = true
  message.value = ''
  isError.value = false
  try {
    const reviewed = ok ? await approveAudit(auditNo.value, { remark: remark.value }) : await rejectAudit(auditNo.value, { remark: remark.value })
    assignAudit(reviewed)
    message.value = ok ? '审核已通过并同步服务端状态' : '审核已拒绝并同步服务端状态'
  } catch {
    isError.value = true
    message.value = '审核状态未同步，已阻断本地假成功'
  } finally { reviewing.value = false }
}
function typeLabel(value: string) { return ({ GOODS: '商品审核', REPORT: '举报审核', VIDEO_IDENTITY: '视频认证', WITHDRAWAL: '提现审核' } as Record<string, string>)[value] ?? value }
function statusLabel(value: string) { return ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' } as Record<string, string>)[value] ?? value }
function formatDateTime(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 19) : '--' }

onMounted(loadDetail)
</script>

<style scoped>
.audit-detail{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.detail-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#3a2a1f;color:#fff;display:flex;align-items:center;justify-content:center;font-size:36rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.row{margin-top:14rpx;display:flex;justify-content:space-between;gap:18rpx;color:#7b5542;font-size:23rpx}.row text:last-child{max-width:460rpx;text-align:right;word-break:break-all}.status{color:#ff7a45;font-weight:950}.desc,.risk-line,.empty-text{margin-top:12rpx;color:#7b5542;font-size:23rpx;line-height:1.5;word-break:break-all}.empty-text{color:#b9856a}.evidence-row{margin-top:16rpx;display:flex;gap:12rpx;flex-direction:column}.evidence{padding:14rpx 16rpx;border-radius:18rpx;background:#fff3e7;color:#ff7a45;font-size:21rpx;font-weight:900;word-break:break-all}.field{margin-top:16rpx;width:100%;box-sizing:border-box;padding:18rpx;border-radius:22rpx;background:#fffaf6;border:1rpx solid #ffd9bd;color:#3a2a1f;font-size:23rpx}.area{height:140rpx}.action-row{margin-top:22rpx;display:grid;grid-template-columns:1fr 1fr;gap:14rpx}.status-card{color:#7b5542;font-size:24rpx}.error{color:#d93025;background:#fff5f3}
</style>
