import { useUserStore } from '../store/modules/user'

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

export interface ApiResult<T> {
  success: boolean
  message: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true'
const ENABLE_DEV_HEADERS = import.meta.env.VITE_ENABLE_DEV_HEADERS === 'true'
const ENABLE_LAN_API_FALLBACK = import.meta.env.VITE_ENABLE_LAN_API_FALLBACK === 'true'
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '1'

function isLanHost(host: string) {
  return /^(10|172\.(1[6-9]|2\d|3[0-1])|192\.168)\.\d{1,3}\.\d{1,3}$/.test(host)
}

function resolveApiBaseUrl() {
  if (API_BASE_URL.trim()) return API_BASE_URL.trim().replace(/\/$/, '')
  if (!ENABLE_LAN_API_FALLBACK || typeof window === 'undefined' || !window.location?.hostname) return ''
  const host = window.location.hostname
  if (!host || host === 'localhost' || host === '127.0.0.1') return ''
  if (!isLanHost(host)) return ''
  return `http://${host}:18080`
}

const RESOLVED_API_BASE_URL = resolveApiBaseUrl()

const DEV_HEADERS: Record<string, string> = ENABLE_DEV_HEADERS
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

function mockResponse<T>(url: string): T | undefined {
  if (!ENABLE_MOCK_DATA) return undefined

  const products = [
    {
      productId: 9001,
      productNo: 'MOCK-DRESS-001',
      title: '奶油白法式连衣裙 只穿过一次',
      description: '温柔法式小裙子，拍照穿过一次，面料舒服，适合约会和周末出门。',
      price: '129.00',
      coverImageUrl: '',
      imageUrls: [],
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      tradeRule: '交易状态以平台订单、支付和售后记录为准',
      createdAt: '2026-05-07T10:00:00'
    },
    {
      productId: 9002,
      productNo: 'MOCK-SHOES-002',
      title: '小香风玛丽珍鞋 37码',
      description: '低跟好走，通勤约会都可以，鞋底轻微痕迹，介意勿拍。',
      price: '88.00',
      coverImageUrl: '',
      imageUrls: [],
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      tradeRule: '交付偏好以平台商品与订单记录为准',
      createdAt: '2026-05-07T09:30:00'
    },
    {
      productId: 9003,
      productNo: 'MOCK-SOCKS-003',
      title: '蝴蝶结长袜三双装 未拆封',
      description: '买多了出，全新未拆，浅色系，很适合裙子和小皮鞋。',
      price: '29.00',
      coverImageUrl: '',
      imageUrls: [],
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      tradeRule: '发货、收货和售后状态以平台记录为准',
      createdAt: '2026-05-06T21:10:00'
    },
    {
      productId: 9004,
      productNo: 'MOCK-BAG-004',
      title: '粉色腋下包 轻微使用痕迹',
      description: '原酷粉色小包，容量可放手机口红纸巾，背过几次。',
      price: '66.00',
      coverImageUrl: '',
      imageUrls: [],
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      tradeRule: '聊天记录以平台会话为准',
      createdAt: '2026-05-06T18:30:00'
    }
  ]

  if (url === '/api/products') return products as T
  if (url === '/api/home/banners') {
    return [
      {
        id: 1,
        kicker: '小原圈 · 今日新鲜',
        title: '把心爱闲置交给懂它的人',
        description: '附近好物、日常分享、圈内互动，一屏逛完。',
        cta: '去发现',
        imageUrl: '/uploads/home/banner-closet.svg',
        action: 'closet',
        sortOrder: 10,
        enabled: true,
        sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。',
        updatedAt: new Date().toISOString()
      },
      {
        id: 2,
        kicker: '礼物积分上升',
        title: '男神女神礼物榜',
        description: '1 元礼物 = 1 分，按礼物积分看榜单。',
        cta: '去看看',
        imageUrl: '/uploads/home/banner-ranking.svg',
        action: 'ranking',
        sortOrder: 20,
        enabled: true,
        sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。',
        updatedAt: new Date().toISOString()
      },
      {
        id: 3,
        kicker: '日常生活频道',
        title: '分享今天的小确幸',
        description: '校园、寝室、城市日常，都可以轻松聊。',
        cta: '去社区',
        imageUrl: '/uploads/home/banner-community.svg',
        action: 'forum',
        sortOrder: 30,
        enabled: true,
        sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。',
        updatedAt: new Date().toISOString()
      }
    ] as T
  }
  if (url === '/api/location/config') {
    return {
      provider: 'BAIDU',
      enabled: true,
      configured: false,
      defaultCity: '请选择城市',
      defaultProvince: '',
      coordinateType: 'wgs84ll',
      updatedAt: new Date().toISOString()
    } as T
  }
  const productMatch = url.match(/^\/api\/products\/(\d+)/)
  if (productMatch) {
    const product = products.find((item) => item.productId === Number(productMatch[1])) ?? products[0]
    return product as T
  }
  return undefined
}

export function isDevRuntimeEnabled() {
  return ENABLE_DEV_HEADERS
}

function authHeaders(): Record<string, string> {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function request<T = unknown>(options: HttpOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const requestUrl = method === 'GET' ? appendQuery(options.url, options.data) : options.url
  const mocked = mockResponse<T>(requestUrl)
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

export function put<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'PUT', data, header })
}

export function del<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'DELETE', data, header })
}
