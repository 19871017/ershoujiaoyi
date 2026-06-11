<template>
  <view class="page-shell category-page">
    <view class="search-card ds-card">
      <view class="search-icon-wrap"><text class="search-icon">🔎</text></view>
      <input :value="keyword" class="search-input" placeholder="搜全部在售宝贝" confirm-type="search" @input="updateKeyword" @confirm="openSearchResult" />
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

    <view class="right-panel">
      <view class="panel-row">
        <view class="panel-title">{{ currentGroup.name }}</view>
        <view class="soft-count">{{ filteredProducts.length }} 件</view>
      </view>

      <view v-if="loading" class="state-tip">加载分类宝贝中...</view>
      <view v-else-if="errorText" class="state-tip danger">{{ errorText }}</view>
      <view v-else-if="products.length === 0" class="state-tip">{{ emptyCategoryText }}</view>

      <view v-if="activeItems.length" class="sub-grid compact">
        <view v-for="item in activeItems" :key="item.name" class="sub-item tapable" :class="{ active: subCategory === item.name }" @click="selectSubCategory(item.name)">
          <view class="sub-icon"><text>{{ item.icon }}</text></view>
          <view class="sub-name">{{ item.name }}</view>
          <view class="sub-count">{{ subCategoryCount(item.name) }} 件</view>
        </view>
      </view>
    </view>

    <view class="product-section-head">
      <view>
        <view class="product-section-title">精选宝贝</view>
        <view class="product-section-subtitle">只看当前在售，喜欢就去看看</view>
      </view>
      <view class="product-section-actions">
        <view class="filter-row">
          <view v-for="item in sortOptions" :key="item.value" class="filter-chip tapable" :class="{ active: sortBy === item.value }" @click="sortBy = item.value">{{ item.label }}</view>
        </view>
        <view v-if="canPublish" class="publish-inline tapable" @click="goPublishForm">＋</view>
      </view>
    </view>

    <view v-if="filteredProducts.length" class="product-grid">
      <view v-for="item in filteredProducts" :key="item.productId" class="product-card tapable" @click="openProduct(item.productId)">
        <view class="cover">
          <image v-if="item.coverImageUrl" class="cover-img" :src="item.coverImageUrl" mode="aspectFill" />
          <text v-else>{{ iconFor(item.title) }}</text>
          <view class="cover-shine"></view>
          <view class="status-chip">{{ statusLabel(item.status) }}</view>
        </view>
        <view class="product-info">
          <view class="product-title">{{ item.title }}</view>
          <view class="product-meta">
            <text class="product-no">{{ productFreshness(item.createdAt) }}</text>
            <text class="product-time">{{ formatPublishTime(item.createdAt) }}</text>
          </view>
          <view class="product-bottom">
            <view class="price">¥{{ compactPrice(item.price) }}</view>
            <view class="detail-pill">去看看</view>
          </view>
        </view>
      </view>
    </view>

    <view v-else-if="!loading && !errorText && products.length > 0" class="empty-card ds-card category-empty">
      <view class="empty-icon">🪞</view>
      <view class="empty-title">暂时没找到这个宝贝</view>
      <view class="empty-copy">换个关键词，或恢复到全部在售宝贝看看。</view>
      <view class="empty-actions">
        <view class="empty-action primary tapable" @click="resetFilters">查看全部宝贝</view>
        <view v-if="keyword" class="empty-action tapable" @click="clearKeyword">清空搜索</view>
      </view>
    </view>

    <view v-else-if="!loading && !errorText && products.length === 0" class="empty-card ds-card category-empty">
      <view class="empty-icon">✨</view>
      <view class="empty-title">暂时还没有在售宝贝</view>
      <view class="empty-copy">可以稍后刷新看看，新的宝贝上架后会直接出现在这里。</view>
      <view class="empty-actions">
        <view class="empty-action primary tapable" @click="refreshCategoryData(true)">刷新看看</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'
import { getMyProfile } from '../../../api/modules/user'

const loadErrorText = '商品暂时不可用，请稍后再来看看'
const emptyCategoryText = '这里暂时还没有上架宝贝'

type CategoryItem = { name: string; icon: string; keywords?: string[] }
type CategoryGroup = { name: string; icon: string; items: CategoryItem[] }

