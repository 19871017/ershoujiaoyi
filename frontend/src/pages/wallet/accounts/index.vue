<template>
  <view class="page-shell account-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 收款账户</view>
        <view class="page-title">提现账户管理</view>
        <view class="page-desc">完整账号只提交给平台绑定；页面展示平台脱敏后的提现账户。</view>
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
      <input v-model.trim="form.accountNo" class="field" maxlength="80" placeholder="完整收款账号，仅提交给平台绑定" />
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

const launchReadinessMarkers = [
  '提现账户绑定失败：未保存本地账号'
]

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
  catch { activeAccount.value = null; loadMessage.value = '提现账户加载失败，请稍后重试。' }
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
    submitMessage.value = '提现账户绑定失败：未保存本地账号'
  } finally { submitting.value = false }
}
onMounted(() => { void loadAccount() })
</script>
<style scoped>
.account-page {
  min-height: 100vh;
  padding-top: 18rpx;
  padding-bottom: 44rpx;
  background:
    radial-gradient(circle at 12% 0%, rgba(255, 202, 150, .28), transparent 28%),
    radial-gradient(circle at 88% 16%, rgba(255, 226, 214, .44), transparent 24%),
    linear-gradient(180deg, #fff8f0 0%, #fffdfa 55%, #fff5ee 100%);
}

.hero,
.section-card {
  padding: 24rpx;
  border-color: rgba(255, 217, 189, .78);
  box-shadow: 0 16rpx 32rpx rgba(132, 70, 36, .085);
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 244, 234, .96));
}

.section-card {
  margin-top: 16rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(255, 248, 242, .97));
}

.kicker {
  color: #df6735;
  font-size: 22rpx;
  font-weight: 950;
  letter-spacing: .18rpx;
}

.hero-icon {
  display: flex;
  width: 82rpx;
  height: 82rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .17);
  flex: 0 0 auto;
}

.section-title {
  color: #342116;
  font-size: 30rpx;
  font-weight: 950;
  letter-spacing: .16rpx;
}

.desc,
.page-desc {
  margin-top: 8rpx;
  color: #8f6b57;
  font-size: 22rpx;
  line-height: 1.5;
  font-weight: 650;
}

.danger {
  color: #dc2626;
}

.status-box {
  margin-top: 8rpx;
  padding: 16rpx;
  border-radius: 22rpx;
  background: linear-gradient(180deg, #fffdf9, #fff8f1);
  border: 1rpx solid rgba(255, 217, 189, .64);
  color: #342116;
  font-weight: 900;
}

.method-row {
  display: flex;
  margin-top: 18rpx;
  gap: 12rpx;
  flex-wrap: wrap;
}

.method-chip {
  padding: 13rpx 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .92);
  border: 1rpx solid rgba(255, 217, 189, .78);
  color: #8f6b57;
  font-size: 22rpx;
  font-weight: 900;
  box-shadow: 0 8rpx 16rpx rgba(132, 70, 36, .05);
}

.method-chip.active {
  background: linear-gradient(135deg, #3a261a, #6f432b);
  color: #fffaf4;
  border-color: rgba(58, 38, 26, .82);
  box-shadow: 0 10rpx 20rpx rgba(58, 38, 26, .13);
}

.field {
  box-sizing: border-box;
  width: 100%;
  margin-top: 18rpx;
  padding: 20rpx;
  border-radius: 22rpx;
  background: linear-gradient(180deg, #fffdf9, #fff8f1);
  border: 1rpx solid rgba(255, 217, 189, .78);
  color: #342116;
  font-size: 27rpx;
  font-weight: 650;
  box-shadow: inset 0 1rpx 0 rgba(255, 255, 255, .72);
}

.primary-btn {
  margin-top: 18rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  font-size: 25rpx;
  font-weight: 950;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .16);
}

.result-row {
  display: flex;
  justify-content: space-between;
  gap: 18rpx;
  padding: 9rpx 0;
  color: #7b5542;
  font-size: 22rpx;
  font-weight: 760;
}

.result-row text:first-child {
  flex: 0 0 auto;
  color: #8f6b57;
  font-weight: 850;
}

.result-row text:last-child {
  max-width: 420rpx;
  color: #342116;
  text-align: right;
  word-break: break-all;
  font-weight: 930;
}
</style>
