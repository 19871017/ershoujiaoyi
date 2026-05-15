<template>
  <view class="page-shell identity-page">
    <view class="hero ds-card">
      <view>
        <view class="kicker">♡ 认证中心</view>
        <view class="page-title">身份与视频认证</view>
        <view class="page-desc">实名用于提现和纠纷处理；视频认证通过后，会在你的个人主页顶部展示给其他用户。</view>
      </view>
      <view class="hero-icon">🪪</view>
    </view>

    <view class="video-card ds-card">
      <view class="section-head">
        <view>
          <view class="section-title">视频认证</view>
          <view class="status-text">当前状态：{{ videoStatusText }}</view>
        </view>
        <view class="status-pill" :class="videoStatusClass">{{ videoStatusText }}</view>
      </view>
      <view class="video-box tapable" @click="chooseVideo">
        <view class="video-play">▶</view>
        <view>
          <view class="upload-title">{{ videoUrl ? '已选择视频认证资料' : '录制/上传 10 秒真人认证视频' }}</view>
          <view class="upload-desc">建议展示本人正脸并口播“小原圈视频认证”，审核通过后对外显示认证卖家标识。</view>
          <view v-if="videoUrl" class="video-url">{{ videoUrl }}</view>
        </view>
      </view>
      <button class="primary-btn" :disabled="submittingVideo || profile.videoVerified" @click="submitVideo">
        {{ submittingVideo ? '提交中...' : profile.videoVerified ? '视频认证已通过' : '提交视频认证' }}
      </button>
    </view>

    <view class="form-card ds-card">
      <view class="section-title">实名认证资料</view>
      <input v-model.trim="form.name" class="input" placeholder="真实姓名" />
      <input v-model.trim="form.idTail" class="input" maxlength="4" type="number" placeholder="证件号码后四位" />
      <view class="upload tapable" @click="choose">
        <view class="upload-icon">＋</view>
        <view>
          <view class="upload-title">实名认证资料提交暂不可用</view>
          <view class="upload-desc">当前仅校验填写格式；暂不提交实名审核。</view>
        </view>
      </view>
      <button class="primary-btn" @click="submit">校验实名认证草稿</button>
    </view>

    <view class="check-card ds-card">
      <view class="section-title">审核检查项</view>
      <view v-for="item in checks" :key="item" class="check-row">✓ {{ item }}</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketFile } from '../../../api/modules/media'
import { getMyProfile, submitVideoIdentity, type UserProfileResponse } from '../../../api/modules/user'

const form = reactive({ name: '', idTail: '' })
const videoUrl = ref('')
const submittingVideo = ref(false)
const profile = reactive<UserProfileResponse>({ userId: 0, nickname: '', mainRole: 'UNVERIFIED', videoIdentityStatus: 'UNVERIFIED', videoVerified: false })
const checks = ['姓名与收款账户一致', '证件凭证已打码', '视频认证需真人出镜', '账号无高风险举报', '提现前需通过平台审核']
const videoStatusText = computed(() => profile.videoVerified ? '已通过' : profile.videoIdentityStatus === 'PENDING' ? '审核中' : profile.videoIdentityStatus === 'REJECTED' ? '已拒绝' : '未认证')
const videoStatusClass = computed(() => profile.videoVerified ? 'approved' : profile.videoIdentityStatus === 'PENDING' ? 'pending' : profile.videoIdentityStatus === 'REJECTED' ? 'rejected' : 'unverified')

function choose() {
  uni.showModal({
    title: '实名认证提交暂不可用',
    content: '证件类实名资料暂无法提交，请先完成视频认证或稍后再试。',
    showCancel: false
  })
}

