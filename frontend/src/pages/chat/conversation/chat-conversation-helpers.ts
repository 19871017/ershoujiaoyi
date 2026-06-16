import type {
  ChatConversationItem,
  ChatConversationListResponse,
  ChatMessageItem,
  MessageSyncResponse,
  SendMessageResponse
} from '../../../api/modules/chat'
import { isDefaultAvatarUrl } from '../../../utils/default-avatar'
import { assertChatPeerIdentityFields } from '../chat-peer'

export const launchReadinessMarkers = [
  '对方资料暂时不可用，仍可继续聊天',
  '聊天图片票据需使用有效选择文件',
  '如涉及交易，请以平台订单、支付和售后状态为准'
]

export const chatImageStoragePrefix = '/uploads/chat-image/'
export const chatVoiceStoragePrefix = '/uploads/chat-voice/'
export const chatVideoStoragePrefix = '/uploads/chat-video/'
export const communityImageStoragePrefix = '/uploads/community-image/'
export const avatarImageStoragePrefix = '/uploads/avatar/'
export const MAX_CHAT_IMAGE_UPLOAD_BYTES = 10_000_000
export const MAX_CHAT_VIDEO_UPLOAD_BYTES = 80_000_000

export type ImageContentType = 'image/png' | 'image/webp' | 'image/jpeg'
export type VoiceContentType = 'audio/webm' | 'audio/mp4' | 'audio/mpeg' | 'audio/wav' | 'audio/aac' | 'audio/x-m4a'
export type VideoContentType = 'video/mp4' | 'video/quicktime' | 'video/x-m4v' | 'video/webm'
export type ChooseImageFile = { name?: string; type?: string; size?: number }
export type ChooseVideoFile = { name?: string; type?: string; size?: number; path?: string }

export class ChatDataIntegrityError extends Error {}

export function isKnownChatMessageType(value: unknown): value is 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO' {
  return value === 'TEXT' || value === 'IMAGE' || value === 'VOICE' || value === 'VIDEO'
}

export function isValidBackendId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function decodeRouteValue(fieldName: string, value: string): string {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    console.warn('chat conversation route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return ''
  }
}

export function routeValue(options: Record<string, string | undefined> | undefined, fieldName: string): string {
  const directValue = options?.[fieldName]
  if (directValue) return directValue
  if (typeof window === 'undefined') return ''
  try {
    return new URLSearchParams(window.location.hash.split('?')[1] || '').get(fieldName) || ''
  } catch (error) {
    console.warn('chat conversation route hash read failed', { fieldName, error })
    return ''
  }
}

export function readPositiveRouteNumber(options: Record<string, string | undefined> | undefined, fieldName: string): number | undefined {
  const value = Number(decodeRouteValue(fieldName, routeValue(options, fieldName)))
  return isValidBackendId(value) ? value : undefined
}

export function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse {
  if (!value || typeof value !== 'object') throw new Error('chat conversation list invalid response')
  const response = value as ChatConversationListResponse
  if (!Array.isArray(response.conversations)) throw new Error('chat conversation list invalid conversations')
  for (const item of response.conversations) assertConversationItem(item)
}

export function assertConversationItem(value: unknown): asserts value is ChatConversationItem {
  if (!value || typeof value !== 'object') throw new Error('chat conversation invalid item')
  const item = value as ChatConversationItem
  if (!isValidBackendId(item.conversationId) || !isValidBackendId(item.peerUserId)) throw new Error('chat conversation invalid ids')
  if (!Number.isSafeInteger(item.lastServerSeq) || item.lastServerSeq < 0) throw new Error('chat conversation invalid lastServerSeq')
  if (!Number.isSafeInteger(item.readSeq) || item.readSeq < 0) throw new Error('chat conversation invalid readSeq')
  if (!Number.isSafeInteger(item.unreadCount) || item.unreadCount < 0) throw new Error('chat conversation invalid unreadCount')
  if (typeof item.updatedAt !== 'string') throw new Error('chat conversation invalid updatedAt')
  if (item.peerNickname != null && typeof item.peerNickname !== 'string') throw new Error('chat conversation invalid peerNickname')
  if (item.peerAvatarUrl != null && typeof item.peerAvatarUrl !== 'string') throw new Error('chat conversation invalid peerAvatarUrl')
  assertChatPeerIdentityFields(item)
}

