<template>
  <view class="page-shell search-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 找宝贝</view>
        <view class="page-title">搜索结果</view>
        <view class="page-desc">按关键词筛选平台返回的在售女生衣物鞋袜小用品。</view>
      </view>
      <view class="hero-icon">🔎</view>
    </view>

    <view class="search-box ds-card">
      <text class="icon">🔎</text>
      <input :value="keyword" placeholder="连衣裙、鞋子、袜子、包包" confirm-type="search" @input="updateKeyword" @confirm="applySearch" />
      <button class="mini-btn primary" @click="applySearch">搜索</button>
    </view>

    <view class="filter-row">
      <view v-for="item in categories" :key="item" class="chip tapable" :class="{ active: category === item }" @click="category = item">{{ item }}</view>
    </view>
    <view class="filter-row small">
      <view v-for="item in sorts" :key="item.value" class="chip tapable" :class="{ active: sort === item.value }" @click="sort = item.value">{{ item.label }}</view>
    </view>

    <view class="result-head">
      <view class="section-title">共 {{ filtered.length }} 件平台宝贝</view>
      <view class="section-desc">{{ keyword || '全部关键词' }} · {{ category }}</view>
    </view>

    <view v-if="loading" class="empty ds-card">
      <view class="empty-icon">🔎</view>
      <view class="section-title">搜索加载中...</view>
      <view class="section-desc">正在读取平台商品。</view>
    </view>
    <view v-else-if="loadMessage" class="empty ds-card danger">
      <view class="empty-icon">⚠️</view>
      <view class="section-title">商品搜索暂时不可用，请稍后重试</view>
      <view class="section-desc">{{ loadMessage }}</view>
    </view>
    <view v-else-if="filtered.length === 0" class="empty ds-card">
      <view class="empty-icon">🧺</view>
      <view class="section-title">没有找到平台宝贝</view>
      <view class="section-desc">仅展示当前在售宝贝，换个关键词也许会有惊喜。</view>
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
          <view class="meta">平台商品 · {{ statusLabel(item.status) }} · {{ item.createdAt ? '已同步' : '时间待同步' }}</view>
          <view class="bottom"><text class="price">¥{{ compactPrice(item.price) }}</text><text class="safe">{{ item.visible ? '平台可见' : '待公开' }}</text></view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listProducts, type ProductListItemResponse } from '../../../api/modules/product'
import { categories, compactPrice, filterProducts, iconFor, inputValue, sorts, statusLabel, type Sort } from './search-result-helpers'

const keyword = ref('')
const category = ref('全部')
const sort = ref<Sort>('latest')
const loading = ref(false)
const loadMessage = ref('')
const products = ref<ProductListItemResponse[]>([])

const filtered = computed(() => filterProducts(products.value, keyword.value, category.value, sort.value))

function readQuery() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  keyword.value = current?.options?.keyword || hash?.get('keyword') || ''
}
function applySearch() {
  keyword.value = keyword.value.trim()
  loadProducts()
}
function openProduct(productId: number) { uni.navigateTo({ url: `/pages/product/detail/index?productId=${productId}` }) }
function goPublish() { uni.navigateTo({ url: '/pages/product/publish/index' }) }
function updateKeyword(event: unknown) { keyword.value = inputValue(event) }
async function loadProducts() {
  loading.value = true
  loadMessage.value = ''
  try {
    const remote = await listProducts()
    products.value = remote
  } catch {
    products.value = []
    loadMessage.value = '商品暂时加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
onMounted(() => { readQuery(); loadProducts() })
</script>
<style scoped lang="scss" src="./style.scss"></style>
