<template>
  <view class="page-shell gift-page">
    <view class="hero ds-card">
      <view>
        <view class="page-title">{{ sendMode ? '送出礼物' : '收到的礼物' }}</view>
      </view>
      <view class="hero-icon">🎁</view>
    </view>

    <view class="summary-grid">
      <view v-for="item in summary" :key="item.label" class="summary-card ds-card">
        <view class="summary-num">{{ item.value }}</view>
        <view class="summary-label">{{ item.label }}</view>
      </view>
    </view>

    <view v-if="sendMode" class="section-card ds-card">
      <view class="section-head">
        <view class="section-title">{{ receiverLabel }}</view>
        <view class="settle-chip">¥{{ rechargeBalanceText }}</view>
      </view>
      <view v-if="catalogMessage" class="empty-row">{{ catalogMessage }}</view>
      <view class="gift-grid">
        <view
          v-for="item in catalogList"
          :key="item.giftCode"
          :class="['gift-card', selectedGift?.giftCode === item.giftCode ? 'active' : '']"
          @click="selectGift(item)"
        >
          <view class="gift-icon pulse">{{ item.icon || '🎁' }}</view>
          <view class="gift-title">{{ item.name }}</view>
          <view class="gift-desc">¥{{ money(item.price) }}</view>
        </view>
      </view>
      <view class="quantity-row">
        <text>数量</text>
        <button class="mini-btn" @click="changeQuantity(-1)">-</button>
        <text class="quantity-num">{{ quantity }}</text>
        <button class="mini-btn" @click="changeQuantity(1)">+</button>
      </view>
      <view class="cost-line">预计扣款：¥{{ estimatedTotal }}</view>
      <button class="primary-btn" :disabled="sending" @click="submitGift">{{ sending ? '提交中...' : '送出礼物' }}</button>
      <view v-if="sendMessage" class="status-text">{{ sendMessage }}</view>
    </view>

    <view class="section-card ds-card">
      <view class="section-head">
        <view class="section-title">礼物流水</view>
        <view class="settle-chip">累计入账 ¥{{ totalIncome }}</view>
      </view>
      <view v-if="receivedMessage" class="empty-row">{{ receivedMessage }}</view>
      <view v-for="item in giftList" :key="item.giftOrderNo" class="gift-row">
        <view class="gift-icon">{{ item.giftIcon || '🎁' }}</view>
        <view class="gift-main">
          <view class="gift-title">{{ item.giftName }} × {{ item.quantity }} · 来自 {{ senderLabel(item.senderId) }}</view>
          <view class="gift-desc">{{ formatDateTime(item.createdAt) }}</view>
        </view>
        <view class="gift-amount">+¥{{ money(item.receiverAmount) }}</view>
      </view>
    </view>

    <view class="section-card ds-card">
      <view class="section-head">
        <view class="section-title">互动入口</view>
      </view>
      <view class="action-grid">
        <view class="action-card tapable" @click="openRanking">
          <view class="action-icon">🏆</view>
          <view class="action-title">礼物榜单</view>
        </view>
        <view class="action-card tapable" @click="sendThanks">
          <view class="action-icon">💌</view>
          <view class="action-title">感谢私信</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getGiftCatalog, getReceivedGifts, sendGift, type GiftCatalogItemResponse, type ReceivedGiftItemResponse } from '../../api/modules/gift'
import { getWalletBalance, type WalletMoneyAmount } from '../../api/modules/wallet'

const receiverId = ref<number | null>(null)
const receiverName = ref('')
const sceneType = ref('PROFILE')
const sceneId = ref<number | undefined>()
const catalogList = ref<GiftCatalogItemResponse[]>([])
const giftList = ref<ReceivedGiftItemResponse[]>([])
const selectedGift = ref<GiftCatalogItemResponse | null>(null)
const quantity = ref(1)
const rechargeBalance = ref<WalletMoneyAmount>('--')
const catalogMessage = ref('')
const receivedMessage = ref('')
const sendMessage = ref('')
const sending = ref(false)
const sendMode = computed(() => Boolean(receiverId.value))
const receiverLabel = computed(() => receiverName.value || (receiverId.value ? `用户 ${receiverId.value}` : '未指定'))
const rechargeBalanceText = computed(() => money(rechargeBalance.value))
const totalIncome = computed(() => giftList.value.reduce((sum, item) => sum + Number(item.receiverAmount || 0), 0).toFixed(2))
const estimatedTotal = computed(() => selectedGift.value ? (Number(selectedGift.value.price) * quantity.value).toFixed(2) : '0.00')
const summary = computed(() => [
  { label: '累计入账', value: `¥${totalIncome.value}` },
  { label: '收礼记录', value: `${giftList.value.length}` },
  { label: '可选礼物', value: `${catalogList.value.length}` }
])

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const rawReceiver = current?.options?.receiverId || hash?.get('receiverId') || ''
  const parsedReceiver = Number(rawReceiver)
  receiverId.value = Number.isSafeInteger(parsedReceiver) && parsedReceiver > 0 ? parsedReceiver : null
  receiverName.value = current?.options?.receiverName || hash?.get('receiverName') || ''
  sceneType.value = current?.options?.sceneType || hash?.get('sceneType') || 'PROFILE'
  const rawSceneId = current?.options?.sceneId || hash?.get('sceneId') || ''
  const parsedSceneId = Number(rawSceneId)
  sceneId.value = Number.isSafeInteger(parsedSceneId) && parsedSceneId > 0 ? parsedSceneId : undefined
}

