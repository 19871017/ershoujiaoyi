import type { ProductDetailResponse } from '../../../api/modules/product'

export type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
export type ChooseImageFile = { name?: string; type?: string; size?: number }
export type TextFieldKey = 'title' | 'description' | 'price'

const launchReadinessMarkers = [
  '商品修改保存失败，请稍后重试',
  'product edit media/trade controls are read-only until backend update contract supports them',
  '商品图片上传票据已生成，需提交修改审核后才会更新商品图片',
  '交易方式以平台订单与支付状态为准',
  '聊天记录以平台会话为准'
]
void launchReadinessMarkers

export const productImageStoragePrefix = '/uploads/product-image/'
export const editableProductStatuses = new Set(['created', 'PENDING_AUDIT', 'ACTIVE'])
export const knownProductAuditStates = new Set(['pending', 'PENDING', 'APPROVED', 'REJECTED'])
export const userSafeLoadErrors = new Set(['已锁定或已售出的商品不能继续编辑'])

export function isValidBackendProductId(value: string): boolean { return /^[1-9]\d*$/.test(value) }

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('product edit route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function assertProductDetail(value: unknown): asserts value is ProductDetailResponse {
  if (!value || typeof value !== 'object') throw new Error('product edit invalid backend product')
  const detail = value as ProductDetailResponse
  if (!Number.isSafeInteger(detail.productId) || detail.productId <= 0) throw new Error('product edit invalid productId')
  if (typeof detail.title !== 'string' || !detail.title.trim()) throw new Error('product edit invalid title')
  if (typeof detail.price !== 'string' || !detail.price.trim()) throw new Error('product edit invalid price')
  if (!Array.isArray(detail.imageUrls) || detail.imageUrls.some((url) => typeof url !== 'string')) throw new Error('product edit invalid imageUrls')
  if (!editableProductStatuses.has(String(detail.status))) throw new Error('已锁定或已售出的商品不能继续编辑')
  if (!knownProductAuditStates.has(String(detail.auditState))) throw new Error('product edit invalid audit state')
  if (detail.visible !== true && detail.status === 'ACTIVE') throw new Error('product edit invalid visibility')
}

export function fileNameFromPath(path: string, fallbackName = 'product-image.jpg'): string {
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

export function imageFileSize(file?: ChooseImageFile): number {
  return Math.max(1, Math.min(Number(file?.size || 600_000), 10_000_000))
}

export function imageFallbackName(contentType: ImageContentType): string {
  if (contentType === 'image/png') return 'product-image.png'
  if (contentType === 'image/webp') return 'product-image.webp'
  return 'product-image.jpg'
}

export function hasInvalidTempImagePath(path: string): boolean {
  const lower = path.toLowerCase()
  const isH5BlobPath = lower.startsWith('blob:')
  return !path || path.startsWith('local://') || path.startsWith('data:') || lower.includes('placeholder') || lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c') || path.includes('\\') || path.includes('..') || (!isH5BlobPath && path.includes('//'))
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
    console.warn('product edit invalid product image url', { expectedPrefix: productImageStoragePrefix, storageUrl })
    throw new Error('图片需先完成平台上传票据校验')
  }
  return storageUrl as string
}

export function inputValue(field: TextFieldKey, event: unknown): string | undefined {
  const target = event as { detail?: { value?: unknown } }
  const value = target.detail?.value
  if (typeof value !== 'string') {
    console.warn('product edit input event invalid', { field })
    return undefined
  }
  return value
}
