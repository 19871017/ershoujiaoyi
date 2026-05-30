const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/chat/conversation/index.vue',
  'src/pages/chat/session-list/index.vue'
]
const supportFilesByFile = {
  'src/pages/chat/conversation/index.vue': [
    'src/pages/chat/conversation/chat-conversation-helpers.ts'
  ]
}

function readSource(file) {
  const supportContent = (supportFilesByFile[file] || [])
    .map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8'))
    .join('\n')
  return [supportContent, fs.readFileSync(path.join(root, file), 'utf8')].filter(Boolean).join('\n')
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
  'markConversationDelivered(conversationId.value)'
]

let failed = false
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
const requiredConversationMarkers = [
  'getMyProfile',
  'getChatConversations',
  'currentUserId.value',
  'peerName.value =',
  "const peerName = ref('聊天用户')",
  "const chatImageStoragePrefix = '/uploads/chat-image/'",
  "const communityImageStoragePrefix = '/uploads/community-image/'",
  'function decodeRouteValue(fieldName: string, value: string): string',
  'function routeValue(options: Record<string, string | undefined> | undefined, fieldName: string): string',
  "console.warn('chat conversation route hash read failed'",
  'function readPositiveRouteNumber(options: Record<string, string | undefined> | undefined, fieldName: string): number | undefined',
  'function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse',
  'function assertMessageSyncResponse(value: unknown): asserts value is MessageSyncResponse',
  'function assertSendMessageResponse(value: unknown): asserts value is SendMessageResponse',
  'function assertSendMessageResponseForRequest(value: unknown, payload: SendMessageRequest): asserts value is SendMessageResponse',
  'function parseMessageContentForValidation(message: ChatMessageItem): { text?: unknown; url?: unknown }',
  'class ChatDataIntegrityError extends Error',
  'function blockChat(reason: string): void',
  'function assertActiveConversationMessage(message: ChatMessageItem, activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number): void',
  'function initializeChatPage(options: Record<string, string | undefined> | undefined): Promise<void>',
  'function verifyRouteConversation(routeConversationId: number, routeReceiverId: number): Promise<ChatConversationItem>',
  "console.warn('chat route conversation verification failed'",
  'let discoveryFailureCount = 0',
  'discoveryFailureCount += 1',
  "statusText.value = '聊天用户资料暂不可用，消息仍以平台会话为准'",
  "throw new ChatDataIntegrityError('chat text message content invalid')",
  "throw new ChatDataIntegrityError('chat message conversation mismatch')",
  "throw new ChatDataIntegrityError('chat message participant mismatch')",
  "throw new Error('chat local send insertion missing required state')",
  "throw new Error('chat send ack clientMsgId mismatch')",
  "throw new Error('chat send ack receiver mismatch')",
  "throw new Error('chat send ack msgType mismatch')",
  "throw new Error('chat send ack conversation mismatch')",
  "throw new Error('chat send ack duplicate server message')",
  "throw new Error('chat read response cursor mismatch')",
  'function discoverConversationWithPeer(showStatus: boolean): Promise<boolean',
  'discoveringConversation.value',
  'const matched = response.conversations.find((item) => item.peerUserId === receiverId.value)',
  'conversationId.value = matched.conversationId',
  '等待平台会话创建；对方发来第一条消息后会自动同步',
  "console.warn('chat conversation discovery failed'",
  'function mergeServerMessages(serverMessages: ChatMessageItem[]): void',
  'await syncConversationMessages(false, true)',
  "console.warn('chat conversation sync failed'",
  "console.warn('chat conversation read receipt failed'",
  "console.warn('chat image picker failed'",
  "console.warn('chat image send failed'",
  "console.warn('chat message send failed'",
  "console.warn('chat report navigation failed'",
  "console.warn('chat draft input event invalid'",
  "console.warn('chat message content parse failed'",
  '暂不能发送消息',
  'conversationId.value = response.ack.conversationId',
  '消息不能为空',
  '图片暂不可用',
  'function hasInvalidStoredImageUrl(url: unknown, storagePrefix: string): boolean',
  'function validatedCommunityImageUrl(url: unknown): string',
  'function updateDraft(event: unknown): void',
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

const requiredNeutralConversationCopy = [
  '聊天留痕',
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

if (!/function startMessageSync\(\): void[\s\S]*chatBlocked\.value \|\| syncTimer[\s\S]*setInterval\(\(\) => \{[\s\S]*if \(chatBlocked\.value\) return[\s\S]*if \(conversationId\.value\) void syncConversationMessages\(false, true\)[\s\S]*else if \(receiverId\.value\) void discoverConversationWithPeer\(false\)/s.test(conversation)) {
  console.error(`${conversationFile}: blocked chat state must not restart message polling or receiverId discovery`)
  failed = true
}

if (!/async function discoverConversationWithPeer\(showStatus: boolean\): Promise<boolean>[\s\S]*if \(chatBlocked\.value \|\| discoveringConversation\.value \|\| conversationId\.value \|\| !receiverId\.value\) return false[\s\S]*discoveryFailureCount = 0[\s\S]*catch \(error\)[\s\S]*discoveryFailureCount \+= 1[\s\S]*discoveryFailureCount >= 3[\s\S]*会话同步暂不可用，请稍后重试/s.test(conversation)) {
  console.error(`${conversationFile}: receiverId-only background discovery failures must be counted and surfaced, and blocked chats must not be rediscovered`)
  failed = true
}

if (!/async function verifyRouteConversation\(routeConversationId: number, routeReceiverId: number\): Promise<ChatConversationItem>[\s\S]*getChatConversations\(\)[\s\S]*assertConversationListResponse\(response\)[\s\S]*item\.conversationId === routeConversationId && item\.peerUserId === routeReceiverId[\s\S]*return matched/s.test(conversation)) {
  console.error(`${conversationFile}: route conversationId must be verified against backend conversation list and receiverId before binding active chat state`)
  failed = true
}

if (/conversationId\.value = routeConversationId/.test(conversation)) {
  console.error(`${conversationFile}: must not directly trust route conversationId without backend ownership verification`)
  failed = true
}

if (!/function assertActiveConversationMessage\(message: ChatMessageItem, activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number\): void[\s\S]*message\.conversationId !== activeConversationId[\s\S]*message\.senderId === activeCurrentUserId && message\.receiverId === activeReceiverId[\s\S]*message\.senderId === activeReceiverId && message\.receiverId === activeCurrentUserId[\s\S]*throw new ChatDataIntegrityError\('chat message participant mismatch'\)/s.test(conversation)) {
  console.error(`${conversationFile}: messages must be bound to the active conversation and exact two participants before merge/render`)
  failed = true
}

if (!/async function syncConversationMessages\(showStatus: boolean, refreshReceipts: boolean\): Promise<boolean>[\s\S]*!currentUserId\.value \|\| !receiverId\.value[\s\S]*console\.warn\('chat conversation sync missing participant state'[\s\S]*blockChat\('缺少会话参与人，不能展示或发送聊天内容'\)[\s\S]*const syncAfterSeq = nextAfterSeq\.value[\s\S]*const activeConversationId = conversationId\.value[\s\S]*const activeCurrentUserId = currentUserId\.value[\s\S]*const activeReceiverId = receiverId\.value[\s\S]*syncMessages\(activeConversationId, syncAfterSeq, 50\)[\s\S]*assertMessageSyncResponse\(response\)[\s\S]*for \(const message of response\.messages\) assertActiveConversationMessage\(message, activeConversationId, activeCurrentUserId, activeReceiverId\)[\s\S]*mergeServerMessages\(response\.messages\)[\s\S]*nextAfterSeq\.value = Math\.max\(response\.nextAfterSeq \|\| syncAfterSeq, syncAfterSeq\)[\s\S]*hasMore\.value = response\.hasMore[\s\S]*return true/s.test(conversation)) {
  console.error(`${conversationFile}: sync must fail closed without participants, poll from nextAfterSeq, validate response, bind every message to the active conversation participants, and update pagination only from sync response`)
  failed = true
}

if (!/const receiptRefreshWindow = 200[\s\S]*function visibleReceiptRefreshAfterSeq\(\): number[\s\S]*Math\.max\(0, maxVisibleSeq - receiptRefreshWindow\)[\s\S]*async function refreshVisibleReceiptStates\(activeConversationId: number, activeCurrentUserId: number, activeReceiverId: number\): Promise<void>[\s\S]*syncMessages\(activeConversationId, receiptAfterSeq, receiptRefreshWindow\)[\s\S]*assertMessageSyncResponse\(response\)[\s\S]*for \(const message of response\.messages\) assertActiveConversationMessage\(message, activeConversationId, activeCurrentUserId, activeReceiverId\)[\s\S]*mergeServerMessages\(response\.messages\)/s.test(conversation)) {
  console.error(`${conversationFile}: visible messages must refresh backend receipt state from a bounded recent window without local read/delivered fabrication`)
  failed = true
}

if (!/if \(refreshReceipts\) await refreshVisibleReceiptStates\(activeConversationId, activeCurrentUserId, activeReceiverId\)/.test(conversation)) {
  console.error(`${conversationFile}: background sync must refresh visible receipt state so sender sees receiver delivered/read updates without new messages`)
  failed = true
}

if (!/catch \(error\)[\s\S]*error instanceof ChatDataIntegrityError[\s\S]*blockChat\('聊天数据校验失败，不能展示或发送聊天内容'\)[\s\S]*if \(showStatus\)[\s\S]*blockChat\('消息暂不可用，不能展示或发送聊天内容'\)/s.test(conversation)) {
  console.error(`${conversationFile}: data-integrity and visible/manual sync failures must clear messages, stop polling, and block chat instead of leaving stale state usable`)
  failed = true
}

if (!/const syncedAfterSend = await syncConversationMessages\(false, true\)[\s\S]*if \(chatBlocked\.value\) return[\s\S]*startMessageSync\(\)[\s\S]*sent = true/s.test(conversation)) {
  console.error(`${conversationFile}: send flow must not overwrite terminal chat blocking with local success state after post-send sync`)
  failed = true
}

if (!/function parseMessageContentForValidation\(message: ChatMessageItem\): \{ text\?: unknown; url\?: unknown \}[\s\S]*JSON\.parse\(message\.contentJson\)[\s\S]*throw new ChatDataIntegrityError\('chat message contentJson malformed'\)[\s\S]*function assertChatMessage\(value: unknown\): asserts value is ChatMessageItem[\s\S]*const content = parseMessageContentForValidation\(message\)[\s\S]*message\.msgType === 'TEXT' && \(typeof content\.text !== 'string' \|\| !content\.text\.trim\(\)\)[\s\S]*message\.msgType === 'IMAGE' && hasInvalidChatImageStorageUrl\(content\.url\)/s.test(conversation)) {
  console.error(`${conversationFile}: message contentJson must be parsed and schema-validated by msgType before merge/render`)
  failed = true
}

if (!/function assertSendMessageResponseForRequest\(value: unknown, payload: SendMessageRequest\): asserts value is SendMessageResponse[\s\S]*ack\.clientMsgId !== payload\.clientMsgId[\s\S]*ack\.receiverId !== payload\.receiverId[\s\S]*ack\.senderId !== currentUserId\.value[\s\S]*ack\.msgType !== payload\.msgType[\s\S]*payload\.conversationId && ack\.conversationId !== payload\.conversationId[\s\S]*messages\.value\.some\(\(message\) => message\.serverSeq === ack\.serverSeq \|\| message\.serverMsgId === ack\.serverMsgId\)/s.test(conversation)) {
  console.error(`${conversationFile}: send ack must be validated against the outbound payload before binding conversation or pushing local message`)
  failed = true
}

if (!/const response = await sendMessage\(payload\)[\s\S]*assertSendMessageResponseForRequest\(response, payload\)[\s\S]*conversationId\.value = response\.ack\.conversationId[\s\S]*pushLocalMessage\(response\.ack\.serverSeq, response\.ack\.serverMsgId, payload\)/s.test(conversation)) {
  console.error(`${conversationFile}: send flow must use request-bound ack validation before local message insertion`)
  failed = true
}

if (!/function pushLocalMessage\([\s\S]*deliveredToReceiver: false[\s\S]*readByReceiver: false/s.test(conversation)) {
  console.error(`${conversationFile}: locally pushed messages must not claim receiver delivery/read before backend receipts confirm it`)
  failed = true
}

if (conversation.includes('if (!conversationId.value || !currentUserId.value) return')) {
  console.error(`${conversationFile}: locally pushed messages must fail loudly when backend ack cannot be inserted into active conversation state`)
  failed = true
}

if (/function pushLocalMessage\([\s\S]*nextAfterSeq\.value\s*=/.test(conversation)) {
  console.error(`${conversationFile}: locally pushed messages must not advance nextAfterSeq; only backend sync responses may move the receive cursor`)
  failed = true
}

if (!/async function handleMarkRead\(\): Promise<void>[\s\S]*const requestedReadSeq = nextAfterSeq\.value[\s\S]*markConversationRead\(conversationId\.value, \{ readSeq: requestedReadSeq \}\)[\s\S]*response\.conversationId !== conversationId\.value \|\| response\.readSeq !== requestedReadSeq \|\| response\.readSeq > nextAfterSeq\.value/s.test(conversation)) {
  console.error(`${conversationFile}: read receipts must only accept backend acknowledgement for the requested synced cursor`)
  failed = true
}

if (/markConversationRead\(conversationId\.value\)(?!,)/.test(conversation)) {
  console.error(`${conversationFile}: read receipt must pass an explicit bounded readSeq`)
  failed = true
}

if (!/function hasInvalidTempChatImagePath\(path: string\): boolean[\s\S]*path\.startsWith\('local:\/\/'\)[\s\S]*path\.startsWith\('blob:'\)[\s\S]*path\.startsWith\('data:'\)[\s\S]*lower\.includes\('%2e'\)[\s\S]*path\.includes\('\\\\'\)/s.test(conversation)) {
  console.error(`${conversationFile}: chat temp image paths must reject local/blob/data/traversal values before ticket upload`)
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

if (!/function validatedCommunityImageUrl\(url: unknown\): string[\s\S]*hasInvalidStoredImageUrl\(url, communityImageStoragePrefix\)[\s\S]*typeof url === 'string' \? url : ''/s.test(conversation)) {
  console.error(`${conversationFile}: peer avatar URL must be validated against backend COMMUNITY_IMAGE media prefix before display`)
  failed = true
}

const sessionFile = 'src/pages/chat/session-list/index.vue'
const sessionList = readSource(sessionFile)
for (const marker of [
  "console.warn('chat session list load failed'",
  "console.warn('chat session read mutation failed'",
  "console.warn('chat session conversation navigation failed'",
  "console.warn('chat session keyword input invalid'",
  'function isValidBackendId(value: unknown): value is number',
  'function assertConversationListResponse(value: unknown): asserts value is ChatConversationListResponse',
  'function assertConversationItem(value: unknown): asserts value is ChatConversationItem',
  'assertConversationListResponse(response)',
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

if (!/async function loadConversations\(showLoading = true, preserveOnError = false\): Promise<void>[\s\S]*if \(!preserveOnError\) errorText\.value = ''[\s\S]*if \(!preserveOnError\) conversations\.value = \[\][\s\S]*if \(!preserveOnError \|\| conversations\.value\.length === 0\)/s.test(sessionList)) {
  console.error(`${sessionFile}: background session refresh must preserve the last valid real conversation list on transient failures`)
  failed = true
}

if (!/function startConversationRefresh\(\): void[\s\S]*setInterval\(\(\) => \{ void loadConversations\(false, true\) \}, 5000\)/s.test(sessionList)) {
  console.error(`${sessionFile}: session list must run a guarded 5s backend refresh for visible incoming-message prompts`)
  failed = true
}

if (!/function handleMarkRead\(item: ChatConversationItem\): Promise<void>[\s\S]*const requestedReadSeq = item\.lastServerSeq[\s\S]*markConversationRead\(item\.conversationId, \{ readSeq: requestedReadSeq \}\)[\s\S]*response\.conversationId !== item\.conversationId \|\| response\.readSeq !== requestedReadSeq \|\| response\.readSeq > item\.lastServerSeq[\s\S]*item\.readSeq = response\.readSeq[\s\S]*item\.unreadCount = response\.unreadCount/s.test(sessionList)) {
  console.error(`${sessionFile}: session list read mutation must only update local read state after matching backend acknowledgement`)
  failed = true
}

if (!/function validatedCommunityImageUrl\(url: unknown\): string[\s\S]*!url\.startsWith\(communityImageStoragePrefix\)[\s\S]*url\.startsWith\('blob:'\)[\s\S]*relativePath\.split\('\/'\)\.some/s.test(sessionList)) {
  console.error(`${sessionFile}: session list avatar URL must be validated against backend COMMUNITY_IMAGE media prefix before display`)
  failed = true
}

if (failed) process.exit(1)
console.log('chat no-demo-fallback check passed')
