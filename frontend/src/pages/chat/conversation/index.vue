<template>
  <view class="chat-page" :class="{ 'has-status': !!statusText }" :style="chatPageStyle">
    <view class="chat-header">
      <view class="back-action tapable" aria-label="返回私聊列表" @click="goBackToSessions">
        <text aria-hidden="true">‹</text>
      </view>
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
          <text v-if="peerGenderBadge.symbol" class="peer-gender-mark" :class="peerGenderBadge.genderClass">{{ peerGenderBadge.symbol }}</text>
          <text v-for="badge in peerIdentityBadges" :key="badge">{{ badge }}</text>
        </view>
      </view>
      <view class="header-actions">
        <view class="header-action tapable" @click="handleClearConversation">清空</view>
        <view class="header-action report tapable" @click="reportConversation">举报</view>
      </view>
    </view>

    <view class="message-scroll-wrap">
      <scroll-view class="message-scroll" scroll-y :scroll-into-view="bottomAnchorId" scroll-with-animation>
        <view v-if="hasEarlierMessages" class="history-loader tapable" @click="loadEarlierMessages">
          {{ loadingEarlierMessages ? '加载中…' : '查看更早消息' }}
        </view>
        <view v-if="messages.length === 0" class="empty-card">{{ chatListStateText }}</view>
        <view v-for="message in messages" :key="message.serverMsgId" class="bubble-row" :class="{ mine: isMine(message) }">
          <view class="bubble">
            <view
              v-if="message.msgType === 'IMAGE' && !isMessageRevoked(message)"
              class="message-media image tapable"
              :class="{ loading: isChatMediaLoading(message), failed: hasChatMediaFailed(message), unavailable: isMessageUnavailable(message) }"
              @click="previewChatImage(message)"
            >
              <image v-if="chatImageMessageUrl(message)" class="message-image" :src="chatImageMessageUrl(message)" mode="aspectFill" />
              <view v-else class="message-media-placeholder">{{ renderMessage(message) }}</view>
            </view>
            <view
              v-else-if="message.msgType === 'VIDEO' && !isMessageRevoked(message)"
              class="message-media video"
              :class="{ loading: isChatMediaLoading(message), failed: hasChatMediaFailed(message), unavailable: isMessageUnavailable(message) }"
            >
              <video
                v-if="chatVideoMessageUrl(message)"
                class="message-video"
                :src="chatVideoMessageUrl(message)"
                :controls="true"
                :show-fullscreen-btn="true"
                :show-play-btn="true"
                :show-center-play-btn="true"
              />
              <view v-else class="message-media-placeholder tapable" @click="prepareChatVideo(message)">{{ renderMessage(message) }}</view>
            </view>
            <view
              v-else-if="message.msgType === 'VOICE' && !isMessageRevoked(message)"
              class="message-voice-bubble tapable"
              :class="{ playing: playingVoiceMessageId === message.serverMsgId, loading: isChatMediaLoading(message), failed: hasChatMediaFailed(message), unavailable: isMessageUnavailable(message) }"
              @click="handleMessageBodyTap(message)"
            >
              <view class="voice-play-disc">
                <view v-if="playingVoiceMessageId === message.serverMsgId" class="voice-play-ring" aria-hidden="true" />
                <view v-if="playingVoiceMessageId === message.serverMsgId" class="voice-playing-dot" />
                <view v-else class="voice-play-triangle" />
              </view>
              <view class="voice-wave" :class="{ active: playingVoiceMessageId === message.serverMsgId }" aria-hidden="true">
                <view class="voice-wave-bar short" />
                <view class="voice-wave-bar medium" />
                <view class="voice-wave-bar tall" />
                <view class="voice-wave-bar medium" />
                <view class="voice-wave-bar short" />
              </view>
              <view class="voice-info">
                <text class="voice-duration">{{ voiceDurationText(message) }}</text>
                <view class="voice-state-row">
                  <text class="voice-state-dot" :class="voicePlaybackStateClass(message)" />
                  <text class="voice-state">{{ voicePlaybackStateText(message) }}</text>
                </view>
              </view>
            </view>
            <view
              v-else
              class="message-body"
              :class="{ image: message.msgType === 'IMAGE', voice: message.msgType === 'VOICE', video: message.msgType === 'VIDEO', playing: playingVoiceMessageId === message.serverMsgId, revoked: isMessageRevoked(message), unavailable: isMessageUnavailable(message), unsupported: !isRenderableMessageType(message) }"
              @click="handleMessageBodyTap(message)"
            >{{ renderMessage(message) }}</view>
            <view class="message-meta">
              {{ messageMetaText(message) }}
            </view>
            <view v-if="canRevokeMessage(message)" class="message-actions">
              <text class="message-action tapable" @click.stop="handleRevokeMessage(message)">撤回</text>
            </view>
          </view>
        </view>
        <view id="chat-bottom-anchor" class="message-bottom-spacer" />
      </scroll-view>
    </view>

    <view v-if="statusText" class="status-bar">
      <text>{{ statusText }}</text>
      <button v-if="canManualSync" class="sync-btn" :disabled="loadingMessages || discoveringConversation" @click="manualSyncMessages">
        {{ loadingMessages || discoveringConversation ? '更新中' : '刷新' }}
      </button>
    </view>

    <view v-if="recording" class="recording-status">
      <view class="recording-orb">
        <view class="recording-pulse" />
      </view>
      <view class="recording-copy">
        <view class="recording-title-row">
          <text class="recording-title">正在录音</text>
          <text class="recording-time">{{ recordingElapsedText }}</text>
        </view>
        <view class="recording-bars" aria-hidden="true">
          <view class="recording-bar short" />
          <view class="recording-bar medium" />
          <view class="recording-bar tall" />
          <view class="recording-bar medium" />
          <view class="recording-bar short" />
        </view>
        <text class="recording-desc">再点麦克风发送，或取消本次录音</text>
      </view>
      <view class="recording-cancel tapable" @click.stop="cancelVoiceRecording">取消</view>
    </view>

    <view class="composer" :class="{ recording }">
      <view class="composer-tools">
        <view class="voice-control">
          <view
            class="tool voice-tool tapable"
            :class="{ disabled: composerBlocked, recording }"
            :aria-label="recording ? '结束并发送语音' : '语音录制'"
            @click="toggleVoiceRecording"
          >
            <view v-if="recording" class="voice-stop-icon" aria-hidden="true" />
            <view v-else class="voice-mic-icon" aria-hidden="true">
              <view class="voice-mic-head" />
              <view class="voice-mic-stem" />
            </view>
          </view>
        </view>
        <view
          class="tool media-tool tapable"
          :class="{ disabled: textComposerBlocked }"
          aria-label="发送图片或视频"
          @click="openMediaComposer"
        >
          <view class="media-image-icon" aria-hidden="true">
            <view class="media-image-sun" />
            <view class="media-image-mountain" />
          </view>
          <view class="media-plus-mark" aria-hidden="true">＋</view>
        </view>
      </view>
      <view class="composer-input-wrap" :class="{ focused: composerFocused, blocked: textComposerBlocked }">
        <input :value="draft" class="field" confirm-type="send" :placeholder="composerPlaceholder" :maxlength="MAX_TEXT_MESSAGE_LENGTH" :disabled="textComposerBlocked" cursor-spacing="96" @focus="handleComposerFocus" @blur="handleComposerBlur" @input="updateDraft" @confirm="handleSendText" />
      </view>
      <button class="send-btn" :class="{ ready: hasDraftText, sending }" :disabled="sendButtonDisabled" :aria-label="sendButtonLabel" @click="handleSendText">
        <view v-if="sending" class="send-loading-dot" aria-hidden="true" />
        <view v-else class="send-arrow-icon" aria-hidden="true" />
      </button>
    </view>

    <view v-if="activeImagePreviewUrl" class="image-preview-overlay" @click="closeImagePreview">
      <view class="image-preview-top">
        <text>图片预览</text>
        <view class="image-preview-close tapable" @click.stop="closeImagePreview">×</view>
      </view>
      <image class="image-preview-img" :src="activeImagePreviewUrl" mode="aspectFit" @click.stop />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { fetchAuthorizedBlobUrl, resolveBackendMediaUrl } from '../../../api/http'
import {
  clearConversation as clearChatConversation,
  getChatConversation,
  getChatConversations,
  markConversationDelivered,
  markConversationRead,
  revokeMessage as revokeChatMessage,
  sendMessage,
  syncEarlierMessages,
  syncMessages,
  syncRecentMessages,
  type ChatConversationItem,
  type ChatMessageItem,
  type SendMessageRequest,
  type SendMessageResponse
} from '../../../api/modules/chat'
import { createMediaUploadTicket, uploadMediaTicketBlob, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, getPublicProfile, type UserProfileResponse } from '../../../api/modules/user'
import { avatarUrlWithGenderFallback } from '../../../utils/default-avatar'
import { buildChatPeerLevel, chatPeerIdentityBadges, normalizedPeerGender, peerGenderSymbol } from '../chat-peer'
import {
  ChatDataIntegrityError,
  assertConversationItem,
  assertChatMessage,
  assertConversationListResponse,
  assertMessageSyncResponse,
  assertSendMessageResponse,
  formatTime,
  filenameFromPath,
  guessImageMime,
  guessVideoMime,
  hasInvalidChatVoiceStorageUrl,
  hasInvalidChatImageStorageUrl,
  hasInvalidChatVideoStorageUrl,
  hasInvalidTempChatImagePath,
  hasInvalidTempChatVideoPath,
  isKnownChatMessageType,
  imageFallbackName,
  imageFileSize,
  isPickerCancel,
  isValidBackendId,
  normalizedVoiceDurationSeconds,
  normalizedVoiceMimeType,
  assertParsableChatMessageContent,
  parseMessageContentForValidation,
  readPositiveRouteNumber,
  safeParsedMessageContent,
  videoFallbackName,
  videoFileSize,
  voiceFallbackName,
  validatedChatAvatarUrl,
  type ChooseImageFile,
  type ChooseVideoFile,
  type VoiceContentType
} from './chat-conversation-helpers'

const MAX_VOICE_RECORD_MS = 60_000
const MIN_VOICE_RECORD_MS = 600
const MAX_CHAT_VOICE_UPLOAD_BYTES = 10_000_000
const MAX_CHAT_VIDEO_UPLOAD_BYTES = 80_000_000
const MAX_CHAT_VIDEO_DURATION_MS = 300_000
const MAX_TEXT_MESSAGE_LENGTH = 1000
const KEYBOARD_FOCUS_FALLBACK_INSET = 120
const MESSAGE_REVOKE_WINDOW_MS = 2 * 60 * 1000

