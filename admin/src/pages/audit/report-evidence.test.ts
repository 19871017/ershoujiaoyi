import { describe, expect, it } from 'vitest'
import { extractReportEvidenceUrls, isValidReportEvidenceUrl, reportEvidenceMediaUrl } from './report-evidence'

describe('admin report evidence helpers', () => {
  it('extracts unique backend report evidence upload URLs from masked descriptions', () => {
    const first = '/uploads/report-evidence/7/proof-one.jpg'
    const second = '/uploads/report-evidence/7/proof-two.webp'

    expect(extractReportEvidenceUrls(`用户已脱敏\n举报凭证：${first},${second},${first}`)).toEqual([first, second])
  })

  it('rejects demo placeholders and path traversal evidence URLs fail closed', () => {
    expect(isValidReportEvidenceUrl('/uploads/report-evidence/7/proof.png')).toBe(true)
    expect(isValidReportEvidenceUrl('/uploads/report-evidence/7/placeholder.png')).toBe(false)
    expect(isValidReportEvidenceUrl('/uploads/report-evidence/7/%2e%2e/secret.png')).toBe(false)
    expect(isValidReportEvidenceUrl('/uploads/evidence/after-sales/7/proof.png')).toBe(false)
    expect(extractReportEvidenceUrls('举报凭证：/uploads/report-evidence/7/placeholder.png')).toEqual([])
  })

  it('builds authorized report evidence media API URLs', () => {
    const evidenceUrl = '/uploads/report-evidence/7/proof.png'
    expect(reportEvidenceMediaUrl('AU-REPORT-20260608-0001', evidenceUrl)).toBe('/api/admin/audit/AU-REPORT-20260608-0001/report-evidence?url=%2Fuploads%2Freport-evidence%2F7%2Fproof.png')
    expect(() => reportEvidenceMediaUrl('bad-audit', evidenceUrl)).toThrow()
    expect(() => reportEvidenceMediaUrl('AU-REPORT-20260608-0001', '/uploads/report-evidence/7/%2e%2e/secret.png')).toThrow()
  })
})
