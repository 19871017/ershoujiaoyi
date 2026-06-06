import { useUserStore } from '../store/modules/user'
import { mockResponse } from './mock-data'

export interface HttpOptions {
  url: string
  method?: UniApp.RequestOptions['method']
  data?: unknown
  header?: Record<string, string>
}

export interface UploadOptions {
  url: string
  filePath: string
  name?: string
  formData?: Record<string, string>
  header?: Record<string, string>
}

export interface UploadBlobOptions {
  url: string
  blob: Blob
  filename: string
  name?: string
  formData?: Record<string, string>
  header?: Record<string, string>
}

export interface ApiResult<T> {
  success: boolean
  message: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const ENABLE_DEV_HEADERS = import.meta.env.VITE_ENABLE_DEV_HEADERS === 'true'
const ENABLE_DEV_RUNTIME = ENABLE_DEV_HEADERS && isLocalDevRuntimeHost()
const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true' && ENABLE_DEV_RUNTIME
const ENABLE_LAN_API_FALLBACK = import.meta.env.VITE_ENABLE_LAN_API_FALLBACK === 'true' && ENABLE_DEV_RUNTIME
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '1'

function isLanHost(host: string) {
  return /^(10|172\.(1[6-9]|2\d|3[0-1])|192\.168)\.\d{1,3}\.\d{1,3}$/.test(host)
}

function isLocalDevRuntimeHost() {
  if (typeof window === 'undefined' || !window.location?.hostname) return false
  const host = window.location.hostname
  return host === 'localhost' || host === '127.0.0.1' || host === '::1' || isLanHost(host)
}

function resolveApiBaseUrl() {
  if (API_BASE_URL.trim()) return API_BASE_URL.trim().replace(/\/$/, '')
  if (!ENABLE_LAN_API_FALLBACK || typeof window === 'undefined' || !window.location?.hostname) return ''
  const host = window.location.hostname
  if (!isLanHost(host)) return ''
  return `http://${host}:18080`
}

const RESOLVED_API_BASE_URL = resolveApiBaseUrl()

const DEV_HEADERS: Record<string, string> = ENABLE_DEV_RUNTIME
  ? { 'X-User-Id': DEV_USER_ID, 'X-Dev-Mode': 'enabled' }
  : {}

function isApiResult<T>(data: unknown): data is ApiResult<T> {
  return Boolean(data && typeof data === 'object' && 'success' in data && 'message' in data && 'data' in data)
}

function toError(message: unknown, fallback: string) {
  return new Error(typeof message === 'string' && message.trim() ? message : fallback)
}

function appendQuery(url: string, data?: unknown) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return url
  const params = new URLSearchParams()
  Object.entries(data as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    params.set(key, String(value))
  })
  const query = params.toString()
  if (!query) return url
  return `${url}${url.includes('?') ? '&' : '?'}${query}`
}

export function isDevRuntimeEnabled() {
  return ENABLE_DEV_RUNTIME
}

export function devRuntimeUserId() {
  return DEV_USER_ID
}

export function resolveBackendMediaUrl(url?: string | null) {
  if (!url) return ''
  if (!url.startsWith('/uploads/')) return url
  return RESOLVED_API_BASE_URL ? `${RESOLVED_API_BASE_URL}${url}` : url
}

function authHeaders(): Record<string, string> {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function request<T = unknown>(options: HttpOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const requestUrl = method === 'GET' ? appendQuery(options.url, options.data) : options.url
  const mocked = ENABLE_MOCK_DATA ? mockResponse<T>(requestUrl, method, options.data) : undefined
  if (mocked !== undefined) {
    return Promise.resolve(mocked)
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${RESOLVED_API_BASE_URL}${requestUrl}`,
      method,
      data: method === 'GET' ? {} : options.data ?? {},
      header: { ...DEV_HEADERS, ...authHeaders(), ...(options.header ?? {}) },
      success: (res: UniApp.RequestSuccessCallbackResult) => {
        const result = res.data

        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(toError(isApiResult<T>(result) ? result.message : undefined, `HTTP ${res.statusCode}`))
          return
        }

        if (!isApiResult<T>(result)) {
          reject(toError(undefined, 'API response invalid'))
          return
        }

        if (!result.success) {
          reject(toError(result.message, 'API request failed'))
          return
        }

        resolve(result.data)
      },
      fail: reject
    })
  })
}

export function get<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'GET', data, header })
}

export function post<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'POST', data, header })
}

type UploadFileResult = { statusCode: number; data: unknown }
type UploadFileClient = {
  uploadFile(options: UploadOptions & {
    success: (res: UploadFileResult) => void
    fail: (error: unknown) => void
  }): void
}

export function upload<T = unknown>(options: UploadOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    ;(uni as unknown as UploadFileClient).uploadFile({
      url: `${RESOLVED_API_BASE_URL}${options.url}`,
      filePath: options.filePath,
      name: options.name ?? 'file',
      formData: options.formData ?? {},
      header: { ...DEV_HEADERS, ...authHeaders(), ...(options.header ?? {}) },
      success: (res) => {
        let result: unknown
        try {
          result = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        } catch {
          result = undefined
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(toError(isApiResult<T>(result) ? result.message : undefined, `上传失败：HTTP ${res.statusCode}`))
          return
        }
        if (!isApiResult<T>(result)) {
          reject(toError(undefined, '上传响应格式异常，请稍后重试'))
          return
        }
        if (!result.success) {
          reject(toError(result.message, '上传失败，请重新选择视频后再试'))
          return
        }
        resolve(result.data)
      },
      fail: reject
    })
  })
}

export async function uploadBlob<T = unknown>(options: UploadBlobOptions): Promise<T> {
  if (typeof fetch !== 'function' || typeof FormData === 'undefined') {
    throw new Error('当前环境不支持语音文件上传')
  }
  const formData = new FormData()
  Object.entries(options.formData ?? {}).forEach(([key, value]) => formData.append(key, value))
  formData.append(options.name ?? 'file', options.blob, options.filename)
  const response = await fetch(`${RESOLVED_API_BASE_URL}${options.url}`, {
    method: 'POST',
    headers: { ...DEV_HEADERS, ...authHeaders(), ...(options.header ?? {}) },
    body: formData
  })
  let result: unknown
  try {
    result = await response.json()
  } catch {
    result = undefined
  }
  if (!response.ok) {
    throw toError(isApiResult<T>(result) ? result.message : undefined, `上传失败：HTTP ${response.status}`)
  }
  if (!isApiResult<T>(result)) {
    throw toError(undefined, '上传响应格式异常，请稍后重试')
  }
  if (!result.success) {
    throw toError(result.message, '上传失败，请重新录制语音后再试')
  }
  return result.data
}

export function put<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'PUT', data, header })
}

export function del<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'DELETE', data, header })
}
