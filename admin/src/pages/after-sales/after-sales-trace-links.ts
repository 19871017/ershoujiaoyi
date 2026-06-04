import type { RouteLocationRaw } from 'vue-router'
import { isValidAdminAfterSalesKeyword, isValidAdminAfterSalesNo } from '../../api'

export interface TraceTarget {
  targetType?: string | null
  targetId?: string | number | null
}

const afterSalesTraceTypes = new Set(['AFTER_SALES', 'ORDER', 'TRADE_ORDER', 'USER', 'APPLICANT', 'BUYER', 'SELLER'])

export function afterSalesTraceListLocation(keyword: string | number, limit = 20): RouteLocationRaw | null {
  const safeKeyword = String(keyword).trim()
  if (!safeKeyword || !isValidAdminAfterSalesKeyword(safeKeyword)) return null
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) return null
  return {
    path: '/after-sales',
    query: {
      status: 'ALL',
      keyword: safeKeyword,
      limit: String(limit)
    }
  }
}

export function afterSalesTraceDetailLocation(afterSalesNo: string | null | undefined): RouteLocationRaw | null {
  const safeNo = String(afterSalesNo || '').trim()
  if (!isValidAdminAfterSalesNo(safeNo)) return null
  return { path: `/after-sales/${encodeURIComponent(safeNo)}` }
}

export function afterSalesAuditTraceLocation(target: TraceTarget): RouteLocationRaw | null {
  const type = String(target.targetType || '').trim().toUpperCase()
  const id = String(target.targetId || '').trim()
  if (!type || !id || !afterSalesTraceTypes.has(type)) return null
  if (type === 'AFTER_SALES') return afterSalesTraceDetailLocation(id)
  return afterSalesTraceListLocation(id)
}
