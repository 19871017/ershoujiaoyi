const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const httpFile = fs.readFileSync(path.join(root, 'src/api/http.ts'), 'utf8')

const required = [
  'VITE_API_BASE_URL',
  'VITE_ENABLE_LAN_API_FALLBACK',
  'resolveApiBaseUrl',
  'window.location.hostname',
  'return `http://${host}:18080`',
  'const RESOLVED_API_BASE_URL = resolveApiBaseUrl()',
  'url: `${RESOLVED_API_BASE_URL}${requestUrl}`'
]

const missing = required.filter((token) => !httpFile.includes(token))

const forbidden = [
  'url: `${API_BASE_URL}${requestUrl}`'
].filter((token) => httpFile.includes(token))

if (missing.length || forbidden.length) {
  console.error('LAN API base check failed')
  if (missing.length) console.error('Missing markers:', missing.join(', '))
  if (forbidden.length) console.error('Forbidden stale markers:', forbidden.join(', '))
  process.exit(1)
}

console.log('LAN API base fallback is enabled for phone H5 preview')
