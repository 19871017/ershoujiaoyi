const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/product/edit/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  "const productId = ref('1001')",
  "catch { form.title='奶油白法式连衣裙 只穿过一次'",
  "form.description='适合春夏，细节图完整，支持同城约看。'",
  "form.price='129'",
  "city:'深圳'",
  "form = reactive({ title:'', description:'', price:'', category:'衣物', condition:'几乎全新', city:'深圳'",
  'Number(productId.value)); form.title',
  'updateProduct(Number(productId.value)',
  '@change="form.safeTrade = !form.safeTrade"',
  '@change="form.chatProof = !form.chatProof"',
  "uni.showToast({title:`已添加 ${images.value.length} 张图片`,icon:'none'})",
  "images.value.push(ticket.storageUrl)",
  'function removeImage(url:string){ images.value = images.value.filter(item => item !== url) }',
  '仅走平台担保交易',
  "safeTrade:true, chatProof:true"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden product-edit demo/default or unsafe numeric-id marker found: ${marker}`)
}

const requiredMarkers = [
  "const productId = ref('')",
  'isValidBackendProductId',
  "loadError.value = '缺少有效商品编号，未加载本地样例商品'",
  "loadError.value = '商品详情加载失败，未展示本地样例商品'",
  'form.title = detail.title',
  'form.description = detail.description ||',
  'form.price = String(detail.price)',
  'images.value = detail.imageUrls || []',
  'updateProduct(backendProductId.value',
  '保存失败时不会展示本地成功状态',
  'product edit media/trade controls are read-only until backend update contract supports them',
  '商品图片上传票据已生成，需提交修改审核后才会更新商品图片',
  '商品图片移除需提交修改审核后生效',
  "function toggleTradePreference(_field: 'serverTradeOnly' | 'serverChatRecord')",
  '交易方式以服务端订单与支付状态为准',
  '聊天记录以服务端会话为准'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed/backend-derived product-edit marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('product edit page is backend-derived and fail-closed')
