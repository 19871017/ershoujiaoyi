export interface HttpOptions {
  url: string
  method?: UniApp.RequestOptions['method']
  data?: unknown
  header?: Record<string, string>
}

export interface ApiResult<T> {
  success: boolean
  message: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const IS_PROD = import.meta.env.PROD
const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true' && !IS_PROD
const ENABLE_DEV_HEADERS = import.meta.env.VITE_ENABLE_DEV_HEADERS === 'true' && !IS_PROD
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '1'

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
      tradeRule: '交易状态以服务端订单、支付和售后记录为准',
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
      tradeRule: '交付偏好以服务端商品与订单记录为准',
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
      tradeRule: '发货、收货和售后状态以服务端记录为准',
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
      tradeRule: '聊天记录以服务端会话为准',
      createdAt: '2026-05-06T18:30:00'
    }
  ]

  if (url === '/api/products') return products as T
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

export function request<T = unknown>(options: HttpOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const requestUrl = method === 'GET' ? appendQuery(options.url, options.data) : options.url
  const mocked = mockResponse<T>(requestUrl)
  if (mocked !== undefined) {
    return Promise.resolve(mocked)
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${requestUrl}`,
      method,
      data: method === 'GET' ? {} : options.data ?? {},
      header: { ...DEV_HEADERS, ...(options.header ?? {}) },
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

export function put<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'PUT', data, header })
}

export function del<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'DELETE', data, header })
}
