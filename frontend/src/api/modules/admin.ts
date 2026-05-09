import { get, isDevRuntimeEnabled, post } from '../http'
import type { AuditRecordResponse } from './audit'
import type { WithdrawalResponse } from './wallet'

function adminHeader() {
  if (!isDevRuntimeEnabled()) {
    throw new Error('后台开发模式未启用，请接入正式管理员登录后再访问')
  }
  return { 'X-Admin-Mode': 'enabled', 'X-User-Id': '1', 'X-Dev-Mode': 'enabled' }
}

export interface AuditReviewRequest {
  remark?: string
}

export function adminDashboard() {
  return get<string>('/api/admin/dashboard', undefined, adminHeader())
}

export function getAdminAuditList() {
  return get<AuditRecordResponse[]>('/api/admin/audit', undefined, adminHeader())
}

export function getAdminAuditDetail(auditNo: string) {
  return get<AuditRecordResponse>(`/api/admin/audit/${encodeURIComponent(auditNo)}`, undefined, adminHeader())
}

export function getAdminWithdrawalDetail(withdrawalNo: string) {
  return get<WithdrawalResponse>(`/api/admin/withdrawals/${encodeURIComponent(withdrawalNo)}`, undefined, adminHeader())
}

export function approveAudit(auditNo: string, data: AuditReviewRequest = {}) {
  return post<AuditRecordResponse>(`/api/admin/audit/${encodeURIComponent(auditNo)}/approve`, data, adminHeader())
}

export function rejectAudit(auditNo: string, data: AuditReviewRequest = {}) {
  return post<AuditRecordResponse>(`/api/admin/audit/${encodeURIComponent(auditNo)}/reject`, data, adminHeader())
}
