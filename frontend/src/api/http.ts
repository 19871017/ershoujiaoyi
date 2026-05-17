import { useUserStore } from '../store/modules/user'
import type { AnnouncementTickerResponse } from './modules/announcement'
import type { ChatConversationListResponse, MessageSyncResponse } from './modules/chat'
import type { CommunityPostResponse } from './modules/community'
import type { GiftCatalogItemResponse, RecentGiftFeedItemResponse, ReceivedGiftItemResponse } from './modules/gift'
import type { NotificationItemResponse } from './modules/notification'
import type { ProductDetailResponse, ProductListItemResponse } from './modules/product'
import type { UserProfileResponse } from './modules/user'

export interface HttpOptions {
  url: string
  method?: UniApp.RequestOptions['method']
  data?: unknown
  header?: Record<string, string>
}

export interface UploadOptions {
  url: string
  filePath: string
  name?: string
  formData?: Record<string, string>
  header?: Record<string, string>
}

export interface ApiResult<T> {
  success: boolean
  message: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const ENABLE_MOCK_DATA = import.meta.env.VITE_ENABLE_MOCK_DATA === 'true'
const ENABLE_DEV_HEADERS = import.meta.env.VITE_ENABLE_DEV_HEADERS === 'true'
const ENABLE_LAN_API_FALLBACK = import.meta.env.VITE_ENABLE_LAN_API_FALLBACK === 'true'
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID ?? '1'

type MockConversation = {
  conversationId: number
  peerUserId: number
  peerName: string
  peerAvatar: string
  scenario: 'ORDER' | 'AFTER_SALES' | 'GIFT' | 'SYSTEM'
  productTitle?: string
  messages: Array<{
    serverSeq: number
    senderId: number
    receiverId: number
    msgType: 'TEXT' | 'IMAGE'
    contentJson: string
    createdAt: string
    deliveredToReceiver?: boolean
    readByReceiver?: boolean
  }>
}

function isLanHost(host: string) {
  return /^(10|172\.(1[6-9]|2\d|3[0-1])|192\.168)\.\d{1,3}\.\d{1,3}$/.test(host)
}

function resolveApiBaseUrl() {
  if (API_BASE_URL.trim()) return API_BASE_URL.trim().replace(/\/$/, '')
  if (!ENABLE_LAN_API_FALLBACK || typeof window === 'undefined' || !window.location?.hostname) return ''
  const host = window.location.hostname
  if (!host || host === 'localhost' || host === '127.0.0.1') return ''
  if (!isLanHost(host)) return ''
  return `http://${host}:18080`
}

const RESOLVED_API_BASE_URL = resolveApiBaseUrl()

const DEV_HEADERS: Record<string, string> = ENABLE_DEV_HEADERS
  ? { 'X-User-Id': DEV_USER_ID, 'X-Dev-Mode': 'enabled' }
  : {}
const MOCK_SELLER_IDS = [8, 12, 18] as const
const MOCK_ASSET_BASE = '/assets/mock'

function mockAsset(name: string) {
  return `${MOCK_ASSET_BASE}/${name}`
}

function isApiResult<T>(data: unknown): data is ApiResult<T> {
  return Boolean(data && typeof data === 'object' && 'success' in data && 'message' in data && 'data' in data)
}

function toError(message: unknown, fallback: string) {
  return new Error(typeof message === 'string' && message.trim() ? message : fallback)
}

function appendQuery(url: string, data?: unknown) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return url
  const params = new URLSearchParams()
  Object.entries(data as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    params.set(key, String(value))
  })
  const query = params.toString()
  if (!query) return url
  return `${url}${url.includes('?') ? '&' : '?'}${query}`
}

function mockNow(offsetMinutes = 0) {
  return new Date(Date.now() - offsetMinutes * 60_000).toISOString()
}

