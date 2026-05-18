<template>
  <view class="page-shell category-page">
    <view class="search-card ds-card">
      <view class="search-icon-wrap"><text class="search-icon">🔎</text></view>
      <input v-model.trim="keyword" class="search-input" placeholder="搜连衣裙、鞋子、袜子、包包" confirm-type="search" @confirm="openSearchResult" />
      <view class="search-action tapable" @click="openSearchResult">搜索</view>
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
        <view class="panel-title">{{ currentGroup.name }}</view>
        <view class="soft-count">{{ filteredProducts.length }} 件</view>
      </view>

      <view v-if="loading" class="state-tip">加载分类宝贝中...</view>
      <view v-else-if="errorText" class="state-tip danger">商品接口暂时不可用，未展示本地分类宝贝样例</view>
      <view v-else-if="products.length === 0" class="state-tip">暂未加载到后端分类宝贝</view>

      <view class="sub-grid compact">
        <view v-for="item in activeItems" :key="item.name" class="sub-item tapable" :class="{ active: subCategory === item.name }" @click="selectSubCategory(item.name)">
          <view class="sub-icon" :class="{ 'wear-icon-wrap': item.iconType }">
            <view v-if="item.iconType === 'bra'" class="wear-icon">
              <view class="bra-cup left" />
              <view class="bra-cup right" />
              <view class="bra-band" />
            </view>
            <view v-else-if="item.iconType === 'panty'" class="wear-icon">
              <view class="panty-waist" />
              <view class="panty-body" />
            </view>
            <view v-else-if="item.iconType === 'bikini'" class="wear-icon">
              <view class="bikini-top">
                <view class="bikini-cup left" />
                <view class="bikini-cup right" />
              </view>
              <view class="bikini-bottom" />
            </view>
            <text v-else>{{ item.icon }}</text>
          </view>
          <view class="sub-name">{{ item.name }}</view>
          <view class="sub-count">{{ subCategoryCount(item.name) }} 件</view>
        </view>
      </view>
    </view>

    <view class="product-section-head">
      <view>
        <view class="product-section-title">精选宝贝</view>
        <view class="product-section-subtitle">按真实在售商品筛选展示</view>
      </view>
      <view class="filter-row">
        <view v-for="item in sortOptions" :key="item.value" class="filter-chip tapable" :class="{ active: sortBy === item.value }" @click="sortBy = item.value">{{ item.label }}</view>
      </view>
    </view>

    <view v-if="filteredProducts.length" class="product-grid">
      <view v-for="item in filteredProducts" :key="item.productId" class="product-card ds-card tapable" @click="openProduct(item.productId)">
        <view class="cover">
          <image v-if="item.coverImageUrl" class="cover-img" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ iconFor(item.title) }}</text>
          <view class="cover-shine"></view>
          <view class="status-chip">{{ statusLabel(item.status) }}</view>
        </view>
        <view class="product-info">
          <view class="product-title">{{ item.title }}</view>
          <view class="product-meta">
            <text class="product-no">{{ shortProductNo(item.productNo) }}</text>
            <text class="product-time">{{ formatPublishTime(item.createdAt) }}</text>
          </view>
          <view class="product-bottom">
            <view class="price">¥{{ compactPrice(item.price) }}</view>
            <view class="detail-pill">去看看</view>
          </view>
        </view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty-card ds-card">
      <view class="empty-icon">🪞</view>
      <view class="empty-title">暂时没找到这个宝贝</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'

const launchReadinessMarkers = [
  '商品暂时不可用，请稍后重试',
  '暂未加载到平台分类宝贝'
]

type WearIconType = 'bra' | 'panty' | 'bikini'
type CategoryItem = { name: string; icon: string; iconType?: WearIconType }
type CategoryGroup = { name: string; icon: string; items: CategoryItem[] }

const groups: CategoryGroup[] = [
  {
    name: '衣物',
    icon: '👗',
    items: [
      { name: '上衣', icon: '胸罩', iconType: 'bra' },
      { name: '下衣', icon: '内裤', iconType: 'panty' },
      { name: '套装', icon: '比基尼', iconType: 'bikini' }
    ]
  },
  {
    name: '鞋袜',
    icon: '👠',
    items: [
      { name: '鞋子', icon: '👠' },
      { name: '袜子', icon: '🧦' }
    ]
  },
  {
    name: '小用品',
    icon: '👜',
    items: [
      { name: '包包', icon: '👜' },
      { name: '帽子', icon: '👒' },
      { name: '饰品', icon: '💍' },
      { name: '小物', icon: '🪞' }
    ]
  }
]

type SortBy = 'new' | 'priceLow'

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
  { label: '低价', value: 'priceLow' }
]

