const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/components/GlobalTicker.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const forbiddenOrder = 'items.value = [...announcementItems, ...giftItems, ...noticeItems].slice(0, 6)'
const requiredOrder = 'items.value = [...giftItems, ...announcementItems, ...noticeItems].slice(0, 6)'

if (source.includes(forbiddenOrder)) {
  failures.push(`${file}: global ticker must not place announcements before gift feed`)
}

if (!source.includes(requiredOrder)) {
  failures.push(`${file}: global ticker must prioritize gift feed before announcements and notices`)
}

if (!source.includes('getRecentGiftFeed')) {
  failures.push(`${file}: global ticker must use the real recent gift feed`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global ticker prioritizes recent gift feed before announcements and notifications')
