const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/location/city/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const forbiddenMarkers = [
  'const selected = ref(\'深圳\')',
  'uni.showToast({ title: `已选择${selected.value}`',
  "setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home/index' }), 300)",
  '@click="selected = city"'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: city picker must not locally save or claim a selected city without backend/system persistence: ${marker}`)
}

const requiredMarkers = [
  "const selected = ref('')",
  '@click="selectCity(city)"',
  'function selectCity(city: string)',
  'function saveCity()',
  '城市偏好接口尚未接入，未保存为正式同城位置',
  '当前未执行任何位置变更',
  'const canConfirm = computed(() => Boolean(selected.value))',
  ':disabled="!canConfirm"'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed city-selection marker: ${marker}`)
}

if (/selected\.value\s*=\s*city[\s\S]*uni\.showToast\(\{ title: `已选择/.test(source)) {
  failures.push(`${file}: selecting a chip may update UI highlight, but must not show saved/selected success copy as a persisted preference`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('location city page keeps manual city choice local-only and fails closed until backend/system preference persistence exists')
