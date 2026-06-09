const MAX_REDIRECT_DECODE_PASSES = 3

const TABBAR_PREFIX = '/pages/tabbar/'
const PAGE_PREFIX = '/pages/'

export function normalizeLoginRedirect(url: string): string {
  let value = String(url || '').trim()
  for (let index = 0; index < MAX_REDIRECT_DECODE_PASSES; index += 1) {
    const decoded = safeDecode(value).trim()
    if (decoded === value) break
    value = decoded
  }
  if (!isSafeLoginRedirect(value)) return ''
  return value
}

export function loginRedirectIsTabbar(url: string): boolean {
  return url.startsWith(TABBAR_PREFIX)
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function isSafeLoginRedirect(value: string): boolean {
  const lower = value.toLowerCase()
  return value.startsWith(PAGE_PREFIX) &&
    !value.startsWith('//') &&
    !lower.startsWith('/pages/auth/login/index') &&
    !lower.includes('://') &&
    !lower.includes('javascript:') &&
    !lower.includes('data:') &&
    !lower.includes('blob:') &&
    !lower.includes('%2e') &&
    !lower.includes('%2f') &&
    !lower.includes('%5c') &&
    !value.includes('\\') &&
    !value.includes('..')
}
