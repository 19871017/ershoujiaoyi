import { get, post } from '../http'

export type MessageType = 'TEXT' | 'IMAGE' | 'VOICE'
export type SendableMessageType = 'TEXT' | 'IMAGE' | 'VOICE'
export type SendState = 'sent'

export interface SendMessageRequest {
  conversationId?: number
  clientMsgId: string
  receiverId: number
  msgType: SendableMessageType
  contentJson: string
}

export interface ChatMessageAck {
  messageId: string
  conversationId: number
  serverSeq: number
  serverMsgId: string
  clientMsgId: string
  sendState: SendState
  serverTs: string
  senderId: number
  receiverId: number
  msgType: string
}

export interface SendMessageResponse {
  ack: ChatMessageAck
}

export interface ChatConversationItem {
  conversationId: number
  peerUserId: number
  peerNickname?: string | null
  peerAvatarUrl?: string | null
  peerGender?: string | null
  peerCity?: string | null
  peerMainRole?: string | null
  peerVideoVerified?: boolean | null
  peerSellerCharmScore?: number | null
  peerBuyerPowerScore?: number | null
  lastMessageSummary?: string
  lastServerSeq: number
  deliveredSeq: number
  readSeq: number
  unreadCount: number
  updatedAt: string
}

export interface ChatConversationListResponse {
  conversations: ChatConversationItem[]
}

export interface ChatMessageItem {
  conversationId: number
  serverSeq: number
  serverMsgId: string
  clientMsgId: string
  senderId: number
  receiverId: number
  msgType: string
  contentJson: string
  createdAt: string
  deliveredToReceiver?: boolean | null
  readByReceiver?: boolean | null
  revoked?: boolean | null
}

export interface MessageSyncResponse {
  messages: ChatMessageItem[]
  nextAfterSeq: number
  hasMore: boolean
  previousBeforeSeq?: number | null
  hasEarlier?: boolean | null
}

export interface ReadConversationRequest {
  readSeq?: number
}

export interface ReadConversationResponse {
  conversationId: number
  readSeq: number
  deliveredSeq: number
  lastServerSeq: number
  unreadCount: number
}

export interface DeliveryReceiptResponse {
  conversationId: number
  deliveredSeq: number
  readSeq: number
  lastServerSeq: number
  unreadCount: number
}

export interface RevokeMessageResponse {
  conversationId: number
  serverSeq: number
  serverMsgId: string
  revoked: boolean
}

export interface ClearConversationResponse {
  conversationId: number
  clearedSeq: number
  lastServerSeq: number
}

export function sendMessage(data: SendMessageRequest) {
  return post<SendMessageResponse>('/api/chat/messages', data)
}

export function getChatConversations() {
  return get<ChatConversationListResponse>('/api/chat/conversations')
}

export function getChatConversation(conversationId: number) {
  return get<ChatConversationItem>(`/api/chat/conversations/${conversationId}`)
}

export function syncMessages(conversationId: number, afterSeq = 0, limit = 50) {
  return get<MessageSyncResponse>(`/api/chat/conversations/${conversationId}/messages`, { afterSeq, limit })
}

export function syncRecentMessages(conversationId: number, limit = 50) {
  return get<MessageSyncResponse>(`/api/chat/conversations/${conversationId}/messages`, { limit, latest: true })
}

export function syncEarlierMessages(conversationId: number, beforeSeq: number, limit = 50) {
  return get<MessageSyncResponse>(`/api/chat/conversations/${conversationId}/messages`, { beforeSeq, limit })
}

export function markConversationDelivered(conversationId: number) {
  return post<DeliveryReceiptResponse>(`/api/chat/conversations/${conversationId}/delivered`, {})
}

export function markConversationRead(conversationId: number, data?: ReadConversationRequest) {
  return post<ReadConversationResponse>(`/api/chat/conversations/${conversationId}/read`, data ?? {})
}

export function revokeMessage(serverMsgId: string) {
  return post<RevokeMessageResponse>(`/api/chat/messages/${encodeURIComponent(serverMsgId)}/revoke`, {})
}

export function clearConversation(conversationId: number) {
  return post<ClearConversationResponse>(`/api/chat/conversations/${conversationId}/clear`, {})
}
