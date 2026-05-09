const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/system/account-security/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  '<view class="hero-score">92</view>',
  '138****8000',
  "const devices=[{id:1,name:'Windows Chrome'",
  "{id:2,name:'iPhone Safari'",
  '今天 22:18',
  '昨天 19:05',
  'city:\'杭州\'',
  'city:\'上海\'',
  '安全校验流程已记录'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden static security/trust marker found: ${marker}`)
}

const requiredMarkers = [
  '安全状态以服务端账号风控接口为准',
  '账号安全接口尚未接入',
  '未展示本地登录设备样例',
  '未执行任何账号安全变更',
  'const devices = computed(() => []',
  'const securityScore = computed(() => \'--\')'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed account-security marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('account security page avoids static trust/device data and fails closed')
