import type { ProductListItemResponse } from '../../../api/modules/product'
import type { UserProfileResponse } from '../../../api/modules/user'

export const unavailableProfileMessage = '卖家资料暂时不可用，请稍后再看'
export const noBackendProductsMessage = '这位卖家暂时没有公开在售宝贝'
export const productLoadFailedMessage = '卖家宝贝暂时加载失败，请稍后重试'
export const soldProductLoadFailedMessage = '卖家已售记录加载失败'
export const videoIdentityStoragePrefix = '/uploads/video-identity/'
export const showcaseImageStoragePrefix = '/uploads/community-image/'
export const avatarImageStoragePrefix = '/uploads/avatar/'
export const productImageStoragePrefix = '/uploads/product-image/'
export const levelThresholds = [0, 50, 200, 800, 2000, 5000, 12000, 30000, 80000, 200000]
export const godLevelTitles = ['初见绅士', '心动骑士', '闪耀贵宾', '星光守护', '黄金公子', '铂金名士', '钻石守护', '星河领主', '传奇男神', '原圈荣耀']
export const goddessLevelTitles = ['心动新星', '魅力甜心', '人气佳人', '星光女神', '闪耀名媛', '璀璨公主', '荣耀女王', '星河缪斯', '传奇女神', '原圈天后']

const rejectedPublicMediaWarnings = new Set<string>()

export function validatedPublicMediaUrl(url: unknown, expectedPrefix: string | string[]): string {
  if (!url) return ''
  const prefixes = Array.isArray(expectedPrefix) ? expectedPrefix : [expectedPrefix]
  if (typeof url !== 'string') {
    console.warn('public profile rejected media url', { expectedPrefix: prefixes.join(','), url })
    return ''
  }
  const matchedPrefix = prefixes.find((prefix) => url.startsWith(prefix))
  const lower = url.toLowerCase()
  const relativePath = matchedPrefix ? url.slice(matchedPrefix.length) : ''
  const invalid = !relativePath ||
    !matchedPrefix ||
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
  if (invalid) {
    const warningKey = `${prefixes.join(',')}:${url}`
    if (!rejectedPublicMediaWarnings.has(warningKey)) {
      rejectedPublicMediaWarnings.add(warningKey)
      console.warn('public profile rejected media url', { expectedPrefix: prefixes.join(','), url })
    }
    return ''
  }
  return url
}

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('public profile route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function isValidVideoIdentityStatus(value: unknown): value is UserProfileResponse['videoIdentityStatus'] {
  return value === 'UNVERIFIED' || value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED'
}

export function hasApprovedPublicSellerVideo(profile: UserProfileResponse): boolean {
  return profile.videoVerified === true &&
    profile.videoIdentityStatus === 'APPROVED' &&
    !!validatedPublicMediaUrl(profile.videoIdentityUrl || '', videoIdentityStoragePrefix)
}

export function isValidBackendUserId(value: string): boolean {
  return /^[1-9]\d*$/.test(value)
}

export function compactNumber(value: number | undefined): string {
  const numberValue = Number(value || 0)
  if (!Number.isFinite(numberValue) || numberValue <= 0) return '0'
  if (numberValue >= 10000) return `${(numberValue / 10000).toFixed(numberValue >= 100000 ? 0 : 1)}万`
  return String(Math.floor(numberValue))
}

export function levelGlow(level?: number | null) {
  const normalized = Math.max(1, Math.min(10, Math.floor(Number(level || 1))))
  return `glow-lv-${normalized}`
}

export function productIcon(title: string): string {
  if (title.includes('裙')) return '👗'
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🧦'
  return '👜'
}

export function assertProductList(value: unknown, expectedStatus: 'ACTIVE' | 'SOLD' = 'ACTIVE'): asserts value is ProductListItemResponse[] {
  if (!Array.isArray(value)) throw new Error('public profile invalid product list')
  for (const item of value) {
    if (!item || typeof item !== 'object') throw new Error('public profile invalid product item')
    const product = item as ProductListItemResponse
    if (!Number.isSafeInteger(product.productId) || product.productId <= 0) throw new Error('public profile invalid productId')
    if (typeof product.title !== 'string' || !product.title.trim()) throw new Error('public profile invalid product title')
    if (typeof product.price !== 'string' || !product.price.trim()) throw new Error('public profile invalid product price')
    if (product.coverImageUrl != null && typeof product.coverImageUrl !== 'string') throw new Error('public profile invalid product cover')
    if (expectedStatus === 'ACTIVE' && product.visible !== true) throw new Error('public profile invalid product visibility')
    if (product.status !== expectedStatus) throw new Error('public profile invalid product status')
    if (product.auditState !== 'APPROVED') throw new Error('public profile invalid product audit state')
  }
}

export function assertPublicProfile(value: unknown, expectedUserId: string): asserts value is UserProfileResponse {
  if (!value || typeof value !== 'object') throw new Error('public profile invalid backend profile')
  const backendProfile = value as UserProfileResponse
  if (!Number.isSafeInteger(backendProfile.userId) || backendProfile.userId <= 0) throw new Error('public profile invalid userId')
  if (String(backendProfile.userId) !== expectedUserId) throw new Error('public profile userId mismatch')
  if (typeof backendProfile.nickname !== 'string' || !backendProfile.nickname.trim()) throw new Error('public profile invalid nickname')
  if (backendProfile.userNo != null && typeof backendProfile.userNo !== 'string') throw new Error('public profile invalid userNo')
  if (backendProfile.avatarUrl != null && typeof backendProfile.avatarUrl !== 'string') throw new Error('public profile invalid avatarUrl')
  if (typeof backendProfile.mainRole !== 'string' || !backendProfile.mainRole.trim()) throw new Error('public profile invalid mainRole')
  if (!isValidVideoIdentityStatus(backendProfile.videoIdentityStatus)) throw new Error('public profile invalid backend video status')
  if (typeof backendProfile.videoVerified !== 'boolean') throw new Error('public profile invalid video verified state')
  if (backendProfile.videoVerified === true && backendProfile.videoIdentityStatus !== 'APPROVED') throw new Error('public profile video verified mismatch')
  if (backendProfile.videoIdentityUrl != null && typeof backendProfile.videoIdentityUrl !== 'string') throw new Error('public profile invalid video url')
  if (backendProfile.videoVerified === true && !validatedPublicMediaUrl(backendProfile.videoIdentityUrl, videoIdentityStoragePrefix)) throw new Error('public profile invalid approved video url')
  if (backendProfile.showcaseImageUrls != null && (!Array.isArray(backendProfile.showcaseImageUrls) || backendProfile.showcaseImageUrls.some((url) => typeof url !== 'string' || !validatedPublicMediaUrl(url, showcaseImageStoragePrefix)))) throw new Error('public profile invalid showcase urls')
  if (backendProfile.followedByMe != null && typeof backendProfile.followedByMe !== 'boolean') throw new Error('public profile invalid follow state')
  for (const [field, numberValue] of Object.entries({ followerCount: backendProfile.followerCount, followingCount: backendProfile.followingCount, sellerCharmScore: backendProfile.sellerCharmScore, buyerPowerScore: backendProfile.buyerPowerScore })) {
    if (numberValue != null && (!Number.isFinite(numberValue) || numberValue < 0)) throw new Error(`public profile invalid ${field}`)
  }
}
