<template>
  <view class="page-shell login-page">
    <view class="brand-card ds-card">
      <view class="brand-logo">♡</view>
      <view class="brand-title">小原圈</view>
      <view class="brand-desc">登录后可发布宝贝、私信卖家、查看钱包和订单。</view>
    </view>

    <view class="form-card ds-card">
      <view class="mode-tabs">
        <view class="mode-tab tapable" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</view>
        <view class="mode-tab tapable" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册账号</view>
      </view>

      <view class="section-title">{{ mode === 'login' ? '手机号登录' : '注册账号' }}</view>
      <view class="section-desc">
        {{ mode === 'login' ? '使用手机号和密码登录小原圈。' : '无需验证码；同一 IP 每天只能注册 1 个账号。' }}
      </view>
      <input v-model.trim="form.mobile" class="field" maxlength="11" type="number" placeholder="请输入手机号" />
      <input v-model="form.password" class="field" password maxlength="32" placeholder="请输入密码，至少 6 位" />
      <input
        v-if="mode === 'register'"
        v-model="confirmPassword"
        class="field"
        password
        maxlength="32"
        placeholder="请再次输入密码"
      />

      <view v-if="mode === 'login'" class="helper-row">
        <label class="remember"><checkbox :checked="remember" color="#ff7a45" @click="remember = !remember" />记住登录</label>
        <view class="link tapable" @click="useCodePreview">验证码登录</view>
      </view>

      <view v-else class="agreement-card">
        <view class="agree-row tapable" @click="toggleAgreement">
          <view class="fake-check" :class="{ checked: agreed }">{{ agreed ? '✓' : '' }}</view>
          <text>我已阅读并同意小原圈注册协议</text>
        </view>
        <view class="agreement-title">注册协议规则</view>
        <view class="rule-item" v-for="rule in agreementRules" :key="rule">{{ rule }}</view>
      </view>

      <button class="primary-btn" :disabled="loading" @click="handleSubmit">
        {{ loading ? (mode === 'login' ? '登录中...' : '注册中...') : (mode === 'login' ? '登录并进入小原圈' : '注册并进入小原圈') }}
      </button>
      <view v-if="message" class="status-text" :class="{ error: isError }">{{ message }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { login, register, type LoginRequest } from '../../../api/modules/auth'
import { useUserStore } from '../../../store/modules/user'

type AuthMode = 'login' | 'register'

const form = reactive<LoginRequest>({ mobile: '', password: '' })
const mode = ref<AuthMode>('login')
const loading = ref(false)
const message = ref('')
const isError = ref(false)
const remember = ref(true)
const agreed = ref(false)
const confirmPassword = ref('')
const userStore = useUserStore()
const agreementRules = [
  '注册信息需真实可联系，不得冒用他人手机号或身份。',
  '平台交易必须走小原圈订单、支付、发货、收货和售后流程，禁止诱导私下转账。',
  '发布内容不得违法违规，不得发布侵权、虚假、色情低俗、诈骗或危险物品信息。',
  '买卖双方应如实描述商品成色、瑕疵、价格和交付方式，聊天与交易记录以平台留存为准。',
  '账号仅限本人使用，不得批量注册、转卖、出租或用于刷榜、刷单、骚扰他人。',
  '违反协议或存在风险行为时，平台可限制发布、交易、私信、提现或封禁账号。'
]

function validMobile(value: string) { return /^1\d{10}$/.test(value) }
function switchMode(nextMode: AuthMode) {
  mode.value = nextMode
  message.value = ''
  isError.value = false
}
function toggleAgreement() {
  agreed.value = !agreed.value
  message.value = ''
  isError.value = false
}
function useCodePreview() {
  message.value = '验证码登录暂未开放，请使用密码登录'
  isError.value = false
}
function validateForm() {
  if (!validMobile(form.mobile)) return '请输入 11 位手机号'
  if (!form.password || form.password.length < 6) return '密码至少 6 位'
  if (mode.value === 'register' && form.password !== confirmPassword.value) return '两次输入的密码不一致'
  if (mode.value === 'register' && !agreed.value) return '请先同意小原圈注册协议'
  return ''
}
function resolveRegisterError(error: unknown) {
  const text = error instanceof Error ? error.message : ''
  if (text.includes('daily registration limit exceeded')) return '当前 IP 今日已注册过账号，请明天再试'
  if (text.includes('mobile already registered')) return '该手机号已注册，请直接登录'
  return '注册请求未完成，请稍后重试'
}
async function handleSubmit() {
  if (loading.value) return
  isError.value = false
  const validationMessage = validateForm()
  if (validationMessage) { message.value = validationMessage; isError.value = true; return }
  loading.value = true
  message.value = ''
  try {
    const payload = { ...form }
    const token = mode.value === 'login' ? await login(payload) : await register(payload)
    if (!token.accessToken) throw new Error('access token missing')
    userStore.setToken(token.accessToken)
    message.value = mode.value === 'login' ? '登录成功，正在进入首页' : '注册成功，正在进入首页'
    setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home/index' }), 500)
  } catch (error) {
    message.value = mode.value === 'login' ? '登录请求未完成，请检查账号密码或稍后重试' : resolveRegisterError(error)
    isError.value = true
  } finally { loading.value = false }
}
</script>

<style scoped>
.login-page { min-height:100vh; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.brand-card { margin-top:20rpx; padding:42rpx 28rpx; text-align:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.brand-logo { width:104rpx; height:104rpx; margin:0 auto; border-radius:36rpx; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:60rpx; font-weight:950; box-shadow:0 18rpx 36rpx rgba(255,122,69,.2); }
.brand-title { margin-top:22rpx; color:#3a2a1f; font-size:42rpx; font-weight:950; }
.brand-desc { margin-top:10rpx; color:#9b7560; font-size:24rpx; }
.form-card { margin-top:22rpx; padding:24rpx; border-color:#ffd9bd; }
.mode-tabs { display:flex; gap:12rpx; margin-bottom:22rpx; padding:8rpx; border-radius:999rpx; background:#fff4ea; }
.mode-tab { flex:1; height:62rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; color:#9b7560; font-size:25rpx; font-weight:900; }
.mode-tab.active { background:#ff7a45; color:#fff; box-shadow:0 12rpx 24rpx rgba(255,122,69,.22); }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.section-desc { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.45; }
.field { box-sizing:border-box; width:100%; height:78rpx; margin-top:18rpx; padding:0 22rpx; border:1rpx solid #ffd9bd; border-radius:22rpx; background:#fffaf6; color:#3a2a1f; font-size:27rpx; }
.helper-row { margin-top:16rpx; display:flex; justify-content:space-between; align-items:center; color:#9b7560; font-size:22rpx; }
.remember,.agree-row { display:flex; align-items:center; gap:8rpx; }
.link { color:#ff7a45; font-weight:900; }
.agreement-card { margin-top:18rpx; padding:18rpx; border:1rpx solid #ffd9bd; border-radius:22rpx; background:#fffaf6; }
.agree-row { color:#3a2a1f; font-size:23rpx; font-weight:800; }
.fake-check { width:34rpx; height:34rpx; border:2rpx solid #ffb08a; border-radius:10rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:24rpx; font-weight:950; background:#fff; }
.fake-check.checked { border-color:#ff7a45; background:#ff7a45; }
.agreement-title { margin-top:16rpx; color:#3a2a1f; font-size:24rpx; font-weight:950; }
.rule-item { position:relative; margin-top:10rpx; padding-left:22rpx; color:#8d6b59; font-size:22rpx; line-height:1.45; }
.rule-item::before { content:'•'; position:absolute; left:0; color:#ff7a45; font-weight:950; }
.primary-btn { margin-top:24rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:28rpx; font-weight:950; }
.primary-btn[disabled] { opacity:.66; }
.status-text { margin-top:16rpx; color:#16a34a; font-size:23rpx; text-align:center; }
.status-text.error { color:#dc2626; }
</style>
