import { post } from '../http'

export type RegisterGender = 'god' | 'goddess'

export interface LoginRequest {
  mobile: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  gender: RegisterGender
}

export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
}

export function login(data: LoginRequest) {
  return post<AuthTokenResponse>('/api/auth/login', data)
}

export function register(data: RegisterRequest) {
  return post<AuthTokenResponse>('/api/auth/register', data)
}
