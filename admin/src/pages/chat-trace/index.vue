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
              <span>会话 ID：{{ detail.conversation.conversationId }} / 最新序号：{{ detail.conversation.lastSeq }}</span>
            </div>
            <button class="secondary-btn" :disabled="loadingMessages" @click="loadMessages(detail.conversation.conversationId)">刷新消息</button>
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
                <span>{{ senderLabel(message) }} → {{ receiverLabel(message) }}</span>
                <small>{{ message.createdAt || '暂无时间' }}</small>
              </div>
              <p v-if="message.messageType === 'TEXT'" class="message-text">{{ parsedMessage(message).text || '文本内容为空' }}</p>
              <a v-else-if="message.messageType === 'IMAGE' && parsedMessage(message).url" class="evidence-link" :href="parsedMessage(message).url" target="_blank" rel="noopener noreferrer">
                <span>查看聊天图片</span>
                <small>{{ parsedMessage(message).url }}</small>
              </a>
              <div v-else-if="message.messageType === 'VOICE'" class="voice-trace">
                <audio v-if="voiceUrl(message)" controls preload="none" :src="voiceUrl(message)"></audio>
                <span v-else>语音地址无效，已阻止播放。</span>
                <small>{{ voiceSummary(message) }}</small>
              </div>
              <pre v-else>{{ message.contentJson }}</pre>
            </section>
          </div>
        </template>
        <div v-else class="empty">请选择左侧会话，或输入会话 ID 直接查询。</div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

interface ParsedMessage {
  text?: string
  url?: string
  durationMs?: number
  durationSeconds?: number
  sizeBytes?: number
  mimeType?: string
}

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

function parsedMessage(message: AdminChatMessageTrace): ParsedMessage {
  try {
    const value = JSON.parse(message.contentJson)
    return value && typeof value === 'object' ? value as ParsedMessage : {}
  } catch {
    return {}
  }
}

function voiceUrl(message: AdminChatMessageTrace) {
  const url = parsedMessage(message).url || ''
  return url.startsWith('/uploads/chat-voice/') ? url : ''
}

function voiceSummary(message: AdminChatMessageTrace) {
  const parsed = parsedMessage(message)
  const duration = parsed.durationMs ? `${Math.round(parsed.durationMs / 1000)} 秒` : parsed.durationSeconds ? `${parsed.durationSeconds} 秒` : '时长未知'
  const size = parsed.sizeBytes ? `${Math.round(parsed.sizeBytes / 1024)} KB` : '大小未知'
  return `${duration} / ${size} / ${parsed.mimeType || 'audio'}`
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
</script>
