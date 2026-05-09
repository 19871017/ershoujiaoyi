import { get, post } from '../http'

export interface CreateCommunityPostRequest {
  title: string
  topic: string
  content: string
  imageUrls: string[]
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
  createdAt: string
  authorName?: string
  authorAvatar?: string
  city?: string
  relatedProductId?: number | null
  relatedProductTitle?: string | null
  relatedProductPrice?: string | number | null
}

export interface CommunityCommentResponse {
  commentNo: string
  authorId: number
  content: string
  createdAt: string
}

export interface CommunityPostDetailResponse extends CommunityPostResponse {
  likedByMe: boolean
  comments: CommunityCommentResponse[]
}

export function listCommunityPosts(limit = 20) {
  return get<CommunityPostResponse[]>('/api/community/posts', { limit })
}

export function getCommunityPostDetail(postId: string | number) {
  return get<CommunityPostDetailResponse>(`/api/community/posts/${postId}`)
}

export function createCommunityPost(data: CreateCommunityPostRequest) {
  return post<CommunityPostResponse>('/api/community/posts', data)
}

export function createCommunityComment(postId: number, content: string) {
  return post<CommunityCommentResponse>(`/api/community/posts/${postId}/comments`, { content })
}

export function likeCommunityPost(postId: number) {
  return post<CommunityPostResponse>(`/api/community/posts/${postId}/likes`, {})
}
