export type TickerItem = {
  id: string
  kind: 'gift'
  text: string
  giftIcon?: string
  targetUrl?: string
}

export function buildGiftText(item: { senderName: string; receiverName: string; giftName: string; giftIcon?: string; quantity?: number }) {
  const quantity = Math.max(1, Number(item.quantity || 1))
  const giftLabel = `${item.giftName}${quantity > 1 ? ` ×${quantity}` : ''}`
  return `${item.senderName} 为 ${item.receiverName} 点亮 ${giftLabel}，这份高光礼遇正在全场闪耀`
}

export function safeGiftIconUrl(value?: string | null) {
  const icon = value?.trim() || ''
  if (!icon) return ''
  if (icon.startsWith('/assets/gifts/')) return icon
  if (/^[\u{1F300}-\u{1FAFF}]$/u.test(icon)) return icon
  return ''
}

export function normalizeTargetUrl(url?: string | null) {
  if (!url) return ''
  const value = url.trim()
  return value.startsWith('/') ? value : ''
}
