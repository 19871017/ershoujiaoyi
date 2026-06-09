<template>
  <section class="page-shell user-page">
    <div class="page-title">用户管理</div>
    <div class="page-desc">按用户 ID 读取用户资料；手机号仅展示脱敏值。</div>

    <form class="lookup-card" @submit.prevent="loadDetail">
      <label>
        <span>用户 ID</span>
        <input v-model.trim="userId" placeholder="例如 8331" />
      </label>
      <button class="primary-btn" :disabled="loading || !userId">{{ loading ? '查询中...' : '查询用户' }}</button>
    </form>

    <form class="lookup-card" @submit.prevent="loadUsers">
      <label>
        <span>用户检索</span>
        <input v-model.trim="keyword" placeholder="昵称、用户编号或手机号后四位" />
      </label>
      <button class="primary-btn" :disabled="searching || !keyword">{{ searching ? '检索中...' : '检索用户' }}</button>
    </form>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="searching" class="empty">用户检索中...</div>
    <div v-else-if="users.length > 0" class="result-list">
      <button v-for="item in users" :key="item.userId" class="result-row" @click="selectUser(item)">
        <strong>{{ item.nickname }}</strong>
        <span>{{ item.userNo || `用户 ${item.userId}` }} / {{ item.maskedPhone || '暂无脱敏手机号' }}</span>
        <small>{{ item.mainRole || '暂无角色' }} · {{ item.city || '暂无城市' }} · {{ item.status }}</small>
      </button>
    </div>
    <div v-if="loading" class="empty">用户详情加载中...</div>
    <div v-else-if="!detail" class="empty">请输入用户 ID 查询详情。</div>

    <article v-else class="detail-card">
      <div class="detail-head">
        <div>
          <strong>{{ detail.nickname }}</strong>
          <span>{{ detail.userNo || `用户 ${detail.userId}` }}</span>
        </div>
        <b :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</b>
      </div>
      <dl class="detail-grid">
        <div><dt>用户 ID</dt><dd>{{ detail.userId }}</dd></div>
        <div><dt>脱敏手机号</dt><dd>{{ detail.maskedPhone || '暂无' }}</dd></div>
        <div><dt>主要角色</dt><dd>{{ detail.mainRole || '暂无' }}</dd></div>
        <div><dt>城市</dt><dd>{{ detail.city || '暂无' }}</dd></div>
        <div><dt>实名认证</dt><dd>{{ detail.identityStatus || 'UNVERIFIED' }}</dd></div>
        <div><dt>视频核验</dt><dd>{{ detail.videoIdentityStatus }} / {{ detail.videoVerified ? '已公开展示' : '未公开展示' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
      </dl>
      <div class="toolbar">
        <button class="secondary-btn" @click="openUserOrderTrace">追溯该用户订单</button>
        <button class="secondary-btn" @click="openUserAfterSalesTrace">追溯该用户售后</button>
        <button class="secondary-btn" @click="openUserChatTrace">追溯该用户私聊</button>
        <button class="secondary-btn" @click="openUserWithdrawalTrace">追溯该用户提现</button>
      </div>
      <section v-if="detail.opsSummary" class="audit-card">
        <h3>运营复盘</h3>
        <div class="ops-grid">
          <div>
            <span>订单</span>
            <strong>{{ detail.opsSummary.orderCount }}</strong>
            <small>已支付链路 {{ detail.opsSummary.paidOrderCount }}</small>
          </div>
          <div>
            <span>售后</span>
            <strong>{{ detail.opsSummary.afterSalesCount }}</strong>
            <small>待处理 {{ detail.opsSummary.pendingAfterSalesCount }}</small>
          </div>
          <div>
            <span>举报</span>
            <strong>{{ detail.opsSummary.reportCount }}</strong>
            <small>待处理 {{ detail.opsSummary.pendingReportCount }}</small>
          </div>
          <div>
            <span>提现</span>
            <strong>{{ detail.opsSummary.withdrawalCount }}</strong>
            <small>待审核 {{ detail.opsSummary.pendingWithdrawalCount }}</small>
          </div>
          <div>
            <span>私聊</span>
            <strong>{{ detail.opsSummary.chatConversationCount }}</strong>
            <small>会话数</small>
          </div>
        </div>
        <dl class="detail-grid">
          <div><dt>最近订单</dt><dd>{{ detail.opsSummary.lastOrderNo || '暂无' }}</dd></div>
          <div><dt>最近售后</dt><dd>{{ detail.opsSummary.lastAfterSalesNo || '暂无' }}</dd></div>
          <div><dt>最近提现</dt><dd>{{ detail.opsSummary.lastWithdrawalNo || '暂无' }}</dd></div>
          <div><dt>最近私聊会话</dt><dd>{{ detail.opsSummary.lastChatConversationId || '暂无' }}</dd></div>
        </dl>
      </section>
      <p class="safe-note">{{ detail.bio || '暂无补充简介' }}</p>
      <p class="safe-note">用户资料以平台记录为准；本页仅展示脱敏联系方式与平台返回资料。</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminUserDetail, isValidAdminUserId, isValidAdminWithdrawalNo, searchAdminUsers, type AdminUserDetailResponse } from '../../api/modules/admin'
import { afterSalesTraceListLocation } from '../after-sales/after-sales-trace-links'
import { chatTraceDetailLocation, chatTraceListLocation } from '../chat-trace/chat-trace-links'
import { orderTraceListLocation } from '../orders/order-trace-links'

const route = useRoute()
const router = useRouter()
const userId = ref('')
const keyword = ref('')
const users = ref<AdminUserDetailResponse[]>([])
const detail = ref<AdminUserDetailResponse | null>(null)
const loading = ref(false)
const searching = ref(false)
const error = ref('')

async function loadDetail() {
  const safeId = userId.value.trim()
  detail.value = null
  error.value = ''
  loading.value = true
  if (!isValidAdminUserId(safeId)) {
    error.value = '用户编号无效，请输入正确的用户 ID。'
    loading.value = false
    return
  }
  try {
    detail.value = await getAdminUserDetail(safeId)
  } catch {
    error.value = '用户详情加载失败，请确认管理员权限与用户编号。'
  } finally {
    loading.value = false
  }
}
async function loadUsers() {
  const safeKeyword = keyword.value.trim()
  users.value = []
  detail.value = null
  error.value = ''
  searching.value = true
  try {
    users.value = await searchAdminUsers({ keyword: safeKeyword, limit: 20 })
    if (users.value.length === 0) {
      error.value = '未检索到符合条件的用户记录。'
    }
  } catch {
    error.value = '用户检索失败，请确认查询条件、管理员权限与服务状态。'
  } finally {
    searching.value = false
  }
}

function selectUser(item: AdminUserDetailResponse) {
  userId.value = String(item.userId)
  detail.value = item
  loadDetail()
}

function openUserAfterSalesTrace() {
  const location = afterSalesTraceListLocation(detail.value?.userId || userId.value)
  if (!location) {
    error.value = '用户编号无效，未打开售后追溯。'
    return
  }
  router.push(location)
}

function openUserOrderTrace() {
  const location = orderTraceListLocation(detail.value?.userId || userId.value)
  if (!location) {
    error.value = '用户编号无效，未打开订单追溯。'
    return
  }
  router.push(location)
}

function openUserChatTrace() {
  const recentConversation = detail.value?.opsSummary?.lastChatConversationId
  const location = recentConversation
    ? chatTraceDetailLocation(recentConversation)
    : chatTraceListLocation(detail.value?.userId || userId.value)
  if (!location) {
    error.value = '用户编号无效，未打开私聊追溯。'
    return
  }
  router.push(location)
}

function openUserWithdrawalTrace() {
  const withdrawalNo = detail.value?.opsSummary?.lastWithdrawalNo || ''
  if (withdrawalNo && isValidAdminWithdrawalNo(withdrawalNo)) {
    router.push({ path: `/finance/withdrawals/${encodeURIComponent(withdrawalNo)}` })
    return
  }
  router.push({ path: '/finance/withdrawals' })
}

onMounted(() => {
  const routeUserId = String(route.params.userId || '').trim()
  if (routeUserId) {
    userId.value = routeUserId
    loadDetail()
  }
})
</script>

<style scoped>
.ops-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin: 12px 0;
}

.ops-grid div {
  border: 1px solid #f0e4d6;
  border-radius: 8px;
  padding: 12px;
  background: #fffaf5;
}

.ops-grid span,
.ops-grid small {
  display: block;
  color: #7c6a5b;
  font-size: 12px;
}

.ops-grid strong {
  display: block;
  margin: 6px 0;
  color: #4c2f1a;
  font-size: 24px;
}
</style>
