const fs = require('fs')
const path = require('path')
const http = require('http')
const { execFileSync } = require('child_process')

const root = path.resolve(__dirname, '..')
const repoRoot = path.resolve(root, '..')
const h5DistDir = path.join(root, 'dist/build/h5')
const outputDir = path.join(repoRoot, 'output/playwright/chat-mobile-media-compat')
const videoPath = path.join(repoRoot, 'output/test-media/chat-test-video.mp4')
const transparentPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
  'base64'
)

let ownedPreviewServer = null

const contexts = [
  {
    name: 'mobile-browser',
    userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'
  },
  {
    name: 'wechat-webview',
    userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.49'
  }
]

async function main() {
  const playwright = loadPlaywright()
  fs.mkdirSync(outputDir, { recursive: true })
  ensureTestVideo()
  const baseUrl = await startCurrentDistPreview()
  ownedPreviewServer = baseUrl.server
  const browser = await playwright.chromium.launch({ headless: true })
  const failures = []
  const reports = []
  try {
    for (const contextSpec of contexts) {
      const context = await browser.newContext({
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        deviceScaleFactor: 2,
        userAgent: contextSpec.userAgent
      })
      await context.addInitScript(() => {
        window.localStorage.setItem('xiaoyuan_user_access_token', 'chat-mobile-media-compat-token')
        window.__xqyDenyMicrophone = true
        const installDeniedMic = () => {
          const mediaDevices = navigator.mediaDevices || {}
          mediaDevices.getUserMedia = () => Promise.reject(new DOMException('Permission denied for compatibility check', 'NotAllowedError'))
          Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: mediaDevices })
        }
        installDeniedMic()
      })
      const page = await context.newPage()
      await installChatMediaFixture(page)
      try {
        const url = `${baseUrl.baseUrl}/#/pages/chat/conversation/index?conversationId=10&receiverId=3`
        await page.goto(url, { waitUntil: 'networkidle', timeout: 20000 })
        await page.waitForSelector('.chat-page', { timeout: 8000 })
        await page.screenshot({ path: path.join(outputDir, `${contextSpec.name}-initial.png`), fullPage: true })

        const capability = await inspectBrowserCapability(page)
        if (!capability.secureContext) failures.push(`${contextSpec.name}: page is not treated as a secure context`)
        if (!capability.hasGetUserMedia) failures.push(`${contextSpec.name}: getUserMedia API is unavailable`)
        if (!capability.hasMediaRecorder) failures.push(`${contextSpec.name}: MediaRecorder API is unavailable`)

        const keyboardResult = await inspectKeyboardPosition(page)
        if (!keyboardResult.ok) failures.push(`${contextSpec.name}: ${keyboardResult.message}`)
        await page.screenshot({ path: path.join(outputDir, `${contextSpec.name}-keyboard.png`), fullPage: true })

        const voiceResult = await inspectDeniedVoicePermission(page)
        if (!voiceResult.ok) failures.push(`${contextSpec.name}: ${voiceResult.message}`)
        await page.screenshot({ path: path.join(outputDir, `${contextSpec.name}-voice-denied.png`), fullPage: true })

        const imageResult = await inspectImagePreviewFallbackClose(page)
        if (!imageResult.ok) failures.push(`${contextSpec.name}: ${imageResult.message}`)
        await page.screenshot({ path: path.join(outputDir, `${contextSpec.name}-image-preview-closed.png`), fullPage: true })

        const videoResult = await inspectVideoPlaybackAndFullscreen(page)
        if (!videoResult.ok) failures.push(`${contextSpec.name}: ${videoResult.message}`)
        await page.screenshot({ path: path.join(outputDir, `${contextSpec.name}-video.png`), fullPage: true })

        reports.push({
          context: contextSpec.name,
          capability,
          keyboard: keyboardResult,
          voicePermission: voiceResult,
          imagePreview: imageResult,
          video: videoResult
        })
      } catch (error) {
        failures.push(`${contextSpec.name}: ${error instanceof Error ? error.message : String(error)}`)
      } finally {
        await page.close()
        await context.close()
      }
    }
  } finally {
    await browser.close()
    if (ownedPreviewServer) {
      await new Promise((resolve) => ownedPreviewServer.close(resolve))
      ownedPreviewServer = null
    }
  }

  fs.writeFileSync(path.join(outputDir, 'report.json'), JSON.stringify({ reports, failures }, null, 2))
  if (failures.length) {
    console.error(failures.join('\n'))
    process.exit(1)
  }
  console.log(`chat mobile media compatibility check passed; report: ${path.join(outputDir, 'report.json')}`)
}

