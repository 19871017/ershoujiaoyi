const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const publishPath = path.join(root, 'src/pages/tabbar/publish/index.vue')
const source = fs.readFileSync(publishPath, 'utf8')
const formBlock = source.match(/const form = reactive\(\{[\s\S]*?\n\}\)/)?.[0] || ''

const failures = []

function assertNo(pattern, message, haystack = source) {
  if (pattern.test(haystack)) failures.push(message)
}

if (!formBlock) failures.push('publish page form state was not found')
assertNo(/\btitle:\s*['"][^'"]+['"]/, 'publish form must not prefill a product title; user-created listings must start empty', formBlock)
assertNo(/\bdescription:\s*['"][^'"]+['"]/, 'publish form must not prefill a product description/demo copy', formBlock)
assertNo(/\bprice:\s*['"][^'"]+['"]/, 'publish form must not prefill a product price', formBlock)
assertNo(/\blocation:\s*['"][^'"]+['"]/, 'publish form must not prefill a static location/trade signal', formBlock)
assertNo(/tradeRule:\s*['"]平台担保['"]/, 'publish form must not default to platform escrow/trust wording before backend-derived order/payment state', formBlock)
assertNo(/const\s+tradeOptions\s*=\s*\[[^\]]*(平台担保|同城约看)[^\]]*\]/, 'publish trade options must not present static escrow/location trust signals as seller-selectable product facts')
assertNo(/placeholder=["'][^"']*(同城可约|平台担保)[^"']*["']/, 'publish placeholders must not suggest static location/escrow trust signals')
assertNo(/奶油白法式连衣裙|小原圈断舍离|只穿过一次/, 'publish page contains demo listing copy that could be submitted as real inventory')

if (!/imageUrls:\s*\[\]\s+as\s+string\[\]/.test(source)) {
  failures.push('publish imageUrls should start as an empty array and only contain storage URLs from PRODUCT_IMAGE tickets')
}

if (!/createProduct\(/.test(source) || !/createMediaUploadTicket\(/.test(source)) {
  failures.push('publish page must continue to use real product API and media upload-ticket API')
}

if (failures.length) {
  console.error('check-publish-form-no-static-defaults failed:')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exit(1)
}

console.log('check-publish-form-no-static-defaults passed')
