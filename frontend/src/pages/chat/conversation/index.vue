<template>
  <view class="chat-page">
    <view class="chat-header">
      <view class="peer-avatar">{{ peerAvatar }}</view>
      <view class="peer-main">
        <view class="peer-name">{{ peerName }}</view>
        <view class="peer-status">聊天记录以平台会话为准</view>
      </view>
      <view class="report tapable" @click="reportConversation">举报</view>
    </view>

    <view class="goods-tip ds-card">
      <view class="goods-icon">💬</view>
      <view class="goods-main">
        <view class="goods-title">聊天留痕</view>
        <view class="goods-desc">仅同步真实会话消息；缺少会话或接口失败时不会展示聊天内容。如涉及交易，请以平台订单、支付和售后状态为准。</view>
      </view>
    </view>

    <scroll-view class="message-scroll" scroll-y>
      <view class="toolbar">
        <button class="secondary-btn" :disabled="loadingMessages || !conversationId" @click="loadMoreMessages">
          {{ loadingMessages ? '补拉中...' : hasMore ? '继续补拉' : '同步消息' }}
        </button>
        <button class="secondary-btn" :disabled="!conversationId" @click="handleMarkRead">标记已读</button>
      </view>

      <view v-if="messages.length === 0" class="empty-card">暂无消息，先发一句问问宝贝细节吧～</view>
      <view v-for="message in messages" :key="message.serverMsgId" class="bubble-row" :class="{ mine: isMine(message) }">
        <view class="bubble">
          <view class="message-body" :class="{ image: message.msgType === 'IMAGE' }">{{ renderMessage(message) }}</view>
          <view class="message-meta">
            #{{ message.serverSeq }} · {{ formatTime(message.createdAt) }} · {{ receiptText(message) }}
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="composer">
      <view class="tool tapable" @click="sendImagePlaceholder">＋</view>
      <input v-model.trim="draft" class="field" confirm-type="send" placeholder="问尺码、瑕疵、发货时间..." @confirm="handleSendText" />
      <button class="send-btn" :disabled="sending" @click="handleSendText">{{ sending ? '...' : '发送' }}</button>
    </view>
    <view v-if="statusText" class="status-text">{{ statusText }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { markConversationDelivered, markConversationRead, sendMessage, syncMessages, type ChatMessageItem, type SendMessageRequest } from '../../../api/modules/chat'
import { createMediaUploadTicket } from '../../../api/modules/media'
import { getMyProfile } from '../../../api/modules/user'

const launchReadinessMarkers = [
  '聊天用户以服务端会话为准',
  '聊天图片票据需使用有效本地选择文件',
  '聊天记录以服务端会话为准',
  '如涉及交易，请以平台订单、支付和售后状态为准'
]

const currentUserId = ref<number | null>(null)
const draft = ref('')
const sending = ref(false)
const loadingMessages = ref(false)
const statusText = ref('')
const conversationId = ref<number | undefined>()
const receiverId = ref<number | undefined>()
const messages = ref<ChatMessageItem[]>([])
const nextAfterSeq = ref(0)
const hasMore = ref(false)
const peerName = ref('聊天用户以平台会话为准')
const peerAvatar = computed(() => peerName.value.slice(0, 1))

async function loadCurrentUser() {
  try {
    const profile = await getMyProfile()
    currentUserId.value = profile.userId
  } catch {
    currentUserId.value = null
    statusText.value = '当前登录用户加载失败，不能发送消息'
  }
}

onLoad((options) => {
  void loadCurrentUser()
  const routeConversationId = Number(options?.conversationId)
  const routeReceiverId = Number(options?.receiverId ?? options?.peerUserId)
  if (Number.isFinite(routeConversationId) && routeConversationId > 0) {
    conversationId.value = routeConversationId
    void loadMoreMessages()
  } else {
    statusText.value = '缺少有效会话编号，不能展示或发送聊天消息'
  }
  if (Number.isFinite(routeReceiverId) && routeReceiverId > 0) {
    receiverId.value = routeReceiverId
    peerName.value = `用户 ${routeReceiverId}`
  }
})
async function loadMoreMessages() {
  if (loadingMessages.value || !conversationId.value) return
  loadingMessages.value = true
  statusText.value = ''
  try {
    const response = await syncMessages(conversationId.value, nextAfterSeq.value, 50)
    const existingSeqs = new Set(messages.value.map((message) => message.serverSeq))
    messages.value = messages.value.concat(response.messages.filter((message) => !existingSeqs.has(message.serverSeq)))
    nextAfterSeq.value = response.nextAfterSeq || Math.max(...messages.value.map((message) => message.serverSeq), 0)
    hasMore.value = response.hasMore
    await markConversationDelivered(conversationId.value)
    statusText.value = response.hasMore ? '已补拉部分消息，可继续补拉' : '消息已同步'
  } catch {
    statusText.value = '消息服务未连接，不能展示或发送聊天内容'
  } finally {
    loadingMessages.value = false
  }
}
async function handleMarkRead() {
  if (!conversationId.value) { statusText.value = '缺少有效会话编号，不能标记已读'; return }
  try { const response = await markConversationRead(conversationId.value, { readSeq: nextAfterSeq.value }); statusText.value = `已读至 ${response.readSeq}` }
  catch { statusText.value = '已读状态未同步，请稍后重试' }
}
async function handleSendText() { await handleSend('TEXT') }
function guessImageMime(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}
function filenameFromPath(path: string) {
  const clean = path.split('?')[0] || ''
  return clean.split('/').pop() || `chat-${Date.now()}.jpg`
}
function sendImagePlaceholder() {
  if (sending.value) return
  if (!receiverId.value) { statusText.value = '缺少会话目标用户'; return }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const path = res.tempFilePaths[0] || ''
      if (!path) { statusText.value = '没有选择图片'; return }
      void handleSendImage(path)
    },
    fail: () => { statusText.value = '已取消选择图片' }
  })
}
async function handleSendImage(localPath: string) {
  if (!localPath || localPath.startsWith('local://') || localPath.includes('placeholder')) {
    statusText.value = '聊天图片票据需使用有效选择文件'
    return
  }
  sending.value = true
  statusText.value = '正在签发聊天图片票据...'
  try {
    const ticket = await createMediaUploadTicket({ scene: 'CHAT_IMAGE', contentType: guessImageMime(localPath), fileSize: 600_000, filename: filenameFromPath(localPath) })
    await handleSend('IMAGE', {
      url: ticket.storageUrl,
      width: 720,
      height: 720,
      sizeBytes: 600000,
      mimeType: guessImageMime(localPath)
    })
  } catch {
    statusText.value = '图片发送失败，请重新选择图片'
  } finally {
    sending.value = false
  }
}
async function handleSend(type: 'TEXT' | 'IMAGE', imagePayload?: { url: string; width: number; height: number; sizeBytes: number; mimeType: string }) {
  if (sending.value && type === 'TEXT') return
  if (!currentUserId.value) { statusText.value = '缺少当前登录用户，不能发送消息'; return }
  if (!conversationId.value) { statusText.value = '缺少有效会话编号，不能发送消息'; return }
  if (!receiverId.value) { statusText.value = '缺少会话目标用户'; return }
  if (type === 'IMAGE' && (!imagePayload || !imagePayload.url.startsWith('/uploads/'))) { statusText.value = '图片票据无效，不能发送'; return }
  const text = draft.value.trim()
  if (type === 'TEXT' && !text) { statusText.value = '消息不能为空，未发送默认聊天文案'; return }
  const payload: SendMessageRequest = {
    conversationId: conversationId.value,
    clientMsgId: `h5-${Date.now()}`,
    receiverId: receiverId.value,
    msgType: type,
    contentJson: type === 'IMAGE' ? JSON.stringify(imagePayload) : JSON.stringify({ text })
  }
  sending.value = true
  statusText.value = ''
  try {
    const response = await sendMessage(payload)
    conversationId.value = response.ack.conversationId
    pushLocalMessage(response.ack.serverSeq, response.ack.serverMsgId, payload)
    statusText.value = '消息已发送'
  } catch {
    statusText.value = '消息发送失败，请检查网络后重试'
  } finally {
    draft.value = ''
    sending.value = false
  }
}
function pushLocalMessage(serverSeq: number, serverMsgId: string, payload: SendMessageRequest) {
  if (!conversationId.value || !currentUserId.value) return
  nextAfterSeq.value = Math.max(nextAfterSeq.value, serverSeq)
  messages.value.push({ conversationId: conversationId.value, serverSeq, serverMsgId, clientMsgId: payload.clientMsgId, senderId: currentUserId.value, receiverId: payload.receiverId, msgType: payload.msgType, contentJson: payload.contentJson, createdAt: new Date().toISOString(), deliveredToReceiver: true, readByReceiver: false })
}
function isMine(message: ChatMessageItem) { return message.senderId === currentUserId.value }
function renderMessage(message: ChatMessageItem) {
  try { const content = JSON.parse(message.contentJson) as { text?: string; url?: string }; return message.msgType === 'IMAGE' ? `[图片] ${content.url || '图片消息'}` : content.text || message.contentJson }
  catch { return message.contentJson }
}
function receiptText(message: ChatMessageItem) { return isMine(message) ? (message.readByReceiver ? '已读' : message.deliveredToReceiver ? '已送达' : '发送中') : '对方消息' }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(11, 16) : '--' }
function reportConversation() {
  if (!conversationId.value) {
    uni.showToast({ title: '缺少有效会话编号，不能提交举报', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/report/submit/index?targetType=CHAT&targetId=${encodeURIComponent(String(conversationId.value))}` })
}
</script>

<style scoped>
.chat-page { min-height:100vh; padding:24rpx 24rpx calc(130rpx + env(safe-area-inset-bottom)); box-sizing:border-box; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.chat-header { display:flex; align-items:center; gap:16rpx; }
.peer-avatar { width:84rpx; height:84rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:32rpx; font-weight:950; }
.peer-main { flex:1; min-width:0; }
.peer-name { color:#3a2a1f; font-size:32rpx; font-weight:950; }
.peer-status { margin-top:5rpx; color:#9b7560; font-size:21rpx; }
.report { padding:10rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:21rpx; font-weight:900; }
.goods-tip { margin-top:18rpx; padding:18rpx; display:flex; gap:14rpx; border-color:#ffd9bd; }
.goods-icon { width:78rpx; height:78rpx; border-radius:22rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; font-size:36rpx; }
.goods-main { flex:1; min-width:0; }
.goods-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.goods-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; }
.message-scroll { height:calc(100vh - 330rpx); margin-top:18rpx; }
.toolbar { display:flex; gap:14rpx; margin-bottom:16rpx; }
.secondary-btn { flex:1; margin:0; height:58rpx; line-height:58rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:22rpx; font-weight:900; }
.empty-card { margin:28rpx auto; padding:28rpx; border-radius:28rpx; background:#fff; color:#9b7560; text-align:center; font-size:24rpx; }
.bubble-row { display:flex; justify-content:flex-start; margin:14rpx 0; }
.bubble-row.mine { justify-content:flex-end; }
.bubble { max-width:78%; padding:16rpx 18rpx; border-radius:24rpx 24rpx 24rpx 8rpx; background:#fff; border:1rpx solid #ffd9bd; box-shadow:0 10rpx 24rpx rgba(255,122,69,.08); }
.bubble-row.mine .bubble { border-radius:24rpx 24rpx 8rpx 24rpx; background:#ff7a45; border-color:#ff7a45; color:#fff; }
.message-body { color:#3a2a1f; font-size:27rpx; line-height:1.55; word-break:break-word; }
.bubble-row.mine .message-body { color:#fff; }
.message-body.image { padding:24rpx; border-radius:18rpx; background:rgba(255,255,255,.22); }
.message-meta { margin-top:8rpx; color:#b9856a; font-size:18rpx; }
.bubble-row.mine .message-meta { color:rgba(255,255,255,.78); }
.composer { position:fixed; left:0; right:0; bottom:0; padding:16rpx 22rpx calc(16rpx + env(safe-area-inset-bottom)); display:flex; gap:12rpx; background:rgba(255,247,251,.96); border-top:1rpx solid #ffd9bd; box-shadow:0 -10rpx 30rpx rgba(255,122,69,.12); }
.tool { width:68rpx; height:68rpx; border-radius:50%; background:#fff3e7; color:#ff7a45; display:flex; align-items:center; justify-content:center; font-size:36rpx; font-weight:900; }
.field { flex:1; height:68rpx; padding:0 20rpx; border-radius:999rpx; background:#fff; color:#3a2a1f; font-size:25rpx; }
.send-btn { margin:0; width:104rpx; height:68rpx; line-height:68rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:23rpx; font-weight:950; }
.status-text { position:fixed; left:24rpx; right:24rpx; bottom:100rpx; padding:10rpx 16rpx; border-radius:999rpx; background:rgba(63,36,50,.78); color:#fff; font-size:20rpx; text-align:center; }
</style>
