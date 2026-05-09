declare module '@vue/runtime-core' {
  interface ComponentCustomProperties {
    $api: Record<string, unknown>
  }
}

declare global {
  namespace UniApp {
    interface RequestSuccessCallbackResult {
      data: unknown
      statusCode: number
      header: Record<string, string>
    }

    interface RequestOptions {
      url: string
      method?: 'OPTIONS' | 'GET' | 'HEAD' | 'POST' | 'PUT' | 'DELETE' | 'TRACE' | 'CONNECT'
      data?: unknown
      header?: Record<string, string>
      success?: (result: RequestSuccessCallbackResult) => void
      fail?: (error: unknown) => void
    }
  }

  function getCurrentPages(): Array<{ options?: Record<string, string> }>

  interface ImportMetaEnv {
    readonly VITE_API_BASE_URL?: string
    readonly VITE_ENABLE_MOCK_DATA?: string
    readonly VITE_ENABLE_DEV_HEADERS?: string
    readonly VITE_ENABLE_LAN_API_FALLBACK?: string
    readonly VITE_DEV_USER_ID?: string
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv
  }

  interface Window {
    location: Location
  }

  const uni: {
    request(options: UniApp.RequestOptions): void
    navigateTo(options: { url: string }): void
    switchTab(options: { url: string }): void
    redirectTo(options: { url: string }): void
    navigateBack(options?: { delta?: number }): void
    showToast(options: { title: string; icon?: 'success' | 'error' | 'fail' | 'exception' | 'loading' | 'none'; duration?: number }): void
    showModal(options: { title?: string; content: string; showCancel?: boolean; confirmText?: string; cancelText?: string; success?: (result: { confirm: boolean; cancel: boolean }) => void }): void
    chooseImage(options: { count?: number; sizeType?: Array<'original' | 'compressed'>; sourceType?: Array<'album' | 'camera'>; success?: (result: { tempFilePaths: string[] }) => void; fail?: (error: unknown) => void }): void
    chooseVideo(options: { sourceType?: Array<'album' | 'camera'>; compressed?: boolean; maxDuration?: number; success?: (result: { tempFilePath: string; duration?: number; size?: number }) => void; fail?: (error: unknown) => void }): void
    getLocation(options: { type?: 'wgs84' | 'gcj02'; success?: (result: { latitude: number; longitude: number }) => void; fail?: (error: unknown) => void }): void
    getStorageSync(key: string): unknown
    setStorageSync(key: string, data: unknown): void
    removeStorageSync(key: string): void
    reLaunch(options: { url: string }): void
    showLoading(options: { title?: string; mask?: boolean }): void
    hideLoading(): void
  }
}

export {}
