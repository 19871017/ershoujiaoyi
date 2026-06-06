import type { UserProfileResponse } from '../../../api/modules/user'
import type { WalletBalanceResponse } from '../../../api/modules/wallet'

export const emptyProfile: UserProfileResponse = {
  userId: 0,
  userNo: '',
  nickname: '小原圈用户',
  avatarUrl: '',
  gender: 'goddess',
  mainRole: 'BUYER',
  identityStatus: 'UNVERIFIED',
  videoIdentityStatus: 'UNVERIFIED',
  videoVerified: false
}

export const emptyBalance: WalletBalanceResponse = {
  rechargeBalance: '--',
  incomeBalance: '--',
  frozenBalance: '--',
  withdrawableBalance: '--'
}

export const publishRoles = ['SELLER', 'BOTH']

export type OrderStatusKey = 'pendingPay' | 'pendingShip' | 'pendingReceive' | 'afterSales'
export type MenuActionKey = 'afterSales'
export type MeMenuItem = {
  icon: string
  label: string
  url?: string
  key?: MenuActionKey
}

export const orderStatusItems: Array<{ key: OrderStatusKey; icon: string; label: string }> = [
  { key: 'pendingPay', icon: '💳', label: '待付款' },
  { key: 'pendingShip', icon: '📦', label: '待发货' },
  { key: 'pendingReceive', icon: '🧾', label: '待收货' },
  { key: 'afterSales', icon: '🌸', label: '售后' }
]

export const menus: MeMenuItem[] = [
  { icon: '👤', label: '编辑资料', url: '/pages/user/profile/index' },
  { icon: '📦', label: '我的订单', url: '/pages/order/list/index' },
  { icon: '🌸', label: '我的售后', key: 'afterSales' },
  { icon: '💰', label: '钱包账本', url: '/pages/wallet/index' },
  { icon: '🏦', label: '提现审核', url: '/pages/wallet/index?tab=withdraw' },
  { icon: '💳', label: '收款账户', url: '/pages/wallet/accounts/index' },
  { icon: '📍', label: '地址管理', url: '/pages/user/address/index' },
  { icon: '🎁', label: '收到的礼物', url: '/pages/gift/index' },
  { icon: '🛡️', label: '举报与风控', url: '/pages/risk/index' },
  { icon: '⚙️', label: '设置', url: '/pages/system/settings/index' }
]
