import type { AnnouncementTickerResponse } from './modules/announcement'
import type { ChatConversationItem, ChatConversationListResponse, MessageSyncResponse } from './modules/chat'
import type { CommunityPostPageResponse, CommunityPostResponse } from './modules/community'
import type { GiftCatalogItemResponse, RecentGiftFeedItemResponse, ReceivedGiftItemResponse } from './modules/gift'
import type { MediaUploadTicketResponse } from './modules/media'
import type { NotificationItemResponse } from './modules/notification'
import type { ProductDetailResponse, ProductListItemResponse } from './modules/product'
import type { UserProfileResponse } from './modules/user'
import giftCoffeeAsset from '../assets/gifts/gift-coffee.png'
import giftCrownAsset from '../assets/gifts/gift-crown.png'
import giftCrystalShoeAsset from '../assets/gifts/gift-crystal-shoe.png'
import giftGalaxyAsset from '../assets/gifts/gift-galaxy.png'
import giftHeartAsset from '../assets/gifts/gift-heart.png'
import giftLoveCastleAsset from '../assets/gifts/gift-love-castle.png'
import giftPerfumeAsset from '../assets/gifts/gift-perfume.png'
import giftRibbonBoxAsset from '../assets/gifts/gift-ribbon-box.png'
import giftRoseAsset from '../assets/gifts/gift-rose.png'
import giftStarAsset from '../assets/gifts/gift-star.png'

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
    msgType: 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO'
    contentJson: string
    createdAt: string
    deliveredToReceiver?: boolean
    readByReceiver?: boolean
    revoked?: boolean
  }>
}

const MOCK_SELLER_IDS = [8, 12, 18] as const
const MOCK_ASSET_BASE = '/assets/mock'
const giftAssets = {
  coffee: giftCoffeeAsset,
  crown: giftCrownAsset,
  crystalShoe: giftCrystalShoeAsset,
  galaxy: giftGalaxyAsset,
  heart: giftHeartAsset,
  loveCastle: giftLoveCastleAsset,
  perfume: giftPerfumeAsset,
  ribbonBox: giftRibbonBoxAsset,
  rose: giftRoseAsset,
  star: giftStarAsset
} as const

function mockAsset(name: string) {
  return `${MOCK_ASSET_BASE}/${name}`
}

function mockUpload(scene: string, name: string) {
  return `/uploads/${scene.toLowerCase().replace(/_/g, '-')}/1/${name}`
}

function mockNow(offsetMinutes = 0) {
  return new Date(Date.now() - offsetMinutes * 60_000).toISOString()
}

