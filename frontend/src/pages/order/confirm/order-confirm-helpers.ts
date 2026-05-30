import type { CreateOrderResponse } from '../../../api/modules/order'
import type { ProductDetailResponse } from '../../../api/modules/product'
import type { UserAddressResponse } from '../../../api/modules/address'

export type DeliveryType = 'EXPRESS' | 'LOCAL_MEET'
export interface ShippingAddress { id?: number | string; name: string; mobile: string; full: string }

export const launchReadinessMarkers = [
  '平台订单创建后再进入支付确认',
  '支付、售后和聊天记录以服务端订单状态为准'
]
void launchReadinessMarkers

export const productImageStoragePrefix = '/uploads/product-image/'
export const backendProductIdPattern = /^[1-9][0-9]{0,9}$/
export const backendOrderNoPattern = /^OD-[0-9]{1,10}$/

export const deliveryTypes = [
  { value: 'EXPRESS' as const, label: '快递邮寄', desc: '卖家发货后可看物流' },
  { value: 'LOCAL_MEET' as const, label: '线下交付', desc: '交付状态以平台订单记录为准' }
]

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('order confirm route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function isValidBackendProductId(value: unknown): boolean {
  return typeof value === 'string' && backendProductIdPattern.test(value)
}

export function isValidBackendOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}

export function validatedProductImageUrl(url: string): string {
  if (!url) return ''
  if (url.startsWith('local://') || url.startsWith('blob:') || url.includes('placeholder') || !url.startsWith(productImageStoragePrefix)) {
    console.warn('order confirm rejected product image url', { expectedPrefix: productImageStoragePrefix, url })
    return ''
  }
  return url
}

export function isValidOrderAmount(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0
}

export function assertBackendOrderForCheckout(order: CreateOrderResponse, expectedProductId: number): void {
  if (!isValidBackendOrderNo(order.orderNo)) throw new Error('订单创建异常，服务端未返回有效订单号')
  if (order.productId !== expectedProductId || order.goodsId !== expectedProductId) throw new Error('订单创建异常，商品编号不一致')
  if (!isValidOrderAmount(order.productPrice)) throw new Error('订单创建异常，服务端未返回有效金额')
  if (order.acceptedTradeRule !== true || order.status !== 'PENDING_PAY') throw new Error('订单创建异常，请重新确认订单状态')
}

export function toShippingAddress(item: UserAddressResponse): ShippingAddress {
  return { id: item.addressId, name: item.name, mobile: item.mobile, full: `${item.provinceCity} ${item.detail}` }
}

export function productStatusTextFor(product: ProductDetailResponse | null): string {
  if (!product) return ''
  if (product.status === 'SOLD') return '已售出'
  if (product.visible && String(product.auditState).toUpperCase() === 'APPROVED') return '在售'
  return '待确认'
}

export function coverIcon(title: string): string {
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🎀'
  if (title.includes('包')) return '👜'
  if (title.includes('衣') || title.includes('裙')) return '👗'
  return '🛍️'
}
