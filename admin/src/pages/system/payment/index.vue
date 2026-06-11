<template>
  <section class="page-shell payment-page">
    <div class="page-title">支付配置</div>
    <div class="page-desc">配置支付宝与微信支付商户参数；密钥只提交给后端，不会在页面回显。</div>

    <div class="config-panel">
      <div class="panel-head">
        <div>
          <strong>通道状态</strong>
          <span>只有启用且必填参数完整时，用户端才能拉起第三方收银台</span>
        </div>
        <button class="ghost-btn" :disabled="loading" @click="loadConfigs">{{ loading ? '刷新中...' : '刷新配置' }}</button>
      </div>
      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="loading && !configs.length" class="empty">支付配置加载中...</div>
      <dl v-if="configs.length" class="detail-grid">
        <div v-for="item in configs" :key="item.channel">
          <dt>{{ channelLabel(item.channel) }}</dt>
          <dd>{{ item.enabled ? '已启用' : '未启用' }} / {{ item.configured ? '已完整配置' : '配置不完整' }}</dd>
          <small>AppID {{ item.appId || '未填' }} · 商户 {{ item.merchantId || '未填' }}</small>
        </div>
      </dl>
    </div>

    <form v-for="channel in channels" :key="channel" class="config-panel" @submit.prevent="saveChannel(channel)">
      <div class="panel-head">
        <div>
          <strong>{{ channelLabel(channel) }}</strong>
          <span>{{ channelHint(channel) }}</span>
        </div>
      </div>
      <div class="form-grid">
        <label class="toggle-row">
          <input v-model="forms[channel].enabled" type="checkbox" />
          <span>启用{{ channelLabel(channel) }}</span>
        </label>
        <label class="toggle-row">
          <input v-model="forms[channel].sandbox" type="checkbox" />
          <span>沙箱环境</span>
        </label>
        <label>
          <span>AppID</span>
          <input v-model.trim="forms[channel].appId" autocomplete="off" />
        </label>
        <label>
          <span>商户号 / 合作者身份</span>
          <input v-model.trim="forms[channel].merchantId" autocomplete="off" />
        </label>
        <label>
          <span>异步通知地址</span>
          <input v-model.trim="forms[channel].notifyUrl" placeholder="https://你的域名/api/payments/notify/..." />
        </label>
        <label>
          <span>同步返回地址</span>
          <input v-model.trim="forms[channel].returnUrl" placeholder="https://你的域名/#/pages/order/list/index" />
        </label>
        <label>
          <span>网关地址（可选）</span>
          <input v-model.trim="forms[channel].gatewayUrl" autocomplete="off" />
        </label>
        <label v-if="channel === 'WECHAT'">
          <span>商户证书序列号</span>
          <input v-model.trim="forms[channel].merchantSerialNo" autocomplete="off" />
        </label>
        <label class="wide">
          <span>商户私钥 PEM / PKCS8</span>
          <textarea v-model.trim="forms[channel].merchantPrivateKey" autocomplete="off" placeholder="留空则保持原密钥不变"></textarea>
        </label>
        <label v-if="channel === 'ALIPAY'" class="wide">
          <span>支付宝公钥</span>
          <textarea v-model.trim="forms[channel].alipayPublicKey" autocomplete="off" placeholder="留空则保持原公钥不变"></textarea>
        </label>
        <label v-if="channel === 'WECHAT'">
          <span>APIv3 密钥</span>
          <input v-model.trim="forms[channel].apiV3Key" type="password" autocomplete="off" placeholder="留空则保持原密钥不变" />
        </label>
        <label v-if="channel === 'WECHAT'" class="wide">
          <span>微信支付平台公钥</span>
          <textarea v-model.trim="forms[channel].wechatPayPublicKey" autocomplete="off" placeholder="留空则保持原公钥不变"></textarea>
        </label>
      </div>
      <p class="safe-note">{{ secretStateText(channel) }}</p>
      <label class="confirm-row">
        <span>二次确认</span>
        <input v-model.trim="confirmText[channel]" autocomplete="off" :placeholder="`输入 保存${channelLabel(channel)} 才能提交`" />
      </label>
      <button class="primary-btn" :disabled="saving[channel]">{{ saving[channel] ? '保存中...' : `保存${channelLabel(channel)}` }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  getAdminPaymentConfig,
  updateAdminPaymentConfig,
  type AdminPaymentChannel,
  type AdminPaymentChannelConfig
} from '../../../api'

const channels: AdminPaymentChannel[] = ['ALIPAY', 'WECHAT']
const loading = ref(false)
const error = ref('')
const configs = ref<AdminPaymentChannelConfig[]>([])
const saving = reactive<Record<AdminPaymentChannel, boolean>>({ ALIPAY: false, WECHAT: false })
const confirmText = reactive<Record<AdminPaymentChannel, string>>({ ALIPAY: '', WECHAT: '' })
const forms = reactive<Record<AdminPaymentChannel, {
  enabled: boolean
  sandbox: boolean
  appId: string
  merchantId: string
  gatewayUrl: string
  notifyUrl: string
  returnUrl: string
  merchantPrivateKey: string
  alipayPublicKey: string
  merchantSerialNo: string
  apiV3Key: string
  wechatPayPublicKey: string
}>>({
  ALIPAY: blankForm(),
  WECHAT: blankForm()
})