function loadPlaywright() {
  try {
    return require('playwright')
  } catch (error) {
    console.error(`Playwright is required for chat mobile media compatibility checks: ${error instanceof Error ? error.message : String(error)}`)
    process.exit(1)
  }
}

function ensureTestVideo() {
  if (fs.existsSync(videoPath) && fs.statSync(videoPath).size > 0) return
  fs.mkdirSync(path.dirname(videoPath), { recursive: true })
  execFileSync('ffmpeg', [
    '-y',
    '-f', 'lavfi',
    '-i', 'color=c=orange:s=320x180:d=1',
    '-an',
    '-movflags', '+faststart',
    videoPath
  ], { stdio: 'ignore' })
}

function startCurrentDistPreview() {
  if (!fs.existsSync(path.join(h5DistDir, 'index.html'))) {
    throw new Error('H5 dist is missing. Run npm run build:h5:prod first.')
  }
  const server = http.createServer((request, response) => {
    const rawUrl = request.url || '/'
    const urlPath = decodeURIComponent(rawUrl.split('?')[0] || '/')
    const relativePath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '')
    const requestedPath = path.resolve(h5DistDir, relativePath)
    const safePath = requestedPath.startsWith(h5DistDir + path.sep) || requestedPath === h5DistDir
      ? requestedPath
      : path.join(h5DistDir, 'index.html')
    const filePath = fs.existsSync(safePath) && fs.statSync(safePath).isFile()
      ? safePath
      : path.join(h5DistDir, 'index.html')
    response.writeHead(200, { 'Content-Type': contentType(filePath) })
    fs.createReadStream(filePath).pipe(response)
  })
  return new Promise((resolve, reject) => {
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close()
        reject(new Error('failed to start H5 dist preview'))
        return
      }
      resolve({ server, baseUrl: `http://127.0.0.1:${address.port}` })
    })
  })
}

function contentType(filePath) {
  const extension = path.extname(filePath).toLowerCase()
  if (extension === '.html') return 'text/html; charset=utf-8'
  if (extension === '.js') return 'text/javascript; charset=utf-8'
  if (extension === '.css') return 'text/css; charset=utf-8'
  if (extension === '.png') return 'image/png'
  if (extension === '.jpg' || extension === '.jpeg') return 'image/jpeg'
  if (extension === '.svg') return 'image/svg+xml'
  return 'application/octet-stream'
}

