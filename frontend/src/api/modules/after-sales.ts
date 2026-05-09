import { get, post } from '../http'
import type { MoneyAmount } from './payment'

export type AfterSalesStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface CreateAfterSalesRequest {
  orderNo: string
  afterSalesType: string
  refundAmount: string
  reason: string
  description: string
  evidenceUrls: string[]
}

export interface AfterSalesResponse {
  afterSalesNo: string
  orderNo: string
  applicantId: number
  afterSalesType: string
  refundAmount: MoneyAmount
  reason: string
  description: string
  evidenceUrls: string[]
  status: AfterSalesStatus
  createdAt: string
  sellerId?: number | null
}

export function createAfterSales(data: CreateAfterSalesRequest) {
  return post<AfterSalesResponse>('/api/after-sales', data)
}

export function getAfterSalesDetail(afterSalesNo: string) {
  return get<AfterSalesResponse>(`/api/after-sales/${encodeURIComponent(afterSalesNo)}`)
}