type VoiceRecordingMode = 'browser' | 'uni' | ''
type UniVoiceRecorderStartOptions = {
  duration?: number
  sampleRate?: number
  numberOfChannels?: number
  encodeBitRate?: number
  format?: 'mp3' | 'aac' | 'wav'
}
type UniVoiceRecorderStopResult = {
  tempFilePath?: string
  duration?: number
  fileSize?: number
}
type UniVoiceRecorderManager = {
  start: (options?: UniVoiceRecorderStartOptions) => void
  stop: () => void
  onStop: (callback: (result: UniVoiceRecorderStopResult) => void) => void
  onError: (callback: (error: unknown) => void) => void
}
type ChooseVideoResult = {
  tempFilePath: string
  duration?: number
  size?: number
  type?: string
  name?: string
  tempFile?: ChooseVideoFile
  file?: ChooseVideoFile
}
type UniInnerAudioContext = {
  src: string
  play: () => void
  stop?: () => void
  pause?: () => void
  destroy?: () => void
  onEnded?: (callback: () => void) => void
  onError?: (callback: (error: unknown) => void) => void
  offEnded?: (callback?: () => void) => void
  offError?: (callback?: (error: unknown) => void) => void
}
type ChatNavigateFail = (error: unknown) => void
type ChatNavigateBack = (options: { delta?: number; fail?: ChatNavigateFail }) => void
type ChatRedirectTo = (options: { url: string; fail?: ChatNavigateFail }) => void
type ChatSwitchTab = (options: { url: string; fail?: ChatNavigateFail }) => void

const currentUserId = ref<number | null>(null)
const draft = ref('')
const sending = ref(false)
const recording = ref(false)
const loadingMessages = ref(false)
const loadingEarlierMessages = ref(false)
const discoveringConversation = ref(false)
const chatBlocked = ref(false)
const statusText = ref('')
const conversationId = ref<number | undefined>()
const receiverId = ref<number | undefined>()
const messages = ref<ChatMessageItem[]>([])
const chatMediaBlobUrls = ref<Record<string, string>>({})
const chatMediaLoadingIds = ref<Record<string, boolean>>({})
const chatMediaFailedIds = ref<Record<string, boolean>>({})
const activeImagePreviewUrl = ref('')
const playingVoiceMessageId = ref('')
const recordingElapsedMs = ref(0)
const nextAfterSeq = ref(0)
const previousBeforeSeq = ref(0)
const hasMore = ref(false)
const hasEarlierMessages = ref(false)
const bottomAnchorId = ref('')
const keyboardInset = ref(0)
const composerFocused = ref(false)
const pageVisible = ref(true)
let syncTimer: ReturnType<typeof setInterval> | null = null
let statusClearTimer: ReturnType<typeof setTimeout> | null = null
let voiceRecorder: MediaRecorder | null = null
let voiceStream: MediaStream | null = null
let uniVoiceRecorder: UniVoiceRecorderManager | null = null
let uniVoiceRecorderBound = false
let voiceRecordingMode: VoiceRecordingMode = ''
let voiceRecordStartedAt = 0
let voiceStopTimer: ReturnType<typeof setTimeout> | null = null
let voiceRecordTicker: ReturnType<typeof setInterval> | null = null
let voiceRecordCancelled = false
let voiceChunks: Blob[] = []
let activeAudio: HTMLAudioElement | null = null
let activeUniAudio: UniInnerAudioContext | null = null
let activeUniAudioEndedHandler: (() => void) | null = null
let activeUniAudioErrorHandler: ((error: unknown) => void) | null = null
let discoveryFailureCount = 0
let autoDeliveredInFlight = false
let autoReadInFlight = false
let revokeWindowTimer: ReturnType<typeof setInterval> | null = null
let removeKeyboardInsetListeners: (() => void) | null = null
let removePageVisibilityListeners: (() => void) | null = null
let refreshKeyboardInset: (() => void) | null = null
const receiptRefreshWindow = 200
const peerName = ref('聊天用户')
const peerAvatarUrl = ref('')
const peerGender = ref<string | null>(null)
const peerCity = ref<string | null>(null)
const peerVideoVerified = ref(false)
const peerSellerCharmScore = ref(0)
const peerBuyerPowerScore = ref(0)
const peerProfileTrusted = ref(false)
const revokeWindowNowMs = ref(Date.now())
const peerAvatar = computed(() => peerName.value.slice(0, 1))
const peerIdentitySource = computed(() => ({
  peerGender: peerGender.value,
  peerCity: peerCity.value,
  peerVideoVerified: peerVideoVerified.value,
  peerSellerCharmScore: peerSellerCharmScore.value,
  peerBuyerPowerScore: peerBuyerPowerScore.value
}))
const peerLevel = computed(() => buildChatPeerLevel(peerIdentitySource.value))
const peerGenderBadge = computed(() => {
  const gender = normalizedPeerGender(peerIdentitySource.value)
  return {
    symbol: peerGenderSymbol(peerIdentitySource.value),
    genderClass: gender === 'god' ? 'god' : gender === 'goddess' ? 'goddess' : ''
  }
})
const peerIdentityBadges = computed(() => chatPeerIdentityBadges(peerIdentitySource.value).filter((badge) => !badge.startsWith('LV.') && badge !== '♂' && badge !== '♀'))
const canManualSync = computed(() => !chatBlocked.value && (!!conversationId.value || !!receiverId.value))
const composerBlocked = computed(() => chatBlocked.value || sending.value || !currentUserId.value)
const textComposerBlocked = computed(() => composerBlocked.value || recording.value)
const chatPageStyle = computed(() => `--chat-keyboard-inset:${keyboardInset.value}px;`)
const hasDraftText = computed(() => !!draft.value.trim())
const composerPlaceholder = computed(() => recording.value ? '录音中，结束后再输入文字' : '问尺码、瑕疵、发货时间...')
const sendButtonDisabled = computed(() => textComposerBlocked.value || !hasDraftText.value)
const sendButtonLabel = computed(() => sending.value ? '发送中' : hasDraftText.value ? '发送消息' : '请输入消息后发送')
const recordingElapsedText = computed(() => formatDurationSeconds(Math.max(0, Math.ceil(recordingElapsedMs.value / 1000))))
const peerProfileUnavailableText = '对方资料暂时不可用，仍可继续聊天'
const voicePlaybackRetryText = '语音播放失败，请确认浏览器允许音频播放后再点一次'
const chatListStateText = computed(function chatListStateText(): string {
  if (loadingMessages.value || discoveringConversation.value) return '消息加载中...'
  return emptyMessageText.value
})
const emptyMessageText = computed(function emptyChatMessageText(): string {
  if (conversationId.value) return '暂时还没有可显示的消息'
  if (receiverId.value) return '可以直接发送第一条消息'
  return '暂时无法进入聊天'
})

function blockChat(reason: string): void {
  chatBlocked.value = true
  messages.value = []
  conversationId.value = undefined
  stopMessageSync()
  setPersistentStatus(reason)
}

function clearTransientStatusTimer(): void {
  if (!statusClearTimer) return
  clearTimeout(statusClearTimer)
  statusClearTimer = null
}

function setPersistentStatus(message: string): void {
  clearTransientStatusTimer()
  statusText.value = message
}

function clearStatusText(): void {
  clearTransientStatusTimer()
  statusText.value = ''
}

function showTransientStatus(message: string, duration = 1800): void {
  clearTransientStatusTimer()
  if (!message) return
  try {
    uni.showToast({ title: message, icon: 'none', duration })
  } catch (error) {
    console.warn('chat transient status toast failed', { message, error })
    if (!chatBlocked.value) {
      statusText.value = message
      statusClearTimer = setTimeout(() => {
        if (statusText.value === message) statusText.value = ''
        statusClearTimer = null
      }, duration)
    }
  }
}

function goBackToSessions(): void {
  const fallbackToSessionList = () => {
    ;(uni.redirectTo as unknown as ChatRedirectTo)({
      url: '/pages/chat/session-list/index',
      fail: (error: unknown) => {
        console.warn('chat back navigation failed', { error })
        ;(uni.switchTab as unknown as ChatSwitchTab)({ url: '/pages/tabbar/message/index' })
      }
    })
  }
  try {
    ;(uni.navigateBack as unknown as ChatNavigateBack)({
      delta: 1,
      fail: fallbackToSessionList
    })
  } catch (error) {
    console.warn('chat back navigation failed', { error })
    fallbackToSessionList()
  }
}

function assertSendMessageResponseForRequest(value: unknown, payload: SendMessageRequest): SendMessageResponse {
  assertSendMessageResponse(value)
  const ack = value.ack
  if (ack.clientMsgId !== payload.clientMsgId) throw new Error('chat send ack clientMsgId mismatch')
  if (ack.receiverId !== payload.receiverId) throw new Error('chat send ack receiver mismatch')
  if (currentUserId.value && ack.senderId !== currentUserId.value) throw new Error('chat send ack sender mismatch')
  if (ack.msgType !== payload.msgType) throw new Error('chat send ack msgType mismatch')
  if (payload.conversationId && ack.conversationId !== payload.conversationId) throw new Error('chat send ack conversation mismatch')
  return value
}

function hasAckedServerMessage(response: SendMessageResponse): boolean {
  const ack = response.ack
  return messages.value.some((message) => message.serverSeq === ack.serverSeq || message.serverMsgId === ack.serverMsgId)
}

async function loadCurrentUser(): Promise<void> {
  try {
    const profile = await getMyProfile()
    if (!isValidBackendId(profile.userId)) throw new Error('chat current userId invalid')
    currentUserId.value = profile.userId
  } catch (error) {
    currentUserId.value = null
    console.warn('chat current user load failed', { error })
    setPersistentStatus('当前登录用户加载失败，暂不能发送消息')
  }
}

async function loadPeerProfile(peerUserId: number): Promise<void> {
  const fallbackName = `用户 ${peerUserId}`
  if (!hasTrustedPeerProfileContext(peerUserId)) {
    peerName.value = fallbackName
    peerAvatarUrl.value = ''
  }
  try {
    const profile = await getPublicProfile(peerUserId)
    if (String(profile.userId) !== String(peerUserId)) throw new Error('chat peer userId mismatch')
    applyPeerProfile(profile)
  } catch (error) {
    if (!hasTrustedPeerProfileContext(peerUserId)) {
      peerName.value = fallbackName
      peerAvatarUrl.value = ''
      setPersistentStatus(peerProfileUnavailableText)
    }
    console.warn('chat peer profile load failed', { peerUserId, error })
  }
}

