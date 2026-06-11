const fs = require('fs')
const path = require('path')
const vm = require('vm')
const ts = require('typescript')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/chat/conversation/index.vue',
  'src/pages/chat/session-list/index.vue',
  'src/api/modules/chat.ts'
]
const supportFilesByFile = {
  'src/pages/chat/conversation/index.vue': [
    'src/pages/chat/conversation/chat-conversation-helpers.ts',
    'src/pages/chat/chat-peer.ts'
  ],
  'src/pages/chat/session-list/index.vue': [
    'src/pages/chat/chat-peer.ts'
  ]
}

function readSource(file) {
  const supportContent = (supportFilesByFile[file] || [])
    .map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8'))
    .join('\n')
  return [supportContent, fs.readFileSync(path.join(root, file), 'utf8')].filter(Boolean).join('\n')
}

function loadConversationHelpersForFixture() {
  const helperFile = path.join(root, 'src/pages/chat/conversation/chat-conversation-helpers.ts')
  const helperSource = fs.readFileSync(helperFile, 'utf8')
    .replace(/import\s+type\s+\{[\s\S]*?\}\s+from\s+['"][^'"]+['"]\s*/g, '')
    .replace(/import\s+\{[^}]*assertChatPeerIdentityFields[^}]*\}\s+from\s+['"][^'"]+['"]\s*/g, '')
  const compiled = ts.transpileModule(helperSource, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  }).outputText
  const sandbox = {
    exports: {},
    console,
    require,
    module: { exports: {} }
  }
  sandbox.module.exports = sandbox.exports
  vm.runInNewContext(compiled, sandbox, { filename: helperFile })
  return sandbox.module.exports
}

function assertChatContentFixtureBehavior() {
  const helpers = loadConversationHelpersForFixture()
  const {
    assertParsableChatMessageContent,
    imageFileSize,
    MAX_CHAT_IMAGE_UPLOAD_BYTES,
    normalizedVoiceMimeType,
    parseMessageContentForValidation,
    safeParsedMessageContent,
    videoFileSize,
    MAX_CHAT_VIDEO_UPLOAD_BYTES
  } = helpers
  if (
    typeof assertParsableChatMessageContent !== 'function' ||
    typeof imageFileSize !== 'function' ||
    MAX_CHAT_IMAGE_UPLOAD_BYTES !== 10_000_000 ||
    typeof normalizedVoiceMimeType !== 'function' ||
    typeof videoFileSize !== 'function' ||
    MAX_CHAT_VIDEO_UPLOAD_BYTES !== 80_000_000 ||
    typeof parseMessageContentForValidation !== 'function' ||
    typeof safeParsedMessageContent !== 'function'
  ) {
    console.error('src/pages/chat/conversation/chat-conversation-helpers.ts: missing exported safe chat content helpers')
    failed = true
    return
  }

  const baseMessage = {
    conversationId: 1,
    serverSeq: 1,
    serverMsgId: 'srv-1',
    clientMsgId: 'cli-1',
    senderId: 1,
    receiverId: 2,
    msgType: 'TEXT',
    createdAt: '2026-06-08T00:00:00Z'
  }
  const normalText = { ...baseMessage, contentJson: '{"text":"你好"}' }
  const doubleEncodedText = { ...baseMessage, serverMsgId: 'srv-2', contentJson: JSON.stringify(JSON.stringify({ text: '双重编码' })) }
  const legacyEscapedText = { ...baseMessage, serverMsgId: 'srv-3', contentJson: '{\\"text\\":\\"历史消息\\"}' }
  const malformedText = { ...baseMessage, serverMsgId: 'srv-4', contentJson: '{"text":' }
  const revokedMalformedVoice = { ...baseMessage, serverMsgId: 'srv-5', msgType: 'VOICE', revoked: true, contentJson: '{"url":' }

  try {
    if (imageFileSize(undefined) !== 10_000_000) throw new Error('unknown image size should request max CHAT_IMAGE ticket')
    if (imageFileSize({ size: 2_000_000 }) !== 2_000_000) throw new Error('known image size should be preserved')
    if (imageFileSize({ size: 20_000_000 }) !== 10_000_000) throw new Error('oversized image ticket request should be clamped')
    if (videoFileSize(undefined) !== 80_000_000) throw new Error('unknown video size should request max CHAT_VIDEO ticket')
    if (videoFileSize({ size: 2_000_000 }) !== 2_000_000) throw new Error('known video size should be preserved')
    if (videoFileSize({ size: 90_000_000 }) !== 80_000_000) throw new Error('oversized video ticket request should be clamped')
    if (parseMessageContentForValidation(normalText).text !== '你好') throw new Error('normal text not parsed')
    if (parseMessageContentForValidation(doubleEncodedText).text !== '双重编码') throw new Error('double encoded text not parsed')
    if (parseMessageContentForValidation(legacyEscapedText).text !== '历史消息') throw new Error('legacy escaped text not parsed')
    if (safeParsedMessageContent(malformedText) !== null) throw new Error('malformed content should be isolated')
    if (normalizedVoiceMimeType('audio/mp4;codecs=mp4a.40.2') !== 'audio/mp4') throw new Error('voice mime parameters should be normalized before upload ticket request')
    if (normalizedVoiceMimeType('audio/x-m4a; codecs=mp4a.40.2') !== 'audio/x-m4a') throw new Error('m4a mime parameters should be normalized before upload ticket request')
    assertParsableChatMessageContent(revokedMalformedVoice)
    const isolated = [normalText, malformedText, doubleEncodedText, revokedMalformedVoice].filter((message) => {
      try {
        assertParsableChatMessageContent(message)
        return true
      } catch {
        return false
      }
    })
    if (isolated.length !== 3 || isolated.some((message) => message.serverMsgId === malformedText.serverMsgId)) {
      throw new Error('bad message did not isolate while preserving good/revoked messages')
    }
  } catch (error) {
    console.error(`src/pages/chat/conversation/chat-conversation-helpers.ts: malformed/legacy content fixture failed: ${error.message}`)
    failed = true
  }
}

const forbiddenMarkers = [
  'demoMessages',
  'demoConversations',
  "serverMsgId: 'demo-1'",
  "receiverId.value = 21",
  '当前仅展示示例消息',
  '示例会话已标记已读',
  'openDemoProduct',
  'productId=9001',
  'response.conversations.length ? response.conversations : demoConversations',
  'conversations.value = demoConversations',
  'item.readSeq = item.lastServerSeq',
  'item.unreadCount = 0',
  'const currentUserId = 1',
  '8101',
  '小原圈主理人',
  '小鹿同学',
  '袜袜收藏家',
  '玫瑰女孩',
  "8: '桃桃'",
  "12: '可心'",
  "18: '晚晚'",
  '(receiverId.value ?? 21)',
  "readPositiveRouteNumber(options, 'peerUserId')",
  'refreshReceipts ? 0 : nextAfterSeq.value',
  '聊天留痕'
]

let failed = false
assertChatContentFixtureBehavior()
for (const file of files) {
  const content = readSource(file)
  for (const marker of forbiddenMarkers) {
    if (content.includes(marker)) {
      console.error(`${file}: forbidden IM demo/fake fallback marker found: ${marker}`)
      failed = true
    }
  }
  if (/v-model(?:\.trim)?=/.test(content)) {
    console.error(`${file}: chat forms must use explicit :value + @input bindings instead of v-model drift-prone bindings`)
    failed = true
  }
}

