import type { UserProfileResponse } from '../../../api/modules/user'

export const categories = ['衣物', '鞋袜', '小用品']
export const conditions = ['全新未拆', '几乎全新', '轻微使用', '有瑕疵已说明']
export const platformTradeRule = '按平台订单流程交易'
export const productImageStoragePrefix = '/uploads/product-image/'
export const tradeOptions = [platformTradeRule]
export const locationMaxLength = 24

export type TextFieldKey = 'title' | 'description' | 'price' | 'location'
export type GetLocationResult = { latitude: number; longitude: number }

export function fileNameFromPath(path: string) {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'product-image.jpg'
  return last.includes('.') ? last : `${last}.jpg`
}

export function imageContentType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}

export function hasInvalidTempImagePath(path: string): boolean {
  const lower = path.toLowerCase()
  return !path || path.startsWith('local://') || path.startsWith('blob:') || path.startsWith('data:') || lower.includes('placeholder') || lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c') || path.includes('\\') || path.includes('..')
}

export function hasInvalidProductImageUrl(url: unknown): boolean {
  if (typeof url !== 'string' || !url.startsWith('/uploads/product-image/')) return true
  const lower = url.toLowerCase()
  const relativePath = url.slice(productImageStoragePrefix.length)
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

export function validatedProductImageUrl(storageUrl: unknown): string {
  if (hasInvalidProductImageUrl(storageUrl)) {
    console.warn('product publish invalid product image url', { expectedPrefix: productImageStoragePrefix, storageUrl })
    throw new Error('商品图片需先完成平台上传票据校验')
  }
  return storageUrl as string
}

export function inputValue(event: unknown): string {
  const target = event as { detail?: { value?: unknown } }
  const value = target.detail?.value
  return typeof value === 'string' ? value : ''
}

export function normalizeLocation(value: string) {
  return value.trim().slice(0, locationMaxLength)
}

export function assertBackendProfile(value: unknown): asserts value is UserProfileResponse {
  if (!value || typeof value !== 'object') throw new Error('product publish invalid backend profile')
  const profile = value as UserProfileResponse
  if (!Number.isSafeInteger(profile.userId) || profile.userId <= 0) throw new Error('product publish invalid userId')
  if (typeof profile.mainRole !== 'string' || !profile.mainRole.trim()) throw new Error('product publish invalid mainRole')
  if (typeof profile.videoVerified !== 'boolean') throw new Error('product publish invalid video verified state')
  if (typeof profile.videoIdentityStatus !== 'string' || !profile.videoIdentityStatus) throw new Error('product publish invalid video identity status')
  if (profile.city != null && typeof profile.city !== 'string') throw new Error('product publish invalid city')
}

export function resolvePublishPermission(profile: UserProfileResponse) {
  const role = String(profile.mainRole || '').toUpperCase()
  return profile.videoVerified === true && profile.videoIdentityStatus === 'APPROVED' && (role === 'SELLER' || role === 'BOTH')
}

export function resolvePublishBlockMessage(profile: UserProfileResponse) {
  const status = String(profile.videoIdentityStatus || '').toUpperCase()
  if (status === 'PENDING') return '卖家认证审核中'
  if (status === 'REJECTED') return '卖家认证未通过，请重新提交认证'
  return '请先完成卖家认证'
}

export function locationErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : ''
  switch (message) {
    case 'location reverse geocode disabled':
      return '定位服务已关闭，请手动填写城市/区域'
    case 'location reverse geocode not configured':
      return '定位服务未配置完成，请手动填写城市/区域'
    case '定位服务未返回真实城市':
    case '定位结果为空':
      return message
    default:
      return '定位失败，请手动填写城市/区域'
  }
}
