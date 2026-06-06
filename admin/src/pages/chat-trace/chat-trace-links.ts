import type { RouteLocationRaw } from 'vue-router'
import { isValidAdminChatTraceId, isValidAdminChatTraceKeyword } from '../../api'

export interface ChatTraceTarget {
  targetType?: string | null
  targetId?: string | number | null
}

export function chatTraceListLocation(keyword: string | number, limit = 20): RouteLocationRaw | null {
  const safeKeyword = String(keyword).trim()
  if (!safeKeyword || !isValidAdminChatTraceKeyword(safeKeyword)) return null
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) return null
  return {
    path: '/chat-trace',
    query: {
      keyword: safeKeyword,
      limit: String(limit)
    }
  }
}

export function chatTraceDetailLocation(conversationId: string | number | null | undefined): RouteLocationRaw | null {
  const safeId = String(conversationId || '').trim()
  if (!isValidAdminChatTraceId(safeId)) return null
  return { path: `/chat-trace/${encodeURIComponent(safeId)}` }
}

export function chatAuditTraceLocation(target: ChatTraceTarget): RouteLocationRaw | null {
  const type = String(target.targetType || '').trim().toUpperCase()
  const id = String(target.targetId || '').trim()
  if (type !== 'CHAT' || !id) return null
  if (isValidAdminChatTraceId(id)) return chatTraceDetailLocation(id) || chatTraceListLocation(id)
  return chatTraceListLocation(id)
}
