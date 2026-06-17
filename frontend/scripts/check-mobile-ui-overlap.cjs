const fs = require('fs')
const path = require('path')
const http = require('http')
const https = require('https')

const root = path.resolve(__dirname, '..')
const outputDir = path.resolve(root, '..', 'output', 'playwright', 'mobile-ui')
const h5DistDir = path.join(root, 'dist/build/h5')
let ownedPreviewServer = null
const viewports = [
  { name: '360x740', width: 360, height: 740 },
  { name: '390x844', width: 390, height: 844 }
]
const pages = [
  {
    name: 'home',
    hash: '/pages/tabbar/home/index',
    coreTabFixture: true,
    selectors: {
      page: '.home-page',
      topCard: '.banner-swiper, .banner-empty',
      required: '.ranking-entrance, .product-grid',
      cards: '.product-grid-card',
      bottomNav: '.global-bottom-nav-wrap',
      allowScrollableContentUnderBottomNav: true
    }
  },
  {
    name: 'category',
    hash: '/pages/tabbar/category/index',
    coreTabFixture: true,
    selectors: {
      page: '.category-page',
      topCard: '.search-card',
      required: '.category-pills, .product-grid',
      cards: '.product-card',
      bottomNav: '.global-bottom-nav-wrap',
      allowScrollableContentUnderBottomNav: true
    }
  },
  {
    name: 'community',
    hash: '/pages/tabbar/message/index',
    selectors: {
      page: '.community-page',
      bottomNav: '.global-bottom-nav-wrap',
      floating: '.compose-fab',
      communitySwitcher: '.community-switcher',
      communitySwitcherToggle: '.global-bottom-nav-wrap .bottom-nav-item:has-text("社区")'
    },
    switcherCheck: true
  },
  {
    name: 'community-compose',
    hash: '/pages/community/compose/index',
    selectors: {
      page: '.compose-page',
      topCard: '.hero',
      action: '.submit',
      inputs: '.field, .textarea',
      bottomSafeGap: 18,
      absentBottomNav: '.global-bottom-nav-wrap'
    }
  },
  {
    name: 'community-detail',
    hash: '/pages/community/detail/index?postId=1',
    communityDetailFixture: true,
    selectors: {
      page: '.detail-page',
      topCard: '.post-card',
      required: '.comment-form, .comment-input',
      action: '.comment-form',
      inputs: '.comment-input',
      focusSelector: '.comment-input',
      bottomSafeGap: 18,
      absentBottomNav: '.global-bottom-nav-wrap'
    }
  },
  {
    name: 'session-list',
    hash: '/pages/chat/session-list/index',
    chatSessionListFixture: true,
    selectors: {
      page: '.session-page',
      sessionList: '.session-list',
      cards: '.session-card',
      absentTicker: '.global-ticker-wrap'
    }
  },
  {
    name: 'me',
    hash: '/pages/tabbar/me/index',
    coreTabFixture: true,
    selectors: {
      page: '.me-page',
      topCard: '.profile-card',
      required: '.wallet-card, .ops-card, .order-status, .menu-card',
      cards: '.menu-card',
      bottomNav: '.global-bottom-nav-wrap',
      floating: '.seller-verify-fab',
      allowScrollableContentUnderBottomNav: true
    }
  },
  {
    name: 'conversation',
    hash: '/pages/chat/conversation/index?receiverId=2',
    authenticatedChatFixture: true,
    selectors: {
      page: '.chat-page',
      header: '.chat-header',
      peerMain: '.peer-main',
      headerActions: '.header-actions',
      scroll: '.message-scroll',
      composer: '.composer',
      composerField: '.composer .field',
      focusSelector: '.composer .field',
      statusBar: '.status-bar',
      spacer: '.message-bottom-spacer',
      absentBottomNav: '.global-bottom-nav-wrap',
      absentTicker: '.global-ticker-wrap'
    }
  },
  {
    name: 'identity',
    hash: '/pages/user/identity/index',
    selectors: {
      page: '.identity-page',
      topCard: '.hero',
      cards: '.video-card, .form-card, .check-card',
      actions: '.primary-btn',
      inputs: '.input',
      bottomSafeGap: 18,
      absentBottomNav: '.global-bottom-nav-wrap'
    }
  },
  {
    name: 'product-detail',
    hash: '/pages/product/detail/index?productId=101',
    productProfileFixture: true,
    selectors: {
      page: '.detail-page',
      topCard: '.hero-card',
      required: '.info-card, .seller-card, .rule-card, .action-panel',
      cards: '.hero-card, .info-card, .seller-card, .rule-card, .action-panel',
      action: '.bottom-actions',
      bottomSafeGap: 0,
      absentBottomNav: '.global-bottom-nav-wrap',
      allowScrollableContentUnderBottomNav: true
    }
  },
  {
    name: 'public-profile',
    hash: '/pages/user/public-profile/index?userId=8187306278',
    productProfileFixture: true,
    selectors: {
      page: '.public-profile',
      topCard: '.hero',
      required: '.action-row, .seller-trade-card',
      action: '.action-row',
      cards: '.video-verify-card, .showcase-card, .section-card',
      bottomSafeGap: 18,
      absentBottomNav: '.global-bottom-nav-wrap',
      allowScrollableContentUnderBottomNav: true
    }
  }
]

