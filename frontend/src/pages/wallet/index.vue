<template>
  <view class="page-shell wallet-page">
    <view class="page-title">钱包</view>
    <view class="page-desc">管理充值余额、收入余额、冻结资金和提现审核。</view>

    <view class="balance-card ds-card">
      <view class="balance-top">
        <view>
          <view class="balance-label">总可用余额</view>
          <view class="balance-total">¥{{ totalAvailable }}</view>
        </view>
        <view class="refresh tapable" @click="refreshAll">刷新</view>
      </view>
      <view class="balance-grid">
        <view class="balance-item"><text>充值余额</text><strong>{{ balance.rechargeBalance }}</strong></view>
        <view class="balance-item"><text>收入余额</text><strong>{{ balance.incomeBalance }}</strong></view>
        <view class="balance-item"><text>冻结金额</text><strong>{{ balance.frozenBalance }}</strong></view>
        <view class="balance-item"><text>可提现</text><strong>{{ balance.withdrawableBalance }}</strong></view>
      </view>
      <view class="status-text">{{ loading ? '余额加载中...' : statusText }}</view>
    </view>

    <view class="tab-row">
      <view v-for="item in tabs" :key="item.value" class="tab-chip tapable" :class="{ active: activeTab === item.value }" @click="activeTab = item.value">{{ item.label }}</view>
    </view>

    <view v-if="activeTab === 'recharge'" class="action-card ds-card">
      <view class="section-title">充值</view>
      <view class="section-desc">{{ rechargeDesc }}</view>
      <view class="safe-guard">充值单创建后请按安全收银台流程完成支付。</view>
      <input v-model="rechargeForm.amount" class="field" type="digit" placeholder="请输入充值金额" />
      <button class="primary-btn" :disabled="recharging" @click="handleCreateRecharge">
        {{ recharging ? '创建中...' : '创建充值单' }}
      </button>
      <view v-if="lastRecharge" class="result-box">
        <view class="result-row"><text>充值单号</text><text>{{ lastRecharge.rechargeNo }}</text></view>
        <view class="result-row"><text>状态</text><text>{{ statusLabel(lastRecharge.status) }}</text></view>
        <view class="result-row"><text>金额</text><text>{{ lastRecharge.amount }}</text></view>
        <view v-if="lastRecharge.ledgerNo" class="result-row"><text>流水号</text><text>{{ lastRecharge.ledgerNo }}</text></view>
      </view>
      <view v-if="rechargeMessage" class="status-text">{{ rechargeMessage }}</view>
    </view>

    <view v-if="activeTab === 'withdraw'" class="action-card ds-card">
      <view class="section-title">提现申请</view>
      <view class="section-desc">提交后立即冻结可提现余额；审核通过会从冻结资金出款，拒绝会原路解冻。</view>
      <view class="safe-guard">资金动作均以服务端账本为准：冻结、出款、解冻都会写入幂等流水。</view>
      <view class="safe-guard danger">提现页不采集完整收款账号；仅使用后端返回的提现账户引用提交审核。</view>
      <input v-model.trim="withdrawForm.amount" class="field" type="digit" placeholder="提现金额" />
      <button class="secondary-btn" @click="openPayoutAccount">管理提现账户</button>
      <view v-if="maskedAccountNo" class="result-box">
        <view class="result-row"><text>收款方式</text><text>{{ methodLabel(activePayoutAccount?.paymentMethod || '') }}</text></view>
        <view class="result-row"><text>收款人</text><text>{{ activePayoutAccount?.accountName }}</text></view>
        <view class="result-row"><text>脱敏账户</text><text>{{ maskedAccountNo }}</text></view>
      </view>
      <input v-model.trim="withdrawForm.remark" class="field" maxlength="80" placeholder="备注，可不填" />
      <button class="primary-btn" :disabled="withdrawing" @click="handleCreateWithdrawal">
        {{ withdrawing ? '提交中...' : '提交提现审核' }}
      </button>
      <view v-if="withdrawMessage" class="status-text">{{ withdrawMessage }}</view>
    </view>

    <view class="ledger-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">最近流水</view>
          <view class="section-desc">收入、支出、冻结和分账都会进入账本。</view>
        </view>
        <view class="ledger-count tapable" @click="openLedger">全部 {{ ledgerList.length }} 条</view>
      </view>
      <view v-if="ledgerLoading" class="status-text">流水加载中...</view>
      <view v-else-if="ledgerList.length === 0" class="empty-text">暂无钱包流水</view>
      <view v-else class="ledger-list">
        <view v-for="item in recentLedgers" :key="item.ledgerNo" class="ledger-item">
          <view class="ledger-main-row">
            <view>
              <view class="ledger-title">{{ businessLabel(item.businessType) }}</view>
              <view class="ledger-subtitle">{{ item.ledgerNo }}</view>
            </view>
            <view :class="['ledger-amount', item.direction === 'CREDIT' ? 'amount-credit' : 'amount-debit']">
              {{ item.direction === 'CREDIT' ? '+' : '-' }}{{ item.amount }}
            </view>
          </view>
          <view class="ledger-meta-row">
            <text>{{ directionLabel(item.direction) }} · {{ balanceTypeLabel(item.balanceType) }} · {{ statusTextLabel(item.status) }}</text>
            <text>{{ formatDateTime(item.createdAt) }}</text>
          </view>
        </view>
      </view>
      <view v-if="ledgerMessage" class="status-text">{{ ledgerMessage }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createRecharge, type RechargeResponse, type RechargeStatus } from '../../api/modules/payment'
