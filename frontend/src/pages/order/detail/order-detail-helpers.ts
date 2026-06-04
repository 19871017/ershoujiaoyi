import type { OrderDetailResponse, OrderListStatus, OrderRole } from '../../../api/modules/order'

export const launchReadinessMarkers = [
  '订单、支付、售后和聊天记录以服务端状态为准',
  '确认收货将调用后端接口完成状态变更'
]

export const backendOrderNoPattern = /^OD-[0-9]{1,10}$/

export const states: Record<OrderListStatus, { icon: string; label: string; desc: string; index: number }> = {
  PENDING_PAY: { icon: '💳', label: '等待付款', desc: '请确认宝贝信息后完成支付。', index: 0 },
  PAID: { icon: '📦', label: '等待卖家发货', desc: '订单已付款，卖家需要尽快发货；支付状态以平台记录为准。', index: 1 },
  SHIPPED: { icon: '🚚', label: '宝贝运输中', desc: '收到宝贝并确认无误后再确认收货。', index: 2 },
  COMPLETED: { icon: '🌸', label: '交易完成', desc: '订单完成状态以平台订单、支付和售后记录为准，可以评价这次交易。', index: 3 },
  REFUNDING: { icon: '🛟', label: '售后处理中', desc: '售后处理以平台订单、支付、物流、聊天记录和已提交票据为准。', index: 1 }
}

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('order detail route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}

export function isValidAfterSalesNo(value: string): boolean {
  return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}

export function isKnownAfterSalesStatus(value: unknown): boolean {
  return value === 'PENDING_REVIEW' || value === 'APPROVED' || value === 'REJECTED' || value === 'CANCELLED'
}

export function isValidOrderAmount(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}

export function isValidBackendProductId(value: unknown): boolean {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function isOrderRole(value: unknown): value is OrderRole {
  return value === 'buyer' || value === 'seller'
}

export function assertBackendOrderDetail(detail: OrderDetailResponse, expectedOrderNo: string): void {
  if (!isValidBackendOrderNo(detail.orderNo)) throw new Error('order detail invalid backend orderNo')
  if (detail.orderNo !== expectedOrderNo) throw new Error('order detail orderNo mismatch')
  if (!isValidOrderAmount(detail.amount)) throw new Error('order detail invalid order amount')
  if (!isOrderRole(detail.role)) throw new Error('order detail invalid role')
  if (detail.afterSalesNo && !isValidAfterSalesNo(detail.afterSalesNo)) throw new Error('order detail invalid afterSalesNo')
  if (detail.afterSalesStatus && !isKnownAfterSalesStatus(detail.afterSalesStatus)) throw new Error('order detail invalid afterSalesStatus')
}

export function actionsForOrderDetail(order: OrderDetailResponse, displayStatus: OrderListStatus): string[] {
  if (displayStatus === 'REFUNDING') return ['查看售后', '联系客服']
  if (displayStatus === 'PENDING_PAY') return order.role === 'buyer' ? ['去付款', '联系卖家'] : ['联系买家']
  if (displayStatus === 'PAID') return order.role === 'seller' ? ['去发货', '联系买家'] : ['提醒发货', '联系卖家', '申请售后']
  if (displayStatus === 'SHIPPED') return order.role === 'buyer' ? ['确认收货', '查看物流', '申请售后'] : ['查看物流', '联系买家']
  if (displayStatus === 'COMPLETED') return order.role === 'buyer' ? ['评价', '申请售后', '联系卖家'] : ['联系买家']
  return ['联系客服']
}

export function coverIcon(title: string): string {
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🎀'
  if (title.includes('包')) return '👜'
  if (title.includes('衣') || title.includes('裙')) return '👗'
  return '🛍️'
}
