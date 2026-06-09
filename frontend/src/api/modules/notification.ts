import { get, post } from '../http'

export type NotificationType = 'ORDER' | 'CHAT' | 'AUDIT' | 'SYSTEM' | 'FOLLOW' | 'LIKE' | 'COMMENT' | 'GIFT'

export interface NotificationItemResponse {
  notificationNo: string
  userId: number
  type: NotificationType
  title: string
  description: string
  targetUrl?: string | null
  read: boolean
  createdAt: string
  readAt?: string | null
}

export function listNotifications(type: 'ALL' | NotificationType = 'ALL', limit = 50) {
  return get<NotificationItemResponse[]>('/api/notifications', { type, limit })
}

export function markNotificationRead(notificationNo: string) {
  return post<NotificationItemResponse>(`/api/notifications/${encodeURIComponent(notificationNo)}/read`, {})
}
