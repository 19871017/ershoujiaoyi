<template>
  <view class="page-shell confirm-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 平台订单创建后再进入支付确认</view>
        <view class="page-title">确认订单</view>
        <view class="page-desc">付款前请确认商品、配送和订单规则；支付、售后和聊天记录以平台订单状态为准。</view>
      </view>
      <view class="hero-icon">🧾</view>
    </view>

    <view v-if="loading" class="status-card ds-card">
      <view class="status-icon">⌛</view>
      <view>
        <view class="status-title">正在读取商品</view>
        <view class="status-desc">平台正在同步最新商品信息和交易规则。</view>
      </view>
    </view>
    <view v-else-if="errorText" class="status-card ds-card danger">
      <view class="status-icon">!</view>
      <view>
        <view class="status-title">无法确认订单</view>
        <view class="status-desc">{{ errorText }}</view>
      </view>
    </view>

    <template v-else-if="product">
      <view class="goods-card ds-card tapable" @click="openProduct">
        <image v-if="coverImage" class="goods-image" :src="coverImage" mode="aspectFill" />
        <view v-else class="goods-cover">{{ coverIcon(product.title) }}</view>
        <view class="goods-main">
          <view class="goods-title">{{ product.title }}</view>
          <view class="goods-desc">{{ productStatusText }} · {{ product.tradeRule }}</view>
          <view class="goods-price">¥{{ product.price }}</view>
        </view>
        <view class="arrow">›</view>
      </view>

      <view class="address-card ds-card tapable" @click="openAddress">
        <view class="section-title">收货信息</view>
        <template v-if="address">
          <view class="address-name">{{ address.name }} {{ address.mobile }}</view>
          <view class="address-text">{{ address.full }}</view>
        </template>
        <template v-else>
          <view class="address-empty">请选择收货信息</view>
          <view class="address-text">快递邮寄需要收货人、手机号和详细地址；正式配送状态以平台订单记录为准。</view>
        </template>
      </view>

      <view class="form-card ds-card">
        <view class="section-title">配送方式</view>
        <view class="ship-row">
          <view v-for="item in deliveryTypes" :key="item.value" class="ship-chip tapable" :class="{ active: deliveryType === item.value }" @click="deliveryType = item.value">
            <view>{{ item.label }}</view>
            <text>{{ item.desc }}</text>
          </view>
        </view>
        <textarea v-model.trim="buyerRemark" class="textarea" maxlength="80" placeholder="给卖家留言，可填写尺码确认、发货提醒或配送偏好" />
      </view>

      <view class="safe-card ds-card">
        <view class="section-title">订单规则确认</view>
        <view v-for="item in confirmItems" :key="item.text" class="confirm-row tapable" @click="item.checked = !item.checked">
          <view class="check" :class="{ active: item.checked }">✓</view>
          <view>{{ item.text }}</view>
        </view>
      </view>

      <view class="amount-card ds-card">
        <view class="section-title">金额明细</view>
        <view class="amount-row"><text>商品金额</text><text>¥{{ product.price }}</text></view>
        <view class="amount-row"><text>配送费用</text><text>卖家发货规则为准</text></view>
        <view class="amount-row total"><text>应付合计</text><text>¥{{ product.price }}</text></view>
      </view>

      <view class="bottom-bar">
        <view>
          <view class="pay-label">应付</view>
          <view class="pay-amount">¥{{ product.price }}</view>
        </view>
        <button class="primary-btn submit" :disabled="submitting || !canSubmit" @click="submitOrder">{{ submitting ? '提交中...' : '提交订单' }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createOrder } from '../../../api/modules/order'
import { getProductDetail, type ProductDetailResponse } from '../../../api/modules/product'

type DeliveryType = 'EXPRESS' | 'LOCAL_MEET'
interface ShippingAddress { id?: number | string; name: string; mobile: string; full: string }

const launchReadinessMarkers = [
  '平台订单创建后再进入支付确认',
  '支付、售后和聊天记录以服务端订单状态为准'
]

