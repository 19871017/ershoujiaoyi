<template>
  <section class="page-shell order-page">
    <div class="page-title">订单管理</div>
    <div class="page-desc">按订单编号读取订单详情。</div>

    <form class="lookup-card" @submit.prevent="loadDetail">
      <label>
        <span>订单编号</span>
        <input v-model.trim="orderNo" placeholder="例如 OD-ABC123" />
      </label>
      <button class="primary-btn" :disabled="loading || !orderNo">{{ loading ? '查询中...' : '查询详情' }}</button>
    </form>

    <div class="toolbar">
      <select v-model="statusFilter">
        <option value="ALL">全部订单</option>
        <option value="PENDING_PAY">待支付</option>
        <option value="PAID">已支付</option>
        <option value="SHIPPED">已发货</option>
        <option value="COMPLETED">已完成</option>
        <option value="REFUNDING">售后中</option>
      </select>
      <input v-model.trim="keyword" maxlength="64" placeholder="订单号/商品/用户ID/售后号" @keyup.enter="loadList" />
      <input v-model.number="listLimit" type="number" min="1" max="100" step="1" @keyup.enter="loadList" />
      <button class="secondary-btn" :disabled="listLoading" @click="loadList">{{ listLoading ? '加载中...' : '加载订单列表' }}</button>
    </div>

    <div v-if="list.length" class="table-card">
      <table>
        <thead><tr><th>订单</th><th>商品</th><th>买家/卖家</th><th>状态</th><th>金额</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in list" :key="item.orderNo">
            <td>{{ item.orderNo }}</td>
            <td>{{ item.productTitle }}</td>
            <td>{{ item.buyerId }} / {{ item.sellerId }}</td>
            <td>{{ item.status }}</td>
            <td>¥{{ item.amount }}</td>
            <td><button class="link-btn" @click="selectOrder(item.orderNo)">查看详情</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="empty">订单详情加载中...</div>
    <div v-else-if="!detail" class="empty">请输入订单编号查询详情。</div>

    <article v-else class="detail-card">
      <div class="detail-head">
        <div>
          <strong>{{ detail.orderNo }}</strong>
          <span>{{ detail.productTitle }}</span>
        </div>
        <span :class="['status', detail.status.toLowerCase()]">{{ detail.status }}</span>
      </div>
      <dl class="detail-grid">
        <div><dt>买家 ID</dt><dd>{{ detail.buyerId }}</dd></div>
        <div><dt>卖家 ID</dt><dd>{{ detail.sellerId }}</dd></div>
        <div><dt>商品 ID</dt><dd>{{ detail.productId }}</dd></div>
        <div><dt>订单金额</dt><dd>¥{{ detail.amount }}</dd></div>
        <div><dt>售后编号</dt><dd>{{ detail.afterSalesNo || '暂无' }}</dd></div>
        <div><dt>售后状态</dt><dd>{{ detail.afterSalesStatus || '暂无' }}</dd></div>
        <div><dt>配送方式</dt><dd>{{ detail.shippingType || '以平台记录为准' }}</dd></div>
        <div><dt>物流公司</dt><dd>{{ detail.shippingCompany || '暂无' }}</dd></div>
        <div><dt>物流单号</dt><dd>{{ detail.trackingNo || '暂无' }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ detail.createdAt || '暂无' }}</dd></div>
        <div><dt>付款时间</dt><dd>{{ detail.paidAt || '暂无' }}</dd></div>
        <div><dt>完成时间</dt><dd>{{ detail.completedAt || '暂无' }}</dd></div>
      </dl>
      <div class="toolbar">
        <button v-if="detail.afterSalesNo" class="secondary-btn" @click="openAfterSalesDetail">查看售后详情</button>
        <button class="secondary-btn" @click="openAfterSalesByOrder">按订单追溯售后</button>
      </div>
      <p class="safe-note">订单、支付、发货和售后状态以平台记录为准；本页当前仅查询详情，不提供改价、发货或结算操作。</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminOrderDetail, getAdminOrderList, isValidAdminOrderKeyword, isValidAdminOrderNo, type AdminOrderDetail, type AdminOrderListQuery } from '../../api'
import { afterSalesTraceDetailLocation, afterSalesTraceListLocation } from '../after-sales/after-sales-trace-links'

const route = useRoute()
const router = useRouter()
const orderNo = ref('')
const statusFilter = ref<'ALL' | 'PENDING_PAY' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'REFUNDING'>('ALL')
const keyword = ref('')
const listLimit = ref(20)
const loading = ref(false)
const listLoading = ref(false)
const error = ref('')
const detail = ref<AdminOrderDetail | null>(null)
const list = ref<AdminOrderDetail[]>([])
const statusValues: NonNullable<AdminOrderListQuery['status']>[] = ['ALL', 'PENDING_PAY', 'PAID', 'SHIPPED', 'COMPLETED', 'REFUNDING']

async function loadList() {
  listLoading.value = true
  error.value = ''
  list.value = []
  try {
    const safeKeyword = keyword.value.trim()
    if (safeKeyword && !isValidAdminOrderKeyword(safeKeyword)) {
      error.value = '订单关键词无效：最多 64 字，不能包含测试占位语义。'
      return
    }
    list.value = await getAdminOrderList({ status: statusFilter.value, keyword: safeKeyword || undefined, limit: Number(listLimit.value) })
  } catch {
    error.value = '订单列表加载失败，请确认管理员权限与服务状态。'
  } finally {
    listLoading.value = false
  }
}

function selectOrder(no: string) {
  orderNo.value = no
  loadDetail()
}

async function loadDetail() {
  const safeNo = orderNo.value.trim()
  loading.value = true
  error.value = ''
  detail.value = null
  if (!isValidAdminOrderNo(safeNo)) {
    error.value = '订单编号无效，请输入正确的订单编号。'
    loading.value = false
    return
  }
  try {
    detail.value = await getAdminOrderDetail(safeNo)
  } catch {
    error.value = '订单详情加载失败，请确认管理员权限与订单编号。'
  } finally {
    loading.value = false
  }
}

function openAfterSalesDetail() {
  const location = afterSalesTraceDetailLocation(detail.value?.afterSalesNo)
  if (!location) {
    error.value = '售后编号无效，未打开售后详情。'
    return
  }
  router.push(location)
}

function openAfterSalesByOrder() {
  const location = afterSalesTraceListLocation(detail.value?.orderNo || orderNo.value)
  if (!location) {
    error.value = '订单编号无效，未打开售后追溯。'
    return
  }
  router.push(location)
}

onMounted(() => {
  const routeStatus = String(route.query.status || '').trim().toUpperCase() as NonNullable<AdminOrderListQuery['status']>
  const routeKeyword = String(route.query.keyword || '').trim()
  const routeLimit = Number(route.query.limit)
  if (statusValues.includes(routeStatus)) statusFilter.value = routeStatus
  if (routeKeyword) keyword.value = routeKeyword
  if (Number.isInteger(routeLimit) && routeLimit >= 1 && routeLimit <= 100) listLimit.value = routeLimit
  loadList()
  const routeOrderNo = String(route.params.orderNo || '').trim()
  if (routeOrderNo) {
    orderNo.value = routeOrderNo
    loadDetail()
  }
})
</script>
