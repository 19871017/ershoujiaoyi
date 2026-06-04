import { describe, expect, it } from 'vitest'
import {
  afterSalesAuditTraceLocation,
  afterSalesTraceDetailLocation,
  afterSalesTraceListLocation
} from './after-sales-trace-links'

describe('admin after-sales trace links', () => {
  it('builds fail-closed list trace locations for order and user keywords', () => {
    expect(afterSalesTraceListLocation('OD-12345')).toEqual({
      path: '/after-sales',
      query: { status: 'ALL', keyword: 'OD-12345', limit: '20' }
    })
    expect(afterSalesTraceListLocation(8821)).toEqual({
      path: '/after-sales',
      query: { status: 'ALL', keyword: '8821', limit: '20' }
    })
    expect(afterSalesTraceListLocation('preview-after-sales')).toBeNull()
    expect(afterSalesTraceListLocation('x'.repeat(65))).toBeNull()
    expect(afterSalesTraceListLocation('OD-12345', 101)).toBeNull()
  })

  it('builds direct detail locations only for backend after-sales numbers', () => {
    expect(afterSalesTraceDetailLocation('AS-USER-20260520-000001')).toEqual({
      path: '/after-sales/AS-USER-20260520-000001'
    })
    expect(afterSalesTraceDetailLocation('AS-ADMIN-20260510-0001')).toEqual({
      path: '/after-sales/AS-ADMIN-20260510-0001'
    })
    expect(afterSalesTraceDetailLocation('preview-after-sales')).toBeNull()
    expect(afterSalesTraceDetailLocation('AS-DEMO-0001')).toBeNull()
  })

  it('routes audit targets to after-sales detail or keyword trace only for traceable target types', () => {
    expect(afterSalesAuditTraceLocation({ targetType: 'AFTER_SALES', targetId: 'AS-ADMIN-20260510-0001' })).toEqual({
      path: '/after-sales/AS-ADMIN-20260510-0001'
    })
    expect(afterSalesAuditTraceLocation({ targetType: 'ORDER', targetId: 'OD-12345' })).toEqual({
      path: '/after-sales',
      query: { status: 'ALL', keyword: 'OD-12345', limit: '20' }
    })
    expect(afterSalesAuditTraceLocation({ targetType: 'USER', targetId: 8821 })).toEqual({
      path: '/after-sales',
      query: { status: 'ALL', keyword: '8821', limit: '20' }
    })
    expect(afterSalesAuditTraceLocation({ targetType: 'PRODUCT', targetId: 42 })).toBeNull()
    expect(afterSalesAuditTraceLocation({ targetType: 'ORDER', targetId: 'mock-order' })).toBeNull()
  })
})
