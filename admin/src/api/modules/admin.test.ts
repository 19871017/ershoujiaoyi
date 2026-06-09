import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  approveAdminAudit,
  getAdminAfterSalesDetail,
  getAdminAfterSalesList,
  getAdminAuditDetail,
  getAdminAuditList,
  getAdminAuditLogs,
  getAdminDashboard,
  getAdminLocationConfig,
  getAdminOrderDetail,
  getAdminOrderList,
  getAdminProductDetail,
  getAdminProductList,
  approveAdminProduct,
  rejectAdminProduct,
  getAdminChatConversationMessages,
  getAdminChatConversations,
  getAdminCommunityPostDetail,
  getAdminCommunityPosts,
  getAdminOperatorPermissions,
  updateAdminOperatorPermissions,
  getAdminUserDetail,
  getAdminWithdrawalDetail,
  getAdminWithdrawalList,
  searchAdminUsers,
  isValidAdminAfterSalesKeyword,
  isValidAdminAfterSalesNo,
  isValidAdminAuditKeyword,
  isValidAdminAuditLogId,
  isValidAdminAuditNo,
  isValidAdminChatTraceId,
  isValidAdminChatTraceKeyword,
  isValidAdminCommunityTraceId,
  isValidAdminCommunityTraceKeyword,
  isValidAdminOrderKeyword,
  isValidAdminOrderNo,
  isValidAdminProductId,
  isValidAdminProductKeyword,
  isValidAdminUserId,
  isValidAdminWithdrawalNo,
  rejectAdminAudit,
  recordAdminVideoEvidenceProgress,
  reviewAdminAfterSales,
  reviewAdminWithdrawal,
  updateAdminLocationConfig
} from './admin'
import { setAdminHeaderProvider } from '../http'

