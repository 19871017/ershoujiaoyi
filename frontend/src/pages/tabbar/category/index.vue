<template>
  <view class="page-shell category-page">
    <view class="cute-head ds-card">
      <view>
        <view class="kicker">♡ 小原圈宝贝</view>
        <view class="page-title">在小原圈挑好物</view>
        <view class="page-desc">衣物、鞋袜、小用品，只放女孩子常逛的小宝贝。</view>
      </view>
      <view class="head-badge">🎀</view>
    </view>

    <view class="search-card ds-card">
      <text class="search-icon">🔎</text>
      <input v-model.trim="keyword" class="search-input" placeholder="搜连衣裙、鞋子、袜子、包包" confirm-type="search" @confirm="openSearchResult" />
    </view>

    <view class="category-pills">
      <view
        v-for="item in groups"
        :key="item.name"
        class="pill tapable"
        :class="{ active: active === item.name }"
        @click="selectGroup(item.name)"
      >
        <text class="pill-icon">{{ item.icon }}</text>
        <text>{{ item.name }}</text>
      </view>
    </view>

    <view class="right-panel ds-card">
      <view class="panel-row">
        <view>
          <view class="panel-title">{{ currentGroup.name }}</view>
          <view class="panel-sub">{{ currentGroup.desc }}</view>
        </view>
        <view class="soft-count">{{ filteredProducts.length }} 件</view>
      </view>

      <view v-if="loading" class="state-tip">加载分类宝贝中...</view>
      <view v-else-if="errorText" class="state-tip danger">商品接口暂时不可用，未展示本地分类宝贝样例</view>
      <view v-else-if="products.length === 0" class="state-tip">暂未加载到后端分类宝贝</view>

      <view class="sub-grid compact">
        <view v-for="item in activeItems" :key="item.name" class="sub-item tapable" :class="{ active: subCategory === item.name }" @click="selectSubCategory(item.name)">
          <view class="sub-icon">{{ item.icon }}</view>
          <view class="sub-name">{{ item.name }}</view>
          <view class="sub-count">{{ item.count }} 件</view>
        </view>
      </view>
    </view>

    <view class="filter-row">
      <view v-for="item in sortOptions" :key="item.value" class="filter-chip tapable" :class="{ active: sortBy === item.value }" @click="sortBy = item.value">{{ item.label }}</view>
      <view class="filter-chip tapable" @click="openSearchResult">查看搜索结果</view>
    </view>

    <view v-if="filteredProducts.length" class="product-grid">
      <view v-for="item in filteredProducts" :key="item.productId" class="product-card ds-card tapable" @click="openProduct(item.productId)">
        <view class="cover">
          <image v-if="item.coverImageUrl" class="cover-img" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ iconFor(item.title) }}</text>
        </view>
        <view class="product-title">{{ item.title }}</view>
        <view class="product-meta">后端商品 · {{ statusLabel(item.status) }} · {{ item.createdAt ? '已同步' : '时间待同步' }}</view>
        <view class="product-bottom">
          <view class="price">¥{{ compactPrice(item.price) }}</view>
          <view class="distance">{{ item.visible ? '可查看' : '待公开' }}</view>
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-card ds-card">
      <view class="empty-icon">🪞</view>
      <view class="empty-title">暂时没找到这个宝贝</view>
      <view class="empty-desc">仅展示后端返回的在售商品，未使用本地分类样例。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'

const groups = [
  {
    name: '衣物',
    icon: '👗',
    desc: '裙子、上衣、外套、套装',
    items: [
      { name: '连衣裙', icon: '👗', count: 28 },
      { name: '上衣', icon: '🎽', count: 34 },
      { name: '外套', icon: '🧥', count: 18 },
      { name: '套装', icon: '🧸', count: 12 }
    ]
  },
  {
    name: '鞋袜',
    icon: '👠',
    desc: '鞋子和袜子放一起，入口更少更直接',
    items: [
      { name: '玛丽珍鞋', icon: '👠', count: 16 },
      { name: '运动鞋', icon: '👟', count: 20 },
      { name: '短袜', icon: '🧦', count: 22 },
      { name: '长袜', icon: '🎀', count: 15 }
    ]
  },
  {
    name: '小用品',
    icon: '👜',
    desc: '包包、帽子、饰品、女生小物',
    items: [
      { name: '包包', icon: '👜', count: 19 },
      { name: '帽子', icon: '👒', count: 10 },
      { name: '饰品', icon: '💍', count: 21 },
      { name: '小物', icon: '🪞', count: 14 }
    ]
  }
]

type SortBy = 'new' | 'priceLow' | 'near'

const defaultGroup = groups[0]!
const active = ref(defaultGroup.name)
const subCategory = ref('')
const keyword = ref('')
const sortBy = ref<SortBy>('new')
const loading = ref(false)
const errorText = ref('')
const products = ref<ProductListItemResponse[]>([])
const sortOptions: Array<{ label: string; value: SortBy }> = [
  { label: '最新', value: 'new' },
  { label: '低价', value: 'priceLow' },
  { label: '同城近', value: 'near' }
]

