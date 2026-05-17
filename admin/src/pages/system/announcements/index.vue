<template>
  <section class="page-shell announcement-page">
    <div class="page-title">公告配置</div>
    <div class="page-desc">配置用户端顶部跑马灯公告；公告内容以后台保存结果为准。</div>

    <div class="config-panel">
      <div class="panel-head">
        <div>
          <strong>当前跑马灯公告</strong>
          <span>关闭时用户端不展示后台公告</span>
        </div>
        <button class="ghost-btn" :disabled="loading" @click="loadTicker">{{ loading ? '刷新中...' : '刷新公告' }}</button>
      </div>

      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading && !ticker" class="empty">公告配置加载中...</div>

      <dl v-if="ticker" class="detail-grid">
        <div><dt>启用状态</dt><dd>{{ ticker.enabled ? '已启用' : '未启用' }}</dd></div>
        <div><dt>图标</dt><dd>{{ ticker.icon }}</dd></div>
        <div><dt>跳转路径</dt><dd>{{ ticker.targetUrl }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ ticker.updatedAt || '暂无' }}</dd></div>
      </dl>
      <div v-if="ticker" class="preview-card">
        <span>{{ ticker.icon }}</span>
        <strong>{{ ticker.text }}</strong>
      </div>
      <div v-else-if="!loading" class="empty">暂未读取到公告配置。</div>
    </div>

    <form class="config-panel" @submit.prevent="saveTicker">
      <div class="panel-head">
        <div>
          <strong>更新跑马灯公告</strong>
          <span>保存后用户端下次刷新公告时生效</span>
        </div>
      </div>
      <div class="form-grid">
        <label>
          <span>公告文案</span>
          <input v-model.trim="form.text" maxlength="80" placeholder="例如 今晚 22:00 女神榜刷新" />
        </label>
        <label>
          <span>图标</span>
          <input v-model.trim="form.icon" maxlength="8" placeholder="例如 📣" />
        </label>
        <label>
          <span>跳转路径</span>
          <input v-model.trim="form.targetUrl" placeholder="例如 /pages/notification/index" />
        </label>
        <label class="toggle-row">
          <input v-model="form.enabled" type="checkbox" />
          <span>启用公告</span>
        </label>
      </div>
      <p class="safe-note">路径只能填写用户端 /pages/ 开头的站内页面；不要填写外部链接或示例占位文案。</p>
      <label class="confirm-row">
        <span>二次确认</span>
        <input v-model.trim="confirmText" autocomplete="off" placeholder="输入 保存公告配置 后才能提交" />
      </label>
      <button class="primary-btn" :disabled="saving">{{ saving ? '保存中...' : '保存公告配置' }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getAdminAnnouncementTicker, updateAdminAnnouncementTicker, type AdminAnnouncementTicker } from '../../../api'

const CONFIRM_TEXT = '保存公告配置'
const DEFAULT_ICON = '📣'
const DEFAULT_TARGET_URL = '/pages/notification/index'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const ticker = ref<AdminAnnouncementTicker | null>(null)
const confirmText = ref('')

const form = reactive({
  enabled: false,
  text: '',
  icon: DEFAULT_ICON,
  targetUrl: DEFAULT_TARGET_URL
})

function fillForm(next: AdminAnnouncementTicker) {
  form.enabled = next.enabled
  form.text = next.text || ''
  form.icon = next.icon || DEFAULT_ICON
  form.targetUrl = next.targetUrl || DEFAULT_TARGET_URL
}

async function loadTicker() {
  loading.value = true
  error.value = ''
  try {
    const next = await getAdminAnnouncementTicker()
    ticker.value = next
    fillForm(next)
  } catch (err) {
    console.warn('admin announcement ticker load failed', err)
    ticker.value = null
    error.value = '公告配置加载失败，请确认管理员权限与服务状态。'
  } finally {
    loading.value = false
  }
}

async function saveTicker() {
  error.value = ''
  if (confirmText.value !== CONFIRM_TEXT) {
    error.value = `公告配置保存已阻止：请输入“${CONFIRM_TEXT}”完成二次确认。`
    return
  }
  saving.value = true
  try {
    const next = await updateAdminAnnouncementTicker({
      enabled: form.enabled,
      text: form.text,
      icon: form.icon,
      targetUrl: form.targetUrl
    })
    ticker.value = next
    fillForm(next)
    confirmText.value = ''
  } catch (err) {
    console.warn('admin announcement ticker save failed', err)
    error.value = err instanceof Error ? err.message : '公告配置保存失败，请检查字段、权限与服务状态。'
  } finally {
    saving.value = false
  }
}

onMounted(loadTicker)
</script>

<style scoped>
.announcement-page { max-width: 980px; }
.config-panel { margin-top: 18px; padding: 22px; border: 1px solid #ffe1cc; border-radius: 22px; background: #fff; box-shadow: 0 14px 34px rgba(255, 122, 69, .08); }
.panel-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.panel-head strong { display: block; color: #3a2a1f; font-size: 18px; }
.panel-head span { display: block; margin-top: 6px; color: #9b7560; font-size: 13px; }
.detail-grid { margin-top: 18px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.detail-grid div { padding: 14px; border-radius: 16px; background: #fffaf6; }
.detail-grid dt { color: #9b7560; font-size: 12px; }
.detail-grid dd { margin: 6px 0 0; color: #3a2a1f; font-weight: 800; word-break: break-all; }
.preview-card { margin-top: 16px; padding: 14px 16px; border-radius: 999px; display: flex; align-items: center; gap: 10px; background: #fff3e7; color: #5a3526; }
.preview-card span { width: 28px; height: 28px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: #ff7a45; }
.form-grid { margin-top: 18px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.form-grid label { display: flex; flex-direction: column; gap: 8px; color: #7b5542; font-weight: 800; }
.form-grid input { height: 40px; padding: 0 12px; border: 1px solid #ffd9bd; border-radius: 14px; color: #3a2a1f; }
.toggle-row { flex-direction: row !important; align-items: center; }
.toggle-row input { width: 18px; height: 18px; }
.safe-note { margin: 14px 0 0; color: #9b7560; font-size: 13px; }
.confirm-row { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; color: #7b5542; font-weight: 800; }
.confirm-row input { height: 40px; padding: 0 12px; border: 1px solid #ffd9bd; border-radius: 14px; }
.primary-btn,.ghost-btn { height: 40px; padding: 0 16px; border: 0; border-radius: 999px; font-weight: 900; cursor: pointer; }
.primary-btn { margin-top: 16px; background: #ff7a45; color: #fff; }
.ghost-btn { background: #fff3e7; color: #ff7a45; }
.alert { margin-top: 16px; padding: 12px 14px; border-radius: 14px; background: #fff1f2; color: #dc2626; }
.empty { margin-top: 16px; padding: 18px; border-radius: 16px; background: #fffaf6; color: #9b7560; }
</style>