function blankForm() {
  return {
    enabled: false,
    sandbox: false,
    appId: '',
    merchantId: '',
    gatewayUrl: '',
    notifyUrl: '',
    returnUrl: '',
    merchantPrivateKey: '',
    alipayPublicKey: '',
    merchantSerialNo: '',
    apiV3Key: '',
    wechatPayPublicKey: ''
  }
}

function channelLabel(channel: AdminPaymentChannel) {
  return channel === 'ALIPAY' ? '支付宝' : '微信支付'
}

function channelHint(channel: AdminPaymentChannel) {
  return channel === 'ALIPAY'
    ? '手机网站支付：AppID、商户私钥、支付宝公钥、异步通知地址必须完整。'
    : '微信 H5 支付：AppID、商户号、证书序列号、商户私钥、APIv3 密钥、微信支付平台公钥必须完整。'
}

function fillForm(item: AdminPaymentChannelConfig) {
  const form = forms[item.channel]
  form.enabled = item.enabled
  form.sandbox = item.sandbox
  form.appId = item.appId || ''
  form.merchantId = item.merchantId || ''
  form.gatewayUrl = item.gatewayUrl || ''
  form.notifyUrl = item.notifyUrl || ''
  form.returnUrl = item.returnUrl || ''
  form.merchantSerialNo = item.merchantSerialNo || ''
  form.merchantPrivateKey = ''
  form.alipayPublicKey = ''
  form.apiV3Key = ''
  form.wechatPayPublicKey = ''
}

function secretStateText(channel: AdminPaymentChannel) {
  const item = configs.value.find((row) => row.channel === channel)
  if (!item) return '密钥状态加载中。'
  if (channel === 'ALIPAY') {
    return `商户私钥：${item.merchantPrivateKeyConfigured ? '已配置' : '未配置'}；支付宝公钥：${item.alipayPublicKeyConfigured ? '已配置' : '未配置'}。`
  }
  return `商户私钥：${item.merchantPrivateKeyConfigured ? '已配置' : '未配置'}；APIv3 密钥：${item.apiV3KeyConfigured ? '已配置' : '未配置'}；平台公钥：${item.wechatPayPublicKeyConfigured ? '已配置' : '未配置'}。`
}

async function loadConfigs() {
  loading.value = true
  error.value = ''
  try {
    const next = await getAdminPaymentConfig()
    configs.value = next
    next.forEach(fillForm)
  } catch (err) {
    console.warn('payment config load failed', err)
    error.value = '支付配置加载失败，请确认后台权限与服务状态。'
  } finally {
    loading.value = false
  }
}

async function saveChannel(channel: AdminPaymentChannel) {
  error.value = ''
  const confirm = `保存${channelLabel(channel)}`
  if (confirmText[channel] !== confirm) {
    error.value = `支付配置保存已阻止：请输入“${confirm}”完成二次确认。`
    return
  }
  saving[channel] = true
  const form = forms[channel]
  try {
    const next = await updateAdminPaymentConfig(channel, {
      enabled: form.enabled,
      sandbox: form.sandbox,
      appId: form.appId,
      merchantId: form.merchantId,
      gatewayUrl: form.gatewayUrl || undefined,
      notifyUrl: form.notifyUrl,
      returnUrl: form.returnUrl || undefined,
      merchantPrivateKey: form.merchantPrivateKey || undefined,
      alipayPublicKey: form.alipayPublicKey || undefined,
      merchantSerialNo: form.merchantSerialNo || undefined,
      apiV3Key: form.apiV3Key || undefined,
      wechatPayPublicKey: form.wechatPayPublicKey || undefined
    })
    configs.value = configs.value.filter((item) => item.channel !== channel).concat(next)
    fillForm(next)
    confirmText[channel] = ''
  } catch (err) {
    console.warn('payment config save failed', err)
    error.value = err instanceof Error ? err.message : '支付配置保存失败，请检查字段、权限与服务状态。'
  } finally {
    saving[channel] = false
  }
}

onMounted(loadConfigs)
</script>

<style scoped>
.payment-page { max-width: 1080px; }
.detail-grid small { display: block; margin-top: 8px; color: #6b7280; word-break: break-all; }
.wide { grid-column: 1 / -1; }
textarea { min-height: 120px; resize: vertical; border: 1px solid #d1d5db; border-radius: 10px; padding: 10px 12px; background: #fff; color: #111827; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.confirm-row { margin-top: 14px; display: grid; gap: 8px; color: #374151; font-weight: 700; }
.confirm-row input { border: 1px solid #d1d5db; border-radius: 10px; padding: 10px 12px; }
</style>
