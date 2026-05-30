<template>
  <view class="page-shell method-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 支付方式</view>
        <view class="page-title">{{ title }}</view>
        <view class="page-desc">当前通道尚未完成商户接入，不会在前端伪造支付成功。</view>
      </view>
      <view class="hero-icon">{{ icon }}</view>
    </view>

    <view class="status-card ds-card danger">
      <view class="section-head">
        <view>
          <view class="section-title">通道未开通</view>
          <view class="status-line">{{ title }}暂不可用于当前订单支付</view>
        </view>
        <view class="method-chip">未接入</view>
      </view>
      <view class="desc">正式接入前必须由平台创建支付单、完成回调验签、订单状态机、退款和对账任务；前端不得保存商户号、密钥或伪造成功状态。</view>
      <view v-if="routeErrorText" class="warning-line">{{ routeErrorText }}</view>
      <view class="warning-line">当前页面只做说明与安全返回，不会发起第三方扣款。</view>
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

    <button class="primary-btn" :disabled="returning" @click="backToCheckout">{{ returning ? '读取订单中...' : '返回收银台' }}</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getOrderDetail } from '../../../api/modules/order'

type Method = 'WECHAT' | 'ALIPAY' | 'UNKNOWN'

const method = ref<Method>('UNKNOWN')
const orderNo = ref('')
const returning = ref(false)
const routeErrorText = ref('')
const title = computed(() => method.value === 'WECHAT' ? '微信支付' : method.value === 'ALIPAY' ? '支付宝' : '支付方式异常')
const icon = computed(() => method.value === 'WECHAT' ? '💚' : method.value === 'ALIPAY' ? '💙' : '!')
const steps = ['平台创建支付单并保存幂等号', '前端只拉起官方收银台，不接触密钥', '支付平台回调必须验签并防重放', '订单、钱包、账本状态统一事务更新', '退款、提现和对账单独审计']

function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('payment method route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    routeErrorText.value = '订单链接异常，请返回订单详情重新进入支付。'
    return ''
  }
}

function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  routeErrorText.value = ''
  const value = current?.options?.method || hash?.get('method') || ''
  if (value === 'WECHAT' || value === 'ALIPAY') {
    method.value = value
  } else {
    method.value = 'UNKNOWN'
    routeErrorText.value = '支付方式参数异常，未进入任何第三方支付通道。'
    console.warn('payment method invalid route method', { rawLength: value.length, rawPreview: value.slice(0, 24) })
  }
  const routeOrderNo = decodeRouteValue('orderNo', current?.options?.orderNo || hash?.get('orderNo') || '')
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    routeErrorText.value = '订单号无效，请返回订单详情重新进入支付。'
    console.warn('payment method invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  orderNo.value = routeOrderNo
}

function isValidBackendOrderNo(value: string): boolean {
  return /^OD-[0-9]{1,10}$/.test(value)
}

async function backToCheckout(): Promise<void> {
  if (returning.value) return

  const safeOrderNo = orderNo.value
  if (!isValidBackendOrderNo(safeOrderNo)) {
    uni.showToast({ title: '订单号无效，已清除订单信息并返回收银台重新读取', icon: 'none' })
    const cleanRoute = {
      url: '/pages/payment/checkout/index',
      fail(error: unknown) {
        console.warn('payment method clean checkout redirect failed', { error })
        uni.showToast({ title: '订单号无效，且暂时无法返回收银台', icon: 'none' })
      }
    }
    uni.redirectTo(cleanRoute)
    return
  }

  returning.value = true
  try {
    const detail = await getOrderDetail(safeOrderNo)
    if (!isValidBackendOrderNo(detail.orderNo)) throw new Error('订单编号异常，已阻止返回收银台')
    if (detail.orderNo !== safeOrderNo) throw new Error('订单编号校验不一致，已阻止返回收银台')

    const checkoutRoute = { orderNo: detail.orderNo }
    const route = {
      url: `/pages/payment/checkout/index?orderNo=${encodeURIComponent(checkoutRoute.orderNo)}`,
      fail(error: unknown) {
        console.warn('payment method checkout redirect failed', { orderNo: checkoutRoute.orderNo, error })
        uni.showToast({ title: '订单已校验，但暂时无法返回收银台', icon: 'none' })
      }
    }
    uni.redirectTo(route)
  } catch (error) {
    console.warn('payment method order reload failed', { orderNo: safeOrderNo, error })
    uni.showToast({ title: error instanceof Error ? error.message : '订单读取失败，已阻止返回收银台', icon: 'none' })
  } finally {
    returning.value = false
  }
}

onMounted(readQuery)
</script>
<style scoped>
.method-page{min-height:100vh;padding-top:18rpx;padding-bottom:44rpx;background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.30),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.46),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 48%,#fff5ee 100%)}
.hero,.status-card,.steps-card,.order-card{margin-top:12rpx;padding:18rpx;border-color:rgba(255,217,189,.78);box-shadow:0 16rpx 34rpx rgba(132,70,36,.08)}
.hero{margin-top:0;display:flex;justify-content:space-between;align-items:center;gap:12rpx;background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96))}
.kicker{color:#df6735;font-size:20rpx;font-weight:950;letter-spacing:.16rpx}
.hero-icon{width:64rpx;height:64rpx;border-radius:22rpx;background:linear-gradient(135deg,#3a261a,#6f432b);color:#fffaf4;display:flex;align-items:center;justify-content:center;font-size:28rpx;box-shadow:0 10rpx 20rpx rgba(58,38,26,.14);flex:0 0 auto}
.section-title{color:#342116;font-size:25rpx;font-weight:950;letter-spacing:.14rpx}
.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}
.method-chip{flex:0 0 auto;padding:7rpx 12rpx;border:1rpx solid rgba(220,38,38,.16);border-radius:999rpx;background:#fff1f2;color:#be123c;font-size:20rpx;font-weight:950}
.danger{border-color:#fecaca;background:linear-gradient(180deg,#fff7f7,#fffafa)}
.order-card,.steps-card{background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97))}
.status-line{margin-top:8rpx;color:#dc2626;font-size:22rpx;font-weight:950;line-height:1.4}
.desc,.step,.info-row{margin-top:8rpx;color:#7b5542;font-size:21rpx;line-height:1.42;font-weight:650}
.warning-line{margin-top:10rpx;padding:10rpx 12rpx;border-radius:18rpx;background:#fff1f2;color:#be123c;font-size:20rpx;font-weight:850;line-height:1.38}
.step{padding:12rpx 13rpx;border-radius:20rpx;background:linear-gradient(180deg,#fffdf9,#fff8f1);border:1rpx solid rgba(255,217,189,.70);box-shadow:inset 0 0 0 1rpx rgba(255,255,255,.62)}
.info-row{display:flex;justify-content:space-between;gap:12rpx;padding-bottom:8rpx;border-bottom:1rpx solid rgba(255,217,189,.50)}
.info-row:last-child{border-bottom:0;padding-bottom:0}
.info-row text:first-child{flex:0 0 auto;color:#8f6b57;font-weight:850}
.info-row text:last-child{color:#342116;font-weight:900;text-align:right}
.primary-btn{margin-top:16rpx;background:linear-gradient(135deg,#ef6f3f,#ff8b76);color:#fffaf4;box-shadow:0 12rpx 26rpx rgba(255,122,69,.18)}
.primary-btn[disabled]{opacity:.65}
</style>