export function assertMessageSyncResponse(value: unknown): asserts value is MessageSyncResponse {
  if (!value || typeof value !== 'object') throw new ChatDataIntegrityError('chat sync invalid response')
  const response = value as MessageSyncResponse
  if (!Array.isArray(response.messages)) throw new ChatDataIntegrityError('chat sync invalid messages')
  if (!Number.isSafeInteger(response.nextAfterSeq) || response.nextAfterSeq < 0) throw new ChatDataIntegrityError('chat sync invalid nextAfterSeq')
  if (typeof response.hasMore !== 'boolean') throw new ChatDataIntegrityError('chat sync invalid hasMore')
  if (response.previousBeforeSeq != null && (!Number.isSafeInteger(response.previousBeforeSeq) || response.previousBeforeSeq < 0)) throw new ChatDataIntegrityError('chat sync invalid previousBeforeSeq')
  if (response.hasEarlier != null && typeof response.hasEarlier !== 'boolean') throw new ChatDataIntegrityError('chat sync invalid hasEarlier')
}

export function parseMessageContentForValidation(message: ChatMessageItem): Record<string, unknown> {
  const content = safeParsedMessageContent(message)
  if (!content) {
    throw new ChatDataIntegrityError('chat message contentJson malformed')
  }
  return content
}

export function safeParsedMessageContent(message: Pick<ChatMessageItem, 'contentJson'>): Record<string, unknown> | null {
  try {
    return parseChatContentObject(message.contentJson)
  } catch {
    return null
  }
}

export function assertParsableChatMessageContent(message: ChatMessageItem): void {
  if (message.revoked === true) return
  parseMessageContentForValidation(message)
}