function mockProducts(): ProductListItemResponse[] {
  return [
    { productId: 9001, productNo: 'XYQ-DRESS-001', title: '奶油白法式连衣裙 只穿过一次', price: '129.00', coverImageUrl: mockAsset('product-dress.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-07T10:00:00' },
    { productId: 9002, productNo: 'XYQ-SHOES-002', title: '小香风玛丽珍鞋 37码', price: '88.00', coverImageUrl: mockAsset('product-shoes.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-07T09:30:00' },
    { productId: 9003, productNo: 'XYQ-SOCKS-003', title: '蝴蝶结长袜三双装 未拆封', price: '29.00', coverImageUrl: mockAsset('product-bikini.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T21:10:00' },
    { productId: 9004, productNo: 'XYQ-BAG-004', title: '粉色腋下包 轻微使用痕迹', price: '66.00', coverImageUrl: mockAsset('product-bag.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T18:30:00' },
    { productId: 9005, productNo: 'XYQ-CAMERA-005', title: '富士拍立得 mini 12 粉色', price: '299.00', coverImageUrl: mockAsset('product-camera.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T16:18:00' },
    { productId: 9006, productNo: 'XYQ-PERFUME-006', title: '祖玛珑蓝风铃分装 30ml', price: '79.00', coverImageUrl: mockAsset('product-perfume.svg'), status: 'ACTIVE', auditState: 'APPROVED', visible: true, createdAt: '2026-05-06T13:02:00' }
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
    1: { userId: 1, userNo: 'XYQ10001', nickname: '雨哥体验号', avatarUrl: '', mainRole: 'BUYER', gender: 'god', city: '杭州', bio: '喜欢淘小众好物，也会认真确认成色。', identityStatus: 'UNVERIFIED', videoIdentityStatus: 'UNVERIFIED', videoVerified: false },
    8: { userId: 8, userNo: 'XYQ20008', nickname: '桃桃', avatarUrl: mockAsset('avatar-taotao.svg'), mainRole: 'SELLER', gender: 'goddess', city: '上海', bio: '认证商家｜穿搭和轻奢闲置', identityStatus: 'VERIFIED', videoIdentityStatus: 'APPROVED', videoVerified: true, followedByMe: true },
    12: { userId: 12, userNo: 'XYQ20012', nickname: '可心', avatarUrl: mockAsset('avatar-kexin.svg'), mainRole: 'SELLER', gender: 'goddess', city: '苏州', bio: '礼物互动很活跃的认证卖家', identityStatus: 'VERIFIED', videoIdentityStatus: 'APPROVED', videoVerified: true, followedByMe: false },
    18: { userId: 18, userNo: 'XYQ20018', nickname: '晚晚', avatarUrl: mockAsset('avatar-wanwan.svg'), mainRole: 'SELLER', gender: 'goddess', city: '成都', bio: '美妆、包包、香水都在更', identityStatus: 'VERIFIED', videoIdentityStatus: 'APPROVED', videoVerified: true, followedByMe: false },
    21: { userId: 21, userNo: 'XYQ30021', nickname: '阿澈', avatarUrl: '', mainRole: 'BUYER', gender: 'god', city: '南京', bio: '喜欢收相机和球鞋', identityStatus: 'UNVERIFIED', videoIdentityStatus: 'UNVERIFIED', videoVerified: false }
  }
}

function mockGiftCatalog(): GiftCatalogItemResponse[] {
  return [
    { giftId: 1, giftCode: 'ROSE', name: '玫瑰花', icon: giftAssets.rose, price: '1.00', platformRate: '0.20' },
    { giftId: 2, giftCode: 'COFFEE', name: '暖心咖啡', icon: giftAssets.coffee, price: '6.00', platformRate: '0.20' },
    { giftId: 3, giftCode: 'STAR', name: '星光应援', icon: giftAssets.star, price: '18.00', platformRate: '0.25' },
    { giftId: 4, giftCode: 'HEART', name: '心动告白', icon: giftAssets.heart, price: '32.00', platformRate: '0.25' },
    { giftId: 5, giftCode: 'RIBBON_BOX', name: '丝带礼盒', icon: giftAssets.ribbonBox, price: '52.00', platformRate: '0.25' },
    { giftId: 6, giftCode: 'PERFUME', name: '香氛礼赞', icon: giftAssets.perfume, price: '88.00', platformRate: '0.28' },
    { giftId: 7, giftCode: 'CRYSTAL_SHOE', name: '水晶鞋', icon: giftAssets.crystalShoe, price: '131.00', platformRate: '0.28' },
    { giftId: 8, giftCode: 'CROWN', name: '小原皇冠', icon: giftAssets.crown, price: '188.00', platformRate: '0.30' },
    { giftId: 9, giftCode: 'GALAXY', name: '银河之约', icon: giftAssets.galaxy, price: '299.00', platformRate: '0.30' },
    { giftId: 10, giftCode: 'LOVE_CASTLE', name: '心愿城堡', icon: giftAssets.loveCastle, price: '520.00', platformRate: '0.30' }
  ]
}

function mockReceivedGifts(): ReceivedGiftItemResponse[] {
  return [
    { giftOrderNo: 'GIFT-20260516008', senderId: 21, giftId: 1, giftCode: 'ROSE', giftName: '玫瑰花', giftIcon: giftAssets.rose, quantity: 2, totalAmount: '2.00', platformShare: '0.40', receiverAmount: '1.60', receiverCreditLedgerNo: 'LEDGER-90001', status: 'SUCCESS', createdAt: '2026-05-16T21:20:00' },
    { giftOrderNo: 'GIFT-20260516012', senderId: 16, giftId: 6, giftCode: 'PERFUME', giftName: '香氛礼赞', giftIcon: giftAssets.perfume, quantity: 1, totalAmount: '88.00', platformShare: '24.64', receiverAmount: '63.36', receiverCreditLedgerNo: 'LEDGER-90002', status: 'SUCCESS', createdAt: '2026-05-16T20:32:00' },
    { giftOrderNo: 'GIFT-20260516021', senderId: 7, giftId: 10, giftCode: 'LOVE_CASTLE', giftName: '心愿城堡', giftIcon: giftAssets.loveCastle, quantity: 1, totalAmount: '520.00', platformShare: '156.00', receiverAmount: '364.00', receiverCreditLedgerNo: 'LEDGER-90003', status: 'SUCCESS', createdAt: '2026-05-16T19:08:00' }
  ]
}

export function mockRecentGiftFeed(): RecentGiftFeedItemResponse[] {
  return [
    { giftOrderNo: 'GIFT-20260516001', senderId: 21, senderName: '阿澈', receiverId: 8, receiverName: '桃桃', giftId: 1, giftName: '玫瑰花', giftIcon: giftAssets.rose, quantity: 1, totalAmount: '1.00', createdAt: '2026-05-16T21:20:00' },
    { giftOrderNo: 'GIFT-20260516002', senderId: 16, senderName: '阿宇', receiverId: 12, receiverName: '可心', giftId: 8, giftName: '小原皇冠', giftIcon: giftAssets.crown, quantity: 1, totalAmount: '188.00', createdAt: '2026-05-16T21:12:00' },
    { giftOrderNo: 'GIFT-20260516003', senderId: 7, senderName: '小北', receiverId: 18, receiverName: '晚晚', giftId: 10, giftName: '心愿城堡', giftIcon: giftAssets.loveCastle, quantity: 1, totalAmount: '520.00', createdAt: '2026-05-16T20:48:00' }
  ]
}

function mockNotifications(): NotificationItemResponse[] {
  return [
    { notificationNo: 'NOTICE-20260516001', userId: 1, type: 'SYSTEM', title: '今晚 22:00 女神榜更新', description: '榜单将按最新礼物数据刷新', targetUrl: '/pages/tabbar/home/index', read: false, createdAt: '2026-05-16T21:08:00', readAt: null },
    { notificationNo: 'NOTICE-20260516002', userId: 1, type: 'SYSTEM', title: '交易提醒', description: '请继续通过平台订单流程完成交易', targetUrl: '/pages/notification/index', read: false, createdAt: '2026-05-16T20:58:00', readAt: null },
    { notificationNo: 'NOTICE-20260516003', userId: 1, type: 'AUDIT', title: '卖家认证审核中', description: '视频认证资料已提交，通知中心会提醒你', targetUrl: '/pages/user/identity/index', read: true, createdAt: '2026-05-16T18:20:00', readAt: '2026-05-16T18:26:00' }
  ]
}

function mockCommunityPosts(): CommunityPostResponse[] {
  return [
    { postNo: 'POST-1001', postId: 1001, authorId: 8, title: '今天整理出三件适合约会的小裙子', topic: '生活日常', content: '奶油白、藕粉和浅蓝都拍了细节图，晚上会上新一部分，先来社区放个预告。', imageUrls: [mockAsset('post-outfit.svg')], status: 'PUBLISHED', likeCount: 28, commentCount: 6, likedByMe: true, followedByMe: true, createdAt: '2026-05-16T20:30:00', authorName: '桃桃', authorAvatar: mockAsset('avatar-taotao.svg'), city: '上海', relatedProductId: 9001, relatedProductTitle: '奶油白法式连衣裙 只穿过一次', relatedProductPrice: '129.00' },
    { postNo: 'POST-1002', postId: 1002, authorId: 12, title: '求购一只成色好的粉色腋下包', topic: '求购心愿', content: '预算 100 左右，最好容量能放下手机和口红，姐妹们有出的吗？', imageUrls: [mockAsset('post-wishlist.svg')], status: 'PUBLISHED', likeCount: 14, commentCount: 9, likedByMe: false, followedByMe: false, createdAt: '2026-05-16T19:42:00', authorName: '可心', authorAvatar: mockAsset('avatar-kexin.svg'), city: '苏州', relatedProductId: 9004, relatedProductTitle: '粉色腋下包 轻微使用痕迹', relatedProductPrice: '66.00' },
    { postNo: 'POST-1003', postId: 1003, authorId: 18, title: '收到礼物啦，感谢今天来逛我主页的姐妹', topic: '生活日常', content: '刚刚有姐妹送了皇冠和玫瑰，太开心了，今晚继续更新香水和拍立得。', imageUrls: [mockAsset('post-gift.svg')], status: 'PUBLISHED', likeCount: 36, commentCount: 12, likedByMe: false, followedByMe: false, createdAt: '2026-05-16T18:58:00', authorName: '晚晚', authorAvatar: mockAsset('avatar-wanwan.svg'), city: '成都', relatedProductId: 9006, relatedProductTitle: '祖玛珑蓝风铃分装 30ml', relatedProductPrice: '79.00' }
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
        { serverSeq: 3, senderId: 8, receiverId: 1, msgType: 'VOICE', contentJson: JSON.stringify({ url: mockUpload('CHAT_VOICE', 'local-preview-voice.webm'), duration: 8 }), createdAt: mockNow(86), deliveredToReceiver: true, readByReceiver: false },
        { serverSeq: 4, senderId: 8, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '只有腰侧一点点不明显的勾丝，今晚下单明早就能寄。' }), createdAt: mockNow(84), deliveredToReceiver: true, readByReceiver: false }
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
        { serverSeq: 1, senderId: 12, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '包包已经发出啦，单号我放到订单里了。' }), createdAt: mockNow(210), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 2, senderId: 1, receiverId: 12, msgType: 'TEXT', contentJson: JSON.stringify({ text: '收到后如果肩带有问题我会走售后。' }), createdAt: mockNow(205), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 3, senderId: 12, receiverId: 1, msgType: 'IMAGE', contentJson: JSON.stringify({ url: mockUpload('CHAT_IMAGE', 'local-preview-image.png') }), createdAt: mockNow(202), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 4, senderId: 12, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '好的，有问题直接在平台发起售后就行。' }), createdAt: mockNow(198), deliveredToReceiver: true, readByReceiver: false }
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
        { serverSeq: 2, senderId: 1, receiverId: 18, msgType: 'VOICE', contentJson: JSON.stringify({ url: mockUpload('CHAT_VOICE', 'local-sent-voice.webm'), duration: 5 }), createdAt: mockNow(37), deliveredToReceiver: true, readByReceiver: true },
        { serverSeq: 3, senderId: 18, receiverId: 1, msgType: 'TEXT', contentJson: JSON.stringify({ text: '看到了，谢谢你～今晚还会上两瓶分装和一台拍立得。' }), createdAt: mockNow(34), deliveredToReceiver: true, readByReceiver: false }
      ]
    }
  ]
}

