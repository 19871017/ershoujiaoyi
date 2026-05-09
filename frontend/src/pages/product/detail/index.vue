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
import { getProductDetail, type ProductDetailResponse } from '../../../api/modules/product'
import { resolveProductSellerContactTarget } from '../../../api/modules/order-contact'

const productId = ref<number>(0)
const loading = ref(false)
const ordering = ref(false)
const errorMessage = ref('')
const orderMessage = ref('')
const detail = ref<ProductDetailResponse | null>(null)
const activeImageIndex = ref(0)
const favorited = ref(false)
const safeRules = [
  { icon: '🛡️', title: '平台交易', desc: '订单、支付和售后状态以服务端记录为准' },
  { icon: '💬', title: '聊天留痕', desc: '私下转账可直接举报' },
  { icon: '📦', title: '验货确认', desc: '收到后再确认收货' }
]
const confirmItems = reactive([
  { key: 'rule', label: '已阅读订单、支付和售后状态以服务端记录为准', checked: true },
  { key: 'condition', label: '已确认商品成色和瑕疵说明', checked: false },
  { key: 'address', label: '已确认收货/同城约看信息', checked: false }
])
const displayImages = computed(() => detail.value?.imageUrls?.length ? detail.value.imageUrls : ['', '', ''])
const activeImage = computed(() => displayImages.value[activeImageIndex.value] || '')
const statusText = computed(() => detail.value?.status === 'created' ? '在售' : detail.value?.status || '未知')
const auditText = computed(() => detail.value?.auditState === 'pending' ? '审核中' : detail.value?.auditState || '审核状态')
const sellerName = computed(() => detail.value?.sellerId ? `卖家 ${detail.value.sellerId}` : '商品卖家')
const sellerCity = computed(() => '卖家城市以服务端资料为准')
const sellerTrustText = computed(() => '商品卖家信息以服务端返回为准 · 暂无服务端信用/成交统计')
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
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '商品详情加载失败，不能展示本地样例商品'; detail.value = null }
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
function shareProduct() { uni.showToast({ title: '已生成分享卡片预览', icon: 'none' }) }
function toggleFavorite() { uni.showToast({ title: '收藏接口暂未接通后端，未执行任何收藏变更', icon: 'none' }) }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function toneClass(id: number) { return `tone-${id % 4}` }
onMounted(() => { readProductId(); loadDetail() })
</script>

<style scoped>
.detail-page { padding-top:20rpx; padding-bottom:148rpx; background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.detail-body { display:flex; flex-direction:column; gap:18rpx; }
.hero-card,.info-card,.seller-card,.rule-card,.action-panel,.message { padding:20rpx; border-color:#ffd9bd; }
.hero { height:430rpx; border-radius:30rpx; display:flex; align-items:center; justify-content:center; font-size:96rpx; overflow:hidden; box-shadow:0 14rpx 30rpx rgba(255,122,69,.12); }
.hero-img { width:100%; height:100%; }
.thumb-row { margin-top:14rpx; display:flex; gap:12rpx; }
.thumb { width:92rpx; height:92rpx; border-radius:22rpx; background:#fff3e7; display:flex; align-items:center; justify-content:center; border:2rpx solid transparent; overflow:hidden; font-size:32rpx; }
.thumb image { width:100%; height:100%; }
.thumb.active { border-color:#ff7a45; }
.tone-0 { background:#fff3e7; } .tone-1 { background:#fff4e7; } .tone-2 { background:#fdf2f8; } .tone-3 { background:#fff7ed; }
.title-row { display:flex; gap:16rpx; align-items:flex-start; }
.title { flex:1; font-size:38rpx; line-height:1.3; font-weight:950; color:#3a2a1f; }
.favorite { width:58rpx; height:58rpx; border-radius:50%; background:#fff3e7; color:#ff7a45; display:flex; align-items:center; justify-content:center; font-size:34rpx; }
.favorite.active { background:#ff7a45; color:#fff; }
.price { margin-top:14rpx; font-size:46rpx; font-weight:950; color:#ff7a45; }
.desc,.rule-line,.message { margin-top:18rpx; color:#7b5542; line-height:1.6; font-size:26rpx; }
.meta-row { margin-top:18rpx; display:flex; gap:12rpx; flex-wrap:wrap; }
.pill { padding:8rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:22rpx; font-weight:850; }
.pill.green { background:#fff8e8; color:#b45309; }
.pill.soft { background:#f0fdf4; color:#15803d; }
.seller-card { display:flex; align-items:center; gap:16rpx; }
.seller-avatar { width:78rpx; height:78rpx; border-radius:50%; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; align-items:center; justify-content:center; font-size:32rpx; font-weight:950; }
.seller-main { flex:1; min-width:0; }
.seller-name { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.seller-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; }
.tag-row { margin-top:8rpx; display:flex; gap:8rpx; flex-wrap:wrap; }
.mini-tag { padding:5rpx 10rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:18rpx; font-weight:850; }
.mini-btn { margin:0; padding:0 18rpx; height:54rpx; line-height:54rpx; border-radius:999rpx; background:#fff; color:#ff7a45; border:1rpx solid #ffd9bd; font-size:21rpx; font-weight:900; }
.section-title,.panel-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }
.safe-grid { margin-top:16rpx; display:flex; flex-direction:column; gap:12rpx; }
.safe-item { padding:16rpx; border-radius:24rpx; background:#fffaf6; display:flex; gap:14rpx; }
.safe-icon { font-size:32rpx; }
.safe-title { color:#3a2a1f; font-size:24rpx; font-weight:950; }
.safe-desc { margin-top:4rpx; color:#9b7560; font-size:21rpx; }
.confirm-row { margin-top:14rpx; display:flex; align-items:center; gap:12rpx; color:#7b5542; font-size:23rpx; font-weight:850; }
.check { width:34rpx; height:34rpx; border-radius:50%; border:1rpx solid #ffd9bd; color:transparent; display:flex; align-items:center; justify-content:center; }
.check.active { background:#ff7a45; border-color:#ff7a45; color:#fff; }
.bottom-actions { position:fixed; left:0; right:0; bottom:0; padding:16rpx 18rpx calc(16rpx + env(safe-area-inset-bottom)); background:rgba(255,247,251,.96); border-top:1rpx solid #ffd9bd; display:flex; gap:10rpx; z-index:20; }
.icon-btn { margin:0; padding:0 14rpx; min-width:86rpx; height:58rpx; line-height:58rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#7b5542; font-size:21rpx; font-weight:900; }
.action { flex:1; min-width:0; height:58rpx; line-height:58rpx; font-size:22rpx; }
.state { padding:28rpx; color:#6b7280; }
.error { color:#ef4444; }
</style>
