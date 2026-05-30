<template>
  <view class="page-shell favorite-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 我的心愿夹</view>
        <view class="page-title">我的收藏</view>
        <view class="page-desc">仅展示平台收藏商品，加载失败时请稍后重试。</view>
      </view>
      <view class="hero-icon">💗</view>
    </view>
    <view class="filter-row">
      <view v-for="item in filters" :key="item" class="filter-chip tapable" :class="{ active: active === item }" @click="active = item">{{ item }}</view>
    </view>

    <view v-if="loading" class="empty-card ds-card">收藏列表加载中...</view>
    <view v-else-if="loadMessage" class="empty-card ds-card danger">{{ loadMessage }}</view>
    <view v-else-if="filtered.length === 0" class="empty-card ds-card">暂无收藏商品</view>

    <view class="fav-grid">
      <view v-for="item in filtered" :key="item.productId" class="fav-card ds-card tapable" @click="openProduct(item.productId)">
        <view class="cover">
          <image v-if="item.coverImageUrl" class="cover-img" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ iconFor(item.title) }}</text>
        </view>
        <view class="title">{{ item.title }}</view>
        <view class="meta">平台商品 · {{ statusLabel(item.status) }}</view>
        <view class="bottom"><text>¥{{ compactPrice(item.price) }}</text><button class="mini-btn" :disabled="removingIds.has(item.productId)" @click.stop="unfav(item.productId)">{{ removingIds.has(item.productId) ? '处理中' : '取消' }}</button></view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listFavoriteProducts, unfavoriteProduct, type ProductListItemResponse } from '../../api/modules/product'
const launchReadinessMarkers = [
  '收藏列表接口加载失败，未展示本地收藏样例',
  '后端取消收藏失败，未执行本地收藏变更'
]

const active = ref('全部')
const loading = ref(false)
const loadMessage = ref('')
const productStatusLabels: Partial<Record<ProductListItemResponse['status'], string>> = {
  created: '在售',
  ACTIVE: '在售'
}

const removingIds = ref<Set<number>>(new Set())
const favorites = ref<ProductListItemResponse[]>([])
const filters = computed(() => ['全部', ...Array.from(new Set(favorites.value.map((item) => statusLabel(item.status))))])
const filtered = computed(() => {
  if (active.value === '全部') return favorites.value
  return favorites.value.filter((item) => statusLabel(item.status) === active.value)
})
function openProduct(productId: number) {
  if (!productId || productId <= 0) { uni.showToast({ title: '收藏商品编号无效，暂无法打开商品详情', icon: 'none' }); return }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` })
}
async function unfav(productId: number) {
  if (!productId || productId <= 0) { uni.showToast({ title: '收藏商品编号无效，暂无法取消收藏', icon: 'none' }); return }
  if (removingIds.value.has(productId)) return
  removingIds.value = new Set([...removingIds.value, productId])
  try {
    await unfavoriteProduct(productId)
    favorites.value = favorites.value.filter((item) => item.productId !== productId)
    if (active.value !== '全部' && !filtered.value.length) active.value = '全部'
    uni.showToast({ title: '平台已确认取消收藏', icon: 'none' })
  } catch {
    uni.showToast({ title: '后端取消收藏失败，未执行本地收藏变更', icon: 'none' })
  } finally {
    const next = new Set(removingIds.value)
    next.delete(productId)
    removingIds.value = next
  }
}
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function statusLabel(status: ProductListItemResponse['status']): string { return productStatusLabels[status] || status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
async function loadFavorites() {
  loading.value = true
  loadMessage.value = ''
  try {
    favorites.value = await listFavoriteProducts()
  } catch {
    favorites.value = []
    loadMessage.value = '收藏列表接口加载失败，未展示本地收藏样例'
  } finally {
    loading.value = false
  }
}
onMounted(loadFavorites)
</script>
<style scoped>
.favorite-page { min-height:100vh; padding-top:18rpx; padding-bottom:44rpx; background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%); }
.hero,.fav-card,.empty-card { margin-top:16rpx; padding:22rpx; border-color:rgba(255,217,189,.78); box-shadow:0 15rpx 30rpx rgba(132,70,36,.08); }
.hero { margin-top:0; display:flex; justify-content:space-between; align-items:center; gap:20rpx; background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96)); }
.fav-card,.empty-card { background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97)); }
.kicker { color:#df6735; font-size:22rpx; font-weight:950; letter-spacing:.18rpx; }
.hero-icon,.cover { display:flex; align-items:center; justify-content:center; }
.hero-icon { width:82rpx; height:82rpx; border-radius:28rpx; background:linear-gradient(135deg,#ef6f3f,#ff8b76); color:#fffaf4; font-size:38rpx; box-shadow:0 12rpx 24rpx rgba(255,122,69,.17); flex:0 0 auto; }
.filter-row { margin-top:18rpx; display:flex; gap:12rpx; overflow-x:auto; padding-bottom:4rpx; }
.filter-chip,.mini-btn { border-radius:999rpx; background:rgba(255,255,255,.92); border:1rpx solid rgba(255,217,189,.78); }
.filter-chip { flex:none; padding:13rpx 20rpx; color:#8f6b57; font-size:22rpx; font-weight:900; box-shadow:0 8rpx 16rpx rgba(132,70,36,.05); }
.filter-chip.active { background:linear-gradient(135deg,#3a261a,#6f432b); color:#fffaf4; border-color:rgba(58,38,26,.82); box-shadow:0 10rpx 20rpx rgba(58,38,26,.13); }
.empty-card { color:#8f6b57; font-size:24rpx; line-height:1.5; text-align:center; font-weight:760; }
.empty-card.danger { background:#fff7f7; color:#be123c; border-color:#fecaca; }
.fav-grid { margin-top:16rpx; display:grid; grid-template-columns:repeat(2,1fr); gap:14rpx; }
.cover { height:180rpx; border-radius:28rpx; background:linear-gradient(135deg,#fff3e7,#ffe5ef); font-size:58rpx; overflow:hidden; box-shadow:inset 0 0 0 1rpx rgba(255,217,189,.52); }
.cover text { font-size:58rpx; }
.cover-img { width:100%; height:100%; }
.title { margin-top:12rpx; color:#342116; font-size:25rpx; line-height:1.35; font-weight:950; letter-spacing:.12rpx; }
.meta { margin-top:6rpx; color:#8f6b57; font-size:20rpx; line-height:1.38; font-weight:650; }
.bottom { margin-top:10rpx; display:flex; align-items:center; justify-content:space-between; gap:10rpx; color:#df6735; font-size:28rpx; font-weight:950; }
.mini-btn { margin:0; padding:0 16rpx; height:46rpx; line-height:46rpx; color:#7b5542; font-size:20rpx; font-weight:900; }
</style>
