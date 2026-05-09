#!/usr/bin/env node
const fs = require('fs')
const path = require('path')

const patches = [
  {
    file: path.resolve(__dirname, '..', 'node_modules', '@dcloudio', 'uni-cloud', 'lib', 'uni.plugin.js'),
    transforms: [
      (s) => s.replace(/\n\s*console\.log\(\);\n\s*console\.log\('欢迎将web站点部署到uniCloud前端网页托管平台，高速、免费、安全、省心，详见：https:\/\/uniapp\.dcloud\.io\/uniCloud\/hosting'\);/g, ''),
      (s) => s.replace(/\n\s*console\.log\('欢迎将web站点部署到uniCloud前端网页托管平台，高速、免费、安全、省心，详见：https:\/\/uniapp\.dcloud\.io\/uniCloud\/hosting'\);/g, ''),
      (s) => s.replace(
        /const initUniCloudWarningOnce = \(0, uni_shared_1\.once\)\(\(\) => \{[\s\S]*?\n\}\);\nfunction checkProjectUniCloudDir\(\) \{/m,
        'const initUniCloudWarningOnce = (0, uni_shared_1.once)(() => {});\nfunction checkProjectUniCloudDir() {'
      )
    ]
  },
  {
    file: path.resolve(__dirname, '..', 'node_modules', '@dcloudio', 'uni-cli-shared', 'dist', 'checkUpdate.js'),
    transforms: [
      (s) => s.replace(
        /async function checkUpdate1\(options\) \{[\s\S]*?\nfunction normalizeUpdateCache\(updateCache, manifestJson\) \{/m,
        'async function checkUpdate1(options) { return void debugCheckUpdate("disabled"); }\nfunction normalizeUpdateCache(updateCache, manifestJson) {'
      )
    ]
  }
]

function applyPatch({ file, transforms }) {
  if (!fs.existsSync(file)) return 'missing'
  const before = fs.readFileSync(file, 'utf8')
  let after = before
  for (const transform of transforms) after = transform(after)
  if (after !== before) {
    fs.writeFileSync(file, after)
    return 'cleaned'
  }
  return 'already clean'
}

for (const patch of patches) {
  const status = applyPatch(patch)
  console.log(`[clean-uni-console-output] ${status} ${path.relative(process.cwd(), patch.file)}`)
}
