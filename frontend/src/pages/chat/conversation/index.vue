<template>
  <view class="chat-page">
    <view class="chat-header">
      <view class="peer-avatar" :class="{ image: !!peerAvatarUrl }">
        <image v-if="peerAvatarUrl" class="peer-avatar-img" :src="peerAvatarUrl" mode="aspectFill" />
        <text v-else>{{ peerAvatar }}</text>
      </view>
      <view class="peer-main">
        <view class="peer-name-row">
          <view class="peer-name">{{ peerName }}</view>
          <view class="peer-level" :class="{ charm: peerLevel.track === 'CHARM', power: peerLevel.track === 'POWER' }">LV.{{ peerLevel.level }} {{ peerLevel.title }}</view>
        </view>
        <view class="peer-badges">
          <text v-for="badge in peerIdentityBadges" :key="badge">{{ badge }}</text>
        </view>
      </view>
      <view class="report tapable" @click="reportConversation">举报</view>
    </view>

    <scroll-view class="message-scroll" scroll-y>
      <view v-if="messages.length === 0" class="empty-card">{{ emptyMessageText }}</view>
      <view v-for="message in messages" :key="message.serverMsgId" class="bubble-row" :class="{ mine: isMine(message) }">
        <view class="bubble">
          <image v-if="chatImageMessageUrl(message)" class="message-image" :src="chatImageMessageUrl(message)" mode="aspectFill" />
          <view v-else class="message-body" :class="{ image: message.msgType === 'IMAGE' }">{{ renderMessage(message) }}</view>
          <view class="message-meta">
            #{{ message.serverSeq }} · {{ formatTime(message.createdAt) }} · {{ receiptText(message) }}
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="composer">
      <view class="tool tapable" :class="{ disabled: chatBlocked }" @click="sendImagePlaceholder">＋</view>
      <input :value="draft" class="field" confirm-type="send" placeholder="问尺码、瑕疵、发货时间..." :disabled="chatBlocked" @input="updateDraft" @confirm="handleSendText" />
      <button class="send-btn" :disabled="chatBlocked || sending" @click="handleSendText">{{ sending ? '...' : '发送' }}</button>
    </view>
    <view v-if="statusText" class="status-text">{{ statusText }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getChatConversations, markConversationRead, sendMessage, syncMessages, type ChatConversationItem, type ChatMessageItem, type SendMessageRequest, type SendMessageResponse } from '../../../api/modules/chat'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, getPublicProfile, type UserProfileResponse } from '../../../api/modules/user'
import { buildChatPeerLevel, chatPeerIdentityBadges } from '../chat-peer'
import {
  ChatDataIntegrityError,
  assertChatMessage,
  assertConversationListResponse,
  assertMessageSyncResponse,
  assertSendMessageResponse,
  formatTime,
  filenameFromPath,
  guessImageMime,
  hasInvalidChatImageStorageUrl,
  hasInvalidTempChatImagePath,
  imageFallbackName,
  imageFileSize,
  isPickerCancel,
  isValidBackendId,
  parseMessageContentForValidation,
  readPositiveRouteNumber,
  validatedCommunityImageUrl,
  type ChooseImageFile
} from './chat-conversation-helpers'

const currentUserId = ref<number | null>(null)
const draft = ref('')
const sending = ref(false)
const loadingMessages = ref(false)
const discoveringConversation = ref(false)
const chatBlocked = ref(false)
const statusText = ref('')
const conversationId = ref<number | undefined>()
const receiverId = ref<number | undefined>()
const messages = ref<ChatMessageItem[]>([])
const nextAfterSeq = ref(0)
const hasMore = ref(false)
let syncTimer: ReturnType<typeof setInterval> | null = null
let discoveryFailureCount = 0
let autoReadInFlight = false
const receiptRefreshWindow = 200
const peerName = ref('聊天用户')
const peerAvatarUrl = ref('')
const peerGender = ref<string | null>(null)
const peerCity = ref<string | null>(null)
const peerVideoVerified = ref(false)
const peerSellerCharmScore = ref(0)
const peerBuyerPowerScore = ref(0)
const peerAvatar = computed(() => peerName.value.slice(0, 1))
const peerIdentitySource = computed(() => ({
  peerGender: peerGender.value,
  peerCity: peerCity.value,
  peerVideoVerified: peerVideoVerified.value,
  peerSellerCharmScore: peerSellerCharmScore.value,
  peerBuyerPowerScore: peerBuyerPowerScore.value
}))
const peerLevel = computed(() => buildChatPeerLevel(peerIdentitySource.value))
const peerIdentityBadges = computed(() => chatPeerIdentityBadges(peerIdentitySource.value).filter((badge) => !badge.startsWith('LV.')))
const emptyMessageText = computed(function emptyChatMessageText(): string {
  if (conversationId.value) return '消息暂不可用，请等待服务端会话同步。'
  if (receiverId.value) return '等待平台会话创建；对方发来第一条消息后会自动同步。'
  return '缺少有效会话编号，不能展示或发送聊天消息'
})

