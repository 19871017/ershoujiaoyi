import { describe, expect, it } from 'vitest'
import {
  buildAdminHeaders,
  menuAllowsSession,
  normalizeAdminSession,
  shouldRedirectToLogin,
  sessionAllowsPermission
} from './auth'

describe('admin auth helpers', () => {
  it('normalizes a dev admin session into non-sensitive request headers', () => {
    const session = normalizeAdminSession({ username: ' ops ', userId: '7', devAdminEnabled: true })

    expect(session).toEqual({ username: 'ops', userId: '7', devAdminEnabled: true, permissions: ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'system:config', 'audit:log'] })
    expect(buildAdminHeaders(session)).toEqual({
      'X-User-Id': '7'
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
      devAdminEnabled: true,
      permissions: ['audit:read', 'unknown:root']
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
      devAdminEnabled: true,
      permissions: ['audit:read']
    })

    expect(menuAllowsSession({ path: '/audit', label: '审核工作台', permission: 'audit:read' }, auditOnly)).toBe(true)
    expect(menuAllowsSession({ path: '/finance/withdrawals', label: '提现审核', permission: 'finance:read' }, auditOnly)).toBe(false)
    expect(menuAllowsSession({ path: '/system/location', label: '位置配置', permission: 'system:config' }, null)).toBe(false)
  })

  it('maps protected admin routes to explicit permissions and blocks deep links without permission', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      devAdminEnabled: true,
      permissions: ['audit:read']
    })

    expect(shouldRedirectToLogin('/audit/AU-20260510-0001', auditOnly)).toBe(false)
    expect(shouldRedirectToLogin('/users', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users/8331', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/users', normalizeAdminSession({ username: 'user-admin', userId: '9', devAdminEnabled: true, permissions: ['user:read'] }))).toBe(false)
    expect(shouldRedirectToLogin('/finance/withdrawals', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/system/location', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/audit-logs', auditOnly)).toBe(true)
    expect(shouldRedirectToLogin('/finance/withdrawals', null)).toBe(true)
  })

  it('requires module-specific read permissions for order and after-sales deep links', () => {
    const auditOnly = normalizeAdminSession({
      username: 'audit-only',
      userId: '8',
      devAdminEnabled: true,
      permissions: ['audit:read']
    })
    const orderOnly = normalizeAdminSession({
      username: 'order-admin',
      userId: '10',
      devAdminEnabled: true,
      permissions: ['order:read']
    })
    const afterSalesOnly = normalizeAdminSession({
      username: 'after-sales-admin',
      userId: '11',
      devAdminEnabled: true,
      permissions: ['after-sales:read']
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
