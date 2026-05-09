const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')

const checks = [
  {
    file: 'src/pages/system/privacy/index.vue',
    forbidden: [
      '平台担保交易',
      '资金先进入担保账户',
      '风控凭证'
    ]
  },
  {
    file: 'src/pages/system/empty/index.vue',
    forbidden: [
      '使用平台担保，降低交易风险',
      '聊天页举报'
    ]
  },
  {
    file: 'src/pages/user/address/index.vue',
    forbidden: [
      '用于平台担保交易发货',
      '同城约看'
    ]
  }
]

let failed = false
for (const check of checks) {
  const absolute = path.join(root, check.file)
  const content = fs.readFileSync(absolute, 'utf8')
  for (const copy of check.forbidden) {
    if (content.includes(copy)) {
      console.error(`${check.file}: forbidden static platform trust copy found: ${copy}`)
      failed = true
    }
  }
}

if (failed) process.exit(1)
console.log('static platform trust copy check passed')
