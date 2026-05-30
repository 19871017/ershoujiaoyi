import type { ChatConversationItem } from '../../api/modules/chat'

export const chatLevelThresholds = [0, 50, 200, 800, 2000, 5000, 12000, 30000, 80000, 200000]
export const chatGodLevelTitles = ['初见绅士', '心动骑士', '闪耀贵宾', '星光守护', '黄金公子', '铂金名士', '钻石守护', '星河领主', '传奇男神', '原圈荣耀']
export const chatGoddessLevelTitles = ['心动新星', '魅力甜心', '人气佳人', '星光女神', '闪耀名媛', '璀璨公主', '荣耀女王', '星河缪斯', '传奇女神', '原圈天后']

export interface ChatPeerLevel {
  level: number
  title: string
  track: 'POWER' | 'CHARM'
  score: number
}

export type ChatPeerIdentitySource = Pick<ChatConversationItem, 'peerGender' | 'peerCity' | 'peerVideoVerified' | 'peerSellerCharmScore' | 'peerBuyerPowerScore'>

export function assertChatPeerIdentityFields(item: ChatConversationItem): void {
  for (const field of ['peerGender', 'peerCity', 'peerMainRole'] as const) {
    if (item[field] != null && typeof item[field] !== 'string') throw new Error(`chat conversation invalid ${field}`)
  }
  if (item.peerVideoVerified != null && typeof item.peerVideoVerified !== 'boolean') throw new Error('chat conversation invalid peerVideoVerified')
  for (const field of ['peerSellerCharmScore', 'peerBuyerPowerScore'] as const) {
    const value = item[field]
    if (value != null && (!Number.isFinite(value) || value < 0)) throw new Error(`chat conversation invalid ${field}`)
  }
}

export function normalizedPeerGender(item: Pick<ChatConversationItem, 'peerGender'>): string {
  return (item.peerGender || '').trim().toLowerCase()
}

export function peerGenderSymbol(item: Pick<ChatConversationItem, 'peerGender'>): string {
  const gender = normalizedPeerGender(item)
  if (gender === 'god') return '♂'
  if (gender === 'goddess') return '♀'
  return '性别待完善'
}

export function buildChatPeerLevel(item: Pick<ChatConversationItem, 'peerGender' | 'peerSellerCharmScore' | 'peerBuyerPowerScore'>): ChatPeerLevel {
  const powerTrack = normalizedPeerGender(item) === 'god'
  const score = Math.max(0, Math.floor(Number(powerTrack ? item.peerBuyerPowerScore : item.peerSellerCharmScore) || 0))
  let level = 1
  for (let index = 0; index < chatLevelThresholds.length; index += 1) {
    if (score >= chatLevelThresholds[index]) level = index + 1
  }
  return {
    level,
    title: (powerTrack ? chatGodLevelTitles : chatGoddessLevelTitles)[level - 1],
    track: powerTrack ? 'POWER' : 'CHARM',
    score
  }
}

export function chatPeerIdentityBadges(item: ChatPeerIdentitySource): string[] {
  const level = buildChatPeerLevel(item)
  return [
    peerGenderSymbol(item),
    item.peerCity?.trim() || '地区待完善',
    `LV.${level.level} ${level.title}`,
    item.peerVideoVerified ? '视频认证' : ''
  ].filter(Boolean)
}
