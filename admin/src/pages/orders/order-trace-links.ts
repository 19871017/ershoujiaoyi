import type { RouteLocationRaw } from 'vue-router'
import { isValidAdminOrderKeyword, isValidAdminOrderNo } from '../../api'

export interface OrderTraceTarget {
  targetType?: string | null
  targetId?: string | number | null
}

const orderTraceTypes = new Set(['ORDER', 'TRADE_ORDER', 'AFTER_SALES', 'USER', 'APPLICANT', 'BUYER', 'SELLER', 'PRODUCT', 'GOODS'])

export function orderTraceListLocation(keyword: string | number, limit = 20): RouteLocationRaw | null {
  const safeKeyword = String(keyword).trim()
  if (!safeKeyword || !isValidAdminOrderKeyword(safeKeyword)) return null
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) return null
  return {
    path: '/orders',
    query: {
      status: 'ALL',
      keyword: safeKeyword,
      limit: String(limit)
    }
  }
}

export function orderTraceDetailLocation(orderNo: string | null | undefined): RouteLocationRaw | null {
  const safeNo = String(orderNo || '').trim()
  if (!isValidAdminOrderNo(safeNo)) return null
  return { path: `/orders/${encodeURIComponent(safeNo)}` }
}

export function orderAuditTraceLocation(target: OrderTraceTarget): RouteLocationRaw | null {
  const type = String(target.targetType || '').trim().toUpperCase()
  const id = String(target.targetId || '').trim()
  if (!type || !id || !orderTraceTypes.has(type)) return null
  if (type === 'ORDER' || type === 'TRADE_ORDER') return orderTraceDetailLocation(id) || orderTraceListLocation(id)
  return orderTraceListLocation(id)
}