describe('admin finance api', () => {
  beforeEach(() => {
    setAdminHeaderProvider(() => ({ 'X-User-Id': '7', 'X-Admin-Session': 'adm_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }))
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

  it('approves and rejects product audit through backend product endpoints and rejects malformed product ids before fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          productId: 88,
          title: '后台审核通过商品',
          description: '后端返回商品状态',
          price: 12.34,
          status: 'ON_SALE'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    expect(isValidAdminProductId(88)).toBe(true)
    await expect(approveAdminProduct('preview-product')).rejects.toThrow('商品编号无效')
    await expect(rejectAdminProduct('preview-product')).rejects.toThrow('商品编号无效')
    await expect(approveAdminProduct(0)).rejects.toThrow('商品编号无效')
    await expect(rejectAdminProduct(0)).rejects.toThrow('商品编号无效')
    expect(fetchMock).not.toHaveBeenCalled()

    const product = await approveAdminProduct(88)
    await rejectAdminProduct(88)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/products/88/approve'), expect.objectContaining({
      method: 'POST'
    }))
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/products/88/reject'), expect.objectContaining({
      method: 'POST'
    }))
    expect(product.status).toBe('ON_SALE')
  })

  it('loads admin product list and detail through backend product endpoints with bounded filters', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [
            {
              productId: 88,
              productNo: 'PD-88',
              title: '后台商品管理裙子',
              price: 128.5,
              status: 'PENDING_AUDIT',
              auditStatus: 'PENDING',
              sellerId: 8331,
              sellerNickname: '真实卖家',
              createdAt: '2026-05-10T12:00:00'
            }
          ]
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: {
            productId: 88,
            productNo: 'PD-88',
            title: '后台商品管理裙子',
            description: '后端商品详情',
            price: 128.5,
            status: 'PENDING_AUDIT',
            auditStatus: 'PENDING',
            sellerId: 8331,
            sellerNickname: '真实卖家',
            visible: false,
            tradeRule: 'PLATFORM_ORDER',
            imageUrls: ['/uploads/product-image/8331/a.jpg'],
            createdAt: '2026-05-10T12:00:00'
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminProductList({ status: 'PENDING_AUDIT', auditStatus: 'PENDING', keyword: 'PD-88', limit: 20 })
    const detail = await getAdminProductDetail(88)

    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/api/admin/products?status=PENDING_AUDIT&auditStatus=PENDING&keyword=PD-88&limit=20'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/api/admin/products/88'), expect.any(Object))
    expect(rows[0].sellerId).toBe(8331)
    expect(detail.imageUrls?.[0]).toBe('/uploads/product-image/8331/a.jpg')
    expect(isValidAdminProductKeyword('PD-88')).toBe(true)
    expect(isValidAdminProductKeyword('8331')).toBe(true)
    expect(isValidAdminProductKeyword('preview-product')).toBe(false)
  })

  it('fails closed before admin product requests with invalid ids filters or limits', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(getAdminProductDetail('preview-product')).rejects.toThrow('商品编号无效')
    await expect(getAdminProductList({ status: 'PREVIEW' as never, limit: 20 })).rejects.toThrow('商品状态筛选无效')
    await expect(getAdminProductList({ auditStatus: 'PROCESSING' as never, limit: 20 })).rejects.toThrow('商品审核状态筛选无效')
    await expect(getAdminProductList({ keyword: 'preview-product', limit: 20 })).rejects.toThrow('商品关键词无效')
    await expect(getAdminProductList({ keyword: 'PD-88', limit: 101 })).rejects.toThrow('商品列表条数无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads withdrawal detail through the admin endpoint without exposing raw account numbers', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          withdrawal: {
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
          },
          user: {
            userId: 12,
            userNo: 'U-12',
            nickname: '提现用户',
            status: 'ACTIVE',
            identityStatus: 'VERIFIED',
            mainRole: 'BUYER',
            city: '杭州',
            videoIdentityStatus: 'UNVERIFIED',
            videoVerified: false
          },
          balance: {
            rechargeBalance: 0,
            incomeBalance: 0,
            frozenBalance: 128.5,
            withdrawableBalance: 20
          },
          recentLedgers: [
            {
              ledgerNo: 'WD-LEDGER-1',
              direction: 'DEBIT',
              amount: 128.5,
              balanceType: 'WITHDRAWABLE',
              businessType: 'WITHDRAW_FREEZE',
              businessId: 'WD-20260510-0001',
              balanceBefore: 148.5,
              balanceAfter: 20,
              status: 'SUCCESS'
            }
          ]
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminWithdrawalDetail('WD-20260510-0001')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/withdrawals/WD-20260510-0001'), expect.any(Object))
    expect(detail.withdrawal.maskedAccountNo).toBe('138****0000')
    expect(detail.user.identityStatus).toBe('VERIFIED')
    expect(detail.balance.frozenBalance).toBe(128.5)
    expect(detail.recentLedgers[0].businessType).toBe('WITHDRAW_FREEZE')
    expect(Object.prototype.hasOwnProperty.call(detail.withdrawal, 'accountNo')).toBe(false)
  })

  it('rejects preview or malformed withdrawal numbers before fetch', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    expect(isValidAdminWithdrawalNo('WD-20260510-0001')).toBe(true)
    expect(isValidAdminWithdrawalNo('WD-1770000000000-12345')).toBe(true)
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
            withdrawal: {
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
            },
            user: {
              userId: 12,
              nickname: '提现用户',
              status: 'ACTIVE',
              identityStatus: 'VERIFIED',
              mainRole: 'BUYER',
              videoIdentityStatus: 'UNVERIFIED',
              videoVerified: false
            },
            balance: {
              rechargeBalance: 0,
              incomeBalance: 0,
              frozenBalance: 0,
              withdrawableBalance: 20
            },
            recentLedgers: []
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
    expect(detail.withdrawal.status).toBe('APPROVED')
    expect(Object.prototype.hasOwnProperty.call(detail.withdrawal, 'accountNo')).toBe(false)
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
    const auditNo = 'AU-VID-1770000000000-12345'
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          auditNo,
          auditType: 'VIDEO_IDENTITY',
          targetType: 'VIDEO_IDENTITY',
          targetId: '42',
          status: 'PENDING',
          reason: '/uploads/video-identity/42/check.mp4',
          description: '手机号已脱敏',
          createdAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminAuditDetail(auditNo)

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(`/api/admin/audit/${auditNo}`), expect.any(Object))
    expect(detail.auditNo).toBe(auditNo)
    expect(isValidAdminAuditNo('AU-20260510-0001')).toBe(true)
    expect(isValidAdminAuditNo(auditNo)).toBe(true)
    expect(isValidAdminAuditNo('preview-audit')).toBe(false)
    expect(isValidAdminAuditNo('AUDIT-GOODS-1')).toBe(false)
    await expect(getAdminAuditDetail('preview-audit')).rejects.toThrow('审核编号无效')
  })

  it('loads audit list with report and real-name filters and rejects invalid filters before fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          {
            auditNo: 'AU-REP-1770000000000-12345',
            auditType: 'REPORT',
            targetType: 'PRODUCT',
            targetId: 'PRODUCT-100001',
            status: 'PENDING',
            reason: 'SPAM',
            description: '已脱敏举报内容'
          }
        ]
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminAuditList({ auditType: 'REPORT', status: 'PENDING', keyword: 'PRODUCT-100001', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/audit?auditType=REPORT&status=PENDING&keyword=PRODUCT-100001&limit=20'), expect.any(Object))
    expect(rows[0].auditType).toBe('REPORT')
    await getAdminAuditList({ auditType: 'REAL_NAME_IDENTITY', status: 'PENDING', keyword: '8331', limit: 20 })
    expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining('/api/admin/audit?auditType=REAL_NAME_IDENTITY&status=PENDING&keyword=8331&limit=20'), expect.any(Object))
    expect(isValidAdminAuditKeyword('PRODUCT-100001')).toBe(true)
    expect(isValidAdminAuditKeyword('preview-report')).toBe(false)

    await expect(getAdminAuditList({ auditType: 'ROOT' as never, status: 'PENDING', limit: 20 })).rejects.toThrow('审核类型筛选无效')
    await expect(getAdminAuditList({ auditType: 'REPORT', status: 'PROCESSING' as never, limit: 20 })).rejects.toThrow('审核状态筛选无效')
    await expect(getAdminAuditList({ auditType: 'REPORT', status: 'PENDING', keyword: 'preview-report', limit: 20 })).rejects.toThrow('审核关键词无效')
    await expect(getAdminAuditList({ auditType: 'REPORT', status: 'PENDING', limit: 101 })).rejects.toThrow('审核列表条数无效')
  })

  it('records video identity watch progress only after bounded sufficient progress', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ data: null })
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(recordAdminVideoEvidenceProgress('preview-audit', {
      durationSeconds: 10,
      currentTimeSeconds: 9,
      watchedRatio: 0.9
    })).rejects.toThrow('审核编号无效')
    await expect(recordAdminVideoEvidenceProgress('AU-VID-1770000000000-12345', {
      durationSeconds: 10,
      currentTimeSeconds: 7,
      watchedRatio: 0.7
    })).rejects.toThrow('视频观看进度未达标')
    await expect(recordAdminVideoEvidenceProgress('AU-VID-1770000000000-12345', {
      durationSeconds: 0,
      currentTimeSeconds: 0
    })).rejects.toThrow('视频观看进度无效')
    expect(fetchMock).not.toHaveBeenCalled()

    await recordAdminVideoEvidenceProgress('AU-VID-1770000000000-12345', {
      durationSeconds: 10,
      currentTimeSeconds: 8.5,
      watchedRatio: 0.85
    })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/audit/AU-VID-1770000000000-12345/video-evidence/progress'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        durationSeconds: 10,
        currentTimeSeconds: 8.5,
        watchedRatio: 0.85,
        ended: false
      })
    }))
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

  it('loads admin after-sales list through backend endpoint with status keyword and bounded limit', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: [
          {
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
        ]
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminAfterSalesList({ status: 'PENDING_REVIEW', keyword: 'AS-ADMIN-20260510-0001', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/after-sales?status=PENDING_REVIEW&keyword=AS-ADMIN-20260510-0001&limit=20'), expect.any(Object))
    expect(rows[0].afterSalesNo).toBe('AS-ADMIN-20260510-0001')
    expect(isValidAdminAfterSalesKeyword('AS-ADMIN-20260510-0001')).toBe(true)
    expect(isValidAdminAfterSalesKeyword('preview-after-sales')).toBe(false)
    vi.clearAllMocks()
    await expect(getAdminAfterSalesList({ status: 'preview' as never, limit: 20 })).rejects.toThrow('售后状态筛选无效')
    await expect(getAdminAfterSalesList({ status: 'ALL', keyword: 'preview-after-sales', limit: 20 })).rejects.toThrow('售后关键词无效')
    await expect(getAdminAfterSalesList({ status: 'ALL', keyword: 'x'.repeat(65), limit: 20 })).rejects.toThrow('售后关键词无效')
    await expect(getAdminAfterSalesList({ status: 'ALL', limit: 101 })).rejects.toThrow('售后列表条数无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads admin chat trace conversations and messages with bounded filters', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [
            {
              conversationId: 12,
              conversationNo: 'CHAT-100088',
              conversationType: 'SINGLE',
              lastSeq: 2,
              lastMessageSummary: '[语音]',
              owner: { userId: 101, userNo: 'U-101', nickname: '买家' },
              peer: { userId: 102, userNo: 'U-102', nickname: '卖家' }
            }
          ]
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: {
            conversation: {
              conversationId: 12,
              conversationNo: 'CHAT-100088',
              conversationType: 'SINGLE',
              lastSeq: 2,
              owner: { userId: 101, nickname: '买家' },
              peer: { userId: 102, nickname: '卖家' }
            },
            hasMore: false,
            oldestSeq: 2,
            nextBeforeSeq: null,
            messages: [
              {
                messageId: 8,
                messageNo: 'MSG-12-2',
                conversationId: 12,
                conversationNo: 'CHAT-100088',
                serverSeq: 2,
                clientMsgId: 'voice-1',
                senderId: 101,
                receiverId: 102,
                messageType: 'VOICE',
                contentJson: '{"url":"/uploads/chat-voice/101/voice.webm","durationMs":1800,"mimeType":"audio/webm"}',
                revoked: true,
                revokedAt: '2026-06-08T10:00:00'
              }
            ]
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminChatConversations({ keyword: 'CHAT-100088', userId: 101, limit: 20 })
    const detail = await getAdminChatConversationMessages(12, { limit: 100, beforeSeq: 20 })

    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/api/admin/chat/conversations?userId=101&keyword=CHAT-100088&limit=20'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/api/admin/chat/conversations/12/messages?limit=100&beforeSeq=20'), expect.any(Object))
    expect(rows[0].conversationNo).toBe('CHAT-100088')
    expect(detail.messages[0].messageType).toBe('VOICE')
    expect(detail.messages[0].revoked).toBe(true)
    expect(detail.messages[0].revokedAt).toBe('2026-06-08T10:00:00')
    expect(detail.hasMore).toBe(false)
    expect(isValidAdminChatTraceId('12')).toBe(true)
    expect(isValidAdminChatTraceKeyword('CHAT-100088')).toBe(true)
  })

  it('fails closed before admin chat trace requests with unsafe filters', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    expect(isValidAdminChatTraceId('0')).toBe(false)
    expect(isValidAdminChatTraceKeyword('preview-chat')).toBe(false)
    await expect(getAdminChatConversations({ conversationId: 0, limit: 20 })).rejects.toThrow('私聊会话编号无效')
    await expect(getAdminChatConversations({ userId: 'preview-user', limit: 20 })).rejects.toThrow('私聊用户编号无效')
    await expect(getAdminChatConversations({ keyword: 'preview-chat', limit: 20 })).rejects.toThrow('私聊追溯关键词无效')
    await expect(getAdminChatConversations({ keyword: 'CHAT-100088', limit: 101 })).rejects.toThrow('私聊会话条数无效')
    await expect(getAdminChatConversationMessages('preview-conversation')).rejects.toThrow('私聊会话编号无效')
    await expect(getAdminChatConversationMessages(12, { limit: 201 })).rejects.toThrow('私聊消息条数无效')
    await expect(getAdminChatConversationMessages(12, { limit: 100, beforeSeq: 0 })).rejects.toThrow('私聊消息游标无效')
    await expect(getAdminChatConversationMessages(12, { limit: 100, beforeSeq: Number.MAX_SAFE_INTEGER + 1 })).rejects.toThrow('私聊消息游标无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('loads admin community trace posts and detail with bounded filters', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [
            {
              postNo: 'POST-101-1770000000000',
              postId: 18,
              authorId: 101,
              authorName: '社区作者',
              title: '社区追溯标题',
              topic: '生活日常',
              content: '社区追溯内容',
              imageUrls: ['/uploads/community-image/101/a.jpg'],
              status: 'PUBLISHED',
              likeCount: 2,
              commentCount: 1
            }
          ]
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: {
            postNo: 'POST-101-1770000000000',
            postId: 18,
            authorId: 101,
            authorName: '社区作者',
            title: '社区追溯标题',
            topic: '生活日常',
            content: '社区追溯内容',
            imageUrls: ['/uploads/community-image/101/a.jpg'],
            status: 'PUBLISHED',
            likeCount: 2,
            commentCount: 1,
            comments: [
              { commentNo: 'CMT-102-1770000000000', authorId: 102, authorName: '评论者', content: '真实评论' }
            ]
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)

    const rows = await getAdminCommunityPosts({ keyword: '社区追溯标题', authorId: 101, limit: 20 })
    const detail = await getAdminCommunityPostDetail(18)

    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/api/admin/community/posts?authorId=101&keyword=%E7%A4%BE%E5%8C%BA%E8%BF%BD%E6%BA%AF%E6%A0%87%E9%A2%98&limit=20'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/api/admin/community/posts/18'), expect.any(Object))
    expect(rows[0].postNo).toBe('POST-101-1770000000000')
    expect(detail.comments[0].commentNo).toBe('CMT-102-1770000000000')
    expect(isValidAdminCommunityTraceId('POST-101-1770000000000')).toBe(true)
    expect(isValidAdminCommunityTraceKeyword('社区追溯标题')).toBe(true)
  })

  it('fails closed before admin community trace requests with unsafe filters', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    expect(isValidAdminCommunityTraceId('0')).toBe(false)
    expect(isValidAdminCommunityTraceKeyword('preview-community')).toBe(false)
    await expect(getAdminCommunityPosts({ authorId: 0, limit: 20 })).rejects.toThrow('社区作者编号无效')
    await expect(getAdminCommunityPosts({ keyword: 'preview-community', limit: 20 })).rejects.toThrow('社区追溯关键词无效')
    await expect(getAdminCommunityPosts({ keyword: '社区追溯标题', limit: 101 })).rejects.toThrow('社区帖子条数无效')
    await expect(getAdminCommunityPostDetail('preview-post')).rejects.toThrow('社区帖子编号无效')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('reviews after-sales through backend endpoint and fails closed for malformed ids/actions', async () => {
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
          status: 'APPROVED',
          createdAt: '2026-05-10T12:00:00'
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await reviewAdminAfterSales('AS-ADMIN-20260510-0001', 'approve', '同意协调')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/after-sales/AS-ADMIN-20260510-0001/approve'), expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ remark: '同意协调' })
    }))
    expect(detail.status).toBe('APPROVED')

    vi.clearAllMocks()
    await expect(reviewAdminAfterSales('preview-after-sales', 'approve', 'ok')).rejects.toThrow('售后编号无效')
    await expect(reviewAdminAfterSales('AS-ADMIN-20260510-0001', 'preview' as never, 'ok')).rejects.toThrow('售后审核动作无效')
    expect(fetchMock).not.toHaveBeenCalled()
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

  it('loads admin order list through backend endpoint with status keyword and bounded limit', async () => {
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

    const rows = await getAdminOrderList({ status: 'PAID', keyword: 'OD-ABC123', limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/orders?status=PAID&keyword=OD-ABC123&limit=20'), expect.any(Object))
    expect(rows[0].orderNo).toBe('OD-ABC123')
    expect(rows[0].afterSalesNo).toBe('AS-ADMINLIST-6101')
    expect(isValidAdminOrderKeyword('OD-ABC123')).toBe(true)
    expect(isValidAdminOrderKeyword('6101')).toBe(true)
    expect(isValidAdminOrderKeyword('preview-order')).toBe(false)
    vi.clearAllMocks()
    await expect(getAdminOrderList({ status: 'preview' as never, limit: 20 })).rejects.toThrow('订单状态筛选无效')
    await expect(getAdminOrderList({ status: 'ALL', keyword: 'preview-order', limit: 20 })).rejects.toThrow('订单关键词无效')
    await expect(getAdminOrderList({ status: 'ALL', keyword: 'x'.repeat(65), limit: 20 })).rejects.toThrow('订单关键词无效')
    await expect(getAdminOrderList({ status: 'ALL', limit: 101 })).rejects.toThrow('订单列表条数无效')
    expect(fetchMock).not.toHaveBeenCalled()
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

  it('loads and updates operator permissions through backend-only authorization endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { userId: 8331, userNo: 'U-8331', nickname: '运营经理', status: 'ACTIVE', permissions: ['audit:read', 'chat:trace'] } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { userId: 8331, userNo: 'U-8331', nickname: '运营经理', status: 'ACTIVE', permissions: ['audit:read', 'chat:trace', 'finance:read'] } }) })
    vi.stubGlobal('fetch', fetchMock)

    const current = await getAdminOperatorPermissions('8331')
    const updated = await updateAdminOperatorPermissions('8331', { permissions: ['audit:read', 'chat:trace', 'finance:read', 'audit:read'] })

    expect(fetchMock).toHaveBeenNthCalledWith(1, expect.stringContaining('/api/admin/operators/8331/permissions'), expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(2, expect.stringContaining('/api/admin/operators/8331/permissions'), expect.objectContaining({
      method: 'POST',
      body: '{"permissions":["audit:read","chat:trace","finance:read"]}'
    }))
    expect(current.permissions).toEqual(['audit:read', 'chat:trace'])
    expect(updated.permissions).toEqual(['audit:read', 'chat:trace', 'finance:read'])
  })

  it('allows clearing operator permissions through backend endpoint while still rejecting malformed payloads before fetch', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ data: { userId: 8331, userNo: 'U-8331', nickname: '运营经理', status: 'ACTIVE', permissions: [] } }) })
    vi.stubGlobal('fetch', fetchMock)

    const cleared = await updateAdminOperatorPermissions('8331', { permissions: [] })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/operators/8331/permissions'), expect.objectContaining({
      method: 'POST',
      body: '{"permissions":[]}'
    }))
    expect(cleared.permissions).toEqual([])
    await expect(updateAdminOperatorPermissions('8331', { permissions: ['root:all' as never] })).rejects.toThrow('运营经理权限无效')
  })

  it('rejects malformed operator ids and unknown operator permission codes before fetch', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(getAdminOperatorPermissions('preview-operator')).rejects.toThrow('运营经理编号无效')
    await expect(updateAdminOperatorPermissions('8331', { permissions: ['root:all' as never] })).rejects.toThrow('运营经理权限无效')
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
          createdAt: '2026-05-10T12:00:00',
          opsSummary: {
            orderCount: 2,
            paidOrderCount: 1,
            afterSalesCount: 1,
            pendingAfterSalesCount: 1,
            reportCount: 1,
            pendingReportCount: 1,
            withdrawalCount: 1,
            pendingWithdrawalCount: 1,
            chatConversationCount: 1,
            lastOrderNo: 'TO-ADMIN-USER-2',
            lastAfterSalesNo: 'AS-ADMIN-USER-1',
            lastWithdrawalNo: 'WD-1770000000000-12345',
            lastChatConversationId: 6
          }
        }
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const detail = await getAdminUserDetail('8331')

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/admin/users/8331'), expect.any(Object))
    expect(detail.maskedPhone).toBe('138****8331')
    expect(detail.opsSummary?.pendingAfterSalesCount).toBe(1)
    expect(detail.opsSummary?.lastWithdrawalNo).toBe('WD-1770000000000-12345')
    expect(detail.opsSummary?.lastChatConversationId).toBe(6)
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
