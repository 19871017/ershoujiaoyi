<template>
  <section class="page-shell pricing-page">
    <div class="page-title">商品加价</div>
    <div class="page-desc">设置卖家底价到买家展示价的统一加价比例；订单会保留卖家结算金额和平台加价金额。</div>

    <div class="config-panel">
      <div class="panel-head">
        <div>
          <strong>当前加价策略</strong>
          <span>默认 30%，卖家挂 100 元时买家看到 130 元</span>
        </div>
        <button class="ghost-btn" :disabled="loading" @click="loadConfig">{{ loading ? '刷新中...' : '刷新配置' }}</button>
      </div>
      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading && !config" class="empty">商品加价配置加载中...</div>
      <dl v-if="config" class="detail-grid">
        <div><dt>加价比例</dt><dd>{{ percentText(config.markupRate) }}</dd></div>
        <div><dt>配置值</dt><dd>{{ rateText(config.markupRate) }}</dd></div>
        <div><dt>示例底价</dt><dd>¥{{ exampleSellerPrice.toFixed(2) }}</dd></div>
        <div><dt>买家展示价</dt><dd>¥{{ previewBuyerPrice(config.markupRate) }}</dd></div>
        <div><dt>更新时间</dt><dd>{{ config.updatedAt || '默认配置' }}</dd></div>
      </dl>
    </div>

    <form class="config-panel" @submit.prevent="saveConfig">
      <div class="panel-head">
        <div>
          <strong>更新加价比例</strong>
          <span>填写 0 到 5 之间的小数，最多 4 位小数；0.3000 表示加价 30%</span>
        </div>
      </div>
      <div class="form-grid">
        <label>
          <span>加价比例</span>
          <input v-model.trim="markupRate" inputmode="decimal" placeholder="例如 0.3000" />
        </label>
        <label>
          <span>预览</span>
          <input :value="`卖家 ¥${exampleSellerPrice.toFixed(2)} -> 买家 ¥${previewBuyerPrice(parsedRate)}`" disabled />
        </label>
      </div>
      <p class="safe-note">该配置只控制商品买家展示价和订单应付价；卖家确认收货结算仍按卖家底价进入可提现余额。</p>
      <label class="confirm-row">
        <span>二次确认</span>
        <input v-model.trim="confirmText" autocomplete="off" placeholder="输入 保存商品加价 后才能提交" />
      </label>
      <button class="primary-btn" :disabled="saving">{{ saving ? '保存中...' : '保存商品加价' }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  getAdminProductPricingConfig,
  updateAdminProductPricingConfig,
  type AdminProductPricingConfig
} from '../../../api'

const CONFIRM_TEXT = '保存商品加价'
const exampleSellerPrice = 100
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const config = ref<AdminProductPricingConfig | null>(null)
const markupRate = ref('0.3000')
const confirmText = ref('')

const parsedRate = computed(() => Number(markupRate.value))

function rateText(rate: number) {
  const value = Number(rate)
  return Number.isFinite(value) ? value.toFixed(4) : '0.0000'
}

function percentText(rate: number) {
  const value = Number(rate)
  return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : '0.00%'
}

function previewBuyerPrice(rate: number) {
  const value = Number(rate)
  const safeRate = Number.isFinite(value) ? value : 0
  return (exampleSellerPrice * (1 + safeRate)).toFixed(2)
}

function fillForm(next: AdminProductPricingConfig) {
  config.value = next
  markupRate.value = rateText(next.markupRate)
}

async function loadConfig() {
  loading.value = true
  error.value = ''
  try {
    fillForm(await getAdminProductPricingConfig())
  } catch (err) {
    console.warn('admin product pricing config load failed', err)
    error.value = '商品加价配置加载失败，请确认管理员权限与服务状态。'
    config.value = null
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  error.value = ''
  if (confirmText.value !== CONFIRM_TEXT) {
    error.value = `商品加价保存已阻止：请输入“${CONFIRM_TEXT}”完成二次确认。`
    return
  }
  saving.value = true
  try {
    const next = await updateAdminProductPricingConfig({ markupRate: parsedRate.value })
    fillForm(next)
    confirmText.value = ''
  } catch (err) {
    console.warn('admin product pricing config save failed', err)
    error.value = err instanceof Error ? err.message : '商品加价配置保存失败，请检查字段、权限与服务状态。'
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.pricing-page { max-width: 980px; }
.config-panel { margin-top: 18px; padding: 22px; border: 1px solid #ffe1cc; border-radius: 22px; background: #fff; box-shadow: 0 14px 34px rgba(255, 122, 69, .08); }
.panel-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.panel-head strong { display: block; color: #3a2a1f; font-size: 18px; }
.panel-head span { display: block; margin-top: 6px; color: #9b7560; font-size: 13px; }
.detail-grid { margin-top: 18px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.detail-grid div { padding: 14px; border-radius: 16px; background: #fffaf6; }
.detail-grid dt { color: #9b7560; font-size: 12px; }
.detail-grid dd { margin: 6px 0 0; color: #3a2a1f; font-weight: 800; word-break: break-all; }
.form-grid { margin-top: 18px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.form-grid label { display: flex; flex-direction: column; gap: 8px; color: #7b5542; font-weight: 800; }
.form-grid input { height: 40px; padding: 0 12px; border: 1px solid #ffd9bd; border-radius: 14px; color: #3a2a1f; }
.form-grid input:disabled { background: #fffaf6; color: #7b5542; }
.safe-note { margin: 14px 0 0; color: #9b7560; font-size: 13px; }
.confirm-row { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; color: #7b5542; font-weight: 800; }
.confirm-row input { height: 40px; padding: 0 12px; border: 1px solid #ffd9bd; border-radius: 14px; }
.primary-btn,.ghost-btn { height: 40px; padding: 0 16px; border: 0; border-radius: 999px; font-weight: 900; cursor: pointer; }
.primary-btn { margin-top: 16px; background: #ff7a45; color: #fff; }
.ghost-btn { background: #fff3e7; color: #ff7a45; }
.alert { margin-top: 16px; padding: 12px 14px; border-radius: 14px; background: #fff1f2; color: #dc2626; }
.empty { margin-top: 16px; padding: 18px; border-radius: 16px; background: #fffaf6; color: #9b7560; }
@media (max-width: 720px) {
  .form-grid,
  .detail-grid { grid-template-columns: 1fr; }
  .panel-head { flex-direction: column; }
}
</style>
