<template>
  <view class="page-shell settings-page">
    <view class="page-title">设置</view>
    <view class="page-desc">管理账号安全、隐私、通知和开发预览开关。</view>

    <view class="settings-card ds-card">
      <view v-for="item in rows" :key="item.label" class="setting-row tapable" @click="handleRow(item)">
        <view class="row-left">
          <view class="row-icon">{{ item.icon }}</view>
          <view>
            <view class="row-label">{{ item.label }}</view>
            <view class="row-desc">{{ item.desc }}</view>
          </view>
        </view>
        <view v-if="item.switch" :class="['switch', { on: item.on }]" @click.stop="toggle(item)"><view></view></view>
        <view v-else class="arrow">›</view>
      </view>
    </view>

    <view class="danger-card ds-card">
      <view class="section-title">安全提醒</view>
      <view class="danger-text">不要私下转账，不要发送验证码、密码、银行卡完整信息。平台客服不会索要支付密码。</view>
    </view>

    <button class="logout-btn" @click="logout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useUserStore } from '../../../store/modules/user'
interface Row { icon: string; label: string; desc: string; switch?: boolean; on?: boolean; url?: string }
const userStore = useUserStore()
const rows = reactive<Row[]>([
  { icon: '🔐', label: '账号安全', desc: '密码、手机号、登录设备', url: '/pages/system/account-security/index' },
  { icon: '🪪', label: '实名认证', desc: '保障交易和提现安全', url: '/pages/user/identity/index' },
  { icon: '💬', label: '消息通知', desc: '订单、私信、审核通知（需后端配置接口）', switch: true, on: false },
  { icon: '📍', label: '定位权限', desc: '同城推荐需系统授权与后端定位配置', switch: true, on: false },
  { icon: '🧪', label: '开发预览模式', desc: '仅展示当前构建环境，不能在页面内切换', switch: true, on: false },
  { icon: '📜', label: '用户协议与隐私', desc: '查看平台规则和隐私说明', url: '/pages/system/privacy/index' }
])
function toggle(item: Row) {
  uni.showToast({ title: `${item.label}需通过后端或系统设置变更，当前未执行任何设置修改`, icon: 'none' })
}
function handleRow(item: Row) { if (item.url) uni.navigateTo({ url: item.url }); else if (!item.switch) uni.showToast({ title: `${item.label}暂未接入正式页面`, icon: 'none' }) }
function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号吗？退出后会清除本机登录态。',
    success: (res) => {
      if (!res.confirm) return
      userStore.clearSession()
      uni.removeStorageSync('token')
      uni.removeStorageSync('user')
      uni.reLaunch({ url: '/pages/auth/login/index' })
    }
  })
}
</script>

<style scoped>
.settings-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.settings-card,.danger-card { margin-top:20rpx; overflow:hidden; border-color:#ffd9bd; }
.setting-row { min-height:106rpx; padding:0 22rpx; display:flex; align-items:center; justify-content:space-between; border-bottom:1rpx solid #ffe5ef; }
.setting-row:last-child { border-bottom:0; }
.row-left { display:flex; align-items:center; gap:16rpx; min-width:0; }
.row-icon { width:56rpx; height:56rpx; border-radius:20rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; font-size:28rpx; }
.row-label { color:#3a2a1f; font-size:26rpx; font-weight:950; }
.row-desc { margin-top:5rpx; color:#9b7560; font-size:21rpx; }
.arrow { color:#d79262; font-size:36rpx; }
.switch { width:82rpx; height:44rpx; padding:4rpx; box-sizing:border-box; border-radius:999rpx; background:#ffd9bd; }
.switch view { width:36rpx; height:36rpx; border-radius:50%; background:#fff; transition:.2s; }
.switch.on { background:#ff7a45; }
.switch.on view { transform:translateX(38rpx); }
.danger-card { padding:22rpx; }
.section-title { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.danger-text { margin-top:10rpx; color:#9b7560; font-size:23rpx; line-height:1.55; }
.logout-btn { margin-top:28rpx; height:72rpx; line-height:72rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#be123c; font-size:25rpx; font-weight:950; }
</style>
