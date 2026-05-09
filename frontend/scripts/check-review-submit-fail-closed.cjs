const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/review/submit/index.vue'
const content = fs.readFileSync(path.join(root, file), 'utf8')

const forbiddenCopies = [
  '评价已填写',
  '后续接入评价接口后再更新信用分和榜单热度',
  "uni.showModal({title:'评价已填写'"
]

const requiredCopies = [
  '评价接口尚未接入',
  '不会更新信用分或榜单热度'
]

let failed = false
for (const copy of forbiddenCopies) {
  if (content.includes(copy)) {
    console.error(`${file}: forbidden fake-success review copy found: ${copy}`)
    failed = true
  }
}

for (const copy of requiredCopies) {
  if (!content.includes(copy)) {
    console.error(`${file}: missing fail-closed review copy: ${copy}`)
    failed = true
  }
}

if (failed) process.exit(1)
console.log('review submit fail-closed check passed')