function buildConversationItem(item: MockConversation): ChatConversationItem {
  const last = item.messages[item.messages.length - 1]
  const unreadCount = item.messages.filter((message) => message.receiverId === 1 && !message.readByReceiver).length
  const profile = mockProfiles()[item.peerUserId]
  return {
    conversationId: item.conversationId,
    peerUserId: item.peerUserId,
    peerNickname: profile?.nickname || item.peerName,
    peerAvatarUrl: profile?.avatarUrl || item.peerAvatar,
    peerGender: profile?.gender || null,
    peerCity: profile?.city || null,
    peerMainRole: profile?.mainRole || null,
    peerVideoVerified: profile?.videoIdentityStatus === 'APPROVED' && profile?.videoVerified === true,
    peerSellerCharmScore: profile?.mainRole === 'SELLER' ? 1880 : 0,
    peerBuyerPowerScore: profile?.mainRole !== 'SELLER' ? 660 : 0,
    lastMessageSummary: last ? renderContentText(last.contentJson, item.scenario, item.productTitle) : '',
    lastServerSeq: last?.serverSeq || 0,
    deliveredSeq: last?.serverSeq || 0,
    readSeq: unreadCount > 0 ? Math.max(0, (last?.serverSeq || 1) - unreadCount) : last?.serverSeq || 0,
    unreadCount,
    updatedAt: last?.createdAt || mockNow(240)
  }
}

