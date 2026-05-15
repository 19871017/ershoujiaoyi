import { defineStore } from 'pinia'
import { setAdminHeaderProvider } from '../../api/http'

const API_BASE = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')

async function postAdminSessionLogin(mobile: string, password: string): Promise<AdminSessionInput> {
  const response = await fetch(`${API_BASE}/api/admin/session/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mobile, password }),
    credentials: 'include'
  })
  if (!response.ok) {
    throw new Error('后台登录失败')
  }
  const payload = await response.json() as { data?: AdminSessionInput }
  return payload.data ?? {}
}

async function postAdminSessionLogout(session: AdminSession): Promise<void> {
  await fetch(`${API_BASE}/api/admin/session/logout`, {
    method: 'POST',
    headers: {
      'X-User-Id': session.userId,
      'X-Admin-Session': session.sessionId
    },
    credentials: 'include'
  }).catch(() => undefined)
}

async function getAdminSessionMe(session: AdminSession): Promise<AdminSessionInput> {
  const response = await fetch(`${API_BASE}/api/admin/session/me`, {
    method: 'GET',
    headers: {
      'X-User-Id': session.userId,
      'X-Admin-Session': session.sessionId
    },
    credentials: 'include'
  })
  if (!response.ok) {
    throw new Error('后台会话已失效')
  }
  const payload = await response.json() as { data?: AdminSessionInput }
  return payload.data ?? {}
}

export interface AdminSessionInput {
  username?: string
  userId?: string
  permissions?: string[]
  sessionId?: string
  expiresAt?: string
}

export type AdminPermission = 'audit:read' | 'audit:review' | 'finance:read' | 'finance:review' | 'user:read' | 'user:risk-control' | 'order:read' | 'after-sales:read' | 'after-sales:review' | 'system:config' | 'audit:log' | 'operator:grant'

interface RoutePermissionRule {
  pattern: RegExp
  permission: AdminPermission | null
}

const PROTECTED_ROUTE_PERMISSIONS: RoutePermissionRule[] = [
  { pattern: /^\/dashboard\/?$/, permission: null },
  { pattern: /^\/audit(?:\/|$)/, permission: 'audit:read' },
  { pattern: /^\/finance\/withdrawals(?:\/|$)/, permission: 'finance:read' },
  { pattern: /^\/users(?:\/|$)/, permission: 'user:read' },
  { pattern: /^\/orders(?:\/|$)/, permission: 'order:read' },
  { pattern: /^\/after-sales(?:\/|$)/, permission: 'after-sales:read' },
  { pattern: /^\/audit-logs(?:\/|$)/, permission: 'audit:log' },
  { pattern: /^\/operators(?:\/|$)/, permission: 'operator:grant' },
  { pattern: /^\/system\/location(?:\/|$)/, permission: 'system:config' },
  { pattern: /^\/system\/banners(?:\/|$)/, permission: 'system:config' }
]

export interface AdminMenuItem {
  path: string
  label: string
  permission: AdminPermission | null
}

export const ADMIN_DASHBOARD_ACTIONS: AdminMenuItem[] = [
  { path: '/audit', label: '审核工作台', permission: 'audit:read' },
  { path: '/finance/withdrawals', label: '提现审核', permission: 'finance:read' },
  { path: '/after-sales', label: '售后管理', permission: 'after-sales:read' },
  { path: '/orders', label: '订单管理', permission: 'order:read' },
  { path: '/users', label: '用户检索', permission: 'user:read' },
  { path: '/audit-logs', label: '审计日志', permission: 'audit:log' },
  { path: '/operators', label: '运营授权', permission: 'operator:grant' },
  { path: '/system/location', label: '位置配置', permission: 'system:config' },
  { path: '/system/banners', label: '首页轮播', permission: 'system:config' }
]

export interface AdminSession {
  username: string
  userId: string
  permissions: AdminPermission[]
  sessionId: string
  expiresAt: string
}

interface AuthState {
  token: string
  username: string
  session: AdminSession | null
}

const STORAGE_KEY = 'xiaoyuanquan_admin_session'
const USER_ID_PATTERN = /^[1-9]\d*$/
const DEFAULT_DEV_ADMIN_PERMISSIONS: AdminPermission[] = ['audit:read', 'audit:review', 'finance:read', 'finance:review', 'user:read', 'user:risk-control', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log', 'operator:grant']
const PERMISSION_SET = new Set<AdminPermission>(DEFAULT_DEV_ADMIN_PERMISSIONS)

function normalizePermissions(permissions?: string[]): AdminPermission[] {
  if (!Array.isArray(permissions)) return []
  return permissions.filter((permission): permission is AdminPermission => PERMISSION_SET.has(permission as AdminPermission))
}

function hasOnlyExpectedSessionKeys(input: AdminSessionInput | null | undefined): boolean {
  if (!input || typeof input !== 'object') return false
  return Object.keys(input).every((key) => key === 'username' || key === 'userId' || key === 'permissions' || key === 'sessionId' || key === 'expiresAt')
}

export function normalizeAdminSession(input: AdminSessionInput | null | undefined): AdminSession | null {
  if (!hasOnlyExpectedSessionKeys(input) || !input) return null
  const username = input.username?.trim()
  const userId = input?.userId?.trim()
  const sessionId = input?.sessionId?.trim()
  const expiresAt = input?.expiresAt?.trim()
  if (!username || !userId) return null
  if (!USER_ID_PATTERN.test(userId)) return null
  if (!sessionId || !/^adm_[a-f0-9]{32}$/i.test(sessionId)) return null
  if (!expiresAt || Number.isNaN(Date.parse(expiresAt))) return null
  if (Date.parse(expiresAt) <= Date.now()) return null
  const permissions = normalizePermissions(input.permissions)
  if (permissions.length === 0) return null
  return { username, userId, permissions, sessionId, expiresAt }
}

export function sessionAllowsPermission(session: AdminSession | null, permission: AdminPermission): boolean {
  return Boolean(session?.permissions.includes(permission))
}

export function menuAllowsSession(item: AdminMenuItem, session: AdminSession | null): boolean {
  if (!session) return false
  if (item.permission === null) return true
  return sessionAllowsPermission(session, item.permission)
}

export function dashboardActionsForSession(session: AdminSession | null): AdminMenuItem[] {
  return ADMIN_DASHBOARD_ACTIONS.filter((item) => menuAllowsSession(item, session))
}

export function canReviewAfterSales(session: AdminSession | null): boolean {
  return sessionAllowsPermission(session, 'after-sales:review')
}

export function canReviewAudit(session: AdminSession | null): boolean {
  return sessionAllowsPermission(session, 'audit:review')
}

export function canReviewAuditRecord(session: AdminSession | null, auditType?: string | null): boolean {
  if (auditType === 'WITHDRAWAL') {
    return sessionAllowsPermission(session, 'finance:review')
  }
  return canReviewAudit(session)
}

export function canReviewFinance(session: AdminSession | null): boolean {
  return sessionAllowsPermission(session, 'finance:review')
}

export function buildAdminHeaders(session: AdminSession | null): Record<string, string> {
  if (!session) return {}
  return {
    'X-User-Id': session.userId,
    'X-Admin-Session': session.sessionId
  }
}

export function createAdminAuthToken(session: AdminSession | null): string {
  return session?.sessionId ?? ''
}

export function isAdminAuthenticated(state: Pick<AuthState, 'session'>): boolean {
  return Boolean(state.session)
}

export async function logoutAdminSession(session: AdminSession | null): Promise<void> {
  if (session) {
    await postAdminSessionLogout(session)
  }
  setAdminHeaderProvider(null)
}

export async function loginAdminSession(mobile: string, password: string): Promise<AdminSession> {
  const session = normalizeAdminSession(await postAdminSessionLogin(mobile, password))
  if (!session) {
    throw new Error('后台登录失败')
  }
  return session
}

export async function refreshAdminSession(currentSession: AdminSession | null): Promise<AdminSession> {
  if (!currentSession) {
    setAdminHeaderProvider(null)
    throw new Error('后台会话已失效')
  }
  try {
    const session = normalizeAdminSession(await getAdminSessionMe(currentSession))
    if (!session) {
      throw new Error('后台会话已失效')
    }
    setAdminHeaderProvider(() => buildAdminHeaders(session))
    return session
  } catch (error) {
    setAdminHeaderProvider(null)
    throw error instanceof Error ? error : new Error('后台会话已失效')
  }
}

export function shouldRedirectToLogin(path: string, session: AdminSession | null): boolean {
  if (path === '/login') return false
  if (!session) return true
  const rule = PROTECTED_ROUTE_PERMISSIONS.find((item) => item.pattern.test(path))
  if (!rule) return true
  return rule.permission ? !sessionAllowsPermission(session, rule.permission) : false
}

function readInitialState(): AuthState {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return { token: '', username: '', session: null }
    const parsed = JSON.parse(raw) as Partial<AuthState> & AdminSessionInput
    const session = normalizeAdminSession(parsed.session ?? null)
    if (!session) {
      sessionStorage.removeItem(STORAGE_KEY)
      return { token: '', username: '', session: null }
    }
    return { token: createAdminAuthToken(session), username: session.username, session }
  } catch {
    sessionStorage.removeItem(STORAGE_KEY)
    return { token: '', username: '', session: null }
  }
}

function persistState(state: AuthState) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: state.token, username: state.username, session: state.session }))
}

export const useAuthStore = defineStore('admin-auth', {
  state: (): AuthState => readInitialState(),
  getters: {
    isAuthenticated: (state) => isAdminAuthenticated(state),
    headers: (state) => buildAdminHeaders(state.session)
  },
  actions: {
    async login(username: string, accessKey: string) {
      const session = await loginAdminSession(username, accessKey)
      this.username = session.username
      this.session = session
      this.token = createAdminAuthToken(session)
      persistState({ token: this.token, username: this.username, session: this.session })
    },
    setToken(_token: string) {
      throw new Error('本地管理员令牌写入已禁用，请通过后端登录接口获取服务端会话')
    },
    async refresh() {
      try {
        const session = await refreshAdminSession(this.session)
        this.username = session.username
        this.session = session
        this.token = createAdminAuthToken(session)
        persistState({ token: this.token, username: this.username, session: this.session })
        return session
      } catch (error) {
        this.token = ''
        this.username = ''
        this.session = null
        sessionStorage.removeItem(STORAGE_KEY)
        throw error
      }
    },
    async clear() {
      const currentSession = this.session
      this.token = ''
      this.username = ''
      this.session = null
      sessionStorage.removeItem(STORAGE_KEY)
      await logoutAdminSession(currentSession)
    }
  }
})
