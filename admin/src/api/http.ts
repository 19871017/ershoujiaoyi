export interface HttpOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  data?: Record<string, unknown>
}

export async function request<T = unknown>(_options: HttpOptions): Promise<T> {
  return Promise.resolve({} as T)
}