function mockProducts(): ProductListItemResponse[] {
  return [
    { productId: 9001, productNo: 'MOCK-DRESS-001', title: '奶油白法式连衣裙 只穿过一次', price: '129.00', coverImageUrl: mockAsset('product-dress.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-07T10:00:00' },
    { productId: 9002, productNo: 'MOCK-SHOES-002', title: '小香风玛丽珍鞋 37码', price: '88.00', coverImageUrl: mockAsset('product-shoes.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-07T09:30:00' },
    { productId: 9003, productNo: 'MOCK-SOCKS-003', title: '蝴蝶结长袜三双装 未拆封', price: '29.00', coverImageUrl: mockAsset('product-bikini.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T21:10:00' },
    { productId: 9004, productNo: 'MOCK-BAG-004', title: '粉色腋下包 轻微使用痕迹', price: '66.00', coverImageUrl: mockAsset('product-bag.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T18:30:00' },
    { productId: 9005, productNo: 'MOCK-CAMERA-005', title: '富士拍立得 mini 12 粉色', price: '299.00', coverImageUrl: mockAsset('product-camera.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T16:18:00' },
    { productId: 9006, productNo: 'MOCK-PERFUME-006', title: '祖玛珑蓝风铃分装 30ml', price: '79.00', coverImageUrl: mockAsset('product-perfume.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T13:02:00' }
  ]
}

function mockProductDetails(products: ProductListItemResponse[]): ProductDetailResponse[] {
  return products.map((item, index) => ({
    productId: item.productId,
    productNo: item.productNo,
    title: item.title,
    description: [
      '支持平台下单，成色与瑕疵已尽量拍清。',
      '默认顺丰或京东寄出，发货前会再次确认。',
      '交易、支付和售后均以平台订单记录为准。'
    ].join(' '),
    price: item.price,
    imageUrls: item.coverImageUrl ? [item.coverImageUrl] : [],
    status: item.status,
    auditState: item.auditState,
    visible: item.visible,
    tradeRule: '交易状态以平台订单、支付和售后记录为准',
    createdAt: item.createdAt,
    sellerId: MOCK_SELLER_IDS[index % MOCK_SELLER_IDS.length]
  }))
}

function mockProfiles(): Record<number, UserProfileResponse> {
  return {
    1: { userId: 1, userNo: 'XYQ10001', nickname: '雨哥体验号', avatarUrl: '', mainRole: 'BUYER', gender: 'god', city: '杭州', bio: '演示环境买家账号', videoIdentityStatus: 'UNVERIFIED', videoVerified: false },
    8: { userId: 8, userNo: 'XYQ20008', nickname: '桃桃', avatarUrl: mockAsset('avatar-taotao.svg'), mainRole: 'SELLER', gender: 'goddess', city: '上海', bio: '认证商家｜穿搭和轻奢闲置', videoIdentityStatus: 'APPROVED', videoVerified: true, followedByMe: true },
    12: { userId: 12, userNo: 'XYQ20012', nickname: '可心', avatarUrl: mockAsset('avatar-kexin.svg'), mainRole: 'SELLER', gender: 'goddess', city: '苏州', bio: '礼物互动很活跃的认证卖家', videoIdentityStatus: 'APPROVED', videoVerified: true, followedByMe: false },
    18: { userId: 18, userNo: 'XYQ20018', nickname: '晚晚', avatarUrl: mockAsset('avatar-wanwan.svg'), mainRole: 'SELLER', gender: 'goddess', city: '成都', bio: '美妆、包包、香水都在更', videoIdentityStatus: 'APPROVED', videoVerified: true, followedByMe: false },
    21: { userId: 21, userNo: 'XYQ30021', nickname: '阿澈', avatarUrl: '', mainRole: 'BUYER', gender: 'god', city: '南京', bio: '喜欢收相机和球鞋', videoIdentityStatus: 'UNVERIFIED', videoVerified: false }
  }
}

function mockGiftCatalog(): GiftCatalogItemResponse[] {
  return [
    { giftId: 1, giftCode: 'rose', name: '玫瑰', icon: '🌹', price: '19.90', platformRate: '0.10' },
    { giftId: 2, giftCode: 'starlight-box', name: '星光礼盒', icon: '🎁', price: '99.00', platformRate: '0.12' },
    { giftId: 3, giftCode: 'crown', name: '女神皇冠', icon: '👑', price: '199.00', platformRate: '0.15' },
    { giftId: 4, giftCode: 'spark-heart', name: '心动烟花', icon: '💖', price: '299.00', platformRate: '0.15' }
  ]
}

function mockReceivedGifts(): ReceivedGiftItemResponse[] {
  return [
    { giftOrderNo: 'GIFT-20260516008', senderId: 21, giftId: 1, giftCode: 'rose', giftName: '玫瑰', giftIcon: '🌹', quantity: 2, totalAmount: '39.80', platformShare: '3.98', receiverAmount: '35.82', receiverCreditLedgerNo: 'LEDGER-90001', status: 'SUCCESS', createdAt: '2026-05-16T21:20:00' },
    { giftOrderNo: 'GIFT-20260516012', senderId: 16, giftId: 2, giftCode: 'starlight-box', giftName: '星光礼盒', giftIcon: '🎁', quantity: 1, totalAmount: '99.00', platformShare: '11.88', receiverAmount: '87.12', receiverCreditLedgerNo: 'LEDGER-90002', status: 'SUCCESS', createdAt: '2026-05-16T20:32:00' },
    { giftOrderNo: 'GIFT-20260516021', senderId: 7, giftId: 3, giftCode: 'crown', giftName: '女神皇冠', giftIcon: '👑', quantity: 1, totalAmount: '199.00', platformShare: '29.85', receiverAmount: '169.15', receiverCreditLedgerNo: 'LEDGER-90003', status: 'SUCCESS', createdAt: '2026-05-16T19:08:00' }
  ]
}

function mockRecentGiftFeed(): RecentGiftFeedItemResponse[] {
  return [
    { giftOrderNo: 'GIFT-20260516001', senderId: 21, senderName: '阿澈', receiverId: 8, receiverName: '桃桃', giftId: 1, giftName: '玫瑰', giftIcon: '🌹', quantity: 1, totalAmount: '19.90', createdAt: '2026-05-16T21:20:00' },
    { giftOrderNo: 'GIFT-20260516002', senderId: 16, senderName: '阿宇', receiverId: 12, receiverName: '可心', giftId: 2, giftName: '星光礼盒', giftIcon: '🎁', quantity: 1, totalAmount: '99.00', createdAt: '2026-05-16T21:12:00' },
    { giftOrderNo: 'GIFT-20260516003', senderId: 7, senderName: '小北', receiverId: 18, receiverName: '晚晚', giftId: 3, giftName: '女神皇冠', giftIcon: '👑', quantity: 1, totalAmount: '199.00', createdAt: '2026-05-16T20:48:00' }
  ]
}

function mockNotifications(): NotificationItemResponse[] {
  return [
    { notificationNo: 'NOTICE-20260516001', userId: 1, type: 'SYSTEM', title: '今晚 22:00 女神榜更新', description: '榜单将按最新礼物数据刷新', targetUrl: '/pages/ranking/index?tab=goddess&period=week', read: false, createdAt: '2026-05-16T21:08:00', readAt: null },
    { notificationNo: 'NOTICE-20260516002', userId: 1, type: 'SYSTEM', title: '交易提醒', description: '请继续通过平台订单流程完成交易', targetUrl: '/pages/notification/index', read: false, createdAt: '2026-05-16T20:58:00', readAt: null },
    { notificationNo: 'NOTICE-20260516003', userId: 1, type: 'AUDIT', title: '卖家认证审核中', description: '视频认证资料已提交，结果会同步到通知中心', targetUrl: '/pages/user/identity/index', read: true, createdAt: '2026-05-16T18:20:00', readAt: '2026-05-16T18:26:00' }
  ]
}

function mockCommunityPosts(): CommunityPostResponse[] {
  return [
    { postNo: 'POST-1001', postId: 1001, authorId: 8, title: '今天整理出三件适合约会的小裙子', topic: '生活日常', content: '奶油白、藕粉和浅蓝都拍了细节图，晚上会上新一部分，先来社区放个预告。', imageUrls: [mockAsset('post-outfit.svg')], status: 'PUBLISHED', likeCount: 28, commentCount: 6, likedByMe: true, createdAt: '2026-05-16T20:30:00', authorName: '桃桃', authorAvatar: mockAsset('avatar-taotao.svg'), city: '上海', relatedProductId: 9001, relatedProductTitle: '奶油白法式连衣裙 只穿过一次', relatedProductPrice: '129.00' },
    { postNo: 'POST-1002', postId: 1002, authorId: 12, title: '求购一只成色好的粉色腋下包', topic: '求购心愿', content: '预算 100 左右，最好容量能放下手机和口红，姐妹们有出的吗？', imageUrls: [mockAsset('post-wishlist.svg')], status: 'PUBLISHED', likeCount: 14, commentCount: 9, likedByMe: false, createdAt: '2026-05-16T19:42:00', authorName: '可心', authorAvatar: mockAsset('avatar-kexin.svg'), city: '苏州', relatedProductId: 9004, relatedProductTitle: '粉色腋下包 轻微使用痕迹', relatedProductPrice: '66.00' },
    { postNo: 'POST-1003', postId: 1003, authorId: 18, title: '收到礼物啦，感谢今天来逛我主页的姐妹', topic: '生活日常', content: '刚刚有姐妹送了皇冠和玫瑰，太开心了，今晚继续更新香水和拍立得。', imageUrls: [mockAsset('post-gift.svg')], status: 'PUBLISHED', likeCount: 36, commentCount: 12, likedByMe: false, createdAt: '2026-05-16T18:58:00', authorName: '晚晚', authorAvatar: mockAsset('avatar-wanwan.svg'), city: '成都', relatedProductId: 9006, relatedProductTitle: '祖玛珑蓝风铃分装 30ml', relatedProductPrice: '79.00' }
  ]
}

function mockConversations(): MockConversation[] {
  return [
    {
      conversationId: 5001,
      peerUserId: 8,
      peerName: '桃桃',
      peerAvatar: mockAsset('avatar-taotao.svg'),
      scenario: 'ORDER',
      productTitle: '奶油白法式连衣裙 只穿过一次',
      messages: [
        { serverSeq: 1, senderId: 8, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '裙子还在哦，细节图可以再补拍给你。' }), createdAt: mockNow(95), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 2, senderId: 1, receiverId: 8, msgType: 'TEXT', contentJson: JSON.stringify({ text: '有轻微瑕疵吗？大概什么时候能发货？' }), createdAt: mockNow(90), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 3, senderId: 8, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '只有腰侧一点点不明显的勾丝，今晚下单明早就能寄。' }), createdAt: mockNow(84), deliveredToReceiver: true, readByReceiver: false }
      ]
    },
    {
      conversationId: 5002,
      peerUserId: 12,
      peerName: '可心',
      peerAvatar: mockAsset('avatar-kexin.svg'),
      scenario: 'AFTER_SALES',
      productTitle: '粉色腋下包 轻微使用痕迹',
      messages: [
        { serverSeq: 1, senderId: 12, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '包包已经发出啦，单号我同步到订单里了。' }), createdAt: mockNow(210), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 2, senderId: 1, receiverId: 12, msgType: 'TEXT', contentJson: JSON.stringify({ text: '收到后如果肩带有问题我会走售后。' }), createdAt: mockNow(205), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 3, senderId: 12, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '好的，有问题直接在平台发起售后就行。' }), createdAt: mockNow(198), deliveredToReceiver: true, readByReceiver: false }
      ]
    },
    {
      conversationId: 5003,
      peerUserId: 18,
      peerName: '晚晚',
      peerAvatar: mockAsset('avatar-wanwan.svg'),
      scenario: 'GIFT',
      productTitle: '祖玛珑蓝风铃分装 30ml',
      messages: [
        { serverSeq: 1, senderId: 1, receiverId: 18, msgType: 'TEXT', contentJson: JSON.stringify({ text: '刚给你送了个心动烟花，香水还会继续上新吗？' }), createdAt: mockNow(40), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 2, senderId: 18, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '看到了，谢谢你～今晚还会上两瓶分装和一台拍立得。' }), createdAt: mockNow(34), deliveredToReceiver: true, readByReceiver: false }
      ]
    }
  ]
}