export function parseChatContentObject(contentJson: string): Record<string, unknown> {
  const candidates = [
    contentJson,
    contentJson.trim().replace(/\\"/g, '"')
  ]
  for (const candidate of candidates) {
    try {
      const parsed = JSON.parse(candidate) as unknown
      if (typeof parsed === 'string') return parseChatContentObject(parsed)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed as Record<string, unknown>
    } catch {
      // try the next legacy encoding form
    }
  }
  throw new Error('chat content json malformed')
}

export function assertChatMessage(value: unknown): asserts value is ChatMessageItem {
  if (!value || typeof value !== 'object') throw new ChatDataIntegrityError('chat message invalid item')
  const message = value as ChatMessageItem
  if (!isValidBackendId(message.conversationId) || !isValidBackendId(message.senderId) || !isValidBackendId(message.receiverId)) throw new ChatDataIntegrityError('chat message invalid ids')
  if (!Number.isSafeInteger(message.serverSeq) || message.serverSeq <= 0) throw new ChatDataIntegrityError('chat message invalid serverSeq')
  if (typeof message.serverMsgId !== 'string' || !message.serverMsgId.trim()) throw new ChatDataIntegrityError('chat message invalid serverMsgId')
  if (typeof message.clientMsgId !== 'string' || !message.clientMsgId.trim()) throw new ChatDataIntegrityError('chat message invalid clientMsgId')
  if (typeof message.msgType !== 'string' || !message.msgType.trim()) throw new ChatDataIntegrityError('chat message invalid msgType')
  if (typeof message.contentJson !== 'string' || !message.contentJson.trim()) throw new ChatDataIntegrityError('chat message invalid contentJson')
  if (typeof message.createdAt !== 'string' || !message.createdAt) throw new ChatDataIntegrityError('chat message invalid createdAt')
  if (message.revoked != null && typeof message.revoked !== 'boolean') throw new ChatDataIntegrityError('chat message invalid revoked')
}

export function assertSendMessageResponse(value: unknown): asserts value is SendMessageResponse {
  if (!value || typeof value !== 'object') throw new Error('chat send invalid response')
  const response = value as SendMessageResponse
  const ack = response.ack
  if (!ack || typeof ack !== 'object') throw new Error('chat send invalid ack')
  if (!isValidBackendId(ack.conversationId) || !Number.isSafeInteger(ack.serverSeq) || ack.serverSeq <= 0) throw new Error('chat send invalid ack ids')
  if (typeof ack.serverMsgId !== 'string' || !ack.serverMsgId.trim()) throw new Error('chat send invalid serverMsgId')
  if (typeof ack.clientMsgId !== 'string' || !ack.clientMsgId.trim()) throw new Error('chat send invalid clientMsgId')
  if (!isValidBackendId(ack.senderId) || !isValidBackendId(ack.receiverId)) throw new Error('chat send invalid participant ids')
  if (ack.msgType !== 'TEXT' && ack.msgType !== 'IMAGE' && ack.msgType !== 'VOICE' && ack.msgType !== 'VIDEO') throw new Error('chat send invalid msgType')
}

export function isValidVoiceMessageContent(content: Record<string, unknown>): boolean {
  if (content.revoked === true || content.recalled === true) return true
  const duration = normalizedVoiceDurationSeconds(content)
  const url = content.url ?? content.audioUrl ?? content.voiceUrl
  if (!Number.isFinite(duration) || duration < 0 || duration > 600) return false
  if (typeof url !== 'string' || !url) return duration > 0
  return isValidChatVoiceStorageUrl(url)
}

export function normalizedVoiceDurationSeconds(content: Record<string, unknown> | null | undefined): number {
  if (!content) return 0
  const seconds = Number(content.durationSeconds ?? content.duration ?? 0)
  if (Number.isFinite(seconds) && seconds > 0) return seconds
  const milliseconds = Number(content.durationMs ?? 0)
  return Number.isFinite(milliseconds) && milliseconds > 0 ? milliseconds / 1000 : 0
}

export function guessImageMime(path: string, fallbackType?: string): ImageContentType {
  const normalizedType = fallbackType?.toLowerCase()
  if (normalizedType === 'image/png' || normalizedType === 'image/webp' || normalizedType === 'image/jpeg') return normalizedType
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}

export function filenameFromPath(path: string, fallbackName = `chat-${Date.now()}.jpg`): string {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || fallbackName
  return last.includes('.') ? last : fallbackName
}

export function imageFileSize(file?: ChooseImageFile): number {
  const selectedSize = Number(file?.size)
  const requestedSize = Number.isFinite(selectedSize) && selectedSize > 0 ? selectedSize : MAX_CHAT_IMAGE_UPLOAD_BYTES
  return Math.max(1, Math.min(requestedSize, MAX_CHAT_IMAGE_UPLOAD_BYTES))
}

export function imageFallbackName(contentType: ImageContentType): string {
  if (contentType === 'image/png') return 'chat-image.png'
  if (contentType === 'image/webp') return 'chat-image.webp'
  return 'chat-image.jpg'
}

export function normalizedVoiceMimeType(mimeType?: string): VoiceContentType {
  const normalized = mimeType?.split(';')[0]?.trim().toLowerCase()
  if (normalized === 'audio/mp4' || normalized === 'audio/mpeg' || normalized === 'audio/wav' || normalized === 'audio/aac' || normalized === 'audio/x-m4a') return normalized
  return 'audio/webm'
}

export function voiceFallbackName(contentType: VoiceContentType): string {
  if (contentType === 'audio/mpeg') return 'chat-voice.mp3'
  if (contentType === 'audio/wav') return 'chat-voice.wav'
  if (contentType === 'audio/aac') return 'chat-voice.aac'
  if (contentType === 'audio/mp4' || contentType === 'audio/x-m4a') return 'chat-voice.m4a'
  return 'chat-voice.webm'
}

export function guessVideoMime(path: string, fallbackType?: string): VideoContentType {
  const normalizedType = fallbackType?.split(';')[0]?.trim().toLowerCase()
  if (normalizedType === 'video/mp4' || normalizedType === 'video/quicktime' || normalizedType === 'video/x-m4v' || normalizedType === 'video/webm') return normalizedType
  const lower = path.toLowerCase().split('?')[0] || ''
  if (lower.endsWith('.mov')) return 'video/quicktime'
  if (lower.endsWith('.m4v')) return 'video/x-m4v'
  if (lower.endsWith('.webm')) return 'video/webm'
  return 'video/mp4'
}

export function videoFallbackName(contentType: VideoContentType): string {
  if (contentType === 'video/quicktime') return 'chat-video.mov'
  if (contentType === 'video/x-m4v') return 'chat-video.m4v'
  if (contentType === 'video/webm') return 'chat-video.webm'
  return 'chat-video.mp4'
}

export function videoFileSize(file?: ChooseVideoFile, pickerSize?: number): number {
  const selectedSize = Number(file?.size ?? pickerSize)
  const requestedSize = Number.isFinite(selectedSize) && selectedSize > 0 ? selectedSize : MAX_CHAT_VIDEO_UPLOAD_BYTES
  return Math.max(1, Math.min(requestedSize, MAX_CHAT_VIDEO_UPLOAD_BYTES))
}

export function hasInvalidChatVoiceStorageUrl(url: unknown): boolean {
  return hasInvalidStoredImageUrl(url, chatVoiceStoragePrefix)
}

export function hasInvalidChatVideoStorageUrl(url: unknown): boolean {
  return hasInvalidStoredImageUrl(url, chatVideoStoragePrefix)
}

export function isPickerCancel(error: unknown): boolean {
  return typeof error === 'object' && error !== null && String((error as { errMsg?: unknown }).errMsg || '').toLowerCase().includes('cancel')
}

export function hasInvalidTempChatImagePath(path: string): boolean {
  const lower = path.toLowerCase()
  const isH5BlobPath = lower.startsWith('blob:')
  return !path ||
    path.startsWith('local://') ||
    path.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..') ||
    (!isH5BlobPath && path.includes('//'))
}

export function hasInvalidTempChatVideoPath(path: string): boolean {
  const lower = path.toLowerCase()
  const isH5BlobPath = lower.startsWith('blob:')
  const allowedTempScheme = lower.startsWith('wxfile://') || lower.startsWith('file://') || lower.startsWith('ttfile://') || lower.startsWith('cdvfile://')
  return !path ||
    path.startsWith('local://') ||
    path.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('preview') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    path.includes('\\') ||
    path.includes('..') ||
    (!isH5BlobPath && path.includes('//') && !allowedTempScheme)
}

export function hasInvalidStoredImageUrl(url: unknown, storagePrefix: string): boolean {
  if (typeof url !== 'string' || !url.startsWith(storagePrefix)) return true
  const lower = url.toLowerCase()
  const relativePath = url.slice(storagePrefix.length)
  return !relativePath ||
    url.startsWith('local://') ||
    url.startsWith('blob:') ||
    url.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    url.includes('\\') ||
    url.includes('..') ||
    url.includes('//') ||
    relativePath.split('/').some((segment) => !segment)
}

export function hasInvalidChatImageStorageUrl(url: unknown): boolean {
  return hasInvalidStoredImageUrl(url, chatImageStoragePrefix)
}

export function isValidChatVoiceStorageUrl(url: string): boolean {
  return !hasInvalidStoredImageUrl(url, chatVoiceStoragePrefix)
}

export function isValidChatVideoStorageUrl(url: string): boolean {
  return !hasInvalidStoredImageUrl(url, chatVideoStoragePrefix)
}

export function validatedCommunityImageUrl(url: unknown): string {
  if (hasInvalidStoredImageUrl(url, communityImageStoragePrefix)) return ''
  return typeof url === 'string' ? url : ''
}

export function validatedChatAvatarUrl(url: unknown): string {
  if (typeof url !== 'string') return ''
  if (isDefaultAvatarUrl(url)) return url
  if (!hasInvalidStoredImageUrl(url, communityImageStoragePrefix)) return url
  if (!hasInvalidStoredImageUrl(url, avatarImageStoragePrefix)) return url
  return ''
}

export function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(11, 16) : '--'
}
