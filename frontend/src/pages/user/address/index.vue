<template>
  <view class="page-shell address-page">
    <view class="page-title">地址管理</view>
    <view class="page-desc">用于订单发货、售后联系等正式平台地址记录。</view>

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
    <view v-if="loading" class="empty-card ds-card">地址加载中...</view>
    <view v-else-if="!addresses.length" class="empty-card ds-card">暂无平台地址记录，请新增后再用于订单发货。</view>
    <view v-for="item in addresses" :key="item.addressId" class="address-card ds-card">
      <view class="address-head">
        <view class="person">{{ item.name }} {{ item.mobile }}</view>
        <view v-if="item.isDefault" class="tag">默认</view>
      </view>
      <view class="address-text">{{ item.provinceCity }} {{ item.detail }}</view>
      <view class="card-actions">
        <button class="mini-btn" @click="editAddress(item)">编辑</button>
        <button class="mini-btn" @click="setDefault(item.addressId)">设默认</button>
        <button class="mini-btn danger" @click="removeAddress(item.addressId)">删除</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { deleteUserAddress, listAddresses, saveUserAddress, setDefaultUserAddress, type UserAddressResponse } from '../../../api/modules/address'

interface Address { addressId: number; name: string; mobile: string; provinceCity: string; detail: string; isDefault: boolean }
const addresses = ref<Address[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive<Address>({ addressId: 0, name: '', mobile: '', provinceCity: '', detail: '', isDefault: false })

function toAddress(item: UserAddressResponse): Address {
  return {
    addressId: item.addressId,
    name: item.name,
    mobile: item.mobile,
    provinceCity: item.provinceCity,
    detail: item.detail,
    isDefault: item.isDefault
  }
}

function resetForm() {
  Object.assign(form, { addressId: 0, name: '', mobile: '', provinceCity: '', detail: '', isDefault: false })
}

async function loadAddresses() {
  loading.value = true
  try {
    addresses.value = (await listAddresses()).map(toAddress)
  } catch (error) {
    addresses.value = []
    uni.showToast({ title: '地址加载失败，请稍后重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function validateAddressForm() {
  if (!form.name.trim()) return '请填写收货人'
  if (!/^1\d{10}$/.test(form.mobile.trim())) return '请填写正确手机号'
  if (!form.provinceCity.trim() || !form.detail.trim()) return '请填写完整地址'
  return ''
}

async function saveAddress() {
  const error = validateAddressForm()
  if (error) return uni.showToast({ title: error, icon: 'none' })
  if (saving.value) return
  saving.value = true
  try {
    await saveUserAddress({
      addressId: form.addressId || undefined,
      name: form.name.trim(),
      mobile: form.mobile.trim(),
      provinceCity: form.provinceCity.trim(),
      detail: form.detail.trim(),
      isDefault: form.isDefault
    })
    resetForm()
    await loadAddresses()
    uni.showToast({ title: '地址已保存', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: '保存地址失败，请稍后重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}
function editAddress(item: Address) { Object.assign(form, item) }
async function setDefault(id: number) {
  try {
    await setDefaultUserAddress(id)
    await loadAddresses()
    uni.showToast({ title: '默认地址已更新', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: '设置默认地址失败', icon: 'none' })
  }
}
function removeAddress(id: number) {
  uni.showModal({
    title: '确认删除',
    content: '删除后该收货地址将从平台地址记录移除。',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteUserAddress(id)
        await loadAddresses()
        uni.showToast({ title: '地址已删除', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: '删除地址失败', icon: 'none' })
      }
    }
  })
}

onMounted(loadAddresses)
</script>

<style scoped>
.address-page {
  min-height:100vh;
  padding-top:18rpx;
  padding-bottom:44rpx;
  background:radial-gradient(circle at 12% 0%,rgba(255,202,150,.26),transparent 28%),radial-gradient(circle at 88% 16%,rgba(255,226,214,.42),transparent 24%),linear-gradient(180deg,#fff8f0 0%,#fffdfa 55%,#fff5ee 100%);
}
.form-card,.address-card,.empty-card {
  margin-top:16rpx;
  padding:22rpx;
  border-color:rgba(255,217,189,.78);
  background:linear-gradient(180deg,rgba(255,255,255,.98),rgba(255,248,242,.97));
  box-shadow:0 15rpx 30rpx rgba(132,70,36,.08);
}
.form-title,.section-title {
  margin-top:20rpx;
  color:#342116;
  font-size:29rpx;
  font-weight:950;
  letter-spacing:.16rpx;
}
.form-card .form-title { margin-top:0; }
.input,.textarea {
  box-sizing:border-box;
  width:100%;
  margin-top:14rpx;
  padding:20rpx;
  border:1rpx solid rgba(255,217,189,.78);
  border-radius:24rpx;
  background:linear-gradient(180deg,#fffdf9,#fff8f1);
  color:#342116;
  font-size:24rpx;
  font-weight:650;
}
.textarea {
  height:140rpx;
  line-height:1.55;
}
.default-row {
  display:flex;
  align-items:center;
  gap:12rpx;
  margin-top:16rpx;
  color:#7b5542;
  font-size:23rpx;
  font-weight:850;
}
.check {
  display:flex;
  flex:0 0 auto;
  align-items:center;
  justify-content:center;
  width:34rpx;
  height:34rpx;
  border:1rpx solid rgba(255,217,189,.82);
  border-radius:50%;
  background:rgba(255,255,255,.92);
  color:transparent;
  box-shadow:inset 0 0 0 1rpx rgba(255,255,255,.45);
}
.check.active {
  border-color:#ef6f3f;
  background:#ef6f3f;
  color:#fffaf4;
  box-shadow:0 6rpx 12rpx rgba(255,122,69,.14);
}
.primary-btn {
  height:72rpx;
  margin-top:18rpx;
  border-radius:999rpx;
  background:linear-gradient(135deg,#ef6f3f,#ff8b76);
  color:#fffaf4;
  font-size:25rpx;
  font-weight:950;
  line-height:72rpx;
  box-shadow:0 12rpx 24rpx rgba(255,122,69,.16);
}
.empty-card {
  color:#8f6b57;
  font-size:23rpx;
  font-weight:700;
  line-height:1.5;
  text-align:center;
}
.address-head {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:14rpx;
}
.person {
  color:#342116;
  font-size:28rpx;
  font-weight:950;
  letter-spacing:.12rpx;
}
.tag {
  flex:0 0 auto;
  padding:7rpx 14rpx;
  border:1rpx solid rgba(239,111,63,.18);
  border-radius:999rpx;
  background:#fff3e7;
  color:#df6735;
  font-size:20rpx;
  font-weight:950;
}
.address-text {
  margin-top:12rpx;
  color:#8f6b57;
  font-size:23rpx;
  font-weight:650;
  line-height:1.55;
}
.card-actions {
  display:flex;
  flex-wrap:wrap;
  justify-content:flex-end;
  gap:12rpx;
  margin-top:16rpx;
}
.mini-btn {
  height:54rpx;
  margin:0;
  padding:0 20rpx;
  border:1rpx solid rgba(255,217,189,.78);
  border-radius:999rpx;
  background:rgba(255,255,255,.92);
  color:#7b5542;
  font-size:21rpx;
  font-weight:900;
  line-height:54rpx;
}
.mini-btn.danger {
  border-color:rgba(244,63,94,.16);
  background:#fff7f7;
  color:#be123c;
}
</style>
