import type {
  CommunityCommentResponse,
  CommunityPostDetailResponse
} from '../../../api/modules/community'
import { isDefaultAvatarUrl } from '../../../utils/default-avatar'

export const launchReadinessMarkers = [
  '缺少后端作者ID，未执行任何关注变更',
  '关注状态没有提交成功，未执行本地关注变更'
]

export const communityImageStoragePrefix = '/uploads/community-image/'
export const avatarImageStoragePrefix = '/uploads/avatar/'
const validCommunityTopics = new Set(['生活日常', '闲置避坑', '交易经验', '求购心愿'])

export interface CommentItem { id: string; avatar: string; avatarUrl: string; name: string; text: string }

export function firstChar(value: string | undefined): string {
  return value?.trim()?.slice(0, 1) || '用'
}

export function formatDateTime(value: string | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '--'
}

export function isValidCommunityPostId(value: string): boolean {
  return toSafeBackendId(value) !== null
}

export function isValidCommunityCommentNo(value: string): boolean {
  return /^CMT-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(value)
}

export function isValidBackendProductId(value: string): boolean {
  return toSafeBackendId(value) !== null
}

export function toSafeBackendId(value: string): number | null {
  if (!/^[1-9]\d{0,15}$/.test(value)) return null
  const numeric = Number(value)
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null
}

export function isValidBackendUserId(value: number | null): boolean {
  return typeof value === 'number' && Number.isInteger(value) && value > 0
}

export function assertCommunityPostDetail(value: unknown, expectedPostId: number): asserts value is CommunityPostDetailResponse {
  if (!value || typeof value !== 'object') throw new Error('community detail invalid backend response')
  const detail = value as CommunityPostDetailResponse
  if (!Number.isSafeInteger(detail.postId) || detail.postId <= 0) throw new Error('community detail invalid postId')
  if (detail.postId !== expectedPostId) throw new Error('community detail postId mismatch')
  if (typeof detail.postNo !== 'string' || !/^POST-[A-Za-z0-9-]{1,80}$/.test(detail.postNo)) throw new Error('community detail invalid postNo')
  if (!isValidBackendUserId(detail.authorId)) throw new Error('community detail invalid authorId')
  if (typeof detail.title !== 'string' || !detail.title.trim()) throw new Error('community detail invalid title')
  if (typeof detail.content !== 'string' || !detail.content.trim()) throw new Error('community detail invalid content')
  if (typeof detail.topic !== 'string' || !validCommunityTopics.has(detail.topic)) throw new Error('community detail invalid topic')
  if (detail.status !== 'PUBLISHED') throw new Error('community detail invalid status')
  if (!Number.isSafeInteger(detail.likeCount) || detail.likeCount < 0) throw new Error('community detail invalid likeCount')
  if (!Number.isSafeInteger(detail.commentCount) || detail.commentCount < 0) throw new Error('community detail invalid commentCount')
  if (typeof detail.likedByMe !== 'boolean') throw new Error('community detail invalid liked state')
  if (typeof detail.createdAt !== 'string' || !detail.createdAt.trim()) throw new Error('community detail invalid createdAt')
  detail.imageUrls = sanitizeCommunityDetailImageUrls(detail.imageUrls, detail.postId)
  if (detail.authorName != null && typeof detail.authorName !== 'string') throw new Error('community detail invalid authorName')
  if (detail.authorAvatar != null && typeof detail.authorAvatar !== 'string') throw new Error('community detail invalid authorAvatar')
  if (detail.city != null && typeof detail.city !== 'string') throw new Error('community detail invalid city')
  if (detail.relatedProductId != null && !isValidBackendProductId(String(detail.relatedProductId))) throw new Error('community detail invalid relatedProductId')
  if (detail.relatedProductTitle != null && typeof detail.relatedProductTitle !== 'string') throw new Error('community detail invalid relatedProductTitle')
  if (detail.relatedProductPrice != null && typeof detail.relatedProductPrice !== 'string' && typeof detail.relatedProductPrice !== 'number') throw new Error('community detail invalid relatedProductPrice')
  if (!Array.isArray(detail.comments)) throw new Error('community detail invalid comments')
  for (const comment of detail.comments) assertCommunityCommentResponse(comment)
}

export function assertCommunityCommentResponse(value: unknown): asserts value is CommunityCommentResponse {
  if (!value || typeof value !== 'object') throw new Error('community comment invalid backend response')
  const comment = value as CommunityCommentResponse
  if (typeof comment.commentNo !== 'string' || !/^CMT-[A-Za-z0-9-]{1,80}$/.test(comment.commentNo)) throw new Error('community comment invalid commentNo')
  if (!isValidBackendUserId(comment.authorId)) throw new Error('community comment invalid authorId')
  if (comment.authorName != null && typeof comment.authorName !== 'string') throw new Error('community comment invalid authorName')
  if (comment.authorAvatar != null && typeof comment.authorAvatar !== 'string') throw new Error('community comment invalid authorAvatar')
  if (typeof comment.content !== 'string' || !comment.content.trim()) throw new Error('community comment invalid content')
  if (typeof comment.createdAt !== 'string' || !comment.createdAt.trim()) throw new Error('community comment invalid createdAt')
}

export function validatedCommunityImageUrl(url: unknown): string {
  return validatedStoredImageUrl(url, [communityImageStoragePrefix])
}

export function validatedCommunityAvatarUrl(url: unknown): string {
  return validatedStoredImageUrl(url, [avatarImageStoragePrefix, communityImageStoragePrefix])
}

export function sanitizeCommunityDetailImageUrls(imageUrls: unknown, postId: number): string[] {
  if (!Array.isArray(imageUrls)) throw new Error('community detail invalid image urls')
  const validUrls: string[] = []
  let isolatedCount = 0
  for (const url of imageUrls) {
    if (typeof url !== 'string') {
      isolatedCount += 1
      continue
    }
    const validUrl = validatedCommunityImageUrl(url)
    if (validUrl) validUrls.push(validUrl)
    else isolatedCount += 1
  }
  if (isolatedCount > 0) {
    console.warn('community detail invalid image isolated', { postId, isolatedCount })
  }
  return validUrls
}

function validatedStoredImageUrl(url: unknown, storagePrefixes: string[]): string {
  if (typeof url !== 'string') return ''
  if (isDefaultAvatarUrl(url)) return url
  const storagePrefix = storagePrefixes.find((prefix) => url.startsWith(prefix))
  if (!storagePrefix) return ''
  const lower = url.toLowerCase()
  const relativePath = url.slice(storagePrefix.length)
  const hasInvalidPath = !relativePath ||
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
    relativePath.split('/').some((segment) => !segment)
  return hasInvalidPath ? '' : url
}

export function inputValue(event: unknown): string | undefined {
  const target = (event as { detail?: { value?: unknown }; target?: { value?: unknown } }) || {}
  const value = target.detail?.value ?? target.target?.value
  return typeof value === 'string' ? value : undefined
}
