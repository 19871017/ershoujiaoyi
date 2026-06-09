export type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
export type ChooseImageFile = { name?: string; type?: string; size?: number }
export type ChooseImageResult = { tempFilePaths?: string[]; tempFiles?: ChooseImageFile[] }
export type TextFieldKey = 'title' | 'content'
export interface CommunityCreateResultLike {
  postNo?: unknown
  postId?: unknown
  topic?: unknown
  status?: unknown
}

export interface RelatedProductLike {
  productId?: unknown
  productNo?: unknown
  title?: unknown
  price?: unknown
  coverImageUrl?: unknown
  visible?: unknown
  status?: unknown
  auditState?: unknown
  sellerVideoVerified?: unknown
}

export interface ComposeSelectableRelatedProduct {
  productId: number
  productNo: string
  title: string
  price: string
  coverImageUrl: string | null
}

export const communityImageStoragePrefix = '/uploads/community-image/'
const productImageStoragePrefix = '/uploads/product-image/'

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
  const isH5BlobPath = lower.startsWith('blob:')
  return !path ||
    path.startsWith('local://') ||
    path.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..') ||
    (!isH5BlobPath && path.includes('//'))
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

function hasInvalidProductImageUrl(url: unknown): boolean {
  if (typeof url !== 'string' || !url.startsWith(productImageStoragePrefix)) return true
  const lower = url.toLowerCase()
  const relativePath = url.slice(productImageStoragePrefix.length)
  return !relativePath ||
    url.startsWith('local://') ||
    url.startsWith('blob:') ||
    url.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('preview') ||
    lower.includes('demo') ||
    lower.includes('mock') ||
    lower.includes('sample') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    url.includes('\\') ||
    url.includes('..') ||
    url.includes('//') ||
    relativePath.split('/').some(segment => !segment)
}

export function validatedComposeProductCoverUrl(url: unknown): string | null {
  if (url == null || url === '') return null
  if (hasInvalidProductImageUrl(url)) {
    console.warn('community compose invalid product cover url isolated', { expectedPrefix: productImageStoragePrefix })
    return null
  }
  return (url as string).trim()
}

export function assertCreatedCommunityPost(value: CommunityCreateResultLike, expectedTopic: string): asserts value is { postNo: string; postId: number; topic: string; status: string } {
  if (!value || typeof value !== 'object') {
    throw new Error('发布响应异常，请刷新社区后确认')
  }
  if (!Number.isSafeInteger(value.postId) || Number(value.postId) <= 0) {
    throw new Error('发布响应缺少动态编号，请刷新社区后确认')
  }
  if (typeof value.postNo !== 'string' || !/^POST-[A-Za-z0-9-]{1,80}$/.test(value.postNo)) {
    throw new Error('发布响应缺少动态流水号，请刷新社区后确认')
  }
  if (value.topic !== expectedTopic) {
    throw new Error('发布响应话题异常，请刷新社区后确认')
  }
  if (typeof value.status !== 'string' || !value.status.trim()) {
    throw new Error('发布状态异常，请刷新社区后确认')
  }
}

export function inputValue(field: TextFieldKey, event: unknown): string | undefined {
  const value = (event as { detail?: { value?: unknown } } | null | undefined)?.detail?.value
  if (typeof value !== 'string') {
    console.warn('community compose input event invalid', { field })
    return undefined
  }
  return value
}

export function toSafeBackendId(value: string): number | null {
  if (!/^[1-9]\d{0,15}$/.test(value)) return null
  const numeric = Number(value)
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null
}

export function normalizeComposeProductPrice(value: unknown): string | null {
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return null
    const text = String(value)
    return /^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(text) ? text : null
  }
  if (typeof value !== 'string') return null
  const text = value.trim()
  return /^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(text) ? text : null
}

export function assertRelatedProductForCompose(value: unknown, expectedProductId: number): asserts value is { productId: number; title: string; price: string | number } {
  if (!value || typeof value !== 'object') {
    throw new Error('关联商品资料加载失败，已取消关联')
  }
  const product = value as RelatedProductLike
  if (!Number.isSafeInteger(product.productId) || product.productId !== expectedProductId) {
    throw new Error('关联商品编号校验失败，已取消关联')
  }
  if (typeof product.title !== 'string' || !product.title.trim()) {
    throw new Error('关联商品标题校验失败，已取消关联')
  }
  if (normalizeComposeProductPrice(product.price) === null) {
    throw new Error('关联商品价格校验失败，已取消关联')
  }
  if (product.visible !== true || product.status !== 'ACTIVE' || product.auditState !== 'APPROVED') {
    throw new Error('关联商品当前不可用，已取消关联')
  }
}

export function isSelectableRelatedProductForCompose(value: unknown): value is RelatedProductLike & {
  productId: number
  title: string
  price: string | number
  visible: true
  status: 'ACTIVE'
  auditState: 'APPROVED'
  sellerVideoVerified: true
} {
  if (!value || typeof value !== 'object') return false
  const product = value as RelatedProductLike
  return Number.isSafeInteger(product.productId) &&
    Number(product.productId) > 0 &&
    typeof product.title === 'string' &&
    !!product.title.trim() &&
    normalizeComposeProductPrice(product.price) !== null &&
    product.visible === true &&
    product.status === 'ACTIVE' &&
    product.auditState === 'APPROVED' &&
    product.sellerVideoVerified === true
}

export function selectableRelatedProductsForCompose(value: unknown): ComposeSelectableRelatedProduct[] {
  if (!Array.isArray(value)) {
    throw new Error('我的商品列表加载失败，请稍后重试')
  }
  return value.filter(isSelectableRelatedProductForCompose).map((product) => {
    const normalizedPrice = normalizeComposeProductPrice(product.price)
    if (normalizedPrice === null) throw new Error('关联商品价格校验失败，请刷新后重试')
    return {
      productId: product.productId,
      productNo: typeof product.productNo === 'string' ? product.productNo.trim() : '',
      title: product.title.trim(),
      price: normalizedPrice,
      coverImageUrl: validatedComposeProductCoverUrl(product.coverImageUrl)
    }
  })
}
