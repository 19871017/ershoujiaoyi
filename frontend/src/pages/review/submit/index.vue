<template>
  <view class="page-shell review-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 交易评价</view>
        <view class="page-title">评价这次交易</view>
        <view class="page-desc">评价提交后以平台订单评价记录为准。</view>
      </view>
      <view class="hero-icon">⭐</view>
    </view>
    <view v-if="errorText" class="status-card ds-card danger">
      <view class="section-title">暂不能评价</view>
      <view class="fail-closed-tip compact">{{ errorText }}</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">评分</view>
          <view class="section-desc">评价只会在后端订单评价接口确认成功后写入。</view>
        </view>
        <view class="order-chip">{{ orderNo || '未选择' }}</view>
      </view>
      <view v-for="item in scores" :key="item.key" class="score-row">
        <text>{{ item.label }}</text>
        <view><text v-for="star in 5" :key="star" class="star tapable" :class="{ active: star <= item.value }" @click="item.value = star">★</text></view>
      </view>
      <textarea v-model.trim="content" class="textarea" maxlength="160" placeholder="说说成色、沟通、发货和整体体验" />
      <view class="fail-closed-tip">仅订单完成后的买家可提交一次评价；提交结果以平台订单评价记录为准。</view>
      <button class="primary-btn" :disabled="submitting || !orderNo" @click="submitReview">{{ submitting ? '提交中...' : '提交评价' }}</button>
    </view>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitOrderReview, type OrderReviewResponse } from '../../../api/modules/order'

const scores = reactive([{ key: 'desc', label: '描述相符', value: 5 }, { key: 'service', label: '沟通服务', value: 5 }, { key: 'ship', label: '发货速度', value: 5 }])
const content = ref('')
const orderNo = ref('')
const errorText = ref('')
const submitting = ref(false)
const backendOrderNoPattern = /^OD-[0-9]{1,10}$/
const backendReviewNoPattern = /^RV-[0-9]{1,10}$/

function decodeRouteValue(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return ''
  }
}

function isValidOrderNo(value: string): boolean {
  return backendOrderNoPattern.test(value)
}

function isValidReviewNo(value: string): boolean {
  return backendReviewNoPattern.test(value)
}

function isValidScore(value: unknown): boolean {
  return Number.isSafeInteger(value) && Number(value) >= 1 && Number(value) <= 5
}

function assertBackendReviewResponse(response: OrderReviewResponse, expectedOrderNo: string): void {
  if (!isValidReviewNo(response.reviewNo)) throw new Error('review submit invalid backend reviewNo')
  if (!isValidOrderNo(response.orderNo)) throw new Error('review submit invalid backend orderNo')
  if (response.orderNo !== expectedOrderNo) throw new Error('review submit orderNo mismatch')
  if (!Number.isSafeInteger(response.reviewerId) || response.reviewerId <= 0) throw new Error('review submit invalid reviewerId')
  if (!Number.isSafeInteger(response.revieweeId) || response.revieweeId <= 0) throw new Error('review submit invalid revieweeId')
  if (!isValidScore(response.descriptionScore) || !isValidScore(response.serviceScore) || !isValidScore(response.shippingScore)) {
    throw new Error('review submit invalid backend score')
  }
}

onLoad((query) => {
  const rawOrderNo = typeof query?.orderNo === 'string' ? decodeRouteValue(query.orderNo) : ''
  if (!isValidOrderNo(rawOrderNo)) {
    orderNo.value = ''
    errorText.value = '缺少有效订单编号，请从订单详情进入评价'
    return
  }
  orderNo.value = rawOrderNo
  errorText.value = ''
})

