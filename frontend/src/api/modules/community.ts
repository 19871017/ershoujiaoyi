import { del, get, post, put } from '../http'

export const COMMUNITY_TOPICS = ['生活日常', '闲置避坑', '交易经验', '求购心愿'] as const
export type CommunityTopic = typeof COMMUNITY_TOPICS[number]

export interface CreateCommunityPostRequest {
  title: string
  topic: CommunityTopic | string
  content: string
  imageUrls: string[]
  relatedProductId?: number | null
}

export interface CommunityPostResponse {
  postNo: string
  postId: number
  authorId: number
  title: string
  topic: string
  content: string
  imageUrls: string[]
  status: string
  likeCount: number
  commentCount: number
  likedByMe: boolean
  followedByMe: boolean
  createdAt: string
  authorName?: string
  authorAvatar?: string
  city?: string
  ipLocation?: string
  relatedProductId?: number | null
  relatedProductTitle?: string | null
  relatedProductPrice?: string | number | null
}

export interface CommunityPostPageResponse {
  posts: CommunityPostResponse[]
  nextCursor: string | null
  hasMore: boolean
}

export interface CommunityPostPageQuery {
  limit?: number
  topic?: CommunityTopic | string
  cursor?: string | null
}

export interface CommunityCommentResponse {
  commentNo: string
  authorId: number
  authorName?: string
  authorAvatar?: string
  content: string
  createdAt: string
}

export interface CommunityPostDetailResponse extends CommunityPostResponse {
  likedByMe: boolean
  comments: CommunityCommentResponse[]
}

export function listCommunityPosts(limit = 20, topic?: CommunityTopic | string) {
  const query: { limit: number; topic?: CommunityTopic } = { limit }
  if (topic !== undefined && topic !== null && String(topic).trim()) {
    query.topic = normalizeCommunityTopic(topic)
  }
  return get<CommunityPostResponse[]>('/api/community/posts', query)
}

export function listCommunityPostPage(query: CommunityPostPageQuery = {}) {
  const params: { limit: number; topic?: CommunityTopic; cursor?: string } = {
    limit: normalizeCommunityLimit(query.limit ?? 20)
  }
  if (query.topic !== undefined && query.topic !== null && String(query.topic).trim()) {
    params.topic = normalizeCommunityTopic(query.topic)
  }
  const cursor = normalizeCommunityCursor(query.cursor)
  if (cursor) {
    params.cursor = cursor
  }
  return get<CommunityPostPageResponse>('/api/community/posts/page', params)
}

export function getCommunityPostDetail(postId: string | number) {
  return get<CommunityPostDetailResponse>(`/api/community/posts/${postId}`)
}

export function createCommunityPost(data: CreateCommunityPostRequest) {
  return post<CommunityPostResponse>('/api/community/posts', { ...data, topic: normalizeCommunityTopic(data.topic) })
}

export function listMyCommunityPosts(limit = 20) {
  return get<CommunityPostResponse[]>('/api/community/posts/mine', { limit: normalizeCommunityLimit(limit) })
}

export function updateCommunityPost(postId: string | number, data: CreateCommunityPostRequest) {
  return put<CommunityPostDetailResponse>(`/api/community/posts/${normalizeCommunityPostId(postId)}`, { ...data, topic: normalizeCommunityTopic(data.topic) })
}

export function deleteCommunityPost(postId: string | number) {
  return del<CommunityPostDetailResponse>(`/api/community/posts/${normalizeCommunityPostId(postId)}`)
}

export function createCommunityComment(postId: number, content: string) {
  return post<CommunityCommentResponse>(`/api/community/posts/${postId}/comments`, { content })
}

export function likeCommunityPost(postId: number) {
  return post<CommunityPostDetailResponse>(`/api/community/posts/${postId}/likes`, {})
}

export function unlikeCommunityPost(postId: number) {
  return del<CommunityPostDetailResponse>(`/api/community/posts/${postId}/likes`)
}

export function isCommunityTopic(value: unknown): value is CommunityTopic {
  return typeof value === 'string' && COMMUNITY_TOPICS.includes(value.trim() as CommunityTopic)
}

function normalizeCommunityTopic(value: unknown): CommunityTopic {
  const topic = typeof value === 'string' ? value.trim() : ''
  if (!isCommunityTopic(topic)) {
    throw new Error('请选择有效社区话题')
  }
  return topic
}

function normalizeCommunityLimit(value: unknown): number {
  const numeric = Number(value)
  if (!Number.isInteger(numeric) || numeric <= 0) {
    throw new Error('社区列表条数无效')
  }
  return Math.min(numeric, 50)
}

function normalizeCommunityPostId(value: unknown): number {
  const numeric = Number(value)
  if (!Number.isSafeInteger(numeric) || numeric <= 0) {
    throw new Error('动态编号无效')
  }
  return numeric
}

function normalizeCommunityCursor(value: unknown): string | undefined {
  if (value === undefined || value === null || String(value).trim() === '') {
    return undefined
  }
  const cursor = String(value).trim()
  const lower = cursor.toLowerCase()
  if (cursor.length > 160 ||
    lower.includes('preview') ||
    lower.includes('demo') ||
    lower.includes('mock') ||
    lower.includes('sample') ||
    lower.includes('placeholder') ||
    !/^[A-Za-z0-9_-]+$/.test(cursor)) {
    throw new Error('社区翻页游标无效')
  }
  return cursor
}
