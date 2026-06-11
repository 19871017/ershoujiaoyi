<template>
  <view class="page-shell upload-page">
    <view class="hero ds-card"><view><view class="kicker">♡ 媒体上传</view><view class="page-title">上传媒体文件</view><view class="page-desc">用于售后、举报、实名、商品和聊天媒体上传；本页只完成平台文件上传，不代表业务表单已提交。</view></view><view class="hero-icon">🖼️</view></view>
    <view v-if="errorText" class="status-card ds-card danger"><view class="section-title">暂不能上传</view><view class="status-desc">{{ errorText }}</view></view>
    <view class="type-row"><view v-for="item in types" :key="item.value" class="chip tapable" :class="{ active: scene === item.value }" @click="selectScene(item.value)">{{ item.label }}</view></view>
    <view class="rule-card ds-card"><view class="section-head"><view><view class="section-title">上传规则</view><view class="section-desc">当前场景：{{ currentSceneLabel }}，返回地址会按后端场景前缀校验。</view></view><view class="scene-chip">{{ images.length }}/{{ maxImages }}</view></view><view v-for="item in rules" :key="item" class="rule-line">{{ item }}</view></view>
    <view class="image-grid">
      <view v-for="(item,index) in images" :key="item" class="image-box"><text>{{ item }}</text><view class="remove tapable" @click="remove(index)">×</view></view>
      <view v-if="images.length < maxImages" class="image-box add tapable" @click="chooseMedia">＋</view>
    </view>
    <textarea v-model.trim="remark" class="field area" placeholder="补充说明：例如瑕疵位置、聊天记录、物流异常、身份材料说明" />
    <button class="primary-btn" :disabled="saving || uploading || !!errorText" @click="submit">{{ saving ? '校验中...' : '校验上传结果' }}</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createMediaUploadTicket, uploadMediaTicketBlob, uploadMediaTicketFile, type MediaUploadScene } from '../../../api/modules/media'

type Scene = MediaUploadScene
type ChooseImageFile = { name?: string; type?: string; size?: number }
type ChooseImageResult = { tempFilePaths?: string[]; tempFiles?: ChooseImageFile[] }
type ChooseVideoFile = { name?: string; path?: string; tempFilePath?: string; type?: string; size?: number }
type ChooseVideoResult = { tempFilePath?: string; size?: number; name?: string; type?: string; tempFile?: ChooseVideoFile; file?: ChooseVideoFile }
const launchReadinessMarkers = [
  '媒体文件已完成平台上传',
  '上传媒体文件',
  '校验上传结果',
  '媒体文件已上传',
  '上传结果不代表业务已受理'
]