async function installChatMediaFixture(page) {
  const json = (data) => ({ success: true, message: 'ok', data })
  const conversation = {
    conversationId: 10,
    peerUserId: 3,
    peerNickname: '手机兼容验收号',
    peerAvatarUrl: '',
    peerGender: 'goddess',
    peerCity: '杭州',
    peerMainRole: 'SELLER',
    peerVideoVerified: true,
    peerSellerCharmScore: 39,
    peerBuyerPowerScore: 0,
    lastMessageSummary: '移动端媒体兼容验收',
    lastServerSeq: 3,
    deliveredSeq: 3,
    readSeq: 3,
    unreadCount: 0,
    updatedAt: '2026-06-09T08:00:00'
  }
  const currentUser = {
    userId: 1,
    nickname: '验收用户',
    avatarUrl: '',
    mainRole: 'BUYER',
    identityStatus: 'VERIFIED',
    videoIdentityStatus: 'UNVERIFIED',
    videoVerified: false,
    gender: 'god',
    city: '上海',
    sellerCharmScore: 0,
    buyerPowerScore: 18,
    followedByMe: false,
    followerCount: 0,
    followingCount: 0,
    showcaseImageUrls: [],
    level: null
  }
  const peerProfile = {
    ...currentUser,
    userId: 3,
    nickname: conversation.peerNickname,
    gender: conversation.peerGender,
    city: conversation.peerCity,
    sellerCharmScore: conversation.peerSellerCharmScore,
    buyerPowerScore: 0,
    videoVerified: true
  }
  const messages = [
    {
      conversationId: 10,
      serverSeq: 1,
      serverMsgId: 'compat-image-1',
      clientMsgId: 'compat-image-client-1',
      senderId: 3,
      receiverId: 1,
      msgType: 'IMAGE',
      contentJson: JSON.stringify({ url: '/uploads/chat-image/mobile-compat.png', width: 1, height: 1, mimeType: 'image/png' }),
      createdAt: '2026-06-09T08:00:01',
      deliveredToReceiver: true,
      readByReceiver: true
    },
    {
      conversationId: 10,
      serverSeq: 2,
      serverMsgId: 'compat-video-1',
      clientMsgId: 'compat-video-client-1',
      senderId: 3,
      receiverId: 1,
      msgType: 'VIDEO',
      contentJson: JSON.stringify({ url: '/uploads/chat-video/mobile-compat.mp4', durationMs: 1000, sizeBytes: fs.statSync(videoPath).size, mimeType: 'video/mp4' }),
      createdAt: '2026-06-09T08:00:02',
      deliveredToReceiver: true,
      readByReceiver: true
    }
  ]

  await page.route('**/api/user/me', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(currentUser)) }))
  await page.route('**/api/user/3/profile', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(peerProfile)) }))
  await page.route('**/api/chat/conversations', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json({ conversations: [conversation], serverTime: new Date().toISOString() })) }))
  await page.route('**/api/chat/conversations/10', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json(conversation)) }))
  await page.route('**/api/chat/conversations/10/messages**', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({ messages, nextAfterSeq: 2, hasMore: false, previousBeforeSeq: 1, hasEarlier: false }))
  }))
  await page.route('**/api/chat/conversations/10/delivered', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({ conversationId: 10, deliveredSeq: 2, readSeq: 2, lastServerSeq: 2, unreadCount: 0 }))
  }))
  await page.route('**/api/chat/conversations/10/read', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({ conversationId: 10, deliveredSeq: 2, readSeq: 2, lastServerSeq: 2, unreadCount: 0 }))
  }))
  await page.route('**/api/chat/media?**', (route) => {
    const target = new URL(route.request().url()).searchParams.get('url') || ''
    if (target.includes('/uploads/chat-video/')) {
      route.fulfill({ status: 200, contentType: 'video/mp4', body: fs.readFileSync(videoPath) })
      return
    }
    route.fulfill({ status: 200, contentType: 'image/png', body: transparentPng })
  })
}

async function inspectBrowserCapability(page) {
  return page.evaluate(() => ({
    secureContext: window.isSecureContext === true,
    hasGetUserMedia: typeof navigator !== 'undefined' && typeof navigator.mediaDevices?.getUserMedia === 'function',
    hasMediaRecorder: typeof MediaRecorder !== 'undefined',
    supportedVoiceMimeType: typeof MediaRecorder !== 'undefined' && typeof MediaRecorder.isTypeSupported === 'function'
      ? ['audio/webm', 'audio/mp4', 'audio/x-m4a', 'audio/aac', 'audio/mpeg', 'audio/wav'].find((type) => MediaRecorder.isTypeSupported(type)) || ''
      : '',
    fullscreenEnabled: document.fullscreenEnabled !== false,
    hasVideoFullscreenApi: (() => {
      const video = document.createElement('video')
      return typeof video.requestFullscreen === 'function' ||
        typeof video.webkitEnterFullscreen === 'function' ||
        typeof video.webkitRequestFullscreen === 'function'
    })()
  }))
}