import { createWithdrawal, getPayoutAccount, getWalletBalance, getWalletLedger, type PayoutAccountResponse, type WalletBalanceResponse, type WalletLedgerDirection, type WalletLedgerItemResponse } from '../../api/modules/wallet'

const emptyBalance: WalletBalanceResponse = { rechargeBalance: '--', incomeBalance: '--', frozenBalance: '--', withdrawableBalance: '--' }
const tabs = [{ label: '充值', value: 'recharge' }, { label: '提现', value: 'withdraw' }] as const
const activeTab = ref<'recharge' | 'withdraw'>('recharge')
const balance = reactive<WalletBalanceResponse>({ ...emptyBalance })
const loading = ref(false)
const statusText = ref('')
const rechargeForm = reactive({ amount: '', channel: 'ONLINE' })
const recharging = ref(false)
const rechargeMessage = ref('')
const lastRecharge = ref<RechargeResponse | null>(null)
const withdrawForm = reactive({ amount: '', remark: '' })
const maskedAccountNo = ref('')
const activePayoutAccount = ref<PayoutAccountResponse | null>(null)
const withdrawing = ref(false)
const withdrawMessage = ref('')
const ledgerList = ref<WalletLedgerItemResponse[]>([])
const ledgerLoading = ref(false)
const ledgerMessage = ref('')
const recentLedgers = computed(() => ledgerList.value.slice(0, 8))
const totalAvailable = computed(() => money(Number(balance.rechargeBalance || 0) + Number(balance.incomeBalance || 0)))
const rechargeDesc = computed(() => '充值单创建后进入待支付，请等待正式支付通道回调入账。')

async function refreshAll() { await Promise.all([loadBalance(), loadLedger(), loadPayoutAccount()]) }
async function loadBalance() {
  loading.value = true
  statusText.value = ''
  try { Object.assign(balance, await getWalletBalance()); statusText.value = '余额已更新' }
  catch { Object.assign(balance, emptyBalance); statusText.value = '余额加载失败' }
  finally { loading.value = false }
}
async function loadLedger() {
  ledgerLoading.value = true
  ledgerMessage.value = ''
  try { ledgerList.value = await getWalletLedger() }
  catch { ledgerList.value = []; ledgerMessage.value = '流水加载失败' }
  finally { ledgerLoading.value = false }
}
function money(value: number) { return Number.isFinite(value) ? value.toFixed(2) : '--' }
function statusLabel(status: RechargeStatus) { return status === 'PAID' ? '已支付' : '待支付' }
function directionLabel(direction: WalletLedgerDirection | string) { return direction === 'CREDIT' ? '收入' : '支出' }
function statusTextLabel(status: string) { return ({ SUCCESS: '成功', FAILED: '失败', PENDING: '处理中' } as Record<string, string>)[status] ?? status }
function balanceTypeLabel(balanceType: string) { return ({ RECHARGE: '充值余额', INCOME: '收入余额', WITHDRAWABLE: '可提现余额', FROZEN: '冻结余额' } as Record<string, string>)[balanceType] ?? balanceType }
function businessLabel(businessType?: string | null) { return businessType ? ({ RECHARGE: '充值入账', ORDER_PAYMENT: '订单支付', ORDER_PAY: '订单支付', ORDER_REFUND: '订单退款', WITHDRAW: '提现', WITHDRAW_FREEZE: '提现冻结', WITHDRAW_PAYOUT: '提现出款', WITHDRAW_RELEASE: '提现解冻', GIFT: '礼物分账' } as Record<string, string>)[businessType] ?? businessType : '钱包流水' }
function formatDateTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '--' }
function openLedger() { uni.navigateTo({ url: '/pages/wallet/ledger/index' }) }
function normalizeAmount(amount: string) { return amount.trim() }
function isValidMoneyAmount(amount: string) { return /^\d+(\.\d{1,2})?$/.test(amount) && Number(amount) > 0 }
function methodLabel(method: string) { return method === 'ALIPAY' ? '支付宝' : '银行卡' }
function openPayoutAccount() { uni.navigateTo({ url: '/pages/wallet/accounts/index' }) }
async function loadPayoutAccount() {
  try {
    activePayoutAccount.value = await getPayoutAccount()
    maskedAccountNo.value = activePayoutAccount.value?.maskedAccountNo || ''
  } catch {
    activePayoutAccount.value = null
    maskedAccountNo.value = ''
  }
}
async function handleCreateRecharge() {
  const amount = normalizeAmount(rechargeForm.amount)
  if (!isValidMoneyAmount(amount)) { rechargeMessage.value = '请输入有效充值金额，最多两位小数'; return }
  recharging.value = true
  rechargeMessage.value = ''
  try { lastRecharge.value = await createRecharge({ amount, channel: rechargeForm.channel }); rechargeMessage.value = `充值单已创建：${lastRecharge.value.rechargeNo}` }
  catch { rechargeMessage.value = '充值单创建失败' }
  finally { recharging.value = false }
}
async function handleCreateWithdrawal() {
  const amount = normalizeAmount(withdrawForm.amount)
  if (!isValidMoneyAmount(amount)) { withdrawMessage.value = '请输入有效提现金额'; return }
  if (!activePayoutAccount.value?.payoutAccountId) { withdrawMessage.value = '请先在账户管理页完成后端提现账户绑定；未执行资金冻结'; return }
  withdrawing.value = true
  withdrawMessage.value = ''
  try {
    const withdrawal = await createWithdrawal({ amount, payoutAccountId: activePayoutAccount.value.payoutAccountId, remark: withdrawForm.remark })
    withdrawMessage.value = `提现已提交审核：${withdrawal.withdrawalNo}，冻结状态以平台账本为准`
    await refreshAll()
  } catch { withdrawMessage.value = '提现提交失败：未执行本地资金状态变更，请确认账户绑定、余额和后端审核接口。' }
  finally { withdrawing.value = false }
}
onMounted(() => { void refreshAll() })
</script>