async function main() {
  const playwright = loadPlaywright()
  const baseUrl = await resolveBaseUrl()
  fs.mkdirSync(outputDir, { recursive: true })
  const browser = await playwright.chromium.launch({ headless: true })
  const failures = []
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        isMobile: true,
        hasTouch: true,
        deviceScaleFactor: 2
      })
      await context.addInitScript(() => {
        window.localStorage.setItem('xiaoyuan_user_access_token', 'mobile-ui-overlap-check-token')
      })
      for (const pageSpec of pages) {
        const page = await context.newPage()
        if (pageSpec.coreTabFixture) {
          await installCoreTabFixture(page)
        }
        if (pageSpec.chatSessionListFixture) {
          await installChatSessionListFixture(page)
        }
        if (pageSpec.authenticatedChatFixture) {
          await installAuthenticatedChatFixture(page)
        }
        if (pageSpec.communityDetailFixture) {
          await installCommunityDetailFixture(page)
        }
        if (pageSpec.productProfileFixture) {
          await installProductProfileFixture(page)
        }
        const url = `${baseUrl}/#${pageSpec.hash}`
        try {
          await page.goto(url, { waitUntil: 'networkidle', timeout: 20000 })
          await page.waitForTimeout(500)
          await page.screenshot({
            path: path.join(outputDir, `${pageSpec.name}-${viewport.name}.png`),
            fullPage: true
          })
          const result = await inspectPage(page, pageSpec.selectors)
          if (!result.ok) {
            failures.push(`${pageSpec.name}@${viewport.name}: ${result.message}`)
          }
          if (pageSpec.switcherCheck) {
            const switcherResult = await inspectCommunitySwitcher(page, pageSpec.selectors)
            if (!switcherResult.ok) {
              failures.push(`${pageSpec.name}:switcher@${viewport.name}: ${switcherResult.message}`)
            }
          }
          if (pageSpec.selectors.focusSelector) {
            await page.locator(pageSpec.selectors.focusSelector).click({ timeout: 5000 })
            await page.waitForTimeout(400)
            await page.screenshot({
              path: path.join(outputDir, `${pageSpec.name}-focused-${viewport.name}.png`),
              fullPage: true
            })
            const focusedResult = await inspectPage(page, { ...pageSpec.selectors, requireKeyboardInset: true })
            if (!focusedResult.ok) {
              failures.push(`${pageSpec.name}:focused@${viewport.name}: ${focusedResult.message}`)
            }
          }
        } catch (error) {
          failures.push(`${pageSpec.name}@${viewport.name}: ${error instanceof Error ? error.message : String(error)}`)
        } finally {
          await page.close()
        }
      }
      await context.close()
    }
  } finally {
    await browser.close()
    if (ownedPreviewServer) {
      await new Promise((resolve) => ownedPreviewServer.close(resolve))
      ownedPreviewServer = null
    }
  }
  if (failures.length) {
    console.error(failures.join('\n'))
    process.exit(1)
  }
  console.log(`mobile ui overlap check passed; screenshots: ${outputDir}`)
}

function loadPlaywright() {
  try {
    return require('playwright')
  } catch (error) {
    console.error([
      'check:mobile-ui-overlap requires Playwright to be installed locally.',
      'Run this once in frontend/: npm install --save-dev playwright --legacy-peer-deps',
      'Then install browser binaries if needed: npx playwright install chromium',
      `Load error: ${error instanceof Error ? error.message : String(error)}`
    ].join('\n'))
    process.exit(1)
  }
}

async function resolveBaseUrl() {
  const explicit = normalizeBaseUrl(process.env.MOBILE_UI_BASE_URL || '')
  if (explicit) {
    await assertReachable(explicit)
    return explicit
  }
  if (fs.existsSync(path.join(h5DistDir, 'index.html'))) {
    const preview = await startCurrentDistPreview()
    ownedPreviewServer = preview.server
    return preview.baseUrl
  }
  const candidates = ['http://127.0.0.1:4187', 'http://localhost:4187', 'http://127.0.0.1:4173', 'http://localhost:4173', 'http://127.0.0.1:5173', 'http://localhost:5173']
  for (const candidate of candidates) {
    try {
      await assertReachable(candidate)
      return candidate
    } catch {
      // try the next local dev/preview server
    }
  }
  throw new Error('No reachable H5 server found. Start frontend dev server or set MOBILE_UI_BASE_URL.')
}

