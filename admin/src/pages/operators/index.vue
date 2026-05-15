<template>
  <section class="page-shell">
    <div class="page-title">运营经理授权</div>
    <div class="page-desc">仅最高授权管理员可访问；读取与更新运营权限，所有变更写入审计日志。</div>

    <form class="toolbar" @submit.prevent="loadOperator">
      <input v-model="operatorId" placeholder="运营经理用户 ID" />
      <button type="submit" :disabled="loading">加载权限</button>
    </form>

    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="success" class="success">{{ success }}</div>

    <div v-if="operator" class="operator-card">
      <div class="operator-meta">
        <strong>{{ operator.nickname }}</strong>
        <span>{{ operator.userNo }} · ID {{ operator.userId }} · {{ operator.status }}</span>
      </div>
      <p class="clear-all-note">如需清空全部可分配权限，请取消勾选后保存，系统会提交空权限数组并写入审计日志。</p>
      <div v-if="selectedPermissions.length === 0" class="clear-all-confirm">
        <label>清空授权二次确认</label>
        <input v-model.trim="clearAllConfirmText" placeholder="请输入：清空授权" />
        <small>二次确认文本正确后才会提交空权限数组，避免误清空运营经理权限。</small>
      </div>
      <div class="permission-grid">
        <label v-for="item in permissionOptions" :key="item.code" class="permission-item">
          <input v-model="selectedPermissions" type="checkbox" :value="item.code" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.code }}</small>
          </span>
        </label>
      </div>
      <button class="primary" :disabled="saving" @click="savePermissions">
        {{ saving ? '保存中...' : selectedPermissions.length === 0 ? '清空授权' : '保存授权' }}
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  getAdminOperatorPermissions,
  isValidAdminUserId,
  updateAdminOperatorPermissions,
  type AdminOperatorPermissionCode,
  type AdminOperatorPermissionResponse
} from '../../api'

const route = useRoute()
const operatorId = ref(String(route.params.userId || ''))
const operator = ref<AdminOperatorPermissionResponse | null>(null)
const selectedPermissions = ref<AdminOperatorPermissionCode[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const clearAllConfirmText = ref('')

const permissionOptions: Array<{ code: AdminOperatorPermissionCode; label: string }> = [
  { code: 'audit:read', label: '审核查看' },
  { code: 'audit:review', label: '审核处理' },
  { code: 'finance:read', label: '财务查看' },
  { code: 'finance:review', label: '提现复核' },
  { code: 'user:read', label: '用户查看' },
  { code: 'user:risk-control', label: '用户风控' },
  { code: 'order:read', label: '订单查看' },
  { code: 'after-sales:read', label: '售后查看' },
  { code: 'after-sales:review', label: '售后处理' },
  { code: 'system:config', label: '系统配置' },
  { code: 'audit:log', label: '审计日志' },
  { code: 'operator:grant', label: '运营授权' }
]

async function loadOperator() {
  error.value = ''
  success.value = ''
  operator.value = null
  const id = operatorId.value.trim()
  if (!isValidAdminUserId(id)) {
    error.value = '请输入有效的运营经理用户 ID。'
    return
  }
  loading.value = true
  try {
    const data = await getAdminOperatorPermissions(id)
    operator.value = data
    selectedPermissions.value = [...data.permissions]
    clearAllConfirmText.value = ''
  } catch {
    error.value = '运营经理权限加载失败，请确认用户存在、账号启用且当前会话具备 operator:grant。'
  } finally {
    loading.value = false
  }
}

async function savePermissions() {
  if (!operator.value) return
  error.value = ''
  success.value = ''
  if (selectedPermissions.value.length === 0 && clearAllConfirmText.value !== '清空授权') {
    error.value = '清空授权需要二次确认：请输入“清空授权”。'
    return
  }
  saving.value = true
  try {
    const data = await updateAdminOperatorPermissions(operator.value.userId, { permissions: selectedPermissions.value })
    operator.value = data
    selectedPermissions.value = [...data.permissions]
    clearAllConfirmText.value = ''
    success.value = '运营经理授权已保存，系统已记录管理员操作审计。'
  } catch {
    error.value = '运营经理授权保存失败，请检查权限码与服务状态。'
  } finally {
    saving.value = false
  }
}

if (operatorId.value) {
  loadOperator()
}
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; margin: 16px 0; }
.toolbar input { flex: 1; padding: 10px 12px; border: 1px solid #d9e2ef; border-radius: 10px; }
.operator-card { display: grid; gap: 16px; padding: 18px; border: 1px solid #d9e2ef; border-radius: 16px; background: #fff; }
.operator-meta { display: flex; flex-direction: column; gap: 4px; }
.clear-all-note { margin: 0; padding: 10px 12px; border-radius: 10px; background: #fff7ed; color: #9a3412; font-size: 13px; }
.clear-all-confirm { display: grid; gap: 6px; padding: 12px; border-radius: 12px; border: 1px solid #fed7aa; background: #fffbeb; }
.clear-all-confirm label { font-weight: 700; color: #9a3412; }
.clear-all-confirm input { max-width: 260px; padding: 9px 10px; border: 1px solid #fdba74; border-radius: 8px; }
.clear-all-confirm small { color: #9a3412; }
.permission-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; }
.permission-item { display: flex; gap: 10px; align-items: flex-start; padding: 12px; border: 1px solid #e6edf5; border-radius: 12px; background: #f8fbff; }
.permission-item span { display: flex; flex-direction: column; gap: 4px; }
.permission-item small { color: #64748b; }
.primary { width: fit-content; padding: 10px 16px; border: 0; border-radius: 10px; background: #0f172a; color: white; }
.error { color: #b42318; margin: 12px 0; }
.success { color: #047857; margin: 12px 0; }
</style>
