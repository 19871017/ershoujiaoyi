const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const checks = [
  {
    file: 'src/pages/closet/index.vue',
    forbidden: [
      'item.status = item.status ===',
      "uni.showToast({ title: statusLabel(item.status), icon: 'none' })"
    ]
  },
  {
    file: 'src/pages/product/detail/index.vue',
    forbidden: [
      'const demoDetails:',
      'defaultDemoDetail',
      'detail.value = demoDetails.find',
      'favorited.value = !favorited.value',
      '已收藏，可在我的收藏查看',
      '已取消收藏'
    ]
  },
  {
    file: 'src/pages/community/detail/index.vue',
    forbidden: [
      'followed = !followed',
      'liked = !liked',
      '{{ liked ? 129 : 128 }}'
    ]
  },
  {
    file: 'src/pages/ranking/index.vue',
    forbidden: [
      'item.followed = !item.followed',
      '已取消关注 ${item.name}',
      '已关注 ${item.name}`'
    ]
  },
  {
    file: 'src/pages/user/public-profile/index.vue',
    forbidden: [
      'followed.value=!followed.value',
      "followed.value?'已关注':'已取消关注'"
    ]
  },
  {
    file: 'src/pages/tabbar/message/index.vue',
    forbidden: [
      'item.followed = !item.followed',
      '已取消关注 ${item.name}',
      '已关注 ${item.name}`'
    ]
  },
  {
    file: 'src/pages/favorite/index.vue',
    forbidden: [
      'favorites.splice(idx, 1)',
      "uni.showToast({ title: '已取消收藏', icon: 'none' })"
    ]
  },
  {
    file: 'src/pages/system/settings/index.vue',
    forbidden: [
      'item.on = !item.on',
      "`${item.label}${item.on ? '已开启' : '已关闭'}`",
      "on: true },\n  { icon: '📍', label: '定位权限'",
      "label: '开发预览模式', desc: '当前阶段保留 mock 和开发接口', switch: true, on: true"
    ]
  }
]

let failed = false
for (const check of checks) {
  const absolute = path.join(root, check.file)
  const content = fs.readFileSync(absolute, 'utf8')
  for (const copy of check.forbidden) {
    if (content.includes(copy)) {
      console.error(`${check.file}: forbidden local-only sensitive success found: ${copy}`)
      failed = true
    }
  }
}

if (failed) process.exit(1)
console.log('sensitive fake-success check passed')
