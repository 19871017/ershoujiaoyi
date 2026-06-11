<template>
  <view class="page-shell login-page">
    <view class="brand-card ds-card">
      <view class="brand-logo">
        <image class="brand-logo-img" :src="brandLogoUrl" mode="aspectFit" />
      </view>
      <view class="brand-title">小原圈</view>
    </view>

    <view class="form-card ds-card">
      <view class="mode-tabs">
        <view class="mode-tab tapable" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</view>
        <view class="mode-tab tapable" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</view>
      </view>

      <view class="section-title">{{ mode === 'login' ? '登录' : '注册' }}</view>
      <input v-model.trim="form.mobile" class="field" maxlength="11" type="number" placeholder="手机号" />
      <input v-model="form.password" class="field" password maxlength="32" placeholder="密码" />
      <input
        v-if="mode === 'register'"
        v-model="confirmPassword"
        class="field"
        password
        maxlength="32"
        placeholder="确认密码"
      />
      <view v-if="mode === 'register'" class="gender-row">
        <view class="gender-chip tapable" :class="{ active: registerGender === 'goddess' }" @click="registerGender = 'goddess'">♀ 女</view>
        <view class="gender-chip tapable" :class="{ active: registerGender === 'god' }" @click="registerGender = 'god'">♂ 男</view>
      </view>

      <view v-if="mode === 'register'" class="agreement-card">
        <view class="agree-row tapable" @click="toggleAgreement">
          <view class="fake-check" :class="{ checked: agreed }">{{ agreed ? '✓' : '' }}</view>
          <text>同意注册协议</text>
        </view>
      </view>

      <button class="primary-btn" :disabled="loading" @click="handleSubmit">
        {{ loading ? (mode === 'login' ? '登录中...' : '注册中...') : (mode === 'login' ? '登录' : '注册') }}
      </button>
      <view v-if="message" class="status-text" :class="{ error: isError }">{{ message }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { login, register, type LoginRequest, type RegisterGender } from '../../../api/modules/auth'
import { useUserStore } from '../../../store/modules/user'
import { loginRedirectIsTabbar, normalizeLoginRedirect } from './login-redirect'
import brandLogoUrl from '../../../assets/brand/xiaoyuanquan-logo-mark.png'

type AuthMode = 'login' | 'register'

const form = reactive<LoginRequest>({ mobile: '', password: '' })
const mode = ref<AuthMode>('login')
const loading = ref(false)
const message = ref('')
const isError = ref(false)
const agreed = ref(false)
const confirmPassword = ref('')
const registerGender = ref<RegisterGender>('goddess')
const userStore = useUserStore()
const redirectUrl = ref('')

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
function resolveLoginTarget() {
  const fallback = '/pages/tabbar/home/index'
  const target = redirectUrl.value || fallback
  if (loginRedirectIsTabbar(target)) {
    return () => uni.switchTab({ url: target })
  }
  return () => uni.redirectTo({ url: target })
}
function validateForm() {
  if (!validMobile(form.mobile)) return '请输入 11 位手机号'
  if (!form.password || form.password.length < 6) return '密码至少 6 位'
  if (mode.value === 'register' && form.password !== confirmPassword.value) return '两次输入的密码不一致'
  if (mode.value === 'register' && !registerGender.value) return '请选择性别'
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
    const token = mode.value === 'login' ? await login(payload) : await register({ ...payload, gender: registerGender.value })
    if (!token.accessToken) throw new Error('access token missing')
    userStore.setToken(token.accessToken)
    message.value = mode.value === 'login' ? '登录成功' : '注册成功'
    const openTarget = resolveLoginTarget()
    setTimeout(() => openTarget(), 300)
  } catch (error) {
    const detail = error instanceof Error && error.message ? `：${error.message}` : ''
    message.value = mode.value === 'login' ? `登录请求未完成，请检查账号密码或稍后重试${detail}` : resolveRegisterError(error)
    isError.value = true
  } finally { loading.value = false }
}

onLoad((options) => {
  redirectUrl.value = normalizeLoginRedirect(String(options?.redirect || ''))
})
</script>

<style scoped>
.login-page { min-height:100vh; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.brand-card { margin-top:20rpx; padding:42rpx 28rpx; text-align:center; border-color:#ffd9bd; background:linear-gradient(135deg,#fff,#fff3e7); }
.brand-logo { width:104rpx; height:104rpx; margin:0 auto; border-radius:36rpx; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:60rpx; font-weight:950; box-shadow:0 18rpx 36rpx rgba(255,122,69,.2); overflow:hidden; }
.brand-logo-img { width:92rpx; height:92rpx; display:block; filter:drop-shadow(0 8rpx 14rpx rgba(120,45,22,.16)); }
.brand-title { margin-top:22rpx; color:#3a2a1f; font-size:42rpx; font-weight:950; }
.form-card { margin-top:22rpx; padding:24rpx; border-color:#ffd9bd; }
.mode-tabs { display:flex; gap:12rpx; margin-bottom:22rpx; padding:8rpx; border-radius:999rpx; background:#fff4ea; }
.mode-tab { flex:1; height:62rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; color:#9b7560; font-size:25rpx; font-weight:900; }
.mode-tab.active { background:#ff7a45; color:#fff; box-shadow:0 12rpx 24rpx rgba(255,122,69,.22); }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.field { box-sizing:border-box; width:100%; height:78rpx; margin-top:18rpx; padding:0 22rpx; border:1rpx solid #ffd9bd; border-radius:22rpx; background:#fffaf6; color:#3a2a1f; font-size:27rpx; }
.gender-row { margin-top:18rpx; display:flex; gap:12rpx; }
.gender-chip { flex:1; height:70rpx; border-radius:22rpx; border:1rpx solid #ffd9bd; background:#fffaf6; color:#9b7560; display:flex; align-items:center; justify-content:center; font-size:25rpx; font-weight:900; }
.gender-chip.active { border-color:#ff7a45; background:#ff7a45; color:#fff; }
.agree-row { display:flex; align-items:center; gap:8rpx; }
.agreement-card { margin-top:18rpx; padding:18rpx; border:1rpx solid #ffd9bd; border-radius:22rpx; background:#fffaf6; }
.agree-row { color:#3a2a1f; font-size:23rpx; font-weight:800; }
.fake-check { width:34rpx; height:34rpx; border:2rpx solid #ffb08a; border-radius:10rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:24rpx; font-weight:950; background:#fff; }
.fake-check.checked { border-color:#ff7a45; background:#ff7a45; }
.primary-btn { margin-top:24rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:28rpx; font-weight:950; }
.primary-btn[disabled] { opacity:.66; }
.status-text { margin-top:16rpx; color:#16a34a; font-size:23rpx; text-align:center; }
.status-text.error { color:#dc2626; }
</style>