const maxImages = 9
const backendOrderNoPattern = /^OD-[0-9]{1,10}$/
const scene=ref<Scene>('AFTER_SALES_EVIDENCE')
const orderNo=ref('')
const errorText=ref('')
const fatalRouteError=ref(false)
const saving=ref(false)
const uploading=ref(false)
const remark=ref('')
const images=ref<string[]>([])
const types=[{label:'售后',value:'AFTER_SALES_EVIDENCE' as const},{label:'举报',value:'REPORT_EVIDENCE' as const},{label:'实名视频',value:'VIDEO_IDENTITY' as const},{label:'商品',value:'PRODUCT_IMAGE' as const},{label:'社区',value:'COMMUNITY_IMAGE' as const},{label:'聊天图片',value:'CHAT_IMAGE' as const},{label:'聊天视频',value:'CHAT_VIDEO' as const}]
const supportedScenes: Scene[] = ['AFTER_SALES_EVIDENCE','REPORT_EVIDENCE','VIDEO_IDENTITY','PRODUCT_IMAGE','COMMUNITY_IMAGE','CHAT_IMAGE','CHAT_VIDEO']
const storagePrefixByScene: Record<Scene, string> = {
  AFTER_SALES_EVIDENCE: '/uploads/evidence/after-sales/',
  REPORT_EVIDENCE: '/uploads/report-evidence/',
  VIDEO_IDENTITY: '/uploads/video-identity/',
  PRODUCT_IMAGE: '/uploads/product-image/',
  COMMUNITY_IMAGE: '/uploads/community-image/',
  CHAT_IMAGE: '/uploads/chat-image/',
  CHAT_VOICE: '/uploads/chat-voice/',
  CHAT_VIDEO: '/uploads/chat-video/'
}
const legacySceneMap: Record<string, Scene> = {
  AFTER_SALES: 'AFTER_SALES_EVIDENCE',
  REPORT: 'REPORT_EVIDENCE',
  IDENTITY: 'VIDEO_IDENTITY',
  PRODUCT: 'PRODUCT_IMAGE',
  CHAT: 'CHAT_IMAGE'
}
const rules=['最多 9 张，建议使用清晰原图','不展示真实证件完整号码，敏感信息需打码','媒体文件已完成平台上传，不代表举报、售后、聊天或审核已提交','提交正式业务表单前需先完成平台上传，拒绝临时路径和无效图片']
const currentSceneLabel = computed(() => types.find(item => item.value === scene.value)?.label || '媒体')
function normalizeScene(value?: string | null): Scene | undefined {
  const normalized = value ? (legacySceneMap[value] || value) : ''
  return supportedScenes.includes(normalized as Scene) ? normalized as Scene : undefined
}
function isValidBackendOrderNo(value: string): boolean { return backendOrderNoPattern.test(value) }
function hasInvalidTempMediaPath(path: string): boolean {
  return path.startsWith('local://') || path.startsWith('data:') || path.toLowerCase().includes('placeholder')
}
function isPickerCancel(error: unknown): boolean {
  const message = String((error as { errMsg?: string })?.errMsg || '').trim().toLowerCase()
  return message === 'cancel' || message === 'chooseimage:fail cancel' || message === 'choosevideo:fail cancel'
}
function decodeRouteValue(fieldName: string, value: string): { ok: boolean; value: string } {
  try {
    return { ok: true, value: decodeURIComponent(value) }
  } catch (error) {
    console.warn('upload evidence route decode failed', { fieldName, rawLength: value.length, rawPreview: value.slice(0, 24), error })
    return { ok: false, value: '' }
  }
}
function readQuery(): void {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] as unknown as { options?: Record<string, string> } : undefined
  const hash = typeof window !== 'undefined' ? new URLSearchParams(window.location.hash.split('?')[1] || '') : undefined
  const rawScene = current?.options?.scene || hash?.get('scene') || ''
  const rawOrderNo = current?.options?.orderNo || hash?.get('orderNo') || ''
  const sceneParam = decodeRouteValue('scene', rawScene)
  const orderParam = decodeRouteValue('orderNo', rawOrderNo)
  if (!sceneParam.ok || !orderParam.ok) {
    fatalRouteError.value = true
    errorText.value = '上传入口参数无效，请返回上一页重新进入'
    return
  }
  const routeScene = normalizeScene(sceneParam.value)
  if (sceneParam.value && !routeScene) {
    console.warn('upload evidence invalid route scene', { rawLength: sceneParam.value.length, rawPreview: sceneParam.value.slice(0, 24) })
    fatalRouteError.value = true
    errorText.value = '上传场景无效，请返回上一页重新进入'
    return
  }
  if (orderParam.value && !isValidBackendOrderNo(orderParam.value)) {
    console.warn('upload evidence invalid route orderNo', { rawLength: orderParam.value.length, rawPreview: orderParam.value.slice(0, 24) })
    fatalRouteError.value = true
    errorText.value = '订单号格式无效，请从售后详情重新进入补充票据'
    return
  }
  if (routeScene) scene.value = routeScene
  orderNo.value = isValidBackendOrderNo(orderParam.value) ? orderParam.value : ''
  refreshSceneError()
}
function refreshSceneError(): void {
  if (fatalRouteError.value) return
  errorText.value = scene.value === 'AFTER_SALES_EVIDENCE' && !orderNo.value ? '缺少有效订单号，请从售后详情进入补充票据' : ''
}
function selectScene(value: Scene): void {
  if (fatalRouteError.value) return uni.showToast({ title:errorText.value, icon:'none' })
  if (uploading.value) return uni.showToast({ title:'文件上传中，请稍后切换场景', icon:'none' })
  scene.value = value
  images.value = []
  refreshSceneError()
}
function chooseMedia(): void {
  if (fatalRouteError.value) return uni.showToast({ title:errorText.value, icon:'none' })
  if (uploading.value) return uni.showToast({ title:'文件上传中，请稍后再选', icon:'none' })
  refreshSceneError()
  if (errorText.value) return uni.showToast({ title:errorText.value, icon:'none' })
  if (scene.value === 'VIDEO_IDENTITY' || scene.value === 'CHAT_VIDEO') {
    chooseVideoIdentity()
    return
  }
  chooseImage()
}
function chooseVideoIdentity(): void {
  if (images.value.length >= 1) return uni.showToast({ title:'视频认证上传票据每次仅保留 1 条', icon:'none' })
  const sceneSnapshot: Scene = scene.value === 'CHAT_VIDEO' ? 'CHAT_VIDEO' : 'VIDEO_IDENTITY'
  uploading.value = true
  try {
    uni.chooseVideo({ sourceType:['camera','album'], compressed:true, maxDuration:60, async success(res: ChooseVideoResult){
      try {
        const path = res.tempFilePath
        if (!path || hasInvalidTempMediaPath(path)) throw new Error('视频文件无效，请重新选择')
        const blob = await readH5TempVideoBlob(path)
        const contentType = videoContentType(path, videoTypeFromPickerResult(res, blob), sceneSnapshot)
        const filename = videoFileNameFromPickerResult(res, contentType)
        const ticket = await createMediaUploadTicket({ scene: sceneSnapshot, contentType, fileSize: Math.max(1, blob?.size ?? res.size ?? 1), filename })
        const uploaded = blob
          ? await uploadMediaTicketBlob(ticket, new Blob([blob], { type: contentType }), filename)
          : await uploadMediaTicketFile(ticket, path)
        const storageUrl = validatedStorageUrl(sceneSnapshot, uploaded.storageUrl)
        if (scene.value !== sceneSnapshot) return uni.showToast({ title:'上传期间切换了场景，请在当前场景重新选择视频', icon:'none' })
        images.value = [storageUrl]
        uni.showToast({title:'视频文件已上传 1 条',icon:'none'})
      } catch (error) {
        console.warn('upload evidence video upload failed', { scene: sceneSnapshot, error })
        uni.showToast({title:error instanceof Error ? error.message : '视频上传票据创建失败',icon:'none'})
      } finally {
        uploading.value = false
      }
    }, fail(error: unknown){ uploading.value = false; console.warn('upload evidence choose video failed', { error }); uni.showToast({title:isPickerCancel(error)?'未选择视频文件':'无法打开视频选择器，请检查相册或相机权限后重试',icon:'none'}) } })
  } catch (error) {
    uploading.value = false
    console.warn('upload evidence choose video failed', { error })
    uni.showToast({title:'无法打开视频选择器，请检查相册或相机权限后重试',icon:'none'})
  }
}
function chooseImage(): void {
  if (scene.value === 'VIDEO_IDENTITY') return chooseVideoIdentity()
  const sceneSnapshot = scene.value
  const remain = maxImages - images.value.length
  if (remain <= 0) return uni.showToast({ title:`媒体文件最多上传 ${maxImages} 张，请先移除一张后再添加`, icon:'none' })
  uploading.value = true
  try {
    uni.chooseImage({ count: remain, sizeType: ['compressed'], sourceType: ['album','camera'], async success(res: ChooseImageResult){
      try {
        const paths = (res.tempFilePaths || []).slice(0, remain)
        if (!paths.length) throw new Error('未选择到有效媒体图片，请重新选择')
        const files = res.tempFiles || []
        const uploadedUrls: string[] = []
        for (const [index, path] of paths.entries()) {
          if (hasInvalidTempMediaPath(path)) throw new Error('媒体图片无效，请重新选择')
          const file = files[index]
          const ticket = await createMediaUploadTicket({ scene: sceneSnapshot, contentType: imageContentType(path, file?.type), fileSize: imageFileSize(file), filename: fileNameFromPath(file?.name || path) })
          const uploaded = await uploadMediaTicketFile(ticket, path)
          uploadedUrls.push(validatedStorageUrl(sceneSnapshot, uploaded.storageUrl))
        }
        if (scene.value !== sceneSnapshot) return uni.showToast({ title:'上传期间切换了场景，请在当前场景重新选择文件', icon:'none' })
        images.value = [...images.value, ...uploadedUrls].slice(0, maxImages)
        uni.showToast({title:`媒体文件已上传 ${images.value.length} 张`,icon:'none'})
      } catch (error) {
        console.warn('upload evidence image upload failed', { scene: sceneSnapshot, error })
        uni.showToast({title:error instanceof Error ? error.message : '媒体文件上传失败',icon:'none'})
      } finally {
        uploading.value = false
      }
    }, fail(error: unknown){ uploading.value = false; console.warn('upload evidence choose image failed', { scene: sceneSnapshot, error }); uni.showToast({title:isPickerCancel(error)?'未选择媒体图片':'无法打开图片选择器，请检查相册或相机权限后重试',icon:'none'}) } })
  } catch (error) {
    uploading.value = false
    console.warn('upload evidence choose image failed', { scene: sceneSnapshot, error })
    uni.showToast({title:'无法打开图片选择器，请检查相册或相机权限后重试',icon:'none'})
  }
}
function remove(index:number): void {
  images.value.splice(index,1)
}
function hasInvalidStorageUrl(sceneValue: Scene, storageUrl: unknown): boolean {
  const prefix = storagePrefixByScene[sceneValue]
  if (typeof storageUrl !== 'string') return true
  if (!storageUrl.startsWith(prefix)) return true
  if (storageUrl.startsWith('local://') || storageUrl.startsWith('blob:') || storageUrl.startsWith('data:')) return true
  if (storageUrl.includes('\\') || storageUrl.includes('..') || storageUrl.includes('//')) return true

  const lower = storageUrl.toLowerCase()
  if (lower.includes('placeholder') || lower.includes('%2e') || lower.includes('%2f') || lower.includes('%5c')) return true

  const relativePath = storageUrl.slice(prefix.length)
  return !relativePath || relativePath.split('/').some(segment => !segment || segment === '..')
}
function validatedStorageUrl(sceneValue: Scene, storageUrl: string): string {
  const prefix = storagePrefixByScene[sceneValue]
  if (hasInvalidStorageUrl(sceneValue, storageUrl)) {
    console.warn('upload evidence invalid storageUrl', { scene: sceneValue, expectedPrefix: prefix, storageUrl })
    throw new Error('媒体文件未完成对应场景上传校验，请重新选择')
  }
  return storageUrl
}
function fileNameFromPath(path: string): string {
  const clean = path.split('?')[0] || ''
  const last = clean.split('/').pop() || 'evidence.jpg'
  return last.includes('.') ? last : `${last}.jpg`
}
function imageContentType(path: string, fallbackType?: string): string {
  const normalizedType = fallbackType?.toLowerCase()
  if (normalizedType === 'image/png' || normalizedType === 'image/webp' || normalizedType === 'image/jpeg') return normalizedType

  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}
