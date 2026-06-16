<template>
  <section class="page-shell product-page">
    <div class="page-title">商品管理</div>
    <div class="page-desc">按真实后台商品记录筛选、定位和处理待审商品。</div>

    <form class="lookup-card" @submit.prevent="loadDetail">
      <label>
        <span>商品 ID</span>
        <input v-model.trim="productId" placeholder="例如 88" />
      </label>
      <button class="primary-btn" :disabled="loadingDetail || !productId">{{ loadingDetail ? '查询中...' : '定位商品' }}</button>
    </form>

    <div class="toolbar product-filter-bar">
      <select v-model="statusFilter" :disabled="loadingList || reviewing">
        <option value="ALL">全部商品状态</option>
        <option value="PENDING_AUDIT">待审核</option>
        <option value="ACTIVE">在售</option>
        <option value="SOLD">已售出</option>
        <option value="OFFLINE">已下架</option>
        <option value="DELETED">已删除</option>
      </select>
      <select v-model="auditStatusFilter" :disabled="loadingList || reviewing">
        <option value="ALL">全部审核状态</option>
        <option value="PENDING">待审核</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已拒绝</option>
      </select>
      <input v-model.trim="keyword" maxlength="64" placeholder="商品号/标题/卖家 ID" :disabled="loadingList || reviewing" @keyup.enter="loadList" />
      <input v-model.number="listLimit" type="number" min="1" max="100" step="1" :disabled="loadingList || reviewing" @keyup.enter="loadList" />
      <button class="secondary-btn" :disabled="loadingList || reviewing" @click="loadList">{{ loadingList ? '加载中...' : '加载商品列表' }}</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>

    <div v-if="loadingList" class="empty">商品列表加载中...</div>
    <div v-else-if="products.length === 0" class="empty">暂无符合筛选条件的商品记录。</div>
    <div v-else class="table-card product-table">
      <table>
        <thead>
          <tr>
            <th>商品</th>
            <th>状态</th>
            <th>审核</th>
            <th>卖家</th>
            <th>价格</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in products" :key="item.productId" :class="{ selected: detail?.productId === item.productId }">
            <td>
              <strong>{{ item.title }}</strong>
              <small>{{ item.productNo || `商品 ${item.productId}` }}</small>
            </td>
            <td><span :class="['status', statusClass(item.status)]">{{ item.status }}</span></td>
            <td><span :class="['status', auditStatusClass(productAuditStatus(item))]">{{ productAuditStatus(item) }}</span></td>
            <td>
              <span>{{ sellerName(item) }}</span>
              <small>ID {{ item.sellerId }}</small>
            </td>
            <td>
              <span>买家 ¥{{ formatPrice(item.price) }}</span>
              <small v-if="item.sellerPrice">卖家底价 ¥{{ formatPrice(item.sellerPrice) }}</small>
            </td>
            <td>{{ item.createdAt || '暂无' }}</td>
            <td><button class="link-btn" :disabled="reviewing" @click="selectProduct(item.productId)">查看</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="loadingDetail" class="empty">商品详情加载中...</div>
    <article v-else-if="detail" class="detail-card product-detail">
      <div class="detail-head">
        <div>
          <strong>{{ detail.title }}</strong>
          <span>{{ detail.productNo || `商品 ${detail.productId}` }} · 卖家 {{ sellerName(detail) }}</span>
        </div>
        <span :class="['status', auditStatusClass(detailAuditStatus)]">{{ detailAuditStatus }}</span>
      </div>

      <dl class="detail-grid">
        <div><dt>商品 ID</dt><dd>{{ detail.productId }}</dd></div>
        <div><dt>商品状态</dt><dd>{{ detail.status }}</dd></div>
        <div><dt>审核状态</dt><dd>{{ detailAuditStatus }}</dd></div>
        <div><dt>卖家</dt><dd>{{ sellerName(detail) }} / {{ detail.sellerId }}</dd></div>
        <div><dt>买家展示价</dt><dd>¥{{ formatPrice(detail.price) }}</dd></div>
        <div><dt>卖家底价</dt><dd>¥{{ formatPrice(detail.sellerPrice || detail.price) }}</dd></div>
        <div><dt>平台加价</dt><dd>{{ markupText(detail) }}</dd></div>
        <div><dt>类目</dt><dd>{{ detail.category || '暂无' }}</dd></div>
        <div><dt>可见性</dt><dd>{{ detail.visible ? '可见' : '不可见' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
      </dl>

      <section class="product-description">
        <strong>商品描述</strong>
        <p>{{ detail.description || '暂无描述' }}</p>
      </section>

      <div v-if="detail.imageUrls?.length" class="media-panel">
        <strong>商品图片</strong>
        <div class="product-images">
          <a v-for="url in detail.imageUrls" :key="url" :href="url" target="_blank" rel="noreferrer">{{ url }}</a>
        </div>
      </div>

      <div class="toolbar review-toolbar">
        <button class="primary-btn" :disabled="reviewing || !canReviewDetail" @click="reviewProduct('approve')">
          {{ reviewing ? '提交中...' : '通过商品审核' }}
        </button>
        <button class="danger-btn" :disabled="reviewing || !canReviewDetail" @click="reviewProduct('reject')">驳回商品审核</button>
        <button class="danger-btn" :disabled="reviewing || !canHideDetail" @click="hideProduct">隐藏商品</button>
        <button class="danger-btn" :disabled="reviewing || !canOfflineDetail" @click="offlineProduct">运营下架</button>
        <button class="danger-btn" :disabled="reviewing || !canDeleteDetail" @click="deleteProduct">删除商品</button>
        <button class="secondary-btn" :disabled="reviewing" @click="openSeller">查看卖家</button>
      </div>
      <p class="safe-note">商品状态以后台接口返回为准；审核动作只提交真实 /api/admin/products* 请求，不在本地伪造成功状态。</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  approveAdminProduct,
  deleteAdminProduct,
  getAdminProductDetail,
  getAdminProductList,
  hideAdminProduct,
  isValidAdminProductId,
  isValidAdminProductKeyword,
  isValidAdminProductModerationReason,
  isValidAdminProductOfflineReason,
  offlineAdminProduct,
  rejectAdminProduct,
  type AdminProductDetail,
  type AdminProductListItem,
  type AdminProductListQuery
} from '../../api'
import { canReviewAudit, useAuthStore } from '../../store/modules/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const productId = ref('')
const statusFilter = ref<NonNullable<AdminProductListQuery['status']>>('ALL')
const auditStatusFilter = ref<NonNullable<AdminProductListQuery['auditStatus']>>('PENDING')
const keyword = ref('')
const listLimit = ref(20)
const loadingList = ref(false)
const loadingDetail = ref(false)
const reviewing = ref(false)
const error = ref('')
const products = ref<AdminProductListItem[]>([])
const detail = ref<AdminProductDetail | null>(null)

const statusValues: NonNullable<AdminProductListQuery['status']>[] = ['ALL', 'PENDING_AUDIT', 'ACTIVE', 'OFFLINE', 'SOLD', 'DELETED']
const auditStatusValues: NonNullable<AdminProductListQuery['auditStatus']>[] = ['ALL', 'PENDING', 'APPROVED', 'REJECTED']
const detailAuditStatus = computed(() => detail.value ? productAuditStatus(detail.value) : 'PENDING')
const canReviewDetail = computed(() => Boolean(detail.value && canReviewAudit(auth.session) && isPendingProduct(detail.value)))
const canHideDetail = computed(() => Boolean(
  detail.value &&
  canReviewAudit(auth.session) &&
  detailAuditStatus.value === 'APPROVED' &&
  detail.value.status !== 'SOLD' &&
  detail.value.status !== 'DELETED' &&
  detail.value.visible === true
))
const canOfflineDetail = computed(() => Boolean(
  detail.value &&
  canReviewAudit(auth.session) &&
  detail.value.status === 'ACTIVE' &&
  detailAuditStatus.value === 'APPROVED' &&
  detail.value.visible === true
))
const canDeleteDetail = computed(() => Boolean(
  detail.value &&
  canReviewAudit(auth.session) &&
  detail.value.status !== 'SOLD' &&
  detail.value.status !== 'DELETED'
))

function productAuditStatus(item: AdminProductListItem | AdminProductDetail) {
  return item.auditStatus || item.auditState || 'PENDING'
}

function isPendingProduct(item: AdminProductListItem | AdminProductDetail) {
  return productAuditStatus(item) === 'PENDING' || item.status === 'PENDING_AUDIT'
}

function sellerName(item: AdminProductListItem | AdminProductDetail) {
  return item.sellerNickname || item.sellerName || item.sellerUserNo || '未命名卖家'
}

function formatPrice(price: number) {
  const value = Number(price)
  if (!Number.isFinite(value)) return '0.00'
  return value.toFixed(2)
}

function markupText(item: AdminProductListItem | AdminProductDetail) {
  const rate = Number(item.platformMarkupRate)
  const amount = Number(item.platformMarkupAmount)
  if (!Number.isFinite(rate) || !Number.isFinite(amount)) return '暂无'
  return `${(rate * 100).toFixed(2)}% / ¥${amount.toFixed(2)}`
}

function statusClass(status: string) {
  if (status === 'ACTIVE' || status === 'SOLD') return 'approved'
  if (status === 'OFFLINE' || status === 'DELETED') return 'rejected'
  return 'pending'
}

function auditStatusClass(status: string) {
  return status.toLowerCase()
}

async function loadList(force = false) {
  if (reviewing.value && !force) return
  loadingList.value = true
  error.value = ''
  products.value = []
  try {
    const safeKeyword = keyword.value.trim()
    if (safeKeyword && !isValidAdminProductKeyword(safeKeyword)) {
      error.value = '商品关键词无效：最多 64 字，不能包含测试占位语义。'
      return
    }
    products.value = await getAdminProductList({
      status: statusFilter.value,
      auditStatus: auditStatusFilter.value,
      keyword: safeKeyword || undefined,
      limit: Number(listLimit.value)
    })
  } catch {
    error.value = '商品列表加载失败，请确认管理员权限、筛选条件与服务状态。'
  } finally {
    loadingList.value = false
  }
}

function selectProduct(id: string | number) {
  productId.value = String(id)
  router.replace({ path: `/products/${encodeURIComponent(String(id))}`, query: route.query })
  loadDetail()
}

async function loadDetail() {
  const safeId = productId.value.trim()
  loadingDetail.value = true
  error.value = ''
  detail.value = null
  if (!isValidAdminProductId(safeId)) {
    error.value = '商品编号无效，请输入正确的商品 ID。'
    loadingDetail.value = false
    return
  }
  try {
    detail.value = await getAdminProductDetail(safeId)
  } catch {
    error.value = '商品详情加载失败，请确认管理员权限与商品编号。'
  } finally {
    loadingDetail.value = false
  }
}

async function reviewProduct(action: 'approve' | 'reject') {
  if (!detail.value || !canReviewDetail.value) return
  const safeId = detail.value.productId
  if (!isValidAdminProductId(safeId)) {
    error.value = '商品编号无效，未提交审核。'
    return
  }
  reviewing.value = true
  error.value = ''
  try {
    await (action === 'approve' ? approveAdminProduct(safeId) : rejectAdminProduct(safeId))
    await loadDetail()
    await loadList(true)
  } catch {
    error.value = '商品审核提交失败，请确认审核权限与商品待审状态。'
  } finally {
    reviewing.value = false
  }
}

async function offlineProduct() {
  if (!detail.value || !canOfflineDetail.value) return
  const safeId = detail.value.productId
  if (!isValidAdminProductId(safeId)) {
    error.value = '商品编号无效，未提交下架。'
    return
  }
  const reason = window.prompt('请输入运营下架原因，便于后续追溯。', '')
  if (reason === null) return
  const safeReason = reason.trim()
  if (!isValidAdminProductOfflineReason(safeReason)) {
    error.value = '商品下架原因无效：不能为空、最多 128 字，不能包含测试占位语义。'
    return
  }
  reviewing.value = true
  error.value = ''
  try {
    await offlineAdminProduct(safeId, { reason: safeReason })
    await loadDetail()
    await loadList(true)
  } catch {
    error.value = '商品下架失败，请确认审核权限、商品状态和订单锁定状态。'
  } finally {
    reviewing.value = false
  }
}

async function hideProduct() {
  if (!detail.value || !canHideDetail.value) return
  const safeId = detail.value.productId
  if (!isValidAdminProductId(safeId)) {
    error.value = '商品编号无效，未提交隐藏。'
    return
  }
  const reason = window.prompt('请输入隐藏原因，商品会从用户端不可见，但保留审核通过记录。', '')
  if (reason === null) return
  const safeReason = reason.trim()
  if (!isValidAdminProductModerationReason(safeReason)) {
    error.value = '商品处理原因无效：不能为空、最多 128 字，不能包含测试占位语义。'
    return
  }
  reviewing.value = true
  error.value = ''
  try {
    await hideAdminProduct(safeId, { reason: safeReason })
    await loadDetail()
    await loadList(true)
  } catch {
    error.value = '商品隐藏失败，请确认审核权限、商品状态和订单锁定状态。'
  } finally {
    reviewing.value = false
  }
}

async function deleteProduct() {
  if (!detail.value || !canDeleteDetail.value) return
  const safeId = detail.value.productId
  if (!isValidAdminProductId(safeId)) {
    error.value = '商品编号无效，未提交删除。'
    return
  }
  const reason = window.prompt('请输入删除原因，商品会软删除并保留后台追溯记录。', '')
  if (reason === null) return
  const safeReason = reason.trim()
  if (!isValidAdminProductModerationReason(safeReason)) {
    error.value = '商品处理原因无效：不能为空、最多 128 字，不能包含测试占位语义。'
    return
  }
  reviewing.value = true
  error.value = ''
  try {
    await deleteAdminProduct(safeId, { reason: safeReason })
    await loadDetail()
    await loadList(true)
  } catch {
    error.value = '商品删除失败，请确认审核权限、商品状态和订单锁定状态。'
  } finally {
    reviewing.value = false
  }
}

function openSeller() {
  const sellerId = detail.value?.sellerId
  if (!sellerId || !/^[1-9]\d*$/.test(String(sellerId))) {
    error.value = '卖家编号无效，未打开用户详情。'
    return
  }
  router.push({ path: `/users/${encodeURIComponent(String(sellerId))}` })
}

onMounted(() => {
  const routeStatus = String(route.query.status || '').trim().toUpperCase() as NonNullable<AdminProductListQuery['status']>
  const routeAuditStatus = String(route.query.auditStatus || '').trim().toUpperCase() as NonNullable<AdminProductListQuery['auditStatus']>
  const routeKeyword = String(route.query.keyword || '').trim()
  const routeLimit = Number(route.query.limit)
  if (statusValues.includes(routeStatus)) statusFilter.value = routeStatus
  if (auditStatusValues.includes(routeAuditStatus)) auditStatusFilter.value = routeAuditStatus
  if (routeKeyword) keyword.value = routeKeyword
  if (Number.isInteger(routeLimit) && routeLimit >= 1 && routeLimit <= 100) listLimit.value = routeLimit
  loadList()
  const routeProductId = String(route.params.productId || '').trim()
  if (routeProductId) {
    productId.value = routeProductId
    loadDetail()
  }
})
</script>

<style scoped>
.product-filter-bar input:not([type='number']) {
  min-width: 220px;
}

.product-table {
  overflow-x: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  margin-bottom: 16px;
}

.product-table table {
  width: 100%;
  border-collapse: collapse;
  min-width: 860px;
}

.product-table th,
.product-table td {
  padding: 13px 14px;
  border-bottom: 1px solid #eef2f7;
  text-align: left;
  vertical-align: top;
}

.product-table th {
  color: #64748b;
  font-size: 12px;
  background: #f8fafc;
}

.product-table td {
  color: #111827;
}

.product-table td strong,
.product-table td span {
  display: block;
}

.product-table td small {
  display: block;
  margin-top: 5px;
  color: #64748b;
}

.product-table tr.selected {
  background: #eff6ff;
}

.product-description {
  display: grid;
  gap: 8px;
  margin-top: 16px;
  padding: 14px;
  border-radius: 14px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}

.product-description p {
  margin: 0;
  color: #4b5563;
  line-height: 1.7;
  white-space: pre-wrap;
}

.product-images {
  display: grid;
  gap: 8px;
}

.product-images a {
  color: #2563eb;
  word-break: break-all;
}

.review-toolbar {
  margin-top: 16px;
}

.danger-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fee2e2;
  color: #b91c1c;
  border-radius: 10px;
  padding: 9px 14px;
  font-weight: 700;
}

.danger-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}
</style>