function blockChat(reason: string): void {
  chatBlocked.value = true
  messages.value = []
  conversationId.value = undefined
  stopMessageSync()
  statusText.value = reason
}

function assertSendMessageResponseForRequest(value: unknown, payload: SendMessageRequest): asserts value is SendMessageResponse {
  assertSendMessageResponse(value)
  const ack = value.ack
  if (ack.clientMsgId !== payload.clientMsgId) throw new Error('chat send ack clientMsgId mismatch')
  if (ack.receiverId !== payload.receiverId) throw new Error('chat send ack receiver mismatch')
  if (currentUserId.value && ack.senderId !== currentUserId.value) throw new Error('chat send ack sender mismatch')
  if (ack.msgType !== payload.msgType) throw new Error('chat send ack msgType mismatch')
  if (payload.conversationId && ack.conversationId !== payload.conversationId) throw new Error('chat send ack conversation mismatch')
  if (messages.value.some((message) => message.serverSeq === ack.serverSeq || message.serverMsgId === ack.serverMsgId)) throw new Error('chat send ack duplicate server message')
}

async function loadCurrentUser(): Promise<void> {
  try {
    const profile = await getMyProfile()
    if (!isValidBackendId(profile.userId)) throw new Error('chat current userId invalid')
    currentUserId.value = profile.userId
  } catch (error) {
    currentUserId.value = null
    console.warn('chat current user load failed', { error })
    statusText.value = '当前登录用户加载失败，暂不能发送消息'
  }
}

async function loadPeerProfile(peerUserId: number): Promise<void> {
  peerName.value = `用户 ${peerUserId}`
  peerAvatarUrl.value = ''
  try {
    const profile = await getPublicProfile(peerUserId)
    if (String(profile.userId) !== String(peerUserId)) throw new Error('chat peer userId mismatch')
    applyPeerProfile(profile)
  } catch (error) {
    peerAvatarUrl.value = ''
    statusText.value = '聊天用户资料暂不可用，消息仍以平台会话为准'
    console.warn('chat peer profile load failed', { peerUserId, error })
  }
}

function applyPeerProfile(profile: UserProfileResponse): void {
  peerName.value = profile.nickname || `用户 ${profile.userId}`
  peerAvatarUrl.value = validatedCommunityImageUrl(profile.avatarUrl || '')
  peerGender.value = typeof profile.gender === 'string' ? profile.gender : null
  peerCity.value = typeof profile.city === 'string' ? profile.city : null
  peerVideoVerified.value = profile.videoVerified === true
  peerSellerCharmScore.value = Math.max(0, Math.floor(Number(profile.sellerCharmScore || 0)))
  peerBuyerPowerScore.value = Math.max(0, Math.floor(Number(profile.buyerPowerScore || 0)))
}

function applyPeerConversationItem(item: ChatConversationItem): void {
  peerName.value = item.peerNickname || `用户 ${item.peerUserId}`
  peerAvatarUrl.value = validatedCommunityImageUrl(item.peerAvatarUrl || '')
  peerGender.value = item.peerGender || null
  peerCity.value = item.peerCity || null
  peerVideoVerified.value = item.peerVideoVerified === true
  peerSellerCharmScore.value = Math.max(0, Math.floor(Number(item.peerSellerCharmScore || 0)))
  peerBuyerPowerScore.value = Math.max(0, Math.floor(Number(item.peerBuyerPowerScore || 0)))
}

