const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const removedPaths = [
  'src/pages/location/city/index.vue',
  'src/api/modules/location.ts'
]
const routeFiles = ['src/pages.json', 'pages.json']
const forbiddenRoute = 'pages/location/city/index'

const failures = []
for (const file of removedPaths) {
  if (fs.existsSync(path.join(root, file))) {
    failures.push(`${file}: manual/fixed location entry must be removed; keep only server-derived IP location`)
  }
}
for (const file of routeFiles) {
  const full = path.join(root, file)
  if (!fs.existsSync(full)) continue
  const source = fs.readFileSync(full, 'utf8')
  if (source.includes(forbiddenRoute)) {
    failures.push(`${file}: must not route to removed manual city picker`)
  }
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('manual city picker and frontend location API are removed; user-facing location stays IP-derived')
