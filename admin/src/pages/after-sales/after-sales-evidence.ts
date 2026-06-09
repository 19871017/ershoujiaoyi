const afterSalesEvidencePrefix = '/uploads/evidence/after-sales/'

export function isValidAfterSalesEvidenceUrl(value: string): boolean {
  const safeValue = value.trim()
  const lower = safeValue.toLowerCase()
  if (!safeValue.startsWith(afterSalesEvidencePrefix)) return false
  if (
    lower.includes('preview') ||
    lower.includes('demo') ||
    lower.includes('mock') ||
    lower.includes('sample') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    /^(?:https?:|data:|blob:|file:)/i.test(safeValue) ||
    safeValue.includes('\\') ||
    safeValue.includes('..') ||
    safeValue.includes('//')
  ) {
    return false
  }
  const relativePath = safeValue.slice(afterSalesEvidencePrefix.length)
  return !!relativePath && relativePath.split('/').every((segment) => !!segment)
}

export function afterSalesEvidenceMediaUrl(afterSalesNo: string, evidenceUrl: string): string {
  if (!/^AS-[A-Z0-9-]{6,128}$/.test(afterSalesNo)) {
    throw new Error('afterSalesNo invalid')
  }
  if (!isValidAfterSalesEvidenceUrl(evidenceUrl)) {
    throw new Error('after-sales evidence url invalid')
  }
  const params = new URLSearchParams({ url: evidenceUrl })
  return `/api/admin/after-sales/${encodeURIComponent(afterSalesNo)}/evidence?${params.toString()}`
}
