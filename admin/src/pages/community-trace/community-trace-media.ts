const blockedCommunityImageMarkers = /(preview|demo|mock|sample|placeholder)/i
const communityImagePrefix = '/uploads/community-image/'

export function isSafeCommunityImageUrl(url: string): boolean {
  const decoded = strictDecodeCommunityImageUrl(url)
  if (!decoded) return false
  if (!decoded.startsWith(communityImagePrefix)) return false
  if (decoded.includes('..') || decoded.includes('\\')) return false
  return !blockedCommunityImageMarkers.test(decoded)
}

function strictDecodeCommunityImageUrl(url: string) {
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
