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
  'await listNotifications(active.value)',
  'await markNotificationRead(item.notificationNo)',
  'isSafeNotificationTargetUrl',
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

if (!/async function openNotice\(item: NotificationItemResponse\)[\s\S]*const read = await markNotificationRead\(item\.notificationNo\)[\s\S]*assertNotificationItem\(read\)[\s\S]*read\.notificationNo !== item\.notificationNo[\s\S]*notices\.value = notices\.value\.map/s.test(content)) {
  console.error(`${file}: notification read mutation must validate backend response before updating local read state`)
  failed = true
}

if (!/function navigateToNotificationTarget\(item: NotificationItemResponse\): void[\s\S]*fail\(error: unknown\)[\s\S]*console\.warn\('notification target navigation failed'[\s\S]*try\s*\{\s*uni\.navigateTo\(route\)[\s\S]*catch \(error\)/s.test(content)) {
  console.error(`${file}: notification target navigation must handle async and synchronous navigation failures`)
  failed = true
}

if (failed) process.exit(1)
console.log('notification real-data/fail-closed check passed')
