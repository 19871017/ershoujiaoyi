import { describe, expect, it } from 'vitest'
import {
  orderAuditTraceLocation,
  orderTraceDetailLocation,
  orderTraceListLocation
} from './order-trace-links'

describe('admin order trace links', () => {
  it('builds fail-closed list trace locations for user product and after-sales keywords', () => {
    expect(orderTraceListLocation('6101')).toEqual({
      path: '/orders',
      query: { status: 'ALL', keyword: '6101', limit: '20' }
    })
    expect(orderTraceListLocation('AS-ADMINLIST-6101')).toEqual({
      path: '/orders',
      query: { status: 'ALL', keyword: 'AS-ADMINLIST-6101', limit: '20' }
    })
    expect(orderTraceListLocation('preview-order')).toBeNull()
    expect(orderTraceListLocation('x'.repeat(65))).toBeNull()
    expect(orderTraceListLocation('6101', 101)).toBeNull()
  })

  it('builds direct detail locations only for backend order numbers', () => {
    expect(orderTraceDetailLocation('OD-ABC123')).toEqual({ path: '/orders/OD-ABC123' })
    expect(orderTraceDetailLocation('preview-order')).toBeNull()
    expect(orderTraceDetailLocation('ORDER-DEMO-0001')).toBeNull()
  })

  it('routes audit targets to order detail or keyword trace only for traceable target types', () => {
    expect(orderAuditTraceLocation({ targetType: 'ORDER', targetId: 'OD-ABC123' })).toEqual({ path: '/orders/OD-ABC123' })
    expect(orderAuditTraceLocation({ targetType: 'TRADE_ORDER', targetId: 'ORDER-LEGACY-123' })).toEqual({
      path: '/orders',
      query: { status: 'ALL', keyword: 'ORDER-LEGACY-123', limit: '20' }
    })
    expect(orderAuditTraceLocation({ targetType: 'AFTER_SALES', targetId: 'AS-ADMINLIST-6101' })).toEqual({
      path: '/orders',
      query: { status: 'ALL', keyword: 'AS-ADMINLIST-6101', limit: '20' }
    })
    expect(orderAuditTraceLocation({ targetType: 'USER', targetId: 6101 })).toEqual({
      path: '/orders',
      query: { status: 'ALL', keyword: '6101', limit: '20' }
    })
    expect(orderAuditTraceLocation({ targetType: 'WITHDRAWAL', targetId: 'WD-20260510-0001' })).toBeNull()
    expect(orderAuditTraceLocation({ targetType: 'ORDER', targetId: 'mock-order' })).toBeNull()
  })
})
