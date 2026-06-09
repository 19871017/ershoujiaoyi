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

export interface AdminWithdrawalReviewDetail {
  withdrawal: AdminWithdrawalDetail
  user: {
    userId: number
    userNo?: string | null
    nickname: string
    status: string
    identityStatus: string
    mainRole: string
    city?: string | null
    videoIdentityStatus: string
    videoVerified: boolean
  }
  balance: {
    rechargeBalance: number
    incomeBalance: number
    frozenBalance: number
    withdrawableBalance: number
  }
  recentLedgers: Array<{
    ledgerNo: string
    direction: 'CREDIT' | 'DEBIT' | string
    amount: number
    balanceType: string
    businessType: string
    businessId?: string | null
    balanceBefore: number
    balanceAfter: number
    status: string
    remark?: string | null
    createdAt?: string
  }>
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
  keyword?: string
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
  keyword?: string
}

export interface AdminProductAuditResponse {
  productId: number
  productNo?: string
  title: string
  description?: string
  price: number
  status: string
  auditState?: string
  auditStatus?: string
  visible?: boolean
  tradeRule?: string
  createdAt?: string
}

export interface AdminProductListItem {
  productId: number
  productNo?: string | null
  title: string
  description?: string | null
  price: number
  status: string
  auditStatus?: string | null
  auditState?: string | null
  sellerId: number
  sellerNickname?: string | null
  sellerName?: string | null
  sellerUserNo?: string | null
  category?: string | null
  visible?: boolean | null
  createdAt?: string | null
}

export interface AdminProductDetail extends AdminProductListItem {
  imageUrls?: string[]
  tradeRule?: string | null
  updatedAt?: string | null
}

export interface AdminProductListQuery {
  status?: 'ALL' | 'PENDING_AUDIT' | 'ACTIVE' | 'OFFLINE' | 'SOLD'
  auditStatus?: 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'
  keyword?: string
  limit?: number
}

export interface AdminProductOfflineRequest {
  reason: string
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
  identityStatus?: string
  videoIdentityStatus: string
  videoVerified: boolean
  createdAt?: string
  updatedAt?: string
  opsSummary?: {
    orderCount: number
    paidOrderCount: number
    afterSalesCount: number
    pendingAfterSalesCount: number
    reportCount: number
    pendingReportCount: number
    withdrawalCount: number
    pendingWithdrawalCount: number
    chatConversationCount: number
    lastOrderNo?: string | null
    lastAfterSalesNo?: string | null
    lastWithdrawalNo?: string | null
    lastChatConversationId?: number | null
  } | null
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

export interface AdminChatParticipantTrace {
  userId: number
  userNo?: string | null
  nickname?: string | null
  avatarUrl?: string | null
  status?: string | null
  gender?: string | null
  city?: string | null
  mainRole?: string | null
  videoVerified?: boolean | null
}

export interface AdminChatConversationTrace {
  conversationId: number
  conversationNo: string
  conversationType: string
  lastSeq: number
  lastMessageSummary?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  owner: AdminChatParticipantTrace
  peer: AdminChatParticipantTrace
}

export interface AdminChatMessageTrace {
  messageId: number
  messageNo: string
  conversationId: number
  conversationNo: string
  serverSeq: number
  clientMsgId: string
  senderId: number
  receiverId: number
  messageType: 'TEXT' | 'IMAGE' | 'VOICE' | string
  contentJson: string
  revoked?: boolean | null
  revokedAt?: string | null
  createdAt?: string | null
}

export interface AdminChatConversationMessageTrace {
  conversation: AdminChatConversationTrace
  messages: AdminChatMessageTrace[]
  hasMore?: boolean
  oldestSeq?: number | null
  nextBeforeSeq?: number | null
}

export interface AdminCommunityPostTrace {
  postNo: string
  postId: number
  authorId: number
  authorName?: string | null
  authorAvatar?: string | null
  city?: string | null
  title: string
  topic: string
  content: string
  imageUrls: string[]
  status: string
  likeCount: number
  commentCount: number
  createdAt?: string | null
  relatedProductId?: number | null
  relatedProductTitle?: string | null
  relatedProductPrice?: number | string | null
}

export interface AdminCommunityCommentTrace {
  commentNo: string
  authorId: number
  authorName?: string | null
  authorAvatar?: string | null
  content: string
  status?: string | null
  createdAt?: string | null
}

export interface AdminCommunityPostDetailTrace extends AdminCommunityPostTrace {
  comments: AdminCommunityCommentTrace[]
}

export interface AdminChatConversationTraceQuery {
  conversationId?: string | number
  userId?: string | number
  keyword?: string
  limit?: number
}

export interface AdminCommunityPostTraceQuery {
  keyword?: string
  authorId?: string | number
  limit?: number
}

export interface AdminCommunityModerationRequest {
  reason: string
}

export interface AdminChatMessageTraceQuery {
  limit?: number
  beforeSeq?: number
}

export interface AdminAuditListQuery {
  auditType?: 'ALL' | 'REPORT' | 'WITHDRAWAL' | 'VIDEO_IDENTITY' | 'REAL_NAME_IDENTITY' | 'PRODUCT'
  status?: 'ALL' | AuditStatus
  keyword?: string
  limit?: number
}

export interface AdminUserSearchQuery {
  keyword: string
  limit?: number
}

export type AdminOperatorPermissionCode = 'audit:read' | 'audit:review' | 'chat:trace' | 'finance:read' | 'finance:review' | 'user:read' | 'user:risk-control' | 'order:read' | 'after-sales:read' | 'after-sales:review' | 'system:config' | 'audit:log' | 'operator:grant'

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
  videoEvidenceUrl?: string | null
  videoEvidenceVerified?: boolean
  reportEvidenceUrls?: string[]
}

