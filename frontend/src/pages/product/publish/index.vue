<template>
  <view class="page-shell publish-page">
    <view class="preview-card ds-card">
      <view class="photo-stage tapable" @click="choosePhotos">
        <view v-if="form.imageUrls.length" class="photo-grid">
          <image v-for="url in form.imageUrls" :key="url" class="photo-thumb" :src="url" mode="aspectFill" />
          <view v-if="form.imageUrls.length < 9" class="photo-add">＋</view>
        </view>
        <template v-else>
          <view class="photo-main">＋</view>
          <view>
            <view class="photo-title">添加图片</view>
          </view>
        </template>
      </view>

      <view class="category-row">
        <view v-for="item in categories" :key="item" class="category-chip tapable" :class="{ active: form.category === item }" @click="form.category = item">{{ item }}</view>
      </view>

      <view class="form-section">
        <view class="field-label">标题</view>
        <input v-model="form.title" class="field-input" maxlength="40" placeholder="填写标题" confirm-type="next" @blur="trimTextField('title')" />
      </view>

      <view class="form-section">
        <view class="field-label">描述</view>
        <textarea v-model.trim="form.description" class="field-textarea" maxlength="240" placeholder="填写描述" />
      </view>

      <view class="row">
        <view class="form-section half">
          <view class="field-label">售价</view>
          <input v-model="form.price" class="field-input" type="text" inputmode="decimal" placeholder="¥ 0.00" confirm-type="next" @blur="normalizePrice" />
        </view>
        <view class="form-section half">
          <view class="field-label">位置</view>
          <input v-model="form.location" class="field-input" :maxlength="locationMaxLength" placeholder="城市/区域" confirm-type="done" @blur="trimTextField('location')" />
          <view class="location-actions">
            <button class="mini-btn" :disabled="!profileCity" @click="useProfileCity">资料城市</button>
            <button class="mini-btn" :disabled="locating" @click="detectCurrentCity">{{ locating ? '定位中' : '定位' }}</button>
          </view>
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

      <view v-if="errorMessage" class="error-line">{{ errorMessage }}</view>
      <view v-if="successMessage" class="success-line">{{ successMessage }}</view>
      <button class="primary-btn publish-btn" :disabled="submitting" @click="submitProduct">
        {{ submitting ? '发布中...' : '提交审核' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { reverseGeocode } from '../../../api/modules/location'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { createProduct } from '../../../api/modules/product'
import { getMyProfile, type UserProfileResponse } from '../../../api/modules/user'

const categories = ['衣物', '鞋袜', '小用品']
const conditions = ['全新未拆', '几乎全新', '轻微使用', '有瑕疵已说明']
const platformTradeRule = '按平台订单流程交易'
const tradeOptions = [platformTradeRule]
const locationMaxLength = 24

const form = reactive({
  category: '衣物',
  title: '',
  description: '',
  price: '',
  location: '',
  condition: '几乎全新',
  tradeRule: platformTradeRule,
  imageUrls: [] as string[]
})
const submitting = ref(false)
const locating = ref(false)
const profileCity = ref('')
const publishReady = ref(false)
const publishBlockMessage = ref('请先完成卖家认证')
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
          const uploaded = await uploadMediaTicketFile(ticket, path)
          form.imageUrls.push(uploaded.storageUrl)
        }
        form.imageUrls = form.imageUrls.slice(0, 9)
        showToast(`已生成上传票据 ${form.imageUrls.length} 张`)
      } catch (error) {
        showToast(error instanceof Error ? error.message : '图片上传票据创建失败')
      }
    },
    fail() {
      showToast('未选择图片')
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

type TextFieldKey = 'title' | 'location'
type GetLocationResult = { latitude: number; longitude: number }

function trimTextField(field: TextFieldKey) {
  form[field] = form[field].trim()
}

function normalizePrice() {
  const value = form.price
    .replace(/[^\d.]/g, '')
    .replace(/(\.\d{2}).+$/, '$1')
  const dotIndex = value.indexOf('.')
  form.price = dotIndex === -1 ? value : `${value.slice(0, dotIndex + 1)}${value.slice(dotIndex + 1).replace(/\./g, '')}`
}

function getCurrentLocation() {
  return new Promise<GetLocationResult>((resolve, reject) => {
    uni.getLocation({ type: 'wgs84', success: resolve, fail: reject })
  })
}

function normalizeLocation(value: string) {
  return value.trim().slice(0, locationMaxLength)
}

function resolvePublishPermission(profile: UserProfileResponse) {
  const role = String(profile.mainRole || '').toUpperCase()
  return !!profile.videoVerified && (role === 'SELLER' || role === 'BOTH')
}

function resolvePublishBlockMessage(profile: UserProfileResponse) {
  if (String(profile.videoIdentityStatus || '').toUpperCase() === 'PENDING') return '卖家认证审核中'
  return '请先完成卖家认证'
}

async function loadProfileCity() {
  try {
    const profile = await getMyProfile()
    publishReady.value = resolvePublishPermission(profile)
    publishBlockMessage.value = publishReady.value ? '' : resolvePublishBlockMessage(profile)
    profileCity.value = normalizeLocation(profile.city || '')
    if (!form.location && profileCity.value) form.location = profileCity.value
    if (!publishReady.value) errorMessage.value = publishBlockMessage.value
  } catch {
    profileCity.value = ''
    publishReady.value = false
    publishBlockMessage.value = '请先登录后再申请卖家认证'
    errorMessage.value = publishBlockMessage.value
  }
}

function useProfileCity() {
  if (!profileCity.value) return
  form.location = profileCity.value
}

async function detectCurrentCity() {
  if (locating.value) return
  locating.value = true
  try {
    const location = await getCurrentLocation()
    const result = await reverseGeocode({ latitude: location.latitude, longitude: location.longitude })
    const city = normalizeLocation([result.city, result.district].filter(Boolean).join('') || result.address || result.province || '')
    if (!city) throw new Error('定位结果为空')
    form.location = city
    showToast('已填入当前城市')
  } catch {
    showToast('定位失败，请手动填写城市/区域')
  } finally {
    locating.value = false
  }
}

function validateForm() {
  trimTextField('title')
  trimTextField('location')
  normalizePrice()
  form.tradeRule = platformTradeRule
  if (!form.title || form.title.length < 4) return '标题至少 4 个字'
  if (!form.description || form.description.length < 10) return '描述至少 10 个字'
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
  if (!publishReady.value) {
    errorMessage.value = publishBlockMessage.value || '请先完成卖家认证'
    showToast(errorMessage.value)
    uni.navigateTo({ url: '/pages/user/identity/index?tab=video' })
    return
  }
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
      content: '已进入审核。',
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

onMounted(loadProfileCity)

function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
</script>

<style scoped>
.publish-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 52%,#fff7ed 100%); }
.preview-card { margin-top:16rpx; padding:16rpx; border-color:#ffd9bd; }
.photo-stage { min-height:190rpx; padding:18rpx; border-radius:24rpx; background:linear-gradient(135deg,#ff7a45,#ffb08a); color:#fff; display:flex; flex-direction:column; justify-content:space-between; box-shadow:0 14rpx 30rpx rgba(255,122,69,.20); }
.photo-main { width:68rpx; height:68rpx; border-radius:22rpx; background:rgba(255,255,255,.22); display:flex; align-items:center; justify-content:center; font-size:44rpx; font-weight:300; }
.photo-title { font-size:29rpx; font-weight:950; }
.photo-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:9rpx; width:100%; }
.photo-thumb,.photo-add { height:110rpx; border-radius:22rpx; background:rgba(255,255,255,.20); }
.photo-add { display:flex; align-items:center; justify-content:center; font-size:48rpx; color:#fff; border:2rpx dashed rgba(255,255,255,.55); }
.category-row { margin-top:14rpx; display:flex; gap:9rpx; }
.category-chip { flex:1; padding:11rpx 8rpx; text-align:center; border-radius:999rpx; background:#fff3e7; color:#9b7560; font-size:20rpx; font-weight:950; }
.category-chip.active { background:#ff7a45; color:#fff; box-shadow:0 8rpx 18rpx rgba(255,122,69,.18); }
.form-section { margin-top:14rpx; }
.field-label { margin-bottom:7rpx; color:#9b7560; font-size:20rpx; font-weight:900; }
.field-input,.field-textarea { box-sizing:border-box; width:100%; padding:16rpx; border-radius:18rpx; background:#fffaf6; color:#3a2a1f; font-weight:850; border:1rpx solid #ffd9bd; font-size:24rpx; }
.field-textarea { min-height:118rpx; line-height:1.55; font-weight:650; }
.row { display:flex; gap:10rpx; } .half { flex:1; }
.location-actions { margin-top:8rpx; display:flex; gap:8rpx; }
.mini-btn { flex:1; margin:0; padding:0 10rpx; height:48rpx; line-height:48rpx; border-radius:999rpx; background:#fff3e7; color:#9b7560; font-size:20rpx; font-weight:900; }
.mini-btn[disabled] { opacity:.45; }
.chips { display:flex; flex-wrap:wrap; gap:9rpx; }
.chip { padding:10rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#9b7560; font-size:20rpx; font-weight:900; }
.chip.active { background:#3a2a1f; color:#fff; }
.error-line,.success-line { margin-top:14rpx; padding:16rpx 18rpx; border-radius:18rpx; font-size:24rpx; font-weight:850; }
.error-line { color:#be123c; background:#fff1f2; }
.success-line { color:#15803d; background:#f0fdf4; }
.publish-btn { margin-top:16rpx; width:100%; }
.publish-btn[disabled] { opacity:.62; }
</style>