function hasTrustedPeerProfileContext(peerUserId: number): boolean {
  const fallbackName = `用户 ${peerUserId}`
  return peerProfileTrusted.value && (peerName.value !== fallbackName ||
    !!peerAvatarUrl.value ||
    !!peerGender.value ||
    !!peerCity.value ||
    peerVideoVerified.value ||
    peerSellerCharmScore.value > 0 ||
    peerBuyerPowerScore.value > 0)
}

function applyPeerProfile(profile: UserProfileResponse): void {
  peerName.value = profile.nickname || `用户 ${profile.userId}`
  peerGender.value = typeof profile.gender === 'string' ? profile.gender : null
  peerAvatarUrl.value = resolveBackendMediaUrl(validatedChatAvatarUrl(avatarUrlWithGenderFallback(profile.avatarUrl, peerGender.value)))
  peerCity.value = typeof profile.city === 'string' ? profile.city : null
  peerVideoVerified.value = profile.videoVerified === true
  peerSellerCharmScore.value = Math.max(0, Math.floor(Number(profile.sellerCharmScore || 0)))
  peerBuyerPowerScore.value = Math.max(0, Math.floor(Number(profile.buyerPowerScore || 0)))
  peerProfileTrusted.value = true
  if (statusText.value === peerProfileUnavailableText) statusText.value = ''
}

function applyPeerConversationItem(item: ChatConversationItem): void {
  peerName.value = item.peerNickname || `用户 ${item.peerUserId}`
  peerGender.value = item.peerGender || null
  peerAvatarUrl.value = resolveBackendMediaUrl(validatedChatAvatarUrl(avatarUrlWithGenderFallback(item.peerAvatarUrl, peerGender.value)))
  peerCity.value = item.peerCity || null
  peerVideoVerified.value = item.peerVideoVerified === true
  peerSellerCharmScore.value = Math.max(0, Math.floor(Number(item.peerSellerCharmScore || 0)))
  peerBuyerPowerScore.value = Math.max(0, Math.floor(Number(item.peerBuyerPowerScore || 0)))
  peerProfileTrusted.value = true
  if (statusText.value === peerProfileUnavailableText) statusText.value = ''
}

onLoad((options) => {
  void initializeChatPage(options)
})

onShow(() => {
  handleChatPageVisible()
})

onHide(() => {
  handleChatPageHidden()
})

onMounted(() => {
  startRevokeWindowTicker()
  installKeyboardInsetListeners()
  installPageVisibilityListeners()
})

onUnload(() => {
  stopMessageSync()
  stopRevokeWindowTicker()
  clearTransientStatusTimer()
  cleanupVoiceRecording()
  stopActiveVoicePlayback()
  closeImagePreview()
  releaseChatMediaBlobs()
  removeKeyboardInsetListeners?.()
  removeKeyboardInsetListeners = null
  removePageVisibilityListeners?.()
  removePageVisibilityListeners = null
})

function startRevokeWindowTicker(): void {
  if (revokeWindowTimer) return
  revokeWindowNowMs.value = Date.now()
  revokeWindowTimer = setInterval(() => {
    revokeWindowNowMs.value = Date.now()
  }, 15_000)
}

function stopRevokeWindowTicker(): void {
  if (!revokeWindowTimer) return
  clearInterval(revokeWindowTimer)
  revokeWindowTimer = null
}

function installKeyboardInsetListeners(): void {
  if (typeof window === 'undefined' || removeKeyboardInsetListeners) return
  const viewport = window.visualViewport
  if (!viewport) {
    refreshKeyboardInset = () => {
      keyboardInset.value = composerFocused.value ? KEYBOARD_FOCUS_FALLBACK_INSET : 0
      if (keyboardInset.value > 0) setTimeout(scrollMessagesToBottom, 60)
    }
    removeKeyboardInsetListeners = () => {
      refreshKeyboardInset = null
    }
    return
  }
  const update = () => {
    const layoutHeight = window.innerHeight || document.documentElement.clientHeight || viewport.height
    const measuredInset = Math.max(0, Math.round(layoutHeight - viewport.height - viewport.offsetTop))
    const nextInset = composerFocused.value && measuredInset <= 0 ? KEYBOARD_FOCUS_FALLBACK_INSET : measuredInset
    keyboardInset.value = composerFocused.value ? nextInset : 0
    if (keyboardInset.value > 0) setTimeout(scrollMessagesToBottom, 60)
  }
  refreshKeyboardInset = update
  viewport.addEventListener('resize', update)
  viewport.addEventListener('scroll', update)
  window.addEventListener('orientationchange', update)
  removeKeyboardInsetListeners = () => {
    viewport.removeEventListener('resize', update)
    viewport.removeEventListener('scroll', update)
    window.removeEventListener('orientationchange', update)
    refreshKeyboardInset = null
  }
  update()
}

function installPageVisibilityListeners(): void {
  if (typeof document === 'undefined' || removePageVisibilityListeners) return
  const handleVisibilityChange = () => {
    if (document.visibilityState === 'hidden') handleChatPageHidden()
    else handleChatPageVisible()
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)
  removePageVisibilityListeners = () => {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
  handleVisibilityChange()
}

function isBrowserDocumentVisible(): boolean {
  if (typeof document === 'undefined') return true
  return document.visibilityState !== 'hidden'
}

function isChatPageVisible(): boolean {
  return pageVisible.value && isBrowserDocumentVisible()
}

function shouldSyncMessages(): boolean {
  return !chatBlocked.value && isChatPageVisible()
}

function shouldAutoAcknowledgeMessages(): boolean {
  return shouldSyncMessages() && !!conversationId.value
}

function handleChatPageHidden(): void {
  pageVisible.value = false
  stopMessageSync()
  stopActiveVoicePlayback()
}

function handleChatPageVisible(): void {
  pageVisible.value = true
  if (!isChatPageVisible()) return
  resumeVisibleChatSync()
}

function resumeVisibleChatSync(): void {
  if (!shouldSyncMessages()) return
  if (conversationId.value) void syncConversationMessages(false, true)
  else if (receiverId.value) void discoverConversationWithPeer(false)
  startMessageSync()
}

async function initializeChatPage(options: Record<string, string | undefined> | undefined): Promise<void> {
  await loadCurrentUser()
  if (!currentUserId.value) {
    blockChat('当前登录用户加载失败，暂不能发送消息')
    return
  }
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
      await recoverFromRouteConversationMismatch(routeConversationId, receiverId.value)
    }
  } else if (routeConversationId) {
    try {
      const matched = await loadRouteConversation(routeConversationId)
      receiverId.value = matched.peerUserId
      applyPeerConversationItem(matched)
      chatBlocked.value = false
      conversationId.value = matched.conversationId
      await loadMoreMessages()
      startMessageSync()
    } catch (error) {
      console.warn('chat route conversation load failed', { routeConversationId, error })
      blockChat('暂时无法进入聊天，请从私信列表重新打开')
    }
  } else if (receiverId.value) {
    chatBlocked.value = false
    clearStatusText()
    void discoverConversationWithPeer(true)
    startMessageSync()
  } else {
    blockChat('暂时无法进入聊天')
  }
}

async function recoverFromRouteConversationMismatch(routeConversationId: number, routeReceiverId: number): Promise<void> {
  conversationId.value = undefined
  messages.value = []
  releaseChatMediaBlobs()
  nextAfterSeq.value = 0
  previousBeforeSeq.value = 0
  hasMore.value = false
  hasEarlierMessages.value = false
  chatBlocked.value = false
  clearStatusText()
  console.warn('chat route conversation mismatch recovered by receiver discovery', { routeConversationId, routeReceiverId })
  const discovered = await discoverConversationWithPeer(true)
  if (!discovered) showTransientStatus('已按目标用户打开私聊，可直接发送第一条消息', 1500)
  startMessageSync()
}

async function verifyRouteConversation(routeConversationId: number, routeReceiverId: number): Promise<ChatConversationItem> {
  const matched = await getChatConversation(routeConversationId)
  assertConversationItem(matched)
  if (matched.conversationId !== routeConversationId || matched.peerUserId !== routeReceiverId) throw new Error('chat route conversation/peer mismatch')
  return matched
}

async function loadRouteConversation(routeConversationId: number): Promise<ChatConversationItem> {
  const matched = await getChatConversation(routeConversationId)
  assertConversationItem(matched)
  if (matched.conversationId !== routeConversationId || !isValidBackendId(matched.peerUserId)) throw new Error('chat route conversation invalid')
  return matched
}

async function loadMoreMessages(): Promise<void> {
  await syncConversationMessages(true, false, nextAfterSeq.value === 0)
}

