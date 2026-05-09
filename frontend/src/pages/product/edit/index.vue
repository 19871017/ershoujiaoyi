<template>
  <view class="page-shell edit-page">
    <view class="hero ds-card"><view><view class="kicker">♡ 编辑宝贝</view><view class="page-title">商品编辑</view><view class="page-desc">修改后会重新进入审核，已锁定或已售出的商品不能继续编辑。</view></view><view class="hero-icon">✏️</view></view>
    <view class="form-card ds-card">
      <view class="section-title">基础信息</view>
      <input v-model.trim="form.title" class="field" placeholder="宝贝标题" />
      <textarea v-model.trim="form.description" class="field area" placeholder="描述成色、尺码、瑕疵和购买建议" />
      <input v-model="form.price" class="field" type="digit" placeholder="价格" />
      <view class="chip-row"><view v-for="item in categories" :key="item" class="chip tapable" :class="{ active: form.category === item }" @click="form.category = item">{{ item }}</view></view>
      <view class="chip-row"><view v-for="item in conditions" :key="item" class="chip tapable" :class="{ active: form.condition === item }" @click="form.condition = item">{{ item }}</view></view>
    </view>
    <view class="form-card ds-card">
      <view class="section-title">图片与交易</view>
      <view class="image-row"><view v-for="img in images" :key="img" class="image-box"><text>图</text><view class="remove tapable" @click="removeImage(img)">×</view></view><view v-if="images.length < 9" class="image-box add tapable" @click="chooseImage">＋</view></view>
      <input v-model.trim="form.city" class="field" placeholder="城市" />
      <view class="rule"><switch :checked="form.serverTradeOnly" @change="toggleTradePreference('serverTradeOnly')" /> <text>交易方式以服务端订单与支付状态为准</text></view>
      <view class="rule"><switch :checked="form.serverChatRecord" @change="toggleTradePreference('serverChatRecord')" /> <text>聊天记录以服务端会话为准</text></view>
    </view>
    <view v-if="loadError" class="fail-card">{{ loadError }}</view>
    <button class="primary-btn" :disabled="saving || !backendProductId" @click="save">{{ saving ? '提交中...' : '提交修改审核' }}</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { createMediaUploadTicket } from '../../../api/modules/media'
import { getProductDetail, updateProduct } from '../../../api/modules/product'

