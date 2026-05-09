import { get, post } from '../http'

export interface LocationConfigResponse {
  provider: string
  enabled: boolean
  configured: boolean
  defaultCity: string
  defaultProvince: string
  coordinateType: string
  updatedAt: string
}

export interface ReverseGeocodeRequest {
  latitude: number
  longitude: number
}

export interface ReverseGeocodeResponse {
  provider: string
  province: string
  city: string
  district: string
  address: string
  latitude: number
  longitude: number
  fallback: boolean
}

export interface AdminUpdateLocationConfigRequest {
  provider?: 'BAIDU' | 'MANUAL'
  enabled?: boolean
  defaultCity?: string
  defaultProvince?: string
  coordinateType?: 'wgs84ll' | 'gcj02ll' | 'bd09ll'
  baiduAk?: string
}

export function getLocationConfig() {
  return get<LocationConfigResponse>('/api/location/config')
}

export function reverseGeocode(data: ReverseGeocodeRequest) {
  return post<ReverseGeocodeResponse>('/api/location/reverse-geocode', data)
}

export function getAdminLocationConfig(header?: Record<string, string>) {
  return get<LocationConfigResponse>('/api/admin/location/config', undefined, header)
}

export function updateAdminLocationConfig(data: AdminUpdateLocationConfigRequest, header?: Record<string, string>) {
  return post<LocationConfigResponse>('/api/admin/location/config', data, header)
}
