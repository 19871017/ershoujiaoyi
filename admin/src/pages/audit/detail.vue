<template>
  <section class="page-shell">
    <div class="page-title">审核详情</div>
    <div class="page-desc">从后端 /api/admin/audit/{auditNo} 读取持久化审核记录；无有效编号或接口失败时不展示本地样例。</div>

    <div class="toolbar">
      <RouterLink class="secondary-btn" to="/audit">返回审核工作台</RouterLink>
      <button class="primary-btn" :disabled="loading || !safeAuditNo" @click="load">刷新详情</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">审核详情加载中...</div>
    <div v-else-if="!detail" class="empty">请输入有效后端审核编号；不会展示预览或本地审核样例。</div>

    <article v-else class="detail-card">
      <header class="detail-head">
        <div>
          <strong>{{ detail.auditNo }}</strong>
          <span>{{ detail.auditType }} / {{ detail.targetType || '未标注目标' }}</span>
        </div>
        <b :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</b>
      </header>
      <dl class="detail-grid">
        <div><dt>目标编号</dt><dd>{{ detail.targetId || '暂无' }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ detail.updatedAt || '暂无' }}</dd></div>
        <div><dt>审核备注</dt><dd>{{ detail.reviewerRemark || '暂无' }}</dd></div>
      </dl>
      <p class="detail-desc">{{ detail.reason || detail.description || '无补充说明' }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { getAdminAuditDetail, isValidAdminAuditNo, type AuditRecordResponse } from '../../api'

const route = useRoute()
const detail = ref<AuditRecordResponse | null>(null)
const loading = ref(false)
const error = ref('')

const safeAuditNo = computed(() => {
  const value = String(route.params.auditNo || '')
  return isValidAdminAuditNo(value) ? value : ''
})

async function load() {
  detail.value = null
  error.value = ''
  if (!safeAuditNo.value) {
    error.value = '审核编号无效：已阻止预览、占位或非后端编号加载。'
    return
  }
  loading.value = true
  try {
    detail.value = await getAdminAuditDetail(safeAuditNo.value)
  } catch {
    detail.value = null
    error.value = '审核详情加载失败：请确认后端详情接口、管理员权限与审核编号。'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
