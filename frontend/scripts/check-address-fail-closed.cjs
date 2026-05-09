const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const source = fs.readFileSync(path.join(root, 'src/pages/user/address/index.vue'), 'utf8')

const failures = []

const forbiddenMarkers = [
  'addresses.value.unshift',
  'addresses.value[idx]',
  'addresses.value.forEach((item) => { item.isDefault',
  'addresses.value = addresses.value.filter',
  '地址已校验',
  '默认地址已校验',
  '地址已移出页面',
  '雨哥',
  '王同学',
  '138****8000',
  '139****6226'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`address page must not contain local-only success/demo marker: ${marker}`)
}

if (!/地址服务暂未接入|不会保存为正式收货地址|未保存/.test(source)) {
  failures.push('address page must explicitly fail closed when backend address persistence is unavailable')
}

if (!/addresses = ref<Address\[\]>\(\[\]\)/.test(source)) {
  failures.push('address list should not seed static sample addresses as if they were user data')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('address page fails closed without local-only persistence')
