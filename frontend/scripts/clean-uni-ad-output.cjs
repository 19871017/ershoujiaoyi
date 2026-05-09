#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const files = [
  path.resolve(__dirname, '..', 'node_modules', '@dcloudio', 'uni-cloud', 'lib', 'uni.plugin.js')
]

function cleanUniCloudPlugin(file) {
  if (!fs.existsSync(file)) return 'missing'
  let source = fs.readFileSync(file, 'utf8')
  let next = source

  next = next.replace(/\n\s*console\.log\(\);\n\s*console\.log\('欢迎将web站点部署到uniCloud前端网页托管平台，高速、免费、安全、省心，详见：https:\/\/uniapp\.dcloud\.io\/uniCloud\/hosting'\);/g, '')
  next = next.replace(/\n\s*console\.log\('欢迎将web站点部署到uniCloud前端网页托管平台，高速、免费、安全、省心，详见：https:\/\/uniapp\.dcloud\.io\/uniCloud\/hosting'\);/g, '')
  next = next.replace(/const initUniCloudWarningOnce = \(0, uni_shared_1\.once\)\(\(\) => \{[\s\S]*?\n\}\);\nfunction checkProjectUniCloudDir\(\) \{/m,
    "const initUniCloudWarningOnce = (0, uni_shared_1.once)(() => {});\nfunction checkProjectUniCloudDir() {")

  if (next !== source) {
    fs.writeFileSync(file, next)
    return 'cleaned'
  }
  return 'already clean'
}

for (const file of files) {
  const status = cleanUniCloudPlugin(file)
  console.log(`[clean-uni-ad-output] ${status} ${path.relative(process.cwd(), file)}`)
}