onLoad((options) => {
  void initializeChatPage(options)
})

onUnload(stopMessageSync)

async function initializeChatPage(options: Record<string, string | undefined> | undefined): Promise<void> {
  await loadCurrentUser()
  const routeConversationId = readPositiveRouteNumber(options, 'conversationId')
  const routeReceiverId = readPositiveRouteNumber(options, 'receiverId')
  if (routeReceiverId) {
    receiverId.value = routeReceiverId
    void loadPeerProfile(routeReceiverId)
  }
  if (routeConversationId && receiverId.value) {
    try {
      const matched = await verifyRouteConversation(routeConversationId, receiverId.value)
      applyPeerConversationItem(matched)
      chatBlocked.value = false
      conversationId.value = matched.conversationId
      await loadMoreMessages()
      startMessageSync()
    } catch (error) {
      console.warn('chat route conversation verification failed', { routeConversationId, receiverId: receiverId.value, error })
      blockChat('会话与聊天对象不匹配，不能展示或发送聊天消息')
    }
  } else if (receiverId.value) {
    chatBlocked.value = false
    statusText.value = '可发送第一条消息创建平台会话'
    void discoverConversationWithPeer(true)
    startMessageSync()
  } else {
    blockChat('缺少有效会话编号，不能展示或发送聊天消息')
  }
}

async function verifyRouteConversation(routeConversationId: number, routeReceiverId: number): Promise<ChatConversationItem> {
  const response = await getChatConversations()
  assertConversationListResponse(response)
  const matched = response.conversations.find((item) => item.conversationId === routeConversationId && item.peerUserId === routeReceiverId)
  if (!matched) throw new Error('chat route conversation/peer mismatch')
  return matched
}

async function loadMoreMessages(): Promise<void> {
  await syncConversationMessages(true, false)
}

function startMessageSync(): void {
  if (chatBlocked.value || syncTimer) return
  syncTimer = setInterval(() => {
    if (chatBlocked.value) return
    if (conversationId.value) void syncConversationMessages(false, true)
    else if (receiverId.value) void discoverConversationWithPeer(false)
  }, 5000)
}

function stopMessageSync(): void {
  if (!syncTimer) return
  clearInterval(syncTimer)
  syncTimer = null
}

async function discoverConversationWithPeer(showStatus: boolean): Promise<boolean> {
  if (chatBlocked.value || discoveringConversation.value || conversationId.value || !receiverId.value) return false
  discoveringConversation.value = true
  try {
    const response = await getChatConversations()
    assertConversationListResponse(response)
    const matched = response.conversations.find((item) => item.peerUserId === receiverId.value)
    if (!matched) {
      discoveryFailureCount = 0
      if (showStatus) statusText.value = '等待平台会话创建；对方发来第一条消息后会自动同步'
      return false
    }
    discoveryFailureCount = 0
    chatBlocked.value = false
    conversationId.value = matched.conversationId
    applyPeerConversationItem(matched)
    statusText.value = '已接入平台会话，正在同步消息'
    await syncConversationMessages(true, true)
    return true
  } catch (error) {
    discoveryFailureCount += 1
    console.warn('chat conversation discovery failed', { receiverId: receiverId.value, discoveryFailureCount, error })
    if (showStatus || discoveryFailureCount >= 3) statusText.value = '会话同步暂不可用，请稍后重试'
    return false
  } finally {
    discoveringConversation.value = false
  }
}

function assertActiveConversationMessage(message: ChatMessageItem, activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number): void {
  assertChatMessage(message)
  if (message.conversationId !== activeConversationId) throw new ChatDataIntegrityError('chat message conversation mismatch')
  const sentByCurrentUser = message.senderId === activeCurrentUserId && message.receiverId === activeReceiverId
  const receivedByCurrentUser = message.senderId === activeReceiverId && message.receiverId === activeCurrentUserId
  if (!sentByCurrentUser && !receivedByCurrentUser) throw new ChatDataIntegrityError('chat message participant mismatch')
}

