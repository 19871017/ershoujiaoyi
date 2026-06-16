<template>
  <view class="page-shell order-detail-page">
    <view v-if="loading" class="status-card ds-card"><view class="status-icon">⌛</view><view class="status-main"><view class="status-title">正在读取订单</view><view class="status-desc">平台正在同步订单和售后状态。</view></view></view>
    <view v-else-if="errorText" class="status-card ds-card danger"><view class="status-icon">!</view><view class="status-main"><view class="status-title">订单读取失败</view><view class="status-desc">{{ errorText }}</view></view></view>

    <template v-else-if="order">
      <view class="status-card ds-card">
        <view class="status-icon">{{ current.icon }}</view>
        <view class="status-main">
          <view class="status-title">{{ current.label }}</view>
          <view class="status-desc">{{ current.desc }}</view>
        </view>
      </view>

      <view class="goods-card ds-card tapable" @click="openProduct">
        <view class="goods-cover">{{ coverIcon(order.productTitle) }}</view>
        <view class="goods-main">
          <view class="goods-title">{{ order.productTitle }}</view>
          <view class="goods-desc">{{ order.tradeRuleSnapshot }}</view>
          <view class="goods-price-label">买家应付</view>
          <view class="goods-price">¥{{ formatMoneyAmount(order.amount) }}</view>
          <view class="goods-subprice">卖家结算 ¥{{ sellerAmountText }} · 平台加价 ¥{{ platformMarkupText }}</view>
        </view>
        <view class="arrow">›</view>
      </view>

      <view class="flow-card ds-card">
        <view class="section-title">订单进度</view>
        <view class="flow-row">
          <view v-for="step in flow" :key="step.value" class="flow-step" :class="{ done: step.index <= current.index }">
            <view class="dot"></view><view class="flow-label">{{ step.label }}</view><view class="flow-time">{{ step.time || '待完成' }}</view>
          </view>
        </view>
      </view>

      <view v-if="showAfterSalesSummary" class="after-sales-card ds-card">
        <view class="after-sales-head">
          <view>
            <view class="section-title">售后复盘</view>
            <view class="after-sales-desc">{{ afterSalesNextStep }}</view>
          </view>
          <view class="after-sales-status">{{ afterSalesStatusLabel }}</view>
        </view>
        <view class="after-sales-row">
          <text>售后单号</text>
          <text>{{ order.afterSalesNo }}</text>
        </view>
        <view class="after-sales-action tapable" @click="openAfterSalesDetail">查看售后详情</view>
      </view>

      <view class="info-card ds-card">
        <view class="section-title">交易信息</view>
        <view v-for="item in infoRows" :key="item.label" class="info-row"><text>{{ item.label }}</text><text>{{ item.value }}</text></view>
      </view>

      <view class="safe-card ds-card">
        <view class="section-title">订单安全提示</view>
        <view class="safe-line">订单、支付、售后和聊天记录以服务端状态为准。</view>
        <view class="safe-line">如遇私下转账、绕平台交易、诱导外部联系，请立即举报。</view>
        <view class="safe-action tapable" @click="reportOrder">举报此订单</view>
      </view>

      <view class="bottom-actions">
        <button v-for="action in actions" :key="action" class="action-btn" :class="{ primary: action === '去付款' || action === '去发货' || action === '确认收货' }" :disabled="action === '确认收货' && confirming" @click="handleAction(action)">{{ action === '确认收货' && confirming ? '确认中...' : action }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { confirmReceipt, getOrderDetail, type OrderDetailResponse, type OrderListStatus } from '../../../api/modules/order'
import { resolveOrderContactTarget, type OrderContactAction } from '../../../api/modules/order-contact'
import {
  assertBackendOrderDetail,
  actionsForOrderDetail,
  coverIcon,
  decodeRouteValue,
  isValidBackendOrderNo,
  isValidBackendProductId,
  isValidAfterSalesNo,
  isValidOrderAmount,
  states
} from './order-detail-helpers'

const orderNo = ref('')
const order = ref<OrderDetailResponse | null>(null)
const loading = ref(false)
const confirming = ref(false)
const errorText = ref('')
const displayStatus = computed<OrderListStatus>(() => order.value?.afterSalesNo ? 'REFUNDING' : (order.value?.status || 'PENDING_PAY'))
const current = computed(() => states[displayStatus.value])
const showAfterSalesSummary = computed(() => !!order.value?.afterSalesNo)
const afterSalesStatusLabel = computed(() => {
  const status = String(order.value?.afterSalesStatus || 'PENDING_REVIEW').toUpperCase()
  const labels: Record<string, string> = {
    PENDING_REVIEW: '处理中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消'
  }
  return labels[status] || '处理中'
})
const afterSalesNextStep = computed(() => {
  const status = String(order.value?.afterSalesStatus || 'PENDING_REVIEW').toUpperCase()
  if (status === 'APPROVED') return '售后已通过，请继续查看售后详情和关联订单记录，资金结果以后端记录为准。'
  if (status === 'REJECTED') return '售后已驳回，如仍有争议，请补充票据并通过平台私信继续沟通。'
  if (status === 'CANCELLED') return '售后已取消，可回到订单确认当前交易状态。'
  return '售后处理中，请保留聊天、物流和票据材料，进度以平台售后详情为准。'
})
const sellerAmountText = computed(() => formatMoneyAmount(order.value?.sellerAmount ?? order.value?.amount))
const platformMarkupText = computed(() => formatMoneyAmount(order.value?.platformMarkupAmount ?? 0))
const platformMarkupRateText = computed(() => formatMarkupRate(order.value?.platformMarkupRate))
const flow = computed(() => {
  const item = order.value
  return [
    { label: '创建订单', value: 'created', index: 0, time: item?.createdAt || '' },
    { label: '付款', value: 'paid', index: 1, time: item?.paidAt || '' },
    { label: '发货', value: 'shipped', index: 2, time: item?.shippedAt || '' },
    { label: '完成', value: 'completed', index: 3, time: item?.completedAt || '' }
  ]
})
const infoRows = computed(() => order.value ? [
  { label: '订单号', value: order.value.orderNo },
  { label: '对方', value: order.value.counterpartyName },
  { label: '买家应付', value: `¥${formatMoneyAmount(order.value.amount)}` },
  { label: '卖家结算', value: `¥${sellerAmountText.value}` },
  { label: '平台加价', value: `¥${platformMarkupText.value}` },
  { label: '加价比例', value: platformMarkupRateText.value },
  { label: '交易方式', value: order.value.tradeRuleSnapshot },
  { label: '售后单', value: order.value.afterSalesNo || '暂无' }
] : [])
const actions = computed(() => {
  if (!order.value) return []
  return actionsForOrderDetail(order.value, displayStatus.value)
})
function readQuery(): void {
  const pages = getCurrentPages()
  const currentPage = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeOrderNo = decodeRouteValue('orderNo', currentPage?.options?.orderNo || hashParams?.get('orderNo') || '')
  if (routeOrderNo && !isValidBackendOrderNo(routeOrderNo)) {
    console.warn('order detail invalid route orderNo', { rawLength: routeOrderNo.length, rawPreview: routeOrderNo.slice(0, 24) })
  }
  orderNo.value = routeOrderNo
}
function navigateWithFailure(url: string, fail: (error: unknown) => void): void {
  uni.navigateTo({ url, fail } as { url: string; fail(error: unknown): void })
}
function formatMoneyAmount(value: unknown): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '0.00'
  return numeric.toFixed(2)
}
function formatMarkupRate(value: unknown): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '0.00%'
  return `${(numeric * 100).toFixed(2)}%`
}
function validatedOrderNo(invalidTitle: string): string {
  const currentOrder = order.value
  if (!currentOrder) return ''
  if (!isValidBackendOrderNo(currentOrder.orderNo)) {
    uni.showToast({ title: invalidTitle, icon: 'none' })
    return ''
  }
  return currentOrder.orderNo
}
async function loadDetail(): Promise<void> {
  if (!isValidBackendOrderNo(orderNo.value)) {
    errorText.value = '缺少有效订单号，请从订单列表进入'
    order.value = null
    return
  }

  const safeOrderNo = orderNo.value
  loading.value = true
  errorText.value = ''
  try {
    const detail = await getOrderDetail(safeOrderNo)
    assertBackendOrderDetail(detail, safeOrderNo)
    order.value = detail
  } catch (error) {
    console.warn('order detail load failed', { orderNo: safeOrderNo, error })
    errorText.value = '订单详情读取失败，请从订单列表重新进入'
    order.value = null
  } finally {
    loading.value = false
  }
}
function handleAction(action: string): void {
  const currentOrder = order.value
  if (!currentOrder) return
  const safeOrderNo = validatedOrderNo('订单编号无效，已阻止敏感订单操作')
  if (!safeOrderNo) return
  const encodedOrderNo = encodeURIComponent(safeOrderNo)

  if (action === '去付款') {
    if (!isValidOrderAmount(currentOrder.amount)) return uni.showToast({ title: '订单金额异常，未进入收银台', icon: 'none' })
    if (!isValidBackendProductId(currentOrder.productId)) return uni.showToast({ title: '商品编号异常，未进入收银台', icon: 'none' })
    navigateWithFailure(
      `/pages/payment/checkout/index?orderNo=${encodedOrderNo}&amount=${encodeURIComponent(String(currentOrder.amount))}&productId=${currentOrder.productId}`,
      (error: unknown) => {
        console.warn('order detail checkout navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法进入收银台，请稍后重试', icon: 'none' })
      }
    )
  }
  else if (action === '查看物流') {
    navigateWithFailure(
      `/pages/order/logistics/index?orderNo=${encodedOrderNo}`,
      (error: unknown) => {
        console.warn('order detail logistics navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法打开物流详情', icon: 'none' })
      }
    )
  }
  else if (action === '去发货') {
    navigateWithFailure(
      `/pages/order/ship/index?orderNo=${encodedOrderNo}`,
      (error: unknown) => {
        console.warn('order detail ship navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法进入发货页', icon: 'none' })
      }
    )
  }
  else if (action === '申请退款' || action === '申请售后') {
    if (!isValidOrderAmount(currentOrder.amount)) return uni.showToast({ title: '订单金额异常，未进入售后申请', icon: 'none' })
    navigateWithFailure(
      `/pages/after-sales/apply/index?orderNo=${encodedOrderNo}&amount=${encodeURIComponent(String(currentOrder.amount))}`,
      (error: unknown) => {
        console.warn('order detail after-sales apply navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法进入售后申请', icon: 'none' })
      }
    )
  }
  else if (action === '查看售后') {
    openAfterSalesDetail()
  }
  else if (action === '确认收货') void confirmOrderReceipt()
  else if (action === '提醒发货') showUnavailableAction(action)
  else if (action === '评价') {
    navigateWithFailure(
      `/pages/review/submit/index?orderNo=${encodedOrderNo}`,
      (error: unknown) => {
        console.warn('order detail review navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法打开评价页', icon: 'none' })
      }
    )
  }
  else if (action === '联系卖家' || action === '联系买家' || action === '联系客服') openOrderContact(action)
  else showUnavailableAction(action)
}
function showUnavailableAction(action: string): void {
  uni.showToast({ title: `${action}暂不可用，请稍后重试`, icon: 'none' })
}
function reportOrder(): void {
  const currentOrder = order.value
  if (!currentOrder || !isValidBackendOrderNo(currentOrder.orderNo)) {
    uni.showToast({ title: '缺少有效订单号，不能提交举报', icon: 'none' })
    return
  }
  const safeOrderNo = currentOrder.orderNo
  const route = {
    url: `/pages/report/submit/index?targetType=ORDER&targetId=${encodeURIComponent(safeOrderNo)}`,
    fail(error: unknown) {
      console.warn('order detail report navigation failed', { orderNo: safeOrderNo, error })
      uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('order detail report navigation failed', { orderNo: safeOrderNo, error })
    uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
  }
}
function openAfterSalesDetail(): void {
  const currentOrder = order.value
  if (!currentOrder) return
  if (!isValidBackendOrderNo(currentOrder.orderNo)) return uni.showToast({ title: '订单编号无效，未打开售后详情', icon: 'none' })
  if (!currentOrder.afterSalesNo) return uni.showToast({ title: '暂无售后单号', icon: 'none' })
  if (!isValidAfterSalesNo(currentOrder.afterSalesNo)) {
    console.warn('order detail invalid after-sales trace target', { orderNo: currentOrder.orderNo, afterSalesNo: currentOrder.afterSalesNo })
    return uni.showToast({ title: '售后单号异常，已阻止跳转', icon: 'none' })
  }
  const safeOrderNo = currentOrder.orderNo
  const safeAfterSalesNo = currentOrder.afterSalesNo
  navigateWithFailure(
    `/pages/after-sales/detail/index?afterSalesNo=${encodeURIComponent(safeAfterSalesNo)}&orderNo=${encodeURIComponent(safeOrderNo)}`,
    (error: unknown) => {
      console.warn('order detail after-sales detail navigation failed', { orderNo: safeOrderNo, afterSalesNo: safeAfterSalesNo, error })
      uni.showToast({ title: '暂时无法打开售后详情', icon: 'none' })
    }
  )
}
function openOrderContact(action: OrderContactAction): void {
  const currentOrder = order.value
  if (!currentOrder) return
  const target = resolveOrderContactTarget(currentOrder, action)
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  navigateWithFailure(
    `/pages/chat/conversation/index?receiverId=${target.receiverId}`,
    (error: unknown) => {
      console.warn('order detail contact navigation failed', { orderNo: currentOrder.orderNo, receiverId: target.receiverId, error })
      uni.showToast({ title: '暂时无法打开聊天', icon: 'none' })
    }
  )
}
async function confirmOrderReceipt(): Promise<void> {
  if (!order.value || confirming.value) return
  const safeOrderNo = validatedOrderNo('订单编号无效，未确认收货')
  if (!safeOrderNo) return

  const modalOptions = {
    title: '确认收货',
    content: '确认收到宝贝且无争议后，确认收货将调用后端接口完成状态变更。确认后不可直接撤回。',
    fail(error: unknown) {
      console.warn('order detail confirm receipt modal failed', { orderNo: safeOrderNo, error })
      uni.showToast({ title: '暂时无法确认收货', icon: 'none' })
    },
    success: async (res: { confirm?: boolean }) => {
      if (!res.confirm || !order.value) return
      confirming.value = true
      try {
        const detail = await confirmReceipt(safeOrderNo)
        assertBackendOrderDetail(detail, safeOrderNo)
        order.value = detail
        uni.showToast({ title: '已确认收货', icon: 'success' })
      } catch (error) {
        console.warn('order detail confirm receipt failed', { orderNo: safeOrderNo, error })
        const failureModal = {
          title: '确认失败',
          content: '确认收货未完成，请刷新订单状态后重试。',
          showCancel: false,
          fail(modalError: unknown) {
            console.warn('order detail confirm receipt failure modal failed', { orderNo: safeOrderNo, error: modalError })
            uni.showToast({ title: '确认收货未完成', icon: 'none' })
          }
        }
        uni.showModal(failureModal)
      } finally {
        confirming.value = false
      }
    }
  }
  uni.showModal(modalOptions)
}
function openProduct(): void {
  const currentOrder = order.value
  if (!currentOrder) return
  if (!isValidBackendProductId(currentOrder.productId)) {
    uni.showToast({ title: '商品编号无效，未打开商品详情', icon: 'none' })
    return
  }

  navigateWithFailure(
    `/pages/product/detail/index?productId=${currentOrder.productId}`,
    (error: unknown) => {
      console.warn('order detail product navigation failed', { orderNo: currentOrder.orderNo, productId: currentOrder.productId, error })
      uni.showToast({ title: '暂时无法打开商品详情', icon: 'none' })
    }
  )
}
onMounted(() => { readQuery(); void loadDetail() })
</script>

<style scoped lang="scss" src="./style.scss"></style>
