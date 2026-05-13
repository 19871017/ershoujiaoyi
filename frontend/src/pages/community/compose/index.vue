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
      <input v-model.trim="form.title" class="field" maxlength="32" placeholder="写个标题，比如：奶油白裙子怎么搭？" />
      <textarea v-model.trim="form.content" class="textarea" maxlength="500" placeholder="分享细节、交易经验、搭配心得或想求购的小物..." />
      <view class="counter">{{ form.content.length }}/500</view>

      <view class="image-box tapable" @click="chooseImages">
        <view class="image-plus">＋</view>
        <view>
          <view class="image-title">添加图片</view>
          <view class="image-desc">已生成上传票据 {{ form.images.length }} 张，最多 9 张</view>
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

    <button class="primary-btn submit" :disabled="submitting" @click="submitPost">{{ submitting ? '提交中...' : '提交发布' }}</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { createCommunityPost } from '../../../api/modules/community'
import { createMediaUploadTicket } from '../../../api/modules/media'

const topics = ['生活日常', '闲置避坑', '交易经验', '求购心愿']
const submitting = ref(false)
const submitMessage = ref('')
const form = reactive({ topic: '生活日常', title: '', content: '', images: [] as string[] })
function chooseImages() {
  const remain = Math.max(1, 9 - form.images.length)
  uni.chooseImage({
    count: remain,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    async success(res) {
      try {
        const issuedUrls: string[] = []
        for (const path of res.tempFilePaths.slice(0, remain)) {
          if (path.startsWith('local://') || path.includes('placeholder')) {
            throw new Error('图片资料无效，请重新选择')
          }
          const ticket = await createMediaUploadTicket({
            scene: 'COMMUNITY_IMAGE',
            contentType: imageContentType(path),
            fileSize: 300_000,
            filename: fileNameFromPath(path)
          })
          issuedUrls.push(ticket.storageUrl)
        }
        form.images = [...form.images, ...issuedUrls].slice(0, 9)
        uni.showToast({ title: `已生成上传票据 ${form.images.length} 张，提交发布后进入动态`, icon: 'none' })
      } catch (error) {
        uni.showToast({ title: error instanceof Error ? error.message : '图片上传票据创建失败', icon: 'none' })
      }
    }
  })
}
function removeImage(index: number) { form.images = form.images.filter((_, current) => current !== index) }
function fileNameFromPath(path: string) {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'community-image.jpg'
  return last.includes('.') ? last : `${last}.jpg`
}
function imageContentType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}
async function submitPost() {
  submitMessage.value = ''
  if (!form.title) return uni.showToast({ title: '请填写标题', icon: 'none' })
  if (form.content.length < 8) return uni.showToast({ title: '内容至少 8 个字', icon: 'none' })
  if (form.images.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/community-image/'))) {
    return uni.showToast({ title: '图片需先完成平台上传票据校验', icon: 'none' })
  }
  submitting.value = true
  try {
    const created = await createCommunityPost({ title: form.title, topic: form.topic, content: form.content, imageUrls: form.images })
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
    submitMessage.value = '发布没有提交成功，未进入广场'
    uni.showToast({ title: error instanceof Error ? error.message : '发布失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.compose-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.hero,.form-card,.safe-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.hero { display:flex; justify-content:space-between; gap:18rpx; background:linear-gradient(135deg,#fff,#fff3e7); }
.kicker { color:#ff7a45; font-size:22rpx; font-weight:950; }
.hero-icon { width:78rpx; height:78rpx; border-radius:28rpx; display:flex; align-items:center; justify-content:center; background:#ff7a45; color:#fff; font-size:40rpx; font-weight:950; }
.section-title { color:#3a2a1f; font-size:29rpx; font-weight:950; }.gap { margin-top:22rpx; }
.topic-row { margin-top:14rpx; display:flex; flex-wrap:wrap; gap:12rpx; }
.topic-chip { padding:13rpx 20rpx; border-radius:999rpx; background:#fffaf6; color:#9b7560; border:1rpx solid #ffd9bd; font-size:22rpx; font-weight:900; }
.topic-chip.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.field,.textarea { width:100%; box-sizing:border-box; margin-top:14rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:24rpx; }
.field { height:72rpx; }.textarea { height:210rpx; line-height:1.55; }
.counter { margin-top:8rpx; text-align:right; color:#b9856a; font-size:20rpx; }
.image-box { margin-top:14rpx; padding:18rpx; border-radius:24rpx; background:#fffaf6; display:flex; align-items:center; gap:14rpx; border:1rpx dashed #ffd9bd; }
.image-plus { width:58rpx; height:58rpx; border-radius:20rpx; background:#fff; color:#ff7a45; display:flex; align-items:center; justify-content:center; font-size:38rpx; }
.image-title { color:#3a2a1f; font-size:24rpx; font-weight:950; }.image-desc { margin-top:6rpx; color:#9b7560; font-size:21rpx; }
.preview-row { margin-top:14rpx; display:grid; grid-template-columns:repeat(3,1fr); gap:12rpx; }
.preview { position:relative; height:150rpx; border-radius:22rpx; overflow:hidden; background:#fff3e7; }.preview image { width:100%; height:100%; }
.remove { position:absolute; right:8rpx; top:8rpx; width:34rpx; height:34rpx; border-radius:50%; background:rgba(63,36,50,.72); color:#fff; display:flex; align-items:center; justify-content:center; }
.safe-title { color:#3a2a1f; font-size:25rpx; font-weight:950; }.safe-line { margin-top:8rpx; color:#9b7560; font-size:22rpx; line-height:1.55; }.safe-line.strong { color:#ff7a45; font-weight:900; }
.submit { margin-top:24rpx; }
</style>
