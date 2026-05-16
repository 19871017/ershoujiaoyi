import { get } from '../http'

export type RankingGender = 'goddess' | 'god'
export type RankingPeriod = 'day' | 'week' | 'all'

export interface UserRankingResponse {
  userId: number
  rank: number
  nickname: string
  avatarUrl?: string | null
  gender: RankingGender | string
  city?: string
  bio?: string
  mainRole?: string
  followerCount: number
  popularityScore: number
  safetyScore: number
  guardianScore: number
  giftScore?: number
  followedByMe: boolean
}

export function listUserRankings(gender: RankingGender, period: RankingPeriod = 'all', limit = 100) {
  return get<UserRankingResponse[]>('/api/user/rankings', { gender, period, limit })
}
