import { get } from '../http'

export type RankingGender = 'goddess' | 'god'

export interface UserRankingResponse {
  userId: number
  rank: number
  nickname: string
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

export function listUserRankings(gender: RankingGender, limit = 100) {
  return get<UserRankingResponse[]>('/api/user/rankings', { gender, limit })
}
