import type { RouteLocationRaw } from 'vue-router'
import { isValidAdminCommunityTraceId, isValidAdminCommunityTraceKeyword } from '../../api'

export interface CommunityTraceTarget {
  targetType?: string | null
  targetId?: string | number | null
}

const communityTraceTypes = new Set(['COMMUNITY_POST', 'COMMUNITY_COMMENT', 'POST', 'COMMENT', 'COMMUNITY'])

export function communityTraceListLocation(keyword: string | number, limit = 20): RouteLocationRaw | null {
  const safeKeyword = String(keyword).trim()
  if (!safeKeyword || !isValidAdminCommunityTraceKeyword(safeKeyword)) return null
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) return null
  return {
    path: '/community-trace',
    query: {
      keyword: safeKeyword,
      limit: String(limit)
    }
  }
}

export function communityTraceDetailLocation(postId: string | number | null | undefined): RouteLocationRaw | null {
  const safeId = String(postId || '').trim()
  if (!isValidAdminCommunityTraceId(safeId)) return null
  return { path: `/community-trace/${encodeURIComponent(safeId)}` }
}

export function communityAuditTraceLocation(target: CommunityTraceTarget): RouteLocationRaw | null {
  const type = String(target.targetType || '').trim().toUpperCase()
  const id = String(target.targetId || '').trim()
  if (!type || !id || !communityTraceTypes.has(type)) return null
  if (type === 'COMMUNITY_POST' || type === 'POST') return communityTraceDetailLocation(id) || communityTraceListLocation(id)
  return communityTraceListLocation(id)
}
