const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const files = [
  'src/pages.json',
  'pages.json',
  'src/pages/tabbar/me/index.vue',
  'src/pages/risk/index.vue'
]
const forbiddenDirs = [
  'src/pages/admin'
]

const forbidden = [
  /pages\/admin\//,
  /后台管理入口/,
  /后台入口/,
  /进入后台首页/,
  /getAdminAuditList/,
  /X-Admin-Mode/
]

let failed = false
for (const dir of forbiddenDirs) {
  const full = path.join(root, dir)
  if (fs.existsSync(full)) {
    console.error(`[user-no-admin-entry] forbidden admin page directory exists: ${dir}`)
    failed = true
  }
}
for (const file of files) {
  const full = path.join(root, file)
  if (!fs.existsSync(full)) continue
  const content = fs.readFileSync(full, 'utf8')
  for (const pattern of forbidden) {
    if (pattern.test(content)) {
      console.error(`[user-no-admin-entry] forbidden marker ${pattern} in ${file}`)
      failed = true
    }
  }
}

if (failed) process.exit(1)
console.log('[user-no-admin-entry] ok: user frontend has no visible/admin-route entry markers')
