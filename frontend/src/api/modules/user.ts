import { del, get, post } from '../http'

export interface UserProfileResponse {
  userId: number
  nickname: string
  mainRole: string
  city?: string
  bio?: string
  videoIdentityStatus: 'UNVERIFIED' | 'PENDING' | 'APPROVED' | 'REJECTED' | string
  videoVerified: boolean
  followedByMe?: boolean
}

export interface SubmitVideoIdentityRequest {
  videoUrl: string
  description?: string
}

export interface UpdateUserProfileRequest {
  nickname: string
  mainRole: 'BUYER' | 'SELLER' | 'BOTH' | string
  city?: string
  bio?: string
}

export function getMyProfile() {
  return get<UserProfileResponse>('/api/user/me')
}

export function updateMyProfile(data: UpdateUserProfileRequest) {
  return post<UserProfileResponse>('/api/user/me/profile', data)
}

export function getPublicProfile(userId: number | string) {
  return get<UserProfileResponse>(`/api/user/${userId}/profile`)
}

export function followPublicProfile(userId: number | string) {
  return post<UserProfileResponse>(`/api/user/${encodeURIComponent(String(userId))}/follow`, {})
}

export function unfollowPublicProfile(userId: number | string) {
  return del<UserProfileResponse>(`/api/user/${encodeURIComponent(String(userId))}/follow`)
}

export function submitVideoIdentity(data: SubmitVideoIdentityRequest) {
  return post('/api/audit/video-identity', data)
}