const conversationFile = 'src/pages/chat/conversation/index.vue'
const conversation = readSource(conversationFile)
const styleFile = 'src/pages/chat/conversation/style.scss'
const style = fs.readFileSync(path.join(root, styleFile), 'utf8')
const requiredConversationMarkers = [
  'getMyProfile',
  'getChatConversation',
  'getChatConversations',
  'syncRecentMessages',
  'syncEarlierMessages',
  'currentUserId.value',
  'peerName.value =',
  "const peerName = ref('聊天用户')",
  'const peerGender = ref<string | null>(null)',
  'const peerCity = ref<string | null>(null)',
  'const peerVideoVerified = ref(false)',
  'const peerSellerCharmScore = ref(0)',
  'const peerBuyerPowerScore = ref(0)',
  'const peerProfileTrusted = ref(false)',
  'const peerLevel = computed(() => buildChatPeerLevel(peerIdentitySource.value))',
  'const peerGenderBadge = computed(() => {',
  'normalizedPeerGender(peerIdentitySource.value)',
  'peerGenderSymbol(peerIdentitySource.value)',
  'const peerIdentityBadges = computed(() => chatPeerIdentityBadges(peerIdentitySource.value).filter((badge) => !badge.startsWith(\'LV.\') && badge !== \'♂\' && badge !== \'♀\'))',
  'function applyPeerProfile(profile: UserProfileResponse): void',
  'function applyPeerConversationItem(item: ChatConversationItem): void',
  "peerAvatarUrl.value = resolveBackendMediaUrl(validatedChatAvatarUrl(profile.avatarUrl || ''))",
  "peerAvatarUrl.value = resolveBackendMediaUrl(validatedChatAvatarUrl(item.peerAvatarUrl || ''))",
  'const peerProfileUnavailableText =',
  'function hasTrustedPeerProfileContext(peerUserId: number): boolean',
  'peerProfileTrusted.value &&',
  'if (!hasTrustedPeerProfileContext(peerUserId))',
  'peerProfileTrusted.value = true',
  'if (statusText.value === peerProfileUnavailableText) statusText.value = \'\'',
  "const chatImageStoragePrefix = '/uploads/chat-image/'",
  "const communityImageStoragePrefix = '/uploads/community-image/'",
  "const avatarImageStoragePrefix = '/uploads/avatar/'",
  'const MAX_CHAT_IMAGE_UPLOAD_BYTES = 10_000_000',
  'const requestedSize = Number.isFinite(selectedSize) && selectedSize > 0 ? selectedSize : MAX_CHAT_IMAGE_UPLOAD_BYTES',
  'function decodeRouteValue(fieldName: string, value: string): string',
  'function routeValue(options: Record<string, string | undefined> | undefined, fieldName: string): string',
  "console.warn('chat conversation route hash read failed'",
  'function readPositiveRouteNumber(options: Record<string, string | undefined> | undefined, fieldName: string): number | undefined',
  'function assertConversationItem(value: unknown): asserts value is ChatConversationItem',
  'function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse',
  'function assertMessageSyncResponse(value: unknown): asserts value is MessageSyncResponse',
  'function assertSendMessageResponse(value: unknown): asserts value is SendMessageResponse',
  'function assertSendMessageResponseForRequest(value: unknown, payload: SendMessageRequest): SendMessageResponse',
  'function hasAckedServerMessage(response: SendMessageResponse): boolean',
  'function parseMessageContentForValidation(message: ChatMessageItem): Record<string, unknown>',
  'class ChatDataIntegrityError extends Error',
  'function blockChat(reason: string): void',
  'function showTransientStatus(message: string, duration = 1800): void',
  'function setPersistentStatus(message: string): void',
  'function clearTransientStatusTimer(): void',
  'function clearStatusText(): void',
  'function assertActiveConversationMessage(message: ChatMessageItem, activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number): void',
  'function initializeChatPage(options: Record<string, string | undefined> | undefined): Promise<void>',
  'async function recoverFromRouteConversationMismatch(routeConversationId: number, routeReceiverId: number): Promise<void>',
  'function verifyRouteConversation(routeConversationId: number, routeReceiverId: number): Promise<ChatConversationItem>',
  "console.warn('chat route conversation verification failed'",
  "console.warn('chat route conversation mismatch recovered by receiver discovery'",
  'let discoveryFailureCount = 0',
  'discoveryFailureCount += 1',
  "const peerProfileUnavailableText = '对方资料暂时不可用，仍可继续聊天'",
  'setPersistentStatus(peerProfileUnavailableText)',
  "return '此条消息暂不可用'",
  "throw new ChatDataIntegrityError('chat message conversation mismatch')",
  "throw new ChatDataIntegrityError('chat message participant mismatch')",
  "throw new Error('chat local send insertion missing required state')",
  "throw new Error('chat send ack clientMsgId mismatch')",
  "throw new Error('chat send ack receiver mismatch')",
  "throw new Error('chat send ack msgType mismatch')",
  "throw new Error('chat send ack conversation mismatch')",
  "throw new Error('chat read response cursor mismatch')",
  'function discoverConversationWithPeer(showStatus: boolean): Promise<boolean',
  'discoveringConversation.value',
  'const matched = response.conversations.find((item) => item.peerUserId === receiverId.value)',
  'conversationId.value = matched.conversationId',
  '可以直接发送第一条消息',
  "console.warn('chat conversation discovery failed'",
  'function mergeServerMessages(serverMessages: ChatMessageItem[]): void',
  'await syncConversationMessages(false, true)',
  "console.warn('chat conversation sync failed'",
  "console.warn('chat conversation read receipt failed'",
  "console.warn('chat image picker failed'",
  "console.warn('chat image send failed'",
  "console.warn('chat image preview failed'",
  "console.warn('chat voice record start failed'",
  "console.warn('chat voice send failed'",
  "console.warn('chat uni voice record start failed'",
  "console.warn('chat uni voice send failed'",
  "console.warn('chat voice play failed'",
  "console.warn('chat message send failed'",
  "console.warn('chat report navigation failed'",
  "console.warn('chat draft input event invalid'",
  "console.warn('chat message content parse failed'",
  'const MAX_TEXT_MESSAGE_LENGTH = 1000',
  'const KEYBOARD_FOCUS_FALLBACK_INSET = 120',
  ':maxlength="MAX_TEXT_MESSAGE_LENGTH"',
  '消息最多 ${MAX_TEXT_MESSAGE_LENGTH} 字',
  ':scroll-into-view="bottomAnchorId"',
  'const bottomAnchorId = ref',
  'const keyboardInset = ref(0)',
  'const recordingElapsedMs = ref(0)',
  'const previousBeforeSeq = ref(0)',
  'const hasEarlierMessages = ref(false)',
  'function scrollMessagesToBottom',
  'function installKeyboardInsetListeners',
  'function startVoiceRecordTicker(): void',
  'function stopVoiceRecordTicker(): void',
  'const recordingElapsedText = computed(() => formatDurationSeconds',
  'function formatDurationSeconds(seconds: number): string',
  'window.visualViewport',
  'scrollMessagesToBottom()',
  '暂不能发送消息',
  'conversationId.value = response.ack.conversationId',
  '请输入消息内容后再发送',
  '图片暂不可用',
  '语音暂不可用',
  '视频暂不可用',
  "createMediaUploadTicket({ scene: 'CHAT_VOICE'",
  "createMediaUploadTicket({ scene: 'CHAT_VIDEO'",
  'uploadMediaTicketBlob(ticket, blob',
  'uploadMediaTicketFile(ticket, tempFilePath)',
  "await handleSend('VOICE'",
  "await handleSend('VIDEO'",
  'const MAX_CHAT_VOICE_UPLOAD_BYTES = 10_000_000',
  'const MAX_CHAT_VIDEO_UPLOAD_BYTES = 80_000_000',
  'const MAX_CHAT_VIDEO_DURATION_MS = 300_000',
  "type VoiceRecordingMode = 'browser' | 'uni' | ''",
  'function canUseBrowserVoiceRecorder(): boolean',
  'function browserVoiceUnsupportedReason(): string',
  'window.isSecureContext !== true',
  'navigator.mediaDevices?.getUserMedia',
  "typeof fetch !== 'function' || typeof FormData === 'undefined'",
  '语音录制需要 HTTPS 安全连接，请切换到 HTTPS 后重试',
  '当前浏览器暂不支持麦克风录制，请换用手机浏览器',
  '当前浏览器暂不支持语音文件上传，请换用手机浏览器',
  'function startUniVoiceRecording(): boolean',
  'function getUniVoiceRecorder(): UniVoiceRecorderManager | null',
  'getRecorderManager',
  'function handleRecordedUniVoice(tempFilePath: string, durationMs: number, fileSize?: number): Promise<void>',
  'function hasInvalidTempChatVoicePath(path: string): boolean',
  'function sendVideoPlaceholder(): void',
  'function handleSendVideo(localPath: string, result: ChooseVideoResult): Promise<void>',
  'function hasInvalidTempChatVideoPath(path: string): boolean',
  'function hasInvalidChatVideoStorageUrl(url: unknown): boolean',
  "browserVoiceUnsupportedReason() || '当前环境暂不支持语音录制，请换用手机浏览器或安全连接'",
  "statusText.value = '语音文件过大，请重新录制'",
  'function chatVoiceMessageUrl(message: ChatMessageItem): string',
  'function chatVideoMessageUrl(message: ChatMessageItem): string',
  'function isMessageUnavailable(message: ChatMessageItem): boolean',
  'function voicePlaybackStateClass(message: ChatMessageItem): string',
  'class="voice-state-dot"',
  'async function prepareChatVideo(message: ChatMessageItem): Promise<void>',
  'async function previewChatImage(message: ChatMessageItem): Promise<void>',
  'function isMessageRevoked(message: ChatMessageItem): boolean',
  'function canRevokeMessage(message: ChatMessageItem): boolean',
  'const MESSAGE_REVOKE_WINDOW_MS = 2 * 60 * 1000',
  'const revokeWindowNowMs = ref(Date.now())',
  'let revokeWindowTimer: ReturnType<typeof setInterval> | null = null',
  'function startRevokeWindowTicker(): void',
  'function stopRevokeWindowTicker(): void',
  'startRevokeWindowTicker()',
  'stopRevokeWindowTicker()',
  'function isMessageWithinRevokeWindow(message: ChatMessageItem): boolean',
  'revokeWindowNowMs.value - createdAtMs <= MESSAGE_REVOKE_WINDOW_MS',
  'async function handleRevokeMessage(message: ChatMessageItem): Promise<void>',
  'async function handleClearConversation(): Promise<void>',
  'const response = await revokeChatMessage(message.serverMsgId)',
  'const response = await clearChatConversation(activeConversationId)',
  'function markLocalMessageRevoked(serverMsgId: string): void',
  'nextAfterSeq.value = response.clearedSeq',
  "showTransientStatus('聊天记录已清空，仅清空你本地可见记录')",
  "showTransientStatus('消息已撤回')",
  "showTransientStatus(hasEarlierMessages.value ? '消息已发送，可查看更早消息' : '消息已发送')",
  '撤回后双方会看到“消息已撤回”。',
  "statusText.value = '消息仅支持 2 分钟内撤回'",
  "if (message.revoked === true) return true",
  "return { ...message, revoked: true, contentJson: '{\"revoked\":true}' }",
  'async function playVoiceMessage(message: ChatMessageItem): Promise<void>',
  'function canUseBrowserAudioPlayer(): boolean',
  'function playVoiceMessageWithBrowserAudio(message: ChatMessageItem, storageUrl: string): void',
  'function playVoiceMessageWithUniAudio(message: ChatMessageItem, storageUrl: string): boolean',
  'createInnerAudioContext',
  "console.warn('chat uni voice play failed'",
  'return chatMediaBlobUrls.value[message.serverMsgId] || \'\'',
  'const chatMediaLoadingIds = ref<Record<string, boolean>>({})',
  'const chatMediaFailedIds = ref<Record<string, boolean>>({})',
  'async function ensureChatMediaBlob(message: ChatMessageItem): Promise<string>',
  'function isChatMediaLoading(message: ChatMessageItem): boolean',
  'function hasChatMediaFailed(message: ChatMessageItem): boolean',
  'function shouldReleaseChatMediaBlob(message: ChatMessageItem): boolean',
  'shouldReleaseChatMediaBlob(message)',
  "if (hasChatMediaFailed(message)) return `${label} 加载失败，点击重试`",
  'function hasInvalidStoredImageUrl(url: unknown, storagePrefix: string): boolean',
  'function hasInvalidChatVoiceStorageUrl(url: unknown): boolean',
  'function validatedChatAvatarUrl(url: unknown): string',
  'function updateDraft(event: unknown): void',
  'const composerPlaceholder = computed(() => recording.value ?',
  'const sendButtonDisabled = computed(() => textComposerBlocked.value || !hasDraftText.value)',
  ':disabled="sendButtonDisabled"',
  'class="composer-tools"',
  'class="composer-input-wrap"',
  'clientMsgId: `h5-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`',
  'chatImageMessageUrl(message)'
]
for (const marker of requiredConversationMarkers) {
  if (!conversation.includes(marker)) {
    console.error(`${conversationFile}: missing server-derived IM identity marker: ${marker}`)
    failed = true
  }
}

