const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/notification/index.vue'
const supportFiles = [
  'src/pages/notification/notification-helpers.ts'
]
const content = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')

const forbidden = [
  'reactive([',
  'ORDER-20260507-001',
  'receiverId=102',
  'item.read=true',
  'notice.read = true',
  '已完成担保支付',
  '举报已受理',
  '通知接口暂未接入'
]

let failed = false
for (const marker of forbidden) {
  if (content.includes(marker)) {
    console.error(`${file}: notification center must not use static trust/order/chat data or local read mutations: ${marker}`)
    failed = true
  }
}

const required = [
  "import { listNotifications, markNotificationRead",
  'const notices = ref<NotificationItemResponse[]>([])',
  "await listNotifications('ALL', 50)",
  'const totalUnread = computed(() => notices.value.filter((item) => !item.read).length)',
  'const activeUnreadCount = computed(() => filtered.value.filter((item) => !item.read).length)',
  'function unreadCountByType(type: NoticeType): number',
  'function markVisibleRead()',
  'await markNotificationRead(item.notificationNo)',
  'notification batch read response mismatch',
  "console.warn('notification batch read mutation failed'",
  '批量已读暂时无法更新，请稍后重试',
  'isSafeNotificationTargetUrl',
  'isSafeAfterSalesDetailTargetUrl',
  'isSafeOrderDetailTargetUrl',
  'isTabBarNotificationTargetUrl',
  'isValidNotificationNo',
  'function assertNotificationItem(value: unknown): asserts value is NotificationItemResponse',
  'function assertNotificationList(value: unknown): asserts value is NotificationItemResponse[]',
  'assertNotificationList(response)',
  'assertNotificationItem(read)',
  'notification read response mismatch',
  "console.warn('notification list load failed'",
  "console.warn('notification read mutation failed'",
  "console.warn('notification target navigation failed'",
  "item.targetUrl && isSafeNotificationTargetUrl(item.targetUrl)",
  "value.startsWith('/pages/after-sales/detail/index?')",
  "value.startsWith('/pages/order/detail/index?')",
  "const afterSalesNo = params.get('afterSalesNo') || ''",
  "const orderNo = params.get('orderNo') || ''",
  "/^AS-[A-Za-z0-9][A-Za-z0-9_-]{5,63}$/.test(afterSalesNo) && /^OD-[0-9]{1,10}$/.test(orderNo)",
  "params.forEach((_value, key) => { keys.push(key) })",
  "keys.length === 1 && keys[0] === 'orderNo' && /^OD-[0-9]{1,10}$/.test(orderNo)",
  'if (isTabBarNotificationTargetUrl(item.targetUrl)) uni.switchTab(route)',
  '通知跳转地址无效，未打开页面',
  '通知编号无效，未更新已读状态',
  '已读状态暂时无法更新，请稍后重试',
  '通知暂时不可用，请稍后刷新',
  '通知页面暂时无法打开，请稍后重试'
]

for (const marker of required) {
  if (!content.includes(marker)) {
    console.error(`${file}: missing fail-closed notification marker: ${marker}`)
    failed = true
  }
}

if (!/function assertNotificationItem\(value: unknown\): asserts value is NotificationItemResponse[\s\S]*item\.targetUrl != null && \(typeof item\.targetUrl !== 'string' \|\| !isSafeNotificationTargetUrl\(item\.targetUrl\)\)[\s\S]*throw new Error\('notification invalid targetUrl'\)/s.test(content)) {
  console.error(`${file}: invalid backend notification targetUrl must fail closed during response validation`)
  failed = true
}

if (!/export function isSafeAfterSalesDetailTargetUrl\(value: string\): boolean[\s\S]*const params = new URLSearchParams\(query\)[\s\S]*const afterSalesNo = params\.get\('afterSalesNo'\) \|\| ''[\s\S]*const orderNo = params\.get\('orderNo'\) \|\| ''[\s\S]*\^AS-\[A-Za-z0-9\]\[A-Za-z0-9_-\]\{5,63\}\$[\s\S]*\^OD-\[0-9\]\{1,10\}\$/s.test(content)) {
  console.error(`${file}: after-sales notification target must require canonical afterSalesNo and orderNo before navigation`)
  failed = true
}

if (!/export function isSafeOrderDetailTargetUrl\(value: string\): boolean[\s\S]*const params = new URLSearchParams\(query\)[\s\S]*const keys: string\[\] = \[\][\s\S]*params\.forEach\(\(_value, key\) => \{ keys\.push\(key\) \}\)[\s\S]*const orderNo = params\.get\('orderNo'\) \|\| ''[\s\S]*keys\.length === 1 && keys\[0\] === 'orderNo' && \/\^OD-\[0-9\]\{1,10\}\$\/\.test\(orderNo\)/s.test(content)) {
  console.error(`${file}: order notification target must require a canonical backend orderNo and no extra query before navigation`)
  failed = true
}

if (!/async function openNotice\(item: NotificationItemResponse\)[\s\S]*const read = await markNotificationRead\(item\.notificationNo\)[\s\S]*assertNotificationItem\(read\)[\s\S]*read\.notificationNo !== item\.notificationNo[\s\S]*notices\.value = notices\.value\.map/s.test(content)) {
  console.error(`${file}: notification read mutation must validate backend response before updating local read state`)
  failed = true
}

if (!/async function markVisibleRead\(\)[\s\S]*filtered\.value\.filter\(\(item\) => !item\.read && isValidNotificationNo\(item\.notificationNo\)\)[\s\S]*const read = await markNotificationRead\(item\.notificationNo\)[\s\S]*assertNotificationItem\(read\)[\s\S]*read\.notificationNo !== item\.notificationNo \|\| read\.read !== true[\s\S]*notices\.value = notices\.value\.map/s.test(content)) {
  console.error(`${file}: batch read must only update each notification after a matching backend read acknowledgement`)
  failed = true
}

if (!/function navigateToNotificationTarget\(item: NotificationItemResponse\): void[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('notification target navigation failed'[\s\S]*try\s*\{[\s\S]*catch \(error\)/s.test(content)) {
  console.error(`${file}: notification target navigation must handle async and synchronous navigation failures`)
  failed = true
}

if (!/function navigateToNotificationTarget\(item: NotificationItemResponse\): void[\s\S]*isTabBarNotificationTargetUrl\(item\.targetUrl\)[\s\S]*uni\.switchTab\(route\)[\s\S]*else uni\.navigateTo\(route\)/s.test(content)) {
  console.error(`${file}: notification navigation must switchTab for tabbar targets and navigateTo for stack pages`)
  failed = true
}

if (failed) process.exit(1)
console.log('notification real-data/fail-closed check passed')
