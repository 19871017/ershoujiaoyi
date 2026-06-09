<template>
  <section class="page-shell chat-trace-page">
    <div class="page-title">私聊追溯</div>
    <div class="page-desc">按会话编号、用户 ID 或聊天编号追溯私聊记录；文字、图片和语音均以平台持久化记录为准。</div>

    <form class="lookup-card chat-trace-filter" @submit.prevent="loadConversations">
      <label>
        <span>会话 ID</span>
        <input v-model.trim="conversationId" :disabled="loadingList || loadingMessages" placeholder="例如 12" />
      </label>
      <label>
        <span>用户 ID</span>
        <input v-model.trim="userId" :disabled="loadingList || loadingMessages" placeholder="任一聊天用户 ID" />
      </label>
      <label>
        <span>关键词</span>
        <input v-model.trim="keyword" :disabled="loadingList || loadingMessages" maxlength="64" placeholder="聊天编号/昵称/摘要" />
      </label>
      <label>
        <span>条数</span>
        <input v-model.number="limit" type="number" min="1" max="100" step="1" :disabled="loadingList || loadingMessages" />
      </label>
      <button class="primary-btn" :disabled="loadingList || loadingMessages">{{ loadingList ? '查询中...' : '查询会话' }}</button>
    </form>

    <div v-if="error" class="alert">{{ error }}</div>

    <div class="chat-trace-grid">
      <aside class="chat-trace-list">
        <div class="section-head">
          <strong>会话列表</strong>
          <span>{{ conversations.length }} 条</span>
        </div>
        <div v-if="loadingList" class="empty">私聊会话加载中...</div>
        <div v-else-if="conversations.length === 0" class="empty">暂无可追溯会话。</div>
        <button
          v-for="item in conversations"
          :key="item.conversationId"
          :class="['chat-session-row', { active: selectedConversationId === item.conversationId }]"
          type="button"
          @click="selectConversation(item.conversationId)"
        >
          <div class="session-title">
            <strong>#{{ item.conversationId }}</strong>
            <span>{{ item.conversationNo }}</span>
          </div>
          <div class="session-participants">
            <span>{{ participantName(item.owner) }}</span>
            <b>↔</b>
            <span>{{ participantName(item.peer) }}</span>
          </div>
          <small>{{ item.lastMessageSummary || '暂无消息摘要' }}</small>
        </button>
      </aside>

      <article class="chat-trace-detail">
        <div v-if="loadingMessages" class="empty">聊天记录加载中...</div>
        <template v-else-if="detail">
          <header class="chat-detail-head">
            <div>
              <strong>{{ detail.conversation.conversationNo }}</strong>
              <span>会话 ID：{{ detail.conversation.conversationId }} / 最新序号：{{ detail.conversation.lastSeq }} / 当前 {{ detail.messages.length }} 条</span>
            </div>
            <div class="chat-detail-actions">
              <button v-if="detail.hasMore && detail.nextBeforeSeq" class="secondary-btn" :disabled="loadingMessages" @click="loadOlderMessages">
                加载更早消息
              </button>
              <button class="secondary-btn" :disabled="loadingMessages" @click="loadMessages(detail.conversation.conversationId)">刷新消息</button>
            </div>
          </header>

          <div class="participant-grid">
            <div class="participant-card">
              <span>用户 A</span>
              <strong>{{ participantName(detail.conversation.owner) }}</strong>
              <small>{{ participantMeta(detail.conversation.owner) }}</small>
            </div>
            <div class="participant-card">
              <span>用户 B</span>
              <strong>{{ participantName(detail.conversation.peer) }}</strong>
              <small>{{ participantMeta(detail.conversation.peer) }}</small>
            </div>
          </div>

          <div v-if="detail.messages.length === 0" class="empty">当前会话暂无消息。</div>
          <div v-else class="message-trace-list">
            <section v-for="message in detail.messages" :key="message.messageId" class="message-trace-item">
              <div class="message-meta">
                <strong>#{{ message.serverSeq }} {{ message.messageType }}</strong>
                <span v-if="isRevokedMessage(message)" class="trace-badge revoked">已撤回</span>
                <span>{{ senderLabel(message) }} → {{ receiverLabel(message) }}</span>
                <small>{{ traceTimeText(message) }}</small>
              </div>
              <div v-if="isRevokedMessage(message)" class="trace-revoked-note">
                用户端已显示为撤回消息，后台保留原始内容用于安全追溯。
              </div>
              <p v-if="message.messageType === 'TEXT'" class="message-text">{{ parsedMessage(message).text || '文本内容为空' }}</p>
              <a v-else-if="message.messageType === 'IMAGE' && mediaBlobUrl(message)" class="evidence-link" :href="mediaBlobUrl(message)" target="_blank" rel="noopener noreferrer">
                <span>查看聊天图片</span>
                <small>{{ mediaSourceLabel(message) }}</small>
              </a>
              <button v-else-if="message.messageType === 'IMAGE' && mediaSourceUrl(message)" class="secondary-btn media-load-btn" type="button" @click="loadMediaBlob(message)">
                授权加载聊天图片
              </button>
              <div v-else-if="message.messageType === 'IMAGE'" class="voice-trace">
                <span>图片地址无效，已阻止打开。</span>
              </div>
              <div v-else-if="message.messageType === 'VOICE'" class="voice-trace">
                <audio v-if="mediaBlobUrl(message)" controls preload="none" :src="mediaBlobUrl(message)"></audio>
                <button v-else-if="mediaSourceUrl(message)" class="secondary-btn media-load-btn" type="button" @click="loadMediaBlob(message)">
                  授权加载语音
                </button>
                <span v-else>语音地址无效，已阻止播放。</span>
                <small>{{ voiceSummary(message) }}</small>
                <small v-if="mediaError(message)" class="media-error">{{ mediaError(message) }}</small>
              </div>
              <pre v-else>{{ safeMessageContent(message) }}</pre>
            </section>
          </div>
        </template>
        <div v-else class="empty">请选择左侧会话，或输入会话 ID 直接查询。</div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAdminChatConversationMessages,
  getAdminChatConversations,
  isValidAdminChatTraceId,
  isValidAdminChatTraceKeyword,
  type AdminChatConversationMessageTrace,
  type AdminChatConversationTrace,
  type AdminChatMessageTrace,
  type AdminChatParticipantTrace
} from '../../api'
import { requestBlob } from '../../api/http'
import { maskSensitiveMediaText } from '../audit/sensitive-media-text'
import { chatTraceImageUrl, chatTraceVoiceSummary, chatTraceVoiceUrl, parseChatTraceMessage } from './chat-trace-media'