function buildConversationList(conversations: MockConversation[]): ChatConversationListResponse {
  return {
    conversations: conversations.map((item) => {
      const last = item.messages[item.messages.length - 1]
      const unreadCount = item.messages.filter((message) => message.receiverId === 1 && !message.readByReceiver).length
      return {
        conversationId: item.conversationId,
        peerUserId: item.peerUserId,
        lastMessageSummary: last ? renderContentText(last.contentJson, item.scenario, item.productTitle) : '',
        lastServerSeq: last?.serverSeq || 0,
        deliveredSeq: last?.serverSeq || 0,
        readSeq: unreadCount > 0 ? Math.max(0, (last?.serverSeq || 1) - 1) : last?.serverSeq || 0,
        unreadCount,
        updatedAt: last?.createdAt || mockNow(240)
      }
    })
  }
}

function buildConversationMessages(conversations: MockConversation[], conversationId: number): MessageSyncResponse | undefined {
  const found = conversations.find((item) => item.conversationId === conversationId)
  if (!found) return undefined
  return {
    messages: found.messages.map((message) => ({
      conversationId,
      serverSeq: message.serverSeq,
      serverMsgId: `msg-${conversationId}-${message.serverSeq}`,
      clientMsgId: `client-${conversationId}-${message.serverSeq}`,
      senderId: message.senderId,
      receiverId: message.receiverId,
      msgType: message.msgType,
      contentJson: message.contentJson,
      createdAt: message.createdAt,
      deliveredToReceiver: message.deliveredToReceiver,
      readByReceiver: message.readByReceiver
    })),
    nextAfterSeq: found.messages[found.messages.length - 1]?.serverSeq || 0,
    hasMore: false
  }
}

