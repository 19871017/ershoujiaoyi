import { defineStore } from 'pinia'

export interface AdminSessionInput {
  username?: string
  userId?: string
  devAdminEnabled?: boolean
  permissions?: string[]
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
  devAdminEnabled: true
  permissions: AdminPermission[]
}

interface AuthState {
  token: string
  username: string
  session: AdminSession | null
}

const STORAGE_KEY = 'xiaoyuanquan_admin_session'
const DEFAULT_DEV_ADMIN_USER_ID = '1'
const USER_ID_PATTERN = /^[1-9]\d*$/
const DEFAULT_DEV_ADMIN_PERMISSIONS: AdminPermission[] = ['audit:read', 'audit:review', 'finance:read', 'user:read', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log']
const PERMISSION_SET = new Set<AdminPermission>(DEFAULT_DEV_ADMIN_PERMISSIONS)

function normalizePermissions(permissions?: string[]): AdminPermission[] {
  if (!permissions) return [...DEFAULT_DEV_ADMIN_PERMISSIONS]
  return permissions.filter((permission): permission is AdminPermission => PERMISSION_SET.has(permission as AdminPermission))
}

export function normalizeAdminSession(input: AdminSessionInput | null | undefined): AdminSession | null {
  const username = input?.username?.trim()
  const userId = input?.userId?.trim()
  if (!username || !userId || input?.devAdminEnabled !== true) return null
  if (!USER_ID_PATTERN.test(userId)) return null
  const permissions = normalizePermissions(input.permissions)
  if (permissions.length === 0) return null
  return { username, userId, devAdminEnabled: true, permissions }
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
    'X-User-Id': session.userId
  }
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
    const session = normalizeAdminSession(parsed.session ?? {
      username: parsed.username,
      userId: parsed.userId || DEFAULT_DEV_ADMIN_USER_ID,
      devAdminEnabled: parsed.devAdminEnabled ?? Boolean(parsed.token && parsed.username)
    })
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
    login(username: string, accessKey: string) {
      const session = normalizeAdminSession({ username, userId: DEFAULT_DEV_ADMIN_USER_ID, devAdminEnabled: true })
      if (!session || accessKey.length < 6) {
        throw new Error('请输入有效管理员信息')
      }
      this.username = session.username
      this.session = session
      this.token = `admin-session-${accessKey.length}-${Date.now()}`
      persistState({ token: this.token, username: this.username, session: this.session })
    },
    setToken(token: string) {
      this.token = token
      persistState({ token: this.token, username: this.username, session: this.session })
    },
    clear() {
      this.token = ''
      this.username = ''
      this.session = null
      sessionStorage.removeItem(STORAGE_KEY)
    }
  }
})
