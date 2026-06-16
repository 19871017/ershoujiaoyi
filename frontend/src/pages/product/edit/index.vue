<template>
  <view class="page-shell edit-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 编辑宝贝</view>
        <view class="page-title">商品编辑</view>
        <view class="page-desc">修改后会重新进入审核，已锁定或已售出的商品不能继续编辑。</view>
      </view>
      <view class="hero-icon">✏️</view>
    </view>

    <view v-if="loadError" class="fail-card">{{ loadError }}</view>

    <template v-if="loaded">
      <view class="form-card ds-card">
        <view class="section-title">基础信息</view>
        <view class="section-desc">本页可提交标题、描述、价格和图片修改审核。</view>
        <input :value="form.title" class="field" maxlength="40" placeholder="宝贝标题" confirm-type="next" @input="updateTextField('title', $event)" @blur="trimTextField('title')" />
        <textarea :value="form.description" class="field area" maxlength="240" placeholder="描述成色、尺码、瑕疵和购买建议" @input="updateTextField('description', $event)" @blur="trimTextField('description')" />
        <input :value="form.price" class="field" type="text" inputmode="decimal" placeholder="价格" confirm-type="done" @input="updateTextField('price', $event)" @blur="normalizePrice" />
      </view>

      <view class="form-card ds-card">
        <view class="section-title">图片与交易</view>
        <view class="section-desc">商品图片上传票据已生成，需提交修改审核后才会更新商品图片。</view>
        <view class="image-row">
          <view v-for="img in images" :key="img" class="image-box image">
            <image class="product-image" :src="img" mode="aspectFill" />
            <view class="remove tapable" @click="removeImage(img)">×</view>
          </view>
          <view v-if="images.length < 9" class="image-box add tapable" :class="{ busy: uploadingImages }" @click="chooseImage">{{ uploadingImages ? '…' : '＋' }}</view>
        </view>
        <view class="rule"><switch :checked="form.serverTradeOnly" @change="toggleTradePreference('serverTradeOnly')" /> <text>交易方式以平台订单与支付状态为准</text></view>
        <view class="rule"><switch :checked="form.serverChatRecord" @change="toggleTradePreference('serverChatRecord')" /> <text>聊天记录以平台会话为准</text></view>
      </view>

      <button class="primary-btn" :disabled="saving || uploadingImages || !backendProductId" @click="save">{{ submitButtonText }}</button>
    </template>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getProductDetail, updateProduct } from '../../../api/modules/product'
import {
  assertProductDetail,
  decodeRouteValue,
  fileNameFromPath,
  hasInvalidProductImageUrl,
  hasInvalidTempImagePath,
  imageContentType,
  imageFallbackName,
  imageFileSize,
  inputValue,
  isValidBackendProductId,
  userSafeLoadErrors,
  validatedProductImageUrl,
  type ChooseImageFile,
  type TextFieldKey
} from './product-edit-helpers'
const productId = ref('')
const saving = ref(false)
const uploadingImages = ref(false)
const loaded = ref(false)
const loadError = ref('')
const images = ref<string[]>([])
const form = reactive({ title: '', description: '', price: '', serverTradeOnly: false, serverChatRecord: false })
const backendProductId = computed(() => isValidBackendProductId(productId.value) ? Number(productId.value) : 0)
const submitButtonText = computed(function submitButtonTextValue() {
  if (saving.value) return '提交中...'
  if (uploadingImages.value) return '图片上传中...'
  return '提交修改审核'
})

function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const routeProductId = decodeRouteValue('productId', current?.options?.productId || hash?.get('productId') || '')
  productId.value = isValidBackendProductId(routeProductId) ? routeProductId : ''
}

