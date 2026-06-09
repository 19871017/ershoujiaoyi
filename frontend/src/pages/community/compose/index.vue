<template>
  <view class="page-shell compose-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 发布社区动态</view>
        <view class="page-title">发一条穿搭/避坑分享</view>
        <view class="page-desc">可以聊穿搭、交易经验、求购心愿；订单、支付和售后状态以平台记录为准。</view>
      </view>
      <view class="hero-icon">✎</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">选择话题</view>
      <view class="topic-row">
        <view v-for="topic in topics" :key="topic" class="topic-chip tapable" :class="{ active: form.topic === topic }" @click="selectTopic(topic)">{{ topic }}</view>
      </view>

      <view class="section-title gap">动态内容</view>
      <input :value="form.title" class="field" maxlength="32" placeholder="写个标题，比如：奶油白裙子怎么搭？" @input="updateTextField('title', $event)" @blur="trimTextField('title')" />
      <textarea :value="form.content" class="textarea" maxlength="500" placeholder="分享细节、交易经验、搭配心得或想求购的小物..." @input="updateTextField('content', $event)" @blur="trimTextField('content')" />
      <view class="counter">{{ form.content.length }}/500</view>

      <view class="product-picker">
        <view class="product-picker-head">
          <view class="product-picker-copy">
            <view class="product-picker-title">关联我的商品</view>
            <view class="product-picker-desc">{{ productPickerHint }}</view>
          </view>
          <view class="product-picker-action tapable" :class="{ loading: myProductsLoading }" @click="toggleMyProductPicker">{{ productPickerActionText }}</view>
        </view>
        <view v-if="productPickerOpen" class="product-picker-body">
          <view v-if="myProductsLoading" class="picker-state">正在读取我的在售商品</view>
          <view v-else-if="myProductsError" class="picker-state error">
            <view>{{ myProductsError }}</view>
            <view class="picker-retry tapable" @click="loadMySelectableProducts(true)">重试</view>
          </view>
          <view v-else-if="!mySelectableProducts.length" class="picker-state">暂无可关联的认证在售商品</view>
          <scroll-view v-else scroll-x class="product-strip">
            <view class="product-option-row">
              <view
                v-for="product in mySelectableProducts"
                :key="product.productId"
                class="product-option tapable"
                :class="{ active: relatedProductId === product.productId }"
                @click="selectRelatedProductFromMine(product)"
              >
                <image v-if="product.coverImageUrl" class="product-cover" :src="product.coverImageUrl" mode="aspectFill" />
                <view v-else class="product-cover fallback">物</view>
                <view class="product-option-main">
                  <view class="product-option-title">{{ product.title }}</view>
                  <view class="product-option-meta">{{ product.productNo || '平台商品' }}</view>
                  <view class="product-option-price">¥{{ product.price }}</view>
                </view>
              </view>
            </view>
          </scroll-view>
        </view>
      </view>

      <view v-if="relatedProductId" class="related-product">
        <view class="related-main">
          <view class="related-label">关联商品</view>
          <view class="related-title">{{ relatedProductTitle }}</view>
          <view class="related-price">{{ relatedProductPriceText }}</view>
        </view>
        <view v-if="relatedProductLoading" class="related-status">校验中</view>
        <view v-else class="related-clear tapable" @click="clearRelatedProduct">移除</view>
      </view>

      <view class="image-box tapable" @click="chooseImages">
        <view class="image-plus">＋</view>
        <view>
          <view class="image-title">{{ uploadingImages ? '图片上传中' : '添加图片' }}</view>
          <view class="image-desc">已上传图片 {{ form.images.length }} 张，最多 9 张</view>
        </view>
      </view>
      <view v-if="form.images.length" class="preview-row">
        <view v-for="(img,index) in form.images" :key="img" class="preview tapable" @click.stop="previewUploadedImages(index)">
          <image :src="img" mode="aspectFill" />
          <view class="remove tapable" @click.stop="removeImage(index)">×</view>
        </view>
      </view>
    </view>

    <view class="safe-card ds-card">
      <view class="safe-title">发布前提醒</view>
      <view class="safe-line">请勿发布联系方式、完整证件号或未打码的隐私信息。</view>
      <view class="safe-line">涉及纠纷或异常交易，可直接从帖子/商品/聊天入口举报。</view>
      <view v-if="submitMessage" class="safe-line strong">{{ submitMessage }}</view>
    </view>

    <button class="primary-btn submit" :disabled="submitting || uploadingImages" @click="submitPost">{{ submitting ? '提交中...' : '提交发布' }}</button>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { COMMUNITY_TOPICS, createCommunityPost, isCommunityTopic, type CommunityTopic } from '../../../api/modules/community'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getProductDetail, listMyProducts } from '../../../api/modules/product'
