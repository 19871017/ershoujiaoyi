<template>
  <view class="page-shell risk-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 风控中心</view>
        <view class="page-title">举报与风控</view>
        <view class="page-desc">从真实审核记录汇总举报、提现、视频认证和商品审核风险，不展示本地静态风险单。</view>
      </view>
      <view class="hero-icon">🛡️</view>
    </view>

    <view v-if="loadError" class="error-card ds-card">{{ loadError }}</view>
    <view class="risk-grid">
      <view v-for="item in stats" :key="item.label" class="risk-card ds-card">
        <view class="risk-num">{{ item.num }}</view>
        <view class="risk-label">{{ item.label }}</view>
      </view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">待处理风险</view>
      <view v-if="loading" class="empty">风控记录加载中...</view>
      <view v-else-if="cases.length === 0" class="empty">暂无待处理风险，请从举报、提现或认证审核进入。</view>
      <view v-for="item in cases" v-else :key="item.auditNo" class="case-row">
        <view class="case-main">
          <view class="case-title">{{ item.auditNo }} · {{ item.type }}</view>
          <view class="case-desc">{{ item.desc }}</view>
          <view class="case-tags"><text v-for="tag in item.tags" :key="tag">{{ tag }}</text></view>
        </view>
        <view class="case-action tapable" @click="openCase(item)">{{ item.status }}</view>
      </view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">安全策略</view>
      <view class="policy">私下转账引导、频繁复制联系方式、异常提现和重复退款必须进入真实后台审核记录，不再用本地风险样例代替处理。</view>
      <button class="primary-btn" @click="goReport">提交新举报</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAdminAuditList } from '../../api/modules/admin'
import type { AuditRecordResponse } from '../../api/modules/audit'

const loading = ref(false)
const loadError = ref('')
const audits = ref<AuditRecordResponse[]>([])
const pendingAudits = computed(() => audits.value.filter((item) => item.status === 'PENDING'))
const stats = computed(() => [
  { label: '举报待审', num: pendingAudits.value.filter((item) => item.auditType === 'REPORT').length },
  { label: '提现复核', num: pendingAudits.value.filter((item) => item.auditType === 'WITHDRAWAL').length },
  { label: '认证待审', num: pendingAudits.value.filter((item) => item.auditType === 'VIDEO_IDENTITY').length }
])
const cases = computed(() => pendingAudits.value.map((item) => ({
  auditNo: item.auditNo,
  type: typeLabel(item.auditType),
  desc: `${item.reason || '待复核'} ${item.description || ''}`.trim(),
  tags: tagsFor(item),
  status: '处理'
})))

async function loadRisks() {
  loading.value = true
  loadError.value = ''
  try {
    audits.value = await getAdminAuditList()
  } catch {
    audits.value = []
    loadError.value = '风控记录加载失败：已阻断本地静态风险单和伪处理入口'
  } finally { loading.value = false }
}
function typeLabel(type: string) { return ({ REPORT: '举报审核', WITHDRAWAL: '提现复核', VIDEO_IDENTITY: '视频认证', GOODS: '商品审核' } as Record<string, string>)[type] ?? type }
function tagsFor(item: AuditRecordResponse) {
  if (item.auditType === 'WITHDRAWAL') return ['钱包', '提现', '资金复核']
  if (item.auditType === 'REPORT') return ['举报', item.targetType || '目标对象']
  if (item.auditType === 'VIDEO_IDENTITY') return ['认证', '信任标识']
  return ['审核', item.targetType || '目标对象']
}
function openCase(item: { auditNo: string }) { uni.navigateTo({ url: `/pages/admin/audit/detail/index?auditNo=${encodeURIComponent(item.auditNo)}` }) }
function goReport() { uni.showToast({ title: '请从用户、商品、聊天或动态详情页发起举报', icon: 'none' }) }
onMounted(loadRisks)
</script>

<style scoped>
.risk-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }.hero,.section-card,.error-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }.hero { display:flex; justify-content:space-between; background:linear-gradient(135deg,#fff,#fff3e7); }.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#3a2a1f; color:#fff; display:flex; align-items:center; justify-content:center; font-size:38rpx; }.risk-grid { margin-top:18rpx; display:grid; grid-template-columns:repeat(3,1fr); gap:12rpx; }.risk-card { padding:18rpx 8rpx; text-align:center; border-color:#ffd9bd; }.risk-num { color:#ff7a45; font-size:32rpx; font-weight:950; }.risk-label { color:#9b7560; font-size:21rpx; }.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.empty,.error-card { margin-top:18rpx; color:#b9856a; font-size:24rpx; }.error-card { color:#d93025; }.case-row { margin-top:16rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; display:flex; align-items:center; gap:14rpx; }.case-main { flex:1; min-width:0; }.case-title { color:#3a2a1f; font-size:24rpx; font-weight:950; }.case-desc,.policy { margin-top:7rpx; color:#7b5542; font-size:22rpx; line-height:1.5; word-break:break-all; }.case-tags { margin-top:10rpx; display:flex; gap:8rpx; flex-wrap:wrap; }.case-tags text { padding:6rpx 10rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:18rpx; font-weight:900; }.case-action { padding:10rpx 16rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:21rpx; font-weight:950; }.primary-btn { margin-top:18rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:25rpx; font-weight:950; }
</style>