async function loadDetail(): Promise<void> {
  loaded.value = false
  loadError.value = ''
  if (!backendProductId.value) { loadError.value = '商品信息缺失，请返回后重试'; return }
  try {
    const detail = await getProductDetail(backendProductId.value)
    assertProductDetail(detail)
    if (detail.productId !== backendProductId.value) throw new Error('product edit productId mismatch')
    form.title = detail.title
    form.description = detail.description || ''
    form.price = String(detail.sellerPrice || detail.price)
    images.value = (detail.imageUrls || []).map(validatedProductImageUrl)
    loaded.value = true
  } catch (error) {
    form.title = ''
    form.description = ''
    form.price = ''
    images.value = []
    const message = error instanceof Error ? error.message : ''
    loadError.value = userSafeLoadErrors.has(message) ? message : '商品详情暂时加载失败，请稍后重试'
    console.warn('product edit load failed', { productId: productId.value, loadError: loadError.value, error })
  }
}

function showToastSafely(title: string, context: string): void {
  try {
    uni.showToast({ title, icon: 'none' })
  } catch (error) {
    console.warn('product edit toast failed', { context, productId: backendProductId.value, error })
  }
}

function chooseImage(): void {
  if (uploadingImages.value) return
  const remain = 9 - images.value.length
  if (remain <= 0) { showToastSafely('商品图片最多上传 9 张，请先移除一张后再添加', 'image-limit'); return }
  uploadingImages.value = true
  try {
    uni.chooseImage({
      count: remain,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      async success(res) {
        try {
          const paths = (res.tempFilePaths || []).slice(0, remain)
          const files = (res as { tempFiles?: ChooseImageFile[] }).tempFiles || []
          if (!paths.length) throw new Error('未选择到有效商品图片，请重新选择')
          const uploadedUrls: string[] = []
          for (const [index, path] of paths.entries()) {
            if (hasInvalidTempImagePath(path)) throw new Error('商品图片无效，请重新选择')
            const file = files[index]
            const contentType = imageContentType(path, file?.type)
            const ticket = await createMediaUploadTicket({ scene: 'PRODUCT_IMAGE', contentType, fileSize: imageFileSize(file), filename: fileNameFromPath(file?.name || path, imageFallbackName(contentType)) })
            const uploaded = await uploadMediaTicketFile(ticket, path)
            uploadedUrls.push(validatedProductImageUrl(uploaded.storageUrl))
          }
          images.value = Array.from(new Set([...images.value, ...uploadedUrls])).slice(0, 9)
          showToastSafely('商品图片上传票据已生成，需提交修改审核后才会更新商品图片', 'image-upload-success')
        } catch (error) {
          console.warn('product edit image upload failed', { count: res.tempFilePaths?.length || 0, error })
          showToastSafely(error instanceof Error && error.message ? error.message : '商品图片上传失败，请重新选择后再试', 'image-upload-failed')
        } finally {
          uploadingImages.value = false
        }
      },
      fail(error: unknown) {
        uploadingImages.value = false
        console.warn('product edit image picker failed', { error })
        const message = String((error as { errMsg?: string })?.errMsg || '').toLowerCase()
        showToastSafely(message.includes('cancel') ? '未选择图片' : '无法打开图片选择器，请检查相册或相机权限后重试', 'image-picker-failed')
      }
    })
  } catch (error) {
    uploadingImages.value = false
    console.warn('product edit image picker failed', { error })
    showToastSafely('无法打开图片选择器，请检查相册或相机权限后重试', 'image-picker-thrown')
  }
}

function removeImage(url: string): void {
  images.value = images.value.filter(item => item !== url)
  showToastSafely('商品图片移除需提交修改审核后生效', 'image-remove')
}

function toggleTradePreference(_field: 'serverTradeOnly' | 'serverChatRecord'): void {
  showToastSafely('交易展示项暂不可变更，请提交商品修改后以平台审核结果为准', 'trade-preference')
}

function updateTextField(field: TextFieldKey, event: unknown): void {
  const value = inputValue(field, event)
  if (value === undefined) {
    showToastSafely('输入内容读取失败，请重新输入', 'input-invalid')
    return
  }
  form[field] = value
}

function trimTextField(field: TextFieldKey): void {
  form[field] = form[field].trim()
}

