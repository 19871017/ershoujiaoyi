<template>
  <view class="page-shell detail-page">
    <view v-if="loading" class="ds-card state">加载中...</view>

    <view v-else-if="detail" class="detail-body">
      <view class="hero-card ds-card">
        <view class="hero" :class="toneClass(detail.productId)">
          <image v-if="activeImage" class="hero-img" :src="activeImage" mode="aspectFill" />
          <text v-else>{{ iconFor(detail.title) }}</text>
        </view>
        <view class="thumb-row">
          <view v-for="(img, index) in displayImages" :key="index" class="thumb tapable" :class="{ active: index === activeImageIndex }" @click="activeImageIndex = index">
            <image v-if="img" :src="img" mode="aspectFill" />
            <text v-else>{{ iconFor(detail.title) }}</text>
          </view>
        </view>
      </view>

      <view class="info-card ds-card">
        <view class="title-row">
          <view class="title">{{ detail.title }}</view>
          <view class="favorite tapable" :class="{ active: favorited }" @click="toggleFavorite">{{ favorited ? '♥' : '♡' }}</view>
        </view>
        <view class="price">¥{{ compactPrice(detail.price) }}</view>
        <view class="meta-row">
          <view class="pill">{{ statusText }}</view>
          <view class="pill green">{{ auditText }}</view>
          <view class="pill soft">{{ sellerCity }}</view>
        </view>
        <view class="desc">{{ detail.description || '暂无描述' }}</view>
      </view>

      <view class="seller-card ds-card">
        <view class="seller-avatar">{{ sellerName.slice(0, 1) }}</view>
        <view class="seller-main">
          <view class="seller-name">{{ sellerName }}</view>
          <view class="seller-desc">{{ sellerTrustText }}</view>
          <view class="tag-row"><text v-for="tag in sellerTags" :key="tag" class="mini-tag">{{ tag }}</text></view>
        </view>
        <button class="mini-btn" @click="contactSeller">私信</button>
      </view>

      <view class="rule-card ds-card">
        <view class="section-title">交易保障</view>
        <view class="rule-line">{{ detail.tradeRule }}</view>
        <view class="safe-grid">
          <view v-for="item in safeRules" :key="item.title" class="safe-item">
            <view class="safe-icon">{{ item.icon }}</view>
            <view>
              <view class="safe-title">{{ item.title }}</view>
              <view class="safe-desc">{{ item.desc }}</view>
            </view>
          </view>
        </view>
      </view>

      <view class="action-panel ds-card">
        <view class="panel-title">购买前确认</view>
        <view v-for="item in confirmItems" :key="item.key" class="confirm-row tapable" @click="item.checked = !item.checked">
          <view :class="['check', { active: item.checked }]">✓</view>
          <view>{{ item.label }}</view>
        </view>
      </view>

      <view v-if="orderMessage" class="message ds-card">{{ orderMessage }}</view>

      <view class="bottom-actions">
        <button class="icon-btn" @click="shareProduct">分享</button>
        <button class="icon-btn" @click="reportProduct">举报</button>
        <button class="secondary-btn action" @click="contactSeller">私信卖家</button>
        <button class="primary-btn action" :disabled="ordering" @click="createAndPay">{{ ordering ? '处理中...' : '确认订单' }}</button>
      </view>
    </view>

    <view v-else class="ds-card state error">{{ errorMessage || '商品不存在' }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { favoriteProduct, getProductDetail, unfavoriteProduct, type ProductDetailResponse } from '../../../api/modules/product'
import { resolveProductSellerContactTarget } from '../../../api/modules/order-contact'

const productId = ref<number>(0)
const loading = ref(false)
const ordering = ref(false)
const errorMessage = ref('')
const orderMessage = ref('')
const detail = ref<ProductDetailResponse | null>(null)
const activeImageIndex = ref(0)
const favorited = ref(false)
const favoriteLoading = ref(false)
const safeRules = [
  { icon: '🛡️', title: '平台交易', desc: '订单、支付和售后状态以平台记录为准' },
  { icon: '💬', title: '会话记录', desc: '沟通内容以平台会话记录为准' },
  { icon: '📦', title: '交付确认', desc: '交付与收货状态以平台订单记录为准' }
]
const confirmItems = reactive([
  { key: 'rule', label: '已阅读订单、支付和售后状态以平台记录为准', checked: true },
  { key: 'condition', label: '已确认商品成色和瑕疵说明', checked: false },
  { key: 'address', label: '已确认收货信息；正式交付状态以平台订单记录为准', checked: false }
])
const displayImages = computed(() => detail.value?.imageUrls?.length ? detail.value.imageUrls : ['', '', ''])
const activeImage = computed(() => displayImages.value[activeImageIndex.value] || '')
const statusText = computed(() => detail.value?.status === 'created' ? '在售' : detail.value?.status || '未知')
const auditText = computed(() => detail.value?.auditState === 'pending' ? '审核中' : detail.value?.auditState || '审核状态')
const sellerName = computed(() => detail.value?.sellerId ? `卖家 ${detail.value.sellerId}` : '商品卖家')
const sellerCity = computed(() => '卖家城市以平台资料为准')
const sellerTrustText = computed(() => '商品卖家信息以平台返回为准 · 暂无信用/成交统计')
const sellerTags = computed(() => [] as string[])
function readProductId() {
  const pages = getCurrentPages()
  const current = pages.length > 0 ? (pages[pages.length - 1] as unknown as { options?: Record<string, string> }) : undefined
  const fromPages = Number(current?.options?.productId || 0)
  const fromHash = typeof window !== 'undefined'
    ? Number(new URLSearchParams(window.location.hash.split('?')[1] || '').get('productId') || 0)
    : 0
  productId.value = fromPages || fromHash || 0
}
async function loadDetail() {
  if (!productId.value) { errorMessage.value = '缺少商品ID'; return }
  loading.value = true
  try { detail.value = await getProductDetail(productId.value) }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '商品详情加载失败，请稍后重试'; detail.value = null }
  finally { loading.value = false }
}
async function createAndPay() {
  if (!detail.value) return
  const unconfirmed = confirmItems.find((item) => !item.checked)
  if (unconfirmed) { uni.showToast({ title: '请先完成购买前确认', icon: 'none' }); return }
  ordering.value = true
  orderMessage.value = ''
  try {
    uni.navigateTo({ url: `/pages/order/confirm/index?productId=${detail.value.productId}` })
  } catch {
    orderMessage.value = '确认订单页面打开失败，请稍后重试'
  } finally { ordering.value = false }
}
function contactSeller() {
  if (!detail.value) return
  const target = resolveProductSellerContactTarget(detail.value)
  if (!target.receiverId) return uni.showToast({ title: target.error || '无法发起聊天', icon: 'none' })
  uni.navigateTo({ url: `/pages/chat/conversation/index?receiverId=${target.receiverId}&productId=${detail.value.productId}` })
}
function isValidProductReportTargetId(value: unknown) {
  const numeric = Number(value)
  return Number.isInteger(numeric) && numeric > 0
}
function reportProduct() {
  const reportTargetId = detail.value?.productId ?? productId.value
  if (!isValidProductReportTargetId(reportTargetId)) {
    uni.showToast({ title: '缺少有效商品编号，不能提交举报', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/report/submit/index?targetType=GOODS&targetId=${encodeURIComponent(String(reportTargetId))}` })
}
function shareProduct() { uni.showToast({ title: '分享功能暂时不可用，请稍后重试', icon: 'none' }) }
async function toggleFavorite() {
  if (!detail.value?.productId || detail.value.productId <= 0) {
    uni.showToast({ title: '商品编号无效，未执行收藏变更', icon: 'none' })
    return
  }
  if (favoriteLoading.value) return
  favoriteLoading.value = true
  const wasFavorited = favorited.value
  try {
    if (wasFavorited) {
      await unfavoriteProduct(detail.value.productId)
      favorited.value = false
      uni.showToast({ title: '取消收藏已同步', icon: 'none' })
    } else {
      await favoriteProduct(detail.value.productId)
      favorited.value = true
      uni.showToast({ title: '收藏已同步', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '收藏失败，请稍后重试', icon: 'none' })
  } finally {
    favoriteLoading.value = false
  }
}
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function toneClass(id: number) { return `tone-${id % 4}` }
onMounted(() => { readProductId(); loadDetail() })
</script>

<style scoped>
.detail-page { padding-top:12rpx; padding-bottom:128rpx; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.detail-body { display:flex; flex-direction:column; gap:12rpx; }
.hero-card,.info-card,.seller-card,.rule-card,.action-panel,.message { padding:14rpx; border-color:#ffd9bd; }
.hero { height:300rpx; border-radius:24rpx; display:flex; align-items:center; justify-content:center; font-size:68rpx; overflow:hidden; box-shadow:0 14rpx 30rpx rgba(255,122,69,.12); }
.hero-img { width:100%; height:100%; }
.thumb-row { margin-top:10rpx; display:flex; gap:8rpx; }
.thumb { width:66rpx; height:66rpx; border-radius:18rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; border:2rpx solid transparent; overflow:hidden; font-size:26rpx; }
.thumb image { width:100%; height:100%; }
.thumb.active { border-color:#ff7a45; }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff4e7; } .tone-2 { background:#fdf2f8; } .tone-3 { background:#fff7ed; }
.title-row { display:flex; gap:10rpx; align-items:flex-start; }
.title { flex:1; font-size:31rpx; line-height:1.3; font-weight:950; color:#3a2a1f; }
.favorite { width:48rpx; height:48rpx; border-radius:50%; background:#fff3e7; color:#ff7a45; display:flex; align-items:center; justify-content:center; font-size:21rpx; }
.favorite.active { background:#ff7a45; color:#fff; }
.price { margin-top:8rpx; font-size:38rpx; font-weight:950; color:#ff7a45; }
.desc,.rule-line,.message { margin-top:10rpx; color:#7b5542; line-height:1.42; font-size:23rpx; }
.meta-row { margin-top:10rpx; display:flex; gap:8rpx; flex-wrap:wrap; }
.pill { padding:6rpx 12rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:850; }
.pill.green { background:#fff8e8; color:#b45309; }
.pill.soft { background:#f0fdf4; color:#15803d; }
.seller-card { display:flex; align-items:center; gap:10rpx; }
.seller-avatar { width:58rpx; height:58rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:26rpx; font-weight:950; }
.seller-main { flex:1; min-width:0; }
.seller-name { color:#3a2a1f; font-size:21rpx; font-weight:950; }
.seller-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; }
.tag-row { margin-top:8rpx; display:flex; gap:8rpx; flex-wrap:wrap; }
.mini-tag { padding:5rpx 10rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:18rpx; font-weight:850; }
.mini-btn { margin:0; padding:0 18rpx; height:46rpx; line-height:46rpx; border-radius:999rpx; background:#fff; color:#ff7a45; border:1rpx solid #ffd9bd; font-size:21rpx; font-weight:900; }
.section-title,.panel-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }
.safe-grid { margin-top:10rpx; display:flex; flex-direction:column; gap:8rpx; }
.safe-item { padding:12rpx; border-radius:18rpx; background:#fffaf6; display:flex; gap:14rpx; }
.safe-icon { font-size:26rpx; }
.safe-title { color:#3a2a1f; font-size:21rpx; font-weight:950; }
.safe-desc { margin-top:4rpx; color:#9b7560; font-size:21rpx; }
.confirm-row { margin-top:10rpx; display:flex; align-items:center; gap:12rpx; color:#7b5542; font-size:23rpx; font-weight:850; }
.check { width:30rpx; height:30rpx; border-radius:50%; border:1rpx solid #ffd9bd; color:transparent; display:flex; align-items:center; justify-content:center; }
.check.active { background:#ff7a45; border-color:#ff7a45; color:#fff; }
.bottom-actions { position:fixed; left:0; right:0; bottom:0; padding:12rpx 14rpx calc(12rpx + env(safe-area-inset-bottom)); background:rgba(255,247,251,.96); border-top:1rpx solid #ffd9bd; display:flex; gap:10rpx; z-index:20; }
.icon-btn { margin:0; padding:0 14rpx; min-width:86rpx; height:52rpx; line-height:52rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#7b5542; font-size:21rpx; font-weight:900; }
.action { flex:1; min-width:0; height:52rpx; line-height:52rpx; font-size:20rpx; }
.state { padding:28rpx; color:#6b7280; }
.error { color:#ef4444; }
</style>
