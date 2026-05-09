import { post } from '../http'

export type MediaUploadScene =
  | 'VIDEO_IDENTITY'
  | 'PRODUCT_IMAGE'
  | 'COMMUNITY_IMAGE'
  | 'AFTER_SALES_EVIDENCE'
  | 'REPORT_EVIDENCE'
  | 'CHAT_IMAGE'

export interface CreateMediaUploadTicketRequest {
  scene: MediaUploadScene
  contentType: string
  fileSize: number
  filename: string
}

export interface MediaUploadTicketResponse {
  ticketNo: string
  ownerUserId: number
  scene: string
  contentType: string
  fileSize: number
  storageUrl: string
  uploadToken: string
  status: string
  expiresAt: string
}

export function createMediaUploadTicket(data: CreateMediaUploadTicketRequest) {
  return post<MediaUploadTicketResponse>('/api/media/upload-tickets', data)
}
