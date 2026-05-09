<template>
  <view class="page-shell address-page">
    <view class="page-title">地址管理</view>
    <view class="page-desc">用于订单发货、售后联系等正式服务端地址记录。</view>

    <view class="form-card ds-card">
      <view class="form-title">新增 / 编辑地址</view>
      <input v-model="form.name" class="input" placeholder="收货人姓名" />
      <input v-model="form.mobile" class="input" placeholder="手机号" type="number" />
      <input v-model="form.provinceCity" class="input" placeholder="省市区，例如 广东省 深圳市 南山区" />
      <textarea v-model="form.detail" class="textarea" placeholder="详细地址，例如街道、小区、门牌号" />
      <view class="default-row tapable" @click="form.isDefault = !form.isDefault">
        <view :class="['check', { active: form.isDefault }]">✓</view>
        <view>设为默认地址</view>
      </view>
      <button class="primary-btn" @click="saveAddress">保存地址</button>
    </view>

    <view class="section-title">我的地址</view>
    <view v-for="item in addresses" :key="item.id" class="address-card ds-card">
      <view class="address-head">
        <view class="person">{{ item.name }} {{ item.mobile }}</view>
        <view v-if="item.isDefault" class="tag">默认</view>
      </view>
      <view class="address-text">{{ item.provinceCity }} {{ item.detail }}</view>
      <view class="card-actions">
        <button class="mini-btn" @click="editAddress(item)">编辑</button>
        <button class="mini-btn" @click="setDefault(item.id)">设默认</button>
        <button class="mini-btn danger" @click="removeAddress(item.id)">删除</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
interface Address { id: number; name: string; mobile: string; provinceCity: string; detail: string; isDefault: boolean }
const addresses = ref<Address[]>([])
const form = reactive<Address>({ id: 0, name: '', mobile: '', provinceCity: '', detail: '', isDefault: false })

function validateAddressForm() {
  if (!form.name.trim()) return '请填写收货人'
  if (!/^1\d{10}$/.test(form.mobile.trim())) return '请填写正确手机号'
  if (!form.provinceCity.trim() || !form.detail.trim()) return '请填写完整地址'
  return ''
}

function showUnavailableToast(action: string) {
  uni.showToast({ title: `${action}失败：地址服务暂未接入，未保存为正式收货地址`, icon: 'none' })
}

function saveAddress() {
  const error = validateAddressForm()
  if (error) return uni.showToast({ title: error, icon: 'none' })
  showUnavailableToast('保存地址')
}
function editAddress(item: Address) { Object.assign(form, item) }
function setDefault(_id: number) { showUnavailableToast('设置默认地址') }
function removeAddress(_id: number) {
  uni.showModal({
    title: '暂不能删除',
    content: '地址服务暂未接入，当前不会删除或保存为正式收货地址。',
    showCancel: false
  })
}
</script>

<style scoped>
.address-page { background:linear-gradient(180deg,#fff7ed 0%,#fffdfa 55%,#fff7ed 100%); }
.form-card,.address-card { margin-top:18rpx; padding:22rpx; border-color:#ffd9bd; }
.form-title,.section-title { margin-top:20rpx; color:#3a2a1f; font-size:29rpx; font-weight:950; }
.input,.textarea { width:100%; box-sizing:border-box; margin-top:14rpx; padding:20rpx; border-radius:24rpx; background:#fffaf6; border:1rpx solid #ffd9bd; color:#3a2a1f; font-size:24rpx; }
.textarea { height:132rpx; }
.default-row { margin-top:16rpx; display:flex; align-items:center; gap:12rpx; color:#7b5542; font-size:23rpx; font-weight:850; }
.check { width:34rpx; height:34rpx; border-radius:50%; background:#fff; border:1rpx solid #ffd9bd; display:flex; align-items:center; justify-content:center; color:transparent; }
.check.active { background:#ff7a45; color:#fff; border-color:#ff7a45; }
.primary-btn { margin-top:18rpx; height:72rpx; line-height:72rpx; border-radius:999rpx; background:#ff7a45; color:#fff; font-size:25rpx; font-weight:950; }
.address-head { display:flex; justify-content:space-between; align-items:center; }
.person { color:#3a2a1f; font-size:28rpx; font-weight:950; }
.tag { padding:7rpx 14rpx; border-radius:999rpx; background:#fff3e7; color:#ff7a45; font-size:20rpx; font-weight:950; }
.address-text { margin-top:12rpx; color:#9b7560; font-size:23rpx; line-height:1.5; }
.card-actions { margin-top:16rpx; display:flex; justify-content:flex-end; gap:12rpx; }
.mini-btn { margin:0; padding:0 20rpx; height:54rpx; line-height:54rpx; border-radius:999rpx; background:#fff; border:1rpx solid #ffd9bd; color:#7b5542; font-size:21rpx; font-weight:900; }
.mini-btn.danger { color:#be123c; }
</style>
