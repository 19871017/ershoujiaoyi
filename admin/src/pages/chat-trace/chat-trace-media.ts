import type { AdminChatMessageTrace } from '../../api'

export interface ParsedChatTraceMessage {
  text?: string
  url?: string
  audioUrl?: string
  voiceUrl?: string
  durationMs?: number
  durationSeconds?: number
  sizeBytes?: number
  mimeType?: string
}

const blockedMediaMarkers = /(preview|demo|mock|sample|placeholder)/i

export function parseChatTraceMessage(message: AdminChatMessageTrace): ParsedChatTraceMessage {
  const candidates = [
    message.contentJson,
    message.contentJson.trim().replace(/\\"/g, '"')
  ]
  for (const candidate of candidates) {
    try {
      const value = JSON.parse(candidate) as unknown
      if (typeof value === 'string') {
        const nested = parseChatTraceMessage({ ...message, contentJson: value })
        if (Object.keys(nested).length) return nested
      }
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        return value as ParsedChatTraceMessage
      }
    } catch {
      // Try the next legacy encoding form.
    }
  }
  return {}
}

export function chatTraceVoiceUrl(message: AdminChatMessageTrace) {
  const parsed = parseChatTraceMessage(message)
  const url = parsed.url || parsed.audioUrl || parsed.voiceUrl || ''
  return adminChatTraceMediaUrl(message, url, '/uploads/chat-voice/')
}

export function chatTraceImageUrl(message: AdminChatMessageTrace) {
  const url = parseChatTraceMessage(message).url || ''
  return adminChatTraceMediaUrl(message, url, '/uploads/chat-image/')
}

export function adminChatTraceMediaUrl(message: AdminChatMessageTrace, url: string, prefix: string) {
  if (!isSafeTraceUploadUrl(url, prefix) || !isPositiveTraceId(message.conversationId) || !isPositiveTraceId(message.messageId) || !message.messageNo?.trim()) {
    return ''
  }
  const params = new URLSearchParams()
  params.set('conversationId', String(message.conversationId))
  params.set('messageId', String(message.messageId))
  params.set('messageNo', message.messageNo.trim())
  params.set('url', strictDecodeMediaUrl(url))
  return `/api/admin/chat/media?${params.toString()}`
}

export function chatTraceVoiceSummary(message: AdminChatMessageTrace) {
  const parsed = parseChatTraceMessage(message)
  const duration = parsed.durationMs ? `${Math.round(parsed.durationMs / 1000)} 秒` : parsed.durationSeconds ? `${parsed.durationSeconds} 秒` : '时长未知'
  const size = parsed.sizeBytes ? `${Math.round(parsed.sizeBytes / 1024)} KB` : '大小未知'
  return `${duration} / ${size} / ${parsed.mimeType || 'audio'}`
}

export function isSafeTraceUploadUrl(url: string, prefix: string) {
  const decoded = strictDecodeMediaUrl(url)
  if (!decoded) return false
  if (!decoded.startsWith(prefix)) return false
  if (decoded.includes('\\') || decoded.includes('..')) return false
  return !blockedMediaMarkers.test(decoded)
}

function strictDecodeMediaUrl(url: string) {
  let decoded = url.trim()
  if (!decoded || /^(?:https?:|data:|blob:|file:)/i.test(decoded)) return ''
  try {
    for (let i = 0; i < 3; i += 1) {
      const next = decodeURIComponent(decoded)
      if (next === decoded) break
      decoded = next
    }
  } catch {
    return ''
  }
  if (/^(?:https?:|data:|blob:|file:)/i.test(decoded)) return ''
  return decoded
}

function isPositiveTraceId(value: unknown) {
  return Number.isInteger(Number(value)) && Number(value) > 0
}