const route = useRoute()
const router = useRouter()
const conversationId = ref('')
const userId = ref('')
const keyword = ref('')
const limit = ref(20)
const conversations = ref<AdminChatConversationTrace[]>([])
const detail = ref<AdminChatConversationMessageTrace | null>(null)
const selectedConversationId = ref<number | null>(null)
const loadingList = ref(false)
const loadingMessages = ref(false)
const error = ref('')
const mediaBlobUrls = ref<Record<number, string>>({})
const mediaErrors = ref<Record<number, string>>({})

async function loadConversations() {
  error.value = ''
  conversations.value = []
  const safeConversationId = conversationId.value.trim()
  const safeUserId = userId.value.trim()
  const safeKeyword = keyword.value.trim()
  if (safeConversationId && !isValidAdminChatTraceId(safeConversationId)) {
    error.value = '会话 ID 无效，请输入正整数。'
    return
  }
  if (safeUserId && !isValidAdminChatTraceId(safeUserId)) {
    error.value = '用户 ID 无效，请输入正整数。'
    return
  }
  if (safeKeyword && !isValidAdminChatTraceKeyword(safeKeyword)) {
    error.value = '关键词无效：最多 64 字，不能包含测试占位语义。'
    return
  }
  loadingList.value = true
  try {
    conversations.value = await getAdminChatConversations({
      conversationId: safeConversationId || undefined,
      userId: safeUserId || undefined,
      keyword: safeKeyword || undefined,
      limit: Number(limit.value)
    })
    syncQuery()
    if (conversations.value.length === 1) {
      await selectConversation(conversations.value[0].conversationId)
    }
  } catch (err) {
    error.value = err instanceof Error && err.message ? err.message : '私聊会话加载失败，请确认管理员权限与服务状态。'
  } finally {
    loadingList.value = false
  }
}

async function selectConversation(id: number) {
  selectedConversationId.value = id
  conversationId.value = String(id)
  await loadMessages(id)
}

async function loadMessages(id: number) {
  error.value = ''
  detail.value = null
  revokeMediaBlobUrls()
  loadingMessages.value = true
  try {
    detail.value = await getAdminChatConversationMessages(id, { limit: 100 })
    selectedConversationId.value = detail.value.conversation.conversationId
    router.replace({ path: `/chat-trace/${detail.value.conversation.conversationId}` }).catch(() => {})
  } catch (err) {
    error.value = err instanceof Error && err.message ? err.message : '聊天记录加载失败，请确认会话编号与管理员权限。'
  } finally {
    loadingMessages.value = false
  }
}

async function loadOlderMessages() {
  if (!detail.value?.nextBeforeSeq || loadingMessages.value) return
  const currentConversationId = detail.value.conversation.conversationId
  const beforeSeq = detail.value.nextBeforeSeq
  error.value = ''
  loadingMessages.value = true
  try {
    const older = await getAdminChatConversationMessages(currentConversationId, { limit: 100, beforeSeq })
    detail.value = {
      ...older,
      messages: [...older.messages, ...(detail.value?.messages ?? [])]
    }
    selectedConversationId.value = currentConversationId
  } catch (err) {
    error.value = err instanceof Error && err.message ? err.message : '更早聊天记录加载失败，请稍后重试。'
  } finally {
    loadingMessages.value = false
  }
}

