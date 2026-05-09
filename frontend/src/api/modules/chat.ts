import { get, post } from '../http'

export type MessageType = 'TEXT' | 'IMAGE'
export type SendState = 'sent'

export interface SendMessageRequest {
  conversationId?: number
  clientMsgId: string
  receiverId: number
  msgType: MessageType
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
  msgType: MessageType
}

export interface SendMessageResponse {
  ack: ChatMessageAck
}

export interface ChatConversationItem {
  conversationId: number
  peerUserId: number
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
  msgType: MessageType
  contentJson: string
  createdAt: string
  deliveredToReceiver?: boolean | null
  readByReceiver?: boolean | null
}

export interface MessageSyncResponse {
  messages: ChatMessageItem[]
  nextAfterSeq: number
  hasMore: boolean
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

export function sendMessage(data: SendMessageRequest) {
  return post<SendMessageResponse>('/api/chat/messages', data)
}

export function getChatConversations() {
  return get<ChatConversationListResponse>('/api/chat/conversations')
}

export function syncMessages(conversationId: number, afterSeq = 0, limit = 50) {
  return get<MessageSyncResponse>(`/api/chat/conversations/${conversationId}/messages`, { afterSeq, limit })
}

export function markConversationDelivered(conversationId: number) {
  return post<DeliveryReceiptResponse>(`/api/chat/conversations/${conversationId}/delivered`, {})
}

export function markConversationRead(conversationId: number, data?: ReadConversationRequest) {
  return post<ReadConversationResponse>(`/api/chat/conversations/${conversationId}/read`, data ?? {})
}