async function inspectKeyboardPosition(page) {
  await page.locator('.composer .field').click({ timeout: 5000 })
  await page.waitForTimeout(450)
  return page.evaluate(() => {
    const pageElement = document.querySelector('.chat-page')
    const composer = document.querySelector('.composer')
    const field = document.querySelector('.composer .field')
    const scroll = document.querySelector('.message-scroll')
    const spacer = document.querySelector('.message-bottom-spacer')
    if (!pageElement || !composer || !field || !scroll || !spacer) return { ok: false, message: 'missing chat layout nodes' }
    const viewportHeight = window.innerHeight
    const composerRect = composer.getBoundingClientRect()
    const fieldRect = field.getBoundingClientRect()
    const scrollRect = scroll.getBoundingClientRect()
    const spacerRect = spacer.getBoundingClientRect()
    const keyboardInset = Number.parseFloat(getComputedStyle(pageElement).getPropertyValue('--chat-keyboard-inset'))
    if (!Number.isFinite(keyboardInset) || keyboardInset <= 0) return { ok: false, message: 'keyboard inset was not reserved after input focus' }
    if (composerRect.bottom > viewportHeight + 2 || composerRect.height <= 0) return { ok: false, message: 'composer is clipped after keyboard focus' }
    if (fieldRect.width < 120) return { ok: false, message: 'composer input is squeezed after keyboard focus' }
    if (scrollRect.height < Math.min(260, viewportHeight * 0.42)) return { ok: false, message: 'message list viewport is too small after keyboard focus' }
    if (spacerRect.height < composerRect.height + 20) return { ok: false, message: 'bottom spacer is too small for composer' }
    return { ok: true, message: 'ok', keyboardInset, composerBottom: composerRect.bottom, viewportHeight, inputWidth: fieldRect.width }
  })
}

async function inspectDeniedVoicePermission(page) {
  await page.locator('.voice-tool').click({ timeout: 5000 })
  await page.waitForTimeout(600)
  return page.evaluate(() => {
    const recording = document.querySelector('.recording-status')
    const statusText = document.querySelector('.status-bar')?.textContent || ''
    if (recording) return { ok: false, message: 'recording UI stayed open after microphone permission denial', statusText }
    if (!/麦克风权限|无法开始录音|语音录制失败/.test(statusText)) {
      return { ok: false, message: `missing microphone permission feedback: ${statusText || 'empty'}`, statusText }
    }
    return { ok: true, message: 'ok', statusText }
  })
}

async function inspectImagePreviewFallbackClose(page) {
  await page.evaluate(() => {
    if (window.uni && typeof window.uni.previewImage === 'function') {
      window.__xqyOriginalPreviewImage = window.uni.previewImage
      window.uni.previewImage = (options) => options?.fail?.(new Error('forced preview fallback for compatibility check'))
    }
  })
  await page.locator('.message-media.image').click({ timeout: 5000 })
  await page.waitForSelector('.image-preview-overlay', { timeout: 8000 })
  const openResult = await page.evaluate(() => {
    const overlay = document.querySelector('.image-preview-overlay')
    const close = document.querySelector('.image-preview-close')
    const image = document.querySelector('.image-preview-img')
    if (!overlay || !close || !image) return { ok: false, message: 'preview fallback overlay is incomplete' }
    const overlayRect = overlay.getBoundingClientRect()
    if (overlayRect.width <= 0 || overlayRect.height <= 0) return { ok: false, message: 'preview fallback overlay is not visible' }
    return { ok: true, message: 'ok' }
  })
  if (!openResult.ok) return openResult
  await page.locator('.image-preview-close').click({ timeout: 5000 })
  await page.waitForTimeout(250)
  return page.evaluate(() => {
    if (document.querySelector('.image-preview-overlay')) return { ok: false, message: 'preview fallback overlay did not close' }
    return { ok: true, message: 'ok' }
  })
}

