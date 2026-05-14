import { post } from '../http'

export interface LoginRequest {
  mobile: string
  password: string
}

export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
}

export function login(data: LoginRequest) {
  return post<AuthTokenResponse>('/api/auth/login', data)
}

export function register(data: LoginRequest) {
  return post<AuthTokenResponse>('/api/auth/register', data)
}