function startCurrentDistPreview() {
  const server = http.createServer((request, response) => {
    const rawUrl = request.url || '/'
    const urlPath = decodeURIComponent(rawUrl.split('?')[0] || '/')
    if (urlPath === '/api' || urlPath.startsWith('/api/')) {
      response.writeHead(404)
      response.end('Not found')
      return
    }
    const relativePath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '')
    const requestedPath = path.resolve(h5DistDir, relativePath)
    const safePath = requestedPath.startsWith(h5DistDir + path.sep) || requestedPath === h5DistDir
      ? requestedPath
      : path.join(h5DistDir, 'index.html')
    const filePath = fs.existsSync(safePath) && fs.statSync(safePath).isFile()
      ? safePath
      : path.join(h5DistDir, 'index.html')
    if (!fs.existsSync(filePath)) {
      response.writeHead(404)
      response.end('Not found')
      return
    }
    response.writeHead(200, { 'Content-Type': contentType(filePath) })
    fs.createReadStream(filePath).pipe(response)
  })
  return new Promise((resolve, reject) => {
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close()
        reject(new Error('failed to start current H5 dist preview'))
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
  if (extension === '.json') return 'application/json; charset=utf-8'
  if (extension === '.png') return 'image/png'
  if (extension === '.jpg' || extension === '.jpeg') return 'image/jpeg'
  if (extension === '.webp') return 'image/webp'
  if (extension === '.svg') return 'image/svg+xml'
  return 'application/octet-stream'
}

function normalizeBaseUrl(value) {
  const trimmed = value.trim().replace(/\/+$/, '')
  if (!trimmed) return ''
  if (!/^https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?$/i.test(trimmed)) {
    throw new Error('MOBILE_UI_BASE_URL must point to localhost or 127.0.0.1 for local mobile UI checks')
  }
  return trimmed
}

function assertReachable(baseUrl) {
  return new Promise((resolve, reject) => {
    const client = baseUrl.startsWith('https:') ? https : http
    const request = client.get(baseUrl, { timeout: 3000 }, (response) => {
      response.resume()
      if (response.statusCode && response.statusCode >= 200 && response.statusCode < 500) resolve()
      else reject(new Error(`unexpected status ${response.statusCode}`))
    })
    request.on('timeout', () => {
      request.destroy(new Error('timeout'))
    })
    request.on('error', reject)
  })
}

async function inspectCommunitySwitcher(page, selectors) {
  const toggleSelector = selectors.communitySwitcherToggle
  if (!toggleSelector) return { ok: false, message: 'missing community switcher toggle selector' }
  await page.locator(toggleSelector).click({ timeout: 5000 })
  await page.waitForTimeout(250)
  return page.evaluate((pageSelectors) => {
    function rect(selector) {
      const element = document.querySelector(selector)
      if (!element) return null
      const style = window.getComputedStyle(element)
      const value = element.getBoundingClientRect()
      return {
        top: value.top,
        right: value.right,
        bottom: value.bottom,
        left: value.left,
        width: value.width,
        height: value.height,
        display: style.display,
        visibility: style.visibility,
        opacity: Number.parseFloat(style.opacity || '1')
      }
    }
    function isVisible(value) {
      return !!value && value.display !== 'none' && value.visibility !== 'hidden' && value.opacity > 0 && value.width > 0 && value.height > 0
    }
    function overlaps(left, right, gap = 0) {
      return left.left < right.right + gap &&
        left.right > right.left - gap &&
        left.top < right.bottom + gap &&
        left.bottom > right.top - gap
    }
    const viewportHeight = window.innerHeight
    const viewportWidth = window.innerWidth
    const switcherRect = rect(pageSelectors.communitySwitcher)
    if (!isVisible(switcherRect)) return { ok: false, message: 'community switcher did not open from bottom nav' }
    if (switcherRect.left < -2 || switcherRect.right > viewportWidth + 2 || switcherRect.top < -2 || switcherRect.bottom > viewportHeight + 2) {
      return { ok: false, message: 'community switcher is clipped outside viewport' }
    }
    const floatingRect = rect(pageSelectors.floating)
    if (isVisible(floatingRect) && overlaps(floatingRect, switcherRect, 6)) {
      return { ok: false, message: 'community switcher overlaps visible compose button' }
    }
    return { ok: true, message: 'ok' }
  }, selectors)
}

async function inspectPage(page, selectors) {
  return page.evaluate((pageSelectors) => {
    function rect(selector) {
      const element = document.querySelector(selector)
      if (!element) return null
      const value = element.getBoundingClientRect()
      return {
        top: value.top,
        right: value.right,
        bottom: value.bottom,
        left: value.left,
        width: value.width,
        height: value.height
      }
    }
    function lastRect(selector) {
      const elements = Array.from(document.querySelectorAll(selector))
      const element = elements[elements.length - 1]
      if (!element) return null
      const value = element.getBoundingClientRect()
      return {
        top: value.top,
        right: value.right,
        bottom: value.bottom,
        left: value.left,
        width: value.width,
        height: value.height
      }
    }
    function overlaps(left, right, gap = 0) {
      return left.left < right.right + gap &&
        left.right > right.left - gap &&
        left.top < right.bottom + gap &&
        left.bottom > right.top - gap
    }
    const viewportHeight = window.innerHeight
    const viewportWidth = window.innerWidth
    const pageRect = rect(pageSelectors.page)
    if (!pageRect) return { ok: false, message: `missing page selector ${pageSelectors.page}` }
    if (pageRect.width <= 0 || pageRect.height <= 0) return { ok: false, message: 'page rendered with empty dimensions' }
    if (pageRect.width > viewportWidth + 2) return { ok: false, message: 'page is wider than mobile viewport' }

    const absentBottomNavRect = pageSelectors.absentBottomNav ? rect(pageSelectors.absentBottomNav) : null
    if (absentBottomNavRect) {
      return { ok: false, message: 'global bottom nav is visible on chat conversation page' }
    }
    const absentTickerRect = pageSelectors.absentTicker ? rect(pageSelectors.absentTicker) : null
    if (absentTickerRect) {
      return { ok: false, message: 'global ticker is visible on private chat page' }
    }

    const bottomNavRect = pageSelectors.bottomNav ? rect(pageSelectors.bottomNav) : null
    const floatingRect = pageSelectors.floating ? rect(pageSelectors.floating) : null
    if (pageSelectors.required) {
      const requiredSelectors = pageSelectors.required.split(',').map((selector) => selector.trim()).filter(Boolean)
      for (const selector of requiredSelectors) {
        const requiredRect = rect(selector)
        if (!requiredRect || requiredRect.width <= 0 || requiredRect.height <= 0) {
          return { ok: false, message: `missing required selector ${selector}` }
        }
      }
    }
    if (bottomNavRect) {
      if (bottomNavRect.height <= 0 || bottomNavRect.bottom > viewportHeight + 2) {
        return { ok: false, message: 'bottom nav is clipped outside viewport' }
      }
      if (floatingRect && floatingRect.bottom > bottomNavRect.top - 6) {
        return { ok: false, message: 'floating compose button overlaps bottom nav' }
      }
    }

    const topCardRect = pageSelectors.topCard ? rect(pageSelectors.topCard) : null
    if (topCardRect) {
      if (topCardRect.top < -2 || topCardRect.width <= 0 || topCardRect.height <= 0) {
        return { ok: false, message: 'first content card is clipped or empty' }
      }
      if (topCardRect.left < -2 || topCardRect.right > viewportWidth + 2) {
        return { ok: false, message: 'first content card overflows mobile viewport' }
      }
    }

    const headerRect = pageSelectors.header ? rect(pageSelectors.header) : null
    const peerMainRect = pageSelectors.peerMain ? rect(pageSelectors.peerMain) : null
    const headerActionsRect = pageSelectors.headerActions ? rect(pageSelectors.headerActions) : null
    if (headerRect) {
      if (headerRect.height > 86) {
        return { ok: false, message: 'chat header is too tall for small mobile viewport' }
      }
      if (headerRect.bottom > viewportHeight * 0.18) {
        return { ok: false, message: 'chat header consumes too much vertical space' }
      }
      if (peerMainRect && headerActionsRect && overlaps(peerMainRect, headerActionsRect)) {
        return { ok: false, message: 'chat peer title area overlaps header actions' }
      }
    }

    const composerRect = pageSelectors.composer ? rect(pageSelectors.composer) : null
    const composerFieldRect = pageSelectors.composerField ? rect(pageSelectors.composerField) : null
    const messageScrollRect = pageSelectors.scroll ? rect(pageSelectors.scroll) : null
    const statusBarRect = pageSelectors.statusBar ? rect(pageSelectors.statusBar) : null
    const spacerRect = pageSelectors.spacer ? rect(pageSelectors.spacer) : null
    if (composerRect) {
      if (pageSelectors.requireKeyboardInset) {
        const keyboardInset = getComputedStyle(pageRect ? document.querySelector(pageSelectors.page) : document.documentElement).getPropertyValue('--chat-keyboard-inset').trim()
        const insetPixels = Number.parseFloat(keyboardInset)
        if (!Number.isFinite(insetPixels) || insetPixels <= 0) {
          return { ok: false, message: 'chat focused input did not reserve keyboard inset' }
        }
      }
      if (composerRect.height <= 0 || composerRect.bottom > viewportHeight + 2) {
        return { ok: false, message: 'chat composer is clipped outside viewport' }
      }
      if (composerFieldRect && composerFieldRect.width < 92) {
        return { ok: false, message: 'chat composer input is squeezed on small mobile viewport' }
      }
      if (!spacerRect && messageScrollRect && messageScrollRect.bottom > composerRect.top - 8) {
        return { ok: false, message: 'chat message viewport overlaps composer' }
      }
      if (messageScrollRect && messageScrollRect.height < Math.min(260, viewportHeight * 0.42)) {
        return { ok: false, message: 'chat message viewport is too small on mobile' }
      }
      if (statusBarRect && overlaps(statusBarRect, composerRect)) {
        return { ok: false, message: 'chat status bar overlaps composer' }
      }
      if (spacerRect && spacerRect.height < composerRect.height + 24) {
        return { ok: false, message: 'chat bottom spacer does not reserve composer height' }
      }
    }

    const sessionListRect = pageSelectors.sessionList ? rect(pageSelectors.sessionList) : null
    if (pageSelectors.sessionList) {
      if (!sessionListRect || sessionListRect.width <= 0 || sessionListRect.height <= 0) {
        return { ok: false, message: 'private chat session list is missing on mobile' }
      }
      if (sessionListRect.height < Math.min(300, viewportHeight * 0.38)) {
        return { ok: false, message: 'private chat session list viewport is too small on mobile' }
      }
      if (sessionListRect.bottom > viewportHeight + 2) {
        return { ok: false, message: 'private chat session list is clipped outside mobile viewport' }
      }
    }

    const actionSelectors = [pageSelectors.action, pageSelectors.actions].filter(Boolean).join(',')
    const actionRects = actionSelectors
      ? Array.from(document.querySelectorAll(actionSelectors)).map((element) => {
        const value = element.getBoundingClientRect()
        return {
          top: value.top,
          right: value.right,
          bottom: value.bottom,
          left: value.left,
          width: value.width,
          height: value.height
        }
      }).filter((value) => value.width > 0 && value.height > 0)
      : []
    const bottomSafeGap = typeof pageSelectors.bottomSafeGap === 'number' ? pageSelectors.bottomSafeGap : 0
    for (const actionRect of actionRects) {
      if (actionRect.left < -2 || actionRect.right > viewportWidth + 2) {
        return { ok: false, message: 'primary action overflows mobile viewport' }
      }
      if (actionRect.bottom > viewportHeight - bottomSafeGap && pageRect.height <= viewportHeight + 2) {
        return { ok: false, message: 'primary action is too close to bottom safe area' }
      }
      if (bottomNavRect && actionRect.bottom > bottomNavRect.top - 8) {
        return { ok: false, message: 'primary action overlaps bottom nav' }
      }
    }

    if (pageSelectors.inputs) {
      const inputRects = Array.from(document.querySelectorAll(pageSelectors.inputs)).map((element) => {
        const value = element.getBoundingClientRect()
        return {
          top: value.top,
          right: value.right,
          bottom: value.bottom,
          left: value.left,
          width: value.width,
          height: value.height
        }
      }).filter((value) => value.width > 0 && value.height > 0)
      for (const inputRect of inputRects) {
        if (inputRect.left < -2 || inputRect.right > viewportWidth + 2) {
          return { ok: false, message: 'form input overflows mobile viewport' }
        }
        if (inputRect.width < 96) {
          return { ok: false, message: 'form input is squeezed on small mobile viewport' }
        }
      }
    }

    const lastCardRect = pageSelectors.cards ? lastRect(pageSelectors.cards) : null
    if (lastCardRect && bottomNavRect && !pageSelectors.allowScrollableContentUnderBottomNav && lastCardRect.bottom > bottomNavRect.top - 8) {
      return { ok: false, message: 'last content card overlaps bottom nav' }
    }

    return { ok: true, message: 'ok' }
  }, selectors)
}

async function installCoreTabFixture(page) {
  const json = (data) => ({ success: true, message: 'ok', data })
  const now = new Date().toISOString()
  const products = [
    {
      productId: 101,
      productNo: 'PRD-MOBILE-101',
      sellerId: 2,
      sellerNickname: '初见卖家',
      sellerAvatarUrl: '',
      sellerGender: 'goddess',
      sellerCity: '',
      sellerVideoVerified: true,
      title: '奶油白连衣裙 轻微瑕疵已说明',
      category: '上衣',
      price: '168.00',
      coverImageUrl: null,
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    },
    {
      productId: 102,
      productNo: 'PRD-MOBILE-102',
      sellerId: 3,
      sellerNickname: '雨哥体验号',
      sellerAvatarUrl: '',
      sellerGender: 'god',
      sellerCity: '',
      sellerVideoVerified: false,
      title: '小众复古包包 日常通勤可背',
      category: '包包',
      price: '89.00',
      coverImageUrl: null,
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    },
    {
      productId: 103,
      productNo: 'PRD-MOBILE-103',
      sellerId: 4,
      sellerNickname: '鞋柜整理',
      sellerAvatarUrl: '',
      sellerGender: 'goddess',
      sellerCity: '',
      sellerVideoVerified: true,
      title: '低跟单鞋 只穿过一次',
      category: '鞋子',
      price: '126.00',
      coverImageUrl: null,
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    },
    {
      productId: 104,
      productNo: 'PRD-MOBILE-104',
      sellerId: 5,
      sellerNickname: '小物收藏',
      sellerAvatarUrl: '',
      sellerGender: 'god',
      sellerCity: '',
      sellerVideoVerified: false,
      title: '暖色围巾柔软不扎肤',
      category: '小物',
      price: '45.00',
      coverImageUrl: null,
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    }
  ]
  const banners = [
    {
      id: 1,
      kicker: '上新',
      title: '今晚看看新上架',
      description: '真实在售宝贝优先展示',
      cta: '去看看',
      imageUrl: '',
      action: 'closet',
      placement: 'HOME',
      sortOrder: 1,
      enabled: true,
      sizeHint: 'wide',
      updatedAt: now
    }
  ]
  const profile = {
    userId: 1,
    userNo: 'XYQ10001',
    nickname: '移动端验收用户',
    avatarUrl: '',
    mainRole: 'SELLER',
    identityStatus: 'VERIFIED',
    videoIdentityStatus: 'APPROVED',
    videoVerified: true,
    gender: 'goddess',
    city: '杭州',
    sellerCharmScore: 48,
    buyerPowerScore: 18,
    followedByMe: false,
    followerCount: 12,
    followingCount: 4,
    showcaseImageUrls: [],
    level: { level: 2, title: '心动新星', track: 'CHARM', score: 48, progressPercent: 62 }
  }
  const wallet = {
    rechargeBalance: '66.00',
    incomeBalance: '128.00',
    frozenBalance: '0.00',
    withdrawableBalance: '128.00'
  }
  const conversations = {
    conversations: [
      {
        conversationId: 1,
        peerUserId: 2,
        peerNickname: '移动端聊天对象',
        peerAvatarUrl: '',
        peerGender: 'god',
        peerCity: '杭州',
        peerMainRole: 'SELLER',
        peerVideoVerified: true,
        peerSellerCharmScore: 38,
        peerBuyerPowerScore: 0,
        lastMessageSummary: '手机端聊天窗口自适应检查',
        lastServerSeq: 6,
        deliveredSeq: 6,
        readSeq: 5,
        unreadCount: 1,
        updatedAt: '2026-06-09T07:30:00'
      }
    ],
    serverTime: now
  }
  const notifications = [
    {
      notificationNo: 'NTF-MOBILE-1',
      userId: 1,
      type: 'CHAT',
      title: '有新的私信',
      description: '移动端消息中心验收',
      targetUrl: '/pages/chat/session-list/index',
      read: false,
      createdAt: now
    }
  ]
  const buyerOrders = [
    {
      orderNo: 'ORD-MOBILE-BUYER-1',
      buyerId: 1,
      sellerId: 2,
      productId: 101,
      goodsId: 101,
      productNo: 'PRD-MOBILE-101',
      productTitle: '奶油白连衣裙',
      amount: '168.00',
      tradeRuleSnapshot: '按平台订单流程交易',
      status: 'PENDING_PAY',
      role: 'buyer',
      counterpartyName: '初见卖家',
      createdAt: now
    }
  ]
  const sellerOrders = [
    {
      orderNo: 'ORD-MOBILE-SELLER-1',
      buyerId: 3,
      sellerId: 1,
      productId: 102,
      goodsId: 102,
      productNo: 'PRD-MOBILE-102',
      productTitle: '小众复古包包',
      amount: '89.00',
      tradeRuleSnapshot: '按平台订单流程交易',
      status: 'PAID',
      role: 'seller',
      counterpartyName: '雨哥体验号',
      createdAt: now
    }
  ]
  await page.route('**/api/home/banners', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(banners))
  }))
  await page.route('**/api/products', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(products))
  }))
  await page.route('**/api/user/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(profile))
  }))
  await page.route('**/api/wallet/balance', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(wallet))
  }))
  await page.route('**/api/notifications**', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(notifications))
  }))
  await page.route('**/api/chat/conversations', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(conversations))
  }))
  await page.route('**/api/orders**', (route) => {
    const url = new URL(route.request().url())
    const role = url.searchParams.get('role')
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(json(role === 'seller' ? sellerOrders : buyerOrders))
    })
  })
}