const forbiddenConversationPatterns = [
  {
    pattern: /const\s+text\s*=\s*draft\.value\s*\|\|\s*['"`]你好，请问宝贝还在吗？['"`]/,
    message: 'empty text messages must fail closed instead of sending a default local chat phrase'
  },
  {
    pattern: /平台担保沟通中|已开启风控保护|平台担保聊天/,
    message: 'chat page must not present static escrow/risk-control trust state; show neutral chat-record copy unless backend conversation/order state proves it'
  }
]
for (const { pattern, message } of forbiddenConversationPatterns) {
  if (pattern.test(conversation)) {
    console.error(`${conversationFile}: ${message}`)
    failed = true
  }
}

if (/#[{]?[{]?\s*message\.serverSeq/.test(conversation) || /#\{\{\s*message\.serverSeq\s*\}\}/.test(conversation)) {
  console.error(`${conversationFile}: user-facing chat bubbles must not expose internal server sequence ids`)
  failed = true
}

if (
  !style.includes('.message-bottom-spacer') ||
  !/height:var\(--chat-bottom-reserve\)/.test(style) ||
  !/--chat-keyboard-inset:0px/.test(style) ||
  !/--chat-mobile-bottom-clearance:12rpx/.test(style) ||
  !/--chat-composer-bottom-offset:max\(var\(--chat-keyboard-inset\), calc\(var\(--chat-mobile-bottom-clearance\) \+ env\(safe-area-inset-bottom\)\)\)/.test(style) ||
  !/--chat-status-height:92rpx/.test(style) ||
  !/--chat-status-gap:12rpx/.test(style) ||
  !/--chat-composer-reserve:calc\(/.test(style) ||
  !/--chat-status-reserve:(?:0rpx|calc\(0px\))/.test(style) ||
  !/--chat-bottom-reserve:calc\(var\(--chat-composer-reserve\) \+ var\(--chat-status-reserve\)\)/.test(style) ||
  !/padding:calc\(16rpx \+ var\(--global-ticker-offset, 0rpx\)\) 18rpx 0/.test(style) ||
  !/\.chat-page\.has-status\s*\{[^}]*--chat-status-reserve:calc\(var\(--chat-status-height\) \+ var\(--chat-status-gap\)\)/.test(style) ||
  !/\.status-bar\s*\{[^}]*bottom:calc\(var\(--chat-composer-reserve\) \+ var\(--chat-status-gap\)\)/.test(style) ||
  !/\.composer\s*\{[^}]*bottom:var\(--chat-composer-bottom-offset\)/.test(style)
) {
  console.error(`${styleFile}: chat message list must reserve composer/status/keyboard height through shared CSS variables`)
  failed = true
}

if (
  /(^|[;{]\s*)height:calc\(100vh\s*-/.test(style) ||
  /160rpx/.test(style) ||
  !/\.message-scroll-wrap\s*\{[^}]*flex:1;[^}]*min-height:0;/.test(style) ||
  !/\.message-scroll\s*\{[^}]*height:100%;[^}]*min-height:0;/.test(style) ||
  !/@supports \(height: 100dvh\)/.test(style)
) {
  console.error(`${styleFile}: chat message list must use flex remaining height instead of fragile fixed viewport subtraction`)
  failed = true
}

if (!conversation.includes(':class="{ \'has-status\': !!statusText }"') || !conversation.includes('cursor-spacing="96"') || !conversation.includes('@focus="handleComposerFocus"') || !conversation.includes('@blur="handleComposerBlur"') || !conversation.includes('function handleComposerFocus') || !conversation.includes('function handleComposerBlur') || !conversation.includes('setTimeout(scrollMessagesToBottom, 180)') || !conversation.includes('keyboardInset.value = 0') || !conversation.includes('measuredInset <= 0 ? KEYBOARD_FOCUS_FALLBACK_INSET : measuredInset') || !conversation.includes('keyboardInset.value = composerFocused.value ? KEYBOARD_FOCUS_FALLBACK_INSET : 0')) {
  console.error(`${conversationFile}: chat page must expose status-aware bottom spacing and scroll to bottom when the mobile keyboard focuses the input`)
  failed = true
}

if (!conversation.includes(':class="{ disabled: composerBlocked, recording }"') || /disabled:\s*composerBlocked\s*\|\|\s*recording/.test(conversation)) {
  console.error(`${conversationFile}: recording voice button must show the recording state instead of looking disabled`)
  failed = true
}

if (!conversation.includes('const textComposerBlocked = computed(() => composerBlocked.value || recording.value)') ||
  !conversation.includes(':disabled="textComposerBlocked"') ||
  !/async function handleSendText\(\): Promise<void>[\s\S]*if \(recording\.value\)[\s\S]*请先结束或取消录音，再发送文字[\s\S]*await handleSend\('TEXT'\)/s.test(conversation) ||
  !/function sendImagePlaceholder\(\): void[\s\S]*if \(recording\.value\) \{ statusText\.value = '请先结束或取消录音，再发送图片'; return \}/s.test(conversation) ||
  !/async function handleSend\(type: 'TEXT' \| 'IMAGE' \| 'VOICE' \| 'VIDEO'[\s\S]*if \(recording\.value && type !== 'VOICE'\)/s.test(conversation)) {
  console.error(`${conversationFile}: recording state must block text/image sending while keeping the voice stop action available`)
  failed = true
}

if (!conversation.includes('class="back-action tapable"') || !conversation.includes('function goBackToSessions(): void') || !conversation.includes("uni.navigateBack") || !conversation.includes("url: '/pages/chat/session-list/index'") || !conversation.includes("console.warn('chat back navigation failed'")) {
  console.error(`${conversationFile}: chat page must provide a visible back action with a session-list fallback for H5 deep links`)
  failed = true
}

if (!conversation.includes('class="voice-control"') || !conversation.includes('class="recording-status"') || !conversation.includes('class="recording-cancel tapable"') || !style.includes('.voice-control') || !style.includes('.recording-status') || !style.includes('.recording-cancel') || /class="tool cancel-voice/.test(conversation)) {
  console.error(`${conversationFile}: recording cancel action must use a refined status strip instead of taking an extra composer column on narrow mobile screens`)
  failed = true
}

if (
  !conversation.includes('class="voice-mic-icon"') ||
  !conversation.includes('class="voice-stop-icon"') ||
  !conversation.includes('class="media-image-icon"') ||
  !conversation.includes('class="media-plus-mark"') ||
  !conversation.includes('class="composer-tools"') ||
  !conversation.includes('class="composer-input-wrap"') ||
  !conversation.includes('class="send-arrow-icon"') ||
  !conversation.includes(':aria-label="sendButtonLabel"') ||
  !conversation.includes(':disabled="sendButtonDisabled"') ||
  !style.includes('.voice-mic-icon') ||
  !style.includes('.voice-stop-icon') ||
  !style.includes('.media-image-icon') ||
  !style.includes('.media-plus-mark') ||
  !style.includes('.composer-tools') ||
  !style.includes('.composer-input-wrap.focused') ||
  !style.includes('.send-arrow-icon') ||
  !style.includes('.send-btn.ready') ||
  /{{\s*recording\s*\?\s*'停'\s*:\s*'语'\s*}}/.test(conversation)
) {
  console.error(`${conversationFile}: mature IM composer must use icon-only microphone, media, and send controls instead of text labels`)
  failed = true
}

if (
  !conversation.includes('class="message-voice-bubble tapable"') ||
  !conversation.includes('class="voice-play-ring"') ||
  !conversation.includes('voicePlaybackStateClass(message)') ||
  !conversation.includes('class="voice-state-dot"') ||
  !conversation.includes('class="voice-wave-bar tall"') ||
  !conversation.includes('voicePlaybackStateText(message)') ||
  !conversation.includes('voiceDurationText(message)') ||
  !style.includes('.message-voice-bubble.playing') ||
  !style.includes('.voice-state-dot.playing') ||
  !style.includes('@keyframes voiceRing') ||
  !style.includes('@keyframes voicePulse') ||
  !style.includes('.voice-state')
) {
  console.error(`${conversationFile}: voice messages must render as a refined playback bubble with waveform, duration, and state text`)
  failed = true
}

if (
  !conversation.includes('class="recording-time"') ||
  !conversation.includes('{{ recordingElapsedText }}') ||
  !conversation.includes('class="recording-bars"') ||
  !style.includes('.recording-time') ||
  !style.includes('.recording-bars') ||
  !style.includes('.recording-orb')
) {
  console.error(`${conversationFile}: recording state must show a compact timer and animated recording meter`)
  failed = true
}

if (
  !conversation.includes('class="peer-gender-mark"') ||
  !conversation.includes(':class="peerGenderBadge.genderClass"') ||
  !style.includes('.peer-badges .peer-gender-mark.god') ||
  !style.includes('.peer-badges .peer-gender-mark.goddess') ||
  !style.includes('#4f8cff') ||
  !style.includes('#ff6fa7')
) {
  console.error(`${conversationFile}: peer gender in chat header must render as a colored symbol badge, blue for male and pink for female`)
  failed = true
}

if (!conversation.includes('@click.stop="handleRevokeMessage(message)"') || !conversation.includes("if (recording.value) { statusText.value = '请先结束或取消录音，再清空聊天记录'; return }")) {
  console.error(`${conversationFile}: revoke/clear actions must avoid mobile mis-taps and block destructive clear while a voice recording is active`)
  failed = true
}

if (!conversation.includes('@click="previewChatImage(message)"') || !/async function previewChatImage\(message: ChatMessageItem\): Promise<void>[\s\S]*await ensureChatMediaBlob\(message\)[\s\S]*previewImage[\s\S]*urls: messages\.value\.map\(\(item\) => chatImageMessageUrl\(item\)\)\.filter\(Boolean\)[\s\S]*console\.warn\('chat image preview failed'/s.test(conversation)) {
  console.error(`${conversationFile}: real chat images must open a backend-media preview gallery and handle preview failures`)
  failed = true
}

if (!/function isMessageUnavailable\(message: ChatMessageItem\): boolean[\s\S]*message\.msgType === 'IMAGE' \|\| message\.msgType === 'VOICE' \|\| message\.msgType === 'VIDEO'[\s\S]*!hasValidChatMediaStorage\(message\)/s.test(conversation) || !conversation.includes("if (!hasValidChatMediaStorage(message)) return '语音文件暂不可播放'") || !conversation.includes("if (!hasValidChatMediaStorage(message)) return '视频文件暂不可播放'")) {
  console.error(`${conversationFile}: unavailable image/voice/video bubbles must have explicit non-playable state instead of looking like ordinary messages`)
  failed = true
}

const requiredNeutralConversationCopy = [
  '消息暂不可用'
]
for (const marker of requiredNeutralConversationCopy) {
  if (!conversation.includes(marker)) {
    console.error(`${conversationFile}: missing neutral IM trust copy marker: ${marker}`)
    failed = true
  }
}

if (!/function startMessageSync\(\): void[\s\S]*if \(conversationId\.value\) void syncConversationMessages\(false, true\)[\s\S]*else if \(receiverId\.value\) void discoverConversationWithPeer\(false\)/s.test(conversation)) {
  console.error(`${conversationFile}: receiverId-only chat pages must poll server conversations so incoming first messages create and attach the real conversation`)
  failed = true
}

if (!/async function discoverConversationWithPeer\(showStatus: boolean\): Promise<boolean>[\s\S]*getChatConversations\(\)[\s\S]*assertConversationListResponse\(response\)[\s\S]*response\.conversations\.find\(\(item\) => item\.peerUserId === receiverId\.value\)[\s\S]*conversationId\.value = matched\.conversationId[\s\S]*await syncConversationMessages\(true, true\)/s.test(conversation)) {
  console.error(`${conversationFile}: receiverId-only chat discovery must use real backend conversation list, validate it, bind matched peer conversation, and sync messages`)
  failed = true
}

if (!/function startMessageSync\(\): void[\s\S]*chatBlocked\.value \|\| syncTimer \|\| !shouldSyncMessages\(\)[\s\S]*setInterval\(\(\) => \{[\s\S]*if \(chatBlocked\.value\) return[\s\S]*if \(!shouldSyncMessages\(\)\) \{[\s\S]*stopMessageSync\(\)[\s\S]*return[\s\S]*\}[\s\S]*if \(conversationId\.value\) void syncConversationMessages\(false, true\)[\s\S]*else if \(receiverId\.value\) void discoverConversationWithPeer\(false\)/s.test(conversation)) {
  console.error(`${conversationFile}: blocked or hidden chat state must not restart message polling or receiverId discovery`)
  failed = true
}

if (!/async function discoverConversationWithPeer\(showStatus: boolean\): Promise<boolean>[\s\S]*if \(chatBlocked\.value \|\| discoveringConversation\.value \|\| conversationId\.value \|\| !receiverId\.value\) return false[\s\S]*discoveryFailureCount = 0[\s\S]*catch \(error\)[\s\S]*discoveryFailureCount \+= 1[\s\S]*discoveryFailureCount >= 3[\s\S]*聊天刷新暂不可用，请稍后重试/s.test(conversation)) {
  console.error(`${conversationFile}: receiverId-only background discovery failures must be counted and surfaced, and blocked chats must not be rediscovered`)
  failed = true
}

if (!/async function verifyRouteConversation\(routeConversationId: number, routeReceiverId: number\): Promise<ChatConversationItem>[\s\S]*getChatConversation\(routeConversationId\)[\s\S]*assertConversationItem\(matched\)[\s\S]*matched\.conversationId !== routeConversationId \|\| matched\.peerUserId !== routeReceiverId[\s\S]*return matched/s.test(conversation)) {
  console.error(`${conversationFile}: route conversationId must be verified against backend conversation detail and receiverId before binding active chat state, so cleared conversations are not rejected by list filtering`)
  failed = true
}

if (!/async function recoverFromRouteConversationMismatch\(routeConversationId: number, routeReceiverId: number\): Promise<void>[\s\S]*conversationId\.value = undefined[\s\S]*messages\.value = \[\][\s\S]*releaseChatMediaBlobs\(\)[\s\S]*chatBlocked\.value = false[\s\S]*const discovered = await discoverConversationWithPeer\(true\)[\s\S]*if \(!discovered\) showTransientStatus\('已按目标用户打开私聊，可直接发送第一条消息'[\s\S]*startMessageSync\(\)/s.test(conversation)) {
  console.error(`${conversationFile}: mismatched route conversationId with a valid receiverId must recover into receiver discovery instead of locking the composer`)
  failed = true
}

if (/conversationId\.value = routeConversationId/.test(conversation)) {
  console.error(`${conversationFile}: must not directly trust route conversationId without backend ownership verification`)
  failed = true
}

const chatApi = fs.readFileSync(path.join(root, 'src/api/modules/chat.ts'), 'utf8')
if (!/export function getChatConversation\(conversationId: number\)[\s\S]*\/api\/chat\/conversations\/\$\{conversationId\}/s.test(chatApi)) {
  console.error('src/api/modules/chat.ts: chat route verification must use a single-conversation backend endpoint, not only the filtered conversation list')
  failed = true
}

const mockData = fs.readFileSync(path.join(root, 'src/api/mock-data.ts'), 'utf8')
for (const marker of [
  "msgType: 'VOICE'",
  "msgType: 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO'",
  "scene === 'CHAT_VIDEO'",
  "mockUpload('CHAT_VOICE'",
  "mockUpload('CHAT_IMAGE'",
  'function buildConversationItem(item: MockConversation): ChatConversationItem',
  'const conversationDetailMatch = url.match(/^\\/api\\/chat\\/conversations\\/(\\d+)$/)',
  'const revokeMessageMatch = url.match(/^\\/api\\/chat\\/messages\\/([^/]+)\\/revoke$/)',
  'const clearConversationMatch = url.match(/^\\/api\\/chat\\/conversations\\/(\\d+)\\/clear$/)',
  'const chatMediaMatch = url.match(/^\\/api\\/chat\\/media(?:\\?.*)?$/)',
  "url === '/api/media/upload-tickets'",
  'const uploadTicketFileMatch = url.match(/^\\/api\\/media\\/upload-tickets\\/([^/]+)\\/file$/)',
  "payload.clientMsgId || `client-${Date.now()}`",
  "msgType: payload.msgType || 'TEXT'"
]) {
  if (!mockData.includes(marker)) {
    console.error(`src/api/mock-data.ts: local IM mock must cover real chat flow marker: ${marker}`)
    failed = true
  }
}

const httpApi = fs.readFileSync(path.join(root, 'src/api/http.ts'), 'utf8')
for (const marker of [
  "mockResponse<T>(options.url, 'POST', options)",
  "mockResponse<Blob>(url, 'GET')",
  'return URL.createObjectURL(mocked)'
]) {
  if (!httpApi.includes(marker)) {
    console.error(`src/api/http.ts: upload/blob authorized media helpers must honor local mock mode marker: ${marker}`)
    failed = true
  }
}

const chatService = fs.readFileSync(path.join(root, '../backend/src/main/java/com/secondhand/platform/modules/chat/application/ChatApplicationService.java'), 'utf8')
if (!/video_identity_status[\s\S]*APPROVED[\s\S]*video_verified[\s\S]*TRUE[\s\S]*main_role[\s\S]*SELLER[\s\S]*BOTH[\s\S]*peer_video_verified/s.test(chatService)) {
  console.error('../backend/src/main/java/com/secondhand/platform/modules/chat/application/ChatApplicationService.java: chat peer video badge must be derived from approved seller video identity, not raw video_verified alone')
  failed = true
}

if (!/function assertActiveConversationMessage\(message: ChatMessageItem, activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number\): void[\s\S]*message\.conversationId !== activeConversationId[\s\S]*message\.senderId === activeCurrentUserId && message\.receiverId === activeReceiverId[\s\S]*message\.senderId === activeReceiverId && message\.receiverId === activeCurrentUserId[\s\S]*throw new ChatDataIntegrityError\('chat message participant mismatch'\)/s.test(conversation)) {
  console.error(`${conversationFile}: messages must be bound to the active conversation and exact two participants before merge/render`)
  failed = true
}

if (!/async function syncConversationMessages\(showStatus: boolean, refreshReceipts: boolean, latest = false\): Promise<boolean>[\s\S]*!currentUserId\.value \|\| !receiverId\.value[\s\S]*console\.warn\('chat conversation sync missing participant state'[\s\S]*blockChat\('暂时无法进入聊天，请重新打开'\)[\s\S]*const syncAfterSeq = nextAfterSeq\.value[\s\S]*const activeConversationId = conversationId\.value[\s\S]*const activeCurrentUserId = currentUserId\.value[\s\S]*const activeReceiverId = receiverId\.value[\s\S]*latest && syncAfterSeq === 0[\s\S]*syncRecentMessages\(activeConversationId, 50\)[\s\S]*syncMessages\(activeConversationId, syncAfterSeq, 50\)[\s\S]*assertMessageSyncResponse\(response\)[\s\S]*const validMessages = filterActiveConversationMessages\(response\.messages, activeConversationId, activeCurrentUserId, activeReceiverId\)[\s\S]*mergeServerMessages\(validMessages\)[\s\S]*nextAfterSeq\.value = Math\.max\(response\.nextAfterSeq \|\| syncAfterSeq, syncAfterSeq\)[\s\S]*hasMore\.value = response\.hasMore[\s\S]*return true/s.test(conversation)) {
  console.error(`${conversationFile}: sync must fail closed without participants, use latest recent-window sync only for initial cursor, poll from nextAfterSeq afterwards, validate response envelope, isolate malformed messages, and update pagination only from sync response`)
  failed = true
}

if (!/const usedRecentWindow = latest && syncAfterSeq === 0[\s\S]*if \(usedRecentWindow\) \{[\s\S]*if \(response\.previousBeforeSeq != null\) previousBeforeSeq\.value = response\.previousBeforeSeq[\s\S]*hasEarlierMessages\.value = response\.hasEarlier === true[\s\S]*\}/s.test(conversation)) {
  console.error(`${conversationFile}: incremental polling must not clear the earlier-message cursor returned by the initial latest-window sync`)
  failed = true
}

if (!/async function loadMoreMessages\(\): Promise<void>[\s\S]*syncConversationMessages\(true, false, nextAfterSeq\.value === 0\)/s.test(conversation)) {
  console.error(`${conversationFile}: initial chat load must request the latest real backend message window instead of the oldest page`)
  failed = true
}

if (!/async function loadEarlierMessages\(\): Promise<void>[\s\S]*syncEarlierMessages\(activeConversationId, previousBeforeSeq\.value, 50\)[\s\S]*assertMessageSyncResponse\(response\)[\s\S]*const validMessages = filterActiveConversationMessages\(response\.messages, activeConversationId, activeCurrentUserId, activeReceiverId\)[\s\S]*mergeEarlierMessages\(validMessages\)[\s\S]*hasEarlierMessages\.value = response\.hasEarlier === true/s.test(conversation)) {
  console.error(`${conversationFile}: long conversations must expose a backend-backed earlier-message loader instead of making messages before the latest window unreachable`)
  failed = true
}

if (!/const receiptRefreshWindow = 200[\s\S]*function visibleReceiptRefreshAfterSeq\(\): number[\s\S]*Math\.max\(0, maxVisibleSeq - receiptRefreshWindow\)[\s\S]*async function refreshVisibleReceiptStates\(activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number\): Promise<void>[\s\S]*syncMessages\(activeConversationId, receiptAfterSeq, receiptRefreshWindow\)[\s\S]*assertMessageSyncResponse\(response\)[\s\S]*const validMessages = filterActiveConversationMessages\(response\.messages, activeConversationId, activeCurrentUserId, activeReceiverId\)[\s\S]*mergeServerMessages\(validMessages\)/s.test(conversation)) {
  console.error(`${conversationFile}: visible messages must refresh backend receipt state from a bounded recent window without local read/delivered fabrication`)
  failed = true
}

if (!/const canAutoAcknowledge = shouldAutoAcknowledgeMessages\(\)[\s\S]*if \(refreshReceipts && canAutoAcknowledge\) await refreshVisibleReceiptStates\(activeConversationId, activeCurrentUserId, activeReceiverId\)/.test(conversation)) {
  console.error(`${conversationFile}: visible sync must refresh receipt state only while the chat page can safely acknowledge messages`)
  failed = true
}

if (!/markConversationDelivered,[\s\S]*markConversationRead,/s.test(conversation)) {
  console.error(`${conversationFile}: conversation page must import explicit delivered receipts before read receipts`)
  failed = true
}

if (!/let autoDeliveredInFlight = false[\s\S]*async function autoMarkDeliveredAfterSync\(deliveredSeq: number\): Promise<void>[\s\S]*markConversationDelivered\(activeConversationId\)[\s\S]*response\.deliveredSeq < deliveredSeq[\s\S]*console\.warn\('chat conversation auto delivered receipt failed'/s.test(conversation)) {
  console.error(`${conversationFile}: visible received messages must explicitly mark delivered through the backend without fabricating local state`)
  failed = true
}

if (!/catch \(error\)[\s\S]*error instanceof ChatDataIntegrityError[\s\S]*blockChat\('聊天内容暂时无法显示，请重新打开'\)[\s\S]*if \(showStatus\)[\s\S]*statusText\.value = '消息刷新失败，请稍后重试'[\s\S]*statusText\.value = '消息刷新失败，可能有新消息未显示，请稍后重试'/s.test(conversation)) {
  console.error(`${conversationFile}: only integrity failures may block chat; transient visible/manual sync failures must keep existing messages usable and show retry copy`)
  failed = true
}

if (!/const syncedAfterSend = await syncConversationMessages\(false, true\)[\s\S]*if \(chatBlocked\.value\) return[\s\S]*startMessageSync\(\)[\s\S]*sent = true/s.test(conversation)) {
  console.error(`${conversationFile}: send flow must not overwrite terminal chat blocking with local success state after post-send sync`)
  failed = true
}

if (!/function assertChatMessage\(value: unknown\): asserts value is ChatMessageItem[\s\S]*message\.conversationId[\s\S]*message\.senderId[\s\S]*message\.receiverId[\s\S]*message\.serverSeq[\s\S]*message\.serverMsgId[\s\S]*message\.clientMsgId[\s\S]*message\.msgType[\s\S]*message\.contentJson[\s\S]*message\.createdAt/s.test(conversation)) {
  console.error(`${conversationFile}: message sync must validate structural envelope before merge/render`)
  failed = true
}

const malformedMessageIsolationMarkers = [
  'function parseChatContentObject(contentJson: string): Record<string, unknown>',
  'contentJson.trim().replace(',
  'parseChatContentObject(message.contentJson)',
  "console.warn('chat message content parse failed'",
  "if (!content) return '此条消息暂不可用'",
  "message.msgType === 'VOICE'",
  'normalizedVoiceDurationSeconds(content)'
]
if (!malformedMessageIsolationMarkers.every((marker) => conversation.includes(marker))) {
  console.error(`${conversationFile}: malformed per-message content must be isolated to one unavailable bubble and voice content must render without blocking the whole chat`)
  failed = true
}

if (!/function assertSendMessageResponseForRequest\(value: unknown, payload: SendMessageRequest\): SendMessageResponse[\s\S]*ack\.clientMsgId !== payload\.clientMsgId[\s\S]*ack\.receiverId !== payload\.receiverId[\s\S]*ack\.senderId !== currentUserId\.value[\s\S]*ack\.msgType !== payload\.msgType[\s\S]*payload\.conversationId && ack\.conversationId !== payload\.conversationId[\s\S]*return value/s.test(conversation)) {
  console.error(`${conversationFile}: send ack must be validated against the outbound payload before binding conversation`)
  failed = true
}

if (!/function hasAckedServerMessage\(response: SendMessageResponse\): boolean[\s\S]*messages\.value\.some\(\(message\) => message\.serverSeq === ack\.serverSeq \|\| message\.serverMsgId === ack\.serverMsgId\)/s.test(conversation)) {
  console.error(`${conversationFile}: send ack must tolerate polling-merged server messages without showing a false failure`)
  failed = true
}

if (!/const response = await sendMessage\(payload\)[\s\S]*assertSendMessageResponseForRequest\(response, payload\)[\s\S]*conversationId\.value = response\.ack\.conversationId[\s\S]*if \(!hasAckedServerMessage\(response\)\) pushLocalMessage\(response\.ack\.serverSeq, response\.ack\.serverMsgId, payload\)/s.test(conversation)) {
  console.error(`${conversationFile}: send flow must validate request-bound ack and skip local insertion when polling already merged the message`)
  failed = true
}

if (!/function pushLocalMessage\([\s\S]*deliveredToReceiver: false[\s\S]*readByReceiver: false/s.test(conversation)) {
  console.error(`${conversationFile}: locally pushed messages must not claim receiver delivery/read before backend receipts confirm it`)
  failed = true
}

if (!/async function handleRevokeMessage\(message: ChatMessageItem\): Promise<void>[\s\S]*canRevokeMessage\(message\)[\s\S]*revokeChatMessage\(message\.serverMsgId\)[\s\S]*response\.conversationId !== message\.conversationId \|\| response\.serverSeq !== message\.serverSeq \|\| response\.serverMsgId !== message\.serverMsgId \|\| response\.revoked !== true[\s\S]*markLocalMessageRevoked\(response\.serverMsgId\)[\s\S]*await syncConversationMessages\(false, true\)/s.test(conversation)) {
  console.error(`${conversationFile}: revoke must call backend, validate the exact response, mark the message revoked locally, and refresh server state`)
  failed = true
}

if (!/async function handleClearConversation\(\): Promise<void>[\s\S]*clearChatConversation\(activeConversationId\)[\s\S]*response\.conversationId !== activeConversationId[\s\S]*response\.clearedSeq > response\.lastServerSeq[\s\S]*messages\.value = \[\][\s\S]*nextAfterSeq\.value = response\.clearedSeq[\s\S]*聊天记录已清空，仅清空你本地可见记录/s.test(conversation)) {
  console.error(`${conversationFile}: clear conversation must call backend, validate clearedSeq, clear only current visible messages, and move cursor to clearedSeq`)
  failed = true
}

if (/messages\.value\s*=\s*messages\.value\.filter\(\(message\) => message\.serverMsgId !==/.test(conversation)) {
  console.error(`${conversationFile}: revoke must not fake-delete messages locally; it must mark backend-revoked state`)
  failed = true
}

if (conversation.includes('if (!conversationId.value || !currentUserId.value) return')) {
  console.error(`${conversationFile}: locally pushed messages must fail loudly when backend ack cannot be inserted into active conversation state`)
  failed = true
}

const pushLocalMessageMatch = conversation.match(/function pushLocalMessage\([\s\S]*?\n}\n\nfunction updateDraft/)
if (!pushLocalMessageMatch) {
  console.error(`${conversationFile}: missing pushLocalMessage function block for cursor safety check`)
  failed = true
} else if (/nextAfterSeq\.value\s*=/.test(pushLocalMessageMatch[0])) {
  console.error(`${conversationFile}: locally pushed messages must not advance nextAfterSeq; only backend sync responses may move the receive cursor`)
  failed = true
}

if (!/function maxVisibleReceivedSeq\(activeReceiverId: number\): number[\s\S]*message\.senderId === activeReceiverId \? Math\.max\(maxSeq, message\.serverSeq\) : maxSeq/s.test(conversation)) {
  console.error(`${conversationFile}: chat page must derive the visible received cursor from messages sent by the active peer`)
  failed = true
}

if (!/function autoReadableReceivedSeq\(activeReceiverId: number\): number[\s\S]*if \(hasEarlierMessages\.value\) return 0[\s\S]*return maxVisibleReceivedSeq\(activeReceiverId\)/s.test(conversation)) {
  console.error(`${conversationFile}: automatic read receipts must not advance across an initial latest-window gap before earlier received messages are loaded`)
  failed = true
}

if (!/const visibleReceivedSeq = maxVisibleReceivedSeq\(activeReceiverId\)[\s\S]*if \(canAutoAcknowledge && visibleReceivedSeq > 0\) \{[\s\S]*void autoMarkDeliveredAfterSync\(visibleReceivedSeq\)[\s\S]*const readableReceivedSeq = autoReadableReceivedSeq\(activeReceiverId\)[\s\S]*if \(readableReceivedSeq > 0\) void autoMarkReadAfterSync\(readableReceivedSeq\)[\s\S]*\}/s.test(conversation)) {
  console.error(`${conversationFile}: chat page must automatically mark received visible messages delivered, then mark read only for a continuous visible prefix`)
  failed = true
}

if (!/async function autoMarkReadAfterSync\(readSeq: number\): Promise<void>[\s\S]*chatBlocked\.value \|\| !isChatPageVisible\(\) \|\| autoReadInFlight \|\| !conversationId\.value \|\| readSeq <= 0[\s\S]*const response = await markConversationRead\(activeConversationId, \{ readSeq \}\)[\s\S]*response\.conversationId !== activeConversationId \|\| response\.readSeq > readSeq \|\| !Number\.isSafeInteger\(response\.unreadCount\) \|\| response\.unreadCount < 0[\s\S]*console\.warn\('chat conversation auto read receipt failed'/s.test(conversation)) {
  console.error(`${conversationFile}: automatic read receipts must be bounded, backend-acknowledged, and non-blocking on transient failure`)
  failed = true
}

if (!/onShow\(\(\) => \{[\s\S]*handleChatPageVisible\(\)[\s\S]*\}\)[\s\S]*onHide\(\(\) => \{[\s\S]*handleChatPageHidden\(\)[\s\S]*\}\)/s.test(conversation) ||
  !/const pageVisible = ref\(true\)/.test(conversation) ||
  !/function installPageVisibilityListeners\(\): void[\s\S]*document\.addEventListener\('visibilitychange', handleVisibilityChange\)[\s\S]*document\.removeEventListener\('visibilitychange', handleVisibilityChange\)/s.test(conversation) ||
  !/function shouldAutoAcknowledgeMessages\(\): boolean[\s\S]*return shouldSyncMessages\(\) && !!conversationId\.value/s.test(conversation) ||
  !/function handleChatPageHidden\(\): void[\s\S]*pageVisible\.value = false[\s\S]*stopMessageSync\(\)[\s\S]*stopActiveVoicePlayback\(\)/s.test(conversation) ||
  !/function handleChatPageVisible\(\): void[\s\S]*pageVisible\.value = true[\s\S]*resumeVisibleChatSync\(\)/s.test(conversation)) {
  console.error(`${conversationFile}: chat page must pause polling, voice playback, and automatic read acknowledgement while hidden, then resume visible sync on return`)
  failed = true
}

if (/markConversationRead\(conversationId\.value\)(?!,)/.test(conversation)) {
  console.error(`${conversationFile}: read receipt must pass an explicit bounded readSeq`)
  failed = true
}

if (/const requestedReadSeq = nextAfterSeq\.value/.test(conversation) ||
  !/async function handleMarkRead\(\): Promise<void>[\s\S]*autoReadableReceivedSeq\(receiverId\.value\)[\s\S]*请先加载更早消息后再标记已读[\s\S]*markConversationRead\(conversationId\.value, \{ readSeq: requestedReadSeq \}\)[\s\S]*response\.readSeq > requestedReadSeq/s.test(conversation)) {
  console.error(`${conversationFile}: manual read receipt must reuse the safe readable cursor and refuse latest-window gaps`)
  failed = true
}

if (!/const voicePlaybackRetryText = '语音播放失败，请确认浏览器允许音频播放后再点一次'[\s\S]*statusText\.value = hasChatMediaFailed\(message\) \? '语音文件加载失败，请稍后再点一次' : '语音文件正在加载，请稍后再点一次'[\s\S]*statusText\.value = voicePlaybackRetryText/s.test(conversation)) {
  console.error(`${conversationFile}: voice playback failures must show actionable retry/permission guidance`)
  failed = true
}

if (!/function hasInvalidTempChatImagePath\(path: string\): boolean[\s\S]*path\.startsWith\('local:\/\/'\)[\s\S]*path\.startsWith\('data:'\)[\s\S]*lower\.includes\('%2e'\)[\s\S]*path\.includes\('\\\\'\)/s.test(conversation)) {
  console.error(`${conversationFile}: chat temp image paths must reject local/data/traversal values before ticket upload while allowing H5 blob picker paths`)
  failed = true
}

if (/function hasInvalidTempChatImagePath\(path: string\): boolean[\s\S]*path\.startsWith\('blob:'\)/s.test(conversation)) {
  console.error(`${conversationFile}: H5 chooseImage can return blob: temp paths; only stored CHAT_IMAGE URLs should reject blob:`)
  failed = true
}

if (!/function hasInvalidStoredImageUrl\(url: unknown, storagePrefix: string\): boolean[\s\S]*!url\.startsWith\(storagePrefix\)[\s\S]*url\.startsWith\('blob:'\)[\s\S]*url\.startsWith\('data:'\)[\s\S]*lower\.includes\('%2e'\)[\s\S]*relativePath\.split\('\/'\)\.some/s.test(conversation)) {
  console.error(`${conversationFile}: stored image URL helper must reject wrong prefix and traversal/local/blob/data values before send/display`)
  failed = true
}

if (!/function hasInvalidChatImageStorageUrl\(url: unknown\): boolean[\s\S]*hasInvalidStoredImageUrl\(url, chatImageStoragePrefix\)/s.test(conversation)) {
  console.error(`${conversationFile}: chat image storage URL must be validated against CHAT_IMAGE prefix before send/display`)
  failed = true
}

if (/600_000/.test(conversation)) {
  console.error(`${conversationFile}: unknown chat image picker file size must request the backend CHAT_IMAGE maximum instead of a small fallback ticket`)
  failed = true
}

for (const marker of [
  'fetchAuthorizedBlobUrl',
  '`/api/chat/media?url=${encodeURIComponent(storageUrl)}`',
  'const chatMediaBlobUrls = ref<Record<string, string>>({})',
  'const chatMediaLoadingIds = ref<Record<string, boolean>>({})',
  'const chatMediaFailedIds = ref<Record<string, boolean>>({})',
  'function prepareChatMediaBlobs(): Promise<void>',
  'async function ensureChatMediaBlob(message: ChatMessageItem): Promise<string>',
  'setChatMediaState(chatMediaLoadingIds, message.serverMsgId, true)',
  'setChatMediaState(chatMediaFailedIds, message.serverMsgId, true)',
  'function releaseChatMediaBlobs(): void',
  'URL.revokeObjectURL(objectUrl)'
]) {
  if (!conversation.includes(marker)) {
    console.error(`${conversationFile}: chat private media must be read through authorized blob URL helper: ${marker}`)
    failed = true
  }
}

if (/new Audio\(resolveBackendMediaUrl\(storageUrl\)\)/.test(conversation)) {
  console.error(`${conversationFile}: chat voice playback must not directly read private /uploads/chat-voice through static media URLs`)
  failed = true
}

if (/chatImageMessageUrl[\s\S]*resolveBackendMediaUrl\(url as string\)/.test(conversation)) {
  console.error(`${conversationFile}: chat image display must not directly read private /uploads/chat-image through static media URLs`)
  failed = true
}

if (!/function validatedChatAvatarUrl\(url: unknown\): string[\s\S]*communityImageStoragePrefix[\s\S]*avatarImageStoragePrefix[\s\S]*return ''/s.test(conversation)) {
  console.error(`${conversationFile}: peer avatar URL must be validated against backend avatar/community-image media prefixes before display`)
  failed = true
}

const sessionFile = 'src/pages/chat/session-list/index.vue'
const sessionList = readSource(sessionFile)
for (const marker of [
  "console.warn('chat session list load failed'",
  "console.warn('chat session invalid conversation isolated'",
  "console.warn('chat session conversation navigation failed'",
  "console.warn('chat session keyword input invalid'",
  'function isValidBackendId(value: unknown): value is number',
  'function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse',
  'function assertConversationItem(value: unknown): asserts value is ChatConversationItem',
  'function validConversationItems(items: unknown[]): ChatConversationItem[]',
  'function assertChatPeerIdentityFields(item: ChatConversationItem): void',
  'chatPeerIdentityBadges(item)',
  'assertConversationListResponse(response)',
  "const avatarImageStoragePrefix = '/uploads/avatar/'",
  'function validatedCommunityImageUrl(url: unknown): string',
  'function updateKeyword(event: unknown): void',
  ':value="keyword"'
]) {
  if (!sessionList.includes(marker)) {
    console.error(`${sessionFile}: missing fail-closed session-list marker: ${marker}`)
    failed = true
  }
}

if (!/function openConversation\(item: ChatConversationItem\): void[\s\S]*!isValidBackendId\(item\.conversationId\) \|\| !isValidBackendId\(item\.peerUserId\)[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)\s*\{[\s\S]*console\.warn\('chat session conversation navigation failed'/s.test(sessionList)) {
  console.error(`${sessionFile}: opening a conversation must validate backend IDs and handle async/synchronous navigation failures`)
  failed = true
}

if (sessionList.includes('peerUserId=${encodeURIComponent(String(item.peerUserId))}')) {
  console.error(`${sessionFile}: conversation navigation must pass receiverId because the conversation page only binds a verified receiverId route param`)
  failed = true
}

if (!/url:\s*`\/pages\/chat\/conversation\/index\?conversationId=\$\{encodeURIComponent\(String\(item\.conversationId\)\)\}&receiverId=\$\{encodeURIComponent\(String\(item\.peerUserId\)\)\}`/s.test(sessionList)) {
  console.error(`${sessionFile}: conversation navigation must pass conversationId and receiverId together for backend ownership verification`)
  failed = true
}

if (!/async function loadConversations\(showLoading = true, preserveOnError = false\): Promise<void>[\s\S]*assertConversationListResponse\(response\)[\s\S]*const safeConversations = validConversationItems\(response\.conversations\)[\s\S]*safeConversations\.length === 0 && response\.conversations\.length > 0[\s\S]*conversations\.value = safeConversations[\s\S]*if \(!preserveOnError\) conversations\.value = \[\][\s\S]*if \(!preserveOnError \|\| conversations\.value\.length === 0\)/s.test(sessionList)) {
  console.error(`${sessionFile}: background session refresh must preserve the last valid real conversation list on transient failures`)
  failed = true
}

if (!/function startConversationRefresh\(\): void[\s\S]*setInterval\(\(\) => \{ void loadConversations\(false, true\) \}, 5000\)/s.test(sessionList)) {
  console.error(`${sessionFile}: session list must run a guarded 5s backend refresh for visible incoming-message prompts`)
  failed = true
}

if (!/function validConversationItems\(items: unknown\[\]\): ChatConversationItem\[\][\s\S]*const validItems: ChatConversationItem\[\] = \[\][\s\S]*assertConversationItem\(item\)[\s\S]*validItems\.push\(item\)[\s\S]*console\.warn\('chat session invalid conversation isolated'/s.test(sessionList)) {
  console.error(`${sessionFile}: session list must isolate invalid single conversations instead of clearing the whole real conversation list`)
  failed = true
}

if (!/function validatedCommunityImageUrl\(url: unknown\): string[\s\S]*communityImageStoragePrefix[\s\S]*avatarImageStoragePrefix[\s\S]*url\.startsWith\('blob:'\)[\s\S]*relativePath\.split\('\/'\)\.some/s.test(sessionList)) {
  console.error(`${sessionFile}: session list avatar URL must be validated against backend avatar/community-image media prefixes before display`)
  failed = true
}

if (!sessionList.includes("function peerAvatarUrl(item: ChatConversationItem): string { return resolveBackendMediaUrl(validatedCommunityImageUrl(item.peerAvatarUrl || '')) }")) {
  console.error(`${sessionFile}: session list avatar URLs must resolve through backend media URL helper after validation`)
  failed = true
}

if (failed) process.exit(1)
console.log('chat no-demo-fallback check passed')
