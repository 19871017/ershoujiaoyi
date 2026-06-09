-- 0003_im_core.sql
-- IM 会话、消息、回执基础表，与后端 ChatApplicationService 当前 schema 对齐。

CREATE TABLE IF NOT EXISTS im_conversation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  conversation_no VARCHAR(128) NOT NULL,
  owner_user_id BIGINT NOT NULL,
  peer_user_id BIGINT NOT NULL,
  conversation_type VARCHAR(32) NOT NULL DEFAULT 'SINGLE',
  last_seq BIGINT NOT NULL DEFAULT 0,
  last_message_summary VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_im_conversation_no (conversation_no),
  KEY idx_im_conversation_owner_updated (owner_user_id, updated_at),
  KEY idx_im_conversation_peer_updated (peer_user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS im_message (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  message_no VARCHAR(128) NOT NULL,
  conversation_id BIGINT NOT NULL,
  conversation_no VARCHAR(128) NOT NULL,
  server_seq BIGINT NOT NULL,
  client_msg_id VARCHAR(64) NOT NULL,
  client_key VARCHAR(255) NOT NULL,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  message_type VARCHAR(32) NOT NULL,
  content_json TEXT NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_im_message_no (message_no),
  UNIQUE KEY uk_im_message_client_key (client_key),
  UNIQUE KEY uk_im_message_conversation_seq (conversation_id, server_seq),
  KEY idx_im_message_receiver_created (receiver_id, created_at),
  KEY idx_im_message_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS im_receipt (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_seq BIGINT NOT NULL DEFAULT 0,
  delivered_seq BIGINT NOT NULL DEFAULT 0,
  cleared_seq BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_im_receipt_conversation_user (conversation_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_im_message_revoked_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE im_message ADD COLUMN revoked TINYINT(1) NOT NULL DEFAULT 0 AFTER content_json',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'im_message'
    AND COLUMN_NAME = 'revoked'
);

PREPARE add_im_message_revoked_stmt FROM @add_im_message_revoked_sql;
EXECUTE add_im_message_revoked_stmt;
DEALLOCATE PREPARE add_im_message_revoked_stmt;

SET @add_im_message_revoked_at_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE im_message ADD COLUMN revoked_at DATETIME NULL AFTER revoked',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'im_message'
    AND COLUMN_NAME = 'revoked_at'
);

PREPARE add_im_message_revoked_at_stmt FROM @add_im_message_revoked_at_sql;
EXECUTE add_im_message_revoked_at_stmt;
DEALLOCATE PREPARE add_im_message_revoked_at_stmt;

SET @add_im_receipt_cleared_seq_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE im_receipt ADD COLUMN cleared_seq BIGINT NOT NULL DEFAULT 0 AFTER delivered_seq',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'im_receipt'
    AND COLUMN_NAME = 'cleared_seq'
);

PREPARE add_im_receipt_cleared_seq_stmt FROM @add_im_receipt_cleared_seq_sql;
EXECUTE add_im_receipt_cleared_seq_stmt;
DEALLOCATE PREPARE add_im_receipt_cleared_seq_stmt;
