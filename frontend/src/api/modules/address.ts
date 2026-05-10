import { del, get, post } from '../http'

export interface UserAddressResponse {
  addressId: number
  userId: number
  name: string
  mobile: string
  provinceCity: string
  detail: string
  isDefault: boolean
  createdAt?: string
  updatedAt?: string
}

export interface SaveUserAddressRequest {
  addressId?: number
  name: string
  mobile: string
  provinceCity: string
  detail: string
  isDefault: boolean
}

export function listAddresses() {
  return get<UserAddressResponse[]>('/api/user/addresses')
}

export function saveUserAddress(data: SaveUserAddressRequest) {
  return post<UserAddressResponse>('/api/user/addresses', data)
}

export function setDefaultUserAddress(addressId: number) {
  return post<UserAddressResponse>(`/api/user/addresses/${addressId}/default`)
}

export function deleteUserAddress(addressId: number) {
  return del<boolean>(`/api/user/addresses/${addressId}`)
}
