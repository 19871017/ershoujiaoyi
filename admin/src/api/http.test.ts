import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request, requestBlob, setAdminHeaderProvider } from './http'

describe('admin http client', () => {
  beforeEach(() => {
    setAdminHeaderProvider(null)
    vi.restoreAllMocks()
  })

  it('adds only the resolved operator identity from the auth provider', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: 'ok' }) })
    vi.stubGlobal('fetch', fetchMock)
    setAdminHeaderProvider(() => ({
      'X-Admin-Mode': 'enabled',
      'X-User-Id': '7',
      'X-Admin-Session': 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      'X-Dev-Mode': 'enabled'
    }))

    const result = await request<string>({ url: '/api/admin/dashboard' })

    expect(result).toBe('ok')
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/dashboard'), expect.objectContaining({
      headers: expect.objectContaining({
        'Content-Type': 'application/json',
        'X-User-Id': '7',
        'X-Admin-Session': 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
      })
    }))
    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers).not.toHaveProperty('X-Admin-Mode')
    expect(init.headers).not.toHaveProperty('X-Dev-Mode')
  })

  it('does not allow per-request headers to override the resolved operator identity', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: 'ok' }) })
    vi.stubGlobal('fetch', fetchMock)
    setAdminHeaderProvider(() => ({ 'X-User-Id': '7', 'X-Admin-Session': 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' }))

    await request<string>({
      url: '/api/admin/users',
      headers: {
        'X-User-Id': '999',
        'X-Admin-Session': 'adm_badbadbadbadbadbadbadbadbadbadba',
        'X-Admin-Mode': 'enabled',
        'X-Dev-Mode': 'enabled'
      }
    })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-User-Id': '7',
      'X-Admin-Session': 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    })
    expect(init.headers).not.toHaveProperty('X-Admin-Mode')
    expect(init.headers).not.toHaveProperty('X-Dev-Mode')
    expect(init.headers).not.toMatchObject({ 'X-Admin-Session': 'adm_badbadbadbadbadbadbadbadbadbadba' })
  })

  it('fails closed before fetch when no admin headers are available', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(request({ url: '/api/admin/audit' })).rejects.toThrow('管理员会话无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('fetches protected blobs with resolved admin headers', async () => {
    const blob = new Blob(['voice'], { type: 'audio/webm' })
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: async () => blob })
    vi.stubGlobal('fetch', fetchMock)
    setAdminHeaderProvider(() => ({ 'X-User-Id': '7', 'X-Admin-Session': 'adm_cccccccccccccccccccccccccccccccc' }))

    const result = await requestBlob({
      url: '/api/admin/chat/media?url=%2Fuploads%2Fchat-voice%2F7%2Fa.webm',
      headers: {
        'X-User-Id': '999'
      }
    })

    expect(result).toBe(blob)
    const [, init] = fetchMock.mock.calls[0]
    expect(init).toMatchObject({
      method: 'GET',
      credentials: 'include'
    })
    expect(init.headers).toMatchObject({
      'X-User-Id': '7',
      'X-Admin-Session': 'adm_cccccccccccccccccccccccccccccccc'
    })
    expect(init.headers).not.toHaveProperty('Content-Type')
  })
})
