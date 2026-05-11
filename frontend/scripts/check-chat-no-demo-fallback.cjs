const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages/chat/conversation/index.vue',
  'src/pages/chat/session-list/index.vue'
]

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
  '(receiverId.value ?? 21)'
]

let failed = false
for (const file of files) {
  const content = fs.readFileSync(path.join(root, file), 'utf8')
  for (const marker of forbiddenMarkers) {
    if (content.includes(marker)) {
      console.error(`${file}: forbidden IM demo/fake fallback marker found: ${marker}`)
      failed = true
    }
  }
}

const conversationFile = 'src/pages/chat/conversation/index.vue'
const conversation = fs.readFileSync(path.join(root, conversationFile), 'utf8')
const requiredConversationMarkers = [
  'getMyProfile',
  'currentUserId.value',
  'peerName.value =',
  '聊天用户以服务端会话为准',
  '缺少当前登录用户，不能发送消息',
  'conversationId.value = response.ack.conversationId',
  '消息不能为空，未发送默认聊天文案',
  '聊天图片票据需使用有效本地选择文件'
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
  '聊天记录以服务端会话为准',
  '聊天留痕',
  '如涉及交易，请以平台订单、支付和售后状态为准'
]
for (const marker of requiredNeutralConversationCopy) {
  if (!conversation.includes(marker)) {
    console.error(`${conversationFile}: missing neutral IM trust copy marker: ${marker}`)
    failed = true
  }
}

if (failed) process.exit(1)
console.log('chat no-demo-fallback check passed')
