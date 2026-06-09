import { describe, expect, it } from 'vitest'
import { afterSalesEvidenceMediaUrl, isValidAfterSalesEvidenceUrl } from './after-sales-evidence'

describe('admin after-sales evidence helpers', () => {
  it('accepts only canonical backend after-sales evidence upload URLs', () => {
    expect(isValidAfterSalesEvidenceUrl('/uploads/evidence/after-sales/201/proof.jpg')).toBe(true)
    expect(isValidAfterSalesEvidenceUrl('/uploads/evidence/after-sales/201/proof.webp')).toBe(true)
    expect(isValidAfterSalesEvidenceUrl('/uploads/report-evidence/201/proof.jpg')).toBe(false)
    expect(isValidAfterSalesEvidenceUrl('https://example.com/uploads/evidence/after-sales/201/proof.jpg')).toBe(false)
    expect(isValidAfterSalesEvidenceUrl('/uploads/evidence/after-sales/201/placeholder.jpg')).toBe(false)
    expect(isValidAfterSalesEvidenceUrl('/uploads/evidence/after-sales/201/%2e%2e/secret.jpg')).toBe(false)
    expect(isValidAfterSalesEvidenceUrl('/uploads/evidence/after-sales/201/..%2fsecret.jpg')).toBe(false)
    expect(isValidAfterSalesEvidenceUrl('/uploads/evidence/after-sales/201/a\\b.jpg')).toBe(false)
  })

  it('builds authorized after-sales evidence media API URLs', () => {
    const evidenceUrl = '/uploads/evidence/after-sales/201/proof.jpg'

    expect(afterSalesEvidenceMediaUrl('AS-ADMIN-20260608-0001', evidenceUrl)).toBe('/api/admin/after-sales/AS-ADMIN-20260608-0001/evidence?url=%2Fuploads%2Fevidence%2Fafter-sales%2F201%2Fproof.jpg')
    expect(() => afterSalesEvidenceMediaUrl('bad-as', evidenceUrl)).toThrow()
    expect(() => afterSalesEvidenceMediaUrl('AS-ADMIN-20260608-0001', '/uploads/evidence/after-sales/201/%2e%2e/secret.jpg')).toThrow()
  })
})
