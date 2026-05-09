const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/notification/index.vue'
const content = fs.readFileSync(path.join(root, file), 'utf8')

const forbidden = [
  'reactive([',
  'ORDER-20260507-001',
  'receiverId=102',
  'item.read=true',
  '已完成担保支付',
  '举报已受理'
]

let failed = false
for (const marker of forbidden) {
  if (content.includes(marker)) {
    console.error(`${file}: notification center must not use static trust/order/chat data or local read mutations: ${marker}`)
    failed = true
  }
}

const required = [
  'const notices = ref<NoticeItem[]>([])',
  'const loadError = ref',
  'const loading = ref',
  '通知接口暂未接入，未展示任何本地样例消息',
  '未调用后端已读接口，未执行任何通知状态变更'
]

for (const marker of required) {
  if (!content.includes(marker)) {
    console.error(`${file}: missing fail-closed notification marker: ${marker}`)
    failed = true
  }
}

if (failed) process.exit(1)
console.log('notification real-data/fail-closed check passed')
