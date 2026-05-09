import { get, post } from '../http'

export interface UserProfileResponse {
  userId: number
  nickname: string
  mainRole: string
  videoIdentityStatus: 'UNVERIFIED' | 'PENDING' | 'APPROVED' | 'REJECTED' | string
  videoVerified: boolean
  followedByMe?: boolean
}

export interface SubmitVideoIdentityRequest {
  videoUrl: string
  description?: string
}

export function getMyProfile() {
  return get<UserProfileResponse>('/api/user/me')
}

export function getPublicProfile(userId: number | string) {
  return get<UserProfileResponse>(`/api/user/${userId}/profile`)
}

export function followPublicProfile(userId: number | string) {
  return post<UserProfileResponse>(`/api/user/${encodeURIComponent(String(userId))}/follow`, {})
}

export function submitVideoIdentity(data: SubmitVideoIdentityRequest) {
  return post('/api/audit/video-identity', data)
}
