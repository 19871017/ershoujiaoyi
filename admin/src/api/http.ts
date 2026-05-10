export interface HttpOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: unknown
  headers?: Record<string, string>
}

export interface ApiEnvelope<T> {
  code?: number
  message?: string
  data?: T
}

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:18080'
type AdminHeaderProvider = () => Record<string, string>
let adminHeaderProvider: AdminHeaderProvider | null = null

export class HttpError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export function setAdminHeaderProvider(provider: AdminHeaderProvider | null) {
  adminHeaderProvider = provider
}

function resolveAdminHeaders(): Record<string, string> {
  const headers = adminHeaderProvider?.() ?? {}
  const userId = headers['X-User-Id'] || ''
  if (!/^[1-9]\d*$/.test(userId)) {
    throw new HttpError('管理员会话无效，请重新登录', 401)
  }
  return { 'X-User-Id': userId }
}

function filterRequestHeaders(headers?: Record<string, string>): Record<string, string> {
  const filtered: Record<string, string> = {}
  for (const [key, value] of Object.entries(headers || {})) {
    const normalized = key.toLowerCase()
    if (normalized === 'x-user-id' || normalized === 'x-admin-mode' || normalized === 'x-dev-mode') continue
    filtered[key] = value
  }
  return filtered
}

export async function request<T = unknown>(options: HttpOptions): Promise<T> {
  const adminHeaders = resolveAdminHeaders()
  const response = await fetch(`${API_BASE}${options.url}`, {
    method: options.method || 'GET',
    headers: {
      ...filterRequestHeaders(options.headers),
      'Content-Type': 'application/json',
      ...adminHeaders
    },
    body: options.data === undefined ? undefined : JSON.stringify(options.data),
    credentials: 'include'
  })

  if (!response.ok) {
    throw new HttpError(`后台接口请求失败(${response.status})`, response.status)
  }

  const payload = (await response.json()) as ApiEnvelope<T> | T
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return (payload as ApiEnvelope<T>).data as T
  }
  return payload as T
}