const productId = ref<number | null>(null)
const product = ref<ProductDetailResponse | null>(null)
const address = ref<ShippingAddress | null>(null)
const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const deliveryType = ref<DeliveryType>('EXPRESS')
const buyerRemark = ref('')
const deliveryTypes = [
  { value: 'EXPRESS' as const, label: '快递邮寄', desc: '卖家发货后可看物流' },
  { value: 'LOCAL_MEET' as const, label: '线下交付', desc: '交付状态以平台订单记录为准' }
]
const confirmItems = reactive([
  { text: '我已确认商品成色、尺码和瑕疵说明', checked: true },
  { text: '我理解创建订单后仍需进入支付页读取平台订单状态', checked: true },
  { text: '我不会脱离平台私下转账或外部联系', checked: true }
])
const coverImage = computed(() => product.value?.imageUrls?.[0] || '')
const productStatusText = computed(() => {
  if (!product.value) return ''
  if (product.value.status === 'SOLD') return '已售出'
  if (product.value.visible && String(product.value.auditState).toUpperCase() === 'APPROVED') return '在售'
  return '待确认'
})
const canSubmit = computed(() => Boolean(product.value && productId.value && !errorText.value))
function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const raw = current?.options?.productId || current?.options?.goodsId || hashParams?.get('productId') || hashParams?.get('goodsId') || ''
  const parsed = Number(raw)
  productId.value = Number.isFinite(parsed) && parsed > 0 ? parsed : null
}
function readAddress() {
  const stored = uni.getStorageSync('xyq_default_address') || uni.getStorageSync('defaultAddress')
  if (stored && typeof stored === 'object') {
    const item = stored as Partial<ShippingAddress>
    if (item.name && item.mobile && item.full) address.value = { id: item.id, name: item.name, mobile: item.mobile, full: item.full }
  }
}
async function loadProduct() {
  if (!productId.value) { errorText.value = '缺少商品编号，请从商品详情页进入确认订单'; return }
  loading.value = true; errorText.value = ''
  try {
    const detail = await getProductDetail(productId.value)
    product.value = detail
    if (!detail.visible || String(detail.auditState).toUpperCase() !== 'APPROVED' || detail.status === 'SOLD') {
      errorText.value = '商品当前不可下单，请返回商品详情刷新状态'
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '商品信息读取失败'
  } finally { loading.value = false }
}
function openProduct() { if (productId.value) uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId.value}` }) }
function openAddress() { uni.navigateTo({ url: '/pages/user/address/index' }) }
async function submitOrder() {
  if (!product.value || !productId.value) return uni.showToast({ title: '商品信息未就绪', icon: 'none' })
  if (deliveryType.value === 'EXPRESS' && !address.value) return uni.showToast({ title: '请先选择收货信息', icon: 'none' })
  const missing = confirmItems.find((item) => !item.checked)
  if (missing) return uni.showToast({ title: '请先确认订单、支付和售后记录规则', icon: 'none' })
  submitting.value = true
  try {
    const order = await createOrder({ goodsId: productId.value, acceptedTradeRule: true })
    const amount = String(order.productPrice || product.value.price)
    uni.navigateTo({ url: `/pages/payment/checkout/index?orderNo=${encodeURIComponent(order.orderNo)}&amount=${encodeURIComponent(amount)}&productId=${productId.value}` })
  } catch (error) {
    uni.showModal({ title: '订单未创建', content: error instanceof Error ? error.message : '订单没有提交成功，请检查商品状态或稍后重试；不会进入收银台。', showCancel: false })
  } finally { submitting.value = false }
}
function coverIcon(title: string) { if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🎀'; if (title.includes('包')) return '👜'; if (title.includes('衣') || title.includes('裙')) return '👗'; return '🛍️' }
onMounted(() => { readQuery(); readAddress(); void loadProduct() })
</script>

<style scoped>
.confirm-page { padding-bottom:124rpx; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.goods-card,.address-card,.form-card,.safe-card,.amount-card,.status-card { margin-top:12rpx; padding:16rpx; border-color:#ffd9bd; }
.hero,.goods-card,.status-card { display:flex; gap:10rpx; align-items:center; }
.hero { justify-content:space-between; background:linear-gradient(135deg,#fff,#fff3e7); }
.kicker { color:#ff7a45; font-size:20rpx; font-weight:950; }
.hero-icon,.status-icon { width:62rpx; height:62rpx; border-radius:22rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:30rpx; flex-shrink:0; }
.danger .status-icon { background:#ef4444; }
.status-title { color:#3a2a1f; font-size:26rpx; font-weight:950; }
.status-desc { margin-top:5rpx; color:#9b7560; font-size:21rpx; line-height:1.38; }
.goods-cover,.goods-image { width:98rpx; height:98rpx; border-radius:22rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; font-size:38rpx; flex-shrink:0; }
.goods-image { display:block; }
.goods-main { flex:1; min-width:0; }
.goods-title,.section-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.goods-desc,.address-text,.address-empty { margin-top:5rpx; color:#9b7560; font-size:20rpx; line-height:1.35; }
.address-empty { color:#ff7a45; font-weight:950; }
.goods-price { margin-top:6rpx; color:#ff3f8d; font-size:27rpx; font-weight:950; }
.address-name { margin-top:8rpx; color:#3a2a1f; font-size:23rpx; font-weight:950; }
.arrow { color:#d79262; font-size:34rpx; }
.ship-row { margin-top:10rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:8rpx; }
.ship-chip { padding:12rpx; border-radius:18rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-weight:950; }
.ship-chip text { display:block; margin-top:5rpx; color:#9b7560; font-size:18rpx; font-weight:700; }
.ship-chip.active { border-color:#ff7a45; background:#fff3e7; }
.textarea { box-sizing:border-box; width:100%; height:98rpx; margin-top:10rpx; padding:14rpx; border-radius:18rpx; background:#fffaf6; color:#3a2a1f; border:1rpx solid #ffd9bd; font-size:22rpx; }
.confirm-row { margin-top:10rpx; display:flex; gap:10rpx; align-items:center; color:#7b5542; font-size:21rpx; font-weight:850; }
.check { width:30rpx; height:30rpx; border-radius:50%; border:1rpx solid #ffd9bd; color:transparent; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.check.active { background:#ff7a45; border-color:#ff7a45; color:#fff; }
.amount-row { min-height:46rpx; display:flex; justify-content:space-between; align-items:center; color:#7b5542; font-size:21rpx; border-bottom:1rpx solid #ffe5ef; }
.amount-row:last-child { border-bottom:0; }
.amount-row.total { color:#3a2a1f; font-weight:950; font-size:24rpx; }
.bottom-bar { position:fixed; left:0; right:0; bottom:0; padding:12rpx 18rpx calc(12rpx + env(safe-area-inset-bottom)); background:rgba(255,247,237,.96); border-top:1rpx solid #ffd9bd; display:flex; justify-content:space-between; align-items:center; gap:14rpx; z-index:20; }
.pay-label { color:#9b7560; font-size:20rpx; }
.pay-amount { color:#ff3f8d; font-size:34rpx; font-weight:950; }
.submit { width:260rpx; height:54rpx; line-height:54rpx; }
</style>
