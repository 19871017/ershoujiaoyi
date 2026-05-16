const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/user/profile/index.vue'
const source = fs.readFileSync(path.join(root, file), 'utf8')
const apiSource = fs.readFileSync(path.join(root, 'src/api/modules/user.ts'), 'utf8')

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
  "资料保存接口尚未接入，未执行任何资料修改",
  "角色修改需通过资料保存接口持久化，当前未执行任何角色修改",
  "需后端保存接口接入"
]

for (const marker of forbiddenMarkers) {
  if (source.includes(marker)) failures.push(`${file}: forbidden local profile/trust marker found: ${marker}`)
}

const requiredMarkers = [
  'getMyProfile',
  'updateMyProfile({',
  "mainRole: form.videoVerified ? form.mainRole : 'BUYER'",
  "scene: 'COMMUNITY_IMAGE'",
  'uploadMediaTicketFile(ticket, path)',
  "function showToast(title: string, icon: 'success' | 'none' = 'none')",
  "showToast('暂不可改号')",
  "showToast('资料暂不可用')",
  "showToast('已保存', 'success')",
  "showToast('保存失败')",
  "showToast('头像已更新', 'success')"
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing real profile-save marker: ${marker}`)
}

const apiRequiredMarkers = [
  'export function updateMyProfile',
  "post<UserProfileResponse>('/api/user/me/profile'",
  'city?: string',
  'bio?: string'
]
for (const marker of apiRequiredMarkers) {
  if (!apiSource.includes(marker)) failures.push('src/api/modules/user.ts: missing profile-save API marker: ' + marker)
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('profile edit page saves via real backend profile API and avoids local trust/profile samples')
