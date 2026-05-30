<template>
  <view class="page-shell compose-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 发布圈内动态</view>
        <view class="page-title">发一条穿搭/避坑分享</view>
        <view class="page-desc">可以聊穿搭、交易经验、求购心愿；订单、支付和售后状态以平台记录为准。</view>
      </view>
      <view class="hero-icon">✎</view>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">选择话题</view>
      <view class="topic-row">
        <view v-for="topic in topics" :key="topic" class="topic-chip tapable" :class="{ active: form.topic === topic }" @click="form.topic = topic">{{ topic }}</view>
      </view>

      <view class="section-title gap">动态内容</view>
      <input :value="form.title" class="field" maxlength="32" placeholder="写个标题，比如：奶油白裙子怎么搭？" @input="updateTextField('title', $event)" @blur="trimTextField('title')" />
      <textarea :value="form.content" class="textarea" maxlength="500" placeholder="分享细节、交易经验、搭配心得或想求购的小物..." @input="updateTextField('content', $event)" @blur="trimTextField('content')" />
      <view class="counter">{{ form.content.length }}/500</view>

      <view class="image-box tapable" @click="chooseImages">
        <view class="image-plus">＋</view>
        <view>
          <view class="image-title">{{ uploadingImages ? '图片上传中' : '添加图片' }}</view>
          <view class="image-desc">已上传图片 {{ form.images.length }} 张，最多 9 张</view>
        </view>
      </view>
      <view v-if="form.images.length" class="preview-row">
        <view v-for="(img,index) in form.images" :key="img" class="preview">
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
import { reactive, ref } from 'vue'
import { createCommunityPost } from '../../../api/modules/community'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import {
  fileNameFromPath,
  hasInvalidCommunityImageUrl,
  hasInvalidTempImagePath,
  imageContentType,
  imageFallbackName,
  imageFileSize,
  inputValue,
  topics,
  validatedCommunityImageUrl,
  type ChooseImageResult,
  type TextFieldKey
} from './compose-helpers'

const submitting = ref(false)
const uploadingImages = ref(false)
const submitMessage = ref('')
const form = reactive({ topic: '生活日常', title: '', content: '', images: [] as string[] })
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
  trimTextField('title')
  trimTextField('content')
  if (!form.title) return uni.showToast({ title: '请填写标题', icon: 'none' })
  if (form.content.length < 8) return uni.showToast({ title: '内容至少 8 个字', icon: 'none' })
  if (form.images.some(hasInvalidCommunityImageUrl)) {
    return uni.showToast({ title: '图片需先完成平台上传票据校验', icon: 'none' })
  }
  submitting.value = true
  try {
    const created = await createCommunityPost({ title: form.title, topic: form.topic, content: form.content, imageUrls: form.images.map(validatedCommunityImageUrl) })
    submitMessage.value = `已提交发布：${created.postNo || created.postId}`
    uni.showModal({
      title: '已提交发布',
      content: '平台已创建社区动态；列表、详情、评论和点赞均以平台记录为准。',
      showCancel: true,
      confirmText: '查看动态',
      cancelText: '继续编辑',
      success: (res) => { if (res.confirm && created.postId > 0) uni.navigateTo({ url: `/pages/community/detail/index?postId=${created.postId}` }) }
    })
  } catch (error) {
    submitMessage.value = '发布没有提交成功，未进入社区广场'
    uni.showToast({ title: error instanceof Error ? error.message : '发布失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>
<style scoped lang="scss" src="./style.scss"></style>
