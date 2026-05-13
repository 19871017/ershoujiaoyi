<template>
  <view class="page-shell account-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 收款账户</view>
        <view class="page-title">提现账户管理</view>
        <view class="page-desc">完整账号只提交给平台绑定接口；页面展示平台脱敏后的默认提现账户。</view>
      </view>
      <view class="hero-icon">💳</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">绑定状态</view>
      <view v-if="loading" class="status-box">正在读取平台提现账户...</view>
      <view v-else-if="activeAccount" class="status-box">
        <view class="result-row"><text>收款方式</text><text>{{ methodLabel(activeAccount.paymentMethod) }}</text></view>
        <view class="result-row"><text>收款人</text><text>{{ activeAccount.accountName }}</text></view>
        <view class="result-row"><text>脱敏账户</text><text>{{ activeAccount.maskedAccountNo }}</text></view>
        <view class="result-row"><text>复核状态</text><text>{{ activeAccount.verifyStatus }}</text></view>
      </view>
      <view v-else class="status-box">暂无平台提现账户绑定；提现页会 fail-closed，不会生成页面账户引用。</view>
      <view v-if="loadMessage" class="desc danger">{{ loadMessage }}</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">绑定/更新账户</view>
      <view class="desc">提交后由平台保存原始账号并只返回脱敏值；前端不生成脱敏账号、不保存账号明文。</view>
      <view class="method-row">
        <view v-for="item in methods" :key="item" class="method-chip tapable" :class="{ active: form.paymentMethod === item }" @click="form.paymentMethod = item">{{ methodLabel(item) }}</view>
      </view>
      <input v-model.trim="form.accountName" class="field" maxlength="24" placeholder="收款人姓名，需与实名一致" />
      <input v-model.trim="form.accountNo" class="field" maxlength="80" placeholder="完整收款账号，仅提交给平台绑定接口" />
      <button class="primary-btn" :disabled="submitting" @click="submitBinding">{{ submitting ? '提交中...' : '提交平台绑定' }}</button>
      <view v-if="submitMessage" class="desc" :class="{ danger: submitFailed }">{{ submitMessage }}</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">安全提醒</view>
      <view class="desc">平台不会索要支付密码、短信验证码或完整身份证号。收款账户变更后必须重新审核，不走页面校验。</view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { bindPayoutAccount, getPayoutAccount, type PayoutAccountResponse } from '../../../api/modules/wallet'

const methods = ['ALIPAY', 'BANK_CARD']
const form = reactive({ paymentMethod: 'ALIPAY', accountName: '', accountNo: '' })
const activeAccount = ref<PayoutAccountResponse | null>(null)
const loading = ref(false)
const submitting = ref(false)
const loadMessage = ref('')
const submitMessage = ref('')
const submitFailed = ref(false)
function methodLabel(method: string) { return method === 'ALIPAY' ? '支付宝' : '银行卡' }
function hasMaskedMarker(value: string) { return /[*＊]/.test(value) }
async function loadAccount() {
  loading.value = true
  loadMessage.value = ''
  try { activeAccount.value = await getPayoutAccount() }
  catch { activeAccount.value = null; loadMessage.value = '提现账户加载失败：未展示默认账户。' }
  finally { loading.value = false }
}
async function submitBinding() {
  if (!form.accountName) { submitFailed.value = true; submitMessage.value = '请填写收款人姓名'; return }
  if (!form.accountNo || hasMaskedMarker(form.accountNo)) { submitFailed.value = true; submitMessage.value = '请提交完整收款账号，不能提交脱敏账号'; return }
  submitting.value = true
  submitFailed.value = false
  submitMessage.value = ''
  try {
    activeAccount.value = await bindPayoutAccount({ paymentMethod: form.paymentMethod, accountName: form.accountName, accountNo: form.accountNo })
    form.accountNo = ''
    submitMessage.value = '提现账户已由平台绑定；页面仅保留脱敏展示。'
  } catch {
    submitFailed.value = true
    submitMessage.value = '提现账户绑定失败：未保存页面账号，请确认平台接口与字段合法性。'
  } finally { submitting.value = false }
}
onMounted(() => { void loadAccount() })
</script>
<style scoped>.account-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.section-card{margin-top:18rpx;padding:24rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:78rpx;height:78rpx;border-radius:24rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:34rpx}.section-title{color:#3a2a1f;font-size:30rpx;font-weight:950}.desc,.page-desc,.status-box{margin-top:8rpx;color:#9b7560;font-size:22rpx;line-height:1.5}.danger{color:#dc2626}.status-box{padding:16rpx;border-radius:22rpx;background:#fffaf6;color:#3a2a1f;font-weight:900}.method-row{margin-top:18rpx;display:flex;gap:12rpx}.method-chip{padding:13rpx 22rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.method-chip.active{background:#3a2a1f;color:#fff;border-color:#3a2a1f}.field{box-sizing:border-box;width:100%;margin-top:18rpx;padding:20rpx;border-radius:20rpx;background:#fffaf6;border:1rpx solid #ffd9bd;color:#3a2a1f;font-size:27rpx}.primary-btn{margin-top:18rpx;border-radius:999rpx;background:#ff7a45;color:#fff;font-size:25rpx;font-weight:950}.result-row{display:flex;justify-content:space-between;gap:18rpx;padding:9rpx 0;color:#7b5542;font-size:22rpx}.result-row text:last-child{max-width:420rpx;text-align:right;word-break:break-all}</style>
