import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { request, setAdminHeaderProvider } from '../../api/http'
import {
  buildAdminHeaders,
  canReviewAfterSales,
  canReviewAudit,
  canReviewAuditRecord,
  canReviewFinance,
  createAdminAuthToken,
  dashboardActionsForSession,
  isAdminAuthenticated,
  loginAdminSession,
  logoutAdminSession,
  menuAllowsSession,
  normalizeAdminSession,
  refreshAdminSession,
  shouldRedirectToLogin,
  sessionAllowsPermission,
  useAuthStore
} from './auth'

const FUTURE_EXPIRES_AT = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()

describe('admin auth helpers', () => {
  it('logs in through backend admin session endpoint and persists returned permissions only', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          username: 'ops',
          userId: '7',
          permissions: ['audit:read', 'finance:read'],
          sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          expiresAt: FUTURE_EXPIRES_AT
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const session = await loginAdminSession('13900000071', 'admin-pass-71')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/session/login'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ mobile: '13900000071', password: 'admin-pass-71' })
    }))
    expect(session?.permissions).toEqual(['audit:read', 'finance:read'])
  })

  it('logs out by revoking the server-issued admin session header pair only', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true })
    vi.stubGlobal('fetch', fetchMock)
    const session = normalizeAdminSession({ username: 'ops', userId: '7', permissions: ['audit:read'], sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', expiresAt: FUTURE_EXPIRES_AT })

    await logoutAdminSession(session)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/session/logout'), expect.objectContaining({
      method: 'POST',
      headers: {
        'X-User-Id': '7',
        'X-Admin-Session': 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
      }
    }))
  })

  it('refreshes persisted admin session through backend session/me before restoring headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          username: 'ops',
          userId: '7',
          permissions: ['order:read'],
          sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          expiresAt: FUTURE_EXPIRES_AT
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)
    const stale = normalizeAdminSession({ username: 'ops', userId: '7', permissions: ['audit:read'], sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', expiresAt: FUTURE_EXPIRES_AT })

    const refreshed = await refreshAdminSession(stale)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/session/me'), expect.objectContaining({
      method: 'GET',
      headers: {
        'X-User-Id': '7',
        'X-Admin-Session': 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
      }
    }))
    expect(refreshed?.permissions).toEqual(['order:read'])
  })

  it('fails closed and clears header provider when backend session/me rejects persisted admin session', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 403, json: async () => ({ message: 'forbidden' }) })
    vi.stubGlobal('fetch', fetchMock)
    const stale = normalizeAdminSession({ username: 'ops', userId: '7', permissions: ['audit:read'], sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', expiresAt: FUTURE_EXPIRES_AT })
    setAdminHeaderProvider(() => buildAdminHeaders(stale))

    await expect(refreshAdminSession(stale)).rejects.toThrow('后台会话已失效')
    await expect(request({ url: '/api/admin/dashboard' })).rejects.toThrow('管理员会话无效')
  })

  it('clears the shared admin HTTP header provider after logout so later protected requests fail closed', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: 'ok' }) })
    vi.stubGlobal('fetch', fetchMock)
    const session = normalizeAdminSession({
      username: 'ops',
      userId: '7',
      permissions: ['audit:read'],
      sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      expiresAt: FUTURE_EXPIRES_AT
    })
    setAdminHeaderProvider(() => buildAdminHeaders(session))

    await request({ url: '/api/admin/dashboard' })
    await logoutAdminSession(session)

    await expect(request({ url: '/api/admin/dashboard' })).rejects.toThrow('管理员会话无效')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('rejects backend admin sessions that omit an explicit permissions array', () => {
    const session = normalizeAdminSession({ username: 'legacy-admin', userId: '7', devAdminEnabled: true })

    expect(session).toBeNull()
    expect(buildAdminHeaders(session)).toEqual({})
  })

  it('rejects backend admin sessions that still depend on the dev-admin compatibility flag', () => {
    const session = normalizeAdminSession({ username: 'legacy-dev-admin', userId: '7', devAdminEnabled: true, permissions: ['audit:read'] })

    expect(session).toBeNull()
    expect(buildAdminHeaders(session)).toEqual({})
  })

  it('normalizes a persisted admin session into non-sensitive request headers', () => {
    const session = normalizeAdminSession({ username: ' ops ', userId: '7', permissions: ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log'], sessionId: 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', expiresAt: FUTURE_EXPIRES_AT })

    expect(session).toEqual({ username: 'ops', userId: '7', permissions: ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log'], sessionId: 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', expiresAt: FUTURE_EXPIRES_AT })
    expect(buildAdminHeaders(session)).toEqual({
      'X-User-Id': '7',
      'X-Admin-Session': 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    })
  })

  it('rejects invalid user ids so admin calls fail closed', () => {
    expect(normalizeAdminSession({ username: 'ops', userId: 'preview', devAdminEnabled: true })).toBeNull()
    expect(buildAdminHeaders(null)).toEqual({})
  })

  it('redirects protected routes to login when no valid session exists', () => {
    expect(shouldRedirectToLogin('/dashboard', null)).toBe(true)
    expect(shouldRedirectToLogin('/login', null)).toBe(false)
  })

  it('keeps role/permission checks fail closed for unknown or missing permissions', () => {
    const session = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      permissions: ['audit:read', 'unknown:root'],
      sessionId: 'adm_cccccccccccccccccccccccccccccccc',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(session?.permissions).toEqual(['audit:read'])
    expect(sessionAllowsPermission(session, 'audit:read')).toBe(true)
    expect(sessionAllowsPermission(session, 'finance:read')).toBe(false)
    expect(sessionAllowsPermission(session, 'system:config')).toBe(false)
    expect(sessionAllowsPermission(null, 'audit:read')).toBe(false)
  })

  it('filters admin layout menus by explicit permissions and hides restricted modules fail closed', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      permissions: ['audit:read'],
      sessionId: 'adm_dddddddddddddddddddddddddddddddd',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(menuAllowsSession({ path: '/audit', label: '审核工作台', permission: 'audit:read' }, auditOnly)).toBe(true)
    expect(menuAllowsSession({ path: '/finance/withdrawals', label: '提现审核', permission: 'finance:read' }, auditOnly)).toBe(false)
    expect(menuAllowsSession({ path: '/system/location', label: '位置配置', permission: 'system:config' }, null)).toBe(false)
  })

  it('maps protected admin routes to explicit permissions and blocks deep links without permission', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      permissions: ['audit:read'],
      sessionId: 'adm_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(shouldRedirectToLogin('/audit/AU-20260510-0001', auditOnly)).toBe(false)
    expect(shouldRedirectToLogin('/users', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users/8331', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users', normalizeAdminSession({ username: 'user-admin', userId: '9', permissions: ['user:read'], sessionId: 'adm_ffffffffffffffffffffffffffffffff', expiresAt: FUTURE_EXPIRES_AT }))).toBe(false)
    expect(shouldRedirectToLogin('/finance/withdrawals', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/system/location', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/audit-logs', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/finance/withdrawals', null)).toBe(true)
  })

  it('requires explicit user-read permission for user detail deep links', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      permissions: ['audit:read'],
      sessionId: 'adm_11111111111111111111111111111111',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const userOnly = normalizeAdminSession({
      username: 'user-admin',
      userId: '9',
      permissions: ['user:read'],
      sessionId: 'adm_22222222222222222222222222222222',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(shouldRedirectToLogin('/users', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users/8331', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users', userOnly)).toBe(false)
    expect(shouldRedirectToLogin('/users/8331', userOnly)).toBe(false)
  })

  it('allows every valid admin session to open dashboard because metrics are already permission-filtered by backend', () => {
    const financeOnly = normalizeAdminSession({
      username: 'finance-admin',
      userId: '14',
      permissions: ['finance:read'],
      sessionId: 'adm_88888888888888888888888888888888',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(shouldRedirectToLogin('/dashboard', financeOnly)).toBe(false)
  })

  it('shows dashboard menu for any valid admin session because dashboard requires session only', () => {
    const financeOnly = normalizeAdminSession({
      username: 'finance-admin',
      userId: '14',
      permissions: ['finance:read'],
      sessionId: 'adm_99999999999999999999999999999999',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(menuAllowsSession({ path: '/dashboard', label: '仪表盘', permission: null }, financeOnly)).toBe(true)
    expect(menuAllowsSession({ path: '/dashboard', label: '仪表盘', permission: null }, null)).toBe(false)
  })

  it('filters dashboard quick actions by explicit module permissions instead of linking read-only sessions into forbidden pages', () => {
    const financeOnly = normalizeAdminSession({
      username: 'finance-admin',
      userId: '14',
      permissions: ['finance:read'],
      sessionId: 'adm_99999999999999999999999999999999',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const allAccess = normalizeAdminSession({
      username: 'ops',
      userId: '7',
      permissions: ['audit:read', 'finance:read', 'order:read', 'after-sales:read', 'user:read', 'audit:log', 'system:config'],
      sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(dashboardActionsForSession(financeOnly).map((item) => item.path)).toEqual(['/finance/withdrawals'])
    expect(dashboardActionsForSession(allAccess).map((item) => item.path)).toEqual(['/audit', '/finance/withdrawals', '/after-sales', '/orders', '/users', '/audit-logs', '/system/location'])
    expect(dashboardActionsForSession(null)).toEqual([])
  })

  it('fails closed when a persisted/admin API session has no explicit permissions', () => {
    const session = normalizeAdminSession({
      username: 'no-permission-admin',
      userId: '12',
      permissions: [],
      sessionId: 'adm_33333333333333333333333333333333',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(session).toBeNull()
    expect(shouldRedirectToLogin('/dashboard', session)).toBe(true)
    expect(menuAllowsSession({ path: '/dashboard', label: '仪表盘', permission: 'audit:read' }, session)).toBe(false)
  })

  it('requires withdrawal audit records to use finance review permission instead of generic audit review', () => {
    const auditReviewer = normalizeAdminSession({
      username: 'audit-reviewer',
      userId: '17',
      permissions: ['audit:read', 'audit:review'],
      sessionId: 'adm_12121212121212121212121212121212',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const financeReviewer = normalizeAdminSession({
      username: 'finance-reviewer',
      userId: '18',
      permissions: ['finance:read', 'finance:review'],
      sessionId: 'adm_34343434343434343434343434343434',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(canReviewAuditRecord(auditReviewer, 'REPORT')).toBe(true)
    expect(canReviewAuditRecord(auditReviewer, 'WITHDRAWAL')).toBe(false)
    expect(canReviewAuditRecord(financeReviewer, 'WITHDRAWAL')).toBe(true)
    expect(canReviewAuditRecord(null, 'WITHDRAWAL')).toBe(false)
  })

  it('fails closed for expired persisted admin sessions before building headers', () => {
    const session = normalizeAdminSession({
      username: 'expired-admin',
      userId: '13',
      permissions: ['audit:read'],
      sessionId: 'adm_77777777777777777777777777777777',
      expiresAt: '2000-01-01T00:00:00Z'
    })

    expect(session).toBeNull()
    expect(buildAdminHeaders(session)).toEqual({})
    expect(shouldRedirectToLogin('/dashboard', session)).toBe(true)
  })

  it('clears stale persisted token and username when stored admin session is expired', () => {
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn(() => JSON.stringify({
        token: 'adm_77777777777777777777777777777777',
        username: 'expired-admin',
        session: {
          username: 'expired-admin',
          userId: '13',
          permissions: ['audit:read'],
          sessionId: 'adm_77777777777777777777777777777777',
          expiresAt: '2000-01-01T00:00:00Z'
        }
      })),
      setItem: vi.fn(),
      removeItem: vi.fn()
    })
    setActivePinia(createPinia())

    const auth = useAuthStore()

    expect(auth.session).toBeNull()
    expect(auth.token).toBe('')
    expect(auth.username).toBe('')
    expect(auth.isAuthenticated).toBe(false)
  })

  it('requires module-specific read permissions for order and after-sales deep links', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      permissions: ['audit:read'],
      sessionId: 'adm_44444444444444444444444444444444',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const orderOnly = normalizeAdminSession({
      username: 'order-admin',
      userId: '10',
      permissions: ['order:read'],
      sessionId: 'adm_55555555555555555555555555555555',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const afterSalesOnly = normalizeAdminSession({
      username: 'after-sales-admin',
      userId: '11',
      permissions: ['after-sales:read'],
      sessionId: 'adm_66666666666666666666666666666666',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(shouldRedirectToLogin('/orders', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/orders/OD-ABC123', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/orders', orderOnly)).toBe(false)
    expect(shouldRedirectToLogin('/after-sales', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/after-sales/AS-ADMIN-20260510-0001', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/after-sales', afterSalesOnly)).toBe(false)
    expect(menuAllowsSession({ path: '/orders', label: '订单管理', permission: 'order:read' }, auditOnly)).toBe(false)
    expect(menuAllowsSession({ path: '/after-sales', label: '售后管理', permission: 'after-sales:read' }, auditOnly)).toBe(false)
  })

  it('requires finance read permission for withdrawal detail deep links', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '19',
      permissions: ['audit:read'],
      sessionId: 'adm_cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const financeOnly = normalizeAdminSession({
      username: 'finance-admin',
      userId: '20',
      permissions: ['finance:read'],
      sessionId: 'adm_efefefefefefefefefefefefefefefef',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(shouldRedirectToLogin('/finance/withdrawals/WD-20260510-0001', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/finance/withdrawals/WD-20260510-0001', financeOnly)).toBe(false)
  })

  it('requires after-sales review permission before enabling admin after-sales approval actions', () => {
    const readOnly = normalizeAdminSession({
      username: 'after-sales-reader',
      userId: '15',
      permissions: ['after-sales:read'],
      sessionId: 'adm_abababababababababababababababab',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const reviewer = normalizeAdminSession({
      username: 'after-sales-reviewer',
      userId: '16',
      permissions: ['after-sales:read', 'after-sales:review'],
      sessionId: 'adm_cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(canReviewAfterSales(readOnly)).toBe(false)
    expect(canReviewAfterSales(reviewer)).toBe(true)
    expect(canReviewAfterSales(null)).toBe(false)
  })

  it('requires audit review permission before enabling admin audit approval actions', () => {
    const readOnly = normalizeAdminSession({
      username: 'audit-reader',
      userId: '17',
      permissions: ['audit:read'],
      sessionId: 'adm_12121212121212121212121212121212',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const reviewer = normalizeAdminSession({
      username: 'audit-reviewer',
      userId: '18',
      permissions: ['audit:read', 'audit:review'],
      sessionId: 'adm_34343434343434343434343434343434',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(canReviewAudit(readOnly)).toBe(false)
    expect(canReviewAudit(reviewer)).toBe(true)
    expect(canReviewAudit(null)).toBe(false)
  })

  it('uses only the server-issued admin session for authenticated state without minting a local pseudo token', () => {
    const session = normalizeAdminSession({
      username: 'session-only-admin',
      userId: '22',
      permissions: ['audit:read'],
      sessionId: 'adm_01010101010101010101010101010101',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(isAdminAuthenticated({ token: '', username: 'legacy-name', session })).toBe(true)
    expect(createAdminAuthToken(session!)).toBe('adm_01010101010101010101010101010101')
    expect(createAdminAuthToken(null)).toBe('')
  })

  it('rejects local pseudo token writes so admin auth stays server-session derived', () => {
    const setItem = vi.fn()
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn(() => null),
      setItem,
      removeItem: vi.fn()
    })
    setActivePinia(createPinia())
    const auth = useAuthStore()

    expect(() => auth.setToken('local-pseudo-token')).toThrow('本地管理员令牌写入已禁用')

    expect(auth.token).toBe('')
    expect(auth.session).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
    expect(setItem).not.toHaveBeenCalled()
  })

  it('requires finance review permission before enabling withdrawal approval actions', () => {
    const readOnly = normalizeAdminSession({
      username: 'finance-reader',
      userId: '19',
      permissions: ['finance:read'],
      sessionId: 'adm_56565656565656565656565656565656',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const auditReviewer = normalizeAdminSession({
      username: 'audit-reviewer',
      userId: '20',
      permissions: ['finance:read', 'audit:review'],
      sessionId: 'adm_78787878787878787878787878787878',
      expiresAt: FUTURE_EXPIRES_AT
    })
    const financeReviewer = normalizeAdminSession({
      username: 'finance-reviewer',
      userId: '21',
      permissions: ['finance:read', 'finance:review'],
      sessionId: 'adm_90909090909090909090909090909090',
      expiresAt: FUTURE_EXPIRES_AT
    })

    expect(canReviewFinance(readOnly)).toBe(false)
    expect(canReviewFinance(auditReviewer)).toBe(false)
    expect(canReviewFinance(financeReviewer)).toBe(true)
    expect(canReviewFinance(null)).toBe(false)
  })
})