function normalizePrice(): void {
  const value = form.price
    .replace(/[^\d.]/g, '')
    .replace(/(\.\d{2}).+$/, '$1')
  const dotIndex = value.indexOf('.')
  form.price = dotIndex === -1 ? value : `${value.slice(0, dotIndex + 1)}${value.slice(dotIndex + 1).replace(/\./g, '')}`
}

function validate(): string {
  trimTextField('title')
  trimTextField('description')
  normalizePrice()
  if (!form.title || !form.price) return '请补全标题和价格'
  if (form.title.length < 4) return '标题至少 4 个字'
  const priceValue = Number(form.price)
  if (!/^\d+(\.\d{1,2})?$/.test(form.price) || !Number.isFinite(priceValue)) return '价格格式不正确'
  if (priceValue <= 0) return '价格需大于0'
  if (!images.value.length) return '请至少保留一张商品图片'
  if (images.value.some(url => hasInvalidProductImageUrl(url))) return '图片需先完成平台上传票据校验'
  return ''
}

function navigateToDetailAfterSave(): void {
  const route = {
    url: `/pages/product/detail/index?productId=${backendProductId.value}`,
    fail(error: unknown) {
      console.warn('product edit detail navigation failed', { productId: backendProductId.value, error })
      showToastSafely('商品修改已提交，但暂时无法打开详情页', 'detail-navigation-failed')
    }
  }
  try {
    uni.navigateTo(route)
  } catch (error) {
    console.warn('product edit detail navigation failed', { productId: backendProductId.value, error })
    showToastSafely('商品修改已提交，但暂时无法打开详情页', 'detail-navigation-thrown')
  }
}

async function save(): Promise<void> {
  if (saving.value) return
  if (uploadingImages.value) { showToastSafely('图片上传中，请稍后提交', 'save-while-uploading'); return }
  if (!backendProductId.value) { showToastSafely('缺少有效商品编号，未提交修改', 'save-invalid-product-id'); return }
  const message = validate()
  if (message) { showToastSafely(message, 'save-validation'); return }
  saving.value = true
  try {
    let safeImageUrls: string[]
    try {
      safeImageUrls = images.value.map(validatedProductImageUrl)
    } catch (error) {
      console.warn('product edit save failed', { productId: backendProductId.value, imageCount: images.value.length, error })
      showToastSafely(error instanceof Error && error.message ? error.message : '商品修改保存失败，请稍后重试', 'save-invalid-images')
      return
    }
    try {
      await updateProduct(backendProductId.value, { title: form.title, description: form.description, price: Number(form.price).toFixed(2), imageUrls: safeImageUrls })
    } catch (error) {
      console.warn('product edit save failed', { productId: backendProductId.value, imageCount: images.value.length, error })
      showToastSafely(error instanceof Error && error.message ? error.message : '商品修改保存失败，请稍后重试', 'save-update-failed')
      return
    }
    const modalOptions = {
      title: '已提交审核',
      content: '商品修改已保存，重新进入平台审核，通过后再公开展示。',
      showCancel: false,
      success: () => navigateToDetailAfterSave(),
      fail: (error: unknown) => {
        console.warn('product edit success modal failed', { productId: backendProductId.value, error })
        showToastSafely('商品修改已提交，但确认弹窗无法显示', 'success-modal-failed')
      }
    }
    try {
      uni.showModal(modalOptions)
    } catch (error) {
      console.warn('product edit success modal failed', { productId: backendProductId.value, error })
      showToastSafely('商品修改已提交，但确认弹窗无法显示', 'success-modal-thrown')
    }
  } finally {
    saving.value = false
  }
}

function initializePage(): void {
  try {
    readQuery()
  } catch (error) {
    productId.value = ''
    loadError.value = '商品信息缺失，请返回后重试'
    console.warn('product edit route read failed', { error })
    return
  }
  void loadDetail()
}

onMounted(initializePage)
</script>
<style scoped lang="scss" src="./style.scss"></style>