function mergeServerMessages(serverMessages: ChatMessageItem[]): void {
  if (!serverMessages.length) return
  const activeConversationId = conversationId.value
  const activeCurrentUserId = currentUserId.value
  const activeReceiverId = receiverId.value
  if (!activeConversationId || !activeCurrentUserId || !activeReceiverId) throw new Error('chat message merge missing active conversation state')
  const bySeq = new Map(messages.value.map((message) => [message.serverSeq, message]))
  for (const message of serverMessages) {
    assertActiveConversationMessage(message, activeConversationId, activeCurrentUserId, activeReceiverId)
    const existing = bySeq.get(message.serverSeq)
    bySeq.set(message.serverSeq, existing ? { ...existing, ...message } : message)
  }
  messages.value = [...bySeq.values()].sort((left, right) => left.serverSeq - right.serverSeq)
}

async function syncConversationMessages(showStatus: boolean, refreshReceipts: boolean): Promise<boolean> {
  if (loadingMessages.value || !conversationId.value) return false
  if (!currentUserId.value || !receiverId.value) {
    console.warn('chat conversation sync missing participant state', { conversationId: conversationId.value, hasCurrentUserId: !!currentUserId.value, hasReceiverId: !!receiverId.value })
    blockChat('缺少会话参与人，不能展示或发送聊天内容')
    return false
  }
  loadingMessages.value = true
  if (showStatus) statusText.value = ''
  const syncAfterSeq = nextAfterSeq.value
  const activeConversationId = conversationId.value
  const activeCurrentUserId = currentUserId.value
  const activeReceiverId = receiverId.value
  try {
    const response = await syncMessages(activeConversationId, syncAfterSeq, 50)
    assertMessageSyncResponse(response)
    for (const message of response.messages) assertActiveConversationMessage(message, activeConversationId, activeCurrentUserId, activeReceiverId)
    mergeServerMessages(response.messages)
    nextAfterSeq.value = Math.max(response.nextAfterSeq || syncAfterSeq, syncAfterSeq)
    hasMore.value = response.hasMore
    if (refreshReceipts) await refreshVisibleReceiptStates(activeConversationId, activeCurrentUserId, activeReceiverId)
    if (nextAfterSeq.value > 0) void autoMarkReadAfterSync(nextAfterSeq.value)
    if (showStatus) statusText.value = response.hasMore ? '已补拉部分消息，可继续补拉' : '消息已同步'
    return true
  } catch (error) {
    console.warn('chat conversation sync failed', { conversationId: activeConversationId, afterSeq: syncAfterSeq, refreshReceipts, error })
    if (error instanceof ChatDataIntegrityError) {
      blockChat('聊天数据校验失败，不能展示或发送聊天内容')
      return false
    }
    if (showStatus) {
      blockChat('消息暂不可用，不能展示或发送聊天内容')
      return false
    }
    statusText.value = '消息同步失败，可能有新消息未显示，请点击同步重试'
    return false
  } finally {
    loadingMessages.value = false
  }
}

function visibleReceiptRefreshAfterSeq(): number {
  const maxVisibleSeq = messages.value.reduce((maxSeq, message) => Math.max(maxSeq, message.serverSeq), 0)
  return Math.max(0, maxVisibleSeq - receiptRefreshWindow)
}

async function refreshVisibleReceiptStates(activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number): Promise<void> {
  if (messages.value.length === 0) return
  const receiptAfterSeq = visibleReceiptRefreshAfterSeq()
  const response = await syncMessages(activeConversationId, receiptAfterSeq, receiptRefreshWindow)
  assertMessageSyncResponse(response)
  for (const message of response.messages) assertActiveConversationMessage(message, activeConversationId, activeCurrentUserId, activeReceiverId)
  mergeServerMessages(response.messages)
}

async function autoMarkReadAfterSync(readSeq: number): Promise<void> {
  if (chatBlocked.value || autoReadInFlight || !conversationId.value || readSeq <= 0) return
  const activeConversationId = conversationId.value
  autoReadInFlight = true
  try {
    const response = await markConversationRead(activeConversationId, { readSeq })
    if (response.conversationId !== activeConversationId || response.readSeq > readSeq || !Number.isSafeInteger(response.unreadCount) || response.unreadCount < 0) {
      throw new Error('chat auto read response invalid')
    }
  } catch (error) {
    console.warn('chat conversation auto read receipt failed', { conversationId: activeConversationId, readSeq, error })
  } finally {
    autoReadInFlight = false
  }
}

