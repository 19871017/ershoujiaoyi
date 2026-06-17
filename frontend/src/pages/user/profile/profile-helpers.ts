import type { UpdateUserProfileRequest, UserProfileResponse } from '../../../api/modules/user'
import { isDefaultAvatarUrl } from '../../../utils/default-avatar'

export const MAX_SHOWCASE_PHOTOS = 6
export const communityImageStoragePrefix = '/uploads/community-image/'
export const genders = [{ label: '♀ 女', value: 'goddess' }, { label: '♂ 男', value: 'god' }]
export const knownVideoIdentityStatuses = new Set(['UNVERIFIED', 'PENDING', 'APPROVED', 'REJECTED'])

export type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
export type ChooseImageFile = { name?: string; type?: string; size?: number }
export type ChosenImageFile = { path: string; filename: string; contentType: ImageContentType; fileSize: number }
export type ChooseImageResult = { tempFilePaths?: string[]; tempFiles?: ChooseImageFile[] }

export function isValidCommunityImageUrl(url: unknown): url is string {
  if (typeof url !== 'string' || !url.startsWith(communityImageStoragePrefix)) return false
  const lower = url.toLowerCase()
  const relativePath = url.slice(communityImageStoragePrefix.length)
  return !!relativePath &&
    !url.startsWith('local://') &&
    !url.startsWith('blob:') &&
    !url.startsWith('data:') &&
    !lower.includes('placeholder') &&
    !lower.includes('%2e') &&
    !lower.includes('%2f') &&
    !lower.includes('%5c') &&
    !url.includes('\\') &&
    !url.includes('..') &&
    !url.includes('//') &&
    !relativePath.split('/').some(segment => !segment)
}

export function uploadedCommunityImageUrl(storageUrl: string, purpose: 'avatar' | 'showcase'): string {
  if (!isValidCommunityImageUrl(storageUrl)) {
    console.warn('profile edit invalid community image url', { purpose, expectedPrefix: communityImageStoragePrefix, storageUrl })
    throw new Error(purpose === 'avatar' ? '头像未完成平台上传校验，请重新选择' : '照片未完成平台上传校验，请重新选择')
  }
  return storageUrl
}

export function storedAvatarUrl(url: string): string {
  if (!url) return ''
  if (isDefaultAvatarUrl(url)) return url
  if (!isValidCommunityImageUrl(url)) {
    console.warn('profile edit rejected stored avatar url', { expectedPrefix: communityImageStoragePrefix, url })
    return ''
  }
  return url
}

export function filterStoredShowcaseUrls(urls: string[]): string[] {
  return urls.filter((url) => {
    const valid = isValidCommunityImageUrl(url)
    if (!valid && url) console.warn('profile edit rejected stored showcase url', { expectedPrefix: communityImageStoragePrefix, url })
    return valid
  }).slice(0, MAX_SHOWCASE_PHOTOS)
}

export function uploadedCommunityImageUrls(urls: string[]): string[] {
  if (urls.some((url) => !isValidCommunityImageUrl(url))) {
    console.warn('profile edit invalid showcase image urls', { expectedPrefix: communityImageStoragePrefix, count: urls.length })
    throw new Error('照片未完成平台上传校验，请重新选择')
  }
  return urls.slice(0, MAX_SHOWCASE_PHOTOS)
}