function renderContentText(contentJson: string, scenario: MockConversation['scenario'], productTitle?: string) {
  try {
    const parsed = JSON.parse(contentJson) as { text?: string; url?: string }
    if (parsed.text) return productTitle ? `${productTitle} · ${parsed.text}` : parsed.text
    if (parsed.url) return `${scenario === 'GIFT' ? '礼物互动' : '图片消息'} · [图片]`
  } catch {
    return productTitle ? `${productTitle} · 新消息` : '新消息'
  }
  return productTitle ? `${productTitle} · 新消息` : '新消息'
}

function mockResponse<T>(url: string, method: string, data?: unknown): T | undefined {
  if (!ENABLE_MOCK_DATA) return undefined

  if (url === '/api/products') return mockProducts() as T
  if (url === '/api/announcements/ticker') {
    return ({ enabled: false, text: '欢迎来到小原圈，请通过平台订单流程完成交易', icon: '📣', targetUrl: '/pages/notification/index', updatedAt: new Date().toISOString() } satisfies AnnouncementTickerResponse) as T
  }
  if (url === '/api/gifts/catalog') return mockGiftCatalog() as T
  if (url === '/api/gifts/received') return mockReceivedGifts() as T
  if (url === '/api/gifts/recent') return mockRecentGiftFeed() as T
  if (url.startsWith('/api/notifications')) return mockNotifications() as T
  if (url === '/api/community/posts') return mockCommunityPosts() as T
  if (url === '/api/chat/conversations') return buildConversationList(mockConversations()) as T

  const profiles = mockProfiles()

  if (url === '/api/user/me') return (profiles[1] || profiles[8]) as T
  if (url === '/api/user/me/profile' && method === 'POST') {
    const base = profiles[1] || profiles[8]
    const patch = (data || {}) as Partial<UserProfileResponse>
    return { ...base, ...patch, userId: base.userId, userNo: base.userNo || 'XYQ10001' } as T
  }

  const userProfileMatch = url.match(/^\/api\/user\/(\d+)\/profile$/)
  if (userProfileMatch) {
    const userId = Number(userProfileMatch[1])
    return (profiles[userId] || profiles[8]) as T
  }

  const followMatch = url.match(/^\/api\/user\/(\d+)\/follow$/)
  if (followMatch) {
    const userId = Number(followMatch[1])
    const profile = profiles[userId] || profiles[8]
    return { ...profile, followedByMe: method !== 'DELETE' } as T
  }

  const sellerMatch = url.match(/^\/api\/products\/seller\/(\d+)$/)
  if (sellerMatch) {
    const productDetails = mockProductDetails(mockProducts())
    const sellerId = Number(sellerMatch[1])
    return productDetails.filter((item) => item.sellerId === sellerId).map((item) => ({
      productId: item.productId,
      productNo: item.productNo,
      title: item.title,
      price: item.price,
      coverImageUrl: item.imageUrls[0] || '',
      status: item.status,
      auditState: item.auditState,
      visible: item.visible,
      createdAt: item.createdAt
    })) as T
  }

  const productMatch = url.match(/^\/api\/products\/(\d+)/)
  if (productMatch) {
    const productDetails = mockProductDetails(mockProducts())
    const productId = Number(productMatch[1])
    const product = productDetails.find((item) => item.productId === productId) || productDetails[0]
    return product as T
  }

  const conversationMessagesMatch = url.match(/^\/api\/chat\/conversations\/(\d+)\/messages(?:\?.*)?$/)
  if (conversationMessagesMatch) {
    const conversationId = Number(conversationMessagesMatch[1])
    return buildConversationMessages(mockConversations(), conversationId) as T
  }

  const readMatch = url.match(/^\/api\/chat\/conversations\/(\d+)\/(read|delivered)$/)
  if (readMatch) {
    const conversations = mockConversations()
    const conversationId = Number(readMatch[1])
    const found = conversations.find((item) => item.conversationId === conversationId)
    const lastSeq = found?.messages[found.messages.length - 1]?.serverSeq || 0
    const unreadCount = readMatch[2] === 'read' ? 0 : Math.max(0, found?.messages.filter((message) => message.receiverId === 1 && !message.readByReceiver).length || 0)
    const payload = {
      conversationId,
      deliveredSeq: lastSeq,
      readSeq: readMatch[2] === 'read' ? lastSeq : Math.max(0, lastSeq - unreadCount),
      lastServerSeq: lastSeq,
      unreadCount
    }
    return payload as T
  }

  if (url === '/api/chat/messages' && method === 'POST') {
    const payload = (data || {}) as { receiverId?: number; msgType?: 'TEXT' | 'IMAGE' }
    return {
      ack: {
        messageId: `mock-${Date.now()}`,
        conversationId: 5009,
        serverSeq: 99,
        serverMsgId: `server-${Date.now()}`,
        clientMsgId: `client-${Date.now()}`,
        sendState: 'sent',
        serverTs: new Date().toISOString(),
        senderId: 1,
        receiverId: payload.receiverId || 8,
        msgType: payload.msgType || 'TEXT'
      }
    } as T
  }

  if (url === '/api/gifts/send' && method === 'POST') {
    const payload = (data || {}) as { receiverId?: number; giftCode?: string; quantity?: number }
    const giftCatalog = mockGiftCatalog()
    const gift = giftCatalog.find((item) => item.giftCode === payload.giftCode) || giftCatalog[0]
    const quantity = Math.max(1, Number(payload.quantity || 1))
    const total = (Number(gift.price) * quantity).toFixed(2)
    const platformShare = (Number(total) * Number(gift.platformRate)).toFixed(2)
    const receiverAmount = (Number(total) - Number(platformShare)).toFixed(2)
    return {
      giftOrderNo: `GIFT-MOCK-${Date.now()}`,
      giftId: gift.giftId,
      giftCode: gift.giftCode,
      receiverId: payload.receiverId || 8,
      totalAmount: total,
      platformShare,
      receiverAmount,
      debitLedgerNo: `DEBIT-${Date.now()}`,
      receiverCreditLedgerNo: `RC-${Date.now()}`,
      status: 'SUCCESS',
      createdAt: new Date().toISOString()
    } as T
  }

  if (url === '/api/home/banners') {
    return [
      { id: 1, kicker: '小原圈 · 今日新鲜', title: '把心爱闲置交给懂它的人', description: '附近好物、日常分享、圈内互动，一屏逛完。', cta: '去发现', imageUrl: '/uploads/home/banner-closet.svg', action: 'closet', sortOrder: 10, enabled: true, sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。', updatedAt: new Date().toISOString() },
      { id: 2, kicker: '榜单热度更新', title: '魅力女神榜 / 霸总男神榜', description: '女神看收礼，男神看消费，支持日榜周榜总榜。', cta: '去看看', imageUrl: '/uploads/home/banner-ranking.svg', action: 'ranking', sortOrder: 20, enabled: true, sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。', updatedAt: new Date().toISOString() },
      { id: 3, kicker: '日常生活频道', title: '分享今天的小确幸', description: '校园、寝室、城市日常，都可以轻松聊。', cta: '去社区', imageUrl: '/uploads/home/banner-community.svg', action: 'forum', sortOrder: 30, enabled: true, sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。', updatedAt: new Date().toISOString() }
    ] as T
  }

  if (url === '/api/location/config') {
    return {
      provider: 'BAIDU',
      enabled: true,
      configured: false,
      defaultCity: '请选择城市',
      defaultProvince: '',
      coordinateType: 'wgs84ll',
      updatedAt: new Date().toISOString()
    } as T
  }

  return undefined
}

export function isDevRuntimeEnabled() {
  return ENABLE_DEV_HEADERS
}

function authHeaders(): Record<string, string> {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function request<T = unknown>(options: HttpOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const requestUrl = method === 'GET' ? appendQuery(options.url, options.data) : options.url
  const mocked = mockResponse<T>(requestUrl, method, options.data)
  if (mocked !== undefined) {
    return Promise.resolve(mocked)
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: `${RESOLVED_API_BASE_URL}${requestUrl}`,
      method,
      data: method === 'GET' ? {} : options.data ?? {},
      header: { ...DEV_HEADERS, ...authHeaders(), ...(options.header ?? {}) },
      success: (res: UniApp.RequestSuccessCallbackResult) => {
        const result = res.data

        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(toError(isApiResult<T>(result) ? result.message : undefined, `HTTP ${res.statusCode}`))
          return
        }

        if (!isApiResult<T>(result)) {
          reject(toError(undefined, 'API response invalid'))
          return
        }

        if (!result.success) {
          reject(toError(result.message, 'API request failed'))
          return
        }

        resolve(result.data)
      },
      fail: reject
    })
  })
}

