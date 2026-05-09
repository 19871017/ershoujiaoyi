-- 0004_wallet_ledger.sql
-- 钱包账户、账本流水、冻结记录、提现申请

CREATE TABLE IF NOT EXISTS wallet_account (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_status VARCHAR(32) NOT NULL DEFAULT 'active',
  recharge_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  income_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  frozen_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  withdrawable_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wallet_account_user (user_id),
  KEY idx_wallet_account_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallet_ledger (
  id BIGINT PRIMARY KEY,
  ledger_no VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  balance_type VARCHAR(32) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  before_balance DECIMAL(18,2) NOT NULL,
  after_balance DECIMAL(18,2) NOT NULL,
  ledger_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  rate_snapshot_json JSON NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wallet_ledger_no (ledger_no),
  UNIQUE KEY uk_wallet_ledger_idempotency (idempotency_key),
  KEY idx_wallet_ledger_user_created (user_id, created_at),
  KEY idx_wallet_ledger_biz (biz_type, biz_no),
  KEY idx_wallet_ledger_status (ledger_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wallet_freeze_record (
  id BIGINT PRIMARY KEY,
  freeze_no VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  freeze_status VARCHAR(32) NOT NULL DEFAULT 'frozen',
  reason VARCHAR(255) NULL,
  frozen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  unfrozen_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wallet_freeze_no (freeze_no),
  KEY idx_wallet_freeze_user_status (user_id, freeze_status),
  KEY idx_wallet_freeze_biz (biz_type, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS withdrawal_request (
  id BIGINT PRIMARY KEY,
  withdraw_no VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  withdraw_status VARCHAR(32) NOT NULL DEFAULT 'applied',
  review_by BIGINT NULL,
  review_at DATETIME NULL,
  reject_reason VARCHAR(255) NULL,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_withdraw_no (withdraw_no),
  KEY idx_withdraw_user_created (user_id, created_at),
  KEY idx_withdraw_status_created (withdraw_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