export interface AdminVideoEvidenceProgressRequest {
  durationSeconds: number
  currentTimeSeconds: number
  watchedRatio?: number
  ended?: boolean
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

export interface AdminAnnouncementTicker {
  enabled: boolean
  text: string
  icon: string
  targetUrl: string
  updatedAt?: string
}

const adminHomeBannerActions = ['closet', 'ranking', 'forum', 'search', 'none'] as const
const adminHomeBannerPlacements = ['HOME', 'MERCHANT_SHOWCASE'] as const

export type AdminHomeBannerAction = typeof adminHomeBannerActions[number]
export type AdminHomeBannerPlacement = typeof adminHomeBannerPlacements[number]

export interface AdminHomeBanner {
  id: number
  kicker: string
  title: string
  description: string
  cta: string
  imageUrl: string
  action: AdminHomeBannerAction
  placement: AdminHomeBannerPlacement
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
  placement: AdminHomeBannerPlacement
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

export interface AdminUpdateAnnouncementTickerRequest {
  enabled: boolean
  text: string
  icon: string
  targetUrl: string
}

export function getAdminDashboard(): Promise<AdminDashboardSummary> {
  return request<AdminDashboardSummary>({ url: '/api/admin/dashboard' })
}

const ADMIN_AUDIT_NO_PATTERN = /^AU-(?:\d{8}-\d{4,}|[A-Z]{3}-[1-9]\d{9,16}-\d{1,6})$/

export function isValidAdminAuditNo(auditNo: string): boolean {
  return ADMIN_AUDIT_NO_PATTERN.test(auditNo)
}

export function isValidAdminAuditKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 64) return false
  if (/(preview|demo|mock|sample|placeholder)/i.test(normalized)) return false
  if (/^\d+$/.test(normalized) && !/^[1-9]\d{0,18}$/.test(normalized)) return false
  return true
}

export function isValidAdminWithdrawalNo(withdrawalNo: string) {
  return /^WD-(?:\d{8}-\d{4,}|\d{12,17}-\d{1,6})$/.test(withdrawalNo)
}

export function isValidAdminAfterSalesNo(afterSalesNo: string) {
  return /^AS-[A-Z]+-\d{8}-\d{4,}$/.test(afterSalesNo)
}

export function isValidAdminAfterSalesKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 64) return false
  return !/(preview|demo|mock|sample|placeholder)/i.test(normalized)
}

export function isValidAdminOrderNo(orderNo: string) {
  return /^OD-[A-Z0-9]{4,}$/.test(orderNo)
}

export function isValidAdminOrderKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 64) return false
  if (/(preview|demo|mock|sample|placeholder)/i.test(normalized)) return false
  if (/^\d+$/.test(normalized) && normalized.length > 18) return false
  return true
}

