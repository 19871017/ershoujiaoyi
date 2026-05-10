import { describe, expect, it, vi } from 'vitest'
import {
  buildAdminHeaders,
  loginAdminSession,
  logoutAdminSession,
  menuAllowsSession,
  normalizeAdminSession,
  shouldRedirectToLogin,
  sessionAllowsPermission
} from './auth'

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
          expiresAt: '2026-05-11T03:00:00'
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
    const session = normalizeAdminSession({ username: 'ops', userId: '7', permissions: ['audit:read'], sessionId: 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', expiresAt: '2026-05-11T03:00:00' })

    await logoutAdminSession(session)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/session/logout'), expect.objectContaining({
      method: 'POST',
      headers: {
        'X-User-Id': '7',
        'X-Admin-Session': 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
      }
    }))
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
    const session = normalizeAdminSession({ username: ' ops ', userId: '7', permissions: ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log'], sessionId: 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', expiresAt: '2026-05-11T03:00:00' })

    expect(session).toEqual({ username: 'ops', userId: '7', permissions: ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log'], sessionId: 'adm_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', expiresAt: '2026-05-11T03:00:00' })
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
      expiresAt: '2026-05-11T03:00:00'
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
      expiresAt: '2026-05-11T03:00:00'
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
      expiresAt: '2026-05-11T03:00:00'
    })

    expect(shouldRedirectToLogin('/audit/AU-20260510-0001', auditOnly)).toBe(false)
    expect(shouldRedirectToLogin('/users', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users/8331', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users', normalizeAdminSession({ username: 'user-admin', userId: '9', permissions: ['user:read'], sessionId: 'adm_ffffffffffffffffffffffffffffffff', expiresAt: '2026-05-11T03:00:00' }))).toBe(false)
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
      expiresAt: '2026-05-11T03:00:00'
    })
    const userOnly = normalizeAdminSession({
      username: 'user-admin',
      userId: '9',
      permissions: ['user:read'],
      sessionId: 'adm_22222222222222222222222222222222',
      expiresAt: '2026-05-11T03:00:00'
    })

    expect(shouldRedirectToLogin('/users', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users/8331', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users', userOnly)).toBe(false)
    expect(shouldRedirectToLogin('/users/8331', userOnly)).toBe(false)
  })

  it('fails closed when a persisted/admin API session has no explicit permissions', () => {
    const session = normalizeAdminSession({
      username: 'no-permission-admin',
      userId: '12',
      permissions: [],
      sessionId: 'adm_33333333333333333333333333333333',
      expiresAt: '2026-05-11T03:00:00'
    })

    expect(session).toBeNull()
    expect(shouldRedirectToLogin('/dashboard', session)).toBe(true)
    expect(menuAllowsSession({ path: '/dashboard', label: '仪表盘', permission: 'audit:read' }, session)).toBe(false)
  })

  it('requires module-specific read permissions for order and after-sales deep links', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      permissions: ['audit:read'],
      sessionId: 'adm_44444444444444444444444444444444',
      expiresAt: '2026-05-11T03:00:00'
    })
    const orderOnly = normalizeAdminSession({
      username: 'order-admin',
      userId: '10',
      permissions: ['order:read'],
      sessionId: 'adm_55555555555555555555555555555555',
      expiresAt: '2026-05-11T03:00:00'
    })
    const afterSalesOnly = normalizeAdminSession({
      username: 'after-sales-admin',
      userId: '11',
      permissions: ['after-sales:read'],
      sessionId: 'adm_66666666666666666666666666666666',
      expiresAt: '2026-05-11T03:00:00'
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
})