async function loadCatalog() {
  try {
    catalogList.value = await getGiftCatalog()
    selectedGift.value = catalogList.value[0] || null
    catalogMessage.value = catalogList.value.length ? '' : '暂无礼物'
  } catch {
    catalogList.value = []
    selectedGift.value = null
    catalogMessage.value = '礼物暂不可用'
  }
}

async function loadReceived() {
  try {
    giftList.value = await getReceivedGifts()
    receivedMessage.value = giftList.value.length ? '' : '暂无记录'
  } catch {
    giftList.value = []
    receivedMessage.value = '礼物暂不可用'
  }
}

async function loadBalance() {
  if (!sendMode.value) return
  try {
    const balance = await getWalletBalance()
    rechargeBalance.value = balance.rechargeBalance
  } catch {
    rechargeBalance.value = '--'
  }
}

function selectGift(item: GiftCatalogItemResponse) {
  selectedGift.value = item
  sendMessage.value = ''
}

function changeQuantity(delta: number) {
  quantity.value = Math.min(99, Math.max(1, quantity.value + delta))
}

async function submitGift() {
  if (sending.value) return
  if (!receiverId.value) {
    sendMessage.value = '请选择礼物接收人'
    uni.showToast({ title: '请选择礼物接收人', icon: 'none' })
    return
  }
  if (!selectedGift.value) {
    sendMessage.value = '礼物目录不可用'
    uni.showToast({ title: '礼物目录不可用', icon: 'none' })
    return
  }
  sending.value = true
  sendMessage.value = ''
  try {
    const requestNo = `gift-${receiverId.value}-${selectedGift.value.giftCode}-${Date.now()}`
    await sendGift({ receiverId: receiverId.value, giftCode: selectedGift.value.giftCode, quantity: quantity.value, sceneType: sceneType.value, sceneId: sceneId.value, requestNo })
    sendMessage.value = '已送出'
    uni.showToast({ title: '送礼成功', icon: 'success' })
    await Promise.all([loadReceived(), loadBalance()])
  } catch {
    sendMessage.value = '送礼失败，请稍后重试'
    uni.showToast({ title: '送礼失败', icon: 'none' })
  } finally {
    sending.value = false
  }
}

function senderLabel(senderId: number) {
  return `用户 ${senderId}`
}

function sendThanks() {
  const first = giftList.value[0]
  if (!first?.senderId) {
    uni.showToast({ title: '暂无送礼用户', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${first.senderId}` })
}

function openRanking() {
  uni.navigateTo({ url: '/pages/ranking/index?tab=goddess&period=week' })
}

function money(value: WalletMoneyAmount) {
  return value === '--' ? '--' : Number(value || 0).toFixed(2)
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '--'
}

onMounted(async () => {
  readQuery()
  await Promise.all([loadCatalog(), loadReceived(), loadBalance()])
})
</script>

<style scoped>
.gift-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.section-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.hero { display:flex; justify-content:space-between; background:linear-gradient(135deg,#fff,#fff3e7); }
.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:40rpx; }
.summary-grid { margin-top:18rpx; display:grid; grid-template-columns:repeat(3,1fr); gap:12rpx; }
.summary-card { padding:18rpx 8rpx; text-align:center; border-color:#ffd9bd; }
.summary-num { color:#ff7a45; font-size:28rpx; font-weight:950; }
.summary-label,.gift-desc,.status-text { margin-top:6rpx; color:#9b7560; font-size:21rpx; }
.section-head,.gift-row,.quantity-row { display:flex; align-items:center; gap:14rpx; }
.section-head { justify-content:space-between; }
.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }
.settle-chip { padding:9rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:900; }
.settle-chip.warm { background:#ffe8ef; color:#ff3f8d; }
.gift-grid { margin-top:16rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:14rpx; }
.gift-card { padding:18rpx; border-radius:24rpx; background:#fffaf6; border:2rpx solid transparent; text-align:center; }
.gift-card.active { border-color:#ff7a45; background:#fff3e7; box-shadow:0 16rpx 28rpx rgba(255,122,69,.14); }
.gift-row { margin-top:16rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; }
.gift-icon { width:68rpx; height:68rpx; border-radius:22rpx; background:#fff; display:flex; align-items:center; justify-content:center; font-size:34rpx; }
.gift-icon.pulse { animation:pulse 1.6s infinite; }
.gift-main { flex:1; min-width:0; }
.gift-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.gift-amount { color:#16a34a; font-size:26rpx; font-weight:950; }
.quantity-row { margin-top:18rpx; color:#7b5542; font-size:24rpx; font-weight:900; }
.mini-btn { width:56rpx; height:56rpx; padding:0; line-height:56rpx; border-radius:50%; background:#fff3e7; color:#ff7a45; font-weight:950; }
.quantity-num { min-width:46rpx; text-align:center; }
.cost-line { margin-top:14rpx; color:#3a2a1f; font-size:25rpx; font-weight:950; }
.empty-row { margin-top:16rpx; padding:20rpx; border-radius:24rpx; background:#fffaf6; color:#9b7560; font-size:23rpx; }
.action-grid { margin-top:16rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:14rpx; }
.action-card { padding:18rpx; border-radius:24rpx; background:#fffaf6; text-align:center; }
.action-icon { font-size:34rpx; }
.action-title { margin-top:8rpx; color:#3a2a1f; font-size:23rpx; font-weight:900; }
.primary-btn,.secondary-btn { margin-top:18rpx; border-radius:999rpx; font-size:25rpx; font-weight:950; }
.primary-btn { background:#ff7a45; color:#fff; }
.secondary-btn { background:#fff3e7; color:#ff7a45; }
@keyframes pulse { 0%,100% { transform:scale(1); } 50% { transform:scale(1.08); } }
</style>
