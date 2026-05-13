<template>
  <view class="page-shell closet-page">
    <view class="hero ds-card">
      <view>
        <view class="page-desc">管理在售、审核中和已卖出宝贝。</view>
      </view>
      <view class="hero-icon">👗</view>
    </view>

    <view class="tab-row">
      <view v-for="tab in tabs" :key="tab.value" class="tab-chip tapable" :class="{ active: activeTab === tab.value }" @click="selectTab(tab.value)">{{ tab.label }} {{ count(tab.value) }}</view>
    </view>

    <view v-if="loadError" class="empty-card ds-card">
      <view class="empty-title">卖家商品列表加载失败</view>
      <view class="empty-desc">无法加载平台卖家商品列表，未展示默认商品。</view>
      <button class="retry-btn" @click="loadProducts">重试加载</button>
    </view>

    <view v-else-if="!filtered.length" class="empty-card ds-card">
      <view class="empty-title">暂无平台商品</view>
      <view class="empty-desc">商品列表为空或暂未接入卖家专属筛选，当前不使用固定商品号/默认示例填充。</view>
    </view>

    <view v-else class="product-list">
      <view v-for="item in filtered" :key="item.productNo || item.productId" class="product-card ds-card">
        <view class="cover">{{ productIcon(item) }}</view>
        <view class="main">
          <view class="title">{{ item.title }}</view>
          <view class="meta">{{ item.productNo }} · {{ statusLabel(item.status) }} · {{ auditLabel(item.auditState) }}</view>
          <view class="price">¥{{ item.price }}</view>
          <view class="actions">
            <button class="mini-btn" @click="openDetail(item)">查看</button>
            <button v-if="item.status !== 'SOLD'" class="mini-btn" :disabled="visibilityUpdatingId === item.productId" @click="toggleOnline(item)">{{ item.status === 'ACTIVE' ? '下架' : '上架' }}</button>
            <button class="mini-btn primary" @click="editProduct(item)">编辑</button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listMyProducts, updateProductVisibility, type ProductListItemResponse, type ProductAuditState, type ProductCreateStatus } from '../../api/modules/product'

type TabValue = 'ALL' | ProductCreateStatus
const tabs: Array<{ label: string; value: TabValue }> = [
  { label: '全部', value: 'ALL' },
  { label: '在售', value: 'ACTIVE' },
  { label: '审核中', value: 'created' },
  { label: '已卖出', value: 'SOLD' }
]
const activeTab = ref<TabValue>('ALL')
const products = ref<ProductListItemResponse[]>([])
const loadError = ref(false)
const visibilityUpdatingId = ref<number | null>(null)
const filtered = computed(() => activeTab.value === 'ALL' ? products.value : products.value.filter((item) => item.status === activeTab.value))

onMounted(() => { void loadProducts() })

async function loadProducts() {
  loadError.value = false
  try {
    products.value = await listMyProducts()
  } catch {
    products.value = []
    loadError.value = true
  }
}
function selectTab(value: TabValue) { activeTab.value = value }
function count(value: TabValue) { return value === 'ALL' ? products.value.length : products.value.filter((item) => item.status === value).length }
function statusLabel(status: ProductCreateStatus) { return ({ created: '待审核', PENDING_AUDIT: '待审核', ACTIVE: '在售', OFFLINE: '已下架', SOLD: '已卖出' } as Record<ProductCreateStatus, string>)[status] || '未知状态' }
function auditLabel(auditState: ProductAuditState) { return ({ pending: '待审', PENDING: '待审', APPROVED: '已通过', REJECTED: '已驳回' } as Record<ProductAuditState, string>)[auditState] || '审核状态未知' }
function productIcon(item: ProductListItemResponse) { return item.coverImageUrl ? '🖼️' : '📦' }
function openDetail(item: ProductListItemResponse) {
  if (!item.productId || item.productId <= 0) {
    uni.showToast({ title: '商品缺少平台 productId，未打开页面商品详情', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/detail/index?productId=${item.productId}` })
}
async function toggleOnline(item: ProductListItemResponse) {
  if (!item.productId || item.productId <= 0) {
    uni.showToast({ title: '商品缺少平台 productId，未执行任何商品变更', icon: 'none' })
    return
  }
  if (item.auditState !== 'APPROVED') {
    uni.showToast({ title: '商品未通过平台审核，未执行上下架变更', icon: 'none' })
    return
  }
  visibilityUpdatingId.value = item.productId
  try {
    await updateProductVisibility(item.productId, item.status !== 'ACTIVE')
    await loadProducts()
    uni.showToast({ title: item.status === 'ACTIVE' ? '已按平台记录下架' : '已按平台记录上架', icon: 'none' })
  } catch {
    uni.showToast({ title: '上下架提交失败，未执行商品状态变更', icon: 'none' })
  } finally {
    visibilityUpdatingId.value = null
  }
}
function editProduct(item: ProductListItemResponse) {
  if (!item.productId || item.productId <= 0) {
    uni.showToast({ title: '商品缺少平台 productId，未进入编辑页', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/product/edit/index?productId=${item.productId}` })
}
</script>
<style scoped>
.closet-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.product-card,.empty-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.tab-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.tab-chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.tab-chip.active{background:#3a2a1f;color:#fff;border-color:#3a2a1f}.empty-title{color:#3a2a1f;font-size:28rpx;font-weight:950}.empty-desc{margin-top:10rpx;color:#9b7560;font-size:23rpx;line-height:1.55}.retry-btn{margin-top:18rpx;height:62rpx;line-height:62rpx;border-radius:999rpx;background:#ff7a45;color:#fff;font-size:22rpx;font-weight:950}.product-list{display:flex;flex-direction:column;gap:16rpx}.product-card{display:flex;gap:16rpx}.cover{width:132rpx;height:132rpx;border-radius:28rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:48rpx}.main{flex:1;min-width:0}.title{color:#3a2a1f;font-size:28rpx;font-weight:950}.meta{margin-top:8rpx;color:#9b7560;font-size:22rpx}.price{margin-top:10rpx;color:#ff3f8d;font-size:31rpx;font-weight:950}.actions{margin-top:12rpx;display:flex;gap:10rpx;flex-wrap:wrap}.mini-btn{margin:0;padding:0 18rpx;height:52rpx;line-height:52rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#7b5542;font-size:21rpx;font-weight:900}.mini-btn.primary{background:#ff7a45;color:#fff;border-color:#ff7a45}
</style>