function startMessageSync(): void {
  if (chatBlocked.value || syncTimer || !shouldSyncMessages()) return
  syncTimer = setInterval(() => {
    if (chatBlocked.value) return
    if (!shouldSyncMessages()) {
      stopMessageSync()
      return
    }
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
      if (showStatus) showTransientStatus('还没有聊天记录，先打个招呼吧', 1500)
      return false
    }
    discoveryFailureCount = 0
    chatBlocked.value = false
    conversationId.value = matched.conversationId
    applyPeerConversationItem(matched)
    if (showStatus) showTransientStatus('正在更新消息', 1000)
    await syncConversationMessages(true, true, true)
    return true
  } catch (error) {
    discoveryFailureCount += 1
    console.warn('chat conversation discovery failed', { receiverId: receiverId.value, discoveryFailureCount, error })
    if (showStatus || discoveryFailureCount >= 3) statusText.value = '聊天刷新暂不可用，请稍后重试'
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

function filterActiveConversationMessages(serverMessages: unknown[], activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number): ChatMessageItem[] {
  const validMessages: ChatMessageItem[] = []
  for (const message of serverMessages) {
    try {
      assertActiveConversationMessage(message as ChatMessageItem, activeConversationId, activeCurrentUserId, activeReceiverId)
      try {
        assertParsableChatMessageContent(message as ChatMessageItem)
      } catch (error) {
        console.warn('chat malformed message content isolated', {
          conversationId: activeConversationId,
          serverSeq: (message as ChatMessageItem).serverSeq,
          serverMsgId: (message as ChatMessageItem).serverMsgId,
          error
        })
      }
      validMessages.push(message as ChatMessageItem)
    } catch (error) {
      console.warn('chat invalid message isolated', {
        conversationId: activeConversationId,
        serverSeq: typeof message === 'object' && message !== null ? (message as { serverSeq?: unknown }).serverSeq : undefined,
        serverMsgId: typeof message === 'object' && message !== null ? (message as { serverMsgId?: unknown }).serverMsgId : undefined,
        error
      })
    }
  }
  return validMessages
}

function mergeServerMessages(serverMessages: ChatMessageItem[]): void {
  if (!serverMessages.length) return
  const activeConversationId = conversationId.value
  const activeCurrentUserId = currentUserId.value
  const activeReceiverId = receiverId.value
  if (!activeConversationId || !activeCurrentUserId || !activeReceiverId) throw new Error('chat message merge missing active conversation state')
  const bySeq = new Map(messages.value.map((message) => [message.serverSeq, message]))
  for (const message of serverMessages) {
    const existing = bySeq.get(message.serverSeq)
    bySeq.set(message.serverSeq, existing ? { ...existing, ...message } : message)
  }
  messages.value = [...bySeq.values()].sort((left, right) => left.serverSeq - right.serverSeq)
  void prepareChatMediaBlobs()
  scrollMessagesToBottom()
}

function mergeEarlierMessages(serverMessages: ChatMessageItem[]): void {
  if (!serverMessages.length) return
  const activeConversationId = conversationId.value
  const activeCurrentUserId = currentUserId.value
  const activeReceiverId = receiverId.value
  if (!activeConversationId || !activeCurrentUserId || !activeReceiverId) throw new Error('chat message merge missing active conversation state')
  const bySeq = new Map(messages.value.map((message) => [message.serverSeq, message]))
  for (const message of serverMessages) {
    const existing = bySeq.get(message.serverSeq)
    bySeq.set(message.serverSeq, existing ? { ...existing, ...message } : message)
  }
  messages.value = [...bySeq.values()].sort((left, right) => left.serverSeq - right.serverSeq)
  void prepareChatMediaBlobs()
}

function scrollMessagesToBottom(): void {
  bottomAnchorId.value = ''
  void nextTick(() => {
    bottomAnchorId.value = 'chat-bottom-anchor'
  })
}

function handleComposerFocus(): void {
  composerFocused.value = true
  keyboardInset.value = KEYBOARD_FOCUS_FALLBACK_INSET
  refreshKeyboardInset?.()
  scrollMessagesToBottom()
  setTimeout(() => {
    if (composerFocused.value && keyboardInset.value <= 0) {
      keyboardInset.value = KEYBOARD_FOCUS_FALLBACK_INSET
      scrollMessagesToBottom()
    }
  }, 120)
  setTimeout(scrollMessagesToBottom, 180)
}

function handleComposerBlur(): void {
  composerFocused.value = false
  setTimeout(() => {
    if (!composerFocused.value) {
      keyboardInset.value = 0
      scrollMessagesToBottom()
    }
  }, 220)
}

async function syncConversationMessages(showStatus: boolean, refreshReceipts: boolean, latest = false): Promise<boolean> {
  if (loadingMessages.value || !conversationId.value) return false
  if (!currentUserId.value || !receiverId.value) {
    console.warn('chat conversation sync missing participant state', { conversationId: conversationId.value, hasCurrentUserId: !!currentUserId.value, hasReceiverId: !!receiverId.value })
    blockChat('暂时无法进入聊天，请重新打开')
    return false
  }
  loadingMessages.value = true
  if (showStatus) clearStatusText()
  const syncAfterSeq = nextAfterSeq.value
  const activeConversationId = conversationId.value
  const activeCurrentUserId = currentUserId.value
  const activeReceiverId = receiverId.value
  try {
    const usedRecentWindow = latest && syncAfterSeq === 0
    const response = usedRecentWindow
      ? await syncRecentMessages(activeConversationId, 50)
      : await syncMessages(activeConversationId, syncAfterSeq, 50)
    assertMessageSyncResponse(response)
    const validMessages = filterActiveConversationMessages(response.messages, activeConversationId, activeCurrentUserId, activeReceiverId)
    mergeServerMessages(validMessages)
    nextAfterSeq.value = Math.max(response.nextAfterSeq || syncAfterSeq, syncAfterSeq)
    hasMore.value = response.hasMore
    if (usedRecentWindow) {
      if (response.previousBeforeSeq != null) previousBeforeSeq.value = response.previousBeforeSeq
      hasEarlierMessages.value = response.hasEarlier === true
    }
    const canAutoAcknowledge = shouldAutoAcknowledgeMessages()
    if (refreshReceipts && canAutoAcknowledge) await refreshVisibleReceiptStates(activeConversationId, activeCurrentUserId, activeReceiverId)
    const visibleReceivedSeq = maxVisibleReceivedSeq(activeReceiverId)
    if (canAutoAcknowledge && visibleReceivedSeq > 0) {
      void autoMarkDeliveredAfterSync(visibleReceivedSeq)
      const readableReceivedSeq = autoReadableReceivedSeq(activeReceiverId)
      if (readableReceivedSeq > 0) void autoMarkReadAfterSync(readableReceivedSeq)
    }
    if (showStatus) showTransientStatus(response.hasMore ? '已加载一部分消息，可继续加载' : '消息已更新')
    return true
  } catch (error) {
    console.warn('chat conversation sync failed', { conversationId: activeConversationId, afterSeq: syncAfterSeq, latest, refreshReceipts, error })
    if (error instanceof ChatDataIntegrityError) {
      blockChat('聊天内容暂时无法显示，请重新打开')
      return false
    }
    if (showStatus) {
      statusText.value = '消息刷新失败，请稍后重试'
      return false
    }
    statusText.value = '消息刷新失败，可能有新消息未显示，请稍后重试'
    return false
  } finally {
    loadingMessages.value = false
  }
}

async function loadEarlierMessages(): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能加载更早消息'; return }
  if (loadingMessages.value || loadingEarlierMessages.value || !conversationId.value || !previousBeforeSeq.value) return
  if (!currentUserId.value || !receiverId.value) {
    blockChat('暂时无法进入聊天，请重新打开')
    return
  }
  const activeConversationId = conversationId.value
  const activeCurrentUserId = currentUserId.value
  const activeReceiverId = receiverId.value
  loadingEarlierMessages.value = true
  try {
    const response = await syncEarlierMessages(activeConversationId, previousBeforeSeq.value, 50)
    assertMessageSyncResponse(response)
    const validMessages = filterActiveConversationMessages(response.messages, activeConversationId, activeCurrentUserId, activeReceiverId)
    mergeEarlierMessages(validMessages)
    if (response.previousBeforeSeq != null) previousBeforeSeq.value = response.previousBeforeSeq
    hasEarlierMessages.value = response.hasEarlier === true
    showTransientStatus(response.messages.length ? '更早消息已加载' : '没有更早消息了')
  } catch (error) {
    console.warn('chat earlier messages load failed', { conversationId: activeConversationId, beforeSeq: previousBeforeSeq.value, error })
    if (error instanceof ChatDataIntegrityError) blockChat('聊天内容暂时无法显示，请重新打开')
    else statusText.value = '更早消息加载失败，请稍后重试'
  } finally {
    loadingEarlierMessages.value = false
  }
}

function visibleReceiptRefreshAfterSeq(): number {
  const maxVisibleSeq = messages.value.reduce((maxSeq, message) => Math.max(maxSeq, message.serverSeq), 0)
  return Math.max(0, maxVisibleSeq - receiptRefreshWindow)
}

function maxVisibleReceivedSeq(activeReceiverId: number): number {
  return messages.value.reduce((maxSeq, message) => {
    return message.senderId === activeReceiverId ? Math.max(maxSeq, message.serverSeq) : maxSeq
  }, 0)
}

function autoReadableReceivedSeq(activeReceiverId: number): number {
  if (hasEarlierMessages.value) return 0
  return maxVisibleReceivedSeq(activeReceiverId)
}

async function autoMarkDeliveredAfterSync(deliveredSeq: number): Promise<void> {
  if (chatBlocked.value || !isChatPageVisible() || autoDeliveredInFlight || !conversationId.value || deliveredSeq <= 0) return
  const activeConversationId = conversationId.value
  autoDeliveredInFlight = true
  try {
    const response = await markConversationDelivered(activeConversationId)
    if (response.conversationId !== activeConversationId || response.deliveredSeq < deliveredSeq || !Number.isSafeInteger(response.unreadCount) || response.unreadCount < 0) {
      throw new Error('chat auto delivered response invalid')
    }
  } catch (error) {
    console.warn('chat conversation auto delivered receipt failed', { conversationId: activeConversationId, deliveredSeq, error })
  } finally {
    autoDeliveredInFlight = false
  }
}

async function refreshVisibleReceiptStates(activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number): Promise<void> {
  if (messages.value.length === 0) return
  const receiptAfterSeq = visibleReceiptRefreshAfterSeq()
  const response = await syncMessages(activeConversationId, receiptAfterSeq, receiptRefreshWindow)
  assertMessageSyncResponse(response)
  const validMessages = filterActiveConversationMessages(response.messages, activeConversationId, activeCurrentUserId, activeReceiverId)
  mergeServerMessages(validMessages)
}

async function autoMarkReadAfterSync(readSeq: number): Promise<void> {
  if (chatBlocked.value || !isChatPageVisible() || autoReadInFlight || !conversationId.value || readSeq <= 0) return
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
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能标记已读'; return }
  if (!conversationId.value) { statusText.value = '聊天还未建立，不能标记已读'; return }
  if (!receiverId.value) { statusText.value = '聊天对象暂时不可用，不能标记已读'; return }
  const requestedReadSeq = autoReadableReceivedSeq(receiverId.value)
  if (requestedReadSeq <= 0) {
    statusText.value = hasEarlierMessages.value ? '请先加载更早消息后再标记已读' : '暂无可标记已读的消息'
    return
  }
  try {
    const response = await markConversationRead(conversationId.value, { readSeq: requestedReadSeq })
    if (response.conversationId !== conversationId.value || response.readSeq > requestedReadSeq || response.readSeq > nextAfterSeq.value) throw new Error('chat read response cursor mismatch')
    statusText.value = `已读至 ${response.readSeq}`
  } catch (error) {
    console.warn('chat conversation read receipt failed', { conversationId: conversationId.value, requestedReadSeq, error })
    statusText.value = '已读状态未更新，请稍后重试'
  }
}

async function manualSyncMessages(): Promise<void> {
  if (chatBlocked.value) {
    statusText.value = '聊天暂不可用，不能刷新'
    return
  }
  if (conversationId.value) {
    await syncConversationMessages(true, true)
    return
  }
  if (receiverId.value) {
    await discoverConversationWithPeer(true)
    return
  }
  statusText.value = '暂时无法刷新消息'
}

async function handleSendText(): Promise<void> {
  if (recording.value) {
    statusText.value = '请先结束或取消录音，再发送文字'
    return
  }
  await handleSend('TEXT')
}

async function toggleVoiceRecording(): Promise<void> {
  if (recording.value) {
    stopVoiceRecording(false)
    return
  }
  await startVoiceRecording()
}

async function startVoiceRecording(): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能发送语音'; return }
  if (sending.value || recording.value) return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，暂不能发送语音'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，暂不能发送语音'; return }
  if (canUseBrowserVoiceRecorder()) {
    await startBrowserVoiceRecording()
    return
  }
  if (startUniVoiceRecording()) return
  statusText.value = browserVoiceUnsupportedReason() || '当前环境暂不支持语音录制，请换用手机浏览器或安全连接'
}