const groups: CategoryGroup[] = [
  {
    name: '全部',
    icon: '✨',
    items: []
  },
  {
    name: '衣物',
    icon: '👗',
    items: [
      { name: '上衣', icon: '👚', keywords: ['上衣', '外套', '衬衫', '针织'] },
      { name: '下装', icon: '👖', keywords: ['下装', '下衣', '裤', '裙'] },
      { name: '套装', icon: '🧥', keywords: ['套装', '成套', '两件套'] }
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
const canPublish = ref(false)
const didMount = ref(false)
const categoryRefreshing = ref(false)
const publishRoles: readonly string[] = ['SELLER', 'BOTH']
const sortOptions: Array<{ label: string; value: SortBy }> = [
  { label: '最新', value: 'new' },
  { label: '低价', value: 'priceLow' }
]

const currentGroup = computed(() => groups.find((item) => item.name === active.value) ?? defaultGroup)
const activeItems = computed(() => currentGroup.value.items)
const categoryKeywords = computed(() => {
  if (currentGroup.value.name === defaultGroup.name) return []
  if (subCategory.value) {
    const selected = activeItems.value.find((item) => item.name === subCategory.value)
    return selected ? itemKeywords(selected) : [subCategory.value]
  }
  return activeItems.value.flatMap((item) => itemKeywords(item))
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

function itemKeywords(item: CategoryItem): string[] { return item.keywords ?? [item.name] }
function productSearchText(item: ProductListItemResponse): string { return `${item.title}${item.category ?? ''}${item.productNo}`.toLowerCase() }

function subCategoryCount(name: string) {
  const selected = activeItems.value.find((item) => item.name === name)
  const keywords = (selected ? itemKeywords(selected) : [name]).map((item) => item.toLowerCase())
  return products.value.filter((item) => {
    const text = productSearchText(item)
    return keywords.some((category) => text.includes(category))
  }).length
}

function selectGroup(name: string) {
  active.value = name
  subCategory.value = ''
}
function selectSubCategory(name: string) {
  subCategory.value = subCategory.value === name ? '' : name
}
function resetFilters(): void {
  active.value = defaultGroup.name
  subCategory.value = ''
  keyword.value = ''
}
function clearKeyword(): void {
  keyword.value = ''
}
function updateKeyword(event: unknown): void {
  const value = (event as { detail?: { value?: unknown } }).detail?.value
  if (typeof value !== 'string') {
    console.warn('category keyword input invalid')
    return
  }
  keyword.value = value.trim()
}
function openSearchResult() { uni.navigateTo({ url: `/pages/search/result/index?keyword=${encodeURIComponent(keyword.value)}` }) }
function openProduct(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }
function goPublishForm(): void { uni.navigateTo({ url: '/pages/product/publish/index' }) }
function iconFor(title: string) { if (title.includes('裙')) return '👗'; if (title.includes('鞋')) return '👠'; if (title.includes('袜')) return '🧦'; return '👜' }
function statusLabel(status: string) {
  if (status === 'created' || status === 'ACTIVE') return '在售'
  if (status === 'SOLD') return '已出'
  if (status === 'OFFLINE') return '已下架'
  if (status === 'PENDING_AUDIT') return '待上架'
  return '更新中'
}
function compactPrice(price: string) { return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 }) }
function productFreshness(createdAt: string) {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return '新鲜上架'
  const diffHours = Math.max(0, (Date.now() - date.getTime()) / (1000 * 60 * 60))
  if (diffHours < 24) return '今日上新'
  if (diffHours < 24 * 7) return '本周上新'
  return '近期上新'
}
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
async function loadPublishPermission(): Promise<void> {
  try {
    const profile = await getMyProfile()
    const role = String(profile.mainRole || '').toUpperCase()
    const videoStatus = String(profile.videoIdentityStatus || '').toUpperCase()
    canPublish.value = profile.videoVerified === true && videoStatus === 'APPROVED' && publishRoles.includes(role)
  } catch {
    canPublish.value = false
  }
}

async function loadProducts() {
  loading.value = true
  errorText.value = ''
  try {
    const remote = await listProducts()
    products.value = remote
  } catch {
    errorText.value = loadErrorText
    products.value = []
  } finally {
    loading.value = false
  }
}
async function refreshCategoryData(showLoading = false): Promise<void> {
  if (categoryRefreshing.value) return
  categoryRefreshing.value = true
  try {
    if (showLoading) await Promise.all([loadProducts(), loadPublishPermission()])
    else await Promise.all([loadProducts(), loadPublishPermission()])
  } finally {
    categoryRefreshing.value = false
  }
}
onMounted(() => {
  didMount.value = true
  void refreshCategoryData(true)
})
onShow(() => {
  if (!didMount.value) return
  void refreshCategoryData(false)
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
