<template>
  <view class="page-shell publish-page">
    <view class="preview-card ds-card">
      <view class="publish-hero">
        <view>
          <view class="kicker">♡ 发布宝贝</view>
          <view class="page-title">我要上新</view>
          <view class="page-desc">完善图片、标题和成色后提交审核，通过后再公开展示。</view>
        </view>
        <view class="hero-badge">审核后展示</view>
      </view>

      <view class="photo-stage tapable" :class="{ busy: uploadingPhotos }" @click="choosePhotos">
        <view class="photo-stage-head">
          <view>
            <view class="photo-title">商品图片</view>
            <view class="photo-subtitle">{{ uploadingPhotos ? '图片上传中' : `${form.imageUrls.length}/9 张，使用平台上传票据保存` }}</view>
          </view>
          <view class="photo-main">＋</view>
        </view>
        <view v-if="form.imageUrls.length" class="photo-grid">
          <view v-for="url in form.imageUrls" :key="url" class="photo-cell">
            <image class="photo-thumb" :src="url" mode="aspectFill" />
            <view class="photo-remove" @click.stop="removePhoto(url)">×</view>
          </view>
          <view v-if="form.imageUrls.length < 9" class="photo-add" @click.stop="choosePhotos">＋</view>
        </view>
      </view>

      <view class="category-row">
        <view v-for="item in categories" :key="item" class="category-chip tapable" :class="{ active: form.category === item }" @click="form.category = item">{{ item }}</view>
      </view>

      <view class="form-section">
        <view class="field-label">标题</view>
        <input :value="form.title" class="field-input" maxlength="40" placeholder="填写标题" confirm-type="next" @input="updateTextField('title', $event)" @blur="trimTextField('title')" />
      </view>

      <view class="form-section">
        <view class="field-label">描述</view>
        <textarea :value="form.description" class="field-textarea" maxlength="240" placeholder="填写描述" @input="updateTextField('description', $event)" @blur="trimTextField('description')" />
      </view>

      <view class="row">
        <view class="form-section half">
          <view class="field-label">售价</view>
          <input :value="form.price" class="field-input" type="text" inputmode="decimal" placeholder="¥ 0.00" confirm-type="next" @input="updateTextField('price', $event)" @blur="normalizePrice" />
        </view>
        <view class="form-section half">
          <view class="field-label">位置</view>
          <input :value="form.location" class="field-input" :maxlength="locationMaxLength" placeholder="城市/区域" confirm-type="done" @input="updateTextField('location', $event)" @blur="trimTextField('location')" />
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
      <button class="primary-btn publish-btn" :disabled="submitting || uploadingPhotos" @click="submitProduct">
        {{ submitting ? '发布中...' : uploadingPhotos ? '图片上传中...' : '提交审核' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { reverseGeocode } from '../../../api/modules/location'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { createProduct } from '../../../api/modules/product'
import { getMyProfile } from '../../../api/modules/user'
import {
  assertBackendProfile,
  categories,
  conditions,
  fileNameFromPath,
  hasInvalidProductImageUrl,
  hasInvalidTempImagePath,
  imageContentType,
  inputValue,
  locationErrorMessage,
  locationMaxLength,
  normalizeLocation,
  platformTradeRule,
  resolvePublishBlockMessage,
  resolvePublishPermission,
  tradeOptions,
  validatedProductImageUrl,
  type GetLocationResult,
  type TextFieldKey
} from './publish-integrity'

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
const uploadingPhotos = ref(false)
const locating = ref(false)
const profileCity = ref('')
const publishReady = ref(false)
const publishBlockMessage = ref('请先完成卖家认证')
const errorMessage = ref('')
const successMessage = ref('')

function choosePhotos() {
  if (uploadingPhotos.value) return
  const remain = 9 - form.imageUrls.length
  if (remain <= 0) {
    showToast('商品图片最多上传 9 张，请先移除一张后再添加')
    return
  }
  uploadingPhotos.value = true
  try {
    uni.chooseImage({
      count: remain,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      async success(result) {
        try {
          const paths = (result.tempFilePaths || []).slice(0, remain)
          if (!paths.length) throw new Error('未选择到有效商品图片，请重新选择')
          const uploadedUrls: string[] = []
          for (const path of paths) {
            if (hasInvalidTempImagePath(path)) throw new Error('图片资料无效，请重新选择')
            const ticket = await createMediaUploadTicket({
              scene: 'PRODUCT_IMAGE',
              contentType: imageContentType(path),
              fileSize: 300_000,
              filename: fileNameFromPath(path)
            })
            const uploaded = await uploadMediaTicketFile(ticket, path)
            uploadedUrls.push(validatedProductImageUrl(uploaded.storageUrl))
          }
          form.imageUrls = Array.from(new Set([...form.imageUrls, ...uploadedUrls])).slice(0, 9)
          showToast(`商品图片已上传 ${form.imageUrls.length} 张`)
        } catch (error) {
          console.warn('product publish image upload failed', { count: result.tempFilePaths?.length || 0, error })
          showToast(error instanceof Error ? error.message : '商品图片上传失败')
        } finally {
          uploadingPhotos.value = false
        }
      },
      fail(error: unknown) {
        uploadingPhotos.value = false
        console.warn('product publish image picker failed', { error })
        const message = String((error as { errMsg?: string })?.errMsg || '').toLowerCase()
        showToast(message.includes('cancel') ? '未选择图片' : '无法打开图片选择器，请检查相册或相机权限后重试')
      }
    })
  } catch (error) {
    uploadingPhotos.value = false
    console.warn('product publish image picker failed', { error })
    showToast('无法打开图片选择器，请检查相册或相机权限后重试')
  }
}

function removePhoto(url: string): void {
  form.imageUrls = form.imageUrls.filter(item => item !== url)
}

function updateTextField(field: TextFieldKey, event: unknown) {
  form[field] = inputValue(event)
}

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

async function loadProfileCity() {
  try {
    const profile = await getMyProfile()
    assertBackendProfile(profile)
    publishReady.value = resolvePublishPermission(profile)
    publishBlockMessage.value = publishReady.value ? '' : resolvePublishBlockMessage(profile)
    profileCity.value = normalizeLocation(profile.city || '')
    if (!form.location && profileCity.value) form.location = profileCity.value
    if (!publishReady.value) errorMessage.value = publishBlockMessage.value
  } catch (error) {
    console.warn('product publish profile load failed', { error })
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

async function detectCurrentCity(): Promise<void> {
  if (locating.value) return
  locating.value = true
  try {
    const location = await getCurrentLocation()
    const result = await reverseGeocode({ latitude: location.latitude, longitude: location.longitude })
    if (result.fallback !== false) throw new Error('定位服务未返回真实城市')
    const city = normalizeLocation([result.city, result.district].filter(Boolean).join('') || result.address || result.province || '')
    if (!city) throw new Error('定位结果为空')
    form.location = city
    showToast('已填入当前城市')
  } catch (error) {
    showToast(locationErrorMessage(error))
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
  if (!form.imageUrls.length) return '请至少上传一张商品图片'
  if (form.imageUrls.some(url => hasInvalidProductImageUrl(url))) return '图片需先完成平台上传票据校验'
  return ''
}

function navigateToIdentityVideo(): void {
  const route = {
    url: '/pages/user/identity/index?tab=video',
    fail(error: unknown) {
      console.warn('product publish identity navigation failed', { error })
      showToast('暂时无法打开认证页面，请稍后重试')
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('product publish identity navigation failed', { error })
    showToast('暂时无法打开认证页面，请稍后重试')
  }
}

function navigateToNotificationAfterPublish(productNo: string): void {
  const route = {
    url: '/pages/notification/index',
    fail(error: unknown) {
      console.warn('product publish notification navigation failed', { productNo, error })
      showToast('商品已提交，但暂时无法打开通知中心')
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('product publish notification navigation failed', { productNo, error })
    showToast('商品已提交，但暂时无法打开通知中心')
  }
}

async function submitProduct() {
  if (submitting.value) return
  errorMessage.value = ''
  successMessage.value = ''
  if (uploadingPhotos.value) {
    errorMessage.value = '图片上传中，请稍后提交'
    showToast(errorMessage.value)
    return
  }
  submitting.value = true
  try {
    const profile = await getMyProfile()
    assertBackendProfile(profile)
    publishReady.value = resolvePublishPermission(profile)
    publishBlockMessage.value = publishReady.value ? '' : resolvePublishBlockMessage(profile)
    profileCity.value = normalizeLocation(profile.city || '')
  } catch (error) {
    console.warn('product publish profile refresh failed', { error })
    publishReady.value = false
    publishBlockMessage.value = '请先登录后再申请卖家认证'
    errorMessage.value = publishBlockMessage.value
    showToast(errorMessage.value)
    submitting.value = false
    return
  }
  if (!publishReady.value) {
    errorMessage.value = publishBlockMessage.value || '请先完成卖家认证'
    showToast(errorMessage.value)
    navigateToIdentityVideo()
    submitting.value = false
    return
  }
  const error = validateForm()
  if (error) {
    errorMessage.value = error
    showToast(error)
    submitting.value = false
    return
  }
  try {
    const safeImageUrls = form.imageUrls.map(validatedProductImageUrl)
    const product = await createProduct({
      title: `[${form.category}] ${form.title}`,
      description: `${form.description}\n成色：${form.condition}\n位置：${form.location}\n交易方式：${form.tradeRule}`,
      price: Number(form.price).toFixed(2),
      imageUrls: safeImageUrls
    })
    if (!product.productNo) throw new Error('商品提交响应异常，请稍后重试')
    successMessage.value = `已提交审核：${product.productNo}`
    const modalOptions = {
      title: '已提交审核',
      content: '商品资料已提交后端审核，审核状态以服务端记录和通知为准。',
      showCancel: true,
      confirmText: '查看通知',
      cancelText: '继续编辑',
      success(result: { confirm: boolean }) {
        if (result.confirm) navigateToNotificationAfterPublish(product.productNo)
      },
      fail(error: unknown) {
        console.warn('product publish success modal failed', { productNo: product.productNo, error })
        showToast('商品已提交，但确认弹窗无法显示，请稍后查看通知')
      }
    }
    try {
      uni.showModal(modalOptions)
    } catch (error) {
      console.warn('product publish success modal failed', { productNo: product.productNo, error })
      showToast('商品已提交，但确认弹窗无法显示，请稍后查看通知')
    }
  } catch (error) {
    console.warn('product publish submit failed', { titleLength: form.title.length, imageCount: form.imageUrls.length, error })
    errorMessage.value = error instanceof Error ? error.message : '发布失败，请稍后重试'
    showToast(errorMessage.value)
  } finally {
    submitting.value = false
  }
}

onMounted(loadProfileCity)

function showToast(title: string) { uni.showToast({ title, icon: 'none' }) }
</script>

<style scoped lang="scss" src="./style.scss"></style>