function imageFileSize(file?: ChooseImageFile): number {
  return Math.max(1, Math.min(Number(file?.size || 600_000), 10_000_000))
}
function videoFallbackName(contentType: string): string {
  if (contentType === 'video/quicktime') return 'video-identity.mov'
  if (contentType === 'video/x-m4v') return 'video-identity.m4v'
  return 'video-identity.mp4'
}
function normalizedVideoContentType(contentType: unknown, sceneValue: Scene = 'VIDEO_IDENTITY'): string | undefined {
  if (typeof contentType !== 'string') return undefined
  const lower = contentType.trim().toLowerCase()
  if (lower === 'video/mp4' || lower === 'video/mpeg4') return 'video/mp4'
  if (lower === 'video/quicktime' || lower === 'video/mov') return 'video/quicktime'
  if (lower === 'video/x-m4v' || lower === 'video/m4v') return 'video/x-m4v'
  if (lower === 'video/webm' && sceneValue === 'CHAT_VIDEO') return 'video/webm'
  if (lower === 'video/webm') throw new Error('暂不支持 WebM 视频，请选择 MP4、MOV 或 M4V')
  if (lower.startsWith('video/')) throw new Error('暂不支持该视频格式，请选择 MP4、MOV 或 M4V')
  return undefined
}
function videoContentType(path: string, fallbackType?: unknown, sceneValue: Scene = 'VIDEO_IDENTITY'): string {
  const normalized = normalizedVideoContentType(fallbackType, sceneValue)
  if (normalized) return normalized
  const lower = path.toLowerCase()
  if (lower.endsWith('.mov')) return 'video/quicktime'
  if (lower.endsWith('.m4v')) return 'video/x-m4v'
  if (lower.endsWith('.webm') && sceneValue === 'CHAT_VIDEO') return 'video/webm'
  if (lower.endsWith('.webm')) throw new Error('暂不支持 WebM 视频，请选择 MP4、MOV 或 M4V')
  return 'video/mp4'
}
async function readH5TempVideoBlob(path: string): Promise<Blob | undefined> {
  if (!path.toLowerCase().startsWith('blob:')) return undefined
  if (typeof fetch !== 'function') throw new Error('当前环境不支持读取视频文件，请换用 MP4 文件或稍后重试')
  const response = await fetch(path)
  if (!response.ok) throw new Error('视频文件读取失败，请重新选择')
  return response.blob()
}
function videoTypeFromPickerResult(result: ChooseVideoResult, blob?: Blob): unknown {
  return blob?.type || result.type || result.tempFile?.type || result.file?.type
}
function videoFileNameFromPickerResult(result: ChooseVideoResult, contentType: string): string {
  const fallback = videoFallbackName(contentType)
  const name = result.name || result.tempFile?.name || result.file?.name
  if (name && name.includes('.')) return name
  return fileNameFromPath(result.tempFilePath || result.tempFile?.path || result.file?.path || fallback)
}
function submit(): void {
  if (saving.value) return
  refreshSceneError()
  if (errorText.value) return uni.showToast({ title:errorText.value, icon:'none' })
  if (!images.value.length) return uni.showToast({ title:'请先上传媒体文件', icon:'none' })
  if (images.value.some(url => hasInvalidStorageUrl(scene.value, url))) return uni.showToast({ title:'媒体文件需先完成当前场景上传校验', icon:'none' })
  saving.value = true
  const modalOptions = {
    title:'媒体文件已上传',
    content:`当前仅返回 ${images.value.length} 个媒体文件存储地址；上传结果不代表业务已受理，售后、举报或聊天等业务仍需回到对应页面提交正式表单。`,
    showCancel:false,
    fail:(error: unknown)=>{
      console.warn('upload evidence result modal failed', { scene: scene.value, count: images.value.length, error })
      uni.showToast({ title:'媒体文件已上传，但结果弹窗无法显示，请稍后查看页面记录', icon:'none' })
    },
    complete:()=>{ saving.value=false }
  }
  try {
    uni.showModal(modalOptions)
  } catch (error) {
    saving.value = false
    console.warn('upload evidence result modal failed', { scene: scene.value, count: images.value.length, error })
    uni.showToast({ title:'媒体文件已上传，但结果弹窗无法显示，请稍后查看页面记录', icon:'none' })
  }
}
function initializeUploadEvidence(): void {
  try {
    readQuery()
  } catch (error) {
    fatalRouteError.value = true
    console.warn('upload evidence initialize failed', { error })
    errorText.value = '上传入口参数无效，请返回上一页重新进入'
  }
}
onMounted(initializeUploadEvidence)
</script>
<style scoped>
.upload-page{min-height:100vh;padding-top:18rpx;padding-bottom:44rpx;background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%)}
.hero,.rule-card,.status-card{margin-top:14rpx;padding:20rpx;border-color:rgba(255,217,189,.78);box-shadow:0 14rpx 30rpx rgba(132,70,36,.08)}
.hero{margin-top:0;display:flex;justify-content:space-between;align-items:center;gap:16rpx;background:linear-gradient(135deg,rgba(255,255,255,.98),rgba(255,244,234,.96))}
.rule-card,.status-card{background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97))}
.status-card.danger{border-color:#fecaca;background:#fff7f7}
.kicker{color:#df6735;font-size:20rpx;font-weight:950;letter-spacing:.16rpx}.hero-icon{width:70rpx;height:70rpx;border-radius:24rpx;background:linear-gradient(135deg,#ef6f3f,#ff8b76);color:#fffaf4;display:flex;align-items:center;justify-content:center;font-size:32rpx;box-shadow:0 12rpx 24rpx rgba(255,122,69,.16);flex:0 0 auto}
.type-row{margin-top:14rpx;display:flex;gap:10rpx;overflow-x:auto}.chip{flex:none;padding:11rpx 16rpx;border-radius:999rpx;background:rgba(255,255,255,.96);border:1rpx solid rgba(255,217,189,.78);color:#8f6b57;font-size:20rpx;font-weight:900}.chip.active{background:#fff3e7;color:#df6735;border-color:rgba(239,111,63,.62);box-shadow:0 8rpx 16rpx rgba(255,122,69,.10)}
.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx;margin-bottom:4rpx}.section-title{color:#342116;font-size:26rpx;font-weight:950;letter-spacing:.16rpx}.section-desc,.status-desc{margin-top:7rpx;color:#8f6b57;font-size:20rpx;line-height:1.43;font-weight:650}.status-desc{color:#be123c}.scene-chip{flex:0 0 auto;padding:8rpx 14rpx;border:1rpx solid rgba(239,111,63,.20);border-radius:999rpx;background:#fff3e7;color:#df6735;font-size:20rpx;font-weight:950}.rule-line{margin-top:8rpx;color:#7b5542;font-size:20rpx;line-height:1.48;font-weight:650}
.image-grid{margin-top:14rpx;display:grid;grid-template-columns:repeat(3,1fr);gap:10rpx}.image-box{position:relative;min-height:146rpx;border-radius:24rpx;background:linear-gradient(180deg,#fffdf9,#fff8f1);display:flex;align-items:center;justify-content:center;font-size:17rpx;word-break:break-all;padding:12rpx;border:1rpx solid rgba(255,217,189,.78);color:#7b5542;font-weight:700}.image-box.add{background:rgba(255,255,255,.96);border-style:dashed;color:#df6735;font-size:42rpx}.remove{position:absolute;right:8rpx;top:8rpx;width:32rpx;height:32rpx;border-radius:50%;background:#3a261a;color:#fff;display:flex;align-items:center;justify-content:center;font-size:22rpx}
.field{margin-top:14rpx;width:100%;box-sizing:border-box;padding:16rpx;border-radius:22rpx;background:linear-gradient(180deg,#fffdf9,#fff8f1);border:1rpx solid rgba(255,217,189,.78);color:#342116;font-size:21rpx;font-weight:650}.area{height:136rpx;line-height:1.52}.primary-btn{margin-top:18rpx;background:linear-gradient(135deg,#ef6f3f,#ff8b76);color:#fffaf4;box-shadow:0 12rpx 24rpx rgba(255,122,69,.16)}
</style>
