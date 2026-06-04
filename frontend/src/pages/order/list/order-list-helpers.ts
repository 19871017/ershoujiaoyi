import type { OrderListItemResponse, OrderListStatus, OrderRole } from '../../../api/modules/order'

export type StatusTab = 'ALL' | OrderListStatus

export const roles: Array<{ label: string; value: OrderRole }> = [
  { label: '我买到的', value: 'buyer' },
  { label: '我卖出的', value: 'seller' }
]

export const statusTabs: Array<{ label: string; value: StatusTab }> = [
  { label: '全部', value: 'ALL' },
  { label: '待付款', value: 'PENDING_PAY' },
  { label: '待发货', value: 'PAID' },
  { label: '待收货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '售后', value: 'REFUNDING' }
]

export const flowSteps: Array<{ label: string; value: OrderListStatus }> = [
  { label: '付款', value: 'PAID' },
  { label: '发货', value: 'SHIPPED' },
  { label: '收货', value: 'COMPLETED' }
]

export const backendOrderNoPattern = /^OD-[0-9]{1,10}$/

export function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}

export function isValidOrderAmount(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}

export function isValidBackendProductId(value: unknown): boolean {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function isValidAfterSalesNo(value: string): boolean {
  return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value || '').trim()
  } catch (error) {
    console.warn('order list route decode failed', { fieldName, rawLength: value.length, error })
    return ''
  }
}

export function isOrderRole(value: string): value is OrderRole {
  return value === 'buyer' || value === 'seller'
}

export function isStatusTab(value: string): value is StatusTab {
  return statusTabs.some((item) => item.value === value)
}

export function assertBackendOrderListItem(item: OrderListItemResponse): void {
  if (!isValidBackendOrderNo(item.orderNo)) throw new Error('order list invalid backend orderNo')
  if (!isValidOrderAmount(item.amount)) throw new Error('order list invalid order amount')
  if (!isValidBackendProductId(item.productId)) throw new Error('order list invalid productId')
  if (item.afterSalesNo && !isValidAfterSalesNo(item.afterSalesNo)) throw new Error('order list invalid afterSalesNo')
}

export function displayStatus(item: OrderListItemResponse): OrderListStatus {
  return item.afterSalesNo ? 'REFUNDING' : item.status
}

export function statusLabel(value: OrderListStatus): string {
  const labels: Record<OrderListStatus, string> = { PENDING_PAY: '待付款', PAID: '待发货', SHIPPED: '待收货', COMPLETED: '已完成', REFUNDING: '售后中' }
  return labels[value]
}

export function stepIndex(value: OrderListStatus): number {
  const map: Record<OrderListStatus, number> = { PENDING_PAY: 0, PAID: 1, SHIPPED: 2, COMPLETED: 3, REFUNDING: 1 }
  return map[value]
}

export function actionsFor(item: OrderListItemResponse): string[] {
  const current = displayStatus(item)
  if (current === 'PENDING_PAY') return ['去付款', '取消订单']
  if (current === 'PAID' && item.role === 'seller') return ['去发货', '联系买家']
  if (current === 'PAID') return ['提醒发货', '联系卖家', '申请售后']
  if (current === 'SHIPPED') return ['确认收货', '查看物流', '申请售后']
  if (current === 'COMPLETED') return ['评价', '申请售后']
  return ['查看售后', '联系客服']
}

export function coverIcon(title: string): string {
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🎀'
  if (title.includes('包')) return '👜'
  if (title.includes('衣') || title.includes('裙')) return '👗'
  return '🛍️'
}
