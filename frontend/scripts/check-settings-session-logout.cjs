const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const settingsFile = 'src/pages/system/settings/index.vue'
const storeFile = 'src/store/modules/user.ts'
const shimFile = 'src/shims-uni.d.ts'
const settings = fs.readFileSync(path.join(root, settingsFile), 'utf8')
const store = fs.readFileSync(path.join(root, storeFile), 'utf8')
const shim = fs.readFileSync(path.join(root, shimFile), 'utf8')
const failures = []

const requiredSettingsMarkers = [
  "import { useUserStore } from '../../../store/modules/user'",
  'const userStore = useUserStore()',
  'userStore.clearSession()',
  "uni.removeStorageSync('token')",
  "uni.removeStorageSync('user')",
  "uni.reLaunch({ url: '/pages/auth/login/index' })"
]

for (const marker of requiredSettingsMarkers) {
  if (!settings.includes(marker)) failures.push(`${settingsFile}: missing secure logout session-clear marker: ${marker}`)
}

if (/if \(res\.confirm\)\s*uni\.navigateTo\(\{ url: '\/pages\/auth\/login\/index' \}\)/.test(settings)) {
  failures.push(`${settingsFile}: logout must not only navigate to login without clearing in-memory and persisted session state`)
}

if (!/function logout\(\) \{[\s\S]*userStore\.clearSession\(\)[\s\S]*uni\.removeStorageSync\('token'\)[\s\S]*uni\.removeStorageSync\('user'\)[\s\S]*uni\.reLaunch\(\{ url: '\/pages\/auth\/login\/index' \}\)/.test(settings)) {
  failures.push(`${settingsFile}: logout confirm path must clear Pinia session and persisted storage before reLaunch to login`)
}

if (!store.includes('clearSession()')) {
  failures.push(`${storeFile}: user store must expose clearSession for logout`) 
}

if (!shim.includes('removeStorageSync(key: string): void')) {
  failures.push(`${shimFile}: uni typings must include removeStorageSync used by secure logout`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('settings logout clears session state and persisted auth before leaving the page')
