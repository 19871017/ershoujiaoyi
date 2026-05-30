import { get } from '../http'

export type HomeBannerAction = 'closet' | 'ranking' | 'forum' | 'search' | 'none'
export type HomeBannerPlacement = 'HOME' | 'MERCHANT_SHOWCASE'

export interface HomeBannerResponse {
  id: number
  kicker: string
  title: string
  description: string
  cta: string
  imageUrl: string
  action: HomeBannerAction
  placement: HomeBannerPlacement
  sortOrder: number
  enabled: boolean
  sizeHint: string
  updatedAt?: string
}

export function getHomeBanners(): Promise<HomeBannerResponse[]> {
  return get<HomeBannerResponse[]>('/api/home/banners')
}

export function getMerchantShowcaseBanners(): Promise<HomeBannerResponse[]> {
  return get<HomeBannerResponse[]>('/api/home/merchant-showcase/banners')
}
