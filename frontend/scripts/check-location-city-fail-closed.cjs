const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/location/city/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const forbiddenMarkers = [
  'const selected = ref(\'深圳\')',
  "setTimeout(() => uni.switchTab({ url: '/pages/tabbar/home/index' }), 300)",
  '@click="selected = city"',
  '城市偏好接口尚未接入，未保存为正式位置偏好'
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: city picker must not keep local-only selection or claim no-op success: ${marker}`)
}

const requiredMarkers = [
  "import { computed, onMounted, reactive, ref } from 'vue'",
  "import { getMyProfile, updateMyProfile } from '../../../api/modules/user'",
  "const profile = reactive({ userId: 0, nickname: '', mainRole: 'UNVERIFIED', city: '', bio: '' })",
  "async function loadProfile()",
  'const selected = ref(\'\')',
  'function selectCity(city: string)',
  'updateMyProfile({ nickname: profile.nickname, mainRole: profile.mainRole, city: selected.value, bio: profile.bio })',
  '城市偏好已保存至服务端资料',
  '城市偏好保存失败，未修改服务端资料',
  '资料接口加载失败，未展示本地城市偏好样例'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing backend-backed city preference marker: ${marker}`)
}

if (/uni\.showModal\(\{[\s\S]*title:\s*['"]城市偏好接口尚未接入['"]/.test(source)) {
  failures.push(`${file}: city picker must not present interface-not-connected copy once backend profile persistence is wired`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('location city page persists city preference via backend profile API and no longer behaves as local-only no-op')

