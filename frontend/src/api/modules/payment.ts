import { isDevRuntimeEnabled, post } from '../http'

export { isDevRuntimeEnabled }

export type RechargeStatus = 'PENDING' | 'PAID'

export type MoneyAmount = number | string

export interface CreateRechargeRequest {
  amount: MoneyAmount
  channel: string
}

export interface RechargeResponse {
  userId: number
  rechargeNo: string
  amount: MoneyAmount
  channel: string
  status: RechargeStatus
  ledgerNo?: string | null
  balanceBefore?: MoneyAmount | null
  balanceAfter?: MoneyAmount | null
  createdAt: string
  idempotentReplay: boolean
}

export interface CreatePaymentIntentRequest {
  bizType: 'ORDER' | 'RECHARGE'
  bizNo: string
  channel: 'WECHAT' | 'ALIPAY'
  clientType?: 'H5'
}

export interface PaymentIntentResponse {
  paymentNo: string
  bizType: string
  bizNo: string
  amount: MoneyAmount
  channel: 'WECHAT' | 'ALIPAY'
  status: string
  actionType: 'REDIRECT' | 'FORM'
  payUrl?: string | null
  formHtml?: string | null
  message?: string | null
}

export function createRecharge(data: CreateRechargeRequest) {
  return post<RechargeResponse>('/api/payments/recharge', data)
}

export function createPaymentIntent(data: CreatePaymentIntentRequest) {
  return post<PaymentIntentResponse>('/api/payments/intents', data)
}

export function simulateRechargeSuccess(rechargeNo: string) {
  if (!isDevRuntimeEnabled()) {
    return Promise.reject(new Error('开发模拟充值已在当前构建中关闭'))
  }
  return post<RechargeResponse>('/api/payments/recharge/simulate-success', { rechargeNo }, { 'X-Dev-Mode': 'enabled' })
}