function canUseBrowserVoiceRecorder(): boolean {
  return browserVoiceUnsupportedReason() === ''
}

function browserVoiceUnsupportedReason(): string {
  if (typeof window === 'undefined') return '当前环境暂不支持语音录制，请换用手机浏览器或安全连接'
  if (window.isSecureContext !== true) return '语音录制需要 HTTPS 安全连接，请切换到 HTTPS 后重试'
  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) return '当前浏览器暂不支持麦克风录制，请换用手机浏览器'
  if (typeof MediaRecorder === 'undefined' || typeof Blob === 'undefined') return '当前浏览器暂不支持语音录制，请换用手机浏览器'
  if (typeof fetch !== 'function' || typeof FormData === 'undefined') return '当前浏览器暂不支持语音文件上传，请换用手机浏览器'
  return ''
}

async function startBrowserVoiceRecording(): Promise<void> {
  try {
    const mimeType = supportedVoiceMimeType()
    voiceStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    voiceChunks = []
    voiceRecordCancelled = false
    voiceRecordingMode = 'browser'
    voiceRecordStartedAt = Date.now()
    voiceRecorder = new MediaRecorder(voiceStream, mimeType ? { mimeType } : undefined)
    voiceRecorder.ondataavailable = (event) => {
      if (event.data?.size > 0) voiceChunks.push(event.data)
    }
    voiceRecorder.onerror = (event) => {
      console.warn('chat voice recorder error', { event })
      statusText.value = '语音录制失败，请检查麦克风权限'
      cleanupVoiceRecording()
    }
    voiceRecorder.onstop = () => {
      const durationMs = Date.now() - voiceRecordStartedAt
      const chunks = [...voiceChunks]
      const recorderMimeType = normalizedVoiceMimeType(voiceRecorder?.mimeType || mimeType)
      const cancelled = voiceRecordCancelled
      cleanupVoiceRecording()
      if (cancelled) {
        showTransientStatus('已取消语音录制')
        return
      }
      void handleRecordedVoice(chunks, durationMs, recorderMimeType)
    }
    voiceRecorder.start()
    recording.value = true
    startVoiceRecordTicker()
    showTransientStatus('正在录音，再点麦克风发送', 1200)
    voiceStopTimer = setTimeout(() => stopVoiceRecording(false), MAX_VOICE_RECORD_MS)
  } catch (error) {
    console.warn('chat voice record start failed', { error })
    cleanupVoiceRecording()
    statusText.value = '无法开始录音，请检查麦克风权限'
  }
}

function startUniVoiceRecording(): boolean {
  const recorder = getUniVoiceRecorder()
  if (!recorder) return false
  try {
    voiceChunks = []
    voiceRecordCancelled = false
    voiceRecordingMode = 'uni'
    voiceRecordStartedAt = Date.now()
    recorder.start({
      duration: MAX_VOICE_RECORD_MS,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      format: 'mp3'
    })
    recording.value = true
    startVoiceRecordTicker()
    showTransientStatus('正在录音，再点麦克风发送', 1200)
    voiceStopTimer = setTimeout(() => stopVoiceRecording(false), MAX_VOICE_RECORD_MS)
    return true
  } catch (error) {
    console.warn('chat uni voice record start failed', { error })
    cleanupVoiceRecording()
    statusText.value = '无法开始录音，请检查麦克风权限'
    return true
  }
}

function getUniVoiceRecorder(): UniVoiceRecorderManager | null {
  const recorderApi = uni as unknown as { getRecorderManager?: () => UniVoiceRecorderManager }
  if (typeof recorderApi.getRecorderManager !== 'function') return null
  if (!uniVoiceRecorder) uniVoiceRecorder = recorderApi.getRecorderManager()
  if (!uniVoiceRecorderBound) {
    uniVoiceRecorder.onStop((result) => {
      if (voiceRecordingMode !== 'uni') return
      const durationMs = normalizeUniVoiceDurationMs(result.duration, Date.now() - voiceRecordStartedAt)
      const cancelled = voiceRecordCancelled
      cleanupVoiceRecording()
      if (cancelled) {
        showTransientStatus('已取消语音录制')
        return
      }
      void handleRecordedUniVoice(result.tempFilePath || '', durationMs, result.fileSize)
    })
    uniVoiceRecorder.onError((error) => {
      if (voiceRecordingMode !== 'uni') return
      console.warn('chat uni voice recorder error', { error })
      cleanupVoiceRecording()
      statusText.value = '语音录制失败，请检查麦克风权限'
    })
    uniVoiceRecorderBound = true
  }
  return uniVoiceRecorder
}

function stopVoiceRecording(cancel: boolean): void {
  voiceRecordCancelled = cancel
  if (voiceRecordingMode === 'uni') {
    if (!uniVoiceRecorder) {
      cleanupVoiceRecording()
      return
    }
    try {
      uniVoiceRecorder.stop()
    } catch (error) {
      console.warn('chat uni voice recorder stop failed', { error })
      cleanupVoiceRecording()
      statusText.value = '语音录制失败，请重新录制'
    }
    return
  }
  if (!voiceRecorder || voiceRecorder.state === 'inactive') {
    cleanupVoiceRecording()
    return
  }
  try {
    voiceRecorder.stop()
  } catch (error) {
    console.warn('chat voice recorder stop failed', { error })
    cleanupVoiceRecording()
    statusText.value = '语音录制失败，请重新录制'
  }
}

function cancelVoiceRecording(): void {
  if (!recording.value) return
  stopVoiceRecording(true)
}

function startVoiceRecordTicker(): void {
  if (voiceRecordTicker) clearInterval(voiceRecordTicker)
  recordingElapsedMs.value = 0
  voiceRecordTicker = setInterval(() => {
    if (!recording.value || voiceRecordStartedAt <= 0) return
    recordingElapsedMs.value = Math.max(0, Date.now() - voiceRecordStartedAt)
  }, 250)
}

function stopVoiceRecordTicker(): void {
  if (voiceRecordTicker) {
    clearInterval(voiceRecordTicker)
    voiceRecordTicker = null
  }
  recordingElapsedMs.value = 0
}

function cleanupVoiceRecording(): void {
  stopVoiceRecordTicker()
  if (voiceStopTimer) {
    clearTimeout(voiceStopTimer)
    voiceStopTimer = null
  }
  voiceStream?.getTracks().forEach((track) => track.stop())
  voiceStream = null
  voiceRecorder = null
  voiceRecordingMode = ''
  voiceChunks = []
  recording.value = false
}

function supportedVoiceMimeType(): VoiceContentType | '' {
  const candidates: VoiceContentType[] = ['audio/webm', 'audio/mp4', 'audio/x-m4a', 'audio/aac', 'audio/mpeg', 'audio/wav']
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') return 'audio/webm'
  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate)) ?? ''
}

async function handleRecordedVoice(chunks: Blob[], durationMs: number, mimeType: VoiceContentType): Promise<void> {
  if (durationMs < MIN_VOICE_RECORD_MS || chunks.length === 0) {
    statusText.value = '录音时间太短，请重新录制'
    return
  }
  const blob = new Blob(chunks, { type: mimeType })
  if (blob.size <= 0 || blob.size > 10_000_000) {
    statusText.value = '语音文件大小异常，请重新录制'
    return
  }
  sending.value = true
  showTransientStatus('正在上传语音...', 1200)
  try {
    const ticket = await createMediaUploadTicket({ scene: 'CHAT_VOICE', contentType: mimeType, fileSize: blob.size, filename: voiceFallbackName(mimeType) })
    const uploaded = await uploadMediaTicketBlob(ticket, blob, voiceFallbackName(mimeType))
    if (hasInvalidChatVoiceStorageUrl(uploaded.storageUrl)) throw new Error('chat voice storageUrl invalid')
    await handleSend('VOICE', {
      url: uploaded.storageUrl,
      durationMs: Math.max(1, Math.round(durationMs)),
      sizeBytes: blob.size,
      mimeType
    })
  } catch (error) {
    console.warn('chat voice send failed', { durationMs, sizeBytes: blob.size, mimeType, error })
    statusText.value = '语音发送失败，请重新录制后发送'
  } finally {
    sending.value = false
  }
}

async function handleRecordedUniVoice(tempFilePath: string, durationMs: number, fileSize?: number): Promise<void> {
  if (durationMs < MIN_VOICE_RECORD_MS) {
    statusText.value = '录音时间太短，请重新录制'
    return
  }
  if (!tempFilePath || hasInvalidTempChatVoicePath(tempFilePath)) {
    statusText.value = '语音文件暂不可用，请重新录制'
    return
  }
  const safeFileSize = normalizeUniVoiceFileSize(fileSize)
  if (safeFileSize && safeFileSize > MAX_CHAT_VOICE_UPLOAD_BYTES) {
    statusText.value = '语音文件过大，请重新录制'
    return
  }
  const mimeType = guessUniVoiceMimeType(tempFilePath)
  sending.value = true
  showTransientStatus('正在上传语音...', 1200)
  try {
    const ticket = await createMediaUploadTicket({
      scene: 'CHAT_VOICE',
      contentType: mimeType,
      fileSize: safeFileSize || MAX_CHAT_VOICE_UPLOAD_BYTES,
      filename: filenameFromPath(tempFilePath, voiceFallbackName(mimeType))
    })
    const uploaded = await uploadMediaTicketFile(ticket, tempFilePath)
    if (hasInvalidChatVoiceStorageUrl(uploaded.storageUrl)) throw new Error('chat voice storageUrl invalid')
    await handleSend('VOICE', {
      url: uploaded.storageUrl,
      durationMs: Math.max(1, Math.round(durationMs)),
      ...(safeFileSize ? { sizeBytes: safeFileSize } : {}),
      mimeType
    })
  } catch (error) {
    console.warn('chat uni voice send failed', { durationMs, fileSize: safeFileSize, mimeType, error })
    statusText.value = '语音发送失败，请重新录制后发送'
  } finally {
    sending.value = false
  }
}

