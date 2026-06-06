import { describe, expect, it } from 'vitest'
import { reportHandlingGuide } from './report-handling'

describe('admin report handling guide', () => {
  it('builds order and after-sales operation guides for report audit targets', () => {
    const order = reportHandlingGuide({ auditType: 'REPORT', targetType: 'ORDER', targetId: 'OD-12345' })
    const afterSales = reportHandlingGuide({ auditType: 'REPORT', targetType: 'AFTER_SALES', targetId: 'AS-ADMIN-20260510-0001' })

    expect(order?.targetLabel).toBe('订单举报')
    expect(order?.traceHint).toContain('订单追溯')
    expect(afterSales?.targetLabel).toBe('售后举报')
    expect(afterSales?.traceHint).toContain('售后追溯')
  })

  it('uses private chat trace copy for chat reports', () => {
    const chat = reportHandlingGuide({ auditType: 'REPORT', targetType: 'CHAT', targetId: 'CHAT-100088' })

    expect(chat?.targetLabel).toBe('私聊举报')
    expect(chat?.traceHint).toContain('私聊追溯')
    expect(chat?.remarkTemplates[0]).toContain('账号风控')
  })

  it('fails closed for non-report audits and placeholder targets', () => {
    expect(reportHandlingGuide({ auditType: 'WITHDRAWAL', targetType: 'ORDER', targetId: 'OD-12345' })).toBeNull()
    expect(reportHandlingGuide({ auditType: 'REPORT', targetType: 'ORDER', targetId: 'preview-order' })).toBeNull()
  })
})
