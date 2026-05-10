import { defineStore } from 'pinia'
import { setAdminHeaderProvider } from '../../api/http'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:18080'

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

export interface AdminSessionInput {
  username?: string
  userId?: string
  permissions?: string[]
  sessionId?: string
  expiresAt?: string
}

export type AdminPermission = 'audit:read' | 'audit:review' | 'finance:read' | 'user:read' | 'order:read' | 'after-sales:read' | 'after-sales:review' | 'system:config' | 'audit:log'

interface RoutePermissionRule {
  pattern: RegExp
  permission: AdminPermission
}

const PROTECTED_ROUTE_PERMISSIONS: RoutePermissionRule[] = [
  { pattern: /^\/dashboard\/?$/, permission: 'audit:read' },
  { pattern: /^\/audit(?:\/|$)/, permission: 'audit:read' },
  { pattern: /^\/finance\/withdrawals(?:\/|$)/, permission: 'finance:read' },
  { pattern: /^\/users(?:\/|$)/, permission: 'user:read' },
  { pattern: /^\/orders(?:\/|$)/, permission: 'order:read' },
  { pattern: /^\/after-sales(?:\/|$)/, permission: 'after-sales:read' },
  { pattern: /^\/audit-logs(?:\/|$)/, permission: 'audit:log' },
  { pattern: /^\/system\/location(?:\/|$)/, permission: 'system:config' }
]

export interface AdminMenuItem {
  path: string
  label: string
  permission: AdminPermission
}

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
const DEFAULT_DEV_ADMIN_PERMISSIONS: AdminPermission[] = ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log']
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
  if (!hasOnlyExpectedSessionKeys(input)) return null
  const username = input?.username?.trim()
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
  return sessionAllowsPermission(session, item.permission)
}

export function buildAdminHeaders(session: AdminSession | null): Record<string, string> {
  if (!session) return {}
  return {
    'X-User-Id': session.userId,
    'X-Admin-Session': session.sessionId
  }
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

export function shouldRedirectToLogin(path: string, session: AdminSession | null): boolean {
  if (path === '/login') return false
  if (!session) return true
  const rule = PROTECTED_ROUTE_PERMISSIONS.find((item) => item.pattern.test(path))
  return rule ? !sessionAllowsPermission(session, rule.permission) : true
}

function readInitialState(): AuthState {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return { token: '', username: '', session: null }
    const parsed = JSON.parse(raw) as Partial<AuthState> & AdminSessionInput
    const session = normalizeAdminSession(parsed.session ?? null)
    return { token: parsed.token || '', username: session?.username || parsed.username || '', session }
  } catch {
    return { token: '', username: '', session: null }
  }
}

function persistState(state: AuthState) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token: state.token, username: state.username, session: state.session }))
}

export const useAuthStore = defineStore('admin-auth', {
  state: (): AuthState => readInitialState(),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.username && state.session),
    headers: (state) => buildAdminHeaders(state.session)
  },
  actions: {
    async login(username: string, accessKey: string) {
      const session = await loginAdminSession(username, accessKey)
      this.username = session.username
      this.session = session
      this.token = `admin-session-${session.userId}-${Date.now()}`
      persistState({ token: this.token, username: this.username, session: this.session })
    },
    setToken(token: string) {
      this.token = token
      persistState({ token: this.token, username: this.username, session: this.session })
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
