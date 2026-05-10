<template>
  <view class="page-shell method-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 支付方式</view>
        <view class="page-title">{{ title }}</view>
        <view class="page-desc">当前通道尚未完成商户接入，不会在前端模拟支付成功。</view>
      </view>
      <view class="hero-icon">{{ icon }}</view>
    </view>

    <view class="status-card ds-card danger">
      <view class="section-title">通道未开通</view>
      <view class="status-line">{{ title }}暂不可用于当前订单支付</view>
      <view class="desc">正式接入前必须由后端创建支付单、完成回调验签、订单状态机、退款和对账任务；前端不得保存商户号、密钥或伪造成功状态。</view>
    </view>

    <view class="order-card ds-card">
      <view class="section-title">当前订单</view>
      <view class="info-row"><text>订单号</text><text>订单号需返回安全收银台重新读取</text></view>
      <view class="info-row"><text>安全说明</text><text>支付方式页不展示路由传入的订单号</text></view>
    </view>

    <view class="steps-card ds-card">
      <view class="section-title">正式接入检查</view>
      <view v-for="item in steps" :key="item" class="step">{{ item }}</view>
    </view>

    <button class="primary-btn" @click="backToCheckout">返回收银台</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getOrderDetail } from '../../../api/modules/order'
type Method='WECHAT'|'ALIPAY'
const method=ref<Method>('WECHAT')
const orderNo=ref('')
const title=computed(()=>method.value==='WECHAT'?'微信支付':'支付宝')
const icon=computed(()=>method.value==='WECHAT'?'💚':'💙')
const steps=['后端创建支付单并保存幂等号','前端只拉起官方收银台，不接触密钥','支付平台回调必须验签并防重放','订单、钱包、账本状态统一事务更新','退款、提现和对账单独审计']
function readQuery(){const pages=getCurrentPages(); const current=pages.length?pages[pages.length-1] as unknown as {options?:Record<string,string>}:undefined; const hash=typeof window!=='undefined'?new URLSearchParams(window.location.hash.split('?')[1]||''):undefined; const value=current?.options?.method||hash?.get('method'); method.value=value==='ALIPAY'?'ALIPAY':'WECHAT'; orderNo.value=current?.options?.orderNo||hash?.get('orderNo')||''}
function isValidBackendOrderNo(value: string) { return /^[A-Z]{2,10}-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value) }
async function backToCheckout(){
  if(!isValidBackendOrderNo(orderNo.value)){ uni.showToast({title:'订单号无效，已阻止返回收银台',icon:'none'}); uni.redirectTo({ url: '/pages/payment/checkout/index' }); return }
  try {
    const detail = await getOrderDetail(orderNo.value)
    const checkoutRoute = { orderNo: detail.orderNo }
    uni.redirectTo({url:`/pages/payment/checkout/index?orderNo=${encodeURIComponent(checkoutRoute.orderNo)}`})
  } catch {
    uni.showToast({title:'订单读取失败，已阻止返回收银台',icon:'none'})
  }
}
onMounted(readQuery)
</script>
<style scoped>
.method-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.status-card,.steps-card,.order-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#3a2a1f;color:#fff;display:flex;align-items:center;justify-content:center;font-size:36rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.danger{border-color:#fecaca;background:#fff7f7}.status-line{margin-top:12rpx;color:#ef4444;font-size:25rpx;font-weight:950}.desc,.step,.info-row{margin-top:12rpx;color:#7b5542;font-size:23rpx;line-height:1.5}.step{padding:14rpx;border-radius:20rpx;background:#fffaf6;border:1rpx solid #ffd9bd}.info-row{display:flex;justify-content:space-between;gap:18rpx}.info-row text:last-child{color:#3a2a1f;font-weight:900;text-align:right}.primary-btn{margin-top:22rpx}
</style>
