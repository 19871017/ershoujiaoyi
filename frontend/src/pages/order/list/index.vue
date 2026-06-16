<template>
  <view class="page-shell order-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 我的订单</view>
        <view class="page-title">订单列表</view>
        <view class="page-desc">买到和卖出的宝贝都在这里，重点跟踪付款、发货、收货和售后。</view>
      </view>
      <view class="hero-icon">🧾</view>
    </view>

    <view class="role-switch ds-card">
      <view v-for="item in roles" :key="item.value" class="role-item tapable" :class="{ active: role === item.value }" @click="switchRole(item.value)">
        {{ item.label }}
      </view>
    </view>

    <view class="status-scroll">
      <view v-for="item in statusTabs" :key="item.value" class="status-chip tapable" :class="{ active: status === item.value }" @click="switchStatus(item.value)">
        {{ item.label }}<text v-if="countByStatus(item.value)"> · {{ countByStatus(item.value) }}</text>
      </view>
    </view>

    <view v-if="showAfterSalesTrace" class="after-sales-trace ds-card">
      <view class="trace-main">
        <view class="trace-title">售后追踪</view>
        <view class="trace-desc">当前筛选下有 {{ afterSalesTraceOrders.length }} 个售后单，进度以平台售后详情为准。</view>
      </view>
      <view class="trace-action tapable" @click="openFirstAfterSalesTrace">查看最近</view>
    </view>

    <view v-if="loading" class="empty-card ds-card">
      <view class="empty-icon">⌛</view>
      <view class="empty-title">正在读取订单</view>
      <view class="empty-desc">平台正在同步买卖订单和售后状态。</view>
    </view>

    <view v-else-if="errorText" class="empty-card ds-card danger">
      <view class="empty-icon">!</view>
      <view class="empty-title">订单读取失败</view>
      <view class="empty-desc">{{ errorText }}</view>
    </view>

    <view v-else-if="filteredOrders.length" class="order-list">
      <view v-for="item in filteredOrders" :key="item.orderNo" class="order-card ds-card">
        <view class="order-head">
          <view>
            <view class="order-no">{{ item.orderNo }}</view>
            <view class="order-time">{{ item.createdAt }}</view>
          </view>
          <view class="status-badge" :class="displayStatus(item)">{{ statusLabel(displayStatus(item)) }}</view>
        </view>

        <view class="goods-row tapable" @click="openOrder(item.orderNo)">
          <view class="goods-cover">{{ coverIcon(item.productTitle) }}</view>
          <view class="goods-main">
            <view class="goods-title">{{ item.productTitle }}</view>
            <view class="goods-desc">{{ item.role === 'buyer' ? '卖家' : '买家' }}：{{ item.counterpartyName }} · {{ item.tradeRuleSnapshot }}</view>
            <view class="goods-price-label">{{ displayOrderAmountLabel(item) }}</view>
            <view class="goods-price">¥{{ displayOrderAmountValue(item) }}</view>
            <view v-if="item.role === 'seller'" class="settlement-line">
              <text>卖家结算 ¥{{ sellerSettlementText(item) }}</text>
              <text>买家支付 ¥{{ formatMoneyAmount(item.amount) }}</text>
              <text>平台加价 ¥{{ platformMarkupText(item) }}</text>
            </view>
          </view>
        </view>

        <view class="flow-row">
          <view v-for="step in flowSteps" :key="step.value" class="flow-step" :class="{ done: stepIndex(step.value) <= stepIndex(displayStatus(item)) }">
            <view class="dot"></view>
            <view>{{ step.label }}</view>
          </view>
        </view>

        <view class="actions">
          <button v-for="action in actionsFor(item)" :key="action" class="action-btn" :class="{ primary: action === '去付款' || action === '确认收货' }" @click="handleAction(item, action)">{{ action }}</button>
        </view>
      </view>
    </view>

    <view v-else class="empty-card ds-card">
      <view class="empty-icon">🧾</view>
      <view class="empty-title">暂无相关订单</view>
      <view class="empty-desc">换个状态看看，或者先去宝贝页挑一件喜欢的。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { confirmReceipt, listOrders, type OrderListItemResponse, type OrderRole } from '../../../api/modules/order'
import { resolveOrderContactTarget, type OrderContactAction } from '../../../api/modules/order-contact'
import {
  actionsFor,
  assertBackendOrderListItem,
  coverIcon,
  decodeRouteValue,
  displayStatus,
  flowSteps,
  isValidAfterSalesNo,
  isValidBackendOrderNo,
  isValidBackendProductId,
  isValidOrderAmount,
  isOrderRole,
  isStatusTab,
  roles,
  statusLabel,
  statusTabs,
  stepIndex,
  type StatusTab
} from './order-list-helpers'

