import type { RechargeStatus } from '../../api/modules/payment'
import type { WalletBalanceResponse, WalletLedgerDirection } from '../../api/modules/wallet'

export const emptyBalance: WalletBalanceResponse = {
  rechargeBalance: '--',
  incomeBalance: '--',
  frozenBalance: '--',
  withdrawableBalance: '--'
}

export const tabs = [{ label: '充值', value: 'recharge' }, { label: '提现', value: 'withdraw' }] as const

const statusTextMap: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  PENDING: '处理中'
}

const balanceTypeMap: Record<string, string> = {
  RECHARGE: '充值余额',
  INCOME: '收入余额',
  WITHDRAWABLE: '可提现余额',
  FROZEN: '冻结余额'
}

const businessTypeMap: Record<string, string> = {
  RECHARGE: '充值入账',
  ORDER_PAYMENT: '订单支付',
  ORDER_PAY: '订单支付',
  ORDER_REFUND: '订单退款',
  WITHDRAW: '提现',
  WITHDRAW_FREEZE: '提现冻结',
  WITHDRAW_PAYOUT: '提现出款',
  WITHDRAW_RELEASE: '提现解冻',
  GIFT: '礼物分账'
}

export function money(value: number): string {
  return Number.isFinite(value) ? value.toFixed(2) : '--'
}

export function statusLabel(status: RechargeStatus): string {
  return status === 'PAID' ? '已支付' : '待支付'
}

export function directionLabel(direction: WalletLedgerDirection | string): string {
  return direction === 'CREDIT' ? '收入' : '支出'
}

export function statusTextLabel(status: string): string {
  return statusTextMap[status] ?? status
}

export function balanceTypeLabel(balanceType: string): string {
  return balanceTypeMap[balanceType] ?? balanceType
}

export function businessLabel(businessType?: string | null): string {
  return businessType ? businessTypeMap[businessType] ?? businessType : '钱包流水'
}

export function formatDateTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '--'
}

export function normalizeAmount(amount: string): string {
  return amount.trim()
}

export function isValidMoneyAmount(amount: string): boolean {
  return /^\d+(\.\d{1,2})?$/.test(amount) && Number(amount) > 0
}

export function methodLabel(method: string): string {
  return method === 'ALIPAY' ? '支付宝' : '银行卡'
}
