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

export interface AdminAfterSalesListQuery {
  status?: 'ALL' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
  limit?: number
}

export interface AdminAfterSalesReviewRequest {
  remark?: string
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

export interface AdminProductAuditResponse {
  productId: number
  title: string
  description?: string
  price: number
  status: string
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

export type AdminOperatorPermissionCode = 'audit:read' | 'audit:review' | 'finance:read' | 'finance:review' | 'user:read' | 'user:risk-control' | 'order:read' | 'after-sales:read' | 'after-sales:review' | 'system:config' | 'audit:log' | 'operator:grant'

export interface AdminOperatorPermissionResponse {
  userId: number
  userNo: string
  nickname: string
  status: string
  permissions: AdminOperatorPermissionCode[]
}

export interface AdminOperatorPermissionUpdateRequest {
  permissions: AdminOperatorPermissionCode[]
}

export interface AuditRecordResponse {
  auditNo: string
  auditType: string
  targetType?: string
  targetId?: string
  status: AuditStatus
  reason?: string
  description?: string
  reviewRemark?: string
  createdAt?: string
  reviewedAt?: string | null
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

export type AdminHomeBannerAction = 'closet' | 'ranking' | 'forum' | 'search' | 'none'

export interface AdminHomeBanner {
  id: number
  kicker: string
  title: string
  description: string
  cta: string
  imageUrl: string
  action: AdminHomeBannerAction
  sortOrder: number
  enabled: boolean
  sizeHint: string
  updatedAt?: string
}

export interface AdminHomeBannerRequest {
  kicker: string
  title: string
  description: string
  cta: string
  imageUrl: string
  action: AdminHomeBannerAction
  sortOrder: number
  enabled: boolean
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

const ADMIN_AUDIT_NO_PATTERN = /^AU-(?:\d{8}-\d{4,}|[A-Z]{3}-[1-9]\d{9,16}-\d{1,6})$/

export function isValidAdminAuditNo(auditNo: string): boolean {
  return ADMIN_AUDIT_NO_PATTERN.test(auditNo)
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

export function isValidAdminProductId(productId: string | number) {
  return /^[1-9]\d*$/.test(String(productId))
}

export function isValidAdminUserId(userId: string | number) {
  return /^[1-9]\d*$/.test(String(userId))
}

export function isValidAdminOperatorPermission(permission: string): permission is AdminOperatorPermissionCode {
  return ['audit:read', 'audit:review', 'finance:read', 'finance:review', 'user:read', 'user:risk-control', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log', 'operator:grant'].includes(permission)
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

export async function approveAdminProduct(productId: string | number) {
  if (!isValidAdminProductId(productId)) {
    throw new Error('商品编号无效')
  }
  return request<AdminProductAuditResponse>({
    url: `/api/admin/products/${encodeURIComponent(String(productId))}/approve`,
    method: 'POST'
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

export async function getAdminAfterSalesList(query: AdminAfterSalesListQuery = {}) {
  const params = new URLSearchParams()
  const status = query.status ?? 'PENDING_REVIEW'
  if (!['ALL', 'PENDING_REVIEW', 'APPROVED', 'REJECTED'].includes(status)) {
    throw new Error('售后状态筛选无效')
  }
  if (status !== 'ALL') params.set('status', status)
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('售后列表条数无效')
  }
  params.set('limit', String(limit))
  return request<AdminAfterSalesDetail[]>({ url: `/api/admin/after-sales?${params.toString()}` })
}

export async function reviewAdminAfterSales(afterSalesNo: string, action: 'approve' | 'reject', remark: string = '') {
  if (!isValidAdminAfterSalesNo(afterSalesNo)) {
    throw new Error('售后编号无效')
  }
  if (!['approve', 'reject'].includes(action)) {
    throw new Error('售后审核动作无效')
  }
  return request<AdminAfterSalesDetail>({
    url: `/api/admin/after-sales/${encodeURIComponent(afterSalesNo)}/${action}`,
    method: 'POST',
    data: { remark }
  })
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

export async function getAdminOperatorPermissions(userId: string | number) {
  if (!isValidAdminUserId(userId)) {
    throw new Error('运营经理编号无效')
  }
  return request<AdminOperatorPermissionResponse>({ url: `/api/admin/operators/${encodeURIComponent(String(userId))}/permissions` })
}

export async function updateAdminOperatorPermissions(userId: string | number, data: AdminOperatorPermissionUpdateRequest) {
  if (!isValidAdminUserId(userId)) {
    throw new Error('运营经理编号无效')
  }
  if (!Array.isArray(data.permissions) || !data.permissions.every(isValidAdminOperatorPermission)) {
    throw new Error('运营经理权限无效')
  }
  return request<AdminOperatorPermissionResponse>({
    url: `/api/admin/operators/${encodeURIComponent(String(userId))}/permissions`,
    method: 'POST',
    data: { permissions: Array.from(new Set(data.permissions)) }
  })
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

export function getAdminHomeBanners() {
  return request<AdminHomeBanner[]>({ url: '/api/admin/home/banners' })
}

export function createAdminHomeBanner(data: AdminHomeBannerRequest) {
  validateAdminHomeBannerRequest(data)
  return request<AdminHomeBanner>({
    url: '/api/admin/home/banners',
    method: 'POST',
    data
  })
}

export function updateAdminHomeBanner(bannerId: string | number, data: AdminHomeBannerRequest) {
  if (!isValidAdminBannerId(bannerId)) {
    throw new Error('轮播图编号无效')
  }
  validateAdminHomeBannerRequest(data)
  return request<AdminHomeBanner>({
    url: `/api/admin/home/banners/${encodeURIComponent(String(bannerId))}`,
    method: 'POST',
    data
  })
}

export function deleteAdminHomeBanner(bannerId: string | number) {
  if (!isValidAdminBannerId(bannerId)) {
    throw new Error('轮播图编号无效')
  }
  return request<AdminHomeBanner>({
    url: `/api/admin/home/banners/${encodeURIComponent(String(bannerId))}/delete`,
    method: 'POST'
  })
}

export function isValidAdminBannerId(bannerId: string | number) {
  return /^[1-9]\d*$/.test(String(bannerId))
}

function validateAdminHomeBannerRequest(data: AdminHomeBannerRequest) {
  if (!data || typeof data !== 'object') throw new Error('轮播图配置无效')
  if (!data.kicker || data.kicker.trim().length > 32) throw new Error('轮播图角标无效')
  if (!data.title || data.title.trim().length > 40) throw new Error('轮播图标题无效')
  if (!data.description || data.description.trim().length > 80) throw new Error('轮播图说明无效')
  if (!data.cta || data.cta.trim().length > 16) throw new Error('轮播图按钮文案无效')
  if (!data.imageUrl || data.imageUrl.trim().length > 512 || !(data.imageUrl.startsWith('/uploads/') || data.imageUrl.startsWith('https://'))) throw new Error('轮播图图片地址无效')
  if (!['closet', 'ranking', 'forum', 'search', 'none'].includes(data.action)) throw new Error('轮播图跳转动作无效')
  if (!Number.isInteger(data.sortOrder) || data.sortOrder < 1 || data.sortOrder > 999) throw new Error('轮播图排序无效')
}

export function updateAdminLocationConfig(data: AdminUpdateLocationConfigRequest) {
  return request<AdminLocationConfig>({
    url: '/api/admin/location/config',
    method: 'POST',
    data
  })
}

