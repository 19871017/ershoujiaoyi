const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const file = 'src/pages/user/profile/index.vue'
const supportFiles = [
  'src/pages/user/profile/profile-helpers.ts'
]
const source = [
  ...supportFiles.map((supportFile) => fs.readFileSync(path.join(root, supportFile), 'utf8')),
  fs.readFileSync(path.join(root, file), 'utf8')
].join('\n')
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
  'updateMyProfile(profilePayload)',
  "const hasApprovedVideoIdentity = computed(() => form.videoVerified && form.videoIdentityStatus === 'APPROVED')",
  "mainRole: hasApprovedVideoIdentity.value ? form.mainRole : 'BUYER'",
  "scene: 'COMMUNITY_IMAGE'",
  'uploadMediaTicketFile(ticket, path)',
  "const communityImageStoragePrefix = '/uploads/community-image/'",
  'function uploadedCommunityImageUrl(storageUrl: string, purpose:',
  "console.warn('profile edit invalid community image url'",
  'function storedAvatarUrl(url: string): string',
  "avatarUrl: storedAvatarUrl(profile.avatarUrl || '')",
  "console.warn('profile edit rejected stored avatar url'",
  'function filterStoredShowcaseUrls(urls: string[]): string[]',
  "console.warn('profile edit rejected stored showcase url'",
  "console.warn('profile edit invalid showcase image urls'",
  "const uploadedShowcaseUrls = computed(() => form.showcaseImageUrls.filter((url) => isValidCommunityImageUrl(url)))",
  "showcaseImageUrls: approvedVideoIdentity ? filterStoredShowcaseUrls(profile.showcaseImageUrls || []) : []",
  "const safeAvatarUrl = avatarChanged.value ? uploadedCommunityImageUrl(form.avatarUrl, 'avatar') : undefined",
  'const safeShowcaseUrls = hasApprovedVideoIdentity.value && showcaseChanged.value ? uploadedCommunityImageUrls(form.showcaseImageUrls) : undefined',
  'if (safeShowcaseUrls) profilePayload.showcaseImageUrls = safeShowcaseUrls',
  "function showToast(title: string, icon: 'success' | 'none' = 'none')",
  'updateMyUserNo({ userNo: userNoDraft.value })',
  "showToast('小原圈号已更新', 'success')",
  "showToast(error instanceof Error ? error.message : '改号失败，请稍后重试')",
  "showToast('资料暂不可用')",
  "showToast('已保存', 'success')",
  "showToast(error instanceof Error ? error.message : '保存失败')",
  "showToast('头像已上传，保存后生效', 'success')",
  "console.warn('profile avatar upload failed'",
  "console.warn('profile showcase upload failed'",
  "console.warn('profile avatar picker failed'",
  "console.warn('profile showcase picker failed'",
  "console.warn('profile image picker rejected file'",
  "console.warn('profile save failed'",
  "console.warn('profile load failed'",
  "console.warn('profile input event invalid'",
  "const knownVideoIdentityStatuses = new Set(['UNVERIFIED', 'PENDING', 'APPROVED', 'REJECTED'])",
  "throw new Error('profile video verified mismatch')",
  "function assertBackendProfile(value: unknown): asserts value is UserProfileResponse",
  "assertBackendProfile(profile)",
  "function assertSameProfile(profile: UserProfileResponse, action: string): void",
  "function assertUserNoResponse(profile: UserProfileResponse, requestedUserNo: string): void",
  "function assertProfileSaveResponse(profile: UserProfileResponse, payload: UpdateUserProfileRequest, expectedAvatarUrl: string | undefined, expectedShowcaseUrls: string[] | undefined): void",
  "assertSameProfile(profile, 'userNo')",
  'assertUserNoResponse(profile, userNoDraft.value)',
  "assertSameProfile(profile, 'saveProfile')",
  'assertProfileSaveResponse(profile, profilePayload, safeAvatarUrl, safeShowcaseUrls)'
]

