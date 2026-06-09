import { describe, expect, it } from 'vitest'
import { chatTraceVoiceUrl, chatTraceImageUrl, isSafeTraceUploadUrl } from './chat-trace-media'
import type { AdminChatMessageTrace } from '../../api'

function message(contentJson: string, messageType = 'VOICE'): AdminChatMessageTrace {
  return {
    messageId: 1,
    messageNo: 'MSG-1',
    conversationId: 12,
    conversationNo: 'CHAT-12',
    serverSeq: 1,
    clientMsgId: 'client-1',
    senderId: 101,
    receiverId: 102,
    messageType,
    contentJson
  }
}

describe('admin chat trace media helpers', () => {
  const boundVoiceUrl = (url: string) =>
    `/api/admin/chat/media?conversationId=12&messageId=1&messageNo=MSG-1&url=${encodeURIComponent(url)}`

  it('accepts legacy voice url, audioUrl and voiceUrl payload fields', () => {
    expect(chatTraceVoiceUrl(message('{"url":"/uploads/chat-voice/101/a.webm"}'))).toBe(boundVoiceUrl('/uploads/chat-voice/101/a.webm'))
    expect(chatTraceVoiceUrl(message('{"audioUrl":"/uploads/chat-voice/101/b.webm"}'))).toBe(boundVoiceUrl('/uploads/chat-voice/101/b.webm'))
    expect(chatTraceVoiceUrl(message('{"voiceUrl":"/uploads/chat-voice/101/c.webm"}'))).toBe(boundVoiceUrl('/uploads/chat-voice/101/c.webm'))
  })

  it('keeps double-encoded historical voice payloads compatible', () => {
    const encoded = JSON.stringify(JSON.stringify({ audioUrl: '/uploads/chat-voice/101/old.webm' }))

    expect(chatTraceVoiceUrl(message(encoded))).toBe(boundVoiceUrl('/uploads/chat-voice/101/old.webm'))
  })

  it('keeps revoked voice messages playable for backoffice trace with explicit state carried by the API type', () => {
    const revoked = {
      ...message('{"url":"/uploads/chat-voice/101/revoked.webm"}'),
      revoked: true,
      revokedAt: '2026-06-08T10:00:00'
    }

    expect(revoked.revoked).toBe(true)
    expect(chatTraceVoiceUrl(revoked)).toBe(boundVoiceUrl('/uploads/chat-voice/101/revoked.webm'))
  })

  it('rejects unsafe or non-canonical media urls after decoding', () => {
    expect(isSafeTraceUploadUrl('/uploads/chat-voice/101/%2e%2e/a.webm', '/uploads/chat-voice/')).toBe(false)
    expect(isSafeTraceUploadUrl('/uploads/chat-voice/101/%255c/a.webm', '/uploads/chat-voice/')).toBe(false)
    expect(isSafeTraceUploadUrl('/uploads/chat-voice/preview/a.webm', '/uploads/chat-voice/')).toBe(false)
    expect(isSafeTraceUploadUrl('https://example.com/uploads/chat-voice/101/a.webm', '/uploads/chat-voice/')).toBe(false)
    expect(chatTraceImageUrl(message('{"url":"/uploads/chat-image/101/a.jpg"}', 'IMAGE'))).toBe('/api/admin/chat/media?conversationId=12&messageId=1&messageNo=MSG-1&url=%2Fuploads%2Fchat-image%2F101%2Fa.jpg')
  })

  it('fails closed without concrete message binding fields', () => {
    expect(chatTraceVoiceUrl({ ...message('{"url":"/uploads/chat-voice/101/a.webm"}'), messageId: 0 })).toBe('')
    expect(chatTraceVoiceUrl({ ...message('{"url":"/uploads/chat-voice/101/a.webm"}'), conversationId: 0 })).toBe('')
    expect(chatTraceVoiceUrl({ ...message('{"url":"/uploads/chat-voice/101/a.webm"}'), messageNo: '' })).toBe('')
  })
})
