import { post } from '../http'

export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AuditRecordResponse {
  auditNo: string
  auditType: string
  userId: number
  targetType: string
  targetId: string
  reason: string
  description?: string | null
  status: AuditStatus | string
  reviewRemark?: string | null
  createdAt: string
  reviewedAt?: string | null
}

export interface SubmitReportRequest {
  targetType: string
  targetId: string
  reason: string
  description?: string
  evidenceUrls?: string[]
}

export function submitReport(data: SubmitReportRequest) {
  return post<AuditRecordResponse>('/api/audit/reports', data)
}
