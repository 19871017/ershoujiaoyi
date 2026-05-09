<template>
  <view class="page-shell publish-page">
    <view class="page-title">上新宝贝</view>
    <view class="page-desc">把身边的衣物、鞋袜、小用品轻松放进小原圈，发布后先进入平台审核。</view>

    <view class="preview-card ds-card">
      <view class="photo-stage tapable" @click="choosePhotos">
        <view v-if="form.imageUrls.length" class="photo-grid">
          <image v-for="url in form.imageUrls" :key="url" class="photo-thumb" :src="url" mode="aspectFill" />
          <view v-if="form.imageUrls.length < 9" class="photo-add">＋</view>
        </view>
        <template v-else>
          <view class="photo-main">＋</view>
          <view>
            <view class="photo-title">添加宝贝图片</view>
            <view class="photo-desc">建议 3-9 张，首图清晰更容易成交</view>
          </view>
        </template>
      </view>

      <view class="category-row">
        <view v-for="item in categories" :key="item" class="category-chip tapable" :class="{ active: form.category === item }" @click="form.category = item">{{ item }}</view>
      </view>

      <view class="form-section">
        <view class="field-label">宝贝标题</view>
        <input v-model.trim="form.title" class="field-input" maxlength="40" placeholder="请填写真实宝贝标题" />
        <view class="field-hint">{{ form.title.length }}/40</view>
      </view>

      <view class="form-section">
        <view class="field-label">宝贝描述</view>
        <textarea v-model.trim="form.description" class="field-textarea" maxlength="240" placeholder="写清楚成色、尺码、穿着次数、是否可同城约看" />
        <view class="field-hint">{{ form.description.length }}/240</view>
      </view>

      <view class="row">
        <view class="form-section half">
          <view class="field-label">售价</view>
          <input v-model.trim="form.price" class="field-input" type="digit" placeholder="¥ 0.00" />
        </view>
        <view class="form-section half">
          <view class="field-label">所在位置</view>
          <input v-model.trim="form.location" class="field-input" maxlength="24" placeholder="填写真实可公开的城市/区域" />
        </view>
      </view>

      <view class="form-section">
        <view class="field-label">成色</view>
        <view class="chips">
          <view v-for="item in conditions" :key="item" class="chip tapable" :class="{ active: form.condition === item }" @click="form.condition = item">{{ item }}</view>
        </view>
      </view>

      <view class="form-section">
        <view class="field-label">交易方式</view>
        <view class="chips">
          <view v-for="chip in tradeOptions" :key="chip" class="chip tapable" :class="{ active: form.tradeRule === chip }" @click="form.tradeRule = chip">{{ chip }}</view>
        </view>
      </view>

      <view class="safe-card">
        <view class="safe-title">🛡️ 上新安全检查</view>
        <view class="safe-line">发布后会进入敏感词、图片审核和交易规则检查，审核通过后展示给买家。</view>
      </view>

      <view v-if="errorMessage" class="error-line">{{ errorMessage }}</view>
      <view v-if="successMessage" class="success-line">{{ successMessage }}</view>
      <button class="primary-btn publish-btn" :disabled="submitting" @click="submitProduct">
        {{ submitting ? '发布中...' : '提交审核并上新' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { createMediaUploadTicket } from '../../../api/modules/media'
import { createProduct } from '../../../api/modules/product'

const categories = ['衣物', '鞋袜', '小用品']
const conditions = ['全新未拆', '几乎全新', '轻微使用', '有瑕疵已说明']
const tradeOptions = ['按平台订单流程交易', '仅公开联系方式内沟通', '可邮寄']

const form = reactive({
  category: '衣物',
  title: '',
  description: '',
  price: '',
  location: '',
  condition: '几乎全新',
  tradeRule: '',
  imageUrls: [] as string[]
})
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

function choosePhotos() {
  const remain = Math.max(1, 9 - form.imageUrls.length)
  uni.chooseImage({
    count: remain,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    async success(result) {
      try {
        for (const path of result.tempFilePaths.slice(0, remain)) {
          if (path.startsWith('local://') || path.includes('placeholder')) {
            throw new Error('图片资料无效，请重新选择')
          }
          const ticket = await createMediaUploadTicket({
            scene: 'PRODUCT_IMAGE',
            contentType: imageContentType(path),
            fileSize: 300_000,
            filename: fileNameFromPath(path)
          })
          form.imageUrls.push(ticket.storageUrl)
        }
        form.imageUrls = form.imageUrls.slice(0, 9)
        showToast(`已生成上传票据 ${form.imageUrls.length} 张，发布后才会进入商品审核`)
      } catch (error) {
        showToast(error instanceof Error ? error.message : '图片上传票据创建失败')
      }
    },
    fail() {
      showToast('未选择图片，可继续编辑宝贝信息')
    }
  })
}

function fileNameFromPath(path: string) {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'product-image.jpg'
  return last.includes('.') ? last : `${last}.jpg`
}

function imageContentType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}

function validateForm() {
  if (!form.title || form.title.length < 4) return '标题至少 4 个字'
  if (!form.description || form.description.length < 10) return '描述至少 10 个字，写清楚成色和尺码'
  const amount = Number(form.price)
  if (!Number.isFinite(amount) || amount <= 0) return '请输入正确售价'
  if (amount > 99999) return '售价不能超过 99999'
  if (!form.category) return '请选择分类'
  if (!form.tradeRule) return '请选择真实支持的交易方式'
  if (form.imageUrls.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/product-image/'))) return '图片需先完成平台上传票据校验'
  return ''
}

async function submitProduct() {
  errorMessage.value = ''
  successMessage.value = ''
  const error = validateForm()
  if (error) {
    errorMessage.value = error
    showToast(error)
    return
  }
  submitting.value = true
  try {
    const product = await createProduct({
      title: `[${form.category}] ${form.title}`,
      description: `${form.description}\n成色：${form.condition}\n位置：${form.location}\n交易方式：${form.tradeRule}`,
      price: Number(form.price).toFixed(2),
      imageUrls: form.imageUrls
    })
    successMessage.value = `已提交审核：${product.productNo}`
    uni.showModal({
      title: '已提交审核',
      content: '宝贝进入平台审核队列，审核通过后会展示给买家。',
      showCancel: true,
      confirmText: '查看通知',
      cancelText: '继续编辑',
      success(result) {
        if (result.confirm) uni.navigateTo({ url: '/pages/notification/index' })
      }
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '发布失败，请稍后重试'
    showToast(errorMessage.value)
  } finally {
    submitting.value = false
  }
}

function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
</script>

<style scoped>
.publish-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.preview-card { margin-top:24rpx; padding:22rpx; border-color:#ffd9bd; }
.photo-stage { min-height:250rpx; padding:26rpx; border-radius:30rpx; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; flex-direction:column; justify-content:space-between; box-shadow:0 14rpx 30rpx rgba(255,122,69,.20); }
.photo-main { width:86rpx; height:86rpx; border-radius:28rpx; background:rgba(255,255,255,.22); display:flex; align-items:center; justify-content:center; font-size:56rpx; font-weight:300; }
.photo-title { font-size:34rpx; font-weight:950; }
.photo-desc { margin-top:10rpx; color:rgba(255,255,255,.84); font-size:24rpx; }
.photo-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:12rpx; width:100%; }
.photo-thumb,.photo-add { height:138rpx; border-radius:22rpx; background:rgba(255,255,255,.20); }
.photo-add { display:flex; align-items:center; justify-content:center; font-size:48rpx; color:#fff; border:2rpx dashed rgba(255,255,255,.55); }
.category-row { margin-top:18rpx; display:flex; gap:12rpx; }
.category-chip { flex:1; padding:15rpx 8rpx; text-align:center; border-radius:999rpx; background:#fff3e7; color:#9b7560; font-size:22rpx; font-weight:950; }
.category-chip.active { background:#ff7a45; color:#fff; box-shadow:0 8rpx 18rpx rgba(255,122,69,.18); }
.form-section { margin-top:18rpx; }
.field-label { margin-bottom:10rpx; color:#9b7560; font-size:22rpx; font-weight:900; }
.field-input,.field-textarea { box-sizing:border-box; width:100%; padding:22rpx; border-radius:22rpx; background:#fffaf6; color:#3a2a1f; font-weight:850; border:1rpx solid #ffd9bd; font-size:27rpx; }
.field-textarea { min-height:150rpx; line-height:1.55; font-weight:650; }
.field-hint { margin-top:8rpx; text-align:right; color:#c49aac; font-size:20rpx; }
.row { display:flex; gap:14rpx; } .half { flex:1; }
.chips { display:flex; flex-wrap:wrap; gap:12rpx; }
.chip { padding:13rpx 18rpx; border-radius:999rpx; background:#fff3e7; color:#9b7560; font-size:22rpx; font-weight:900; }
.chip.active { background:#3a2a1f; color:#fff; }
.safe-card { margin-top:22rpx; padding:20rpx; border-radius:22rpx; background:#fff8e8; border:1rpx solid #ffe5b5; }
.safe-title { color:#b45309; font-weight:950; }
.safe-line { margin-top:8rpx; color:#a16207; font-size:24rpx; line-height:1.5; }
.error-line,.success-line { margin-top:18rpx; padding:16rpx 18rpx; border-radius:18rpx; font-size:24rpx; font-weight:850; }
.error-line { color:#be123c; background:#fff1f2; }
.success-line { color:#15803d; background:#f0fdf4; }
.publish-btn { margin-top:24rpx; width:100%; }
.publish-btn[disabled] { opacity:.62; }
</style>
