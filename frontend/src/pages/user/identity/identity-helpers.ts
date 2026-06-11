import type { UserProfileResponse } from '../../../api/modules/user'

export type ChooseVideoFile = { name?: string; path?: string; tempFilePath?: string; type?: string; size?: number }
export type ChooseVideoResult = {
  tempFilePath?: string
  duration?: number
  size?: number
  name?: string
  type?: string
  tempFile?: ChooseVideoFile
  file?: ChooseVideoFile
}
export type RealNameFieldKey = 'name' | 'idNumber'
export type PayoutAccountFieldKey = 'accountName' | 'accountNo'

export const videoIdentityStoragePrefix = '/uploads/video-identity/'
export const checks = ['姓名与收款账户一致', '完整身份证号格式校验', '视频认证需真人出镜', '账号无高风险举报', '提现前需通过平台审核']

export function isValidVideoIdentityStatus(value: unknown): value is UserProfileResponse['videoIdentityStatus'] {
  return value === 'UNVERIFIED' || value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED'
}

export function isValidIdentityStatus(value: unknown): value is NonNullable<UserProfileResponse['identityStatus']> {
  return value === 'UNVERIFIED' || value === 'PENDING' || value === 'VERIFIED' || value === 'REJECTED'
}

export function hasApprovedVideoIdentity(value: UserProfileResponse): boolean {
  if (value.videoVerified !== true || value.videoIdentityStatus !== 'APPROVED') {
    return false
  }
  try {
    return !!validatedVideoIdentityUrl(value.videoIdentityUrl)
  } catch {
    return false
  }
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
  const isH5BlobPath = lower.startsWith('blob:')
  return !path ||
    lower.startsWith('local://') ||
    lower.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..') ||
    (!isH5BlobPath && path.includes('//'))
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

export function videoFallbackName(contentType: string): string {
  if (contentType === 'video/quicktime') return 'video-identity.mov'
  if (contentType === 'video/x-m4v') return 'video-identity.m4v'
  return 'video-identity.mp4'
}

export function fileNameFromPath(path: string, fallback = 'video-identity.mp4'): string {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || fallback
  return last.includes('.') ? last : fallback
}

export function normalizedVideoContentType(contentType: unknown): string | undefined {
  if (typeof contentType !== 'string') return undefined
  const lower = contentType.trim().toLowerCase()
  if (lower === 'video/mp4' || lower === 'video/mpeg4') return 'video/mp4'
  if (lower === 'video/quicktime' || lower === 'video/mov') return 'video/quicktime'
  if (lower === 'video/x-m4v' || lower === 'video/m4v') return 'video/x-m4v'
  if (lower === 'video/webm') throw new Error('暂不支持 WebM 视频，请选择 MP4、MOV 或 M4V')
  if (lower.startsWith('video/')) throw new Error('暂不支持该视频格式，请选择 MP4、MOV 或 M4V')
  return undefined
}

export function guessVideoContentType(path: string, fallbackType?: unknown): string {
  const normalized = normalizedVideoContentType(fallbackType)
  if (normalized) return normalized
  const lower = path.toLowerCase()
  if (lower.startsWith('blob:')) return 'video/mp4'
  if (lower.endsWith('.mp4')) return 'video/mp4'
  if (lower.endsWith('.mov')) return 'video/quicktime'
  if (lower.endsWith('.m4v')) return 'video/x-m4v'
  if (lower.endsWith('.webm')) throw new Error('暂不支持 WebM 视频，请选择 MP4、MOV 或 M4V')
  throw new Error('暂不支持该视频格式，请选择 MP4、MOV 或 M4V')
}

export function hasInvalidVideoIdentityDuration(duration: unknown): boolean {
  return typeof duration === 'number' && Number.isFinite(duration) && duration > 10
}

export function normalizedIdNumber(value: string): string {
  return value.trim().toUpperCase()
}

export function isValidChineseIdNumber(value: string): boolean {
  const normalized = normalizedIdNumber(value)
  if (!/^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9X]$/.test(normalized)) return false
  const year = Number(normalized.slice(6, 10))
  const month = Number(normalized.slice(10, 12))
  const day = Number(normalized.slice(12, 14))
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return false
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const checks = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']
  const sum = weights.reduce((total, weight, index) => total + Number(normalized[index]) * weight, 0)
  return checks[sum % 11] === normalized[17]
}

export function hasMaskedAccountMarker(value: string): boolean {
  return /[*＊]/.test(value)
}

export function videoTypeFromPickerResult(result: ChooseVideoResult, blob?: Blob): unknown {
  return blob?.type || result.type || result.tempFile?.type || result.file?.type
}

export function videoNameFromPickerResult(result: ChooseVideoResult, contentType: string): string {
  const fallback = videoFallbackName(contentType)
  const name = result.name || result.tempFile?.name || result.file?.name
  if (name && name.includes('.')) return name
  return fileNameFromPath(result.tempFilePath || result.tempFile?.path || result.file?.path || '', fallback)
}
