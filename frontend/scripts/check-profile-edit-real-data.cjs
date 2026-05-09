const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/user/profile/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')

const failures = []

const forbiddenMarkers = [
  "reactive({ userId: 1, nickname: '雨哥的小原圈'",
  "city: '深圳市'",
  "bio: '喜欢收集温柔风衣物，闲置都走平台担保。'",
  "<text class=\"tag\">女装闲置</text>",
  "<text class=\"tag soft\">同城约看</text>",
  "{ label: '手机号', desc: '用于登录和交易提醒', done: true }",
  "catch { /* keep local demo */ }",
  "资料已校验，接入保存接口后再同步到个人主页",
  "form.mainRole = item.value"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden local profile/trust marker found: ${marker}`)
}

const requiredMarkers = [
  '资料接口加载失败，未展示本地个人资料样例',
  '资料保存接口尚未接入，未执行任何资料修改',
  '认证状态以服务端资料为准',
  'const form = reactive({ userId: 0, nickname: \'\', mainRole: \'UNVERIFIED\', city: \'\', bio: \'\' })',
  'const verifies = computed<VerifyItem[]>(() => []',
  'function chooseRole(role: string)',
  '未执行任何角色修改'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing fail-closed profile marker: ${marker}`)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('profile edit page avoids local trust/profile samples and fails closed')
