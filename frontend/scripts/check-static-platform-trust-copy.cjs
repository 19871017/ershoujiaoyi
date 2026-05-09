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
  },
  {
    file: 'src/pages/location/city/index.vue',
    forbidden: [
      '同城交易',
      '同城约看',
      '同城',
      'nearby',
      '离开平台聊天和支付'
    ]
  },
  {
    file: 'src/pages/community/compose/index.vue',
    forbidden: [
      '同城约看'
    ]
  },
  {
    file: 'src/pages/tabbar/message/index.vue',
    forbidden: [
      '同城约看'
    ]
  },
  {
    file: 'src/pages/chat/session-list/index.vue',
    forbidden: [
      '同城姐妹',
      '约看',
      '包邮'
    ]
  },
  {
    file: 'src/pages/tabbar/home/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看'
    ]
  },
  {
    file: 'src/pages/tabbar/publish/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看'
    ]
  },
  {
    file: 'src/pages/order/confirm/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '担保规则',
      '同城可约',
      '同城约看'
    ]
  },
  {
    file: 'src/pages/order/list/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '担保资金',
      '同城可约',
      '同城约看'
    ]
  },
  {
    file: 'src/pages/order/ship/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看',
      '同城交付',
      '同城当面交付'
    ]
  },
  {
    file: 'src/pages/product/detail/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看'
    ]
  },
  {
    file: 'src/pages/user/public-profile/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看',
      '聊天留痕'
    ]
  },
  {
    file: 'src/pages/tabbar/me/index.vue',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看'
    ]
  },
  {
    file: 'src/pages/after-sales/apply/index.vue',
    forbidden: [
      '平台介入',
      '进入人工仲裁',
      '虚假凭证',
      '影响账号信用',
      '榜单权重'
    ]
  },
  {
    file: 'src/pages/after-sales/detail/index.vue',
    forbidden: [
      '平台介入',
      '进入人工仲裁',
      '虚假凭证',
      '影响账号信用',
      '榜单权重',
      '平台凭证',
      '平台会核对',
      '平台已通过',
      '平台已驳回',
      '平台处理中'
    ]
  },
  {
    file: 'src/api/http.ts',
    forbidden: [
      '平台担保',
      '担保交易',
      '同城可约',
      '同城约看',
      '同城面交',
      '支持试穿沟通',
      '当面验货',
      '七天内确认'
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