async function handleMarkRead(): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天数据暂不可用，不能标记已读'; return }
  if (!conversationId.value) { statusText.value = '缺少有效会话编号，不能标记已读'; return }
  const requestedReadSeq = nextAfterSeq.value
  try {
    const response = await markConversationRead(conversationId.value, { readSeq: requestedReadSeq })
    if (response.conversationId !== conversationId.value || response.readSeq !== requestedReadSeq || response.readSeq > nextAfterSeq.value) throw new Error('chat read response cursor mismatch')
    statusText.value = `已读至 ${response.readSeq}`
  } catch (error) {
    console.warn('chat conversation read receipt failed', { conversationId: conversationId.value, requestedReadSeq, error })
    statusText.value = '已读状态未同步，请稍后重试'
  }
}

async function handleSendText(): Promise<void> { await handleSend('TEXT') }

function sendImagePlaceholder(): void {
  if (chatBlocked.value) { statusText.value = '聊天数据暂不可用，不能发送图片'; return }
  if (sending.value) return
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，图片暂不可用'; return }
  try {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const path = res.tempFilePaths[0] || ''
        if (!path) { statusText.value = '没有选择图片'; return }
        void handleSendImage(path, (res as { tempFiles?: ChooseImageFile[] }).tempFiles?.[0])
      },
      fail: (error) => {
        console.warn('chat image picker failed', { cancelled: isPickerCancel(error), error })
        statusText.value = isPickerCancel(error) ? '已取消选择图片' : '无法选择图片，请检查相册权限后重试'
      }
    })
  } catch (error) {
    console.warn('chat image picker failed', { error })
    statusText.value = '无法打开图片选择器，请检查相册权限后重试'
  }
}

async function handleSendImage(localPath: string, file?: ChooseImageFile): Promise<void> {
  if (!localPath || hasInvalidTempChatImagePath(localPath)) {
    statusText.value = '图片暂不可用，聊天图片票据需使用有效选择文件'
    return
  }
  sending.value = true
  statusText.value = '正在上传聊天图片...'
  try {
    const contentType = guessImageMime(localPath, file?.type)
    const fileSize = imageFileSize(file)
    const ticket = await createMediaUploadTicket({ scene: 'CHAT_IMAGE', contentType, fileSize, filename: filenameFromPath(file?.name || localPath, imageFallbackName(contentType)) })
    const uploaded = await uploadMediaTicketFile(ticket, localPath)
    if (hasInvalidChatImageStorageUrl(uploaded.storageUrl)) throw new Error('chat image storageUrl invalid')
    await handleSend('IMAGE', {
      url: uploaded.storageUrl,
      width: 720,
      height: 720,
      sizeBytes: fileSize,
      mimeType: contentType
    })
  } catch (error) {
    console.warn('chat image send failed', { pathLength: localPath.length, fileType: file?.type, fileSize: file?.size, error })
    statusText.value = '图片暂不可用，请重新选择图片'
  } finally {
    sending.value = false
  }
}

