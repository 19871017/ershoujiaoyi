const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const giftPage = fs.readFileSync(path.join(root, 'src/pages/gift/index.vue'), 'utf8')
const giftApi = fs.readFileSync(path.join(root, 'src/api/modules/gift.ts'), 'utf8')
const publicProfile = fs.readFileSync(path.join(root, 'src/pages/user/public-profile/index.vue'), 'utf8')
const pkg = fs.readFileSync(path.join(root, 'package.json'), 'utf8')

const forbidden = [
  "GIFT-001",
  "GIFT-002",
  "GIFT-003",
  "梨涡裙摆",
  "温柔收纳家",
  "莓莓袜铺",
  "分账状态', value: '正常'",
  "receiverId=102",
  "const gifts = reactive([",
]

const failures = []
for (const marker of forbidden) {
  if (giftPage.includes(marker)) failures.push(`gift page still contains static/fixed marker: ${marker}`)
}

const requiredGiftPage = [
  'getGiftCatalog',
  'getReceivedGifts',
  'sendGift(',
  'getWalletBalance',
  'receiverId.value',
  'requestNo =',
  'giftList.value = []',
  '礼物流水加载失败，未展示本地礼物样例',
  '送礼失败，未扣款也未展示成功状态',
  '请选择礼物接收人',
  '送出礼物',
]
for (const marker of requiredGiftPage) {
  if (!giftPage.includes(marker)) failures.push(`gift page missing real-flow marker: ${marker}`)
}

const requiredApi = [
  'GiftCatalogItemResponse',
  'ReceivedGiftItemResponse',
  "get<GiftCatalogItemResponse[]>('/api/gifts/catalog')",
  "get<ReceivedGiftItemResponse[]>('/api/gifts/received')",
  "post<SendGiftResponse>('/api/gifts/send'",
]
for (const marker of requiredApi) {
  if (!giftApi.includes(marker)) failures.push(`gift api missing marker: ${marker}`)
}

if (!publicProfile.includes('/pages/gift/index?mode=send&receiverId=')) {
  failures.push('public profile missing send-gift entry with backend receiverId')
}
if (!publicProfile.includes('送礼物')) failures.push('public profile missing visible gift action')
if (!pkg.includes('check:gift-module')) failures.push('package.json missing check:gift-module script')

if (failures.length) {
  console.error('gift module real-flow check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('gift module uses backend catalog/received/send flow and no static gift samples')
