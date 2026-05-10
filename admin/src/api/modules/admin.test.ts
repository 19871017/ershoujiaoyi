import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  approveAdminAudit,
  getAdminAfterSalesDetail,
  getAdminAuditDetail,
  getAdminAuditLogs,
  getAdminDashboard,
  getAdminLocationConfig,
  getAdminOrderDetail,
  getAdminOrderList,
  getAdminUserDetail,
  getAdminWithdrawalDetail,
  getAdminWithdrawalList,
  searchAdminUsers,
  isValidAdminAfterSalesNo,
  isValidAdminAuditLogId,
  isValidAdminAuditNo,
  isValidAdminOrderNo,
  isValidAdminUserId,
  isValidAdminWithdrawalNo,
  rejectAdminAudit,
  reviewAdminWithdrawal,
  updateAdminLocationConfig
} from './admin'
import { setAdminHeaderProvider } from '../http'

describe('admin finance api', () => {
  beforeEach(() => {
    setAdminHeaderProvider(() => ({ 'X-Admin-Mode': 'enabled', 'X-User-Id': '7', 'X-Dev-Mode': 'enabled' }))
    vi.restoreAllMocks()
  })


  it('loads dashboard summary from backend endpoint without deriving counts from audit list', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          status: 'dashboard-ready',
          pendingAudits: 3,
          approvedAudits: 8,
          rejectedAudits: 2,
          pendingWithdrawals: 1,
          pendingAfterSales: 4,
          activeUsers: 120,
          todayOrders: 9,
          grossMerchandiseValue: 1208.5
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const summary = await getAdminDashboard()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/dashboard'), expect.any(Object))
    expect(summary.pendingAudits).toBe(3)
    expect(summary.pendingWithdrawals).toBe(1)
    expect(summary.grossMerchandiseValue).toBe(1208.5)
    expect(Object.prototype.hasOwnProperty.call(summary, 'accountNo')).toBe(false)
  })

  it('fails closed before reviewing malformed audit numbers', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(approveAdminAudit('preview-audit', 'ok')).rejects.toThrow('审核编号无效')
    await expect(rejectAdminAudit('AUDIT-GOODS-1', 'bad')).rejects.toThrow('审核编号无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads withdrawal detail through the admin endpoint without exposing raw account numbers', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          withdrawalNo: 'WD-20260510-0001',
          auditNo: 'AU-20260510-0001',
          userId: 12,
          amount: 128.5,
          paymentMethod: 'ALIPAY',
          accountName: '王*',
          maskedAccountNo: '138****0000',
          accountVerifyStatus: 'MATCHED',
          status: 'PENDING',
          remark: '待审核',
          createdAt: '2026-05-10T12:00:00',
          reviewedAt: null
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminWithdrawalDetail('WD-20260510-0001')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/withdrawals/WD-20260510-0001'), expect.any(Object))
    expect(detail.maskedAccountNo).toBe('138****0000')
    expect(Object.prototype.hasOwnProperty.call(detail, 'accountNo')).toBe(false)
  })

  it('rejects preview or malformed withdrawal numbers before fetch', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    expect(isValidAdminWithdrawalNo('WD-20260510-0001')).toBe(true)
    expect(isValidAdminWithdrawalNo('preview-withdrawal')).toBe(false)
    expect(isValidAdminWithdrawalNo('WD-demo')).toBe(false)

    await expect(getAdminWithdrawalDetail('preview-withdrawal')).rejects.toThrow('提现编号无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('lists pending withdrawals through backend list endpoint with masked account fields only', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          {
            withdrawalNo: 'WD-20260510-0001',
            auditNo: 'AU-20260510-0001',
            userId: 12,
            amount: 128.5,
            paymentMethod: 'ALIPAY',
            accountName: '王*',
            maskedAccountNo: '138****0000',
            accountVerifyStatus: 'MATCHED',
            status: 'PENDING',
            createdAt: '2026-05-10T12:00:00',
            reviewedAt: null
          }
        ]
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminWithdrawalList({ status: 'PENDING', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/withdrawals?status=PENDING&limit=20'), expect.any(Object))
    expect(rows[0].maskedAccountNo).toBe('138****0000')
    expect(Object.prototype.hasOwnProperty.call(rows[0], 'accountNo')).toBe(false)
  })

  it('fails closed before listing withdrawals with invalid status or limit', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(getAdminWithdrawalList({ status: 'PREVIEW' as never, limit: 20 })).rejects.toThrow('提现状态筛选无效')
    await expect(getAdminWithdrawalList({ status: 'PENDING', limit: 101 })).rejects.toThrow('提现列表条数无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('reviews withdrawal through audit endpoint then reloads masked backend detail', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ data: { auditNo: 'AU-20260510-0001', auditType: 'WITHDRAWAL', targetId: 'WD-20260510-0001', status: 'APPROVED' } })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: {
            withdrawalNo: 'WD-20260510-0001',
            auditNo: 'AU-20260510-0001',
            userId: 12,
            amount: 128.5,
            paymentMethod: 'ALIPAY',
            accountName: '王*',
            maskedAccountNo: '138****0000',
            accountVerifyStatus: 'MATCHED',
            status: 'APPROVED',
            reviewedAt: '2026-05-10T12:30:00'
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await reviewAdminWithdrawal('WD-20260510-0001', 'AU-20260510-0001', 'approve', '复核通过')

    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/api/admin/audit/AU-20260510-0001/approve'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ remark: '复核通过' })
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/api/admin/withdrawals/WD-20260510-0001'), expect.any(Object))
    expect(detail.status).toBe('APPROVED')
    expect(Object.prototype.hasOwnProperty.call(detail, 'accountNo')).toBe(false)
  })

  it('fails closed before reviewing withdrawal without valid backend ids', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(reviewAdminWithdrawal('preview-withdrawal', 'AU-20260510-0001', 'approve', 'ok')).rejects.toThrow('提现编号无效')
    await expect(reviewAdminWithdrawal('WD-20260510-0001', 'preview-audit', 'reject', 'bad')).rejects.toThrow('审核编号无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads location config without exposing provider secrets', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          provider: 'baidu',
          enabled: true,
          configured: true,
          defaultCity: '上海',
          defaultProvince: '上海市',
          coordinateType: 'bd09ll',
          updatedAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const config = await getAdminLocationConfig()

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/location/config'), expect.any(Object))
    expect(config.configured).toBe(true)
    expect(Object.prototype.hasOwnProperty.call(config, 'baiduAk')).toBe(false)
  })

  it('updates location config through admin endpoint using explicit config fields only', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          provider: 'baidu',
          enabled: false,
          configured: false,
          defaultCity: '杭州',
          defaultProvince: '浙江省',
          coordinateType: 'bd09ll',
          updatedAt: '2026-05-10T12:10:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const config = await updateAdminLocationConfig({
      provider: 'baidu',
      enabled: false,
      defaultCity: '杭州',
      defaultProvince: '浙江省',
      coordinateType: 'bd09ll'
    })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/location/config'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        provider: 'baidu',
        enabled: false,
        defaultCity: '杭州',
        defaultProvince: '浙江省',
        coordinateType: 'bd09ll'
      })
    }))
    expect(config.defaultCity).toBe('杭州')
  })

  it('loads audit detail only for positive backend audit numbers', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          auditNo: 'AU-20260510-0001',
          auditType: 'REPORT',
          targetType: 'PRODUCT',
          targetId: '42',
          status: 'PENDING',
          reason: '举报原因',
          description: '手机号已脱敏',
          createdAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminAuditDetail('AU-20260510-0001')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/audit/AU-20260510-0001'), expect.any(Object))
    expect(detail.auditNo).toBe('AU-20260510-0001')
    expect(isValidAdminAuditNo('AU-20260510-0001')).toBe(true)
    expect(isValidAdminAuditNo('preview-audit')).toBe(false)
    expect(isValidAdminAuditNo('AUDIT-GOODS-1')).toBe(false)
    await expect(getAdminAuditDetail('preview-audit')).rejects.toThrow('审核编号无效')
  })

  it('loads after-sales detail only for positive backend numbers without fake success', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          afterSalesNo: 'AS-ADMIN-20260510-0001',
          orderNo: 'ORDER-ADMIN-20260510-0001',
          applicantId: 8801,
          afterSalesType: 'REFUND_ONLY',
          refundAmount: 30,
          reason: '尺码不合适',
          description: '售后描述已脱敏',
          evidenceUrls: ['/uploads/evidence/after-sales/8801/proof.jpg'],
          status: 'PENDING_REVIEW',
          createdAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminAfterSalesDetail('AS-ADMIN-20260510-0001')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/after-sales/AS-ADMIN-20260510-0001'), expect.any(Object))
    expect(detail.afterSalesNo).toBe('AS-ADMIN-20260510-0001')
    expect(isValidAdminAfterSalesNo('AS-ADMIN-20260510-0001')).toBe(true)
    expect(isValidAdminAfterSalesNo('preview-after-sales')).toBe(false)
    expect(isValidAdminAfterSalesNo('AS-DEMO-0001')).toBe(false)
    await expect(getAdminAfterSalesDetail('preview-after-sales')).rejects.toThrow('售后编号无效')
  })

  it('loads order detail only for backend order numbers without fake success', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          orderNo: 'OD-ABC123',
          buyerId: 6101,
          sellerId: 7101,
          productId: 42,
          goodsId: 42,
          productNo: 'PD-42',
          productTitle: '后台订单详情裙子',
          amount: 129,
          status: 'PAID',
          afterSalesNo: null,
          afterSalesStatus: null,
          createdAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminOrderDetail('OD-ABC123')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/orders/OD-ABC123'), expect.any(Object))
    expect(detail.orderNo).toBe('OD-ABC123')
    expect(detail.buyerId).toBe(6101)
    expect(detail.sellerId).toBe(7101)
    expect(isValidAdminOrderNo('OD-ABC123')).toBe(true)
    expect(isValidAdminOrderNo('preview-order')).toBe(false)
    expect(isValidAdminOrderNo('ORDER-DEMO-0001')).toBe(false)
    await expect(getAdminOrderDetail('preview-order')).rejects.toThrow('订单编号无效')
  })

  it('loads admin order list through backend endpoint with status and bounded limit', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          {
            orderNo: 'OD-ABC123',
            buyerId: 6101,
            sellerId: 7101,
            productId: 42,
            goodsId: 42,
            productNo: 'PD-42',
            productTitle: '后台订单列表裙子',
            amount: 129,
            status: 'PAID',
            afterSalesNo: 'AS-ADMINLIST-6101',
            afterSalesStatus: 'PENDING_REVIEW',
            createdAt: '2026-05-10T12:00:00'
          }
        ]
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminOrderList({ status: 'PAID', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/orders?status=PAID&limit=20'), expect.any(Object))
    expect(rows[0].orderNo).toBe('OD-ABC123')
    expect(rows[0].afterSalesNo).toBe('AS-ADMINLIST-6101')
    await expect(getAdminOrderList({ status: 'preview' as never, limit: 20 })).rejects.toThrow('订单状态筛选无效')
    await expect(getAdminOrderList({ status: 'ALL', limit: 101 })).rejects.toThrow('订单列表条数无效')
  })

  it('searches admin users through backend endpoint with positive query and bounded limit', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          {
            userId: 8331,
            userNo: 'U-8331',
            maskedPhone: '138****8331',
            nickname: '后台用户8331',
            status: 'ACTIVE',
            mainRole: 'SELLER',
            city: '上海',
            videoIdentityStatus: 'APPROVED',
            videoVerified: true
          }
        ]
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await searchAdminUsers({ keyword: '8331', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/users?keyword=8331&limit=20'), expect.any(Object))
    expect(rows[0].maskedPhone).toBe('138****8331')
    expect(Object.prototype.hasOwnProperty.call(rows[0], 'phone')).toBe(false)
  })

  it('fails closed before searching admin users with preview keyword or invalid limit', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(searchAdminUsers({ keyword: 'preview-user', limit: 20 })).rejects.toThrow('用户查询条件无效')
    await expect(searchAdminUsers({ keyword: '', limit: 20 })).rejects.toThrow('用户查询条件无效')
    await expect(searchAdminUsers({ keyword: '8331', limit: 101 })).rejects.toThrow('用户查询条数无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads admin user detail with masked phone and positive backend user id only', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          userId: 8331,
          userNo: 'U-8331',
          maskedPhone: '138****8331',
          nickname: '小原圈用户8331',
          status: 'ACTIVE',
          mainRole: 'SELLER',
          city: '上海',
          videoIdentityStatus: 'APPROVED',
          videoVerified: true,
          createdAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminUserDetail('8331')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/users/8331'), expect.any(Object))
    expect(detail.maskedPhone).toBe('138****8331')
    expect(Object.prototype.hasOwnProperty.call(detail, 'phone')).toBe(false)
    expect(isValidAdminUserId('8331')).toBe(true)
    expect(isValidAdminUserId('0')).toBe(false)
    expect(isValidAdminUserId('preview-user')).toBe(false)
    await expect(getAdminUserDetail('preview-user')).rejects.toThrow('用户编号无效')
  })

  it('loads audit operation logs with positive cursor only and no raw secrets', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          {
            logId: 12,
            action: 'AUDIT_APPROVE',
            operatorId: 7,
            targetType: 'AUDIT',
            targetId: 'AU-20260510-0001',
            result: 'SUCCESS',
            summary: '审核通过，备注已脱敏',
            createdAt: '2026-05-10T12:30:00'
          }
        ]
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const logs = await getAdminAuditLogs({ afterId: '10', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/audit-logs?afterId=10&limit=20'), expect.any(Object))
    expect(logs[0].action).toBe('AUDIT_APPROVE')
    expect(Object.prototype.hasOwnProperty.call(logs[0], 'accountNo')).toBe(false)
    expect(Object.prototype.hasOwnProperty.call(logs[0], 'accessKey')).toBe(false)
    expect(isValidAdminAuditLogId('12')).toBe(true)
    expect(isValidAdminAuditLogId('0')).toBe(false)
    expect(isValidAdminAuditLogId('preview-log')).toBe(false)
  })

  it('rejects malformed audit log cursor before fetch', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(getAdminAuditLogs({ afterId: 'preview-log' })).rejects.toThrow('审计日志游标无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
