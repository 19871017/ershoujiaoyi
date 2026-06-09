import { describe, expect, it } from 'vitest'
import { isSafeCommunityImageUrl } from './community-trace-media'

describe('admin community trace media helpers', () => {
  it('accepts canonical community image upload urls', () => {
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/a.jpg')).toBe(true)
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/a%20b.jpg')).toBe(true)
  })

  it('rejects decoded traversal, backslash and placeholder-style community image urls', () => {
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/%2e%2e/a.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/%252e%252e/a.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/%5c/a.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/%255c/a.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/placeholder.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('/uploads/community-image/101/sample.jpg')).toBe(false)
  })

  it('rejects non-community upload paths and absolute or local preview schemes', () => {
    expect(isSafeCommunityImageUrl('/uploads/product-image/101/a.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('/assets/mock/community.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('https://old.tiklxd09.club/uploads/community-image/101/a.jpg')).toBe(false)
    expect(isSafeCommunityImageUrl('data:image/png;base64,xxx')).toBe(false)
    expect(isSafeCommunityImageUrl('%2Fuploads%2Fcommunity-image%2F101%2Fdemo.jpg')).toBe(false)
  })
})