function normalizeUniVoiceDurationMs(duration: unknown, fallbackMs: number): number {
  const numeric = Number(duration)
  if (Number.isFinite(numeric) && numeric > 0) return Math.round(numeric)
  return Math.max(0, Math.round(fallbackMs))
}

function normalizeUniVoiceFileSize(fileSize: unknown): number | undefined {
  const numeric = Number(fileSize)
  if (!Number.isFinite(numeric) || numeric <= 0) return undefined
  return Math.round(numeric)
}

function guessUniVoiceMimeType(tempFilePath: string): VoiceContentType {
  const lower = tempFilePath.toLowerCase().split('?')[0] || ''
  if (lower.endsWith('.aac')) return 'audio/aac'
  if (lower.endsWith('.wav')) return 'audio/wav'
  if (lower.endsWith('.m4a')) return 'audio/x-m4a'
  return 'audio/mpeg'
}

function hasInvalidTempChatVoicePath(path: string): boolean {
  const lower = path.toLowerCase()
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
    (path.includes('//') && !allowedTempScheme)
}

function openMediaComposer(): void {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能发送媒体'; return }
  if (recording.value) { statusText.value = '请先结束或取消录音，再发送媒体'; return }
  if (sending.value) return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，媒体暂不可用'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，媒体暂不可用'; return }
  try {
    uni.showActionSheet({
      itemList: ['发送图片', '发送视频'],
      success: (result) => {
        if (result.tapIndex === 0) sendImagePlaceholder()
        if (result.tapIndex === 1) sendVideoPlaceholder()
      },
      fail: (error) => {
        console.warn('chat media action sheet failed', { cancelled: isPickerCancel(error), error })
        if (isPickerCancel(error)) showTransientStatus('已取消选择媒体')
      }
    })
  } catch (error) {
    console.warn('chat media action sheet failed', { error })
    statusText.value = '无法打开媒体选择器，请稍后重试'
  }
}

function sendImagePlaceholder(): void {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能发送图片'; return }
  if (recording.value) { statusText.value = '请先结束或取消录音，再发送图片'; return }
  if (sending.value) return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，图片暂不可用'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，图片暂不可用'; return }
  try {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const path = res.tempFilePaths[0] || ''
        if (!path) { showTransientStatus('没有选择图片'); return }
        void handleSendImage(path, (res as { tempFiles?: ChooseImageFile[] }).tempFiles?.[0])
      },
      fail: (error) => {
        console.warn('chat image picker failed', { cancelled: isPickerCancel(error), error })
        if (isPickerCancel(error)) showTransientStatus('已取消选择图片')
        else statusText.value = '无法选择图片，请检查相册权限后重试'
      }
    })
  } catch (error) {
    console.warn('chat image picker failed', { error })
    statusText.value = '无法打开图片选择器，请检查相册权限后重试'
  }
}

function sendVideoPlaceholder(): void {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能发送视频'; return }
  if (recording.value) { statusText.value = '请先结束或取消录音，再发送视频'; return }
  if (sending.value) return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，视频暂不可用'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，视频暂不可用'; return }
  try {
    uni.chooseVideo({
      sourceType: ['album', 'camera'],
      compressed: true,
      maxDuration: Math.floor(MAX_CHAT_VIDEO_DURATION_MS / 1000),
      success: (res) => {
        const result = res as ChooseVideoResult
        const path = result.tempFilePath || ''
        if (!path) { showTransientStatus('没有选择视频'); return }
        void handleSendVideo(path, result)
      },
      fail: (error) => {
        console.warn('chat video picker failed', { cancelled: isPickerCancel(error), error })
        if (isPickerCancel(error)) showTransientStatus('已取消选择视频')
        else statusText.value = '无法选择视频，请检查相册或相机权限后重试'
      }
    })
  } catch (error) {
    console.warn('chat video picker failed', { error })
    statusText.value = '无法打开视频选择器，请检查相册或相机权限后重试'
  }
}