const orders = ref<OrderListItemResponse[]>([])
const role = ref<OrderRole>('buyer')
const status = ref<StatusTab>('ALL')
const loading = ref(false)
const confirmingOrderNo = ref('')
const errorText = ref('')
const filteredOrders = computed(() => orders.value.filter((item) => status.value === 'ALL' || displayStatus(item) === status.value))
const afterSalesTraceOrders = computed(() => filteredOrders.value.filter((item) => displayStatus(item) === 'REFUNDING' && !!item.afterSalesNo))
const showAfterSalesTrace = computed(() => !loading.value && !errorText.value && status.value === 'REFUNDING' && afterSalesTraceOrders.value.length > 0)
const statusCounts = computed<Record<StatusTab, number>>(() => {
  const counts = Object.fromEntries(statusTabs.map((item) => [item.value, 0])) as Record<StatusTab, number>
  for (const item of orders.value) {
    counts.ALL += 1
    counts[displayStatus(item)] += 1
  }
  return counts
})
function navigateWithFailure(url: string, fail: (error: unknown) => void): void {
  uni.navigateTo({ url, fail } as { url: string; fail(error: unknown): void })
}
function countByStatus(value: StatusTab): number {
  return statusCounts.value[value]
}
function formatMoneyAmount(value: unknown): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return '0.00'
  return numeric.toFixed(2)
}
function displayOrderAmountLabel(item: OrderListItemResponse): string {
  return item.role === 'seller' ? '可结算' : '应付'
}
function displayOrderAmountValue(item: OrderListItemResponse): string {
  return formatMoneyAmount(item.role === 'seller' ? item.sellerAmount ?? item.amount : item.amount)
}
function sellerSettlementText(item: OrderListItemResponse): string {
  return formatMoneyAmount(item.sellerAmount ?? item.amount)
}
function platformMarkupText(item: OrderListItemResponse): string {
  return formatMoneyAmount(item.platformMarkupAmount ?? 0)
}
function readRouteFilters(): void {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as { options?: Record<string, string> } | undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeRole = decodeRouteValue('role', current?.options?.role || hashParams?.get('role') || '')
  const routeStatus = decodeRouteValue('status', current?.options?.status || hashParams?.get('status') || '').toUpperCase()

  if (routeRole && !isOrderRole(routeRole)) {
    console.warn('order list invalid route role', { role: routeRole })
  } else if (isOrderRole(routeRole)) {
    role.value = routeRole
  }

  if (routeStatus && !isStatusTab(routeStatus)) {
    console.warn('order list invalid route status', { status: routeStatus })
  } else if (isStatusTab(routeStatus)) {
    status.value = routeStatus
  }
}
async function loadOrders(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const list = await listOrders(role.value, 'ALL')
    list.forEach(assertBackendOrderListItem)
    orders.value = list
  } catch (error) {
    console.warn('order list load failed', { role: role.value, error })
    orders.value = []
    errorText.value = '订单列表读取失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
function switchRole(value: OrderRole): void {
  role.value = value
  void loadOrders()
}
function switchStatus(value: StatusTab): void { status.value = value }
function handleAction(item: OrderListItemResponse, action: string): void {
  if (!isValidBackendOrderNo(item.orderNo)) {
    uni.showToast({ title: '订单编号无效，已阻止敏感订单操作', icon: 'none' })
    return
  }
  const safeOrderNo = item.orderNo
  const encodedOrderNo = encodeURIComponent(safeOrderNo)

  if (action === '去付款') {
    if (!isValidOrderAmount(item.amount)) return uni.showToast({ title: '订单金额异常，未进入收银台', icon: 'none' })
    if (!isValidBackendProductId(item.productId)) return uni.showToast({ title: '商品编号异常，未进入收银台', icon: 'none' })
    navigateWithFailure(
      `/pages/payment/checkout/index?orderNo=${encodedOrderNo}&amount=${encodeURIComponent(String(item.amount))}&productId=${item.productId}`,
      (error: unknown) => {
        console.warn('order list checkout navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法进入收银台，请稍后重试', icon: 'none' })
      }
    )
  }
  else if (action === '去发货') {
    navigateWithFailure(
      `/pages/order/ship/index?orderNo=${encodedOrderNo}`,
      (error: unknown) => {
        console.warn('order list ship navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法进入发货页', icon: 'none' })
      }
    )
  }
  else if (action === '查看物流') {
    navigateWithFailure(
      `/pages/order/logistics/index?orderNo=${encodedOrderNo}`,
      (error: unknown) => {
        console.warn('order list logistics navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法打开物流详情', icon: 'none' })
      }
    )
  }
  else if (action === '申请售后') {
    if (!isValidOrderAmount(item.amount)) return uni.showToast({ title: '订单金额异常，未进入售后申请', icon: 'none' })
    navigateWithFailure(
      `/pages/after-sales/apply/index?orderNo=${encodedOrderNo}&amount=${encodeURIComponent(String(item.amount))}`,
      (error: unknown) => {
        console.warn('order list after-sales apply navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法进入售后申请', icon: 'none' })
      }
    )
  }
  else if (action === '确认收货') void confirmFromList(item)
  else if (action === '查看售后') {
    openAfterSalesDetail(item)
  }
  else if (action === '评价') {
    navigateWithFailure(
      `/pages/review/submit/index?orderNo=${encodedOrderNo}`,
      (error: unknown) => {
        console.warn('order list review navigation failed', { orderNo: safeOrderNo, error })
        uni.showToast({ title: '暂时无法打开评价页', icon: 'none' })
      }
    )
  }
  else if (action === '联系客服' || action.includes('联系')) openOrderContact(item, action as OrderContactAction)
  else showUnavailableAction(action)
}
function showUnavailableAction(action: string): void {
  uni.showToast({ title: `${action}暂不可用，请稍后重试`, icon: 'none' })
}
function openFirstAfterSalesTrace(): void {
  const target = afterSalesTraceOrders.value[0]
  if (!target) return uni.showToast({ title: '暂无可追踪售后', icon: 'none' })
  openAfterSalesDetail(target)
}
function openAfterSalesDetail(item: OrderListItemResponse): void {
  if (!isValidBackendOrderNo(item.orderNo)) return uni.showToast({ title: '订单编号无效，未打开售后详情', icon: 'none' })
  if (!item.afterSalesNo) return uni.showToast({ title: '暂无售后单号', icon: 'none' })
  if (!isValidAfterSalesNo(item.afterSalesNo)) {
    console.warn('order list invalid after-sales trace target', { orderNo: item.orderNo, afterSalesNo: item.afterSalesNo })
    return uni.showToast({ title: '售后单号异常，已阻止跳转', icon: 'none' })
  }
  const safeOrderNo = item.orderNo
  const safeAfterSalesNo = item.afterSalesNo
  navigateWithFailure(
    `/pages/after-sales/detail/index?afterSalesNo=${encodeURIComponent(safeAfterSalesNo)}&orderNo=${encodeURIComponent(safeOrderNo)}`,
    (error: unknown) => {
      console.warn('order list after-sales detail navigation failed', { orderNo: safeOrderNo, afterSalesNo: safeAfterSalesNo, error })
      uni.showToast({ title: '暂时无法打开售后详情', icon: 'none' })
    }
  )
}
function openOrderContact(item: OrderListItemResponse, action: OrderContactAction): void {
  const target = resolveOrderContactTarget(item, action)
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  navigateWithFailure(
    `/pages/chat/conversation/index?receiverId=${target.receiverId}`,
    (error: unknown) => {
      console.warn('order list contact navigation failed', { orderNo: item.orderNo, receiverId: target.receiverId, error })
      uni.showToast({ title: '暂时无法打开聊天', icon: 'none' })
    }
  )
}
async function confirmFromList(item: OrderListItemResponse): Promise<void> {
  if (confirmingOrderNo.value) return
  if (!isValidBackendOrderNo(item.orderNo)) return uni.showToast({ title: '订单编号无效，未确认收货', icon: 'none' })
  const safeOrderNo = item.orderNo
  const modalOptions = {
    title: '确认收货',
    content: '确认收到宝贝且无争议后，结算与售后状态以平台订单、支付和物流记录为准。',
    fail(error: unknown) {
      console.warn('order list confirm receipt modal failed', { orderNo: safeOrderNo, error })
      uni.showToast({ title: '暂时无法确认收货', icon: 'none' })
    },
    success: async (res: { confirm?: boolean }) => {
      if (!res.confirm) return
      confirmingOrderNo.value = safeOrderNo
      try {
        const detail = await confirmReceipt(safeOrderNo)
        if (!isValidBackendOrderNo(detail.orderNo)) throw new Error('order list invalid confirmed orderNo')
        if (detail.orderNo !== safeOrderNo) throw new Error('order list confirmed orderNo mismatch')
        uni.showToast({ title: '已确认收货', icon: 'success' })
        await loadOrders()
      } catch (error) {
        console.warn('order list confirm receipt failed', { orderNo: safeOrderNo, error })
        const failureModal = {
          title: '确认失败',
          content: '确认收货未完成，请刷新订单状态后重试。',
          showCancel: false,
          fail(modalError: unknown) {
            console.warn('order list confirm receipt failure modal failed', { orderNo: safeOrderNo, error: modalError })
            uni.showToast({ title: '确认收货未完成', icon: 'none' })
          }
        }
        uni.showModal(failureModal)
      } finally {
        confirmingOrderNo.value = ''
      }
    }
  }
  uni.showModal(modalOptions)
}
function openOrder(orderNo: string): void {
  if (!isValidBackendOrderNo(orderNo)) {
    uni.showToast({ title: '订单编号无效，未打开订单详情', icon: 'none' })
    return
  }

  navigateWithFailure(
    `/pages/order/detail/index?orderNo=${encodeURIComponent(orderNo)}`,
    (error: unknown) => {
      console.warn('order list detail navigation failed', { orderNo, error })
      uni.showToast({ title: '暂时无法打开订单详情', icon: 'none' })
    }
  )
}
onMounted(() => {
  readRouteFilters()
  void loadOrders()
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