export function get<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'GET', data, header })
}

export function post<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'POST', data, header })
}

type UploadFileResult = { statusCode: number; data: unknown }
type UploadFileClient = {
  uploadFile(options: UploadOptions & {
    success: (res: UploadFileResult) => void
    fail: (error: unknown) => void
  }): void
}

export function upload<T = unknown>(options: UploadOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    ;(uni as unknown as UploadFileClient).uploadFile({
      url: `${RESOLVED_API_BASE_URL}${options.url}`,
      filePath: options.filePath,
      name: options.name ?? 'file',
      formData: options.formData ?? {},
      header: { ...DEV_HEADERS, ...authHeaders(), ...(options.header ?? {}) },
      success: (res) => {
        let result: unknown
        try {
          result = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        } catch {
          result = undefined
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(toError(isApiResult<T>(result) ? result.message : undefined, `上传失败：HTTP ${res.statusCode}`))
          return
        }
        if (!isApiResult<T>(result)) {
          reject(toError(undefined, '上传响应格式异常，请稍后重试'))
          return
        }
        if (!result.success) {
          reject(toError(result.message, '上传失败，请重新选择视频后再试'))
          return
        }
        resolve(result.data)
      },
      fail: reject
    })
  })
}

export function put<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'PUT', data, header })
}

export function del<T = unknown>(url: string, data?: unknown, header?: Record<string, string>) {
  return request<T>({ url, method: 'DELETE', data, header })
}