function buildConversationList(conversations: MockConversation[]): ChatConversationListResponse {
  return {
    conversations: conversations.map(buildConversationItem)
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
      readByReceiver: message.readByReceiver,
      revoked: message.revoked
    })),
    nextAfterSeq: found.messages[found.messages.length - 1]?.serverSeq || 0,
    hasMore: false
  }
}

function renderContentText(contentJson: string, scenario: MockConversation['scenario'], productTitle?: string) {
  try {
    const parsed = JSON.parse(contentJson) as { text?: string; url?: string; duration?: number }
    if (parsed.text) return productTitle ? `${productTitle} · ${parsed.text}` : parsed.text
    if (parsed.duration || parsed.url?.includes('/chat-voice/')) return `${scenario === 'GIFT' ? '礼物互动' : '语音消息'} · [语音]`
    if (parsed.url) return `${scenario === 'GIFT' ? '礼物互动' : '图片消息'} · [图片]`
  } catch {
    return productTitle ? `${productTitle} · 新消息` : '新消息'
  }
  return productTitle ? `${productTitle} · 新消息` : '新消息'
}

export function mockResponse<T>(url: string, method: string, data?: unknown): T | undefined {
  if (url === '/api/products') return mockProducts() as T
  if (url === '/api/announcements/ticker') {
    return ({ enabled: true, text: '欢迎来到小原圈，请通过平台订单流程完成交易', icon: '📣', targetUrl: '/pages/notification/index', updatedAt: new Date().toISOString() } satisfies AnnouncementTickerResponse) as T
  }
  if (url === '/api/gifts/catalog') return mockGiftCatalog() as T
  if (url === '/api/gifts/received') return mockReceivedGifts() as T
  if (url === '/api/gifts/recent') return mockRecentGiftFeed() as T
  if (url.startsWith('/api/notifications')) return mockNotifications() as T
  if (url.startsWith('/api/community/posts/page')) {
    const query = new URLSearchParams(url.split('?')[1] || '')
    const topic = query.get('topic')?.trim()
    const limit = Math.max(1, Math.min(Number(query.get('limit') || 20), 50))
    const offset = Math.max(0, Number(query.get('cursor') || 0))
    const posts = topic ? mockCommunityPosts().filter((item) => item.topic === topic) : mockCommunityPosts()
    const pagePosts = posts.slice(offset, offset + limit)
    const nextOffset = offset + pagePosts.length
    return ({
      posts: pagePosts,
      nextCursor: nextOffset < posts.length ? String(nextOffset) : null,
      hasMore: nextOffset < posts.length
    } satisfies CommunityPostPageResponse) as T
  }
  if (url.startsWith('/api/community/posts')) {
    const query = new URLSearchParams(url.split('?')[1] || '')
    const topic = query.get('topic')?.trim()
    const posts = topic ? mockCommunityPosts().filter((item) => item.topic === topic) : mockCommunityPosts()
    return posts as T
  }
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

  const conversationDetailMatch = url.match(/^\/api\/chat\/conversations\/(\d+)$/)
  if (conversationDetailMatch && method === 'GET') {
    const conversationId = Number(conversationDetailMatch[1])
    const found = mockConversations().find((item) => item.conversationId === conversationId)
    return (found ? buildConversationItem(found) : undefined) as T
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

  const revokeMessageMatch = url.match(/^\/api\/chat\/messages\/([^/]+)\/revoke$/)
  if (revokeMessageMatch && method === 'POST') {
    const serverMsgId = decodeURIComponent(revokeMessageMatch[1])
    const matched = serverMsgId.match(/^msg-(\d+)-(\d+)$/)
    if (!matched) return undefined
    return {
      conversationId: Number(matched[1]),
      serverSeq: Number(matched[2]),
      serverMsgId,
      revoked: true
    } as T
  }

  const clearConversationMatch = url.match(/^\/api\/chat\/conversations\/(\d+)\/clear$/)
  if (clearConversationMatch && method === 'POST') {
    const conversationId = Number(clearConversationMatch[1])
    const found = mockConversations().find((item) => item.conversationId === conversationId)
    const lastServerSeq = found?.messages[found.messages.length - 1]?.serverSeq || 0
    return {
      conversationId,
      clearedSeq: lastServerSeq,
      lastServerSeq
    } as T
  }

  const chatMediaMatch = url.match(/^\/api\/chat\/media(?:\?.*)?$/)
  if (chatMediaMatch && method === 'GET') {
    return new Blob(['mock chat media'], { type: 'application/octet-stream' }) as T
  }

  if (url === '/api/media/upload-tickets' && method === 'POST') {
    const payload = (data || {}) as { scene?: string; contentType?: string; fileSize?: number; filename?: string }
    const scene = payload.scene || 'CHAT_IMAGE'
    const extension = scene === 'CHAT_VIDEO' ? 'mp4' : scene === 'CHAT_VOICE' ? 'webm' : 'png'
    const storageUrl = mockUpload(scene, `local-${Date.now()}.${extension}`)
    return {
      ticketNo: `UT-${scene}-${Date.now()}`,
      ownerUserId: 1,
      scene,
      contentType: payload.contentType || (scene === 'CHAT_VIDEO' ? 'video/mp4' : scene === 'CHAT_VOICE' ? 'audio/webm' : 'image/png'),
      fileSize: Number(payload.fileSize || 1024),
      storageUrl,
      uploadToken: `mock-token-${Date.now()}`,
      status: 'ISSUED',
      expiresAt: mockNow(-30)
    } satisfies MediaUploadTicketResponse as T
  }

  const uploadTicketFileMatch = url.match(/^\/api\/media\/upload-tickets\/([^/]+)\/file$/)
  if (uploadTicketFileMatch && method === 'POST') {
    const ticketNo = decodeURIComponent(uploadTicketFileMatch[1])
    const isVideo = ticketNo.includes('CHAT_VIDEO')
    const isVoice = ticketNo.includes('CHAT_VOICE')
    const scene = isVideo ? 'CHAT_VIDEO' : isVoice ? 'CHAT_VOICE' : 'CHAT_IMAGE'
    return {
      ticketNo,
      ownerUserId: 1,
      scene,
      contentType: isVideo ? 'video/mp4' : isVoice ? 'audio/webm' : 'image/png',
      fileSize: 1024,
      storageUrl: mockUpload(scene, `local-uploaded-${Date.now()}.${isVideo ? 'mp4' : isVoice ? 'webm' : 'png'}`),
      uploadToken: `mock-token-${Date.now()}`,
      status: 'UPLOADED',
      expiresAt: mockNow(-30)
    } satisfies MediaUploadTicketResponse as T
  }

  if (url === '/api/chat/messages' && method === 'POST') {
    const payload = (data || {}) as { conversationId?: number; receiverId?: number; clientMsgId?: string; msgType?: 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO'; contentJson?: string }
    const conversationId = payload.conversationId || 5009
    const serverSeq = Math.floor(Date.now() / 1000)
    return {
      ack: {
        messageId: `mock-${Date.now()}`,
        conversationId,
        serverSeq,
        serverMsgId: `msg-${conversationId}-${serverSeq}`,
        clientMsgId: payload.clientMsgId || `client-${Date.now()}`,
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
      giftOrderNo: `GIFT-LOCAL-${Date.now()}`,
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
      { id: 2, kicker: '榜单热度更新', title: '魅力女神榜 / 多金男神榜', description: '女神看收礼，男神看消费，支持日榜周榜总榜。', cta: '去看看', imageUrl: '/uploads/home/banner-ranking.svg', action: 'ranking', sortOrder: 20, enabled: true, sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。', updatedAt: new Date().toISOString() },
      { id: 3, kicker: '日常生活频道', title: '分享今天的小确幸', description: '校园、寝室、城市日常，都可以轻松聊。', cta: '去社区', imageUrl: '/uploads/home/banner-community.svg', action: 'community', sortOrder: 30, enabled: true, sizeHint: '建议尺寸 750×300px（比例 5:2），JPG/PNG/WebP，单张不超过 500KB。', updatedAt: new Date().toISOString() }
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
