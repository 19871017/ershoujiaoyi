const reportEvidencePrefix = '/uploads/report-evidence/'
const evidencePattern = /\/uploads\/report-evidence\/[A-Za-z0-9._~/%-]+/g

export function isValidReportEvidenceUrl(value: string): boolean {
  const safeValue = value.trim()
  const lower = safeValue.toLowerCase()
  if (!safeValue.startsWith(reportEvidencePrefix)) return false
  if (
    lower.includes('preview') ||
    lower.includes('demo') ||
    lower.includes('mock') ||
    lower.includes('sample') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    safeValue.includes('\\') ||
    safeValue.includes('..') ||
    safeValue.includes('//')
  ) {
    return false
  }
  const relativePath = safeValue.slice(reportEvidencePrefix.length)
  return !!relativePath && relativePath.split('/').every((segment) => !!segment)
}

export function extractReportEvidenceUrls(description?: string | null): string[] {
  if (!description) return []
  const matches = description.match(evidencePattern) || []
  return Array.from(new Set(matches.map((item) => item.trim()).filter(isValidReportEvidenceUrl))).slice(0, 6)
}