import {
  assertRelatedProductForCompose,
  assertCreatedCommunityPost,
  fileNameFromPath,
  hasInvalidCommunityImageUrl,
  hasInvalidTempImagePath,
  imageContentType,
  imageFallbackName,
  imageFileSize,
  inputValue,
  normalizeComposeProductPrice,
  selectableRelatedProductsForCompose,
  toSafeBackendId,
  validatedCommunityImageUrl,
  type ComposeSelectableRelatedProduct,
  type ChooseImageResult,
  type TextFieldKey
} from './compose-helpers'

const submitting = ref(false)
const uploadingImages = ref(false)
const submitMessage = ref('')
const topics = COMMUNITY_TOPICS
const form = reactive({ topic: COMMUNITY_TOPICS[0] as CommunityTopic, title: '', content: '', images: [] as string[] })
const relatedProductId = ref<number | null>(null)
const relatedProductTitle = ref('平台商品待校验')
const relatedProductPrice = ref<string | number | null>(null)
const relatedProductLoading = ref(false)
const productPickerOpen = ref(false)
const myProductsLoading = ref(false)
const myProductsError = ref('')
const myProductsLoaded = ref(false)
const mySelectableProducts = ref<ComposeSelectableRelatedProduct[]>([])
const showModalWithFailure = uni.showModal as unknown as (options: {
  title?: string
  content: string
  showCancel?: boolean
  confirmText?: string
  cancelText?: string
  success?: (result: { confirm: boolean; cancel: boolean }) => void
  fail?: (error: unknown) => void
}) => void
const redirectToWithFailure = uni.redirectTo as unknown as (options: { url: string; fail?: (error: unknown) => void }) => void
const switchTabWithFailure = uni.switchTab as unknown as (options: { url: string; fail?: (error: unknown) => void }) => void
const relatedProductPriceText = computed(() => {
  if (!relatedProductId.value) return '未关联商品'
  if (relatedProductLoading.value) return '正在读取平台商品详情'
  if (relatedProductPrice.value !== null && relatedProductPrice.value !== '') return `¥${relatedProductPrice.value}`
  return '价格以商品详情为准'
})
const productPickerHint = computed(() => {
  if (relatedProductId.value) return '已选择平台商品，发布前会再次校验'
  if (myProductsLoading.value) return '正在读取我的认证在售商品'
  if (myProductsLoaded.value) {
    return mySelectableProducts.value.length
      ? `可选 ${mySelectableProducts.value.length} 件认证在售商品`
      : '暂无可关联的认证在售商品'
  }
  return '只展示已通过视频认证的在售真实商品'
})
const productPickerActionText = computed(() => {
  if (myProductsLoading.value) return '加载中'
  return productPickerOpen.value ? '收起' : '选择我的商品'
})
onLoad((options) => {
  applyRelatedProductRoute(options as Record<string, string | undefined> | undefined)
})
function applyRelatedProductRoute(options?: Record<string, string | undefined>): void {
  const routeProductId = positiveRouteNumber(options?.productId)
  if (!routeProductId) {
    if (options?.productId) {
      clearRelatedProduct()
      uni.showToast({ title: '关联商品编号无效，已取消关联', icon: 'none' })
    }
    return
  }
  relatedProductId.value = routeProductId
  relatedProductTitle.value = '平台商品待校验'
  relatedProductPrice.value = null
  void hydrateRelatedProductFromBackend(routeProductId)
}
function positiveRouteNumber(value?: string): number | null {
  const decoded = safeDecode(value || '')
  return toSafeBackendId(decoded)
}
function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}
async function hydrateRelatedProductFromBackend(productId: number): Promise<void> {
  relatedProductLoading.value = true
  try {
    const product = await getProductDetail(productId)
    assertRelatedProductForCompose(product, productId)
    if (relatedProductId.value !== productId) return
    relatedProductTitle.value = product.title.trim()
    relatedProductPrice.value = normalizeComposeProductPrice(product.price)
  } catch (error) {
    if (relatedProductId.value !== productId) return
    console.warn('community compose related product load failed', { productId, error })
    clearRelatedProduct()
    uni.showToast({ title: error instanceof Error ? error.message : '关联商品资料加载失败，已取消关联', icon: 'none' })
  } finally {
    if (relatedProductId.value === productId) relatedProductLoading.value = false
  }
}
function clearRelatedProduct(): void {
  relatedProductId.value = null
  relatedProductTitle.value = '平台商品待校验'
  relatedProductPrice.value = null
  relatedProductLoading.value = false
}
function selectTopic(topic: CommunityTopic): void { form.topic = topic }
function toggleMyProductPicker(): void {
  if (myProductsLoading.value && !productPickerOpen.value) return
  productPickerOpen.value = !productPickerOpen.value
  if (productPickerOpen.value) void loadMySelectableProducts()
}
async function loadMySelectableProducts(force = false): Promise<void> {
  if (myProductsLoading.value) return
  if (myProductsLoaded.value && !force) return
  myProductsLoading.value = true
  myProductsError.value = ''
  try {
    const products = await listMyProducts()
    mySelectableProducts.value = selectableRelatedProductsForCompose(products)
    myProductsLoaded.value = true
  } catch (error) {
    console.warn('community compose my product list load failed', error)
    mySelectableProducts.value = []
    myProductsLoaded.value = false
    myProductsError.value = error instanceof Error ? error.message : '我的商品列表加载失败，请稍后重试'
  } finally {
    myProductsLoading.value = false
  }
}
function selectRelatedProductFromMine(product: ComposeSelectableRelatedProduct): void {
  if (relatedProductLoading.value) {
    uni.showToast({ title: '关联商品校验中，请稍后选择', icon: 'none' })
    return
  }
  relatedProductId.value = product.productId
  relatedProductTitle.value = '平台商品待校验'
  relatedProductPrice.value = null
  productPickerOpen.value = false
  void hydrateRelatedProductFromBackend(product.productId)
}
function chooseImages() {
  if (uploadingImages.value) {
    uni.showToast({ title: '图片上传中，请稍后再选', icon: 'none' })
    return
  }
  const remain = 9 - form.images.length
  if (remain <= 0) {
    uni.showToast({ title: '图片最多上传 9 张，请先移除一张后再添加', icon: 'none' })
    return
  }
  uploadingImages.value = true
  uni.chooseImage({
    count: remain,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    async success(res: ChooseImageResult) {
      try {
        const issuedUrls: string[] = []
        const paths = (res.tempFilePaths || []).slice(0, remain)
        for (const [index, path] of paths.entries()) {
          if (hasInvalidTempImagePath(path)) {
            throw new Error('图片资料无效，请重新选择')
          }
          const file = res.tempFiles?.[index]
          const contentType = imageContentType(path, file?.type)
          const ticket = await createMediaUploadTicket({
            scene: 'COMMUNITY_IMAGE',
            contentType,
            fileSize: imageFileSize(file),
            filename: fileNameFromPath(file?.name || path, imageFallbackName(contentType))
          })
          const uploaded = await uploadMediaTicketFile(ticket, path)
          issuedUrls.push(validatedCommunityImageUrl(uploaded.storageUrl))
        }
        form.images = [...form.images, ...issuedUrls].slice(0, 9)
        uni.showToast({ title: `图片已上传 ${form.images.length} 张，提交发布后进入动态`, icon: 'none' })
      } catch (error) {
        uni.showToast({ title: error instanceof Error ? error.message : '图片上传失败，请重新选择', icon: 'none' })
      } finally {
        uploadingImages.value = false
      }
    },
    fail() {
      uploadingImages.value = false
      uni.showToast({ title: '未选择图片', icon: 'none' })
    }
  })
}
function removeImage(index: number) { form.images = form.images.filter((_, current) => current !== index) }