const productId = ref('')
const saving = ref(false)
const loadError = ref('')
const categories = ['衣物','鞋袜','小用品']
const conditions = ['全新未拆','几乎全新','轻微使用','明显使用痕迹']
const images = ref<string[]>([])
const form = reactive({ title:'', description:'', price:'', category:'', condition:'', city:'', serverTradeOnly:false, serverChatRecord:false })
const backendProductId = computed(() => isValidBackendProductId(productId.value) ? Number(productId.value) : 0)
function isValidBackendProductId(value: string) { return /^\d+$/.test(value) && Number(value) > 0 }
function readQuery(){const pages=getCurrentPages(); const current=pages.length?pages[pages.length-1] as unknown as {options?:Record<string,string>}:undefined; const hash=typeof window!=='undefined'?new URLSearchParams(window.location.hash.split('?')[1]||''):undefined; productId.value=current?.options?.productId||hash?.get('productId')||productId.value}
async function loadDetail(){
  loadError.value = ''
  if (!backendProductId.value) { loadError.value = '缺少有效商品编号，未加载本地样例商品'; return }
  try { const detail = await getProductDetail(backendProductId.value); form.title = detail.title; form.description = detail.description || ''; form.price = String(detail.price); images.value = detail.imageUrls || [] }
  catch { form.title=''; form.description=''; form.price=''; images.value=[]; loadError.value = '商品详情加载失败，未展示本地样例商品' }
}
function chooseImage(){
  const remain = Math.max(1, 9 - images.value.length)
  uni.chooseImage({ count: remain, sizeType: ['compressed'], sourceType: ['album','camera'], async success(res){
    try {
      const issuedUrls: string[] = []
      for (const path of res.tempFilePaths.slice(0, remain)) {
        if (path.startsWith('local://') || path.includes('placeholder')) throw new Error('商品图片无效，请重新选择')
        const ticket = await createMediaUploadTicket({ scene:'PRODUCT_IMAGE', contentType:imageContentType(path), fileSize:300_000, filename:fileNameFromPath(path) })
        issuedUrls.push(ticket.storageUrl)
      }
      images.value = [...images.value, ...issuedUrls].slice(0, 9)
      uni.showToast({title:'商品图片上传票据已生成，需提交修改审核后才会更新商品图片',icon:'none'})
    } catch (error) { uni.showToast({ title: error instanceof Error ? error.message : '商品图片上传票据创建失败', icon:'none' }) }
  }, fail(){ uni.showToast({ title:'未选择图片', icon:'none' }) } })
}
function removeImage(url:string){ images.value = images.value.filter(item => item !== url); uni.showToast({ title:'商品图片移除需提交修改审核后生效', icon:'none' }) }
// product edit media/trade controls are read-only until backend update contract supports them
function toggleTradePreference(_field: 'serverTradeOnly' | 'serverChatRecord') { uni.showToast({ title:'交易展示项暂未接入后端状态，未执行任何变更', icon:'none' }) }
function fileNameFromPath(path: string) { const clean = path.split('?')[0] || ''; const last = clean.split('/').pop() || 'product-image.jpg'; return last.includes('.') ? last : `${last}.jpg` }
function imageContentType(path: string) { const lower = path.toLowerCase(); if (lower.endsWith('.png')) return 'image/png'; if (lower.endsWith('.webp')) return 'image/webp'; return 'image/jpeg' }
function validate(){ if(!form.title || !form.price) return '请补全标题和价格'; if(Number(form.price)<=0) return '价格需大于0'; if(images.value.some(url => url.startsWith('local://') || url.includes('placeholder') || !url.startsWith('/uploads/product-image/'))) return '图片需先完成平台上传票据校验'; return '' }
async function save(){
  if (!backendProductId.value) return uni.showToast({ title:'缺少有效商品编号，未提交修改', icon:'none' })
  const message = validate(); if(message) return uni.showToast({title:message,icon:'none'})
  saving.value=true
  try { await updateProduct(backendProductId.value, { title:form.title, description:form.description, price:form.price, imageUrls:images.value }); uni.showModal({title:'已提交审核',content:'商品修改已保存，重新进入平台审核，通过后再公开展示。',showCancel:false,success:()=>uni.navigateTo({url:`/pages/product/detail/index?productId=${backendProductId.value}`})}) }
  catch(error){ uni.showToast({ title:error instanceof Error ? error.message : '商品修改保存失败，保存失败时不会展示本地成功状态', icon:'none' }) }
  finally { saving.value=false }
}
onMounted(()=>{readQuery(); void loadDetail()})
</script>
<style scoped>
.edit-page{background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%)}.hero,.form-card{margin-top:18rpx;padding:22rpx;border-color:#ffd9bd}.hero{display:flex;justify-content:space-between;align-items:center;background:linear-gradient(135deg,#fff,#fff3e7)}.kicker{color:#ff7a45;font-size:22rpx;font-weight:950}.hero-icon{width:82rpx;height:82rpx;border-radius:28rpx;background:#ff7a45;color:#fff;display:flex;align-items:center;justify-content:center;font-size:34rpx}.section-title{color:#3a2a1f;font-size:29rpx;font-weight:950}.field{margin-top:16rpx;min-height:78rpx;padding:0 18rpx;border-radius:22rpx;background:#fffaf6;border:1rpx solid #ffd9bd;font-size:24rpx}.area{height:150rpx;padding-top:18rpx}.chip-row{margin-top:14rpx;display:flex;gap:10rpx;flex-wrap:wrap}.chip{padding:12rpx 18rpx;border-radius:999rpx;background:#fff;border:1rpx solid #ffd9bd;color:#9b7560;font-size:22rpx;font-weight:900}.chip.active{background:#3a2a1f;color:#fff}.image-row{margin-top:16rpx;display:flex;gap:12rpx;flex-wrap:wrap}.image-box{position:relative;width:112rpx;height:112rpx;border-radius:24rpx;background:#fff3e7;display:flex;align-items:center;justify-content:center;font-size:24rpx;color:#ff7a45}.image-box.add{border:1rpx dashed #ff8fbd;color:#ff7a45;background:#fff;font-size:42rpx}.remove{position:absolute;right:4rpx;top:4rpx;width:30rpx;height:30rpx;border-radius:50%;background:#3a2a1f;color:#fff;display:flex;align-items:center;justify-content:center;font-size:22rpx}.rule{margin-top:16rpx;display:flex;align-items:center;gap:12rpx;color:#7b5542;font-size:23rpx}.primary-btn{margin-top:22rpx}
</style>
