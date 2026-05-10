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
  'getAccountSecurity',
  'security.value = await getAccountSecurity()',
  '服务端暂未返回登录设备记录',
  '未展示本地登录设备样例',
  '未执行任何账号安全变更',
  'security.value?.maskedPhone',
  'const securityScore = computed(() => security.value?.securityScore || \'--\')'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed account-security marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('account security page avoids static trust/device data and fails closed')