async function installCommunityDetailFixture(page) {
  const json = (data) => ({ success: true, message: 'ok', data })
  const authorProfile = {
    userId: 2,
    nickname: '社区作者',
    avatarUrl: '',
    mainRole: 'SELLER',
    identityStatus: 'VERIFIED',
    videoIdentityStatus: 'APPROVED',
    videoVerified: true,
    gender: 'goddess',
    city: '上海',
    sellerCharmScore: 42,
    buyerPowerScore: 0,
    followedByMe: false,
    followerCount: 6,
    followingCount: 3,
    showcaseImageUrls: [],
    level: null
  }
  const postDetail = {
    postNo: 'POST-2-1770000000001',
    postId: 1,
    authorId: 2,
    authorName: '社区作者',
    authorAvatar: '',
    title: '移动端详情检查',
    topic: '生活日常',
    content: '用于检查社区详情评论输入框在移动端不会被底部区域遮挡。',
    imageUrls: [],
    status: 'PUBLISHED',
    likeCount: 2,
    commentCount: 1,
    likedByMe: false,
    followedByMe: false,
    createdAt: '2026-06-09T07:00:00',
    city: '上海',
    relatedProductId: null,
    relatedProductTitle: null,
    relatedProductPrice: null,
    comments: [
      {
        commentNo: 'CMT-2-1770000000001-AAAAAA1111',
        authorId: 2,
        authorName: '社区作者',
        authorAvatar: '',
        content: '真实评论输入框覆盖检查',
        createdAt: '2026-06-09T07:01:00'
      }
    ]
  }
  await page.route('**/api/community/posts/1', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(postDetail))
  }))
  await page.route('**/api/user/2/profile', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(authorProfile))
  }))
  await page.route('**/api/chat/conversations', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({ conversations: [], serverTime: new Date().toISOString() }))
  }))
}

