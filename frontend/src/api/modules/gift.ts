import { get, post } from '../http'
import type { WalletMoneyAmount } from './wallet'

export type GiftSendStatus = 'SUCCESS'

export interface GiftCatalogItemResponse {
  giftId: number
  giftCode: string
  name: string
  icon: string
  price: WalletMoneyAmount
  platformRate: WalletMoneyAmount
}

export interface ReceivedGiftItemResponse {
  giftOrderNo: string
  senderId: number
  giftId: number
  giftCode: string
  giftName: string
  giftIcon: string
  quantity: number
  totalAmount: WalletMoneyAmount
  platformShare: WalletMoneyAmount
  receiverAmount: WalletMoneyAmount
  receiverCreditLedgerNo: string
  status: GiftSendStatus | string
  createdAt: string
}

export interface SendGiftRequest {
  /** 平台会忽略该字段，发送人以当前登录用户/请求头为准 */
  senderId?: number
  receiverId: number
  giftCode?: string
  giftId?: number
  quantity?: number
  sceneType?: string
  sceneId?: number
  /** 平台强制 requestNo/clientGiftId 至少一个，用于防重复点击重复扣款 */
  clientGiftId?: string
  /** 平台强制 requestNo/clientGiftId 至少一个，用于防重复点击重复扣款 */
  requestNo?: string
}

export interface SendGiftResponse {
  giftOrderNo: string
  giftId: number
  giftCode: string
  receiverId: number
  totalAmount: WalletMoneyAmount
  platformShare: WalletMoneyAmount
  receiverAmount: WalletMoneyAmount
  debitLedgerNo: string
  receiverCreditLedgerNo: string
  status: GiftSendStatus
  createdAt: string
}

export function getGiftCatalog() {
  return get<GiftCatalogItemResponse[]>('/api/gifts/catalog')
}

export function getReceivedGifts() {
  return get<ReceivedGiftItemResponse[]>('/api/gifts/received')
}

export function sendGift(data: SendGiftRequest) {
  return post<SendGiftResponse>('/api/gifts/send', data)
}