export function isValidAdminProductId(productId: string | number) {
  return /^[1-9]\d*$/.test(String(productId))
}

export function isValidAdminProductKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 64) return false
  if (/(preview|demo|mock|sample|placeholder)/i.test(normalized)) return false
  if (/^\d+$/.test(normalized) && !/^[1-9]\d{0,18}$/.test(normalized)) return false
  return true
}

export function isValidAdminProductOfflineReason(reason: string) {
  const normalized = reason.trim()
  if (!normalized || normalized.length > 128) return false
  return !/(preview|demo|mock|sample|placeholder)/i.test(normalized)
}

export function isValidAdminUserId(userId: string | number) {
  return /^[1-9]\d*$/.test(String(userId))
}

export function isValidAdminOperatorPermission(permission: string): permission is AdminOperatorPermissionCode {
  return ['audit:read', 'audit:review', 'chat:trace', 'finance:read', 'finance:review', 'user:read', 'user:risk-control', 'order:read', 'after-sales:read', 'after-sales:review', 'system:config', 'audit:log', 'operator:grant'].includes(permission)
}

export function isValidAdminUserSearchKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 32) return false
  return !/(preview|demo|mock|sample|placeholder)/i.test(normalized)
}

export function isValidAdminAuditLogId(logId: string | number) {
  return /^[1-9]\d*$/.test(String(logId))
}

export function isValidAdminChatTraceId(id: string | number) {
  if (typeof id === 'number') {
    return Number.isSafeInteger(id) && id > 0
  }
  return /^[1-9]\d{0,18}$/.test(String(id).trim())
}

export function isValidAdminChatTraceKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 64) return false
  if (/(preview|demo|mock|sample|placeholder)/i.test(normalized)) return false
  if (/^\d+$/.test(normalized) && !/^[1-9]\d{0,18}$/.test(normalized)) return false
  return true
}

export function isValidAdminCommunityTraceId(id: string | number) {
  if (typeof id === 'number') {
    return Number.isSafeInteger(id) && id > 0
  }
  const normalized = String(id).trim()
  if (/^[1-9]\d{0,18}$/.test(normalized)) return true
  return /^POST-[1-9]\d*-[1-9]\d*$/.test(normalized)
}

export function isValidAdminCommunityCommentNo(commentNo: string) {
  const normalized = commentNo.trim()
  if (!normalized || /(preview|demo|mock|sample|placeholder)/i.test(normalized)) return false
  return /^CMT-[1-9]\d*-[1-9]\d*(?:-[A-Z0-9]{6,16})?$/.test(normalized)
}

export function isValidAdminCommunityTraceKeyword(keyword: string) {
  const normalized = keyword.trim()
  if (!normalized || normalized.length > 64) return false
  if (/(preview|demo|mock|sample|placeholder)/i.test(normalized)) return false
  if (/^\d+$/.test(normalized) && !/^[1-9]\d{0,18}$/.test(normalized)) return false
  return true
}

export function isValidAdminCommunityModerationReason(reason: string) {
  const normalized = reason.trim()
  if (!normalized || normalized.length > 128) return false
  return !/(preview|demo|mock|sample|placeholder)/i.test(normalized)
}

export async function getAdminAuditList(query: AdminAuditListQuery = {}) {
  const params = new URLSearchParams()
  const auditType = query.auditType ?? 'ALL'
  if (!['ALL', 'REPORT', 'WITHDRAWAL', 'VIDEO_IDENTITY', 'REAL_NAME_IDENTITY', 'PRODUCT'].includes(auditType)) {
    throw new Error('审核类型筛选无效')
  }
  if (auditType !== 'ALL') params.set('auditType', auditType)
  const status = query.status ?? 'PENDING'
  if (!['ALL', 'PENDING', 'APPROVED', 'REJECTED'].includes(status)) {
    throw new Error('审核状态筛选无效')
  }
  if (status !== 'ALL') params.set('status', status)
  const keyword = query.keyword?.trim() ?? ''
  if (keyword) {
    if (!isValidAdminAuditKeyword(keyword)) {
      throw new Error('审核关键词无效')
    }
    params.set('keyword', keyword)
  }
  const limit = query.limit ?? 50
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('审核列表条数无效')
  }
  params.set('limit', String(limit))
  return request<AuditRecordResponse[]>({ url: `/api/admin/audit?${params.toString()}` })
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

