<template>
  <section class="page-shell">
    <div class="page-title">审计日志</div>
    <div class="page-desc">读取后台操作日志；仅展示脱敏摘要，不展示访问密钥或完整收款账号。</div>

    <form class="toolbar" @submit.prevent="loadLogs">
      <label>
        <span>起始日志 ID</span>
        <input v-model.trim="afterId" placeholder="可选，例如 100" />
      </label>
      <label>
        <span>条数</span>
        <input v-model.number="limit" type="number" min="1" max="100" />
      </label>
      <button class="primary-btn" :disabled="loading">{{ loading ? '加载中...' : '查询日志' }}</button>
    </form>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">审计日志加载中...</div>
    <div v-else-if="logs.length === 0" class="empty">暂无审计日志。</div>

    <article v-for="item in logs" :key="item.logId" class="audit-card">
      <div class="audit-main">
        <strong>#{{ item.logId }} {{ item.action }}</strong>
        <span>{{ item.targetType }} / {{ item.targetId }}</span>
        <p>{{ item.summary || '无补充摘要' }}</p>
      </div>
      <div class="audit-side">
        <span class="status-pill">{{ item.result }}</span>
        <small>操作人：{{ item.operatorId }}</small>
        <small>{{ item.createdAt || '暂无时间' }}</small>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminAuditLogs, isValidAdminAuditLogId, type AdminAuditLogEntry } from '../../api'

const logs = ref<AdminAuditLogEntry[]>([])
const loading = ref(false)
const error = ref('')
const afterId = ref('')
const limit = ref(50)

async function loadLogs() {
  error.value = ''
  logs.value = []
  const cursor = afterId.value.trim()
  if (cursor && !isValidAdminAuditLogId(cursor)) {
    error.value = '审计日志游标无效，请输入正确的日志 ID。'
    return
  }
  loading.value = true
  try {
    logs.value = await getAdminAuditLogs({ afterId: cursor || undefined, limit: limit.value })
  } catch {
    logs.value = []
    error.value = '审计日志加载失败，请确认管理员权限与服务状态。'
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>