function parsedMessage(message: AdminChatMessageTrace) {
  return parseChatTraceMessage(message)
}

function mediaSourceUrl(message: AdminChatMessageTrace) {
  if (message.messageType === 'VOICE') return chatTraceVoiceUrl(message)
  if (message.messageType === 'IMAGE') return chatTraceImageUrl(message)
  return ''
}

function voiceSummary(message: AdminChatMessageTrace) {
  return chatTraceVoiceSummary(message)
}

function mediaBlobUrl(message: AdminChatMessageTrace) {
  return mediaBlobUrls.value[message.messageId] || ''
}

function mediaError(message: AdminChatMessageTrace) {
  return mediaErrors.value[message.messageId] || ''
}

function mediaSourceLabel(message: AdminChatMessageTrace) {
  return mediaSourceUrl(message) || '已通过后台权限加载'
}

function safeMessageContent(message: AdminChatMessageTrace) {
  return maskSensitiveMediaText(message.contentJson) || '消息内容暂不可展示'
}

function isRevokedMessage(message: AdminChatMessageTrace) {
  return message.revoked === true
}

function traceTimeText(message: AdminChatMessageTrace) {
  const createdAt = message.createdAt || '暂无时间'
  return isRevokedMessage(message) && message.revokedAt ? `${createdAt} / 撤回：${message.revokedAt}` : createdAt
}

async function loadMediaBlob(message: AdminChatMessageTrace) {
  const url = mediaSourceUrl(message)
  if (!url) return
  mediaErrors.value = { ...mediaErrors.value, [message.messageId]: '' }
  try {
    const blob = await requestBlob({ url })
    const previous = mediaBlobUrls.value[message.messageId]
    if (previous) URL.revokeObjectURL(previous)
    mediaBlobUrls.value = {
      ...mediaBlobUrls.value,
      [message.messageId]: URL.createObjectURL(blob)
    }
  } catch (err) {
    console.warn('admin chat trace media load failed', err)
    mediaErrors.value = {
      ...mediaErrors.value,
      [message.messageId]: '媒体加载失败，请确认管理员会话仍有效。'
    }
  }
}

function revokeMediaBlobUrls() {
  Object.values(mediaBlobUrls.value).forEach((url) => URL.revokeObjectURL(url))
  mediaBlobUrls.value = {}
  mediaErrors.value = {}
}

function participantName(participant: AdminChatParticipantTrace) {
  return participant.nickname || participant.userNo || `用户 ${participant.userId}`
}

function participantMeta(participant: AdminChatParticipantTrace) {
  return [participant.userNo, participant.gender, participant.city, participant.mainRole, participant.videoVerified ? '视频认证' : '未视频认证']
    .filter(Boolean)
    .join(' · ')
}

function senderLabel(message: AdminChatMessageTrace) {
  return participantName(participantById(message.senderId))
}

function receiverLabel(message: AdminChatMessageTrace) {
  return participantName(participantById(message.receiverId))
}

function participantById(id: number): AdminChatParticipantTrace {
  const current = detail.value?.conversation
  if (current?.owner.userId === id) return current.owner
  if (current?.peer.userId === id) return current.peer
  return { userId: id, nickname: `用户 ${id}` }
}

function syncQuery() {
  router.replace({
    path: '/chat-trace',
    query: {
      conversationId: conversationId.value.trim() || undefined,
      userId: userId.value.trim() || undefined,
      keyword: keyword.value.trim() || undefined,
      limit: String(limit.value)
    }
  }).catch(() => {})
}

onMounted(async () => {
  const routeConversationId = String(route.params.conversationId || route.query.conversationId || '').trim()
  const routeUserId = String(route.query.userId || '').trim()
  const routeKeyword = String(route.query.keyword || '').trim()
  const routeLimit = Number(route.query.limit)
  if (routeConversationId && isValidAdminChatTraceId(routeConversationId)) conversationId.value = routeConversationId
  if (routeUserId && isValidAdminChatTraceId(routeUserId)) userId.value = routeUserId
  if (routeKeyword && isValidAdminChatTraceKeyword(routeKeyword)) keyword.value = routeKeyword
  if (Number.isInteger(routeLimit) && routeLimit >= 1 && routeLimit <= 100) limit.value = routeLimit
  if (conversationId.value) {
    await loadConversations()
    if (!detail.value && isValidAdminChatTraceId(conversationId.value)) {
      await loadMessages(Number(conversationId.value))
    }
    return
  }
  await loadConversations()
})

onBeforeUnmount(() => {
  revokeMediaBlobUrls()
})
</script>
