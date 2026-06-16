const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = {
  component: 'src/components/GlobalTicker.vue',
  helpers: 'src/components/global-ticker-helpers.ts',
  style: 'src/components/global-ticker.scss'
}

const source = Object.fromEntries(
  Object.entries(files).map(([key, file]) => [key, fs.readFileSync(path.join(root, file), 'utf8')])
)
const combined = Object.values(source).join('\n')
const failures = []

const requiredMarkers = [
  'giftIcon?: string',
  'safeGiftIconUrl',
  "'/assets/gifts/'",
  '这份高光礼遇正在全场闪耀',
  '<image v-if="item.giftIcon" class="ticker-gift-icon"',
  ':src="item.giftIcon"',
  'giftIcon: safeGiftIconUrl(item.giftIcon)',
  '.ticker-gift-icon'
]

for (const marker of requiredMarkers) {
  if (!combined.includes(marker)) failures.push(`global ticker missing luxury gift marker: ${marker}`)
}

const forbiddenMarkers = [
  "return `${item.senderName} 送给 ${item.receiverName}",
  "!giftIcon.startsWith('/assets/')",
  'const visibleGiftIcon = giftIcon && !giftIcon.startsWith'
]

for (const marker of forbiddenMarkers) {
  if (combined.includes(marker)) failures.push(`global ticker still contains old gift ticker behavior: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global gift ticker luxury copy and icon guard passed')
