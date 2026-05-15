<template>
  <section class="page-shell location-page">
    <div class="page-title">位置配置</div>
    <div class="page-desc">读取并保存位置服务配置；地图服务密钥只提交给平台，不在配置详情中回显。</div>

    <div class="config-panel">
      <div class="panel-head">
        <div>
          <strong>当前位置服务状态</strong>
          <span>配置状态以平台记录为准</span>
        </div>
        <button class="ghost-btn" :disabled="loading" @click="loadConfig">{{ loading ? '刷新中...' : '刷新配置' }}</button>
      </div>

      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading && !config" class="empty">位置配置加载中...</div>

      <dl v-if="config" class="detail-grid">
        <div><dt>服务商</dt><dd>{{ config.provider }}</dd></div>
        <div><dt>启用状态</dt><dd>{{ config.enabled ? '已启用' : '未启用' }}</dd></div>
        <div><dt>密钥配置</dt><dd>{{ config.configured ? '已配置' : '未配置' }}</dd></div>
        <div><dt>默认城市</dt><dd>{{ config.defaultProvince }} {{ config.defaultCity }}</dd></div>
        <div><dt>坐标系</dt><dd>{{ config.coordinateType }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ config.updatedAt || '暂无' }}</dd></div>
      </dl>
      <div v-else-if="!loading" class="empty">暂未读取到位置配置。</div>
    </div>

    <form class="config-panel" @submit.prevent="saveConfig">
      <div class="panel-head">
        <div>
          <strong>更新位置配置</strong>
          <span>密钥输入框保存后会清空</span>
        </div>
      </div>
      <div class="form-grid">
        <label>
          <span>服务商</span>
          <select v-model="form.provider">
            <option value="baidu">baidu</option>
            <option value="amap">amap</option>
            <option value="tencent">tencent</option>
            <option value="manual">manual</option>
          </select>
        </label>
        <label>
          <span>坐标系</span>
          <select v-model="form.coordinateType">
            <option value="bd09ll">bd09ll</option>
            <option value="gcj02">gcj02</option>
            <option value="wgs84">wgs84</option>
          </select>
        </label>
        <label>
          <span>默认省份</span>
          <input v-model.trim="form.defaultProvince" placeholder="例如 上海市" />
        </label>
        <label>
          <span>默认城市</span>
          <input v-model.trim="form.defaultCity" placeholder="例如 上海" />
        </label>
        <label>
          <span>百度 AK（可选，仅提交不回显）</span>
          <input v-model.trim="form.baiduAk" type="password" autocomplete="off" placeholder="留空则不变更平台密钥" />
        </label>
        <label class="toggle-row">
          <input v-model="form.enabled" type="checkbox" />
          <span>启用位置服务</span>
        </label>
      </div>
      <p class="safe-note">安全提示：本页不展示服务商密钥；保存成功只采用平台返回配置刷新页面状态。</p>
      <label class="confirm-row">
        <span>二次确认</span>
        <input v-model.trim="locationConfirmText" autocomplete="off" placeholder="输入 保存位置配置 后才能提交" />
      </label>
      <button class="primary-btn" :disabled="saving">{{ saving ? '保存中...' : '保存配置' }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getAdminLocationConfig, updateAdminLocationConfig, type AdminLocationConfig } from '../../../api'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const config = ref<AdminLocationConfig | null>(null)
const locationConfirmText = ref('')

const form = reactive({
  provider: 'baidu',
  enabled: false,
  defaultCity: '',
  defaultProvince: '',
  coordinateType: 'bd09ll',
  baiduAk: ''
})

function fillForm(next: AdminLocationConfig) {
  form.provider = next.provider || 'baidu'
  form.enabled = next.enabled
  form.defaultCity = next.defaultCity || ''
  form.defaultProvince = next.defaultProvince || ''
  form.coordinateType = next.coordinateType || 'bd09ll'
  form.baiduAk = ''
}

async function loadConfig() {
  loading.value = true
  error.value = ''
  try {
    const next = await getAdminLocationConfig()
    config.value = next
    fillForm(next)
  } catch {
    config.value = null
    error.value = '位置配置加载失败，请确认管理员权限与服务状态。'
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  error.value = ''
  if (locationConfirmText.value !== '保存位置配置') {
    error.value = '位置配置保存已阻止：请输入“保存位置配置”完成二次确认。'
    return
  }
  saving.value = true
  try {
    const next = await updateAdminLocationConfig({
      provider: form.provider,
      enabled: form.enabled,
      defaultCity: form.defaultCity,
      defaultProvince: form.defaultProvince,
      coordinateType: form.coordinateType,
      baiduAk: form.baiduAk || undefined
    })
    config.value = next
    fillForm(next)
    locationConfirmText.value = ''
  } catch {
    error.value = '位置配置保存失败，请检查管理员权限、字段合法性和服务状态。'
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>