<style scoped>
.wallet-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.balance-card,.action-card,.ledger-card { margin-top:22rpx; padding:24rpx; border-color:#ffd9bd; }
.balance-card { background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; }
.balance-top { display:flex; justify-content:space-between; gap:18rpx; align-items:flex-start; }
.balance-label { color:rgba(255,255,255,.82); font-size:23rpx; font-weight:850; }
.balance-total { margin-top:8rpx; font-size:52rpx; font-weight:950; }
.refresh { padding:9rpx 18rpx; border-radius:999rpx; background:rgba(255,255,255,.20); font-size:21rpx; font-weight:900; }
.balance-grid { margin-top:22rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:12rpx; }
.balance-item { padding:16rpx; border-radius:22rpx; background:rgba(255,255,255,.16); display:flex; flex-direction:column; gap:8rpx; }
.balance-item text { color:rgba(255,255,255,.78); font-size:20rpx; }
.balance-item strong { font-size:27rpx; }
.tab-row { margin-top:18rpx; display:flex; gap:12rpx; }
.tab-chip,.method-chip { padding:13rpx 22rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:22rpx; font-weight:900; }
.tab-chip.active,.method-chip.active { background:#3a2a1f; color:#fff; border-color:#3a2a1f; }
.section-title { color:#3a2a1f; font-size:31rpx; font-weight:950; }
.section-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.45; }
.field { box-sizing:border-box; width:100%; margin-top:18rpx; padding:20rpx; border-radius:20rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:27rpx; }
.method-row { margin-top:18rpx; display:flex; gap:12rpx; }
.primary-btn,.secondary-btn { margin-top:20rpx; border-radius:999rpx; font-size:26rpx; font-weight:950; }
.safe-guard { margin-top:14rpx; padding:14rpx; border-radius:20rpx; background:#fff3e7; border:1rpx solid #ffd9bd; color:#9b5a32; font-size:22rpx; line-height:1.45; font-weight:850; }
.safe-guard.danger { background:#fff7f7; border-color:#fecaca; color:#dc2626; }
.primary-btn { color:#fff; background:#ff7a45; }
.secondary-btn { color:#ff7a45; background:#fff3e7; }
.result-box { margin-top:18rpx; padding:16rpx; border-radius:20rpx; background:#fffaf6; }
.result-row { display:flex; justify-content:space-between; gap:18rpx; padding:9rpx 0; color:#7b5542; font-size:22rpx; }
.result-row text:last-child { max-width:420rpx; text-align:right; word-break:break-all; }
.status-text { margin-top:16rpx; color:#9b7560; font-size:23rpx; }
.balance-card .status-text { color:rgba(255,255,255,.84); }
.section-head { display:flex; justify-content:space-between; gap:18rpx; align-items:flex-start; }
.ledger-count { padding:9rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
.empty-text { margin-top:20rpx; padding:24rpx 0; color:#b9856a; font-size:25rpx; text-align:center; }
.ledger-list { margin-top:16rpx; }
.ledger-item { padding:18rpx 0; border-bottom:1rpx solid #ffd9bd; }
.ledger-item:last-child { border-bottom:0; }
.ledger-main-row,.ledger-meta-row { display:flex; justify-content:space-between; gap:20rpx; }
.ledger-title { color:#3a2a1f; font-size:27rpx; font-weight:950; }
.ledger-subtitle,.ledger-meta-row { margin-top:8rpx; color:#9b7560; font-size:21rpx; }
.ledger-meta-row text:last-child { text-align:right; }
.ledger-amount { flex-shrink:0; font-size:30rpx; font-weight:950; }
.amount-credit { color:#16a34a; }
.amount-debit { color:#dc2626; }
</style>
