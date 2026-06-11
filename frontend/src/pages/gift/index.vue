<template>
  <view class="page-shell gift-page">
    <view class="hero ds-card">
      <view>
        <view class="page-title">{{ sendMode ? '送出礼物' : '收到的礼物' }}</view>
      </view>
      <view class="hero-icon gift-orb gift-orb-crown">
        <view class="gift-shine" />
        <image class="gift-img" :src="giftCrownUrl" mode="aspectFit" />
      </view>
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
        <view class="settle-chip">{{ rechargeBalanceText }}</view>
      </view>
      <view v-if="loadingCatalog" class="empty-row">礼物加载中...</view>
      <view v-else-if="catalogMessage" class="empty-row">{{ catalogMessage }}</view>
      <view v-else class="gift-grid">
        <view
          v-for="item in visibleCatalogList"
          :key="item.giftCode"
          :class="['gift-card', giftTone(item.giftCode), selectedGift?.giftCode === item.giftCode ? 'active' : '']"
          @click="selectGift(item)"
        >
          <view class="gift-orb pulse">
            <view class="gift-shine" />
            <image v-if="isGiftImage(item.icon)" class="gift-img" :src="resolveGiftImage(item.icon)" mode="aspectFit" />
            <text v-else>{{ item.icon || '🎁' }}</text>
          </view>
          <view class="gift-title">{{ item.name }}</view>
          <view class="gift-desc">¥{{ money(item.price) }}</view>
        </view>
      </view>
      <button v-if="hasFoldedGifts" class="fold-btn" @click="toggleGiftFold">
        {{ giftExpanded ? '收起其他礼物' : `展开其他 ${foldedGiftCount} 个礼物` }}
      </button>
      <view class="quantity-row">
        <text>数量</text>
        <button class="mini-btn" @click="changeQuantity(-1)">-</button>
        <text class="quantity-num">{{ quantity }}</text>
        <button class="mini-btn" @click="changeQuantity(1)">+</button>
      </view>
      <view class="cost-line">预计扣款：¥{{ estimatedTotal }}</view>
      <button class="primary-btn" :disabled="sending || loadingCatalog || !selectedGift" @click="submitGift">{{ sending ? '提交中...' : '送出礼物' }}</button>
      <view v-if="sendMessage" class="status-text">{{ sendMessage }}</view>
      <view v-if="giftEffect" class="gift-success-effect">
        <view class="effect-burst one" />
        <view class="effect-burst two" />
        <view class="effect-icon gift-orb gift-orb-crown">
          <view class="gift-shine" />
          <image v-if="isGiftImage(giftEffect.icon)" class="gift-img" :src="resolveGiftImage(giftEffect.icon)" mode="aspectFit" />
          <text v-else>{{ giftEffect.icon }}</text>
        </view>
        <view class="effect-copy">{{ giftEffect.name }} × {{ giftEffect.quantity }} 已送达</view>
        <view class="effect-order">{{ giftEffect.orderNo }}</view>
      </view>
    </view>

    <view class="section-card ds-card">
      <view class="section-head">
        <view class="section-title">礼物流水</view>
        <view class="settle-chip">累计入账 {{ totalIncomeText }}</view>
      </view>
      <view v-if="loadingReceived" class="empty-row">礼物流水加载中...</view>
      <view v-else-if="receivedMessage" class="empty-row">{{ receivedMessage }}</view>
      <view v-for="item in giftList" :key="item.giftOrderNo" class="gift-row">
        <view class="gift-orb gift-orb-mini">
          <view class="gift-shine" />
          <image v-if="isGiftImage(item.giftIcon)" class="gift-img" :src="resolveGiftImage(item.giftIcon)" mode="aspectFit" />
          <text v-else>{{ item.giftIcon || '🎁' }}</text>
        </view>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getGiftCatalog, getReceivedGifts, sendGift, type GiftCatalogItemResponse, type ReceivedGiftItemResponse } from '../../api/modules/gift'
