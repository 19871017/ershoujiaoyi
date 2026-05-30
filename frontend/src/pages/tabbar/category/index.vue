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

    <view v-if="canPublish" class="publish-fab tapable" @click="goPublishForm">＋</view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'
import { getMyProfile } from '../../../api/modules/user'

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
const canPublish = ref(false)
const publishRoles: readonly string[] = ['SELLER', 'BOTH']
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
function goPublishForm(): void { uni.navigateTo({ url: '/pages/product/publish/index' }) }
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
async function loadPublishPermission(): Promise<void> {
  try {
    const profile = await getMyProfile()
    const role = String(profile.mainRole || '').toUpperCase()
    canPublish.value = Boolean(profile.videoVerified) && publishRoles.includes(role)
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
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '分类宝贝加载失败'
    products.value = []
  } finally {
    loading.value = false
  }
}
onMounted(loadProducts)
onMounted(loadPublishPermission)
</script>

<style scoped lang="scss" src="./style.scss"></style>