async function installProductProfileFixture(page) {
  const json = (data) => ({ success: true, message: 'ok', data })
  const now = new Date().toISOString()
  const sellerProfile = {
    userId: 8187306278,
    userNo: 'XYQ818730',
    nickname: '认证卖家体验号',
    avatarUrl: '/uploads/avatar/mobile-seller-avatar.jpg',
    mainRole: 'SELLER',
    identityStatus: 'VERIFIED',
    videoIdentityStatus: 'APPROVED',
    videoVerified: true,
    videoIdentityUrl: '/uploads/video-identity/mobile-seller-verify.mp4',
    gender: 'goddess',
    age: 24,
    city: '杭州',
    bio: '喜欢分享状态清楚、描述完整的闲置好物。',
    sellerCharmScore: 268,
    buyerPowerScore: 18,
    followedByMe: false,
    followerCount: 36,
    followingCount: 7,
    showcaseImageUrls: [
      '/uploads/community-image/mobile-showcase-1.jpg',
      '/uploads/community-image/mobile-showcase-2.jpg',
      '/uploads/community-image/mobile-showcase-3.jpg'
    ],
    level: { level: 4, title: '星光女神', track: 'CHARM', score: 268, progressPercent: 58 }
  }
  const currentProfile = {
    userId: 1,
    userNo: 'XYQ10001',
    nickname: '移动端验收用户',
    avatarUrl: '',
    mainRole: 'BUYER',
    identityStatus: 'UNVERIFIED',
    videoIdentityStatus: 'UNVERIFIED',
    videoVerified: false,
    gender: 'god',
    city: '上海',
    sellerCharmScore: 0,
    buyerPowerScore: 42,
    followedByMe: false,
    followerCount: 0,
    followingCount: 0,
    showcaseImageUrls: [],
    level: null
  }
  const activeProducts = [
    {
      productId: 101,
      productNo: 'PRD-MOBILE-101',
      sellerId: 8187306278,
      sellerNickname: sellerProfile.nickname,
      sellerAvatarUrl: sellerProfile.avatarUrl,
      sellerGender: sellerProfile.gender,
      sellerCity: '',
      sellerVideoVerified: true,
      title: '奶油白连衣裙 轻微瑕疵已说明',
      category: '衣物',
      price: '168.00',
      coverImageUrl: '/uploads/product-image/mobile-product-101.jpg',
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    },
    {
      productId: 102,
      productNo: 'PRD-MOBILE-102',
      sellerId: 8187306278,
      sellerNickname: sellerProfile.nickname,
      sellerAvatarUrl: sellerProfile.avatarUrl,
      sellerGender: sellerProfile.gender,
      sellerCity: '',
      sellerVideoVerified: true,
      title: '复古通勤小包 成色很新',
      category: '包包',
      price: '89.00',
      coverImageUrl: '/uploads/product-image/mobile-product-102.jpg',
      status: 'ACTIVE',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    }
  ]
  const soldProducts = [
    {
      productId: 201,
      productNo: 'PRD-MOBILE-SOLD-201',
      sellerId: 8187306278,
      title: '低跟单鞋 只穿过一次',
      category: '鞋子',
      price: '126.00',
      coverImageUrl: '/uploads/product-image/mobile-product-201.jpg',
      status: 'SOLD',
      auditState: 'APPROVED',
      visible: true,
      createdAt: now
    }
  ]
  const detail = {
    productId: 101,
    productNo: 'PRD-MOBILE-101',
    sellerId: 8187306278,
    title: '奶油白连衣裙 轻微瑕疵已说明',
    description: '衣服整体很干净，肩线和裙摆状态已拍清楚，介意瑕疵请先私信确认。',
    price: '168.00',
    imageUrls: [
      '/uploads/product-image/mobile-product-101.jpg',
      '/uploads/product-image/mobile-product-101-detail.jpg'
    ],
    status: 'ACTIVE',
    auditState: 'APPROVED',
    visible: true,
    tradeRule: '按平台订单流程交易',
    favoritedByMe: false,
    createdAt: now
  }
  await page.route('**/api/user/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(currentProfile))
  }))
  await page.route('**/api/user/8187306278/profile', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(sellerProfile))
  }))
  await page.route('**/api/products/101', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(detail))
  }))
  await page.route('**/api/products/seller/8187306278', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(activeProducts))
  }))
  await page.route('**/api/products/seller/8187306278/sold', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(soldProducts))
  }))
}