for (const marker of requiredMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: missing real profile-save marker: ${marker}`)
}

const unsafeCommunityImageUrlMarkers = [
  "typeof url !== 'string'",
  "!url.startsWith('local://')",
  "!url.startsWith('blob:')",
  "!url.startsWith('data:')",
  'url.startsWith(communityImageStoragePrefix)',
  "lower.includes('%2e')",
  "lower.includes('%2f')",
  "lower.includes('%5c')",
  "url.includes('\\\\')",
  "url.includes('..')",
  "url.includes('//')",
  "relativePath.split('/').some"
]

for (const marker of unsafeCommunityImageUrlMarkers) {
  if (!source.includes(marker)) failures.push(`${file}: profile edit must reject unsafe COMMUNITY_IMAGE media URL marker: ${marker}`)
}

if (!/function assertBackendProfile\(value: unknown\): asserts value is UserProfileResponse[\s\S]*Number\.isSafeInteger\(profile\.userId\)[\s\S]*knownVideoIdentityStatuses\.has\(profile\.videoIdentityStatus\)[\s\S]*typeof profile\.videoVerified !== 'boolean'[\s\S]*profile\.videoVerified === true && profile\.videoIdentityStatus !== 'APPROVED'[\s\S]*profile\.showcaseImageUrls\.some\(\(url\) => typeof url !== 'string'\)/s.test(source)) {
  failures.push(`${file}: backend profile responses must validate known video identity state before applying local trust/profile state`)
}

if (!/function inputValue\(field: TextFieldKey \| 'userNo', event: unknown\): string \| undefined[\s\S]*\| null \| undefined\)\?\.detail\?\.value[\s\S]*console\.warn\('profile input event invalid'[\s\S]*function updateTextField\(field: TextFieldKey, event: unknown\)[\s\S]*if \(value === undefined\) return[\s\S]*form\[field\] = value/s.test(source)) {
  failures.push(`${file}: profile text inputs must not silently clear fields or throw on malformed input events`)
}

if (!/function assertUserNoResponse\(profile: UserProfileResponse, requestedUserNo: string\): void[\s\S]*profile\.userNo !== requestedUserNo/s.test(source)) {
  failures.push(`${file}: userNo save must validate backend response against the requested userNo before local success`)
}

if (!/function assertProfileSaveResponse\(profile: UserProfileResponse, payload: UpdateUserProfileRequest, expectedAvatarUrl: string \| undefined, expectedShowcaseUrls: string\[\] \| undefined\): void[\s\S]*profile\.nickname !== payload\.nickname[\s\S]*profile\.gender !== payload\.gender[\s\S]*storedAvatarUrl\(profile\.avatarUrl \|\| ''\) !== expectedAvatarUrl[\s\S]*JSON\.stringify\(filterStoredShowcaseUrls\(profile\.showcaseImageUrls \|\| \[\]\)\) !== JSON\.stringify\(expectedShowcaseUrls\)/s.test(source)) {
  failures.push(`${file}: profile save must validate backend response fields and media URLs against the submitted payload before local success`)
}

if (!/function chooseAvatar\(\)[\s\S]*uploadingAvatar\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage[\s\S]*fail\(error: unknown\)[\s\S]*uploadingAvatar\.value = false[\s\S]*catch \(error\)[\s\S]*console\.warn\('profile avatar picker failed'/s.test(source)) {
  failures.push(`${file}: avatar picker must clear busy state and log async/synchronous chooser failures`)
}

if (!/function chooseShowcasePhotos\(\)[\s\S]*!hasApprovedVideoIdentity\.value[\s\S]*uploadingShowcase\.value = true[\s\S]*try\s*\{[\s\S]*uni\.chooseImage[\s\S]*fail\(error: unknown\)[\s\S]*uploadingShowcase\.value = false[\s\S]*catch \(error\)[\s\S]*console\.warn\('profile showcase picker failed'/s.test(source)) {
  failures.push(`${file}: showcase picker must require approved video identity and handle async/synchronous chooser failures`)
}

const apiRequiredMarkers = [
  'export function updateMyProfile',
  "post<UserProfileResponse>('/api/user/me/profile'",
  'export function updateMyUserNo',
  "post<UserProfileResponse>('/api/user/me/user-no'",
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
