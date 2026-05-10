import { get, post } from '../http'

export type WalletMoneyAmount = number | string

export interface WalletBalanceResponse {
  rechargeBalance: WalletMoneyAmount
  incomeBalance: WalletMoneyAmount
  frozenBalance: WalletMoneyAmount
  withdrawableBalance: WalletMoneyAmount
}

export type WalletLedgerDirection = 'CREDIT' | 'DEBIT'
export type WalletLedgerStatus = 'SUCCESS' | 'FAILED' | 'PENDING'

export interface WalletLedgerItemResponse {
  ledgerNo: string
  direction: WalletLedgerDirection
  amount: WalletMoneyAmount
  balanceType: string
  businessType?: string | null
  businessId?: string | null
  balanceBefore: WalletMoneyAmount
  balanceAfter: WalletMoneyAmount
  status: WalletLedgerStatus | string
  remark?: string | null
  createdAt: string
}

export interface CreateWithdrawalRequest {
  amount: WalletMoneyAmount
  paymentMethod: string
  accountName: string
  accountNo: string
  remark?: string
}

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface WithdrawalResponse {
  withdrawalNo: string
  auditNo?: string | null
  userId: number
  amount: WalletMoneyAmount
  paymentMethod: string
  accountName: string
  maskedAccountNo: string
  accountVerifyStatus?: string | null
  status: WithdrawalStatus | string
  remark?: string | null
  createdAt: string
  reviewedAt?: string | null
}

export function getWalletBalance() {
  return get<WalletBalanceResponse>('/api/wallet/balance')
}

export function getWalletLedger() {
  return get<WalletLedgerItemResponse[]>('/api/wallet/ledger')
}

export function getWalletLedgerDetail(ledgerNo: string) {
  return get<WalletLedgerItemResponse>(`/api/wallet/ledger/${encodeURIComponent(ledgerNo)}`)
}

export function createWithdrawal(data: CreateWithdrawalRequest) {
  return post<WithdrawalResponse>('/api/wallet/withdrawals', data)
}

export function getWithdrawals() {
  return get<WithdrawalResponse[]>('/api/wallet/withdrawals')
}
