import { request } from '../http'

export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AdminWithdrawalDetail {
  withdrawalNo: string
  auditNo?: string
  userId: number
  amount: number
  paymentMethod: string
  accountName: string
  maskedAccountNo: string
  accountVerifyStatus?: string
  status: WithdrawalStatus
  remark?: string
  createdAt?: string
  reviewedAt?: string | null
}

export interface AdminWithdrawalListQuery {
  status?: WithdrawalStatus | 'ALL'
  limit?: number
}

export interface AdminAfterSalesDetail {
  afterSalesNo: string
  orderNo: string
  applicantId: number
  afterSalesType: string
  refundAmount: number
  reason: string
  description: string
  evidenceUrls: string[]
  status: string
  createdAt?: string
}

export interface AdminOrderDetail {
  orderNo: string
  buyerId: number
  sellerId: number
  productId: number
  goodsId: number
  productNo: string
  productTitle: string
  amount: number
  tradeRuleSnapshot?: string
  status: string
  peerRoleLabel?: string
  afterSalesNo?: string | null
  afterSalesStatus?: string | null
  shippingType?: string | null
  shippingCompany?: string | null
  trackingNo?: string | null
  shippingRemark?: string | null
  createdAt?: string
  paidAt?: string | null
  shippedAt?: string | null
  completedAt?: string | null
}

export interface AdminOrderListQuery {
  status?: 'ALL' | 'PENDING_PAY' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'REFUNDING'
  limit?: number
}

export interface AdminUserDetailResponse {
  userId: number
  userNo: string
  maskedPhone: string
  nickname: string
  status: string
  mainRole: string
  city?: string | null
  bio?: string | null
  videoIdentityStatus: string
  videoVerified: boolean
  createdAt?: string
  updatedAt?: string
}

export interface AdminAuditLogEntry {
  logId: number
  action: string
  operatorId: number
  targetType: string
  targetId: string
  result: string
  summary?: string
  createdAt?: string
}

export interface AdminAuditLogQuery {
  afterId?: string | number
  limit?: number
}

export interface AdminUserSearchQuery {
  keyword: string
  limit?: number
}

export interface AuditRecordResponse {
  auditNo: string
  auditType: string
  targetType?: string
  targetId?: string
  status: AuditStatus
  reason?: string
  description?: string
  reviewerRemark?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminDashboardSummary {
  status: string
  pendingAudits: number
  approvedAudits: number
  rejectedAudits: number
  pendingWithdrawals: number
  pendingAfterSales: number
  activeUsers: number
  todayOrders: number
  grossMerchandiseValue: number
}

export interface AdminLocationConfig {
  provider: string
  enabled: boolean
  configured: boolean
  defaultCity: string
  defaultProvince: string
  coordinateType: string
  updatedAt?: string
}

export interface AdminUpdateLocationConfigRequest {
  provider: string
  enabled: boolean
  defaultCity: string
  defaultProvince: string
  coordinateType: string
  baiduAk?: string
}

export function getAdminDashboard(): Promise<AdminDashboardSummary> {
  return request<AdminDashboardSummary>({ url: '/api/admin/dashboard' })
}

export function getAdminAuditList() {
  return request<AuditRecordResponse[]>({ url: '/api/admin/audit' })
}

export function isValidAdminAuditNo(auditNo: string) {
  return /^AU-\d{8}-\d{4,}$/.test(auditNo)
}

export function isValidAdminWithdrawalNo(withdrawalNo: string) {
  return /^WD-\d{8}-\d{4,}$/.test(withdrawalNo)
}

export function isValidAdminAfterSalesNo(afterSalesNo: string) {
  return /^AS-[A-Z]+-\d{8}-\d{4,}$/.test(afterSalesNo)
}

export function isValidAdminOrderNo(orderNo: string) {
  return /^OD-[A-Z0-9]{4,}$/.test(orderNo)
}

export function isValidAdminUserId(userId: string | number) {
  return /^[1-9]\d*$/.test(String(userId))
}

export function isValidAdminUserSearchKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 32) return false
  return !/(preview|demo|mock|sample|placeholder)/i.test(normalized)
}

export function isValidAdminAuditLogId(logId: string | number) {
  return /^[1-9]\d*$/.test(String(logId))
}

export async function getAdminAuditDetail(auditNo: string) {
  if (!isValidAdminAuditNo(auditNo)) {
    throw new Error('审核编号无效')
  }
  return request<AuditRecordResponse>({ url: `/api/admin/audit/${encodeURIComponent(auditNo)}` })
}

export async function approveAdminAudit(auditNo: string, remark: string) {
  if (!isValidAdminAuditNo(auditNo)) {
    throw new Error('审核编号无效')
  }
  return request<AuditRecordResponse>({
    url: `/api/admin/audit/${encodeURIComponent(auditNo)}/approve`,
    method: 'POST',
    data: { remark }
  })
}