function previewUploadedImages(index: number): void {
  const urls = form.images.filter((url) => !hasInvalidCommunityImageUrl(url))
  const current = urls[index]
  if (!current) {
    uni.showToast({ title: '图片暂时无法预览', icon: 'none' })
    return
  }
  try {
    uni.previewImage({ current, urls })
  } catch (error) {
    console.warn('community compose image preview failed', { index, error })
    uni.showToast({ title: '图片预览失败，请稍后重试', icon: 'none' })
  }
}

function updateTextField(field: TextFieldKey, event: unknown): void {
  const value = inputValue(field, event)
  if (value === undefined) {
    uni.showToast({ title: '输入内容读取失败，请重新输入', icon: 'none' })
    return
  }
  form[field] = value
}

function trimTextField(field: TextFieldKey): void {
  form[field] = form[field].trim()
}
async function submitPost() {
  submitMessage.value = ''
  if (uploadingImages.value) return uni.showToast({ title: '图片上传中，请稍后提交', icon: 'none' })
  if (relatedProductLoading.value) return uni.showToast({ title: '关联商品校验中，请稍后提交', icon: 'none' })
  trimTextField('title')
  trimTextField('content')
  if (form.title.length < 4) return uni.showToast({ title: '标题至少 4 个字', icon: 'none' })
  if (form.content.length < 8) return uni.showToast({ title: '内容至少 8 个字', icon: 'none' })
  if (!isCommunityTopic(form.topic)) return uni.showToast({ title: '请选择有效社区话题', icon: 'none' })
  if (form.images.some(hasInvalidCommunityImageUrl)) {
    return uni.showToast({ title: '图片需先完成平台上传票据校验', icon: 'none' })
  }
  submitting.value = true
  try {
    const created = await createCommunityPost({ title: form.title, topic: form.topic, content: form.content, imageUrls: form.images.map(validatedCommunityImageUrl), relatedProductId: relatedProductId.value })
    assertCreatedCommunityPost(created, form.topic)
    submitMessage.value = `已提交发布：${created.postNo || created.postId}`
    showModalWithFailure({
      title: '发布成功',
      content: '动态已进入社区，评论、点赞和私信都会以平台记录为准。',
      showCancel: true,
      confirmText: '查看动态',
      cancelText: '回社区',
      success: (res) => redirectAfterPostCreated(res.confirm, created.postId),
      fail: (error: unknown) => {
        console.warn('community compose success modal failed', error)
        showPostCreatedNavigationFallback(created.postId)
      }
    })
  } catch (error) {
    submitMessage.value = '发布没有提交成功，未进入社区广场'
    uni.showToast({ title: error instanceof Error ? error.message : '发布失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function redirectAfterPostCreated(stayOnPost: boolean, postId: number): void {
  try {
    if (stayOnPost && postId > 0) {
      redirectToWithFailure({
        url: `/pages/community/detail/index?postId=${postId}`,
        fail: (error: unknown) => {
          console.warn('community compose detail redirect failed', { postId, error })
          showPostCreatedNavigationFallback(postId)
        }
      })
      return
    }
    switchTabWithFailure({
      url: '/pages/tabbar/message/index',
      fail: (error: unknown) => {
        console.warn('community compose tab redirect failed', { postId, error })
        showPostCreatedNavigationFallback(postId)
      }
    })
  } catch (error) {
    console.warn('community compose success redirect failed', error)
    showPostCreatedNavigationFallback(postId)
  }
}

function showPostCreatedNavigationFallback(postId: number): void {
  submitMessage.value = postId > 0
    ? `动态已发布，可到社区或帖子 ${postId} 查看`
    : '动态已发布，可到社区查看'
  uni.showToast({ title: '动态已发布，可到社区查看', icon: 'none' })
}
</script>
<style scoped lang="scss" src="./style.scss"></style>
