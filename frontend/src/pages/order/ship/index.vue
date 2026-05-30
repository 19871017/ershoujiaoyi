<template>
  <view class="page-shell ship-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 卖家履约</view>
        <view class="page-title">填写发货信息</view>
        <view class="page-desc">付款后请尽快发货，平台会把物流信息同步给买家。</view>
      </view>
      <view class="hero-icon">📦</view>
    </view>

    <view v-if="errorText" class="status-card ds-card danger">
      <view class="section-title">暂不能发货</view>
      <view class="safe-line">{{ errorText }}</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">订单 {{ orderNo || '未选择' }}</view>
          <view class="section-desc">提交后以服务端订单履约状态为准，页面不会本地伪造发货成功。</view>
        </view>
        <view class="order-chip">{{ shipType === 'EXPRESS' ? '快递' : '交付' }}</view>
      </view>
      <view class="ship-row">
        <view v-for="item in shipTypes" :key="item.value" class="ship-chip tapable" :class="{ active: shipType === item.value }" @click="shipType = item.value">{{ item.label }}</view>
      </view>
      <input v-if="shipType === 'EXPRESS'" v-model.trim="company" class="field" maxlength="24" placeholder="快递公司，例如顺丰/圆通" />
      <input v-if="shipType === 'EXPRESS'" v-model.trim="trackingNo" class="field" maxlength="40" placeholder="运单号" />
      <textarea v-model.trim="remark" class="textarea" maxlength="80" :placeholder="shipType === 'MEETUP' ? '请填写线下交付地点/时间，正式履约状态以平台订单记录为准' : '发货备注，可说明包装、清洁、票据等'" />
      <button class="primary-btn" :disabled="submitting || !orderNo" @click="submitShip">{{ submitting ? '提交中...' : '确认发货' }}</button>
    </view>

    <view class="safe-card ds-card">
      <view class="section-title">发货安全提醒</view>
      <view class="safe-line">请保留发货凭证，避免使用无法追踪的物流方式。</view>
      <view class="safe-line">线下交付建议选择公共地点；订单、发货和售后状态以平台记录为准。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { shipOrder, type ShippingType } from '../../../api/modules/order'
import { assertBackendShipResponse, decodeRouteValue, isValidBackendOrderNo, shipTypes } from './order-ship-helpers'

const orderNo = ref('')
const errorText = ref('')
const shipType = ref<ShippingType>('EXPRESS')
const company = ref('')
const trackingNo = ref('')
const remark = ref('')
const submitting = ref(false)

function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeOrderNo = decodeRouteValue('orderNo', current?.options?.orderNo || hashParams?.get('orderNo') || '')
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    console.warn('order ship invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  if (!isValidBackendOrderNo(routeOrderNo)) {
    orderNo.value = ''
    errorText.value = '缺少有效订单号，请从订单列表进入发货'
    return
  }
  orderNo.value = routeOrderNo
  errorText.value = ''
}

async function submitShip(): Promise<void> {
  const safeOrderNo = orderNo.value
  const safeCompany = company.value.trim()
  const safeTrackingNo = trackingNo.value.trim()
  const safeRemark = remark.value.trim()
  if (!isValidBackendOrderNo(safeOrderNo)) return uni.showToast({ title: '订单编号无效，已阻止发货', icon: 'none' })
  if (shipType.value === 'EXPRESS' && (!safeCompany || !safeTrackingNo)) return uni.showToast({ title: '请填写快递公司和运单号', icon: 'none' })
  if (shipType.value === 'MEETUP' && !safeRemark) return uni.showToast({ title: '请填写线下交付备注', icon: 'none' })

  submitting.value = true
  try {
    const response = await shipOrder(safeOrderNo, { shippingType: shipType.value, shippingCompany: safeCompany, trackingNo: safeTrackingNo, remark: safeRemark })
    assertBackendShipResponse(response, safeOrderNo)
    const modalOptions = {
      title: '已同步发货',
      content: `订单已由服务端更新为已发货，发货时间：${response.shippedAt}`,
      showCancel: true,
      confirmText: '查看物流',
      cancelText: '返回订单',
      fail(error: unknown) {
        console.warn('order ship success modal failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '发货状态已同步，请从订单详情查看', icon: 'none' })
      },
      success(modal: { confirm?: boolean }) {
        redirectAfterShip(safeOrderNo, modal.confirm === true)
      }
    }
    uni.showModal(modalOptions)
  } catch (error) {
    console.warn('order ship submit failed', { orderNo: safeOrderNo, shippingType: shipType.value, error })
    uni.showToast({ title: '发货信息未同步，请检查订单状态后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
function redirectAfterShip(orderNoSnapshot: string, showLogistics: boolean): void {
  const page = showLogistics ? '/pages/order/logistics/index' : '/pages/order/detail/index'
  const route = {
    url: `${page}?orderNo=${encodeURIComponent(orderNoSnapshot)}`,
    fail(error: unknown) {
      console.warn('order ship redirect failed', { orderNo: orderNoSnapshot, target: page, error })
      uni.showToast({ title: '发货状态已同步，但暂时无法打开订单页', icon: 'none' })
    }
  }
  uni.redirectTo(route)
}
onMounted(readQuery)
</script>

<style scoped lang="scss" src="./style.scss"></style>
