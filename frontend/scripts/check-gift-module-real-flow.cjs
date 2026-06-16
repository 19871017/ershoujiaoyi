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
  'getSentGifts',
  'getRecentGiftFeed',
  'sendGift(',
  'getWalletBalance',
  'RecentGiftFeedItemResponse',
  'receiverId.value',
  'requestNo =',
  'giftList.value = []',
  'recentGiftList.value = []',
  'sentGiftList.value = []',
  '礼物暂不可用',
  '送礼失败，请稍后重试',
  '请选择礼物接收人',
  '送出礼物',
  '最新礼物流水',
  '我送出的',
  '我收到的',
  'flowRows',
  'sentFlowRows',
  'receivedFlowRows',
  'loadRecentGifts',
  'loadSentGifts',
  'xiaoyuanquan:giftsent',
  'giftTone(item.giftCode)',
  'gift-orb',
  'gift-shine',
  'isGiftImage(item.icon)',
  'isGiftImage(item.giftIcon)',
  'resolveGiftImage(item.icon)',
  'giftImageMap',
  'class="gift-img"',
  'gift-love-castle.png',
  'visibleCatalogList',
  'DEFAULT_VISIBLE_GIFT_COUNT = 3',
  'DEFAULT_VISIBLE_RECORD_COUNT = 3',
  'visibleRecentFlowRows',
  'visibleSentFlowRows',
  'visibleReceivedFlowRows',
  'recordFoldMeta',
  'toggleRecordFold',
  '展开其他',
  'toggleGiftFold',
  'effect-burst'
]
for (const marker of requiredGiftPage) {
  if (!giftPage.includes(marker)) failures.push(`gift page missing real-flow marker: ${marker}`)
}

const forbiddenGiftPage = [
  'brandLogoUrl',
  "assets/brand/xiaoyuanquan-logo-mark.png",
  "'/assets/brand/xiaoyuanquan-logo-mark.png'"
]
for (const marker of forbiddenGiftPage) {
  if (giftPage.includes(marker)) failures.push(`gift page should not render brand logo: ${marker}`)
}

const requiredApi = [
  'GiftCatalogItemResponse',
  'ReceivedGiftItemResponse',
  'RecentGiftFeedItemResponse',
  "get<GiftCatalogItemResponse[]>('/api/gifts/catalog')",
  "get<ReceivedGiftItemResponse[]>('/api/gifts/received')",
  "get<RecentGiftFeedItemResponse[]>('/api/gifts/sent')",
  "get<RecentGiftFeedItemResponse[]>('/api/gifts/recent')",
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

const giftAssets = [
  'src/assets/brand/xiaoyuanquan-logo-mark.png',
  'src/assets/gifts/gift-rose.png',
  'src/assets/gifts/gift-coffee.png',
  'src/assets/gifts/gift-star.png',
  'src/assets/gifts/gift-heart.png',
  'src/assets/gifts/gift-ribbon-box.png',
  'src/assets/gifts/gift-perfume.png',
  'src/assets/gifts/gift-crystal-shoe.png',
  'src/assets/gifts/gift-crown.png',
  'src/assets/gifts/gift-galaxy.png',
  'src/assets/gifts/gift-love-castle.png',
  'src/static/brand/xiaoyuanquan-logo-mark.png',
  'public/assets/brand/xiaoyuanquan-logo-mark.png',
  'public/assets/gifts/gift-rose.png',
  'public/assets/gifts/gift-coffee.png',
  'public/assets/gifts/gift-star.png',
  'public/assets/gifts/gift-heart.png',
  'public/assets/gifts/gift-ribbon-box.png',
  'public/assets/gifts/gift-perfume.png',
  'public/assets/gifts/gift-crystal-shoe.png',
  'public/assets/gifts/gift-crown.png',
  'public/assets/gifts/gift-galaxy.png',
  'public/assets/gifts/gift-love-castle.png'
]
for (const asset of giftAssets) {
  if (!fs.existsSync(path.join(root, asset))) failures.push(`gift visual asset missing: ${asset}`)
}

const requiredGiftImagePaths = [
  '/assets/gifts/gift-rose.png',
  '/assets/gifts/gift-coffee.png',
  '/assets/gifts/gift-star.png',
  '/assets/gifts/gift-heart.png',
  '/assets/gifts/gift-ribbon-box.png',
  '/assets/gifts/gift-perfume.png',
  '/assets/gifts/gift-crystal-shoe.png',
  '/assets/gifts/gift-crown.png',
  '/assets/gifts/gift-galaxy.png',
  '/assets/gifts/gift-love-castle.png'
]
for (const imagePath of requiredGiftImagePaths) {
  if (!giftPage.includes(imagePath)) failures.push(`gift page missing image mapping: ${imagePath}`)
}

if (failures.length) {
  console.error('gift module real-flow check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('gift module uses backend catalog/received/send flow and no static gift samples')
