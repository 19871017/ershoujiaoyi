import type { UserProfileResponse } from '../../../api/modules/user'

export type ChooseVideoResult = { tempFilePath?: string; size?: number }
export type RealNameFieldKey = 'name' | 'idTail'

export const videoIdentityStoragePrefix = '/uploads/video-identity/'
export const checks = ['姓名与收款账户一致', '证件凭证已打码', '视频认证需真人出镜', '账号无高风险举报', '提现前需通过平台审核']

export function isValidVideoIdentityStatus(value: unknown): value is UserProfileResponse['videoIdentityStatus'] {
  return value === 'UNVERIFIED' || value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED'
}

export function isValidIdentityStatus(value: unknown): value is NonNullable<UserProfileResponse['identityStatus']> {
  return value === 'UNVERIFIED' || value === 'PENDING' || value === 'VERIFIED' || value === 'REJECTED'
}

export function hasApprovedVideoIdentity(value: UserProfileResponse): boolean {
  return value.videoVerified === true && value.videoIdentityStatus === 'APPROVED'
}

export function assertBackendProfile(value: unknown): asserts value is UserProfileResponse {
  if (!value || typeof value !== 'object') throw new Error('identity invalid backend profile')
  const backendProfile = value as UserProfileResponse
  if (!Number.isSafeInteger(backendProfile.userId) || backendProfile.userId <= 0) throw new Error('identity invalid backend userId')
  if (!isValidIdentityStatus(backendProfile.identityStatus || 'UNVERIFIED')) throw new Error('identity invalid backend real-name status')
  if (!isValidVideoIdentityStatus(backendProfile.videoIdentityStatus)) throw new Error('identity invalid backend video status')
  if (backendProfile.videoVerified === true && backendProfile.videoIdentityStatus !== 'APPROVED') throw new Error('identity video verified mismatch')
  const requiresVideoUrl = backendProfile.videoVerified === true || backendProfile.videoIdentityStatus === 'PENDING'
  if (requiresVideoUrl && !backendProfile.videoIdentityUrl) throw new Error('identity backend video status missing videoIdentityUrl')
  if (backendProfile.videoIdentityUrl) validatedVideoIdentityUrl(backendProfile.videoIdentityUrl)
}

export function hasInvalidTempVideoPath(path: string): boolean {
  const lower = path.toLowerCase()
  return !path ||
    lower.startsWith('local://') ||
    lower.startsWith('blob:') ||
    lower.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..') ||
    path.includes('//')
}

export function isPickerCancel(error: unknown): boolean {
  const message = String((error as { errMsg?: string })?.errMsg || '').trim().toLowerCase()
  return message === 'cancel' || message === 'choosevideo:fail cancel'
}

export function validatedVideoIdentityUrl(storageUrl: unknown): string {
  if (typeof storageUrl !== 'string') {
    console.warn('identity video invalid storageUrl', { expectedPrefix: videoIdentityStoragePrefix, storageUrl })
    throw new Error('视频资料未完成平台上传校验，请重新选择')
  }
  const lower = storageUrl.toLowerCase()
  const relativePath = storageUrl.startsWith(videoIdentityStoragePrefix) ? storageUrl.slice(videoIdentityStoragePrefix.length) : ''
  const invalid = !relativePath ||
    storageUrl.startsWith('local://') ||
    storageUrl.startsWith('blob:') ||
    storageUrl.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    storageUrl.includes('\\') ||
    storageUrl.includes('..') ||
    storageUrl.includes('//') ||
    relativePath.split('/').some(segment => !segment)
  if (invalid) {
    console.warn('identity video invalid storageUrl', { expectedPrefix: videoIdentityStoragePrefix, storageUrl })
    throw new Error('视频资料未完成平台上传校验，请重新选择')
  }
  return storageUrl
}

export function fileNameFromPath(path: string): string {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'video-identity.mp4'
  return last.includes('.') ? last : `${last}.mp4`
}

export function guessVideoContentType(path: string): string {
  const lower = path.toLowerCase()
  if (lower.endsWith('.mov')) return 'video/quicktime'
  if (lower.endsWith('.m4v')) return 'video/x-m4v'
  return 'video/mp4'
}
