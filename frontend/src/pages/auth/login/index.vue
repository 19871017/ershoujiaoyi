<template>
  <view class="page-shell login-page">
    <view class="brand-card ds-card">
      <view class="brand-logo">♡</view>
      <view class="brand-title">小原圈</view>
      <view class="brand-desc">登录后可发布宝贝、私信卖家、查看钱包和订单。</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">手机号登录</view>
      <view class="section-desc">当前支持密码登录；验证码入口用于开发预览验证登录体验。</view>
      <input v-model.trim="form.mobile" class="field" maxlength="11" type="number" placeholder="请输入手机号" />
      <input v-model="form.password" class="field" password maxlength="32" placeholder="请输入密码" />
      <view class="helper-row">
        <label class="remember"><checkbox :checked="remember" color="#ff7a45" @click="remember = !remember" />记住登录</label>
        <view class="link tapable" @click="useCodePreview">验证码登录</view>
      </view>
      <button class="primary-btn" :disabled="loading" @click="handleLogin">
        {{ loading ? '登录中...' : '登录并进入小原圈' }}
      </button>
      <view v-if="message" class="status-text" :class="{ error: isError }">{{ message }}</view>
    </view>

    <view class="safe-card ds-card">
      <view class="safe-title">安全说明</view>
      <view class="safe-text">不会在前端保存真实密码或密钥；正式上线会改为服务端签发 Token 和管理员权限控制。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { login, type LoginRequest } from '../../../api/modules/auth'
import { useUserStore } from '../../../store/modules/user'

const form = reactive<LoginRequest>({ mobile: '', password: '' })
const loading = ref(false)
const message = ref('')
const isError = ref(false)
const remember = ref(true)
const userStore = useUserStore()
function validMobile(value: string) { return /^1\d{10}$/.test(value) }
function toast(title: string) { uni.showToast({ title, icon: 'none' }) }
function useCodePreview() {
  message.value = '验证码登录需要后端验证码服务，当前请使用密码登录'
  isError.value = false
}
async function handleLogin() {
  if (loading.value) return
  isError.value = false
  if (!validMobile(form.mobile)) { message.value = '请输入 11 位手机号'; isError.value = true; return }
  if (!form.password || form.password.length < 6) { message.value = '密码至少 6 位'; isError.value = true; return }
  loading.value = true
  message.value = ''
  try {
    const token = await login({ ...form })
    if (!token.accessToken) throw new Error('access token missing')
    userStore.setToken(token.accessToken)
    message.value = '登录成功，正在进入首页'
    setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home/index' }), 500)
  } catch {
    message.value = '登录请求未完成，请检查账号密码或稍后重试'
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
.form-card,.safe-card { margin-top:22rpx; padding:24rpx; border-color:#ffd9bd; }
.section-title,.safe-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.section-desc,.safe-text { margin-top:8rpx; color:#9b7560; font-size:23rpx; line-height:1.45; }
.field { box-sizing:border-box; width:100%; height:78rpx; margin-top:18rpx; padding:0 22rpx; border:1rpx solid #ffd9bd; border-radius:22rpx; background:#fffaf6; color:#3a2a1f; font-size:27rpx; }
.helper-row { margin-top:16rpx; display:flex; justify-content:space-between; align-items:center; color:#9b7560; font-size:22rpx; }
.remember { display:flex; align-items:center; gap:8rpx; }
.link { color:#ff7a45; font-weight:900; }
.primary-btn { margin-top:24rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:28rpx; font-weight:950; }
.status-text { margin-top:16rpx; color:#16a34a; font-size:23rpx; text-align:center; }
.status-text.error { color:#dc2626; }
</style>