const currentGroup = computed(() => groups.find((item) => item.name === active.value) ?? defaultGroup)
const activeItems = computed(() => currentGroup.value.items)
const categoryKeywords = computed(() => {
  if (subCategory.value) return [subCategory.value]
  return activeItems.value.map((item) => item.name)
})
const filteredProducts = computed(() => {
  const kw = keyword.value.toLowerCase()
  const keywords = categoryKeywords.value.map((item) => item.toLowerCase())
  const list = products.value.filter((item) => {
    const text = productSearchText(item)
    const matchKeyword = !kw || text.includes(kw)
    const matchCategory = keywords.length === 0 || keywords.some((item) => text.includes(item))
    return matchKeyword && matchCategory
  })
  return [...list].sort((a, b) => {
    if (sortBy.value === 'priceLow') return Number(a.price) - Number(b.price)
    return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
  })
})

function productSearchText(item: ProductListItemResponse): string {
  return `${item.title}${item.productNo}${item.status}${item.auditState}`.toLowerCase()
}

function subCategoryCount(name: string) {
  const category = name.toLowerCase()
  return products.value.filter((item) => productSearchText(item).includes(category)).length
}

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
function shortProductNo(productNo: string) { return productNo ? `编号 ${productNo.slice(-5)}` : '平台严选' }
function formatPublishTime(createdAt: string) {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return '刚刚上新'
  const diffHours = Math.max(0, (Date.now() - date.getTime()) / (1000 * 60 * 60))
  if (diffHours < 1) return '刚刚上新'
  if (diffHours < 24) return `${Math.floor(diffHours)}小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays}天前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}
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
.category-page { background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.32),transparent 26%), linear-gradient(180deg,#fff8ef 0%,#fffdfa 48%,#fff5ee 100%); padding-top:16rpx; }
.search-card { min-height:76rpx; padding:0 10rpx 0 14rpx; display:flex; align-items:center; gap:12rpx; border-radius:28rpx; background:rgba(255,255,255,.94); box-shadow:0 18rpx 34rpx rgba(255,122,69,.10); }
.search-icon-wrap { width:46rpx; height:46rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg,#fff3e7,#ffe8ef); }
.search-icon { font-size:24rpx; }
.search-input { flex:1; height:68rpx; color:#3a2a1f; font-size:24rpx; font-weight:700; }
.search-action { flex:none; min-width:82rpx; height:52rpx; border-radius:999rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:22rpx; font-weight:950; background:linear-gradient(135deg,#ff7a45,#ff7aa6); box-shadow:0 10rpx 20rpx rgba(255,122,69,.20); }
.category-pills { margin-top:14rpx; display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:12rpx; }
.pill { min-height:78rpx; border-radius:24rpx; background:rgba(255,255,255,.92); border:1rpx solid rgba(255,217,189,.88); color:#9b7560; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6rpx; font-size:22rpx; font-weight:950; box-shadow:0 10rpx 22rpx rgba(255,122,69,.08); }
.pill-icon { font-size:27rpx; }
.pill.active { color:#fff; border-color:rgba(255,122,69,.72); background:linear-gradient(135deg,#ff7a8f 0%,#ff9f69 100%); transform:translateY(-3rpx); box-shadow:0 14rpx 26rpx rgba(255,122,69,.20); }
.right-panel { margin-top:14rpx; padding:16rpx; border-radius:30rpx; background:linear-gradient(180deg,rgba(255,255,255,.96),rgba(255,247,238,.96)); box-shadow:0 16rpx 32rpx rgba(255,122,69,.10); }
.panel-row { display:flex; align-items:center; justify-content:space-between; gap:18rpx; }
.panel-title { font-size:31rpx; font-weight:950; color:#3a2a1f; letter-spacing:.5rpx; }
.soft-count { flex:none; padding:8rpx 15rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:21rpx; font-weight:950; }
.sub-grid { margin-top:14rpx; display:grid; grid-template-columns:repeat(4, minmax(0,1fr)); gap:10rpx; }
.sub-item { min-height:94rpx; padding:10rpx 6rpx; border-radius:22rpx; background:linear-gradient(180deg,#fffdfa,#fff5ec); text-align:center; border:1rpx solid rgba(255,217,189,.86); box-shadow:inset 0 0 0 1rpx rgba(255,255,255,.58); }
.sub-item.active { border-color:#ff7a45; background:linear-gradient(180deg,#fff8ef,#ffe7dd); box-shadow:0 10rpx 20rpx rgba(255,122,69,.14), inset 0 0 0 1rpx rgba(255,255,255,.75); }
.sub-icon { height:36rpx; display:flex; align-items:center; justify-content:center; font-size:28rpx; }
.wear-icon-wrap { width:54rpx; height:38rpx; margin:0 auto; border-radius:16rpx; background:linear-gradient(135deg,#fff,#ffe8f1); border:1rpx solid rgba(255,122,69,.2); box-shadow:inset 0 0 0 2rpx rgba(255,255,255,.7); }
.wear-icon { position:relative; width:42rpx; height:30rpx; }
.bra-cup { position:absolute; top:7rpx; width:17rpx; height:15rpx; border:3rpx solid #ff7a9d; border-top:0; border-radius:0 0 15rpx 15rpx; background:rgba(255,192,138,.2); }
.bra-cup.left { left:4rpx; transform:rotate(8deg); }
.bra-cup.right { right:4rpx; transform:rotate(-8deg); }
.bra-band { position:absolute; left:6rpx; right:6rpx; bottom:5rpx; height:3rpx; border-radius:999rpx; background:#ffb36b; }
.panty-waist { position:absolute; left:7rpx; right:7rpx; top:7rpx; height:4rpx; border-radius:999rpx; background:#ff7a9d; }
.panty-body { position:absolute; left:10rpx; right:10rpx; top:10rpx; height:15rpx; border-radius:4rpx 4rpx 14rpx 14rpx; background:linear-gradient(180deg,#ffc08a,#ff7a9d); clip-path:polygon(0 0,100% 0,72% 100%,28% 100%); }
.bikini-top { position:absolute; left:6rpx; right:6rpx; top:4rpx; height:13rpx; }
.bikini-cup { position:absolute; top:0; width:13rpx; height:11rpx; border-radius:3rpx 3rpx 10rpx 10rpx; background:#ff7a9d; }
.bikini-cup.left { left:2rpx; transform:rotate(10deg); }
.bikini-cup.right { right:2rpx; transform:rotate(-10deg); }
.bikini-bottom { position:absolute; left:12rpx; right:12rpx; bottom:3rpx; height:11rpx; border-radius:3rpx 3rpx 10rpx 10rpx; background:#ffb36b; clip-path:polygon(0 0,100% 0,74% 100%,26% 100%); }
.sub-name { margin-top:7rpx; color:#3a2a1f; font-size:22rpx; font-weight:950; }
.sub-count { margin-top:4rpx; color:#b9856a; font-size:18rpx; }
.product-section-head { margin-top:20rpx; display:flex; align-items:flex-end; justify-content:space-between; gap:16rpx; }
.product-section-title { color:#3a2a1f; font-size:32rpx; line-height:1.1; font-weight:950; letter-spacing:.4rpx; }
.product-section-subtitle { margin-top:7rpx; color:#b08368; font-size:20rpx; font-weight:800; }
.filter-row { flex:none; display:flex; gap:8rpx; padding:6rpx; border-radius:999rpx; background:rgba(255,255,255,.78); border:1rpx solid rgba(255,217,189,.72); }
.filter-chip { min-width:64rpx; padding:8rpx 12rpx; border-radius:999rpx; color:#9b7560; font-size:21rpx; font-weight:950; text-align:center; }
.filter-chip.active { color:#fff; background:#3a2a1f; box-shadow:0 8rpx 18rpx rgba(58,42,31,.18); }
.product-grid { margin-top:14rpx; display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:16rpx; }
.product-card { padding:10rpx; border-radius:30rpx; overflow:hidden; background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,247,238,.98)); border-color:rgba(255,217,189,.82); box-shadow:0 18rpx 30rpx rgba(255,122,69,.10); }
.cover { position:relative; height:188rpx; border-radius:24rpx; overflow:hidden; background:linear-gradient(135deg,#fff3e7,#ffe5ef); display:flex; align-items:center; justify-content:center; }
.cover text { font-size:54rpx; }
.cover-img { width:100%; height:100%; }
.cover-shine { position:absolute; inset:0; pointer-events:none; background:linear-gradient(180deg,rgba(255,255,255,.10) 0%,rgba(255,255,255,0) 45%,rgba(58,42,31,.16) 100%); }
.status-chip { position:absolute; right:12rpx; top:12rpx; padding:6rpx 13rpx; border-radius:999rpx; color:#ff6f45; background:rgba(255,255,255,.92); font-size:18rpx; font-weight:950; box-shadow:0 8rpx 18rpx rgba(58,42,31,.12); }
.state-tip { margin-top:18rpx; padding:18rpx; border-radius:22rpx; background:#fff3e7; color:#9b7560; font-size:23rpx; }
.state-tip.danger { color:#b45374; }
.product-info { padding:12rpx 5rpx 4rpx; }
.product-title { color:#3a2a1f; font-size:25rpx; font-weight:950; line-height:1.34; min-height:66rpx; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.product-meta { margin-top:9rpx; display:flex; align-items:center; justify-content:space-between; gap:8rpx; color:#b08368; font-size:18rpx; font-weight:800; }
.product-no,.product-time { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.product-no { max-width:96rpx; }
.product-time { flex:none; }
.product-bottom { margin-top:12rpx; display:flex; align-items:center; justify-content:space-between; gap:8rpx; }
.price { color:#ff3f8d; font-size:31rpx; line-height:1; font-weight:950; letter-spacing:-.5rpx; }
.detail-pill { flex:none; padding:7rpx 12rpx; border-radius:999rpx; color:#ff7a45; background:#fff2e8; font-size:19rpx; font-weight:950; }
.empty-card { margin-top:20rpx; padding:28rpx 20rpx; text-align:center; }
.empty-icon { font-size:46rpx; }
.empty-title { margin-top:12rpx; color:#3a2a1f; font-size:27rpx; font-weight:950; }
</style>
