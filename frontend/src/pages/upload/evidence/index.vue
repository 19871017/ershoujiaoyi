<template>
  <view class="page-shell upload-page">
    <view class="hero ds-card"><view><view class="kicker">♡ 媒体票据</view><view class="page-title">生成上传票据</view><view class="page-desc">用于售后、举报、实名、商品和聊天图片预览；本页只申请服务端上传票据，不代表业务表单已提交。</view></view><view class="hero-icon">🖼️</view></view>
    <view class="type-row"><view v-for="item in types" :key="item.value" class="chip tapable" :class="{ active: scene === item.value }" @click="scene = item.value">{{ item.label }}</view></view>
    <view class="rule-card ds-card"><view class="section-title">上传规则</view><view v-for="item in rules" :key="item" class="rule-line">{{ item }}</view></view>
    <view class="image-grid">
      <view v-for="(item,index) in images" :key="item" class="image-box"><text>{{ item }}</text><view class="remove tapable" @click="remove(index)">×</view></view>
      <view v-if="images.length < 9" class="image-box add tapable" @click="chooseImage">＋</view>
    </view>
    <textarea v-model.trim="remark" class="field area" placeholder="补充说明：例如瑕疵位置、聊天证据、物流异常、身份凭证说明" />
    <button class="primary-btn" :disabled="saving" @click="submit">{{ saving ? '校验中...' : '校验票据' }}</button>
  </view>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createMediaUploadTicket, type MediaUploadScene } from '../../../api/modules/media'

type Scene = MediaUploadScene
const scene=ref<Scene>('AFTER_SALES_EVIDENCE')
const saving=ref(false)
const remark=ref('')
const images=ref<string[]>([])
const types=[{label:'售后',value:'AFTER_SALES_EVIDENCE' as const},{label:'举报',value:'REPORT_EVIDENCE' as const},{label:'实名视频',value:'VIDEO_IDENTITY' as const},{label:'商品',value:'PRODUCT_IMAGE' as const},{label:'聊天',value:'CHAT_IMAGE' as const}]
const supportedScenes: Scene[] = ['AFTER_SALES_EVIDENCE','REPORT_EVIDENCE','VIDEO_IDENTITY','PRODUCT_IMAGE','COMMUNITY_IMAGE','CHAT_IMAGE']
const legacySceneMap: Record<string, Scene> = {
  AFTER_SALES: 'AFTER_SALES_EVIDENCE',
  REPORT: 'REPORT_EVIDENCE',
  IDENTITY: 'VIDEO_IDENTITY',
  PRODUCT: 'PRODUCT_IMAGE',
  CHAT: 'CHAT_IMAGE'
}
const rules=['最多 9 张，建议使用清晰原图','不展示真实证件完整号码，敏感信息需打码','上传票据仅完成服务端签发与本地校验，不代表举报、售后、聊天或审核已提交','提交正式业务表单前需先向服务端申请上传票据，拒绝本地临时路径和占位图']
function normalizeScene(value?: string | null) { const normalized = value ? (legacySceneMap[value] || value) : ''; return supportedScenes.includes(normalized as Scene) ? normalized as Scene : undefined }
function readQuery(){const pages=getCurrentPages(); const current=pages.length?pages[pages.length-1] as unknown as {options?:Record<string,string>}:undefined; const hash=typeof window!=='undefined'?new URLSearchParams(window.location.hash.split('?')[1]||''):undefined; const value=normalizeScene(current?.options?.scene||hash?.get('scene')); if(value) scene.value=value}
function chooseImage(){
  const remain = Math.max(1, 9 - images.value.length)
  uni.chooseImage({ count: remain, sizeType: ['compressed'], sourceType: ['album','camera'], async success(res){
    try {
      for (const path of res.tempFilePaths.slice(0, remain)) {
        if (path.startsWith('local://') || path.includes('placeholder')) throw new Error('凭证图片无效，请重新选择')
        const ticket = await createMediaUploadTicket({ scene: scene.value, contentType: imageContentType(path), fileSize: 300_000, filename: fileNameFromPath(path) })
        images.value.push(ticket.storageUrl)
      }
      images.value = images.value.slice(0, 9)
      uni.showToast({title:`已生成上传票据 ${images.value.length} 张`,icon:'none'})
    } catch (error) {
      uni.showToast({title:error instanceof Error ? error.message : '凭证上传票据创建失败',icon:'none'})
    }
  }, fail(){ uni.showToast({title:'未选择凭证图片',icon:'none'}) } })
}
function remove(index:number){images.value.splice(index,1)}
function fileNameFromPath(path: string) { const clean = path.split('?')[0] || ''; const last = clean.split('/').pop() || 'evidence.jpg'; return last.includes('.') ? last : `${last}.jpg` }
function imageContentType(path: string) { const lower = path.toLowerCase(); if (lower.endsWith('.png')) return 'image/png'; if (lower.endsWith('.webp')) return 'image/webp'; return 'image/jpeg' }
function submit(){
  if (!images.value.length) return uni.showToast({ title:'请先选择凭证图片', icon:'none' })
  if (images.value.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/'))) return uni.showToast({ title:'凭证需先完成平台上传票据校验', icon:'none' })
  saving.value = true
  uni.showModal({title:'上传票据已生成',content:`已完成 ${images.value.length} 张平台上传票据和本地校验；售后、举报或聊天等业务仍需回到对应页面提交正式表单。`,showCancel:false,success:()=>{saving.value=false}})
}
onMounted(readQuery)
</script>
<style scoped>
.upload-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.rule-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:36rpx}.type-row{margin-top:18rpx;display:flex;gap:12rpx;overflow-x:auto}.chip{flex:none;padding:13rpx 20rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.chip.active{background:#3a2a1f;color:#fff;border-color:#3a2a1f}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.rule-line{margin-top:10rpx;color:#7b5542;font-size:23rpx;line-height:1.45}.image-grid{margin-top:18rpx;display:grid;grid-template-columns:repeat(3,1fr);gap:14rpx}.image-box{position:relative;min-height:166rpx;border-radius:28rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:18rpx;word-break:break-all;padding:14rpx;border:1rpx solid #ffd9bd}.image-box.add{background:#fff;border-style:dashed;color:#ff7a45;font-size:48rpx}.remove{position:absolute;right:8rpx;top:8rpx;width:34rpx;height:34rpx;border-radius:50%;background:#3a2a1f;color:#fff;display:flex;align-items:center;justify-content:center;font-size:24rpx}.field{margin-top:18rpx;width:100%;box-sizing:border-box;padding:18rpx;border-radius:22rpx;background:#fff;border:1rpx solid #ffd9bd;color:#3a2a1f;font-size:23rpx}.area{height:150rpx}.primary-btn{margin-top:20rpx}
</style>