import { getWalletBalance, type WalletMoneyAmount } from '../../api/modules/wallet'
import giftCoffeeUrl from '../../assets/gifts/gift-coffee.png'
import giftCrownUrl from '../../assets/gifts/gift-crown.png'
import giftCrystalShoeUrl from '../../assets/gifts/gift-crystal-shoe.png'
import giftGalaxyUrl from '../../assets/gifts/gift-galaxy.png'
import giftHeartUrl from '../../assets/gifts/gift-heart.png'
import giftLoveCastleUrl from '../../assets/gifts/gift-love-castle.png'
import giftPerfumeUrl from '../../assets/gifts/gift-perfume.png'
import giftRibbonBoxUrl from '../../assets/gifts/gift-ribbon-box.png'
import giftRoseUrl from '../../assets/gifts/gift-rose.png'
import giftStarUrl from '../../assets/gifts/gift-star.png'

const DEFAULT_VISIBLE_GIFT_COUNT = 3
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
const loadingCatalog = ref(false)
const loadingReceived = ref(false)
const loadingBalance = ref(false)
const sending = ref(false)
const giftExpanded = ref(false)
type GiftEffect = { icon: string; name: string; quantity: number; orderNo: string }
const giftEffect = ref<GiftEffect | null>(null)
let giftEffectTimer: ReturnType<typeof setTimeout> | null = null
const sendMode = computed(() => Boolean(receiverId.value))
const receiverLabel = computed(() => receiverName.value || (receiverId.value ? `用户 ${receiverId.value}` : '未指定'))
const rechargeBalanceText = computed(() => loadingBalance.value ? '余额加载中' : `¥${money(rechargeBalance.value)}`)
const totalIncome = computed(() => giftList.value.reduce((sum, item) => sum + Number(item.receiverAmount || 0), 0).toFixed(2))
const totalIncomeText = computed(() => loadingReceived.value ? '--' : `¥${totalIncome.value}`)
const estimatedTotal = computed(() => selectedGift.value ? (Number(selectedGift.value.price) * quantity.value).toFixed(2) : '0.00')
const hasFoldedGifts = computed(() => catalogList.value.length > DEFAULT_VISIBLE_GIFT_COUNT)
const foldedGiftCount = computed(() => Math.max(0, catalogList.value.length - DEFAULT_VISIBLE_GIFT_COUNT))
const visibleCatalogList = computed(() => giftExpanded.value || !hasFoldedGifts.value ? catalogList.value : catalogList.value.slice(0, DEFAULT_VISIBLE_GIFT_COUNT))
const summary = computed(() => [
  { label: '累计入账', value: totalIncomeText.value },
  { label: '收礼记录', value: loadingReceived.value ? '--' : `${giftList.value.length}` },
  { label: '可选礼物', value: loadingCatalog.value ? '--' : `${catalogList.value.length}` }
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
  loadingCatalog.value = true
  catalogMessage.value = ''
  try {
    catalogList.value = await getGiftCatalog()
    selectedGift.value = catalogList.value[0] || null
    catalogMessage.value = catalogList.value.length ? '' : '暂无礼物'
  } catch {
    catalogList.value = []
    selectedGift.value = null
    catalogMessage.value = '礼物暂不可用'
  } finally {
    loadingCatalog.value = false
  }
}

async function loadReceived() {
  loadingReceived.value = true
  receivedMessage.value = ''
  try {
    giftList.value = await getReceivedGifts()
    receivedMessage.value = giftList.value.length ? '' : '暂无记录'
  } catch {
    giftList.value = []
    receivedMessage.value = '礼物暂不可用'
  } finally {
    loadingReceived.value = false
  }
}

async function loadBalance() {
  if (!sendMode.value) return
  loadingBalance.value = true
  try {
    const balance = await getWalletBalance()
    rechargeBalance.value = balance.rechargeBalance
  } catch {
    rechargeBalance.value = '--'
  } finally {
    loadingBalance.value = false
  }
}

function selectGift(item: GiftCatalogItemResponse) {
  selectedGift.value = item
  sendMessage.value = ''
}

function toggleGiftFold() {
  giftExpanded.value = !giftExpanded.value
}

function changeQuantity(delta: number) {
  quantity.value = Math.min(99, Math.max(1, quantity.value + delta))
}

function showGiftEffect(gift: GiftCatalogItemResponse, sentQuantity: number, orderNo: string) {
  if (giftEffectTimer) clearTimeout(giftEffectTimer)
  giftEffect.value = { icon: gift.icon || '🎁', name: gift.name, quantity: sentQuantity, orderNo }
  giftEffectTimer = setTimeout(() => {
    giftEffect.value = null
    giftEffectTimer = null
  }, 2200)
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
    const sentGift = selectedGift.value
    const sentQuantity = quantity.value
    const response = await sendGift({ receiverId: receiverId.value, giftCode: sentGift.giftCode, quantity: sentQuantity, sceneType: sceneType.value, sceneId: sceneId.value, requestNo })
    if (response.status !== 'SUCCESS' || !response.giftOrderNo) throw new Error('Gift send did not return a successful order')
    showGiftEffect(sentGift, sentQuantity, response.giftOrderNo)
    sendMessage.value = `已送出，订单 ${response.giftOrderNo}`
    uni.showToast({ title: '送礼成功', icon: 'success' })
    await Promise.all([loadReceived(), loadBalance()])
  } catch {
    sendMessage.value = '送礼失败，请稍后重试'
    uni.showToast({ title: '送礼失败', icon: 'none' })
  } finally {
    sending.value = false
  }
}

