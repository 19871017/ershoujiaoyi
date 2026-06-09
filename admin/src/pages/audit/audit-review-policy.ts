import type { AuditRecordResponse } from '../../api'

export function isPendingVideoIdentityAudit(audit?: Pick<AuditRecordResponse, 'auditType' | 'status'> | null): boolean {
  return audit?.auditType === 'VIDEO_IDENTITY' && audit.status === 'PENDING'
}

export function requiresVideoEvidenceBeforeApproval(audit?: Pick<AuditRecordResponse, 'auditType' | 'status'> | null): boolean {
  return isPendingVideoIdentityAudit(audit)
}

export function canApproveAuditFromDetail(audit: Pick<AuditRecordResponse, 'auditType' | 'status'> | null, hasSufficientVideoWatch: boolean): boolean {
  if (!audit || audit.status !== 'PENDING') return false
  if (requiresVideoEvidenceBeforeApproval(audit)) return hasSufficientVideoWatch
  return true
}

export function canReviewAuditFromList(audit: Pick<AuditRecordResponse, 'auditType' | 'status'> | null, action: 'approve' | 'reject'): boolean {
  if (!audit || audit.status !== 'PENDING') return false
  if (action === 'approve' && requiresVideoEvidenceBeforeApproval(audit)) return false
  return true
}
