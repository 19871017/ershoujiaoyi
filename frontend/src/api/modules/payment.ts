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

export function createRecharge(data: CreateRechargeRequest) {
  return post<RechargeResponse>('/api/payments/recharge', data)
}

export function simulateRechargeSuccess(rechargeNo: string) {
  if (!isDevRuntimeEnabled()) {
    return Promise.reject(new Error('开发模拟充值已在当前构建中关闭'))
  }
  return post<RechargeResponse>('/api/payments/recharge/simulate-success', { rechargeNo }, { 'X-Dev-Mode': 'enabled' })
}