export async function rejectAdminAudit(auditNo: string, remark: string) {
  if (!isValidAdminAuditNo(auditNo)) {
    throw new Error('审核编号无效')
  }
  return request<AuditRecordResponse>({
    url: `/api/admin/audit/${encodeURIComponent(auditNo)}/reject`,
    method: 'POST',
    data: { remark }
  })
}

export async function getAdminWithdrawalDetail(withdrawalNo: string) {
  if (!isValidAdminWithdrawalNo(withdrawalNo)) {
    throw new Error('提现编号无效')
  }
  return request<AdminWithdrawalDetail>({ url: `/api/admin/withdrawals/${encodeURIComponent(withdrawalNo)}` })
}

export async function getAdminWithdrawalList(query: AdminWithdrawalListQuery = {}) {
  const params = new URLSearchParams()
  const status = query.status ?? 'PENDING'
  if (!['ALL', 'PENDING', 'APPROVED', 'REJECTED'].includes(status)) {
    throw new Error('提现状态筛选无效')
  }
  if (status !== 'ALL') params.set('status', status)
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('提现列表条数无效')
  }
  params.set('limit', String(limit))
  return request<AdminWithdrawalDetail[]>({ url: `/api/admin/withdrawals?${params.toString()}` })
}

export async function getAdminAfterSalesDetail(afterSalesNo: string) {
  if (!isValidAdminAfterSalesNo(afterSalesNo)) {
    throw new Error('售后编号无效')
  }
  return request<AdminAfterSalesDetail>({ url: `/api/admin/after-sales/${encodeURIComponent(afterSalesNo)}` })
}

export async function getAdminOrderDetail(orderNo: string) {
  if (!isValidAdminOrderNo(orderNo)) {
    throw new Error('订单编号无效')
  }
  return request<AdminOrderDetail>({ url: `/api/admin/orders/${encodeURIComponent(orderNo)}` })
}

export async function getAdminOrderList(query: AdminOrderListQuery = {}) {
  const params = new URLSearchParams()
  const status = query.status ?? 'ALL'
  if (!['ALL', 'PENDING_PAY', 'PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING'].includes(status)) {
    throw new Error('订单状态筛选无效')
  }
  if (status !== 'ALL') params.set('status', status)
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('订单列表条数无效')
  }
  params.set('limit', String(limit))
  return request<AdminOrderDetail[]>({ url: `/api/admin/orders?${params.toString()}` })
}

export async function getAdminUserDetail(userId: string | number) {
  if (!isValidAdminUserId(userId)) {
    throw new Error('用户编号无效')
  }
  return request<AdminUserDetailResponse>({ url: `/api/admin/users/${encodeURIComponent(String(userId))}` })
}

export async function searchAdminUsers(query: AdminUserSearchQuery) {
  const keyword = query.keyword.trim()
  if (!isValidAdminUserSearchKeyword(keyword)) {
    throw new Error('用户查询条件无效')
  }
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('用户查询条数无效')
  }
  const params = new URLSearchParams({ keyword, limit: String(limit) })
  return request<AdminUserDetailResponse[]>({ url: `/api/admin/users?${params.toString()}` })
}

export async function getAdminAuditLogs(query: AdminAuditLogQuery = {}) {
  const params = new URLSearchParams()
  if (query.afterId !== undefined) {
    if (!isValidAdminAuditLogId(query.afterId)) {
      throw new Error('审计日志游标无效')
    }
    params.set('afterId', String(query.afterId))
  }
  if (query.limit !== undefined) {
    const limit = Number(query.limit)
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
      throw new Error('审计日志条数无效')
    }
    params.set('limit', String(limit))
  }
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request<AdminAuditLogEntry[]>({ url: `/api/admin/audit-logs${suffix}` })
}

export async function reviewAdminWithdrawal(
  withdrawalNo: string,
  auditNo: string,
  action: 'approve' | 'reject',
  remark: string
) {
  if (!isValidAdminWithdrawalNo(withdrawalNo)) {
    throw new Error('提现编号无效')
  }
  if (!isValidAdminAuditNo(auditNo)) {
    throw new Error('审核编号无效')
  }
  if (action === 'approve') {
    await approveAdminAudit(auditNo, remark)
  } else {
    await rejectAdminAudit(auditNo, remark)
  }
  return getAdminWithdrawalDetail(withdrawalNo)
}

export function getAdminLocationConfig() {
  return request<AdminLocationConfig>({ url: '/api/admin/location/config' })
}

export function updateAdminLocationConfig(data: AdminUpdateLocationConfigRequest) {
  return request<AdminLocationConfig>({
    url: '/api/admin/location/config',
    method: 'POST',
    data
  })
}

