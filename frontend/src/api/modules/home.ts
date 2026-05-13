import { get } from '../http'

export type HomeBannerAction = 'closet' | 'ranking' | 'forum' | 'search' | 'none'

export interface HomeBannerResponse {
  id: number
  kicker: string
  title: string
  description: string
  cta: string
  imageUrl: string
  action: HomeBannerAction
  sortOrder: number
  enabled: boolean
  sizeHint: string
  updatedAt?: string
}

export function getHomeBanners() {
  return get<HomeBannerResponse[]>('/api/home/banners')
}
