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

export function getLocationConfig() {
  return get<LocationConfigResponse>('/api/location/config')
}

export function reverseGeocode(data: ReverseGeocodeRequest) {
  return post<ReverseGeocodeResponse>('/api/location/reverse-geocode', data)
}