async function handleSendImage(localPath: string, file?: ChooseImageFile): Promise<void> {
  if (recording.value) {
    statusText.value = '请先结束或取消录音，再发送图片'
    return
  }
  if (!localPath || hasInvalidTempChatImagePath(localPath)) {
    statusText.value = '图片暂不可用，请重新选择图片'
    return
  }
  sending.value = true
  showTransientStatus('正在上传聊天图片...', 1200)
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

async function handleSendVideo(localPath: string, result: ChooseVideoResult): Promise<void> {
  if (recording.value) {
    statusText.value = '请先结束或取消录音，再发送视频'
    return
  }
  if (!localPath || hasInvalidTempChatVideoPath(localPath)) {
    statusText.value = '视频暂不可用，请重新选择视频'
    return
  }
  const durationMs = normalizePickedVideoDurationMs(result.duration)
  if (durationMs <= 0 || durationMs > MAX_CHAT_VIDEO_DURATION_MS) {
    statusText.value = '视频时长最多 5 分钟，请重新选择'
    return
  }
  sending.value = true
  showTransientStatus('正在上传聊天视频...', 1200)
  try {
    const blob = await readH5TempVideoBlob(localPath)
    const file = result.tempFile || result.file
    const contentType = guessVideoMime(localPath, videoTypeFromPickerResult(result, blob))
    const safeFileSize = videoFileSize(file, blob?.size ?? result.size)
    if (safeFileSize > MAX_CHAT_VIDEO_UPLOAD_BYTES) throw new Error('chat video file too large')
    const filename = filenameFromPath(file?.name || localPath, videoFallbackName(contentType))
    const ticket = await createMediaUploadTicket({ scene: 'CHAT_VIDEO', contentType, fileSize: safeFileSize, filename })
    const uploaded = blob
      ? await uploadMediaTicketBlob(ticket, new Blob([blob], { type: contentType }), filename)
      : await uploadMediaTicketFile(ticket, localPath)
    if (hasInvalidChatVideoStorageUrl(uploaded.storageUrl)) throw new Error('chat video storageUrl invalid')
    await handleSend('VIDEO', {
      url: uploaded.storageUrl,
      durationMs,
      sizeBytes: safeFileSize,
      mimeType: contentType
    })
  } catch (error) {
    console.warn('chat video send failed', { pathLength: localPath.length, fileSize: result.size, duration: result.duration, error })
    statusText.value = '视频发送失败，请重新选择后发送'
  } finally {
    sending.value = false
  }
}

function normalizePickedVideoDurationMs(duration: unknown): number {
  const numeric = Number(duration)
  if (!Number.isFinite(numeric) || numeric <= 0) return 1000
  const milliseconds = numeric <= 600 ? numeric * 1000 : numeric
  return Math.max(1, Math.round(milliseconds))
}

async function readH5TempVideoBlob(path: string): Promise<Blob | undefined> {
  if (!path.toLowerCase().startsWith('blob:')) return undefined
  if (typeof fetch !== 'function') throw new Error('chat video blob fetch unsupported')
  const response = await fetch(path)
  if (!response.ok) throw new Error('chat video blob fetch failed')
  return response.blob()
}

function videoTypeFromPickerResult(result: ChooseVideoResult, blob?: Blob): string | undefined {
  return blob?.type || result.type || result.tempFile?.type || result.file?.type
}

async function handleSend(type: 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO', contentPayload?: Record<string, unknown>): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，暂不能发送消息'; return }
  if (recording.value && type !== 'VOICE') { statusText.value = '请先结束或取消录音，再发送其他消息'; return }
  if (sending.value && type === 'TEXT') return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，暂不能发送消息'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户，暂不能发送消息'; return }
  if (type === 'IMAGE' && (!contentPayload || hasInvalidChatImageStorageUrl(contentPayload.url))) { statusText.value = '图片暂不可用，暂不能发送消息'; return }
  if (type === 'VOICE' && (!contentPayload || hasInvalidChatVoiceStorageUrl(contentPayload.url))) { statusText.value = '语音暂不可用，暂不能发送消息'; return }
  if (type === 'VIDEO' && (!contentPayload || hasInvalidChatVideoStorageUrl(contentPayload.url))) { statusText.value = '视频暂不可用，暂不能发送消息'; return }
  const text = draft.value.trim()
  if (type === 'TEXT' && !text) { statusText.value = '请输入消息内容后再发送'; return }
  if (type === 'TEXT' && text.length > MAX_TEXT_MESSAGE_LENGTH) { statusText.value = `消息最多 ${MAX_TEXT_MESSAGE_LENGTH} 字，请精简后再发送`; return }
  const payload: SendMessageRequest = {
    ...(conversationId.value ? { conversationId: conversationId.value } : {}),
    clientMsgId: `h5-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    receiverId: receiverId.value,
    msgType: type,
    contentJson: type === 'TEXT' ? JSON.stringify({ text }) : JSON.stringify(contentPayload)
  }
  let sent = false
  sending.value = true
  clearStatusText()
  try {
    const response = await sendMessage(payload)
    assertSendMessageResponseForRequest(response, payload)
    conversationId.value = response.ack.conversationId
    if (!hasAckedServerMessage(response)) pushLocalMessage(response.ack.serverSeq, response.ack.serverMsgId, payload)
    const syncedAfterSend = await syncConversationMessages(false, true)
    if (chatBlocked.value) return
    startMessageSync()
    sent = true
    if (!syncedAfterSend) showTransientStatus('消息已发送，聊天记录稍后会自动更新')
    else showTransientStatus(hasEarlierMessages.value ? '消息已发送，可查看更早消息' : '消息已发送')
  } catch (error) {
    console.warn('chat message send failed', { conversationId: conversationId.value, receiverId: receiverId.value, msgType: type, error })
    if (error instanceof ChatDataIntegrityError) {
      blockChat('聊天内容暂时无法显示，请重新打开')
    } else if (error instanceof Error && error.message === 'chat local send insertion missing required state') {
      statusText.value = '消息可能已发送，聊天记录稍后会自动更新'
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
  if (value.length > MAX_TEXT_MESSAGE_LENGTH) {
    draft.value = value.slice(0, MAX_TEXT_MESSAGE_LENGTH)
    statusText.value = `消息最多 ${MAX_TEXT_MESSAGE_LENGTH} 字，已自动截断`
    return
  }
  draft.value = value
}

function isMine(message: ChatMessageItem): boolean { return message.senderId === currentUserId.value }

function parsedMessageContent(message: ChatMessageItem): Record<string, unknown> | null {
  const content = safeParsedMessageContent(message)
  if (!content) console.warn('chat message content parse failed', { serverMsgId: message.serverMsgId, msgType: message.msgType })
  return content
}

function chatImageMessageUrl(message: ChatMessageItem): string {
  if (isMessageRevoked(message)) return ''
  if (message.msgType !== 'IMAGE') return ''
  const url = chatMediaStorageUrl(message)
  if (!url || hasInvalidChatImageStorageUrl(url)) return ''
  return chatMediaBlobUrls.value[message.serverMsgId] || ''
}

function chatVoiceMessageUrl(message: ChatMessageItem): string {
  if (isMessageRevoked(message)) return ''
  if (message.msgType !== 'VOICE') return ''
  const url = chatMediaStorageUrl(message)
  if (!url || hasInvalidChatVoiceStorageUrl(url)) return ''
  return chatMediaBlobUrls.value[message.serverMsgId] || ''
}

function chatVideoMessageUrl(message: ChatMessageItem): string {
  if (isMessageRevoked(message)) return ''
  if (message.msgType !== 'VIDEO') return ''
  const url = chatMediaStorageUrl(message)
  if (!url || hasInvalidChatVideoStorageUrl(url)) return ''
  return chatMediaBlobUrls.value[message.serverMsgId] || ''
}

function chatMediaStorageUrl(message: ChatMessageItem): string {
  const content = parsedMessageContent(message)
  const url = content?.url ?? content?.audioUrl ?? content?.voiceUrl ?? content?.videoUrl
  return typeof url === 'string' ? url : ''
}

function hasValidChatMediaStorage(message: ChatMessageItem): boolean {
  if (isMessageRevoked(message)) return false
  const storageUrl = chatMediaStorageUrl(message)
  if (message.msgType === 'IMAGE') return !hasInvalidChatImageStorageUrl(storageUrl)
  if (message.msgType === 'VOICE') return !hasInvalidChatVoiceStorageUrl(storageUrl)
  if (message.msgType === 'VIDEO') return !hasInvalidChatVideoStorageUrl(storageUrl)
  return false
}

function isRenderableMessageType(message: ChatMessageItem): boolean {
  return isKnownChatMessageType(message.msgType)
}

function isMessageUnavailable(message: ChatMessageItem): boolean {
  if (isMessageRevoked(message)) return false
  if (message.msgType === 'IMAGE' || message.msgType === 'VOICE' || message.msgType === 'VIDEO') return !hasValidChatMediaStorage(message)
  const content = parsedMessageContent(message)
  if (!content) return true
  return message.msgType === 'TEXT' && (typeof content.text !== 'string' || !content.text.trim())
}

function isMessageRevoked(message: ChatMessageItem): boolean {
  if (message.revoked === true) return true
  const content = parsedMessageContent(message)
  return content?.revoked === true || content?.recalled === true
}

function canRevokeMessage(message: ChatMessageItem): boolean {
  return isMine(message) && !isMessageRevoked(message) && isMessageWithinRevokeWindow(message) && typeof message.serverMsgId === 'string' && !!message.serverMsgId.trim()
}

function isMessageWithinRevokeWindow(message: ChatMessageItem): boolean {
  const createdAtMs = Date.parse(message.createdAt)
  return Number.isFinite(createdAtMs) && revokeWindowNowMs.value - createdAtMs <= MESSAGE_REVOKE_WINDOW_MS
}

function renderMessage(message: ChatMessageItem): string {
  if (isMessageRevoked(message)) {
    if (message.msgType === 'VOICE') return '语音已撤回'
    if (message.msgType === 'VIDEO') return '视频已撤回'
    return '消息已撤回'
  }
  const content = parsedMessageContent(message)
  if (!content) return '此条消息暂不可用'
  if (message.msgType === 'IMAGE') {
    if (!hasValidChatMediaStorage(message)) return '图片暂不可用'
    if (isChatMediaLoading(message)) return '图片加载中...'
    if (hasChatMediaFailed(message)) return '图片加载失败，点击重试'
    return '点击查看图片'
  }
  if (message.msgType === 'VOICE') {
    if (!hasValidChatMediaStorage(message)) return '语音文件暂不可播放'
    const duration = normalizedVoiceDurationSeconds(content)
    const label = Number.isFinite(duration) && duration > 0 ? `语音消息 ${Math.ceil(duration)}″` : '语音消息'
    if (isChatMediaLoading(message)) return `${label} 加载中...`
    if (hasChatMediaFailed(message)) return `${label} 加载失败，点击重试`
    return `${label} · 点击播放`
  }
  if (message.msgType === 'VIDEO') {
    if (!hasValidChatMediaStorage(message)) return '视频文件暂不可播放'
    const duration = normalizedVideoDurationSeconds(content)
    const label = Number.isFinite(duration) && duration > 0 ? `视频消息 ${Math.ceil(duration)}″` : '视频消息'
    if (isChatMediaLoading(message)) return `${label} 加载中...`
    if (hasChatMediaFailed(message)) return `${label} 加载失败，点击重试`
    return `${label} · 点击播放`
  }
  if (!isKnownChatMessageType(message.msgType)) return '暂不支持的消息类型'
  return typeof content?.text === 'string' && content.text.trim() ? content.text : '消息内容暂不可用'
}

function voiceDurationText(message: ChatMessageItem): string {
  const duration = normalizedVoiceDurationSeconds(parsedMessageContent(message))
  return Number.isFinite(duration) && duration > 0 ? formatDurationSeconds(Math.ceil(duration)) : '语音'
}

function voicePlaybackStateText(message: ChatMessageItem): string {
  if (!hasValidChatMediaStorage(message)) return '不可播放'
  if (isChatMediaLoading(message)) return '加载中'
  if (hasChatMediaFailed(message)) return '点击重试'
  if (playingVoiceMessageId.value === message.serverMsgId) return '播放中'
  return '点击播放'
}

function voicePlaybackStateClass(message: ChatMessageItem): string {
  if (!hasValidChatMediaStorage(message)) return 'unavailable'
  if (isChatMediaLoading(message)) return 'loading'
  if (hasChatMediaFailed(message)) return 'failed'
  if (playingVoiceMessageId.value === message.serverMsgId) return 'playing'
  return 'ready'
}

function formatDurationSeconds(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '0:00'
  const safeSeconds = Math.max(0, Math.min(Math.floor(seconds), 600))
  const minutes = Math.floor(safeSeconds / 60)
  const rest = String(safeSeconds % 60).padStart(2, '0')
  return `${minutes}:${rest}`
}

function normalizedVideoDurationSeconds(content: Record<string, unknown> | null | undefined): number {
  if (!content) return 0
  const seconds = Number(content.durationSeconds ?? content.duration ?? 0)
  if (Number.isFinite(seconds) && seconds > 0) return seconds
  const milliseconds = Number(content.durationMs ?? 0)
  return Number.isFinite(milliseconds) && milliseconds > 0 ? milliseconds / 1000 : 0
}

function handleMessageBodyTap(message: ChatMessageItem): void {
  if (message.msgType === 'IMAGE') {
    void previewChatImage(message)
    return
  }
  if (message.msgType === 'VIDEO') {
    void prepareChatVideo(message)
    return
  }
  if (message.msgType !== 'VOICE') return
  if (isMessageRevoked(message)) {
    statusText.value = '这条语音已撤回'
    return
  }
  void playVoiceMessage(message)
}

async function previewChatImage(message: ChatMessageItem): Promise<void> {
  const imageUrl = await ensureChatMediaBlob(message)
  if (!imageUrl) {
    statusText.value = '图片暂不可预览'
    return
  }
  const imagePreviewer = uni as unknown as {
    previewImage?: (options: { current: string; urls: string[]; fail?: (error: unknown) => void }) => void
  }
  if (typeof imagePreviewer.previewImage !== 'function') {
    openImagePreviewFallback(imageUrl)
    return
  }
  try {
    imagePreviewer.previewImage({
      current: imageUrl,
      urls: messages.value.map((item) => chatImageMessageUrl(item)).filter(Boolean),
      fail: (error: unknown) => {
        console.warn('chat image preview failed', { serverMsgId: message.serverMsgId, error })
        openImagePreviewFallback(imageUrl)
      }
    })
  } catch (error) {
    console.warn('chat image preview failed', { serverMsgId: message.serverMsgId, error })
    openImagePreviewFallback(imageUrl)
  }
}

function openImagePreviewFallback(imageUrl: string): void {
  activeImagePreviewUrl.value = imageUrl
  if (statusText.value === '当前环境暂不支持图片预览' || statusText.value === '图片预览失败，请稍后重试') {
    clearStatusText()
  }
}

function closeImagePreview(): void {
  activeImagePreviewUrl.value = ''
}

async function prepareChatVideo(message: ChatMessageItem): Promise<void> {
  const videoUrl = await ensureChatMediaBlob(message)
  if (!videoUrl) {
    statusText.value = hasChatMediaFailed(message) ? '视频文件加载失败，请稍后再点一次' : '视频文件正在加载，请稍后再点一次'
  }
}

async function playVoiceMessage(message: ChatMessageItem): Promise<void> {
  if (playingVoiceMessageId.value === message.serverMsgId) {
    stopActiveVoicePlayback()
    return
  }
  const storageUrl = await ensureChatMediaBlob(message)
  if (!storageUrl) {
    statusText.value = hasChatMediaFailed(message) ? '语音文件加载失败，请稍后再点一次' : '语音文件正在加载，请稍后再点一次'
    return
  }
  stopActiveVoicePlayback()
  try {
    if (canUseBrowserAudioPlayer()) {
      playVoiceMessageWithBrowserAudio(message, storageUrl)
      return
    }
    if (playVoiceMessageWithUniAudio(message, storageUrl)) return
    statusText.value = '当前环境暂不支持语音播放'
  } catch (error) {
    console.warn('chat voice play failed', { serverMsgId: message.serverMsgId, error })
    stopActiveVoicePlayback()
    statusText.value = voicePlaybackRetryText
  }
}

function canUseBrowserAudioPlayer(): boolean {
  return typeof Audio !== 'undefined'
}

function playVoiceMessageWithBrowserAudio(message: ChatMessageItem, storageUrl: string): void {
    const audio = new Audio(storageUrl)
    activeAudio = audio
    playingVoiceMessageId.value = message.serverMsgId
    audio.onended = stopActiveVoicePlayback
    audio.onerror = () => {
      stopActiveVoicePlayback()
      statusText.value = voicePlaybackRetryText
    }
    void audio.play().catch((error) => {
      console.warn('chat voice play failed', { serverMsgId: message.serverMsgId, error })
      stopActiveVoicePlayback()
      statusText.value = voicePlaybackRetryText
    })
}

function playVoiceMessageWithUniAudio(message: ChatMessageItem, storageUrl: string): boolean {
  const audioApi = uni as unknown as { createInnerAudioContext?: () => UniInnerAudioContext }
  if (typeof audioApi.createInnerAudioContext !== 'function') return false
  const audio = audioApi.createInnerAudioContext()
  activeUniAudio = audio
  activeUniAudioEndedHandler = stopActiveVoicePlayback
  activeUniAudioErrorHandler = (error: unknown) => {
    console.warn('chat uni voice play failed', { serverMsgId: message.serverMsgId, error })
    stopActiveVoicePlayback()
    statusText.value = voicePlaybackRetryText
  }
  audio.src = storageUrl
  audio.onEnded?.(activeUniAudioEndedHandler)
  audio.onError?.(activeUniAudioErrorHandler)
  playingVoiceMessageId.value = message.serverMsgId
  audio.play()
  return true
}

function stopActiveVoicePlayback(): void {
  if (activeAudio) {
    activeAudio.pause()
    activeAudio.onended = null
    activeAudio.onerror = null
  }
  activeAudio = null
  if (activeUniAudio) {
    if (activeUniAudioEndedHandler) activeUniAudio.offEnded?.(activeUniAudioEndedHandler)
    if (activeUniAudioErrorHandler) activeUniAudio.offError?.(activeUniAudioErrorHandler)
    activeUniAudio.stop?.()
    activeUniAudio.pause?.()
    activeUniAudio.destroy?.()
  }
  activeUniAudio = null
  activeUniAudioEndedHandler = null
  activeUniAudioErrorHandler = null
  playingVoiceMessageId.value = ''
}

async function prepareChatMediaBlobs(): Promise<void> {
  const nextCache: Record<string, string> = { ...chatMediaBlobUrls.value }
  const activeMessageIds = new Set(messages.value.map((message) => message.serverMsgId))
  for (const [messageId, objectUrl] of Object.entries(nextCache)) {
    const message = messages.value.find((item) => item.serverMsgId === messageId)
    if (!activeMessageIds.has(messageId) || (message && shouldReleaseChatMediaBlob(message))) {
      revokeObjectUrl(objectUrl)
      delete nextCache[messageId]
      if (playingVoiceMessageId.value === messageId) stopActiveVoicePlayback()
    }
  }
  chatMediaBlobUrls.value = nextCache
  chatMediaLoadingIds.value = pruneChatMediaState(chatMediaLoadingIds.value, activeMessageIds, messages.value)
  chatMediaFailedIds.value = pruneChatMediaState(chatMediaFailedIds.value, activeMessageIds, messages.value)
}

async function ensureChatMediaBlob(message: ChatMessageItem): Promise<string> {
  const cached = chatMediaBlobUrls.value[message.serverMsgId]
  if (cached) return cached
  if (!hasValidChatMediaStorage(message)) return ''
  if (chatMediaLoadingIds.value[message.serverMsgId]) return ''
  const storageUrl = chatMediaStorageUrl(message)
  setChatMediaState(chatMediaLoadingIds, message.serverMsgId, true)
  setChatMediaState(chatMediaFailedIds, message.serverMsgId, false)
  try {
    const objectUrl = await fetchAuthorizedBlobUrl(`/api/chat/media?url=${encodeURIComponent(storageUrl)}`)
    chatMediaBlobUrls.value = { ...chatMediaBlobUrls.value, [message.serverMsgId]: objectUrl }
    return objectUrl
  } catch (error) {
    console.warn('chat secure media fetch failed', { serverMsgId: message.serverMsgId, msgType: message.msgType, error })
    setChatMediaState(chatMediaFailedIds, message.serverMsgId, true)
    return ''
  } finally {
    setChatMediaState(chatMediaLoadingIds, message.serverMsgId, false)
  }
}

function isChatMediaLoading(message: ChatMessageItem): boolean {
  return chatMediaLoadingIds.value[message.serverMsgId] === true
}

function hasChatMediaFailed(message: ChatMessageItem): boolean {
  return chatMediaFailedIds.value[message.serverMsgId] === true
}

function setChatMediaState(target: typeof chatMediaLoadingIds, serverMsgId: string, enabled: boolean): void {
  const next = { ...target.value }
  if (enabled) next[serverMsgId] = true
  else delete next[serverMsgId]
  target.value = next
}

function shouldReleaseChatMediaBlob(message: ChatMessageItem): boolean {
  if (isMessageRevoked(message)) return true
  if (message.msgType !== 'IMAGE' && message.msgType !== 'VOICE' && message.msgType !== 'VIDEO') return true
  return !hasValidChatMediaStorage(message)
}

function pruneChatMediaState(state: Record<string, boolean>, activeMessageIds: Set<string>, activeMessages: ChatMessageItem[]): Record<string, boolean> {
  const messageById = new Map(activeMessages.map((message) => [message.serverMsgId, message]))
  return Object.fromEntries(Object.entries(state).filter(([messageId]) => {
    const message = messageById.get(messageId)
    return activeMessageIds.has(messageId) && !!message && !shouldReleaseChatMediaBlob(message)
  }))
}

function revokeObjectUrl(objectUrl: string): void {
  try {
    if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(objectUrl)
    }
  } catch {
    // Best-effort cleanup only.
  }
}

function releaseChatMediaBlobs(): void {
  Object.values(chatMediaBlobUrls.value).forEach(revokeObjectUrl)
  chatMediaBlobUrls.value = {}
  chatMediaLoadingIds.value = {}
  chatMediaFailedIds.value = {}
}

function messageMetaText(message: ChatMessageItem): string {
  const receipt = receiptText(message)
  return receipt ? `${formatTime(message.createdAt)} · ${receipt}` : formatTime(message.createdAt)
}

function receiptText(message: ChatMessageItem): string {
  if (isMessageRevoked(message)) return '已撤回'
  if (!isMine(message)) return ''
  if (message.readByReceiver) return '已读'
  if (message.deliveredToReceiver) return '已送达'
  return '已发送'
}

async function confirmChatAction(title: string, content: string, confirmText: string): Promise<boolean> {
  return new Promise((resolve) => {
    try {
      uni.showModal({
        title,
        content,
        showCancel: true,
        confirmText,
        cancelText: '取消',
        success: (result) => resolve(result.confirm === true)
      })
    } catch (error) {
      console.warn('chat modal failed', { title, error })
      resolve(false)
    }
  })
}

function markLocalMessageRevoked(serverMsgId: string): void {
  messages.value = messages.value.map((message) => {
    if (message.serverMsgId !== serverMsgId) return message
    return { ...message, revoked: true, contentJson: '{"revoked":true}' }
  })
  const revokedBlobUrl = chatMediaBlobUrls.value[serverMsgId]
  if (revokedBlobUrl) {
    revokeObjectUrl(revokedBlobUrl)
    const nextCache = { ...chatMediaBlobUrls.value }
    delete nextCache[serverMsgId]
    chatMediaBlobUrls.value = nextCache
  }
  if (playingVoiceMessageId.value === serverMsgId) stopActiveVoicePlayback()
}

async function handleRevokeMessage(message: ChatMessageItem): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能撤回消息'; return }
  if (!isMine(message)) { statusText.value = '只能撤回自己发送的消息'; return }
  if (isMessageRevoked(message)) { statusText.value = '这条消息已撤回'; return }
  if (!isMessageWithinRevokeWindow(message)) { statusText.value = '消息仅支持 2 分钟内撤回'; return }
  if (!canRevokeMessage(message)) { statusText.value = '这条消息暂不能撤回'; return }
  const confirmed = await confirmChatAction('撤回消息', '撤回后双方会看到“消息已撤回”。', '撤回')
  if (!confirmed) return
  try {
    const response = await revokeChatMessage(message.serverMsgId)
    if (response.conversationId !== message.conversationId || response.serverSeq !== message.serverSeq || response.serverMsgId !== message.serverMsgId || response.revoked !== true) {
      throw new Error('chat revoke response mismatch')
    }
    markLocalMessageRevoked(response.serverMsgId)
    showTransientStatus('消息已撤回')
    await syncConversationMessages(false, true)
  } catch (error) {
    console.warn('chat message revoke failed', { conversationId: message.conversationId, serverMsgId: message.serverMsgId, error })
    statusText.value = '消息撤回失败，请稍后重试'
  }
}

async function handleClearConversation(): Promise<void> {
  if (chatBlocked.value) { statusText.value = '聊天暂不可用，不能清空记录'; return }
  if (!conversationId.value) { statusText.value = '会话尚未创建，暂无聊天记录可清空'; return }
  if (recording.value) { statusText.value = '请先结束或取消录音，再清空聊天记录'; return }
  const activeConversationId = conversationId.value
  const confirmed = await confirmChatAction('清空聊天记录', '仅清空你当前账号可见的聊天记录，对方仍可看到自己的记录。', '清空')
  if (!confirmed) return
  try {
    const response = await clearChatConversation(activeConversationId)
    if (response.conversationId !== activeConversationId || !Number.isSafeInteger(response.clearedSeq) || response.clearedSeq < 0 || response.clearedSeq > response.lastServerSeq) {
      throw new Error('chat clear response mismatch')
    }
    stopActiveVoicePlayback()
    messages.value = []
    releaseChatMediaBlobs()
    nextAfterSeq.value = response.clearedSeq
    previousBeforeSeq.value = response.clearedSeq
    hasMore.value = false
    hasEarlierMessages.value = false
    showTransientStatus('聊天记录已清空，仅清空你本地可见记录')
  } catch (error) {
    console.warn('chat conversation clear failed', { conversationId: activeConversationId, error })
    statusText.value = '聊天记录清空失败，请稍后重试'
  }
}

function reportConversation(): void {
  if (chatBlocked.value) {
    uni.showToast({ title: '聊天暂不可用，不能提交举报', icon: 'none' })
    return
  }
  if (!conversationId.value) {
    uni.showToast({ title: '聊天还未建立，不能提交举报', icon: 'none' })
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
