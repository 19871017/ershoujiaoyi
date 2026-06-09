import { describe, expect, it } from 'vitest'
import {
  canApproveAuditFromDetail,
  canReviewAuditFromList,
  requiresVideoEvidenceBeforeApproval
} from './audit-review-policy'

describe('audit review policy', () => {
  it('forces pending video identity approval through detail after sufficient watch progress', () => {
    const audit = { auditType: 'VIDEO_IDENTITY', status: 'PENDING' }

    expect(requiresVideoEvidenceBeforeApproval(audit)).toBe(true)
    expect(canReviewAuditFromList(audit, 'approve')).toBe(false)
    expect(canReviewAuditFromList(audit, 'reject')).toBe(true)
    expect(canApproveAuditFromDetail(audit, false)).toBe(false)
    expect(canApproveAuditFromDetail(audit, true)).toBe(true)
  })

  it('keeps non-video pending audits reviewable without media evidence', () => {
    const audit = { auditType: 'REPORT', status: 'PENDING' }

    expect(requiresVideoEvidenceBeforeApproval(audit)).toBe(false)
    expect(canReviewAuditFromList(audit, 'approve')).toBe(true)
    expect(canApproveAuditFromDetail(audit, false)).toBe(true)
  })

  it('blocks already reviewed audits from detail approval', () => {
    expect(canApproveAuditFromDetail({ auditType: 'VIDEO_IDENTITY', status: 'APPROVED' }, true)).toBe(false)
    expect(canReviewAuditFromList({ auditType: 'REPORT', status: 'REJECTED' }, 'reject')).toBe(false)
  })
})
