import { get, post } from '../http'
import type { MoneyAmount } from './payment'

export type OrderCreateStatus = 'PENDING_PAY'
export type OrderPayStatus = 'PENDING_PAY' | 'PAID'
export type OrderListStatus = 'PENDING_PAY' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'REFUNDING'
export type OrderRole = 'buyer' | 'seller'
export type ShippingType = 'EXPRESS' | 'MEETUP'

export interface CreateOrderRequest {
  goodsId: number
  acceptedTradeRule: boolean
}

export interface CreateOrderResponse {
  orderNo: string
  buyerId: number
  goodsId: number
  productId: number
  productNo: string
  productTitle: string
  productPrice: MoneyAmount
  tradeRuleSnapshot: string
  status: OrderCreateStatus
  acceptedTradeRule: boolean
  createdAt: string
}

export interface PayOrderResponse {
  orderNo: string
  buyerId: number
  goodsId: number
  productId: number
  productNo: string
  productTitle: string
  amount: MoneyAmount
  status: OrderPayStatus
  ledgerNo?: string | null
  balanceType?: string | null
  balanceBefore?: MoneyAmount | null
  balanceAfter?: MoneyAmount | null
  paidAt?: string | null
  idempotentReplay: boolean
}

export interface OrderListItemResponse {
  orderNo: string
  buyerId: number
  sellerId: number
  productId: number
  goodsId: number
  productNo: string
  productTitle: string
  amount: MoneyAmount
  tradeRuleSnapshot: string
  status: Exclude<OrderListStatus, 'REFUNDING'>
  role: OrderRole
  counterpartyName: string
  afterSalesNo?: string | null
  afterSalesStatus?: string | null
  createdAt: string
}

export interface OrderDetailResponse {
  orderNo: string
  buyerId: number
  sellerId: number
  productId: number
  goodsId: number
  productNo: string
  productTitle: string
  amount: MoneyAmount
  tradeRuleSnapshot: string
  status: Exclude<OrderListStatus, 'REFUNDING'>
  counterpartyName: string
  afterSalesNo?: string | null
  afterSalesStatus?: string | null
  shippingType?: ShippingType | null
  shippingCompany?: string | null
  trackingNo?: string | null
  shippingRemark?: string | null
  createdAt: string
  paidAt?: string | null
  shippedAt?: string | null
  completedAt?: string | null
}

export interface ShipOrderRequest {
  shippingType: ShippingType
  shippingCompany?: string
  trackingNo?: string
  remark?: string
}

export interface ShipOrderResponse {
  orderNo: string
  status: 'SHIPPED'
  shippingType: ShippingType
  shippingCompany?: string | null
  trackingNo?: string | null
  remark?: string | null
  shippedAt: string
}

export interface SubmitOrderReviewRequest {
  descriptionScore: number
  serviceScore: number
  shippingScore: number
  content: string
}

export interface OrderReviewResponse {
  reviewNo: string
  orderNo: string
  reviewerId: number
  revieweeId: number
  descriptionScore: number
  serviceScore: number
  shippingScore: number
  content: string
  createdAt: string
}

export function createOrder(data: CreateOrderRequest) {
  return post<CreateOrderResponse>('/api/orders', data)
}

export function payOrder(orderNo: string) {
  return post<PayOrderResponse>(`/api/orders/${encodeURIComponent(orderNo)}/pay`, {})
}

export function listOrders(role: OrderRole, status: 'ALL' | OrderListStatus = 'ALL') {
  return get<OrderListItemResponse[]>('/api/orders', { role, status })
}

export function getOrderDetail(orderNo: string) {
  return get<OrderDetailResponse>(`/api/orders/${encodeURIComponent(orderNo)}`)
}

export function shipOrder(orderNo: string, data: ShipOrderRequest) {
  return post<ShipOrderResponse>(`/api/orders/${encodeURIComponent(orderNo)}/ship`, data)
}

export function confirmReceipt(orderNo: string) {
  return post<OrderDetailResponse>(`/api/orders/${encodeURIComponent(orderNo)}/confirm-receipt`, {})
}

export function submitOrderReview(orderNo: string, data: SubmitOrderReviewRequest) {
  return post<OrderReviewResponse>(`/api/orders/${encodeURIComponent(orderNo)}/review`, data)
}
