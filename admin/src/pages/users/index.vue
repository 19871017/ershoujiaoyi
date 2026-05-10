<template>
  <section class="page-shell user-page">
    <div class="page-title">用户管理</div>
    <div class="page-desc">按后端用户 ID 读取真实用户资料；手机号仅展示脱敏值，无有效编号或接口失败时不展示本地样例。</div>

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
    <div v-else-if="!detail" class="empty">请输入后端用户 ID 查询详情；不会展示预览或本地构造资料。</div>

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
        <div><dt>视频核验</dt><dd>{{ detail.videoIdentityStatus }} / {{ detail.videoVerified ? '已公开展示' : '未公开展示' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
      </dl>
      <p class="safe-note">{{ detail.bio || '暂无补充简介' }}</p>
      <p class="safe-note">用户资料以服务端记录为准；本页仅展示脱敏联系方式与后端返回资料，不使用本地构造资料。</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getAdminUserDetail, isValidAdminUserId, searchAdminUsers, type AdminUserDetailResponse } from '../../api/modules/admin'

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
    error.value = '用户编号无效：已阻止预览、占位或非后端用户 ID 查询。'
    loading.value = false
    return
  }
  try {
    detail.value = await getAdminUserDetail(safeId)
  } catch {
    error.value = '用户详情加载失败：未展示本地构造资料，请确认管理员权限、后端服务与 /api/admin/users/{userId} 可用。'
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
      error.value = '未检索到后端用户记录；不会展示本地构造用户。'
    }
  } catch {
    error.value = '用户检索失败：已阻止预览/占位查询，且不会展示本地构造资料。'
  } finally {
    searching.value = false
  }
}

function selectUser(item: AdminUserDetailResponse) {
  userId.value = String(item.userId)
  detail.value = item
}
</script>
