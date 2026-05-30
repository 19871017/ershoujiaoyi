const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/tabbar/me/index.vue'
const supportFiles = [
  'src/pages/tabbar/me/me-data.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')

const failures = []

const forbiddenMarkers = [
  '信用分 98',
  '已实名 · 视频待认证 · 成交 36 单',
  '平台担保卖家',
  "const quickStats = [",
  "const orderStatus = [",
  '¥8,620.50',
  '冻结 ¥126.00 · 可提现 ¥3,280.00',
  "{ icon: '💳', label: '待付款', count: 1 }",
  "{ icon: '📦', label: '待发货', count: 2 }",
  "{ icon: '🧾', label: '待收货', count: 1 }"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static account/trust marker found: ${marker}`)
}

const requiredMarkers = [
  'getMyProfile',
  'getWalletBalance',
  "const publishRoles = ['SELLER', 'BOTH']",
  "const canPublish = computed(() => profile.videoVerified && publishRoles.includes(String(profile.mainRole || '').toUpperCase()))",
  "const sellerEntryTitleText = computed(() => canPublish.value ? '卖家认证' : '申请卖家认证')",
  "const trustTagText = computed(() => canPublish.value ? '已认证卖家' : '普通买家')",
  'Object.assign(profile, emptyProfile)',
  'Object.assign(balance, emptyBalance)',
  '<view v-if="canPublish" class="seller-entry-card ds-card tapable" @click="goPublishForm">',
  '<view class="seller-verify-fab tapable" @click="goVideoVerify">',
  'uni.navigateTo({ url: \'/pages/user/identity/index?tab=video\' })',
  "uni.showToast({ title: '请先完成卖家认证', icon: 'none' })"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived/fail-closed marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('me page uses backend-derived profile/wallet data and avoids static trust markers')
