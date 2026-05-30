<template>
  <view class="page-shell confirm-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 平台订单创建后再进入支付确认</view>
        <view class="page-title">确认订单</view>
        <view class="page-desc">付款前请确认商品、配送和订单规则；支付、售后和聊天记录以服务端订单状态为准。</view>
      </view>
      <view class="hero-icon">🧾</view>
    </view>

    <view v-if="loading" class="status-card ds-card">
      <view class="status-icon">⌛</view>
      <view>
        <view class="status-title">正在读取商品</view>
        <view class="status-desc">平台正在同步最新商品信息和交易规则。</view>
      </view>
    </view>
    <view v-else-if="errorText" class="status-card ds-card danger">
      <view class="status-icon">!</view>
      <view>
        <view class="status-title">无法确认订单</view>
        <view class="status-desc">{{ errorText }}</view>
      </view>
    </view>

    <template v-else-if="product">
      <view class="goods-card ds-card tapable" @click="openProduct">
        <image v-if="coverImage" class="goods-image" :src="coverImage" mode="aspectFill" />
        <view v-else class="goods-cover">{{ coverIcon(product.title) }}</view>
        <view class="goods-main">
          <view class="goods-title">{{ product.title }}</view>
          <view class="goods-desc">{{ productStatusText }} · {{ product.tradeRule }}</view>
          <view class="goods-price">¥{{ product.price }}</view>
        </view>
        <view class="arrow">›</view>
      </view>

      <view class="address-card ds-card tapable" @click="openAddress">
        <view class="section-title">收货信息</view>
        <template v-if="address">
          <view class="address-name">{{ address.name }} {{ address.mobile }}</view>
          <view class="address-text">{{ address.full }}</view>
        </template>
        <template v-else>
          <view class="address-empty">{{ addressLoadFailed ? '收货信息读取失败' : '请选择收货信息' }}</view>
          <view class="address-text">{{ addressLoadFailed ? '请检查网络或登录状态后重试，快递订单不会在缺少服务端收货信息时提交。' : '快递邮寄需要收货人、手机号和详细地址；正式配送状态以平台订单记录为准。' }}</view>
        </template>
      </view>

      <view class="form-card ds-card">
        <view class="section-title">配送方式</view>
        <view class="ship-row">
          <view v-for="item in deliveryTypes" :key="item.value" class="ship-chip tapable" :class="{ active: deliveryType === item.value }" @click="deliveryType = item.value">
            <view>{{ item.label }}</view>
            <text>{{ item.desc }}</text>
          </view>
        </view>
        <textarea v-model.trim="buyerRemark" class="textarea" maxlength="80" placeholder="给卖家留言，可填写尺码确认、发货提醒或配送偏好" />
      </view>

      <view class="safe-card ds-card">
        <view class="section-title">订单规则确认</view>
        <view class="section-caption">提交后只以服务端创建结果为准，未返回有效订单号不会进入收银台。</view>
        <view v-for="item in confirmItems" :key="item.text" class="confirm-row tapable" @click="item.checked = !item.checked">
          <view class="check" :class="{ active: item.checked }">✓</view>
          <view>{{ item.text }}</view>
        </view>
      </view>

      <view class="amount-card ds-card">
        <view class="section-title">金额明细</view>
        <view class="section-caption">页面展示金额用于确认，下单成功后收银台会再次读取真实订单金额。</view>
        <view class="amount-row"><text>商品金额</text><text>¥{{ product.price }}</text></view>
        <view class="amount-row"><text>配送费用</text><text>卖家发货规则为准</text></view>
        <view class="amount-row total"><text>应付合计</text><text>¥{{ product.price }}</text></view>
      </view>

      <view class="bottom-bar">
        <view>
          <view class="pay-label">应付</view>
          <view class="pay-amount">¥{{ product.price }}</view>
        </view>
        <button class="primary-btn submit" :disabled="submitting || !canSubmit" @click="submitOrder">{{ submitting ? '提交中...' : '提交订单' }}</button>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listAddresses } from '../../../api/modules/address'
import { createOrder } from '../../../api/modules/order'
import { getProductDetail, type ProductDetailResponse } from '../../../api/modules/product'
import {
  assertBackendOrderForCheckout,
  coverIcon,
  decodeRouteValue,
  deliveryTypes,
  isValidBackendProductId,
  productStatusTextFor,
  toShippingAddress,
  validatedProductImageUrl,
  type DeliveryType,
  type ShippingAddress
} from './order-confirm-helpers'

const productId = ref<number | null>(null)
const product = ref<ProductDetailResponse | null>(null)
const address = ref<ShippingAddress | null>(null)
const addressLoadFailed = ref(false)
const loading = ref(false)
const submitting = ref(false)
const errorText = ref('')
const deliveryType = ref<DeliveryType>('EXPRESS')
const buyerRemark = ref('')
const confirmItems = reactive([
  { text: '我已确认商品成色、尺码和瑕疵说明', checked: true },
  { text: '我理解创建订单后仍需进入支付页读取平台订单状态', checked: true },
  { text: '我不会脱离平台私下转账或外部联系', checked: true }
])
const coverImage = computed(() => (product.value?.imageUrls || []).find(validatedProductImageUrl) || '')
const productStatusText = computed(() => productStatusTextFor(product.value))
const canSubmit = computed(() => Boolean(product.value && productId.value && !errorText.value))

