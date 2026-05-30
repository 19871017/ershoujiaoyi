import type { HomeBannerAction } from '../../../api/modules/home'
import rankingGoddessArtwork from '../../../assets/ranking/ranking-goddess-desktop.png'
import rankingGodArtwork from '../../../assets/ranking/ranking-god-desktop.png'

export type BannerAction = HomeBannerAction
export type RankingTab = 'goddess' | 'god'
export type RankingCard = {
  tab: RankingTab
  themeClass: string
  artwork: string
  title: string
}

export const launchReadinessMarkers = [
  '暂未加载到后端在售宝贝',
  '商品接口暂时不可用，未展示本地演示宝贝',
  '首页轮播未加载到服务端配置时不展示本地兜底图',
  '件后端在售宝贝'
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
    title: '霸总男神榜'
  }
]

export const PRODUCT_COLUMNS = 2
export const VISIBLE_ROWS = 3
export const MIN_SIMULATED_PRODUCTS = 20
export const CARD_HEIGHT_RPX = 328
export const ROW_GAP_RPX = 16
export const MANUAL_SCROLL_RESUME_DELAY = 6000

export function statusLabel(status: string) {
  return status === 'created' || status === 'ACTIVE' ? '在售' : status
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
