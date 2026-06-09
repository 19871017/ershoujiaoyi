const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const navFile = 'src/components/GlobalBottomNav.vue'
const mainFile = 'src/main.ts'
const navSource = fs.readFileSync(path.join(root, navFile), 'utf8')
const mainSource = fs.readFileSync(path.join(root, mainFile), 'utf8')
const appSource = fs.readFileSync(path.join(root, 'src/App.vue'), 'utf8')
const communitySource = fs.readFileSync(path.join(root, 'src/pages/tabbar/message/index.vue'), 'utf8')
const failures = []

const requiredMarkers = [
  "import { getChatConversations, type ChatConversationItem, type ChatConversationListResponse } from '../api/modules/chat'",
  "import { useUserStore } from '../store/modules/user'",
  "const communityPath = '/pages/tabbar/message/index'",
  'const totalUnread = ref(0)',
  'const displayUnread = computed(() => totalUnread.value > 99 ? \'99+\' : String(totalUnread.value))',
  'item.path === communityPath && totalUnread > 0',
  'class="bottom-nav-unread"',
  'class="community-switcher ds-card"',
  'function openPrivateChat(): void',
  "url: '/pages/chat/session-list/index'",
  'async function refreshUnreadCount(preserveOnError = true): Promise<void>',
  'const response = await getChatConversations()',
  'assertConversationListResponse(response)',
  'response.conversations.reduce((sum, item) => sum + validUnreadCount(item), 0)',
  'console.warn(\'global bottom nav chat unread load failed\'',
  'watch(() => userStore.token',
  "const communitySwitcherEventName = 'xiaoyuanquan:community-switcher'",
  'function publishCommunitySwitcherState(open = communityMenuOpen.value): void',
  "document.documentElement.classList.toggle('community-switcher-open', open)",
  'new CustomEvent(communitySwitcherEventName, { detail: { open } })'
]

for (const marker of requiredMarkers) {
  if (!navSource.includes(marker)) failures.push(`${navFile}: missing private-chat nav marker: ${marker}`)
}

if (!/function openTab\(path: TabPath\): void \{\s*if \(path === communityPath\)[\s\S]*communityMenuOpen\.value = !communityMenuOpen\.value[\s\S]*return[\s\S]*switchToTab\(path\)/.test(navSource)) {
  failures.push(`${navFile}: community tab should open the community/private switcher instead of immediately hiding private chat behind the community page`)
}

if (!/function validUnreadCount\(item: unknown\): number[\s\S]*!isValidBackendId\(row\.conversationId\)[\s\S]*!isValidBackendId\(row\.peerUserId\)[\s\S]*!Number\.isSafeInteger\(row\.unreadCount\)[\s\S]*return row\.unreadCount/s.test(navSource)) {
  failures.push(`${navFile}: unread badge must isolate malformed conversation rows and only count valid backend conversations`)
}

if (!/function startUnreadRefresh\(\): void[\s\S]*if \(unreadTimer \|\| !userStore\.token\) return[\s\S]*setInterval\(\(\) => \{ void refreshUnreadCount\(true\) \}, unreadRefreshMs\)/s.test(navSource)) {
  failures.push(`${navFile}: bottom nav unread polling must be login-gated and preserve previous unread count on transient failures`)
}

if (!mainSource.includes("import GlobalBottomNav from './components/GlobalBottomNav.vue'") || !mainSource.includes('installGlobalComponent(globalBottomNavTarget)')) {
  failures.push(`${mainFile}: global bottom nav must remain mounted once at app bootstrap`)
}

if (!appSource.includes('.community-switcher-open .compose-fab') || !appSource.includes('display: none !important')) {
  failures.push('src/App.vue: community switcher must globally hide the compose FAB while the private/community switcher is open')
}

if (!communitySource.includes('v-if="!communitySwitcherOpen" class="compose-fab tapable"') || !communitySource.includes('window.addEventListener(communitySwitcherEventName, syncCommunitySwitcherState)')) {
  failures.push('src/pages/tabbar/message/index.vue: community page must hide compose FAB while the bottom-nav community switcher is open')
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('global bottom nav exposes private chat and unread badge from real chat conversations')
