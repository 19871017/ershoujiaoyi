import { request } from '../http'

export interface AnnouncementTickerResponse {
  enabled: boolean
  text: string
  icon: string
  targetUrl: string
  updatedAt?: string
}

export function getAnnouncementTicker() {
  return request<AnnouncementTickerResponse>({ url: '/api/announcements/ticker' })
}