function giftTone(giftCode: string): string {
  const normalized = giftCode.toLowerCase()
  if (normalized.includes('crown')) return 'gift-tone-crown'
  if (normalized.includes('heart') || normalized.includes('spark') || normalized.includes('love')) return 'gift-tone-heart'
  if (normalized.includes('box') || normalized.includes('star') || normalized.includes('galaxy')) return 'gift-tone-star'
  if (normalized.includes('perfume') || normalized.includes('shoe')) return 'gift-tone-gold'
  return 'gift-tone-rose'
}

function isGiftImage(value?: string | null): boolean {
  return Boolean(resolveGiftImage(value))
}

function resolveGiftImage(value?: string | null): string {
  const path = value?.trim() || ''
  const mapped = giftImageMap[path]
  if (mapped) return mapped
  return path.startsWith('/assets/') ? path : ''
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

const giftImageMap: Record<string, string> = {
  '/assets/gifts/gift-rose.png': giftRoseUrl,
  '/assets/gifts/gift-coffee.png': giftCoffeeUrl,
  '/assets/gifts/gift-star.png': giftStarUrl,
  '/assets/gifts/gift-heart.png': giftHeartUrl,
  '/assets/gifts/gift-ribbon-box.png': giftRibbonBoxUrl,
  '/assets/gifts/gift-perfume.png': giftPerfumeUrl,
  '/assets/gifts/gift-crystal-shoe.png': giftCrystalShoeUrl,
  '/assets/gifts/gift-crown.png': giftCrownUrl,
  '/assets/gifts/gift-galaxy.png': giftGalaxyUrl,
  '/assets/gifts/gift-love-castle.png': giftLoveCastleUrl
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '--'
}

onMounted(async () => {
  readQuery()
  await Promise.all([loadCatalog(), loadReceived(), loadBalance()])
})

onBeforeUnmount(() => {
  if (!giftEffectTimer) return
  clearTimeout(giftEffectTimer)
  giftEffectTimer = null
})
</script>

<style scoped>
.gift-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.section-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.hero { display:flex; justify-content:space-between; background:linear-gradient(135deg,#fff,#fff3e7); }
.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; font-size:40rpx; }
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
.gift-card { position:relative; padding:20rpx 18rpx; border-radius:28rpx; background:#fffaf6; border:2rpx solid transparent; text-align:center; overflow:hidden; }
.gift-card::before { content:""; position:absolute; inset:-40rpx -20rpx auto auto; width:120rpx; height:120rpx; border-radius:50%; background:rgba(255,122,69,.10); }
.gift-card.active { border-color:#ff7a45; background:#fff3e7; box-shadow:0 18rpx 34rpx rgba(255,122,69,.16); }
.gift-tone-crown { background:linear-gradient(180deg,#fff8d8,#fff3e7); }
.gift-tone-heart { background:linear-gradient(180deg,#fff0f6,#fff7ed); }
.gift-tone-star { background:linear-gradient(180deg,#f6f1ff,#fff7ed); }
.gift-tone-rose { background:linear-gradient(180deg,#fff4f0,#fffaf6); }
.gift-tone-gold { background:linear-gradient(180deg,#fff9e8,#fff5ef); }
.fold-btn { margin:16rpx 0 0; height:64rpx; line-height:64rpx; border-radius:999rpx; background:rgba(255,243,231,.88); color:#c8693f; font-size:23rpx; font-weight:900; }
.gift-row { margin-top:16rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; }
.gift-orb { position:relative; margin:0 auto; width:76rpx; height:76rpx; border-radius:28rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:38rpx; background:radial-gradient(circle at 32% 24%,#fff8cf 0,#ffcf73 28%,#ff7a45 64%,#ff4d8f 100%); box-shadow:0 14rpx 26rpx rgba(255,83,128,.18), inset 0 0 0 2rpx rgba(255,255,255,.45); overflow:hidden; }
.gift-orb-mini { flex:0 0 auto; margin:0; width:68rpx; height:68rpx; border-radius:24rpx; font-size:34rpx; }
.gift-orb-crown { background:radial-gradient(circle at 32% 24%,#fff8cf 0,#ffd76b 30%,#ff9a45 68%,#ff4d8f 100%); }
.gift-shine { position:absolute; left:10rpx; top:8rpx; width:24rpx; height:12rpx; border-radius:999rpx; background:rgba(255,255,255,.72); transform:rotate(-28deg); }
.gift-orb text { position:relative; z-index:1; filter:drop-shadow(0 4rpx 8rpx rgba(90,35,18,.16)); }
.gift-img { position:relative; z-index:1; width:88%; height:88%; display:block; filter:drop-shadow(0 5rpx 10rpx rgba(90,35,18,.16)); }
.gift-orb.pulse { animation:pulse 1.6s infinite; }
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
.gift-success-effect { position:relative; margin-top:18rpx; padding:22rpx 18rpx; border-radius:32rpx; display:flex; flex-direction:column; align-items:center; gap:8rpx; background:radial-gradient(circle at 50% 8%,rgba(255,246,196,.9),transparent 34%),linear-gradient(135deg,#fff4e8,#ffe8ef); color:#3a2a1f; box-shadow:0 22rpx 46rpx rgba(255,122,69,.18); overflow:hidden; animation:gift-pop 2.2s ease both; }
.effect-icon { width:86rpx; height:86rpx; border-radius:50%; font-size:44rpx; }
.effect-copy { position:relative; z-index:1; font-size:25rpx; font-weight:950; }
.effect-order { position:relative; z-index:1; color:#9b7560; font-size:19rpx; font-weight:850; }
.effect-burst { position:absolute; width:150rpx; height:150rpx; border-radius:50%; border:2rpx solid rgba(255,255,255,.72); opacity:.7; animation:burst 2.2s ease both; }
.effect-burst.one { left:38rpx; top:10rpx; }
.effect-burst.two { right:34rpx; bottom:-52rpx; animation-delay:.12s; }
@keyframes pulse { 0%,100% { transform:scale(1); } 50% { transform:scale(1.08); } }
@keyframes gift-pop { 0% { opacity:0; transform:translateY(16rpx) scale(.96); } 18%,82% { opacity:1; transform:translateY(0) scale(1); } 100% { opacity:0; transform:translateY(-10rpx) scale(.98); } }
@keyframes burst { 0% { transform:scale(.42); opacity:.65; } 72% { opacity:.18; } 100% { transform:scale(1.35); opacity:0; } }
</style>
