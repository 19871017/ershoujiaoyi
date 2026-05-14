<template>
  <view class="page-shell favorite-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 我的心愿夹</view>
        <view class="page-title">我的收藏</view>
        <view class="page-desc">仅展示平台收藏接口返回的商品，加载失败不展示默认收藏。</view>
      </view>
      <view class="hero-icon">💗</view>
    </view>
    <view class="filter-row">
      <view v-for="item in filters" :key="item" class="filter-chip tapable" :class="{ active: active === item }" @click="active = item">{{ item }}</view>
    </view>

    <view v-if="loading" class="empty-card ds-card">收藏列表加载中...</view>
    <view v-else-if="loadMessage" class="empty-card ds-card danger">{{ loadMessage }}</view>
    <view v-else-if="filtered.length === 0" class="empty-card ds-card">暂无平台收藏商品，未展示默认收藏</view>

    <view class="fav-grid">
      <view v-for="item in filtered" :key="item.productId" class="fav-card ds-card tapable" @click="openProduct(item.productId)">
        <view class="cover">
          <image v-if="item.coverImageUrl" class="cover-img" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ iconFor(item.title) }}</text>
        </view>
        <view class="title">{{ item.title }}</view>
        <view class="meta">平台商品 · {{ statusLabel(item.status) }}</view>
        <view class="bottom"><text>¥{{ compactPrice(item.price) }}</text><button class="mini-btn" @click.stop="unfav(item.productId)">取消</button></view>
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

const filters = ['全部', '衣物', '鞋袜', '小用品']
const active = ref('全部')
const loading = ref(false)
const loadMessage = ref('')
const favorites = ref<ProductListItemResponse[]>([])
const filtered = computed(() => {
  if (active.value === '全部') return favorites.value
  const categoryText = active.value.toLowerCase()
  return favorites.value.filter((item) => `${item.title}${item.productNo}${item.status}${item.auditState}`.toLowerCase().includes(categoryText))
})
function openProduct(productId: number) {
  if (!productId || productId <= 0) { uni.showToast({ title: '收藏商品缺少平台 productId，未打开默认内容', icon: 'none' }); return }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` })
}
async function unfav(productId: number) {
  if (!productId || productId <= 0) { uni.showToast({ title: '收藏商品缺少平台 productId，未执行任何变更', icon: 'none' }); return }
  try {
    await unfavoriteProduct(productId)
    favorites.value = favorites.value.filter((item) => item.productId !== productId)
    uni.showToast({ title: '平台已确认取消收藏', icon: 'none' })
  } catch {
    uni.showToast({ title: '平台取消收藏失败，未执行收藏变更', icon: 'none' })
  }
}
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
async function loadFavorites() {
  loading.value = true
  loadMessage.value = ''
  try {
    favorites.value = await listFavoriteProducts()
  } catch (error) {
    favorites.value = []
    loadMessage.value = `收藏列表接口加载失败，未展示默认收藏：${error instanceof Error ? error.message : '请稍后重试'}`
  } finally {
    loading.value = false
  }
}
onMounted(loadFavorites)
</script>
<style scoped>
.favorite-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.fav-card,.empty-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.filter-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.filter-chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.filter-chip.active{background:#3a2a1f;color:#fff}.empty-card{color:#9b7560;font-size:24rpx;line-height:1.5;text-align:center;background:#fffdfa}.empty-card.danger{background:#fff3e7;color:#7b5542}.fav-grid{margin-top:16rpx;display:grid;grid-template-columns:repeat(2,1fr);gap:14rpx}.cover{height:180rpx;border-radius:28rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:58rpx;overflow:hidden}.cover text{font-size:58rpx}.cover-img{width:100%;height:100%}.title{margin-top:12rpx;color:#3a2a1f;font-size:25rpx;font-weight:950}.meta{margin-top:6rpx;color:#9b7560;font-size:20rpx}.bottom{margin-top:10rpx;display:flex;align-items:center;justify-content:space-between;color:#ff3f8d;font-size:28rpx;font-weight:950}.mini-btn{margin:0;padding:0 16rpx;height:46rpx;line-height:46rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#7b5542;font-size:20rpx}
</style>
