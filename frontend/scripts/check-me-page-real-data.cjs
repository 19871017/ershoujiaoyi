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
  'getChatConversations',
  'listNotifications',
  'markNotificationRead',
  'listOrders',
  'isSafeNotificationTargetUrl',
  'isTabBarNotificationTargetUrl',
  'isValidNotificationNo',
  'assertNotificationItem',
  "const publishRoles = ['SELLER', 'BOTH']",
  "const canPublish = computed(() => profile.videoVerified && publishRoles.includes(String(profile.mainRole || '').toUpperCase()))",
  "const sellerEntryTitleText = computed(() => canPublish.value ? '卖家认证' : '申请卖家认证')",
  "const trustTagText = computed(() => canPublish.value ? '已认证卖家' : '普通买家')",
  'Object.assign(profile, emptyProfile)',
  'Object.assign(balance, emptyBalance)',
  '<view class="ops-card ds-card">',
  "const notificationUnread = computed(() => notifications.value.filter((item) => !item.read).length)",
  'const recentActionNotice = computed(() => {',
  'notifications.value.filter((item) => item.targetUrl && isSafeNotificationTargetUrl(item.targetUrl))',
  "return safeRows.find((item) => !item.read) || safeRows[0] || null",
  '<view v-if="recentActionNotice" class="ops-action tapable" @click="openRecentActionNotice">',
  "const chatUnread = computed(() => chatConversations.value.reduce((sum, item) => sum + Math.max(0, item.unreadCount), 0))",
  "const pendingPayCount = computed(() => buyerOrders.value.filter((item) => item.status === 'PENDING_PAY').length)",
  "const pendingShipCount = computed(() => sellerOrders.value.filter((item) => item.status === 'PAID').length)",
  "const pendingReceiveCount = computed(() => buyerOrders.value.filter((item) => item.status === 'SHIPPED').length)",
  'const orderTodoTotal = computed(() => pendingPayCount.value + pendingShipCount.value + pendingReceiveCount.value + afterSalesCount.value)',
  'async function refreshOperationalSummary()',
  "listNotifications('ALL', 50)",
  'getChatConversations()',
  "listOrders('buyer', 'ALL')",
  "listOrders('seller', 'ALL')",
  'assertNotificationList(noticeRows)',
  'assertConversationListResponse(chatRows)',
  'assertOrderList(buyerRows)',
  'assertOrderList(sellerRows)',
  'async function openRecentActionNotice()',
  'const read = await markNotificationRead(item.notificationNo)',
  'assertNotificationItem(read)',
  "throw new Error('me notification read response mismatch')",
  "console.warn('me notification read mutation failed'",
  'function navigateToNoticeTarget(item: NotificationItemResponse): void',
  "console.warn('me notification target navigation failed'",
  'if (isTabBarNotificationTargetUrl(item.targetUrl)) uni.switchTab(route)',
  'else uni.navigateTo(route)',
  "opsError.value = '运营待办暂时不可用，请稍后刷新'",
  "console.warn('me operational summary load failed', { error })",
  "return safe > 99 ? '99+' : String(safe)",
  "pendingPay: { role: 'buyer', status: 'PENDING_PAY' }",
  "pendingShip: { role: 'seller', status: 'PAID' }",
  "pendingReceive: { role: 'buyer', status: 'SHIPPED' }",
  "status: 'REFUNDING'",
  "uni.navigateTo({ url: `/pages/order/list/index?role=${target.role}&status=${target.status}` })",
  "export type MenuActionKey = 'afterSales'",
  "{ icon: '🌸', label: '我的售后', key: 'afterSales' }",
  "function openMenu(item: MeMenuItem)",
  "if (item.key === 'afterSales')",
  "openOrderStatus('afterSales')",
  '<view v-if="canPublish" class="seller-entry-card ds-card tapable" @click="goPublishForm">',
  '<view class="seller-verify-fab tapable" @click="goVideoVerify">',
  'uni.navigateTo({ url: \'/pages/user/identity/index?tab=video\' })',
  "uni.showToast({ title: '请先完成卖家认证', icon: 'none' })"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-derived/fail-closed marker: ${marker}`)
}

if (!/async function openRecentActionNotice\(\)[\s\S]*if \(!isValidNotificationNo\(item\.notificationNo\)\)[\s\S]*if \(!item\.targetUrl \|\| !isSafeNotificationTargetUrl\(item\.targetUrl\)\)[\s\S]*const read = await markNotificationRead\(item\.notificationNo\)[\s\S]*assertNotificationItem\(read\)[\s\S]*read\.notificationNo !== item\.notificationNo[\s\S]*notifications\.value = notifications\.value\.map[\s\S]*console\.warn\('me notification read mutation failed'[\s\S]*navigateToNoticeTarget\(item\)/s.test(source)) {
  failures.push(`${file}: recent actionable notice must validate id/target and wait for backend read ack before navigation`)
}

if (!/function navigateToNoticeTarget\(item: NotificationItemResponse\): void\s*\{[\s\S]*if \(!item\.targetUrl \|\| !isSafeNotificationTargetUrl\(item\.targetUrl\)\)[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('me notification target navigation failed'[\s\S]*if \(isTabBarNotificationTargetUrl\(item\.targetUrl\)\) uni\.switchTab\(route\)[\s\S]*else uni\.navigateTo\(route\)[\s\S]*catch \(error\)/s.test(source)) {
  failures.push(`${file}: recent actionable notice navigation must use safe target checks and handle navigation failures`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('me page uses backend-derived profile/wallet data and avoids static trust markers')
