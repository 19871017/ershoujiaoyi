export type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
export type ChooseImageFile = { name?: string; type?: string; size?: number }
export type ChooseImageResult = { tempFilePaths?: string[]; tempFiles?: ChooseImageFile[] }
export type TextFieldKey = 'title' | 'content'

export const topics = ['生活日常', '闲置避坑', '交易经验', '求购心愿']
export const communityImageStoragePrefix = '/uploads/community-image/'

export function fileNameFromPath(path: string, fallbackName = 'community-image.jpg') {
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

export function imageFileSize(file?: ChooseImageFile) {
  return Math.max(1, Math.min(Number(file?.size || 600_000), 10_000_000))
}

export function imageFallbackName(contentType: ImageContentType) {
  if (contentType === 'image/png') return 'community-image.png'
  if (contentType === 'image/webp') return 'community-image.webp'
  return 'community-image.jpg'
}

export function hasInvalidTempImagePath(path: string): boolean {
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
    path.includes('..') ||
    path.includes('//')
}

export function hasInvalidCommunityImageUrl(url: unknown): boolean {
  if (typeof url !== 'string' || !url.startsWith('/uploads/community-image/')) return true
  const lower = url.toLowerCase()
  const relativePath = url.slice(communityImageStoragePrefix.length)
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

export function validatedCommunityImageUrl(storageUrl: unknown): string {
  if (hasInvalidCommunityImageUrl(storageUrl)) {
    console.warn('community compose invalid community image url', { expectedPrefix: communityImageStoragePrefix, storageUrl })
    throw new Error('图片需先完成平台上传票据校验')
  }
  return storageUrl as string
}

export function inputValue(field: TextFieldKey, event: unknown): string | undefined {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  if (typeof value !== 'string') {
    console.warn('community compose input event invalid', { field })
    return undefined
  }
  return value
}