async function submitReview(): Promise<void> {
  const safeOrderNo = orderNo.value
  if (!isValidOrderNo(safeOrderNo)) return uni.showToast({ title: '缺少有效订单编号，评价未提交', icon: 'none' })
  if (content.value.length < 6) return uni.showToast({ title: '请补充至少6个字评价', icon: 'none' })
  if (submitting.value) return
  submitting.value = true
  try {
    const response = await submitOrderReview(safeOrderNo, {
      descriptionScore: scores[0].value,
      serviceScore: scores[1].value,
      shippingScore: scores[2].value,
      content: content.value
    })
    assertBackendReviewResponse(response, safeOrderNo)
    showReviewSuccessModal(response)
  } catch (error) {
    console.warn('review submit failed', { orderNo: safeOrderNo, error })
    uni.showToast({ title: error instanceof Error ? error.message : '评价提交失败，请确认订单已完成且未重复评价', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function showReviewSuccessModal(response: OrderReviewResponse): void {
  const safeOrderNo = response.orderNo
  const modalOptions = {
    title: '评价已提交',
    content: `评价 ${response.reviewNo} 已写入平台订单评价记录，可回订单详情确认。`,
    showCancel: true,
    confirmText: '查看订单',
    cancelText: '订单列表',
    fail(error: unknown) {
      console.warn('review submit success modal failed', { orderNo: safeOrderNo, reviewNo: response.reviewNo, error })
      redirectAfterReview(safeOrderNo, true)
    },
    success(modal: { confirm?: boolean }) {
      redirectAfterReview(safeOrderNo, modal.confirm === true)
    }
  }
  uni.showModal(modalOptions)
}

function redirectAfterReview(orderNoSnapshot: string, showDetail: boolean): void {
  const target = showDetail ? `/pages/order/detail/index?orderNo=${encodeURIComponent(orderNoSnapshot)}` : '/pages/order/list/index?role=buyer&status=COMPLETED'
  const route = {
    url: target,
    fail(error: unknown) {
      console.warn('review submit redirect failed', { orderNo: orderNoSnapshot, target, error })
      uni.showToast({ title: '评价已提交，但暂时无法打开订单页', icon: 'none' })
    }
  }
  uni.redirectTo(route)
}
</script>
<style scoped>
.review-page {
  min-height: 100vh;
  padding-top: 18rpx;
  padding-bottom: 44rpx;
  background: radial-gradient(circle at 12% 0%, rgba(255, 202, 150, .26), transparent 28%), radial-gradient(circle at 88% 16%, rgba(255, 226, 214, .42), transparent 24%), linear-gradient(180deg, #fff8f0 0%, #fffdfa 55%, #fff5ee 100%);
}

.hero,
.form-card,
.status-card {
  margin-top: 16rpx;
  padding: 22rpx;
  border-color: rgba(255, 217, 189, .78);
  box-shadow: 0 15rpx 30rpx rgba(132, 70, 36, .08);
}

.hero {
  margin-top: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20rpx;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(255, 244, 234, .96));
}

.form-card,
.status-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(255, 248, 242, .97));
}

.status-card.danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.section-desc {
  margin-top: 8rpx;
  color: #8f6b57;
  font-size: 21rpx;
  font-weight: 650;
  line-height: 1.45;
}

.order-chip {
  max-width: 250rpx;
  flex: 0 0 auto;
  padding: 8rpx 14rpx;
  border: 1rpx solid rgba(239, 111, 63, .20);
  border-radius: 999rpx;
  background: #fff3e7;
  color: #df6735;
  font-size: 20rpx;
  font-weight: 950;
  word-break: break-all;
}

.kicker {
  color: #df6735;
  font-size: 22rpx;
  font-weight: 950;
  letter-spacing: .18rpx;
}

.hero-icon {
  width: 82rpx;
  height: 82rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 38rpx;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .17);
  flex: 0 0 auto;
}

.section-title {
  color: #342116;
  font-size: 29rpx;
  font-weight: 950;
  letter-spacing: .16rpx;
}

.score-row {
  min-height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid rgba(255, 217, 189, .52);
  color: #7b5542;
  font-size: 23rpx;
  font-weight: 850;
}

.score-row:last-of-type {
  border-bottom: 0;
}

.star {
  margin-left: 8rpx;
  color: #f2c7ad;
  font-size: 38rpx;
  line-height: 1;
  text-shadow: 0 4rpx 10rpx rgba(132, 70, 36, .06);
}

.star.active {
  color: #ff9f43;
  text-shadow: 0 6rpx 12rpx rgba(255, 159, 67, .18);
}

.textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 180rpx;
  margin-top: 18rpx;
  padding: 18rpx;
  border: 1rpx solid rgba(255, 217, 189, .78);
  border-radius: 24rpx;
  background: linear-gradient(180deg, #fffdf9, #fff8f1);
  color: #342116;
  font-size: 24rpx;
  font-weight: 650;
  line-height: 1.55;
}

.fail-closed-tip {
  margin-top: 14rpx;
  padding: 14rpx 16rpx;
  border-radius: 20rpx;
  background: #fff3e7;
  color: #8f6b57;
  font-size: 22rpx;
  font-weight: 650;
  line-height: 1.5;
}

.fail-closed-tip.compact {
  margin-top: 10rpx;
  background: rgba(255, 255, 255, .72);
  color: #be123c;
}

.primary-btn {
  margin-top: 20rpx;
  background: linear-gradient(135deg, #ef6f3f, #ff8b76);
  color: #fffaf4;
  box-shadow: 0 12rpx 24rpx rgba(255, 122, 69, .16);
}
</style>