const currentGroup = computed(() => groups.find((item) => item.name === active.value) ?? defaultGroup)
const activeItems = computed(() => currentGroup.value.items)
const filteredProducts = computed(() => {
  const kw = keyword.value.toLowerCase()
  const categoryWords = [active.value, subCategory.value].filter(Boolean).join('')
  const list = products.value.filter((item) => {
    const text = `${item.title}${item.productNo}${item.status}${item.auditState}`.toLowerCase()
    const matchKeyword = !kw || text.includes(kw)
    const matchCategory = !categoryWords || text.includes(categoryWords.toLowerCase())
    return matchKeyword && matchCategory
  })
  return [...list].sort((a, b) => {
    if (sortBy.value === 'priceLow') return Number(a.price) - Number(b.price)
    if (sortBy.value === 'near') return a.productId - b.productId
    return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
  })
})

function selectGroup(name: string) {
  active.value = name
  subCategory.value = ''
}
function selectSubCategory(name: string) {
  subCategory.value = subCategory.value === name ? '' : name
}
function openSearchResult() { uni.navigateTo({ url: `/pages/search/result/index?keyword=${encodeURIComponent(keyword.value || active.value)}` }) }
function openProduct(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function statusLabel(status: string) { return status === 'created' || status === 'ACTIVE' ? '在售' : status }
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
async function loadProducts() {
  loading.value = true
  errorText.value = ''
  try {
    const remote = await listProducts()
    products.value = remote
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '分类宝贝加载失败'
    products.value = []
  } finally {
    loading.value = false
  }
}
onMounted(loadProducts)
</script>

<style scoped>
.category-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.cute-head { padding:26rpx; display:flex; align-items:center; justify-content:space-between; gap:18rpx; background:linear-gradient(135deg,#fff 0%,#ffe8f2 100%); }
.kicker { display:inline-flex; padding:8rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:22rpx; font-weight:950; }
.head-badge { width:88rpx; height:88rpx; border-radius:32rpx; background:#ff7a45; color:#fff; display:flex; align-items:center; justify-content:center; font-size:42rpx; box-shadow:0 12rpx 28rpx rgba(255,122,69,.24); }
.search-card { margin-top:18rpx; padding:0 18rpx; min-height:80rpx; display:flex; align-items:center; gap:12rpx; }
.search-icon { font-size:28rpx; }
.search-input { flex:1; height:80rpx; color:#3a2a1f; font-size:26rpx; }
.category-pills { margin-top:22rpx; display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:14rpx; }
.pill { min-height:96rpx; border-radius:30rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6rpx; font-size:22rpx; font-weight:900; box-shadow:0 8rpx 20rpx rgba(255,122,69,.08); }
.pill-icon { font-size:34rpx; }
.pill.active { color:#fff; border-color:#ff7a45; background:linear-gradient(135deg,#ff7ab0,#ffb08a); transform:translateY(-3rpx); }
.right-panel { margin-top:22rpx; padding:24rpx; }
.panel-row { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; }
.panel-title { font-size:38rpx; font-weight:950; color:#3a2a1f; }
.panel-sub { margin-top:8rpx; color:#9b7560; font-size:24rpx; line-height:1.45; }
.soft-count { flex:none; padding:10rpx 16rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:22rpx; font-weight:900; }
.sub-grid { margin-top:24rpx; display:grid; grid-template-columns:repeat(4, minmax(0,1fr)); gap:12rpx; }
.sub-item { min-height:116rpx; padding:14rpx 8rpx; border-radius:24rpx; background:linear-gradient(180deg,#fffdfa,#fff3e7); text-align:center; border:1rpx solid #ffd9bd; }
.sub-item.active { border-color:#ff7a45; box-shadow:0 8rpx 18rpx rgba(255,122,69,.16); }
.sub-icon { font-size:34rpx; }
.sub-name { margin-top:6rpx; color:#3a2a1f; font-size:22rpx; font-weight:950; }
.sub-count { margin-top:4rpx; color:#b9856a; font-size:18rpx; }
.filter-row { margin-top:18rpx; display:flex; gap:12rpx; }
.filter-chip { padding:13rpx 22rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#9b7560; font-size:22rpx; font-weight:900; }
.filter-chip.active { color:#fff; background:#3a2a1f; border-color:#3a2a1f; }
.product-grid { margin-top:18rpx; display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:16rpx; }
.product-card { padding:14rpx; }
.cover { height:170rpx; border-radius:24rpx; background:linear-gradient(135deg,#fff3e7,#ffe5ef); display:flex; align-items:center; justify-content:center; }
.cover text { font-size:58rpx; }
.cover-img { width:100%; height:100%; }
.state-tip { margin-top:18rpx; padding:18rpx; border-radius:22rpx; background:#fff3e7; color:#9b7560; font-size:23rpx; }
.state-tip.danger { color:#b45374; }
.product-title { margin-top:12rpx; color:#3a2a1f; font-size:25rpx; font-weight:950; line-height:1.35; min-height:68rpx; }
.product-meta { margin-top:6rpx; color:#b9856a; font-size:20rpx; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.product-bottom { margin-top:12rpx; display:flex; align-items:center; justify-content:space-between; gap:8rpx; }
.price { color:#ff3f8d; font-size:30rpx; font-weight:950; }
.distance { color:#9b7560; font-size:20rpx; }
.empty-card { margin-top:20rpx; padding:40rpx 24rpx; text-align:center; }
.empty-icon { font-size:58rpx; }
.empty-title { margin-top:12rpx; color:#3a2a1f; font-size:30rpx; font-weight:950; }
.empty-desc { margin-top:8rpx; color:#9b7560; font-size:24rpx; }
</style>
