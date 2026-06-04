import type { NotificationItemResponse, NotificationType } from '../../api/modules/notification'

export type NoticeType = 'ALL' | NotificationType

export const launchReadinessMarkers = [
  '已读状态暂时无法更新，请稍后重试',
  '批量已读暂时无法更新，请稍后重试',
  '通知暂时不可用，请稍后刷新'
]
void launchReadinessMarkers

export const tabs = [
  { label: '全部', value: 'ALL' as const },
  { label: '订单', value: 'ORDER' as const },
  { label: '私信', value: 'CHAT' as const },
  { label: '审核', value: 'AUDIT' as const },
  { label: '系统', value: 'SYSTEM' as const }
]

export function filterNotifications(notices: NotificationItemResponse[], active: NoticeType): NotificationItemResponse[] {
  return active === 'ALL' ? notices : notices.filter((item) => item.type === active)
}

export function isValidNotificationType(value: unknown): value is NotificationType {
  return value === 'ORDER' || value === 'CHAT' || value === 'AUDIT' || value === 'SYSTEM'
}

export function isValidNotificationNo(value?: string | null) {
  return /^[A-Za-z0-9_-]{6,64}$/.test(String(value || ''))
}

export function assertNotificationItem(value: unknown): asserts value is NotificationItemResponse {
  if (!value || typeof value !== 'object') throw new Error('notification invalid item')
  const item = value as NotificationItemResponse
  if (!isValidNotificationNo(item.notificationNo)) throw new Error('notification invalid notificationNo')
  if (!Number.isSafeInteger(item.userId) || item.userId <= 0) throw new Error('notification invalid userId')
  if (!isValidNotificationType(item.type)) throw new Error('notification invalid type')
  if (typeof item.title !== 'string' || !item.title.trim()) throw new Error('notification invalid title')
  if (typeof item.description !== 'string') throw new Error('notification invalid description')
  if (typeof item.read !== 'boolean') throw new Error('notification invalid read state')
  if (typeof item.createdAt !== 'string' || !item.createdAt) throw new Error('notification invalid createdAt')
  if (item.targetUrl != null && (typeof item.targetUrl !== 'string' || !isSafeNotificationTargetUrl(item.targetUrl))) throw new Error('notification invalid targetUrl')
}

export function assertNotificationList(value: unknown): asserts value is NotificationItemResponse[] {
  if (!Array.isArray(value)) throw new Error('notification invalid list')
  value.forEach(assertNotificationItem)
}

export function iconFor(type: NotificationType) {
  return ({ ORDER: '📦', CHAT: '💬', AUDIT: '🛡️', SYSTEM: '🔔' } as Record<NotificationType, string>)[type] ?? '🔔'
}

export function formatTime(value: string) {
  if (!value) return '--'
  return value.replace('T', ' ').slice(0, 16)
}

export function isSafeNotificationTargetUrl(value?: string | null) {
  if (!value) return false
  if (!/^\/pages\/[A-Za-z0-9/_-]+\/index(?:\?[A-Za-z0-9%=&_.:-]+)?$/.test(value)) return false
  if (value.startsWith('/pages/after-sales/detail/index?')) return isSafeAfterSalesDetailTargetUrl(value)
  return true
}

export function isSafeAfterSalesDetailTargetUrl(value: string): boolean {
  try {
    const query = value.split('?')[1] || ''
    const params = new URLSearchParams(query)
    const afterSalesNo = params.get('afterSalesNo') || ''
    const orderNo = params.get('orderNo') || ''
    return /^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(afterSalesNo) && /^OD-[0-9]{1,10}$/.test(orderNo)
  } catch {
    return false
  }
}

export function isTabBarNotificationTargetUrl(value?: string | null) {
  return value === '/pages/tabbar/home/index' ||
    value === '/pages/tabbar/category/index' ||
    value === '/pages/tabbar/publish/index' ||
    value === '/pages/tabbar/message/index' ||
    value === '/pages/tabbar/me/index'
}
