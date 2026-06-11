export type TickerItem = {
  id: string
  kind: 'gift'
  text: string
  targetUrl?: string
}

export function buildGiftText(item: { senderName: string; receiverName: string; giftName: string; giftIcon?: string; quantity?: number }) {
  const quantity = Math.max(1, Number(item.quantity || 1))
  const giftIcon = item.giftIcon?.trim()
  const visibleGiftIcon = giftIcon && !giftIcon.startsWith('/assets/') ? giftIcon : ''
  const giftLabel = `${visibleGiftIcon ? `${visibleGiftIcon} ` : ''}${item.giftName}`.trim()
  return `${item.senderName} 送给 ${item.receiverName} ${giftLabel}${quantity > 1 ? ` ×${quantity}` : ''}`
}

export function normalizeTargetUrl(url?: string | null) {
  if (!url) return ''
  const value = url.trim()
  return value.startsWith('/') ? value : ''
}
