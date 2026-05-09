<template>
  <view class="page-shell search-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 找宝贝</view>
        <view class="page-title">搜索结果</view>
        <view class="page-desc">按关键词筛选后端返回的在售女生衣物鞋袜小用品。</view>
      </view>
      <view class="hero-icon">🔎</view>
    </view>

    <view class="search-box ds-card">
      <text class="icon">🔎</text>
      <input v-model.trim="keyword" placeholder="连衣裙、鞋子、袜子、包包" confirm-type="search" @confirm="applySearch" />
      <button class="mini-btn primary" @click="applySearch">搜索</button>
    </view>

    <view class="filter-row">
      <view v-for="item in categories" :key="item" class="chip tapable" :class="{ active: category === item }" @click="category = item">{{ item }}</view>
    </view>
    <view class="filter-row small">
      <view v-for="item in sorts" :key="item.value" class="chip tapable" :class="{ active: sort === item.value }" @click="sort = item.value">{{ item.label }}</view>
    </view>

    <view class="result-head">
      <view class="section-title">共 {{ filtered.length }} 件后端宝贝</view>
      <view class="section-desc">{{ keyword || '全部关键词' }} · {{ category }}</view>
    </view>

    <view v-if="loading" class="empty ds-card">
      <view class="empty-icon">🔎</view>
      <view class="section-title">搜索加载中...</view>
      <view class="section-desc">正在读取后端商品接口。</view>
    </view>
    <view v-else-if="loadMessage" class="empty ds-card danger">
      <view class="empty-icon">⚠️</view>
      <view class="section-title">商品接口暂时不可用，未展示本地搜索宝贝样例</view>
      <view class="section-desc">{{ loadMessage }}</view>
    </view>
    <view v-else-if="filtered.length === 0" class="empty ds-card">
      <view class="empty-icon">🧺</view>
      <view class="section-title">没有找到后端宝贝</view>
      <view class="section-desc">仅展示后端返回的在售商品，未使用本地搜索样例。</view>
      <button class="primary-btn" @click="goPublish">去上新</button>
    </view>

    <view class="product-list">
      <view v-for="item in filtered" :key="item.productId" class="product-card ds-card tapable" @click="openProduct(item.productId)">
        <view class="cover">
          <image v-if="item.coverImageUrl" class="cover-img" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ iconFor(item.title) }}</text>
        </view>
        <view class="main">
          <view class="title">{{ item.title }}</view>
          <view class="meta">后端商品 · {{ statusLabel(item.status) }} · {{ item.createdAt ? '已同步' : '时间待同步' }}</view>
          <view class="bottom"><text class="price">¥{{ compactPrice(item.price) }}</text><text class="safe">{{ item.visible ? '后端可见' : '待公开' }}</text></view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'

type Sort = 'latest' | 'priceAsc' | 'priceDesc'

const keyword = ref('')
const category = ref('全部')
const sort = ref<Sort>('latest')
const loading = ref(false)
const loadMessage = ref('')
const products = ref<ProductListItemResponse[]>([])
const categories = ['全部', '衣物', '鞋袜', '小用品']
const sorts = [{ label: '最新', value: 'latest' as const }, { label: '低价', value: 'priceAsc' as const }, { label: '高价', value: 'priceDesc' as const }]

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const categoryText = category.value === '全部' ? '' : category.value.toLowerCase()
  let list = products.value.filter((item) => {
    const text = `${item.title}${item.productNo}${item.status}${item.auditState}`.toLowerCase()
    return (!kw || text.includes(kw)) && (!categoryText || text.includes(categoryText))
  })
  if (sort.value === 'priceAsc') list = [...list].sort((a, b) => Number(a.price) - Number(b.price))
  if (sort.value === 'priceDesc') list = [...list].sort((a, b) => Number(b.price) - Number(a.price))
  if (sort.value === 'latest') list = [...list].sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
  return list
})

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  keyword.value = current?.options?.keyword || hash?.get('keyword') || ''
}
function applySearch() {
  loadProducts()
}
function openProduct(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }
function goPublish() { uni.switchTab({ url: '/pages/tabbar/publish/index' }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
async function loadProducts() {
  loading.value = true
  loadMessage.value = ''
  try {
    const remote = await listProducts()
    products.value = remote
  } catch (error) {
    products.value = []
    loadMessage.value = error instanceof Error ? error.message : '搜索商品加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(() => { readQuery(); loadProducts() })
</script>
<style scoped>
.search-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.search-box,.empty,.product-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.search-box{display:flex;align-items:center;gap:12rpx}.search-box input{flex:1;font-size:25rpx;color:#3a2a1f}.mini-btn{margin:0;padding:0 18rpx;height:54rpx;line-height:54rpx;border-radius:999rpx;font-size:21rpx}.mini-btn.primary{background:#ff7a45;color:#fff}.filter-row{margin-top:16rpx;display:flex;gap:12rpx;overflow-x:auto}.chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.chip.active{background:#3a2a1f;color:#fff;border-color:#3a2a1f}.result-head{margin-top:20rpx;display:flex;align-items:flex-end;justify-content:space-between}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.section-desc{margin-top:6rpx;color:#9b7560;font-size:21rpx}.empty{text-align:center}.empty.danger{background:#fff3e7}.empty-icon{font-size:58rpx}.product-card{display:flex;gap:16rpx}.cover{width:126rpx;height:126rpx;border-radius:28rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:46rpx;overflow:hidden}.cover text{font-size:46rpx}.cover-img{width:100%;height:100%}.main{flex:1;min-width:0}.title{color:#3a2a1f;font-size:27rpx;font-weight:950;line-height:1.35}.meta{margin-top:8rpx;color:#9b7560;font-size:22rpx}.bottom{margin-top:12rpx;display:flex;justify-content:space-between}.price{color:#ff3f8d;font-size:31rpx;font-weight:950}.safe{padding:6rpx 12rpx;border-radius:999rpx;background:#fff3e7;color:#ff7a45;font-size:19rpx;font-weight:900}
</style>