function chooseVideo() {
  uni.chooseVideo({
    sourceType: ['camera', 'album'],
    compressed: true,
    maxDuration: 10,
    async success(res) {
      if (!res.tempFilePath || res.tempFilePath.startsWith('local://') || res.tempFilePath.includes('placeholder')) {
        uni.showToast({ title: '视频资料无效，请重新选择', icon: 'none' })
        return
      }
      uni.showLoading({ title: '上传视频中' })
      try {
        const ticket = await createMediaUploadTicket({
          scene: 'VIDEO_IDENTITY',
          contentType: guessVideoContentType(res.tempFilePath),
          fileSize: Math.max(1, res.size ?? 1),
          filename: fileNameFromPath(res.tempFilePath)
        })
        const uploaded = await uploadMediaTicketFile(ticket, res.tempFilePath)
        videoUrl.value = uploaded.storageUrl
        uni.showToast({ title: '视频已上传', icon: 'none' })
      } catch (error) {
        videoUrl.value = ''
        uni.showToast({ title: error instanceof Error ? error.message : '视频上传失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    fail() {
      uni.showToast({ title: '未选择视频认证资料', icon: 'none' })
    }
  })
}

function fileNameFromPath(path: string) {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'video-identity.mp4'
  return last.includes('.') ? last : `${last}.mp4`
}

function guessVideoContentType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.mov')) return 'video/quicktime'
  if (lower.endsWith('.m4v')) return 'video/x-m4v'
  return 'video/mp4'
}

async function loadProfile() {
  try {
    Object.assign(profile, await getMyProfile())
  } catch (error) {
    uni.showToast({ title: '认证状态暂时不可用', icon: 'none' })
  }
}

async function submitVideo() {
  if (profile.videoVerified) return uni.showToast({ title: '视频认证已通过', icon: 'none' })
  if (!videoUrl.value) return uni.showToast({ title: '请先上传视频认证资料', icon: 'none' })
  if (submittingVideo.value) return
  submittingVideo.value = true
  try {
    await submitVideoIdentity({ videoUrl: videoUrl.value, description: '小原圈真人视频认证' })
    await loadProfile()
    uni.showModal({
      title: '视频认证已提交',
      content: '资料已由平台接收，当前认证状态已重新读取平台资料；通过后，其他用户可在你的个人主页顶部看到“视频认证卖家”。',
      showCancel: false,
      success: () => uni.navigateTo({ url: '/pages/notification/index' })
    })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '视频认证提交失败', icon: 'none' })
  } finally {
    submittingVideo.value = false
  }
}

function submit() {
  if (!form.name || form.name.length < 2) return uni.showToast({ title: '请填写真实姓名', icon: 'none' })
  if (!/^\d{4}$/.test(form.idTail)) return uni.showToast({ title: '请填写证件号码后四位', icon: 'none' })
  uni.showModal({
    title: '实名认证提交暂不可用',
    content: '实名认证草稿已通过格式校验；实名审核提交暂不可用。',
    showCancel: false
  })
}

onMounted(loadProfile)
</script>

<style scoped>
.identity-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.video-card,.form-card,.check-card{margin-top:18rpx;padding:24rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;gap:18rpx;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:26rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:38rpx}.video-card{border-color:#ffb37c;background:linear-gradient(135deg,#fff2e4,#fffaf6)}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.status-text,.page-desc,.upload-desc{margin-top:8rpx;color:#9b7560;font-size:23rpx;line-height:1.5}.status-pill{flex:none;padding:9rpx 16rpx;border-radius:999rpx;background:#fff;color:#9b7560;font-size:21rpx;font-weight:950}.status-pill.approved{background:#ecfdf5;color:#059669}.status-pill.pending{background:#fff7ed;color:#f97316}.status-pill.rejected{background:#fef2f2;color:#dc2626}.input{box-sizing:border-box;width:100%;height:78rpx;margin-top:16rpx;padding:0 20rpx;border-radius:24rpx;background:#fffaf6;border:1rpx solid #ffd9bd;color:#3a2a1f;font-size:24rpx}.upload,.video-box{margin-top:16rpx;padding:18rpx;border-radius:24rpx;background:#fffaf6;border:1rpx solid #ffd9bd;display:flex;align-items:center;gap:16rpx}.video-box{border-color:#ffb37c}.upload-icon,.video-play{width:58rpx;height:58rpx;border-radius:20rpx;background:#fff3e7;color:#ff7a45;display:flex;align-items:center;justify-content:center;font-size:34rpx}.video-play{border-radius:50%;background:linear-gradient(135deg,#ff7a45,#ff3f8d);color:#fff;font-size:23rpx}.upload-title{color:#3a2a1f;font-size:24rpx;font-weight:950}.video-url{margin-top:8rpx;color:#b45309;font-size:19rpx;word-break:break-all}.check-row{margin-top:14rpx;color:#7b5542;font-size:23rpx}.primary-btn{margin-top:20rpx}
</style>
