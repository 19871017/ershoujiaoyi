-- 0012_payment_gateway_config.sql
-- Official Alipay / WeChat Pay channel config and gateway transaction tracking.

CREATE TABLE IF NOT EXISTS payment_channel_config (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  channel VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  sandbox TINYINT(1) NOT NULL DEFAULT 0,
  app_id VARCHAR(128) NULL,
  merchant_id VARCHAR(128) NULL,
  gateway_url VARCHAR(512) NULL,
  notify_url VARCHAR(512) NULL,
  return_url VARCHAR(512) NULL,
  merchant_private_key TEXT NULL,
  alipay_public_key TEXT NULL,
  merchant_serial_no VARCHAR(128) NULL,
  api_v3_key VARCHAR(128) NULL,
  wechat_pay_public_key TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_channel_config_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(128) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  provider_trade_no VARCHAR(128) NULL,
  gateway_payload TEXT NULL,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_transaction_no (payment_no),
  UNIQUE KEY uk_payment_transaction_biz_channel (biz_type, biz_no, user_id, channel),
  KEY idx_payment_transaction_user_created (user_id, created_at),
  KEY idx_payment_transaction_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO payment_channel_config (channel, enabled, sandbox, created_at, updated_at)
SELECT 'ALIPAY', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM payment_channel_config WHERE channel = 'ALIPAY');

INSERT INTO payment_channel_config (channel, enabled, sandbox, created_at, updated_at)
SELECT 'WECHAT', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM payment_channel_config WHERE channel = 'WECHAT');
