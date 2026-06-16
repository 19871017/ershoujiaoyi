import type { HomeBannerAction } from '../../../api/modules/home'
import type { ProductListItemResponse } from '../../../api/modules/product'
import rankingGoddessArtwork from '../../../assets/ranking/ranking-goddess-desktop.png'
import rankingGodArtwork from '../../../assets/ranking/ranking-god-desktop.png'
import { isDefaultAvatarUrl } from '../../../utils/default-avatar'

export type BannerAction = HomeBannerAction
export type RankingTab = 'goddess' | 'god'
export type RankingCard = {
  tab: RankingTab
  themeClass: string
  artwork: string
  title: string
}

export const launchReadinessMarkers = [
  '暂无在售宝贝',
  '宝贝暂时不可用，请稍后再逛',
  '新鲜宝贝会在这里陆续亮相',
  '今日上新 · {{ products.length }} 件在售宝贝',
  'products.value = remote',
  'products.value = []',
  'sellerDisplayName(item)',
  'sellerAvatarUrl(item)',
  'sellerInitial(item)'
]

export const rankingCards: RankingCard[] = [
  {
    tab: 'goddess',
    themeClass: 'ranking-goddess',
    artwork: rankingGoddessArtwork,
    title: '魅力女神榜'
  },
  {
    tab: 'god',
    themeClass: 'ranking-god',
    artwork: rankingGodArtwork,
    title: '多金男神榜'
  }
]

export function statusLabel(status: string) {
  if (status === 'created' || status === 'ACTIVE') return '在售'
  if (status === 'SOLD') return '已出'
  if (status === 'OFFLINE') return '已下架'
  if (status === 'PENDING_AUDIT') return '待完善'
  return '更新中'
}

export function compactPrice(price: string) {
  return Number(price).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

export function iconFor(title: string) {
  if (title.includes('裙')) return '👗'
  if (title.includes('鞋')) return '👠'
  if (title.includes('袜')) return '🧦'
  return '👜'
}

export function toneClass(id: number) {
  return `tone-${id % 4}`
}

export function formatPublishTime(createdAt: string) {
  const date = new Date(createdAt)
  if (Number.isNaN(date.getTime())) return '刚刚上新'
  const diffHours = Math.max(0, (Date.now() - date.getTime()) / (1000 * 60 * 60))
  if (diffHours < 1) return '刚刚上新'
  if (diffHours < 24) return `${Math.floor(diffHours)} 小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays} 天前`
  return `${date.getMonth() + 1}/${date.getDate()} 上新`
}

export function sellerDisplayName(item: ProductListItemResponse) {
  const name = item.sellerNickname?.trim()
  if (name) return name
  return item.sellerId ? `卖家 ${item.sellerId}` : '卖家资料待同步'
}

export function sellerInitial(item: ProductListItemResponse) {
  return sellerDisplayName(item).slice(0, 1) || '卖'
}

export function sellerAvatarUrl(item: ProductListItemResponse) {
  const avatar = item.sellerAvatarUrl?.trim()
  if (!avatar) return ''
  if (isDefaultAvatarUrl(avatar)) return avatar
  const lower = avatar.toLowerCase()
  const allowedPrefix = avatar.startsWith('/uploads/avatar/') || avatar.startsWith('/uploads/community-image/')
  const invalid = avatar.startsWith('local://') ||
    avatar.startsWith('blob:') ||
    avatar.startsWith('data:') ||
    lower.includes('placeholder') ||
    lower.includes('preview') ||
    lower.includes('%2e') ||
    lower.includes('%2f') ||
    lower.includes('%5c') ||
    avatar.includes('\\') ||
    avatar.includes('..') ||
    avatar.includes('//') ||
    !allowedPrefix
  return invalid ? '' : avatar
}
