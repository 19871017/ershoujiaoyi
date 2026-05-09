const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/community/compose/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const failures = []

const forbiddenMarkers = [
  "success: true",
  "uni.showModal({ title: '动态已发布'",
  "内容已进入小原圈社区广场。",
  "发布中...",
  "发布动态'",
  "form.images.push(ticket.storageUrl)",
  "form.images = form.images.slice(0, 9)",
  "form.images.splice(index, 1)",
  "约看提醒",
  "不要发布手机号、微信、支付宝等联系方式"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden fake-success/static-sensitive compose marker found: ${marker}`)
}

const requiredMarkers = [
  "submitMessage = ref('')",
  "{{ submitting ? '提交中...' : '提交发布' }}",
  "const issuedUrls: string[] = []",
  "form.images = [...form.images, ...issuedUrls].slice(0, 9)",
  "function removeImage(index: number) { form.images = form.images.filter((_, current) => current !== index) }",
  "submitMessage.value = `已提交发布：${created.postNo || created.postId}`",
  "title: '已提交发布'",
  "if (res.confirm && created.postId > 0)",
  "发布没有提交成功，未进入社区广场"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing real backend/fail-closed compose marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('community compose page only shows publish success after backend createPost and avoids static sensitive/local mutation copy')
