<template>
  <view class="page-shell withdraw-detail">
    <view class="hero ds-card">
      <view>
        <view class="kicker">Admin Withdraw</view>
        <view class="page-title">提现审核详情</view>
        <view class="page-desc">核对实名、脱敏收款账户、冻结金额和账本影响后再处理。</view>
      </view>
      <view class="hero-icon">💸</view>
    </view>

    <view v-if="loadError" class="message ds-card error">{{ loadError }}</view>
    <view class="info-card ds-card">
      <view class="section-title">申请信息</view>
      <view v-for="item in rows" :key="item.label" class="info-row"><text>{{ item.label }}</text><text>{{ item.value }}</text></view>
    </view>

    <view class="check-card ds-card">
      <view class="section-title">审核检查</view>
      <view v-for="item in checks" :key="item" class="check-line">✓ {{ item }}</view>
    </view>

    <textarea v-model.trim="remark" class="field area" placeholder="填写审核意见：通过原因、拒绝原因、需补充资料等" />
    <view class="actions">
      <button class="mini-btn" :disabled="reviewing" @click="reject">拒绝</button>
      <button class="mini-btn primary" :disabled="reviewing" @click="approve">通过并确认出款</button>
    </view>
    <view v-if="message" class="message ds-card" :class="{ error: isError }">{{ message }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { approveAudit, getAdminWithdrawalDetail, rejectAudit } from '../../../../api/modules/admin'
import type { WithdrawalResponse } from '../../../../api/modules/wallet'

const withdrawNo = ref('')
const auditNo = ref('')
const remark = ref('')
const reviewing = ref(false)
const message = ref('')
const isError = ref(false)
const loadError = ref('')
const withdrawal = ref<WithdrawalResponse | null>(null)

const rows = computed(() => [
  { label: '提现单号', value: withdrawal.value?.withdrawalNo || withdrawNo.value || '缺少提现单号' },
  { label: '审核单号', value: withdrawal.value?.auditNo || auditNo.value || '缺少审核单号' },
  { label: '提现金额', value: withdrawal.value ? `¥${withdrawal.value.amount}` : '待后端返回' },
  { label: '收款渠道', value: withdrawal.value?.paymentMethod || '待后端返回' },
  { label: '收款人', value: withdrawal.value?.accountName || '待后端返回' },
  { label: '脱敏收款账号', value: withdrawal.value?.maskedAccountNo || '待后端返回安全展示' },
  { label: '一致性状态', value: withdrawal.value?.accountVerifyStatus || '实名与收款账户待人工一致性复核' },
  { label: '资金状态', value: '提交时已冻结；通过扣减冻结余额，拒绝解冻回可提现' },
  { label: '账本影响', value: '提现冻结 / 提现出款 / 提现解冻均写入后端幂等流水' }
])
const checks = ['确认审核单号来自真实后台记录', '收款账号仅展示后端安全返回结果', '确认实名与收款账户一致性状态', '通过会扣减冻结资金并记提现出款流水', '拒绝会释放冻结资金并记提现解冻流水']

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  withdrawNo.value = current?.options?.withdrawNo || hash?.get('withdrawNo') || ''
  auditNo.value = current?.options?.auditNo || hash?.get('auditNo') || ''
}
function isValidWithdrawalNo(value: string) {
  return /^WD-\d{8,}[A-Z0-9-]*$/i.test(value.trim())
}
function isValidAuditNo(value: string) {
  return /^AU-[A-Z0-9][A-Z0-9-]{3,63}$/i.test(value.trim())
}
function requireAuditNo() {
  if (!isValidAuditNo(auditNo.value)) {
    message.value = '缺少有效审核单号，不能同步提现审核动作'
    isError.value = true
    return false
  }
  return true
}
async function loadWithdrawal() {
  if (!isValidWithdrawalNo(withdrawNo.value)) {
    loadError.value = '缺少真实提现单号，已阻断完整账号展示和伪审核'
    return
  }
  try {
    withdrawal.value = await getAdminWithdrawalDetail(withdrawNo.value)
    if (withdrawal.value.auditNo) auditNo.value = withdrawal.value.auditNo
  } catch {
    loadError.value = '提现详情加载失败，不能展示或处理收款账号'
  }
}
function approve() {
  if (!requireAuditNo()) return
  uni.showModal({ title: '确认通过提现', content: '通过后将扣减冻结资金并写入提现出款流水；请确认实名与脱敏收款账户一致。', showCancel: true, success: (res) => { if (res.confirm) review(true) } })
}
function reject() {
  if (!requireAuditNo()) return
  uni.showModal({ title: '确认拒绝提现', content: '拒绝后冻结资金将解冻回用户钱包，请填写审核意见。', showCancel: true, success: (res) => { if (res.confirm) review(false) } })
}
async function review(ok: boolean) {
  reviewing.value = true
  message.value = ''
  isError.value = false
  try {
    const response = ok ? await approveAudit(auditNo.value, { remark: remark.value || '提现审核通过' }) : await rejectAudit(auditNo.value, { remark: remark.value || '提现审核拒绝' })
    message.value = `审核已同步：${response.auditNo} · ${response.status}`
    await loadWithdrawal()
  } catch {
    message.value = '提现审核未同步，请检查管理员权限或稍后重试'
    isError.value = true
  } finally {
    reviewing.value = false
  }
}
onMounted(() => { readQuery(); loadWithdrawal() })
</script>

<style scoped>
.withdraw-detail{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.info-card,.check-card,.message{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:36rpx}.section-title{color:#3a2a1f;font-size:30rpx;font-weight:950}.info-row{min-height:64rpx;display:flex;justify-content:space-between;gap:18rpx;align-items:center;border-bottom:1rpx solid #ffe5ef;color:#7b5542;font-size:23rpx}.info-row text:last-child{font-weight:900;color:#3a2a1f;text-align:right;word-break:break-all}.check-line{margin-top:12rpx;color:#7b5542;font-size:23rpx}.field{margin-top:18rpx;box-sizing:border-box;width:100%;padding:18rpx;border-radius:22rpx;background:#fff;border:1rpx solid #ffd9bd;color:#3a2a1f;font-size:23rpx}.area{height:150rpx}.actions{margin-top:18rpx;display:flex;gap:12rpx}.mini-btn{flex:1;height:72rpx;line-height:72rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#7b5542;font-size:24rpx;font-weight:950}.mini-btn.primary{background:#ff7a45;color:#fff;border-color:#ff7a45}.message{color:#15803d}.message.error{color:#dc2626}
</style>
