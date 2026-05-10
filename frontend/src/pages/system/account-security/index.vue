<template>
  <view class="page-shell security-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 账号安全</view>
        <view class="page-title">安全中心</view>
        <view class="page-desc">安全状态以服务端账号风控接口为准，接口失败时不展示本地分数或设备样例。</view>
      </view>
      <view class="hero-score">{{ securityScore }}</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">安全项目</view>
      <view v-for="item in rows" :key="item.label" class="row">
        <view class="row-icon">{{ item.icon }}</view>
        <view class="row-main">
          <view class="label">{{ item.label }}</view>
          <view class="desc">{{ item.desc }}</view>
        </view>
        <button class="mini-btn" @click="operate(item)">{{ item.action }}</button>
      </view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">最近登录设备</view>
      <view v-if="loading" class="empty-state">账号安全信息加载中...</view>
      <view v-else-if="loadError" class="empty-state">{{ loadError }}</view>
      <view v-else-if="devices.length === 0" class="empty-state">服务端暂未返回登录设备记录，未展示本地登录设备样例。</view>
      <view v-else v-for="item in devices" :key="`${item.deviceName}-${item.loginAt}`" class="device-row">
        <view>
          <view class="label">{{ item.deviceName }}</view>
          <view class="desc">{{ item.loginAt || '暂无时间' }} · {{ item.city || '暂无城市' }}</view>
        </view>
        <text class="safe-tag">{{ item.status || '后端记录' }}</text>
      </view>
    </view>

    <view class="section-card ds-card">
      <view class="section-title">安全建议</view>
      <view class="tip">不要私下转账，不要把验证码、登录密码、支付密码发给任何人。异常登录和高风险交易以服务端风控记录为准。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAccountSecurity, type AccountSecurityResponse } from '../../../api/modules/user'

interface SecurityRow { icon: string; label: string; desc: string; action: string }
type LoginDevice = AccountSecurityResponse['recentDevices'][number]

const security = ref<AccountSecurityResponse | null>(null)
const loading = ref(false)
const loadError = ref('')
const securityScore = computed(() => security.value?.securityScore || '--')
const rows = computed<SecurityRow[]>(() => [
  { icon: '🔑', label: '登录密码', desc: '密码修改需通过后端安全校验流程', action: '修改' },
  { icon: '📱', label: '绑定手机号', desc: `手机号信息由服务端脱敏返回：${security.value?.maskedPhone || '暂无后端记录'}`, action: '换绑' },
  { icon: '🛡️', label: '设备保护', desc: '新设备登录策略需由账号安全接口控制', action: '开启' },
  { icon: '🪪', label: '实名认证', desc: '提现和高额交易需完成实名审核', action: '去认证' }
])
const devices = computed(() => security.value?.recentDevices || [] as LoginDevice[])

async function loadSecurity() {
  loading.value = true
  loadError.value = ''
  security.value = null
  try {
    security.value = await getAccountSecurity()
  } catch {
    loadError.value = '账号安全接口加载失败，未展示本地分数或登录设备样例。'
  } finally {
    loading.value = false
  }
}

function operate(item: SecurityRow) {
  if (item.label === '实名认证') {
    uni.navigateTo({ url: '/pages/user/identity/index' })
    return
  }
  uni.showModal({
    title: item.label,
    content: '账号安全变更需通过后端安全接口处理，当前未执行任何账号安全变更。',
    showCancel: false
  })
}

onMounted(loadSecurity)
</script>

<style scoped>
.security-page { background: linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.section-card { margin-top:18rpx; padding:24rpx; border-color:#ffd9bd; }
.hero { display:flex; justify-content:space-between; align-items:center; background:linear-gradient(135deg,#fff,#fff3e7); }
.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }
.hero-score { width:92rpx; height:92rpx; border-radius:50%; background:#ffd9bd; color:#9b7560; display:flex; align-items:center; justify-content:center; font-size:34rpx; font-weight:950; }
.section-title { color:#3a2a1f; font-size:30rpx; font-weight:950; }
.row,.device-row { min-height:104rpx; border-bottom:1rpx solid #ffe5ef; display:flex; align-items:center; gap:16rpx; }
.row:last-child,.device-row:last-child { border-bottom:0; }
.row-icon { width:64rpx; height:64rpx; border-radius:20rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; }
.row-main { flex:1; min-width:0; }
.label { color:#3a2a1f; font-size:26rpx; font-weight:950; }
.desc,.tip,.page-desc,.empty-state { margin-top:6rpx; color:#9b7560; font-size:22rpx; line-height:1.5; }
.empty-state { padding:22rpx 0 4rpx; }
.mini-btn { margin:0; padding:0 20rpx; height:52rpx; line-height:52rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:21rpx; }
.safe-tag { padding:8rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
</style>