async function installChatSessionListFixture(page) {
  const json = (data) => ({ success: true, message: 'ok', data })
  await page.route('**/api/chat/conversations', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({
      conversations: [
        {
          conversationId: 1,
          peerUserId: 2,
          peerNickname: '移动端聊天对象',
          peerAvatarUrl: '',
          peerGender: 'god',
          peerCity: '杭州',
          peerMainRole: 'SELLER',
          peerVideoVerified: true,
          peerSellerCharmScore: 38,
          peerBuyerPowerScore: 0,
          lastMessageSummary: '手机端聊天窗口自适应检查',
          lastServerSeq: 6,
          deliveredSeq: 6,
          readSeq: 5,
          unreadCount: 1,
          updatedAt: '2026-06-09T07:30:00'
        },
        {
          conversationId: 2,
          peerUserId: 3,
          peerNickname: '雨哥体验号',
          peerAvatarUrl: '',
          peerGender: 'goddess',
          peerCity: '新乡',
          peerMainRole: 'BUYER',
          peerVideoVerified: false,
          peerSellerCharmScore: 0,
          peerBuyerPowerScore: 16,
          lastMessageSummary: '发货时间和瑕疵细节沟通',
          lastServerSeq: 3,
          deliveredSeq: 3,
          readSeq: 3,
          unreadCount: 0,
          updatedAt: '2026-06-09T07:20:00'
        }
      ],
      serverTime: new Date().toISOString()
    }))
  }))
}

async function installAuthenticatedChatFixture(page) {
  const json = (data) => ({ success: true, message: 'ok', data })
  const profile = {
    userId: 1,
    nickname: '小原圈测试用户',
    mainRole: 'BUYER',
    identityStatus: 'UNVERIFIED',
    videoIdentityStatus: 'UNVERIFIED',
    videoVerified: false,
    gender: 'goddess',
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
    ...profile,
    userId: 2,
    nickname: '移动端聊天对象',
    gender: 'god',
    city: '杭州',
    buyerPowerScore: 0,
    sellerCharmScore: 38
  }
  await page.route('**/api/user/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(profile))
  }))
  await page.route('**/api/user/2/profile', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json(peerProfile))
  }))
  await page.route('**/api/chat/conversations', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(json({ conversations: [], serverTime: new Date().toISOString() }))
  }))
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error))
  process.exit(1)
})
