import { get, post } from '../http'

export type NotificationType = 'ORDER' | 'CHAT' | 'AUDIT' | 'SYSTEM'

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

export function listNotifications(type: 'ALL' | NotificationType = 'ALL') {
  return get<NotificationItemResponse[]>('/api/notifications', { type })
}

export function markNotificationRead(notificationNo: string) {
  return post<NotificationItemResponse>(`/api/notifications/${encodeURIComponent(notificationNo)}/read`, {})
}
