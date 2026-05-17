const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const projectRoot = path.resolve(root, '..')
const loginPage = fs.readFileSync(path.join(root, 'src/pages/auth/login/index.vue'), 'utf8')
const authApi = fs.readFileSync(path.join(root, 'src/api/modules/auth.ts'), 'utf8')
const userApi = fs.readFileSync(path.join(root, 'src/api/modules/user.ts'), 'utf8')
const backendAuth = fs.readFileSync(path.join(projectRoot, 'backend/src/main/java/com/secondhand/platform/modules/auth/application/AuthApplicationService.java'), 'utf8')
const backendRequest = fs.readFileSync(path.join(projectRoot, 'backend/src/main/java/com/secondhand/platform/modules/auth/LoginRequest.java'), 'utf8')

const failures = []

function requireMarkers(source, markers, label) {
  for (const marker of markers) {
    if (!source.includes(marker)) failures.push(`${label} missing registration gender marker: ${marker}`)
  }
}

const requiredLoginMarkers = [
  "type RegisterGender",
  "const registerGender = ref<RegisterGender>('goddess')",
  "registerGender === 'goddess'",
  "registerGender === 'god'",
  '♀ 女',
  '♂ 男',
  '请选择性别',
  'register({ ...payload, gender: registerGender.value })'
]
requireMarkers(loginPage, requiredLoginMarkers, 'login page')

const requiredFrontendApiMarkers = [
  "export type RegisterGender = 'god' | 'goddess'",
  'export interface RegisterRequest extends LoginRequest',
  'gender: RegisterGender',
  "post<AuthTokenResponse>('/api/auth/register', data)",
  "export type UserGender = 'god' | 'goddess' | string",
  'gender?: UserGender'
]
for (const marker of requiredFrontendApiMarkers) {
  if (!authApi.includes(marker) && !userApi.includes(marker)) failures.push(`frontend API missing registration gender marker: ${marker}`)
}

const requiredBackendMarkers = [
  'private static final List<String> ALLOWED_GENDERS = List.of("god", "goddess")',
  'String normalizedGender = normalizeRegistrationGender(request.getGender())',
  'createUser(normalizedMobile, passwordHash(request.getPassword()), normalizedGender)',
  'INSERT INTO user_profile (user_id, gender, city, bio, identity_status, main_role, video_identity_status, video_verified, created_at, updated_at)',
  "VALUES (?, ?, NULL, NULL, 'UNVERIFIED', 'BUYER', 'UNVERIFIED', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
  'private String normalizeRegistrationGender(String gender)',
  'throw new IllegalArgumentException("gender required")',
  'throw new IllegalArgumentException("gender invalid")'
]
requireMarkers(backendAuth, requiredBackendMarkers, 'backend auth')

const requiredRequestMarkers = [
  'private String gender',
  'public String getGender()',
  'public void setGender(String gender)'
]
for (const marker of requiredRequestMarkers) {
  if (!backendRequest.includes(marker)) failures.push(`backend LoginRequest missing gender marker: ${marker}`)
}

if (failures.length) {
  console.error('registration gender real-flow check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('registration gender is selected in UI, sent to backend, validated, and saved as a buyer profile')
