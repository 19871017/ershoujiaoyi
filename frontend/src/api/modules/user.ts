import { del, get, post } from '../http'

export type UserGender = 'god' | 'goddess' | string

export interface UserProfileResponse {
  userId: number
  userNo?: string
  nickname: string
  avatarUrl?: string
  mainRole: string
  gender?: UserGender
  age?: number | null
  city?: string
  bio?: string
  identityStatus?: 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED' | string
  videoIdentityStatus: 'UNVERIFIED' | 'PENDING' | 'APPROVED' | 'REJECTED' | string
  videoVerified: boolean
  videoIdentityUrl?: string | null
  showcaseImageUrls?: string[]
  followedByMe?: boolean
  followerCount?: number
  followingCount?: number
  sellerCharmScore?: number
  buyerPowerScore?: number
  level?: {
    level: number
    title: string
    track: 'POWER' | 'CHARM' | string
    score: number
    currentLevelScore?: number | null
    nextLevelScore?: number | null
    progressPercent?: number | null
  } | null
}

export interface AccountSecurityResponse {
  userId: number
  maskedPhone: string
  securityScore: string
  recentDevices: Array<{
    deviceName: string
    loginAt: string
    city: string
    status: string
  }>
}

export interface SubmitVideoIdentityRequest {
  videoUrl: string
  description?: string
}

export interface SubmitRealNameIdentityRequest {
  realName: string
  idTail: string
}

export interface UpdateUserProfileRequest {
  nickname: string
  avatarUrl?: string
  mainRole?: string
  gender: 'god' | 'goddess' | string
  city?: string
  bio?: string
  showcaseImageUrls?: string[]
}

export interface UpdateUserNoRequest {
  userNo: string
}

export function getMyProfile() {
  return get<UserProfileResponse>('/api/user/me')
}

export function getAccountSecurity() {
  return get<AccountSecurityResponse>('/api/user/me/security')
}

export function updateMyProfile(data: UpdateUserProfileRequest) {
  return post<UserProfileResponse>('/api/user/me/profile', data)
}

export function updateMyUserNo(data: UpdateUserNoRequest) {
  return post<UserProfileResponse>('/api/user/me/user-no', data)
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

export function submitRealNameIdentity(data: SubmitRealNameIdentityRequest) {
  return post('/api/audit/real-name-identity', data)
}