function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? (pages[pages.length - 1] as unknown as { options?: Record<string, string> }) : undefined
  const hashParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeProductId = decodeRouteValue('productId', current?.options?.productId || current?.options?.goodsId || hashParams?.get('productId') || hashParams?.get('goodsId') || '')
  if (routeProductId && !isValidBackendProductId(routeProductId)) {
    console.warn('order confirm invalid route productId', { rawLength: routeProductId.length, rawPreview: routeProductId.slice(0, 24) })
  }
  productId.value = isValidBackendProductId(routeProductId) ? Number(routeProductId) : null
}
async function loadDefaultAddress(): Promise<void> {
  addressLoadFailed.value = false
  try {
    const addresses = await listAddresses()
    const selected = addresses.find(item => item.isDefault) || addresses[0]
    address.value = selected ? toShippingAddress(selected) : null
  } catch (error) {
    console.warn('order confirm address load failed', { error })
    address.value = null
    addressLoadFailed.value = true
  }
}
async function loadProduct(): Promise<void> {
  if (!productId.value) {
    errorText.value = '缺少商品编号，请从商品详情页进入确认订单'
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const detail = await getProductDetail(productId.value)
    if (detail.productId !== productId.value) throw new Error('order confirm productId mismatch')
    product.value = detail
    if (!detail.visible || String(detail.auditState).toUpperCase() !== 'APPROVED' || detail.status === 'SOLD') {
      errorText.value = '商品当前不可下单，请返回商品详情刷新状态'
    }
  } catch (error) {
    console.warn('order confirm product load failed', { productId: productId.value, error })
    product.value = null
    errorText.value = error instanceof Error && error.message === 'order confirm productId mismatch' ? '商品信息校验失败，请返回商品详情重新进入确认订单' : '商品信息读取失败'
  } finally {
    loading.value = false
  }
}
function openProduct(): void {
  if (!productId.value) return
  const route = {
    url: `/pages/product/detail/index?productId=${productId.value}`,
    fail(error: unknown) {
      console.warn('order confirm product navigation failed', { productId: productId.value, error })
      uni.showToast({ title: '暂时无法打开商品详情', icon: 'none' })
    }
  }
  uni.navigateTo(route)
}
function openAddress(): void {
  const route = {
    url: '/pages/user/address/index',
    fail(error: unknown) {
      console.warn('order confirm address navigation failed', { error })
      uni.showToast({ title: '暂时无法打开收货地址', icon: 'none' })
    }
  }
  uni.navigateTo(route)
}
async function submitOrder(): Promise<void> {
  if (!product.value || !productId.value) {
    uni.showToast({ title: '商品信息未就绪', icon: 'none' })
    return
  }
  if (deliveryType.value === 'EXPRESS' && !address.value) {
    uni.showToast({ title: '请先选择收货信息', icon: 'none' })
    return
  }
  const missing = confirmItems.find((item) => !item.checked)
  if (missing) {
    uni.showToast({ title: '请先确认订单、支付和售后记录规则', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const safeProductId = productId.value
    const order = await createOrder({ goodsId: safeProductId, acceptedTradeRule: true })
    assertBackendOrderForCheckout(order, safeProductId)
    const amount = String(order.productPrice)
    const route = {
      url: `/pages/payment/checkout/index?orderNo=${encodeURIComponent(order.orderNo)}&amount=${encodeURIComponent(amount)}&productId=${safeProductId}`,
      fail(error: unknown) {
        console.warn('order confirm checkout navigation failed', { orderNo: order.orderNo, productId: safeProductId, error })
        uni.showToast({ title: '订单已创建，但暂时无法进入收银台，请从订单列表继续支付', icon: 'none' })
      },
      complete() {
        submitting.value = false
      }
    }
    uni.navigateTo(route)
  } catch (error) {
    submitting.value = false
    console.warn('order confirm create order failed', { productId: productId.value, error })
    const modalOptions = {
      title: '订单未创建',
      content: error instanceof Error ? error.message : '订单没有提交成功，请检查商品状态或稍后重试；不会进入收银台。',
      showCancel: false,
      fail(modalError: unknown) {
        console.warn('order confirm create failure modal failed', { productId: productId.value, error: modalError })
        uni.showToast({ title: '订单没有提交成功，未进入收银台', icon: 'none' })
      }
    }
    uni.showModal(modalOptions)
  }
}
onMounted(() => {
  readQuery()
  void loadDefaultAddress()
  void loadProduct()
})
</script>

<style scoped lang="scss" src="./style.scss"></style>
