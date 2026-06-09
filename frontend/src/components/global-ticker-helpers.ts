import { getAnnouncementTicker } from '../api/modules/announcement'
import type { NotificationItemResponse } from '../api/modules/notification'

export type TickerItem = {
  id: string
  kind: 'announcement' | 'gift' | 'notice'
  text: string
  targetUrl?: string
}

export const DEFAULT_ANNOUNCEMENT_TARGET_URL = '/pages/notification/index'

export function buildGiftText(item: { senderName: string; receiverName: string; giftName: string; giftIcon?: string; quantity?: number }) {
  const quantity = Math.max(1, Number(item.quantity || 1))
  const giftIcon = item.giftIcon?.trim()
  const giftLabel = `${giftIcon ? `${giftIcon} ` : ''}${item.giftName}`.trim()
  return `${item.senderName} 送给 ${item.receiverName} ${giftLabel}${quantity > 1 ? ` ×${quantity}` : ''}`
}

export function buildNoticeText(item: NotificationItemResponse) {
  return item.title?.trim() || item.description?.trim() || '你有一条新通知'
}

export function normalizeTargetUrl(url?: string | null) {
  if (!url) return ''
  const value = url.trim()
  return value.startsWith('/') ? value : ''
}

export function buildAnnouncementItems(announcement: Awaited<ReturnType<typeof getAnnouncementTicker>> | null) {
  if (!announcement?.enabled) return []
  const text = announcement.text?.trim()
  if (!text) return []
  return [{
    id: `announcement-${announcement.updatedAt || text}`,
    kind: 'announcement' as const,
    text,
    targetUrl: normalizeTargetUrl(announcement.targetUrl) || DEFAULT_ANNOUNCEMENT_TARGET_URL
  }]
}
