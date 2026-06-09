import { describe, expect, it } from 'vitest'
import { maskSensitiveMediaText } from './sensitive-media-text'

describe('admin sensitive media text masking', () => {
  it('masks sensitive upload paths while preserving surrounding review text', () => {
    expect(maskSensitiveMediaText('举报凭证：/uploads/report-evidence/7/proof.png，请核查')).toBe('举报凭证：敏感媒体已隐藏，请使用授权查看，请核查')
    expect(maskSensitiveMediaText('/uploads/video-identity/42/check.mp4')).toBe('敏感媒体已隐藏，请使用授权查看')
    expect(maskSensitiveMediaText('聊天：/uploads/chat-voice/101/a.webm')).toBe('聊天：敏感媒体已隐藏，请使用授权查看')
    expect(maskSensitiveMediaText('售后：/uploads/evidence/after-sales/201/proof.jpg')).toBe('售后：敏感媒体已隐藏，请使用授权查看')
  })

  it('does not mask public business media paths', () => {
    expect(maskSensitiveMediaText('/uploads/product-image/1/a.jpg')).toBe('/uploads/product-image/1/a.jpg')
    expect(maskSensitiveMediaText('/uploads/community-image/1/a.jpg')).toBe('/uploads/community-image/1/a.jpg')
    expect(maskSensitiveMediaText('普通说明')).toBe('普通说明')
  })
})
