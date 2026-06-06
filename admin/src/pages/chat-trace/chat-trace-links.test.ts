import { describe, expect, it } from 'vitest'
import { chatAuditTraceLocation, chatTraceDetailLocation, chatTraceListLocation } from './chat-trace-links'

describe('admin chat trace links', () => {
  it('builds fail-closed chat trace locations from report targets', () => {
    expect(chatAuditTraceLocation({ targetType: 'CHAT', targetId: 'CHAT-100088' })).toEqual({
      path: '/chat-trace',
      query: { keyword: 'CHAT-100088', limit: '20' }
    })
    expect(chatAuditTraceLocation({ targetType: 'CHAT', targetId: '12' })).toEqual({ path: '/chat-trace/12' })
    expect(chatTraceDetailLocation('0')).toBeNull()
    expect(chatTraceListLocation('preview-chat')).toBeNull()
    expect(chatAuditTraceLocation({ targetType: 'ORDER', targetId: 'CHAT-100088' })).toBeNull()
  })
})