async function inspectVideoPlaybackAndFullscreen(page) {
  const placeholder = page.locator('.message-media.video .message-media-placeholder')
  if (await placeholder.count()) await placeholder.click({ timeout: 5000 })
  else await page.locator('.message-media.video').click({ timeout: 5000 })
  await page.waitForSelector('.message-video', { timeout: 8000 })
  await page.waitForTimeout(300)
  return page.evaluate(async () => {
    const host = document.querySelector('.message-video')
    if (!host) return { ok: false, message: 'video component is missing' }
    const video = host instanceof HTMLVideoElement
      ? host
      : (host.shadowRoot?.querySelector('video') || host.querySelector('video'))
    const videoComponent = host.tagName.toLowerCase() === 'uni-video'
      ? host
      : (video instanceof HTMLVideoElement ? video.closest('uni-video') : null) || host
    const componentHtml = () => (videoComponent?.outerHTML || host.outerHTML || '').slice(0, 360)
    const hasUniPlayControl = () => !!videoComponent.querySelector(
      '.uni-video-cover, .uni-video-cover-play-button, .uni-video-control-button, .uni-video-controls, [class*="uni-video-control"], [class*="uni-video-cover"], [class*="uni-video-play"]'
    )
    const hasUniFullscreenControl = () => !!videoComponent.querySelector(
      '.uni-video-fullscreen, [class*="uni-video-fullscreen"]'
    )
    const hostRect = host.getBoundingClientRect()
    if (hostRect.width <= 0 || hostRect.height <= 0) return { ok: false, message: 'video component is not visible' }
    if (!(video instanceof HTMLVideoElement)) {
      const hostCanFullscreen = typeof host.requestFullscreen === 'function' ||
        typeof host.webkitRequestFullscreen === 'function' ||
        typeof host.webkitEnterFullscreen === 'function'
      if (!hasUniPlayControl()) {
        return { ok: false, message: `video component play control is not visible in compiled H5; html=${componentHtml()}` }
      }
      if (!hasUniFullscreenControl() && !hostCanFullscreen) return { ok: false, message: 'video fullscreen control/API is unavailable in compiled H5' }
      return { ok: true, message: 'ok', hostTag: host.tagName.toLowerCase(), hostWidth: hostRect.width, hostHeight: hostRect.height, hasFullscreenApi: hostCanFullscreen, hasUniFullscreenControl: hasUniFullscreenControl() }
    }
    video.muted = true
    video.playsInline = true
    video.load()
    await new Promise((resolve) => {
      if (video.readyState >= 1) return resolve()
      const timer = setTimeout(resolve, 2000)
      video.addEventListener('loadedmetadata', () => {
        clearTimeout(timer)
        resolve()
      }, { once: true })
    })
    let playSucceeded = false
    try {
      await video.play()
      playSucceeded = true
      video.pause()
    } catch {
      playSucceeded = false
    }
    const hasFullscreenApi = typeof video.requestFullscreen === 'function' ||
      typeof video.webkitEnterFullscreen === 'function' ||
      typeof video.webkitRequestFullscreen === 'function'
    if (!video.controls && !hasUniPlayControl()) {
      return {
        ok: false,
        message: `video controls are not enabled; host=${host.tagName.toLowerCase()} component=${videoComponent?.tagName?.toLowerCase?.() || ''} controls=${video.controls} playControl=${hasUniPlayControl()} fullscreenControl=${hasUniFullscreenControl()} html=${componentHtml()}`
      }
    }
    if (video.readyState < 1 || !Number.isFinite(video.duration) || video.duration <= 0) return { ok: false, message: 'video metadata did not load' }
    if (!hasFullscreenApi && !hasUniFullscreenControl()) return { ok: false, message: 'video fullscreen API/control is unavailable in this browser context' }
    return { ok: true, message: 'ok', readyState: video.readyState, duration: video.duration, playSucceeded, hasFullscreenApi, hasUniFullscreenControl: hasUniFullscreenControl() }
  })
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error))
  process.exit(1)
})