export function assertBackendProfile(value: unknown): asserts value is UserProfileResponse {
  if (!value || typeof value !== 'object') throw new Error('profile invalid response')
  const profile = value as UserProfileResponse
  if (!Number.isSafeInteger(profile.userId) || profile.userId <= 0) throw new Error('profile invalid userId')
  if (typeof profile.nickname !== 'string' || !profile.nickname.trim()) throw new Error('profile invalid nickname')
  if (typeof profile.mainRole !== 'string' || !profile.mainRole.trim()) throw new Error('profile invalid mainRole')
  if (profile.userNo != null && typeof profile.userNo !== 'string') throw new Error('profile invalid userNo')
  if (profile.avatarUrl != null && typeof profile.avatarUrl !== 'string') throw new Error('profile invalid avatarUrl')
  if (profile.gender != null && typeof profile.gender !== 'string') throw new Error('profile invalid gender')
  if (profile.city != null && typeof profile.city !== 'string') throw new Error('profile invalid city')
  if (profile.bio != null && typeof profile.bio !== 'string') throw new Error('profile invalid bio')
  if (typeof profile.videoIdentityStatus !== 'string' || !knownVideoIdentityStatuses.has(profile.videoIdentityStatus)) throw new Error('profile invalid video identity status')
  if (typeof profile.videoVerified !== 'boolean') throw new Error('profile invalid video verified state')
  if (profile.videoVerified === true && profile.videoIdentityStatus !== 'APPROVED') throw new Error('profile video verified mismatch')
  if (profile.showcaseImageUrls != null && (!Array.isArray(profile.showcaseImageUrls) || profile.showcaseImageUrls.some((url) => typeof url !== 'string'))) throw new Error('profile invalid showcase urls')
}

export function assertUserNoResponse(profile: UserProfileResponse, requestedUserNo: string): void {
  if (profile.userNo !== requestedUserNo) throw new Error('小原圈号响应未确认本次修改')
}

export function assertProfileSaveResponse(profile: UserProfileResponse, payload: UpdateUserProfileRequest, expectedAvatarUrl: string | undefined, expectedShowcaseUrls: string[] | undefined): void {
  if (profile.nickname !== payload.nickname || profile.gender !== payload.gender || (profile.bio || '') !== (payload.bio || '') || profile.mainRole !== payload.mainRole) throw new Error('资料响应未确认本次修改')
  if (expectedAvatarUrl !== undefined && storedAvatarUrl(profile.avatarUrl || '') !== expectedAvatarUrl) throw new Error('头像响应未确认本次修改')
  if (expectedShowcaseUrls !== undefined && JSON.stringify(filterStoredShowcaseUrls(profile.showcaseImageUrls || [])) !== JSON.stringify(expectedShowcaseUrls)) throw new Error('照片秀响应未确认本次修改')
}

export function fileNameFromPath(path: string, fallbackName = 'avatar.jpg'): string {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || fallbackName
  return last.includes('.') ? last : fallbackName
}

export function isImageContentType(value: string | undefined): value is ImageContentType {
  return value === 'image/png' || value === 'image/webp' || value === 'image/jpeg'
}

export function imageContentType(path: string, fallbackType?: string): ImageContentType {
  const normalizedType = fallbackType?.toLowerCase()
  if (isImageContentType(normalizedType)) return normalizedType
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}

export function imageFallbackName(contentType: ImageContentType, prefix = 'avatar'): string {
  if (contentType === 'image/png') return `${prefix}.png`
  if (contentType === 'image/webp') return `${prefix}.webp`
  return `${prefix}.jpg`
}

export function imageFileSize(file?: ChooseImageFile): number {
  return Math.max(1, Math.min(Number(file?.size || 600_000), 10_000_000))
}

export function isUnsupportedTempImagePath(path: string): boolean {
  const lower = path.toLowerCase()
  return !path ||
    path.startsWith('local://') ||
    path.startsWith('blob:') ||
    path.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..')
}

export function imageFilesFromChooseResult(result: ChooseImageResult, prefix = 'avatar'): ChosenImageFile[] {
  const files: ChosenImageFile[] = []
  for (const [index, path] of (result.tempFilePaths || []).entries()) {
    if (!path || isUnsupportedTempImagePath(path)) {
      console.warn('profile image picker rejected file', { prefix, index, path })
      continue
    }
    const file = result.tempFiles?.[index]
    const contentType = imageContentType(path, file?.type)
    files.push({
      path,
      filename: fileNameFromPath(file?.name || path, imageFallbackName(contentType, prefix)),
      contentType,
      fileSize: imageFileSize(file)
    })
  }
  return files
}
