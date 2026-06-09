import type { ProductDetailResponse } from '../../../api/modules/product'
import type { UserProfileResponse } from '../../../api/modules/user'

export const productImageStoragePrefix = '/uploads/product-image/'
export const communityImageStoragePrefix = '/uploads/community-image/'
export const avatarImageStoragePrefix = '/uploads/avatar/'
export const videoIdentityStoragePrefix = '/uploads/video-identity/'
export const sellerProfileFallbackText = '卖家资料以平台记录为准 · 暂无公开信用/成交统计'
export const sellerProfileFailedText = '卖家资料暂时不可用，请稍后再看'

export const safeRules = [
  { icon: '🛡️', title: '平台交易', desc: '订单、支付和售后状态以平台记录为准' },
  { icon: '💬', title: '会话记录', desc: '沟通内容以平台私信记录为准' },
  { icon: '📦', title: '交付确认', desc: '交付与收货状态以平台订单记录为准' }
]

export const defaultConfirmItems = [
  { key: 'rule', label: '已阅读订单、支付和售后状态以平台记录为准', checked: true },
  { key: 'condition', label: '已确认商品成色和瑕疵说明', checked: false },
  { key: 'address', label: '已确认收货信息；交付与收货状态以平台订单记录为准', checked: false }
]

export function isUnsupportedDisplayMediaUrl(url: string, expectedPrefix: string): boolean {
  const lower = url.toLowerCase()
  const relativePath = url.startsWith(expectedPrefix) ? url.slice(expectedPrefix.length) : ''
  return !relativePath ||
    url.startsWith('local://') ||
    url.startsWith('blob:') ||
    url.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    url.includes('\\') ||
    url.includes('..') ||
    url.includes('//') ||
    relativePath.split('/').some(segment => !segment)
}

export function validatedDisplayMediaUrl(url: unknown, expectedPrefix: string | string[], purpose: string): string {
  if (!url) return ''
  const prefixes = Array.isArray(expectedPrefix) ? expectedPrefix : [expectedPrefix]
  const matchedPrefix = typeof url === 'string' ? prefixes.find((prefix) => url.startsWith(prefix)) : ''
  if (typeof url !== 'string' || !matchedPrefix || isUnsupportedDisplayMediaUrl(url, matchedPrefix)) {
    console.warn('product detail rejected media url', { purpose, expectedPrefix: prefixes.join(','), url })
    return ''
  }
  return url
}

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('product detail route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function isPositiveIntegerId(value: unknown): boolean {
  const numeric = Number(value)
  return Number.isInteger(numeric) && numeric > 0
}

export function isValidBackendProductId(value: unknown): boolean {
  return isPositiveIntegerId(value)
}

export function isValidBackendUserId(value: unknown): boolean {
  return isPositiveIntegerId(value)
}

export function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function isValidVideoIdentityStatus(value: unknown): value is UserProfileResponse['videoIdentityStatus'] {
  return value === 'UNVERIFIED' || value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED'
}

export function hasApprovedSellerVideoIdentity(profile: UserProfileResponse): boolean {
  return profile.videoVerified === true &&
    profile.videoIdentityStatus === 'APPROVED' &&
    !!validatedDisplayMediaUrl(profile.videoIdentityUrl || '', videoIdentityStoragePrefix, 'seller-video')
}

export function assertProductDetail(value: unknown): asserts value is ProductDetailResponse {
  if (!value || typeof value !== 'object') throw new Error('product detail invalid backend product')
  const product = value as ProductDetailResponse
  if (!Number.isSafeInteger(product.productId) || product.productId <= 0) throw new Error('product detail invalid productId')
  if (typeof product.title !== 'string' || !product.title.trim()) throw new Error('product detail invalid title')
  if (typeof product.price !== 'string' || !product.price.trim()) throw new Error('product detail invalid price')
  if (!Array.isArray(product.imageUrls) || product.imageUrls.some((url) => typeof url !== 'string')) throw new Error('product detail invalid image urls')
  if (product.visible !== true) throw new Error('product detail invalid visibility')
  if (product.status !== 'ACTIVE') throw new Error('product detail invalid status')
  if (product.auditState !== 'APPROVED') throw new Error('product detail invalid audit state')
  if (product.sellerId != null && !isValidBackendUserId(product.sellerId)) throw new Error('product detail invalid sellerId')
}

export function assertSellerProfile(value: unknown, sellerId: number): asserts value is UserProfileResponse {
  if (!value || typeof value !== 'object') throw new Error('product seller profile invalid backend profile')
  const profile = value as UserProfileResponse
  if (!Number.isSafeInteger(profile.userId) || profile.userId <= 0) throw new Error('product seller profile invalid userId')
  if (profile.userId !== Number(sellerId)) throw new Error('product seller profile userId mismatch')
  if (typeof profile.nickname !== 'string' || !profile.nickname.trim()) throw new Error('product seller profile invalid nickname')
  if (profile.avatarUrl != null && typeof profile.avatarUrl !== 'string') throw new Error('product seller profile invalid avatarUrl')
  if (typeof profile.mainRole !== 'string' || !profile.mainRole.trim()) throw new Error('product seller profile invalid mainRole')
  if (!isValidVideoIdentityStatus(profile.videoIdentityStatus)) throw new Error('product seller profile invalid video status')
  if (typeof profile.videoVerified !== 'boolean') throw new Error('product seller profile invalid video verified state')
  if (profile.videoVerified === true && profile.videoIdentityStatus !== 'APPROVED') throw new Error('product seller profile video verified mismatch')
  if (profile.videoIdentityUrl != null && typeof profile.videoIdentityUrl !== 'string') throw new Error('product seller profile invalid video url')
  if (profile.videoVerified === true && !hasApprovedSellerVideoIdentity(profile)) throw new Error('product seller profile invalid approved video url')
}

export function compactNumber(value: number | undefined): string {
  const numberValue = Number(value || 0)
  if (!Number.isFinite(numberValue) || numberValue <= 0) return '0'
  if (numberValue >= 10000) return `${(numberValue / 10000).toFixed(numberValue >= 100000 ? 0 : 1)}万`
  return String(Math.floor(numberValue))
}

export function productStatusText(status: unknown): string {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'ACTIVE' || normalized === 'CREATED' || normalized === 'APPROVED') return '在售'
  if (normalized === 'SOLD') return '已售'
  if (normalized === 'OFFLINE') return '已下架'
  if (normalized === 'PENDING_AUDIT' || normalized === 'PENDING') return '审核中'
  if (normalized === 'REJECTED') return '未通过'
  return '状态待确认'
}

export function auditStateText(auditState: unknown): string {
  const normalized = String(auditState || '').toUpperCase()
  if (normalized === 'APPROVED') return '已审核'
  if (normalized === 'PENDING' || normalized === 'PENDING_AUDIT') return '审核中'
  if (normalized === 'REJECTED') return '未通过'
  return '审核状态'
}

export function compactPrice(price: string): string {
  return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

export function iconFor(title: string): string {
  if (title.includes('裙')) return '👗'
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🧦'
  return '👜'
}

export function toneClass(id: number): string {
  return `tone-${id % 4}`
}
