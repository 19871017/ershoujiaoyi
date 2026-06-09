const fs = require('fs')
const path = require('path')
const vm = require('vm')
const ts = require('typescript')

const root = path.resolve(__dirname, '..')
const helperFile = path.join(root, 'src/pages/auth/login/login-redirect.ts')
const loginFile = path.join(root, 'src/pages/auth/login/index.vue')

const helperSource = fs.readFileSync(helperFile, 'utf8')
const loginSource = fs.readFileSync(loginFile, 'utf8')

let failed = false

function fail(message) {
  console.error(message)
  failed = true
}

function loadHelper() {
  const compiled = ts.transpileModule(helperSource, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  }).outputText
  const sandbox = {
    exports: {},
    module: { exports: {} }
  }
  sandbox.module.exports = sandbox.exports
  vm.runInNewContext(compiled, sandbox, { filename: helperFile })
  return sandbox.module.exports
}

const helpers = loadHelper()
const normalizeLoginRedirect = helpers.normalizeLoginRedirect
const loginRedirectIsTabbar = helpers.loginRedirectIsTabbar

if (typeof normalizeLoginRedirect !== 'function') fail('login redirect helper missing normalizeLoginRedirect')
if (typeof loginRedirectIsTabbar !== 'function') fail('login redirect helper missing loginRedirectIsTabbar')

if (!loginSource.includes("import { loginRedirectIsTabbar, normalizeLoginRedirect } from './login-redirect'")) {
  fail('login page must use login-redirect helper')
}
if (loginSource.includes('decodeURIComponent(url || \'\')')) {
  fail('login page must not single-decode redirect inline')
}

if (!failed) {
  const cases = [
    ['/pages/chat/conversation/index?receiverId=2', '/pages/chat/conversation/index?receiverId=2'],
    ['%2Fpages%2Fchat%2Fconversation%2Findex%3FreceiverId%3D2', '/pages/chat/conversation/index?receiverId=2'],
    ['%252Fpages%252Fchat%252Fconversation%252Findex%253FreceiverId%253D2', '/pages/chat/conversation/index?receiverId=2'],
    ['/pages/tabbar/message/index', '/pages/tabbar/message/index'],
    ['/pages/auth/login/index?redirect=%2Fpages%2Ftabbar%2Fhome%2Findex', ''],
    ['https://old.tiklxd09.club/#/pages/chat/conversation/index', ''],
    ['//evil.example/path', ''],
    ['/pages/../admin/login', ''],
    ['/pages/chat/conversation/index%2F..%2Fadmin', ''],
    ['javascript:alert(1)', ''],
    ['data:text/plain,hello', '']
  ]
  for (const [input, expected] of cases) {
    const actual = normalizeLoginRedirect(input)
    if (actual !== expected) fail(`normalizeLoginRedirect(${input}) expected ${expected}, got ${actual}`)
  }
  if (!loginRedirectIsTabbar('/pages/tabbar/home/index')) fail('tabbar redirect should be detected')
  if (loginRedirectIsTabbar('/pages/chat/session-list/index')) fail('non-tabbar redirect should not be treated as tabbar')
}

if (failed) {
  console.error('auth login redirect check failed')
  process.exit(1)
}

console.log('auth login redirect handles double-encoded deep links and rejects unsafe targets')
