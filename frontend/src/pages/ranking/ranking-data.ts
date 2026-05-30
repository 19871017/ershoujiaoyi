import type { RankingGender, RankingPeriod, UserRankingResponse } from '../../api/modules/ranking'

export type Gender = RankingGender
export type Period = RankingPeriod

export interface RankingUser {
  id: number
  rank: number
  gender: Gender
  avatar: string
  avatarUrl: string
  name: string
  bio: string
  city: string
  giftScore: number
  viewerFollows: boolean
}

export const periodTabs = [
  { value: 'day' as const, label: '日榜' },
  { value: 'week' as const, label: '周榜' },
  { value: 'all' as const, label: '总榜' }
]

export function toRankingUser(item: UserRankingResponse): RankingUser {
  return {
    id: item.userId,
    rank: item.rank,
    gender: item.gender === 'god' ? 'god' : 'goddess',
    avatar: (item.nickname || '圈').slice(0, 1),
    avatarUrl: item.avatarUrl || '',
    name: item.nickname || '平台用户',
    bio: item.bio || '这个用户还没有填写个人介绍',
    city: item.city || '全部',
    giftScore: item.giftScore ?? item.popularityScore,
    viewerFollows: item.followedByMe
  }
}

export function isRankingTab(value: unknown): value is Gender {
  return value === 'god' || value === 'goddess'
}

export function isRankingPeriod(value: unknown): value is Period {
  return value === 'day' || value === 'week' || value === 'all'
}