export async function recordAdminVideoEvidenceProgress(auditNo: string, progress: AdminVideoEvidenceProgressRequest) {
  if (!isValidAdminAuditNo(auditNo)) {
    throw new Error('审核编号无效')
  }
  const durationSeconds = Number(progress.durationSeconds)
  const currentTimeSeconds = Number(progress.currentTimeSeconds)
  const watchedRatio = progress.watchedRatio == null ? currentTimeSeconds / durationSeconds : Number(progress.watchedRatio)
  const ended = progress.ended === true
  if (!Number.isFinite(durationSeconds) || durationSeconds <= 0 || !Number.isFinite(currentTimeSeconds) || currentTimeSeconds < 0) {
    throw new Error('视频观看进度无效')
  }
  if (!Number.isFinite(watchedRatio) || watchedRatio < 0 || watchedRatio > 1.05) {
    throw new Error('视频观看进度无效')
  }
  const actualRatio = Math.min(Math.max(currentTimeSeconds / durationSeconds, 0), 1)
  const safeRatio = Math.max(actualRatio, Math.min(watchedRatio, 1))
  if (!ended && safeRatio < 0.8) {
    throw new Error('视频观看进度未达标')
  }
  return request<void>({
    url: `/api/admin/audit/${encodeURIComponent(auditNo)}/video-evidence/progress`,
    method: 'POST',
    data: {
      durationSeconds,
      currentTimeSeconds,
      watchedRatio: safeRatio,
      ended
    }
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

export async function rejectAdminProduct(productId: string | number) {
  if (!isValidAdminProductId(productId)) {
    throw new Error('商品编号无效')
  }
  return request<AdminProductAuditResponse>({
    url: `/api/admin/products/${encodeURIComponent(String(productId))}/reject`,
    method: 'POST'
  })
}

export async function offlineAdminProduct(productId: string | number, payload: AdminProductOfflineRequest) {
  if (!isValidAdminProductId(productId)) {
    throw new Error('商品编号无效')
  }
  const reason = payload?.reason?.trim() ?? ''
  if (!isValidAdminProductOfflineReason(reason)) {
    throw new Error('商品下架原因无效')
  }
  return request<AdminProductAuditResponse>({
    url: `/api/admin/products/${encodeURIComponent(String(productId))}/offline`,
    method: 'POST',
    data: { reason }
  })
}

export async function getAdminProductList(query: AdminProductListQuery = {}) {
  const params = new URLSearchParams()
  const status = query.status ?? 'ALL'
  if (!['ALL', 'PENDING_AUDIT', 'ACTIVE', 'OFFLINE', 'SOLD'].includes(status)) {
    throw new Error('商品状态筛选无效')
  }
  if (status !== 'ALL') params.set('status', status)
  const auditStatus = query.auditStatus ?? 'ALL'
  if (!['ALL', 'PENDING', 'APPROVED', 'REJECTED'].includes(auditStatus)) {
    throw new Error('商品审核状态筛选无效')
  }
  if (auditStatus !== 'ALL') params.set('auditStatus', auditStatus)
  const keyword = query.keyword?.trim() ?? ''
  if (keyword) {
    if (!isValidAdminProductKeyword(keyword)) {
      throw new Error('商品关键词无效')
    }
    params.set('keyword', keyword)
  }
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('商品列表条数无效')
  }
  params.set('limit', String(limit))
  return request<AdminProductListItem[]>({ url: `/api/admin/products?${params.toString()}` })
}

export async function getAdminProductDetail(productId: string | number) {
  if (!isValidAdminProductId(productId)) {
    throw new Error('商品编号无效')
  }
  return request<AdminProductDetail>({ url: `/api/admin/products/${encodeURIComponent(String(productId))}` })
}

export async function getAdminWithdrawalDetail(withdrawalNo: string) {
  if (!isValidAdminWithdrawalNo(withdrawalNo)) {
    throw new Error('提现编号无效')
  }
  return request<AdminWithdrawalReviewDetail>({ url: `/api/admin/withdrawals/${encodeURIComponent(withdrawalNo)}` })
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
  const keyword = query.keyword?.trim() ?? ''
  if (keyword) {
    if (!isValidAdminAfterSalesKeyword(keyword)) {
      throw new Error('售后关键词无效')
    }
    params.set('keyword', keyword)
  }
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
  const keyword = query.keyword?.trim() ?? ''
  if (keyword) {
    if (!isValidAdminOrderKeyword(keyword)) {
      throw new Error('订单关键词无效')
    }
    params.set('keyword', keyword)
  }
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

export async function getAdminChatConversations(query: AdminChatConversationTraceQuery = {}) {
  const params = new URLSearchParams()
  if (query.conversationId !== undefined && String(query.conversationId).trim()) {
    if (!isValidAdminChatTraceId(query.conversationId)) {
      throw new Error('私聊会话编号无效')
    }
    params.set('conversationId', String(query.conversationId))
  }
  if (query.userId !== undefined && String(query.userId).trim()) {
    if (!isValidAdminChatTraceId(query.userId)) {
      throw new Error('私聊用户编号无效')
    }
    params.set('userId', String(query.userId))
  }
  const keyword = query.keyword?.trim() ?? ''
  if (keyword) {
    if (!isValidAdminChatTraceKeyword(keyword)) {
      throw new Error('私聊追溯关键词无效')
    }
    params.set('keyword', keyword)
  }
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('私聊会话条数无效')
  }
  params.set('limit', String(limit))
  return request<AdminChatConversationTrace[]>({ url: `/api/admin/chat/conversations?${params.toString()}` })
}

export async function getAdminChatConversationMessages(conversationId: string | number, query: AdminChatMessageTraceQuery = {}) {
  if (!isValidAdminChatTraceId(conversationId)) {
    throw new Error('私聊会话编号无效')
  }
  const params = new URLSearchParams()
  const limit = query.limit ?? 100
  if (!Number.isInteger(limit) || limit < 1 || limit > 200) {
    throw new Error('私聊消息条数无效')
  }
  params.set('limit', String(limit))
  if (query.beforeSeq !== undefined) {
    if (!isValidAdminChatTraceId(query.beforeSeq)) {
      throw new Error('私聊消息游标无效')
    }
    params.set('beforeSeq', String(query.beforeSeq))
  }
  return request<AdminChatConversationMessageTrace>({
    url: `/api/admin/chat/conversations/${encodeURIComponent(String(conversationId))}/messages?${params.toString()}`
  })
}

export async function getAdminCommunityPosts(query: AdminCommunityPostTraceQuery = {}) {
  const params = new URLSearchParams()
  if (query.authorId !== undefined && String(query.authorId).trim()) {
    if (!isValidAdminUserId(query.authorId)) {
      throw new Error('社区作者编号无效')
    }
    params.set('authorId', String(query.authorId))
  }
  const keyword = query.keyword?.trim() ?? ''
  if (keyword) {
    if (!isValidAdminCommunityTraceKeyword(keyword)) {
      throw new Error('社区追溯关键词无效')
    }
    params.set('keyword', keyword)
  }
  const limit = query.limit ?? 20
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('社区帖子条数无效')
  }
  params.set('limit', String(limit))
  return request<AdminCommunityPostTrace[]>({ url: `/api/admin/community/posts?${params.toString()}` })
}

export async function getAdminCommunityPostDetail(postId: string | number) {
  if (!isValidAdminCommunityTraceId(postId)) {
    throw new Error('社区帖子编号无效')
  }
  return request<AdminCommunityPostDetailTrace>({ url: `/api/admin/community/posts/${encodeURIComponent(String(postId))}` })
}

export async function blockAdminCommunityPost(postId: string | number, payload: AdminCommunityModerationRequest) {
  if (!isValidAdminCommunityTraceId(postId)) {
    throw new Error('社区帖子编号无效')
  }
  const reason = payload?.reason?.trim() ?? ''
  if (!isValidAdminCommunityModerationReason(reason)) {
    throw new Error('社区处置原因无效')
  }
  return request<AdminCommunityPostDetailTrace>({
    url: `/api/admin/community/posts/${encodeURIComponent(String(postId))}/block`,
    method: 'POST',
    data: { reason }
  })
}

export async function restoreAdminCommunityPost(postId: string | number, payload: AdminCommunityModerationRequest) {
  if (!isValidAdminCommunityTraceId(postId)) {
    throw new Error('社区帖子编号无效')
  }
  const reason = payload?.reason?.trim() ?? ''
  if (!isValidAdminCommunityModerationReason(reason)) {
    throw new Error('社区处置原因无效')
  }
  return request<AdminCommunityPostDetailTrace>({
    url: `/api/admin/community/posts/${encodeURIComponent(String(postId))}/restore`,
    method: 'POST',
    data: { reason }
  })
}

export async function blockAdminCommunityComment(commentNo: string, payload: AdminCommunityModerationRequest) {
  const safeCommentNo = commentNo.trim()
  if (!isValidAdminCommunityCommentNo(safeCommentNo)) {
    throw new Error('社区评论编号无效')
  }
  const reason = payload?.reason?.trim() ?? ''
  if (!isValidAdminCommunityModerationReason(reason)) {
    throw new Error('社区处置原因无效')
  }
  return request<AdminCommunityPostDetailTrace>({
    url: `/api/admin/community/comments/${encodeURIComponent(safeCommentNo)}/block`,
    method: 'POST',
    data: { reason }
  })
}

export async function restoreAdminCommunityComment(commentNo: string, payload: AdminCommunityModerationRequest) {
  const safeCommentNo = commentNo.trim()
  if (!isValidAdminCommunityCommentNo(safeCommentNo)) {
    throw new Error('社区评论编号无效')
  }
  const reason = payload?.reason?.trim() ?? ''
  if (!isValidAdminCommunityModerationReason(reason)) {
    throw new Error('社区处置原因无效')
  }
  return request<AdminCommunityPostDetailTrace>({
    url: `/api/admin/community/comments/${encodeURIComponent(safeCommentNo)}/restore`,
    method: 'POST',
    data: { reason }
  })
}

export async function reviewAdminWithdrawal(
  withdrawalNo: string,
  auditNo: string,
  action: 'approve' | 'reject',
  remark: string
): Promise<AdminWithdrawalReviewDetail> {
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

export function getAdminAnnouncementTicker() {
  return request<AdminAnnouncementTicker>({ url: '/api/admin/announcements/ticker' })
}

export function updateAdminAnnouncementTicker(data: AdminUpdateAnnouncementTickerRequest) {
  validateAdminAnnouncementTickerRequest(data)
  return request<AdminAnnouncementTicker>({
    url: '/api/admin/announcements/ticker',
    method: 'POST',
    data
  })
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

function validateAdminAnnouncementTickerRequest(data: AdminUpdateAnnouncementTickerRequest) {
  if (!data || typeof data !== 'object') throw new Error('公告配置无效')
  if (!data.text || data.text.trim().length > 80) throw new Error('公告文案无效')
  if (/preview|demo|mock|sample|placeholder/i.test(data.text)) throw new Error('公告文案无效')
  if (!data.icon || data.icon.trim().length > 8) throw new Error('公告图标无效')
  if (!data.targetUrl || data.targetUrl.trim().length > 256 || !data.targetUrl.startsWith('/pages/')) throw new Error('公告跳转路径无效')
}

function validateAdminHomeBannerRequest(data: AdminHomeBannerRequest) {
  if (!data || typeof data !== 'object') throw new Error('轮播图配置无效')
  if (!data.kicker || data.kicker.trim().length > 32) throw new Error('轮播图角标无效')
  if (!data.title || data.title.trim().length > 40) throw new Error('轮播图标题无效')
  if (!data.description || data.description.trim().length > 80) throw new Error('轮播图说明无效')
  if (!data.cta || data.cta.trim().length > 16) throw new Error('轮播图按钮文案无效')
  if (!data.imageUrl || data.imageUrl.trim().length > 512 || !(data.imageUrl.startsWith('/uploads/') || data.imageUrl.startsWith('https://'))) throw new Error('轮播图图片地址无效')
  if (!adminHomeBannerActions.includes(data.action)) throw new Error('轮播图跳转动作无效')
  if (!adminHomeBannerPlacements.includes(data.placement)) throw new Error('轮播图展示位置无效')
  if (!Number.isInteger(data.sortOrder) || data.sortOrder < 1 || data.sortOrder > 999) throw new Error('轮播图排序无效')
}

export function updateAdminLocationConfig(data: AdminUpdateLocationConfigRequest) {
  return request<AdminLocationConfig>({
    url: '/api/admin/location/config',
    method: 'POST',
    data
  })
}