async function handleSend(type: 'TEXT' | 'IMAGE', imagePayload?: { url: string; width: number; height: number; sizeBytes: number; mimeType: string }): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天数据暂不可用，暂不能发送消息'; return }
  if (sending.value && type === 'TEXT') return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，暂不能发送消息'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，暂不能发送消息'; return }
  if (type === 'IMAGE' && (!imagePayload || hasInvalidChatImageStorageUrl(imagePayload.url))) { statusText.value = '图片暂不可用，暂不能发送消息'; return }
  const text = draft.value.trim()
  if (type === 'TEXT' && !text) { statusText.value = '消息不能为空，未发送默认聊天文案'; return }
  const payload: SendMessageRequest = {
    ...(conversationId.value ? { conversationId: conversationId.value } : {}),
    clientMsgId: `h5-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    receiverId: receiverId.value,
    msgType: type,
    contentJson: type === 'IMAGE' ? JSON.stringify(imagePayload) : JSON.stringify({ text })
  }
  let sent = false
  sending.value = true
  statusText.value = ''
  try {
    const response = await sendMessage(payload)
    assertSendMessageResponseForRequest(response, payload)
    conversationId.value = response.ack.conversationId
    pushLocalMessage(response.ack.serverSeq, response.ack.serverMsgId, payload)
    const syncedAfterSend = await syncConversationMessages(false, true)
    if (chatBlocked.value) return
    startMessageSync()
    sent = true
    if (!syncedAfterSend) statusText.value = '消息已发送，但聊天记录同步暂未完成，请点击同步重试'
    else statusText.value = hasMore.value ? '消息已发送，仍有历史消息可继续补拉' : '消息已发送'
  } catch (error) {
    console.warn('chat message send failed', { conversationId: conversationId.value, receiverId: receiverId.value, msgType: type, error })
    if (error instanceof ChatDataIntegrityError) {
      blockChat('聊天数据校验失败，不能展示或发送聊天内容')
    } else if (error instanceof Error && error.message === 'chat local send insertion missing required state') {
      statusText.value = '消息可能已发送，但本地同步失败，请点击同步'
    } else {
      statusText.value = '消息发送失败，请检查网络后重试'
    }
  } finally {
    if (sent && type === 'TEXT') draft.value = ''
    sending.value = false
  }
}

function pushLocalMessage(serverSeq: number, serverMsgId: string, payload: SendMessageRequest): void {
  if (!conversationId.value || !currentUserId.value || !receiverId.value) throw new Error('chat local send insertion missing required state')
  mergeServerMessages([{ conversationId: conversationId.value, serverSeq, serverMsgId, clientMsgId: payload.clientMsgId, senderId: currentUserId.value, receiverId: payload.receiverId, msgType: payload.msgType, contentJson: payload.contentJson, createdAt: new Date().toISOString(), deliveredToReceiver: false, readByReceiver: false }])
}

function updateDraft(event: unknown): void {
  const value = (event as { detail?: { value?: unknown } }).detail?.value
  if (typeof value !== 'string') {
    console.warn('chat draft input event invalid')
    statusText.value = '输入内容读取失败，请重新输入'
    return
  }
  draft.value = value
}

function isMine(message: ChatMessageItem): boolean { return message.senderId === currentUserId.value }

function parsedMessageContent(message: ChatMessageItem): { text?: string; url?: string } | null {
  try {
    return JSON.parse(message.contentJson) as { text?: string; url?: string }
  } catch (error) {
    blockChat('聊天数据校验失败，不能展示或发送聊天内容')
    console.warn('chat message content parse failed', { serverMsgId: message.serverMsgId, msgType: message.msgType, error })
    return null
  }
}

function chatImageMessageUrl(message: ChatMessageItem): string {
  if (message.msgType !== 'IMAGE') return ''
  const url = parsedMessageContent(message)?.url
  return hasInvalidChatImageStorageUrl(url) ? '' : url as string
}

function renderMessage(message: ChatMessageItem): string {
  const content = parsedMessageContent(message)
  if (message.msgType === 'IMAGE') return '图片暂不可用'
  return content?.text || '消息内容暂不可用'
}

function receiptText(message: ChatMessageItem): string {
  if (!isMine(message)) return '对方消息'
  if (message.readByReceiver) return '已读'
  if (message.deliveredToReceiver) return '已送达'
  return '发送中'
}

function reportConversation(): void {
  if (chatBlocked.value) {
    uni.showToast({ title: '聊天数据暂不可用，不能提交举报', icon: 'none' })
    return
  }
  if (!conversationId.value) {
    uni.showToast({ title: '缺少有效会话编号，不能提交举报', icon: 'none' })
    return
  }
  const route = {
    url: `/pages/report/submit/index?targetType=CHAT&targetId=${encodeURIComponent(String(conversationId.value))}`,
    fail: (error: unknown) => {
      console.warn('chat report navigation failed', { conversationId: conversationId.value, error })
      uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('chat report navigation failed', { conversationId: conversationId.value, error })
    uni.showToast({ title: '暂时无法打开举报页，请稍后重试', icon: 'none' })
  }
}
</script>

<style scoped lang="scss" src="./style.scss"></style>
