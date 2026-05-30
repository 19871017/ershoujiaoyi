import type { ShipOrderResponse, ShippingType } from '../../../api/modules/order'

export const backendOrderNoPattern = /^OD-[0-9]{1,10}$/

export const shipTypes: Array<{ label: string; value: ShippingType }> = [
  { label: '快递邮寄', value: 'EXPRESS' },
  { label: '线下交付', value: 'MEETUP' }
]

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('order ship route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}

export function assertBackendShipResponse(response: ShipOrderResponse, expectedOrderNo: string): void {
  if (!isValidBackendOrderNo(response.orderNo)) throw new Error('order ship invalid backend orderNo')
  if (response.orderNo !== expectedOrderNo) throw new Error('order ship orderNo mismatch')
  if (response.status !== 'SHIPPED') throw new Error('order ship invalid backend status')
  if (!response.shippedAt) throw new Error('order ship missing shippedAt')
}
